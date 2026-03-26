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

package songscribe.ui;

import module java.desktop;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import songscribe.util.UIUtils;

/**
 * Centralized dialog utility. All user-facing dialogs in the application
 * route through this class, which handles suppression in headless and
 * test contexts.
 */
public final class OptionDialogs {

    private static final Logger LOG = LoggerFactory.getLogger(OptionDialogs.class);

    private static final int SCREEN_MARGIN_PX = 20;

    private static boolean suppressDialogs = false;

    private OptionDialogs() {}

    /**
     * Controls dialog suppression for testing. When true, all dialog
     * methods log instead of showing UI.
     */
    public static void setSuppressDialogs(boolean suppress) {
        suppressDialogs = suppress;
    }

    // ------------------------------------------------------------------
    // Core methods
    // ------------------------------------------------------------------

    public static void showInfoMessage(
        @Nullable Component parent,
        String title,
        String message
    ) {
        showMessageDialog(parent, title, message, JOptionPane.INFORMATION_MESSAGE, LOG::trace, false);
    }

    /**
     * Shows an error message dialog. Beeps before showing the dialog
     * (unless suppressed).
     */
    public static void showErrorMessage(
        @Nullable Component parent,
        String title,
        String message
    ) {
        showMessageDialog(parent, title, message, JOptionPane.ERROR_MESSAGE, LOG::error, true);
    }

    public static void showWarningMessage(
        @Nullable Component parent,
        String title,
        String message
    ) {
        showMessageDialog(parent, title, message, JOptionPane.WARNING_MESSAGE, LOG::warn, false);
    }

    public static int showConfirmDialog(
        @Nullable Component parent,
        String title,
        String message,
        int optionType,
        int messageType
    ) {
        return showConfirmDialog(parent, title, message, optionType, messageType, JOptionPane.NO_OPTION);
    }

    public static int showConfirmDialog(
        @Nullable Component parent,
        String title,
        String message,
        int optionType,
        int messageType,
        int suppressedDefault
    ) {
        LOG.trace(message);

        if (isSuppressed()) {
            return suppressedDefault;
        }

        try {
            var pane = new JOptionPane(message, messageType, optionType);
            showOptionPane(pane, parent, title);

            var value = pane.getValue();
            int result = value instanceof Integer i ? i : JOptionPane.CLOSED_OPTION;

            if (result == JOptionPane.CLOSED_OPTION) {
                return optionType == JOptionPane.YES_NO_CANCEL_OPTION
                    ? JOptionPane.CANCEL_OPTION
                    : JOptionPane.NO_OPTION;
            }

            return result;
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing confirm dialog", e);
            return suppressedDefault;
        }
    }

    public static @Nullable String showInputDialog(
        @Nullable Component parent,
        String title,
        String message
    ) {
        return showInputDialog(parent, title, message, null);
    }

    public static @Nullable String showInputDialog(
        @Nullable Component parent,
        String title,
        String message,
        @Nullable String suppressedDefault
    ) {
        LOG.trace(message);

        if (isSuppressed()) {
            return suppressedDefault;
        }

        try {
            var pane = new JOptionPane(
                message, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, null, null
            );
            pane.setWantsInput(true);
            showOptionPane(pane, parent, title);

            var value = pane.getInputValue();
            return value == JOptionPane.UNINITIALIZED_VALUE ? null : (String) value;
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing input dialog", e);
            return suppressedDefault;
        }
    }

    public static int showOptionDialog(
        @Nullable Component parent,
        String title,
        Object message,
        int optionType,
        int messageType,
        @Nullable Icon icon,
        Object @Nullable [] options,
        @Nullable Object initialValue
    ) {
        LOG.trace("{}", message);

        if (isSuppressed()) {
            return JOptionPane.CLOSED_OPTION;
        }

        try {
            var pane = new JOptionPane(message, messageType, optionType, icon, options, initialValue);
            showOptionPane(pane, parent, title);
            return getOptionPaneResult(pane, options);
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing option dialog", e);
            return JOptionPane.CLOSED_OPTION;
        }
    }

    // ------------------------------------------------------------------
    // Convenience methods
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Private
    // ------------------------------------------------------------------

