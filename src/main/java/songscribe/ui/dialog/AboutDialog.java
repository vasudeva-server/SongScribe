/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ApplicationDidEnterBackgroundNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.ActivationGate;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.SplashWindow;
import songscribe.util.DesktopUtils;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

/**
 * The splash artwork shown on demand, with the web and acknowledgement links added below it.
 *
 * <p>It is deliberately not a {@link BaseDialog}: the whole point is the borderless pane the
 * splash uses, and {@code BaseDialog} always builds a titled, decorated {@code JDialog}. Like
 * a splash screen it dismisses itself on the next thing the user does — a click inside, a
 * click outside, any keypress, or switching away from the application — so it also carries no
 * OK/Cancel lifecycle and no persisted geometry.
 *
 * <p>It cannot be modal: Java's modal event filter discards input events aimed at blocked
 * windows before any listener sees them, so a modal window can never learn about the outside
 * click that is supposed to dismiss it.
 *
 * <p><b>It never takes focus</b> ({@code setFocusableWindowState(false)}), and no part of the
 * dismissal reads window focus. An earlier version dismissed on {@code windowLostFocus}, which
 * a borderless window on macOS cannot be relied on to deliver: sometimes the window never
 * became the key window, so the event never arrived and it could not be dismissed at all, and
 * when it did arrive it came after the click had already raised the frame and re-raised this
 * window above it — a visible flicker. Staying unfocusable leaves the frame active throughout,
 * so there is no activation to race with. Every dismissal trigger is instead a global listener
 * that sees the input before it is dispatched, or the application-background notification.
 */
public final class AboutDialog extends JDialog {

    public static final String WEB = "https://www.songscribe.org";
    private static final String ACKNOWLEDGEMENTS_URL =
        "https://github.com/vasudeva-server/SongScribe/blob/main/THIRD_PARTY_LICENSES.txt";

    /**
     * A modifier going down on its own isn't the user doing something — it's the first half of
     * a shortcut, or a hand resting on Shift — so it doesn't count as a dismissing keypress.
     */
    private static final Set<Integer> MODIFIER_KEYS = Set.of(
        KeyEvent.VK_SHIFT,
        KeyEvent.VK_CONTROL,
        KeyEvent.VK_ALT,
        KeyEvent.VK_ALT_GRAPH,
        KeyEvent.VK_META,
        KeyEvent.VK_CAPS_LOCK
    );

    private static @Nullable AboutDialog instance = null;

    private final KeyEventDispatcher keyDismisser = this::dismissOnKeyPress;
    private final AWTEventListener outsideClickDismisser = this::dismissOnOutsideClick;
    private boolean dismissed = false;

    private AboutDialog(MainFrame mainFrame) {
        // Nothing renders the title, but it is the window's accessible name.
        super(mainFrame, Strings.get(Strings.DIALOG_ABOUT_TITLE), false);

        setUndecorated(true);
        setResizable(false);
        setFocusableWindowState(false);
        setBackground(FlatLafProps.getColor(FlatLafKey.SPLASH_WINDOW_BACKGROUND));

        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);

        var divider = new JPanel();
        divider.setBackground(Color.LIGHT_GRAY);
        divider.setPreferredSize(new Dimension(0, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);

        var linksRow = new JPanel(new BorderLayout());
        linksRow.setOpaque(false);
        linksRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        linksRow.add(createLink(WEB, WEB), BorderLayout.WEST);
        linksRow.add(
            createLink(Strings.get(Strings.DIALOG_ABOUT_ACKNOWLEDGEMENTS), ACKNOWLEDGEMENTS_URL),
            BorderLayout.EAST
        );

        var extras = new JPanel();
        extras.setLayout(new BoxLayout(extras, BoxLayout.Y_AXIS));
        extras.setOpaque(false);
        extras.setAlignmentX(Component.LEFT_ALIGNMENT);
        extras.add(Box.createVerticalStrut(gap));
        extras.add(divider);
        extras.add(Box.createVerticalStrut(gap));
        extras.add(linksRow);

        var content = SplashWindow.createContentPanel(extras);

        // Swing delivers a press to the deepest component that has mouse listeners enabled,
        // so a click on a link stops there and never reaches this listener.
        content.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dismiss();
            }
        });

        setContentPane(content);
        pack();
        UIUtils.positionDialog(this, mainFrame);
        MessageCenter.subscribe(this);
    }

    /** Shows the window, replacing one that is already up. */
    public static void show(MainFrame mainFrame) {
        var showing = instance;

        if (showing != null) {
            showing.dismiss();
        }

        var about = new AboutDialog(mainFrame);

        // The static field is also what keeps this subscriber strongly reachable while it is up.
        instance = about;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(about.keyDismisser);
        Toolkit.getDefaultToolkit().addAWTEventListener(about.outsideClickDismisser, AWTEvent.MOUSE_EVENT_MASK);
        ActivationGate.armForOverlay();
        about.setVisible(true);
    }

    /** Switching to another app takes this down with it. */
    @Handler
    public void applicationDidEnterBackground(ApplicationDidEnterBackgroundNotification message) {
        dismiss();
    }

    /** Every dismissal path funnels through here, and more than one can fire for a single click. */
    private void dismiss() {
        if (dismissed) {
            return;
        }

        dismissed = true;
        var focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.removeKeyEventDispatcher(keyDismisser);
        Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickDismisser);
        ActivationGate.disarmForOverlay();

        if (instance == this) {
            instance = null;
        }

        dispose();
    }

    /**
     * Dismisses on a press anywhere else in the application. Presses landing on this window are
     * left to the content panel's own listener, which is what lets a link click through.
     *
     * <p>An {@code AWTEventListener} sees the press before it is dispatched, so this does not
     * depend on which window is focused or on the order the activation events arrive in. The
     * press itself is swallowed by the glass pane {@link ActivationGate#armForOverlay()} raised,
     * because consuming an event from here would not stop Swing delivering it.
     */
    private void dismissOnOutsideClick(AWTEvent e) {
        if (e.getID() != MouseEvent.MOUSE_PRESSED || !(e.getSource() instanceof Component source)) {
            return;
        }

        if (source != this && !isAncestorOf(source)) {
            dismiss();
        }
    }

    /**
     * Dismisses on any key, which covers Escape and Cmd+W without binding them separately.
     *
     * <p>Returns true so the key goes no further: while this window is up, a keypress dismisses
     * it and does nothing else. Letting keys through instead would mean Cmd+W closed the song
     * on its way past. On macOS the screen menu bar handles Cmd+Q before Java sees it, so
     * quitting still works.
     */
    private boolean dismissOnKeyPress(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED || MODIFIER_KEYS.contains(e.getKeyCode())) {
            return false;
        }

        dismiss();
        return true;
    }

    private static JLabel createLink(String text, String url) {
        var label = new JLabel(text);

        if (DesktopUtils.isDesktopSupported()) {
            label.setForeground(UIManager.getColor("Component.linkColor"));
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // This listener is also what keeps a link click from dismissing the window:
            // the press stops at the deepest component that has mouse listeners enabled.
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Utils.openWebPage(url);
                }
            });
        }

        return label;
    }
}
