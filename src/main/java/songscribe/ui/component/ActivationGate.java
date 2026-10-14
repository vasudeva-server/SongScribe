/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.notification.ApplicationDidBecomeActiveNotification;
import songscribe.message.notification.ApplicationDidEnterBackgroundNotification;
import songscribe.util.Debounce;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

/**
 * Suppresses the mouse click that brings the application window to the foreground.
 *
 * <p>Also the single source of {@link ApplicationDidEnterBackgroundNotification} and
 * {@link ApplicationDidBecomeActiveNotification}, posted from {@link #activate()} and
 * {@link #deactivate()}.
 *
 * <p>{@link #armForOverlay()} raises the same glass pane for an unrelated reason, and posts
 * nothing. The two reasons are tracked separately and the pane is up while either holds.
 */
public final class ActivationGate {
    private static final int CMD_TAB_DELAY_MS = 300;
    static @Nullable JPanel glassPane = null;
    static @Nullable Debounce cmdTabDebounce = null;
    static boolean backgrounded = false;
    static boolean overlayArmed = false;

    private ActivationGate() {
    }

    /** Call after the frame is visible and its content is fully initialized. */
    public static void install(JFrame frame) {
        glassPane = new JPanel(null);
        glassPane.setOpaque(false);
        glassPane.setVisible(false);
        glassPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Having a listener at all is what swallows the press. When an overlay armed
                // the pane, the overlay is the one that lowers it — the app never left the
                // foreground, so there is no reactivation to report.
                if (overlayArmed) {
                    return;
                }

                deactivate();
            }
        });

        frame.setGlassPane(glassPane);

        // Cmd+Tab case: deactivate the glass pane if no click arrives
        cmdTabDebounce = Debounce.rescheduling(CMD_TAB_DELAY_MS, ActivationGate::deactivate);
    }

    public static void activate() {
        backgrounded = true;
        updateGlassPane();

        MessageCenter.post(new ApplicationDidEnterBackgroundNotification());
    }

    /**
     * The caller must pair this with {@link #disarmForOverlay()} on every path that takes the
     * overlay down.
     */
    public static void armForOverlay() {
        overlayArmed = true;
        updateGlassPane();
    }

    public static void disarmForOverlay() {
        overlayArmed = false;
        updateGlassPane();
    }

    /**
     * Deactivation is on a timer so that keyboard-based activation (Cmd+Tab) works without
     * suppressing the next click.
     */
    public static void appRaisedToForeground() {
        if (cmdTabDebounce != null) {
            cmdTabDebounce.trigger();
        }
    }

    static void deactivate() {
        if (cmdTabDebounce != null) {
            cmdTabDebounce.cancel();
        }

        backgrounded = false;
        updateGlassPane();

        MessageCenter.post(new ApplicationDidBecomeActiveNotification());
    }

    private static void updateGlassPane() {
        if (glassPane != null) {
            glassPane.setVisible(backgrounded || overlayArmed);
        }
    }
}
