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

import java.io.File;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.Strings;
import songscribe.util.Log;
import songscribe.util.UIUtils;

/**
 * Centralized dialog utility. All user-facing dialogs in the application
 * route through this class, which handles suppression in headless and
 * test contexts.
 */
public final class Dialogs {

    private static final int SCREEN_MARGIN_PX = 20;

    private static boolean suppressDialogs = false;

    private Dialogs() {}

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
        showMessageDialog(parent, title, message, JOptionPane.INFORMATION_MESSAGE, Log::info, false);
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
        showMessageDialog(parent, title, message, JOptionPane.ERROR_MESSAGE, Log::error, true);
    }

    public static void showWarningMessage(
        @Nullable Component parent,
        String title,
        String message
    ) {
        showMessageDialog(parent, title, message, JOptionPane.WARNING_MESSAGE, Log::warning, false);
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
        Log.info(message);

        if (isSuppressed()) {
            return suppressedDefault;
        }

        try {
            var result = JOptionPane.showConfirmDialog(parent, message, title, optionType, messageType);

            if (result == JOptionPane.CLOSED_OPTION) {
                return optionType == JOptionPane.YES_NO_CANCEL_OPTION
                    ? JOptionPane.CANCEL_OPTION
                    : JOptionPane.NO_OPTION;
            }

            return result;
        } catch (HeadlessException e) {
            Log.error("HeadlessException showing confirm dialog: " + e.getMessage());
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
        Log.info(message);

        if (isSuppressed()) {
            return suppressedDefault;
        }

        try {
            return (String) JOptionPane.showInputDialog(
                parent, message, title, JOptionPane.QUESTION_MESSAGE, null, null, null
            );
        } catch (HeadlessException e) {
            Log.error("HeadlessException showing input dialog: " + e.getMessage());
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
        @Nullable Object[] options,
        @Nullable Object initialValue
    ) {
        Log.info(String.valueOf(message));

        if (isSuppressed()) {
            return JOptionPane.CLOSED_OPTION;
        }

        try {
            return JOptionPane.showOptionDialog(
                parent, message, title, optionType, messageType, icon, options, initialValue
            );
        } catch (HeadlessException e) {
            Log.error("HeadlessException showing option dialog: " + e.getMessage());
            return JOptionPane.CLOSED_OPTION;
        }
    }

    // ------------------------------------------------------------------
    // Convenience methods
    // ------------------------------------------------------------------

    /**
     * Confirms overwriting an existing file. Returns true if the file
     * does not exist or the user confirms overwrite. When suppressed,
     * returns true (allows overwrite).
     */
    public static boolean confirmFileOverwrite(
        @Nullable Component parent,
        String title,
        File file
    ) {
        if (!file.exists()) {
            return true;
        }

        var response = showConfirmDialog(
            parent,
            title,
            Strings.get(Strings.CONFIRM_FILE_OVERWRITE, file.getName()),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.YES_OPTION
        );

        return response == JOptionPane.YES_OPTION;
    }

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
            var dialog = pane.createDialog(parent, title);
            positionDialog(dialog, parent);
            UIUtils.addStandardDialogKeyBindings(dialog);
            dialog.setVisible(true);
        } catch (HeadlessException e) {
            Log.error("HeadlessException showing message dialog: " + e.getMessage());
        }
    }

    // Position the dialog at 3/8 of the way down the screen, centered horizontally,
    // clamped to the screen bounds with a 20px margin on all sides.
    private static void positionDialog(@NotNull JDialog dialog, @Nullable Component parent) {
        var screen = getScreenBounds(parent);
        var size = dialog.getSize();

        var x = screen.x + (screen.width - size.width) / 2;
        var y = screen.y + screen.height * 3 / 8 - size.height / 2;

        x = Math.max(screen.x + SCREEN_MARGIN_PX, Math.min(x, screen.x + screen.width - size.width - SCREEN_MARGIN_PX));
        y = Math.max(screen.y + SCREEN_MARGIN_PX, Math.min(y, screen.y + screen.height - size.height - SCREEN_MARGIN_PX));

        dialog.setLocation(x, y);
    }

    private static @NotNull Rectangle getScreenBounds(@Nullable Component parent) {
        Window window = parent instanceof Window w
            ? w
            : parent != null ? SwingUtilities.getWindowAncestor(parent) : null;

        if (window != null) {
            return window.getGraphicsConfiguration().getBounds();
        }

        return GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration()
            .getBounds();
    }

    private static boolean isSuppressed() {
        return GraphicsEnvironment.isHeadless() || suppressDialogs;
    }
}
