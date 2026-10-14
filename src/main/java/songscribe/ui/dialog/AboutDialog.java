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
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
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
import songscribe.message.MessageSubscription;
import songscribe.message.notification.ApplicationDidEnterBackgroundNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.ActivationGate;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.SplashWindow;
import songscribe.util.DesktopUtils;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

/**
 * The splash artwork shown on demand, with the web and acknowledgement links added below it.
 *
 * <p>Not a {@link BaseDialog}: it needs the borderless pane the splash uses, and it dismisses
 * itself on the next thing the user does, so it has no OK/Cancel lifecycle and no persisted
 * geometry.
 *
 * <p>It cannot be modal: a modal window never learns about the outside click that dismisses it.
 *
 * <p><b>It never takes focus</b>, and no part of the dismissal reads window focus: a borderless
 * window on macOS does not reliably deliver {@code windowLostFocus}, and when it does the
 * window flickers.
 *
 * <p>The message subscription ends in {@link #dismiss()}, not in a {@code dispose()} override.
 */
public final class AboutDialog extends JDialog {

    public static final String WEB = "https://www.songscribe.org";
    private static final String ACKNOWLEDGEMENTS_URL =
        "https://github.com/vasudeva-server/SongScribe/blob/main/THIRD_PARTY_LICENSES.txt";

    /**
     * A modifier going down on its own is the first half of a shortcut, so it does not dismiss.
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
    private final MessageSubscription subscription;
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
        subscription = new MessageSubscription(this);
    }

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

    @Handler
    public void applicationDidEnterBackground(ApplicationDidEnterBackgroundNotification message) {
        dismiss();
    }

    /** More than one dismissal path can fire for a single click. */
    private void dismiss() {
        if (dismissed) {
            return;
        }

        dismissed = true;
        var focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.removeKeyEventDispatcher(keyDismisser);
        Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickDismisser);
        subscription.dispose();
        ActivationGate.disarmForOverlay();

        if (instance == this) {
            instance = null;
        }

        dispose();
    }

    /**
     * The press itself is swallowed by the glass pane {@link ActivationGate#armForOverlay()}
     * raised, because consuming an event from here would not stop Swing delivering it.
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
     * Returns true so the key goes no further; otherwise Cmd+W would close the song on its way
     * past.
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