    private static void showMessageDialog(
        @Nullable Component parent,
        String title,
        String message,
        int messageType,
        Consumer<String> logger,
        boolean beep
    ) {
        logger.accept(message);

        if (isSuppressed()) {
            return;
        }

        if (beep) {
            Toolkit.getDefaultToolkit().beep();
        }

        try {
            var pane = new JOptionPane(message, messageType);
            showOptionPane(pane, parent, title);
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing message dialog", e);
        }
    }

    private static void showOptionPane(JOptionPane pane, @Nullable Component parent, String title) {
        var window = getParentWindow(parent);
        JDialog dialog;

        if (window instanceof Frame f) {
            dialog = new JDialog(f, title, true);
        } else if (window instanceof Dialog d) {
            dialog = new JDialog(d, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }

        dialog.setComponentOrientation(pane.getComponentOrientation());
        var contentPane = dialog.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(pane, BorderLayout.CENTER);
        dialog.setResizable(false);

        // Replicate initDialog's L&F decoration check for FlatLaf compatibility
        if (JDialog.isDefaultLookAndFeelDecorated() && UIManager.getLookAndFeel().getSupportsWindowDecorations()) {
            dialog.setUndecorated(true);
            pane.getRootPane().setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
        }

        dialog.pack();
        positionDialog(dialog, parent);
        UIUtils.addStandardDialogKeyBindings(dialog);

        // Close dialog when user selects a value
        PropertyChangeListener closeListener = e -> {
            if (dialog.isVisible()
                    && e.getSource() == pane
                    && JOptionPane.VALUE_PROPERTY.equals(e.getPropertyName())
                    && e.getNewValue() != null
                    && e.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                dialog.setVisible(false);
            }
        };

        pane.addPropertyChangeListener(closeListener);

        var windowListener = new WindowAdapter() {
            private boolean gotFocus = false;

            @Override
            public void windowClosing(WindowEvent e) {
                pane.setValue(null);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                pane.removePropertyChangeListener(closeListener);
                dialog.getContentPane().removeAll();
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (!gotFocus) {
                    gotFocus = true;
                    pane.selectInitialValue();
                }
            }
        };

        dialog.addWindowListener(windowListener);
        dialog.addWindowFocusListener(windowListener);

        // Reset value on each showing to prevent stale value from a previous show
        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                pane.setValue(JOptionPane.UNINITIALIZED_VALUE);
            }
        });

        dialog.setVisible(true);
        dialog.dispose();
    }

    // Position the dialog at 3/8 of the way down the parent window (or screen if no
    // parent), centered horizontally, clamped to the screen bounds with a 20px margin.
    public static void positionDialog(JDialog dialog, @Nullable Component parent) {
        var window = getParentWindow(parent);
        var screen = getScreenBounds(window);
        var bounds = window != null ? window.getBounds() : screen;
        var size = dialog.getSize();

        var x = bounds.x + (bounds.width - size.width) / 2;
        var y = bounds.y + bounds.height * 3 / 8 - size.height / 2;

        x = Math.clamp(x, screen.x + SCREEN_MARGIN_PX, screen.x + screen.width - size.width - SCREEN_MARGIN_PX);
        y = Math.clamp(y, screen.y + SCREEN_MARGIN_PX, screen.y + screen.height - size.height - SCREEN_MARGIN_PX);

        dialog.setLocation(x, y);
    }

    private static @Nullable Window getParentWindow(@Nullable Component parent) {
        if (parent instanceof Window w) return w;
        return parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
    }

    private static Rectangle getScreenBounds(@Nullable Window window) {
        if (window != null) {
            return window.getGraphicsConfiguration().getBounds();
        }

        return GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration()
            .getBounds();
    }

    private static int getOptionPaneResult(JOptionPane pane, Object @Nullable [] options) {
        var value = pane.getValue();

        if (value == null) {
            return JOptionPane.CLOSED_OPTION;
        }

        if (options == null) {
            return value instanceof Integer i ? i : JOptionPane.CLOSED_OPTION;
        }

        for (var i = 0; i < options.length; i++) {
            if (value.equals(options[i])) {
                return i;
            }
        }

        return JOptionPane.CLOSED_OPTION;
    }

    private static boolean isSuppressed() {
        return GraphicsEnvironment.isHeadless() || suppressDialogs;
    }
}
