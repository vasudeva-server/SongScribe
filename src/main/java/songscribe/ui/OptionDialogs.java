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

import java.util.Locale;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.util.UIUtils;

/**
 * Centralized dialog utility. All user-facing dialogs in the application
 * route through this class, which handles suppression in headless and
 * test contexts.
 */
public final class OptionDialogs {

    private static final Logger LOG = LoggerFactory.getLogger(OptionDialogs.class);

    // Extra width added to each JOptionPane message label so FlatLaf's fractional/LCD
    // glyph ink, which extends past the integer advance width, is never clipped (#440).
    private static final int MESSAGE_LABEL_CLIP_PAD = 8;

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
        String titleKey,
        String messageKey,
        Object... messageArgs
    ) {
        showMessageDialog(
            parent, Strings.get(titleKey), resolveMessage(messageKey, messageArgs),
            JOptionPane.INFORMATION_MESSAGE, LOG::trace, false
        );
    }

    /**
     * Shows an error message dialog. Beeps before showing the dialog
     * (unless suppressed).
     */
    public static void showErrorMessage(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        Object... messageArgs
    ) {
        showErrorMessageWithString(parent, Strings.get(titleKey), resolveMessage(messageKey, messageArgs));
    }

    public static void showErrorMessageWithString(
        @Nullable Component parent,
        String title,
        String message
    ) {
        showMessageDialog(parent, title, message, JOptionPane.ERROR_MESSAGE, LOG::error, true);
    }

    public static void showWarningMessage(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        Object... messageArgs
    ) {
        showMessageDialog(
            parent, Strings.get(titleKey), resolveMessage(messageKey, messageArgs),
            JOptionPane.WARNING_MESSAGE, LOG::warn, false
        );
    }

    public static int showConfirmDialog(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        int optionType,
        int messageType
    ) {
        return showConfirmDialog(parent, titleKey, messageKey, optionType, messageType, JOptionPane.NO_OPTION);
    }

    public static int showConfirmDialog(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        int optionType,
        int messageType,
        int suppressedDefault
    ) {
        var title = Strings.get(titleKey);
        var message = Strings.get(messageKey);
        LOG.trace(message);

        if (isSuppressed()) {
            return suppressedDefault;
        }

        try {
            var pane = new JOptionPane(message, messageType, optionType);
            showOptionPane(pane, parent, title);

            var value = pane.getValue();
            var result = value instanceof Integer i ? i : JOptionPane.CLOSED_OPTION;

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
        String titleKey,
        String messageKey
    ) {
        return showInputDialog(parent, titleKey, messageKey, null);
    }

    public static @Nullable String showInputDialog(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        @Nullable String suppressedDefault
    ) {
        var title = Strings.get(titleKey);
        var message = Strings.get(messageKey);
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
        String titleKey,
        String messageKey,
        int optionType,
        int messageType,
        @Nullable Icon icon,
        Object @Nullable [] options,
        @Nullable Object initialValue,
        Object... messageArgs
    ) {
        if (isSuppressed()) {
            return JOptionPane.CLOSED_OPTION;
        }

        var title = Strings.get(titleKey);
        var message = resolveMessage(messageKey, messageArgs);
        LOG.trace("{}", message);

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
        Consumer<? super String> logger,
        boolean beep
    ) {
        logger.accept(message);

        if (isSuppressed()) {
            return;
        }

        if (beep) {
            UIUtils.beep();
        }

        try {
            var pane = new JOptionPane(message, messageType);
            showOptionPane(pane, parent, title);
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing message dialog", e);
        }
    }

    private static void showOptionPane(JOptionPane pane, @Nullable Component parent, String title) {
        // Pad the message labels BEFORE the dialog is created. createDialog packs the pane
        // exactly once, at whatever the labels then prefer, so padding here folds the clip
        // compensation into that single pack. Padding after createDialog instead would need
        // a second pack, which races the not-yet-settled native peer and clips the widest
        // label the first time such a dialog is shown in the session (#459).
        padMessageLabels(pane);

        var dialog = pane.createDialog(parent, title);

        UIUtils.addStandardDialogKeyBindings(dialog);
        UIUtils.positionDialog(dialog, parent);
        dialog.setVisible(true);
        dialog.dispose();
    }

    /**
     * Pads each message label's width so the final glyph is never clipped.
     *
     * <p>JOptionPane bursts a plain-text message into JLabels that BasicLabelUI
     * sizes to the integer advance width. FlatLaf paints with fractional/LCD
     * subpixel metrics whose glyph ink extends a couple pixels past that advance,
     * clipping the last character (issue #440). A render-context measurement can't
     * reliably recover that overhang on a HiDPI screen (the realized context is 2x,
     * so its pixel bounds are device pixels), so instead pad by a small fixed
     * amount — the slack is invisible trailing space on left-aligned message text.
     */
    private static void padMessageLabels(Container container) {
        for (var component : container.getComponents()) {
            if (component instanceof JLabel label) {
                var text = label.getText();

                // Skip icon-only labels (null/empty text) and HTML labels, which
                // wrap and measure themselves correctly and need no padding.
                if (text != null && !text.isEmpty() && !text.toLowerCase(Locale.ROOT).startsWith("<html")) {
                    var current = label.getPreferredSize();
                    var padded = new Dimension(current.width + MESSAGE_LABEL_CLIP_PAD, current.height);

                    label.setPreferredSize(padded);
                    label.setMinimumSize(padded);

                    // The message area is a vertical BoxLayout, which clamps each child's
                    // width to its maximum size; without this the label stays at its old
                    // width even though the Box grew. invalidate busts the layout's cached
                    // sizes so the subsequent pack picks up the new width.
                    label.setMaximumSize(padded);
                    label.invalidate();
                }
            }

            if (component instanceof Container child) {
                padMessageLabels(child);
            }
        }
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

    private static String resolveMessage(String key, Object[] args) {
        return args.length == 0 ? Strings.get(key) : Strings.get(key, args);
    }

    /**
     * Whether a window that blocks for the user would go unanswered — either the
     * environment has no display, or a test asked for suppression.
     * <p>
     * Public so that windows outside this class (which are not built from
     * {@code JOptionPane} and so cannot route through the methods above) honor the
     * same flag rather than declaring a second one that tests would have to know
     * about separately.
     */
    public static boolean isSuppressed() {
        return GraphicsEnvironment.isHeadless() || suppressDialogs;
    }
}
