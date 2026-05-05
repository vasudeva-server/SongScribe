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

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

/**
 * Suppresses the mouse click that brings the application window to the foreground.
 * <p>
 * Uses a transparent glass pane that is activated when the application moves
 * to the background. The glass pane intercepts and consumes the first mouse press,
 * then deactivates itself. For keyboard-based activation (e.g., Cmd+Tab), a timer
 * deactivates the glass pane after a short delay.
 */
public final class ActivationGate {
    private static final int CMD_TAB_DELAY_MS = 300;
    private static @Nullable JPanel glassPane = null;
    private static @Nullable Timer cmdTabTimer = null;

    private ActivationGate() {
    }

    /**
     * Installs the activation gate on the given frame. Call after the frame
     * is visible and its content is fully initialized.
     */
    public static void install(JFrame frame) {
        glassPane = new JPanel(null);
        glassPane.setOpaque(false);
        glassPane.setVisible(false);
        glassPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                deactivate();
            }
        });

        frame.setGlassPane(glassPane);

        // Timer for Cmd+Tab case: deactivate glass pane if no click arrives
        cmdTabTimer = new Timer(CMD_TAB_DELAY_MS, e -> deactivate());
        cmdTabTimer.setRepeats(false);
    }

    /**
     * Called when the application moves to the background.
     * Activates the glass pane so the next click is intercepted.
     */
    public static void activate() {
        if (glassPane != null) {
            glassPane.setVisible(true);
        }
    }

    /**
     * Called when the application is raised to the foreground.
     * Starts a timer to deactivate the glass pane, allowing keyboard-based
     * activation (Cmd+Tab) to work without suppressing the next click.
     */
    public static void appRaisedToForeground() {
        if (cmdTabTimer != null) {
            cmdTabTimer.restart();
        }
    }

    private static void deactivate() {
        if (cmdTabTimer != null) {
            cmdTabTimer.stop();
        }

        if (glassPane != null) {
            glassPane.setVisible(false);
        }
    }
}
