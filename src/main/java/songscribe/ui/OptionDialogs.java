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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.util.UIUtils;

/**
 * Centralized dialog utility. All user-facing dialogs in the application
 * route through this class, which handles suppression in headless and
 * test contexts.
 *
 * <p><strong>Titles and messages are {@code Strings} keys, not text.</strong> A key is a
 * {@code String}, so passing literal text compiles and then fails to resolve. The two
 * exceptions take already-resolved text: the {@code WithString} error variant, and the button
 * labels of the multi-option dialog.
 *
 * <p>A dismissed confirm window answers {@link Answer#CANCEL} where the dialog offered Cancel,
 * and {@link Answer#NO} otherwise.
 *
 * <p>A suppressed confirm or input prompt answers its suppressed default, so a test whose
 * path depends on the answer passes the default that path needs; otherwise it silently takes
 * the "user said no" branch.
 */
public final class OptionDialogs {

    private static final Logger LOG = LoggerFactory.getLogger(OptionDialogs.class);

    // Extra width added to each JOptionPane message label so FlatLaf's fractional/LCD
    // glyph ink, which extends past the integer advance width, is never clipped.
    private static final int MESSAGE_LABEL_CLIP_PAD = 8;

    private static boolean suppressDialogs = false;

    private OptionDialogs() {}

    public static void setSuppressDialogs(boolean suppress) {
        suppressDialogs = suppress;
    }

    public static void showInfoMessage(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        Object... messageArgs
    ) {
        showMessageDialog(
            parent, Strings.get(titleKey), resolveMessage(messageKey, messageArgs),
            MessageType.INFORMATION, LOG::trace, false
        );
    }

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
        showMessageDialog(parent, title, message, MessageType.ERROR, LOG::error, true);
    }

    public static void showWarningMessage(
        @Nullable Component parent,
        String titleKey,
        String messageKey,
        Object... messageArgs
    ) {
        showMessageDialog(
            parent, Strings.get(titleKey), resolveMessage(messageKey, messageArgs),
            MessageType.WARNING, LOG::warn, false
        );
    }

    public static Answer showConfirmDialog(@Nullable Component parent, Confirm confirm) {
        var title = Strings.get(confirm.titleKey());
        var message = resolveMessage(confirm.messageKey(), confirm.messageArgs());
        LOG.trace(message);

        if (isSuppressed()) {
            return confirm.suppressedDefault();
        }

        try {
            var buttons = confirm.buttons();
            var pane = new JOptionPane(message, confirm.messageType().value, buttons.value);
            showOptionPane(pane, parent, title);

            return pane.getValue() instanceof Integer value ? buttons.answerOf(value) : buttons.dismissedAnswer;
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing confirm dialog", e);
            return confirm.suppressedDefault();
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

    /**
     * @return the index in {@link Choice#buttonLabels()} of the button pressed, or
     *     {@link JOptionPane#CLOSED_OPTION} if the window was dismissed or the dialog suppressed
     */
    public static int showChoiceDialog(@Nullable Component parent, Choice choice) {
        if (isSuppressed()) {
            return JOptionPane.CLOSED_OPTION;
        }

        var title = Strings.get(choice.titleKey());
        var message = resolveMessage(choice.messageKey(), choice.messageArgs());
        LOG.trace("{}", message);

        try {
            var labels = choice.buttonLabels();
            var pane = new JOptionPane(
                message,
                choice.messageType().value,
                JOptionPane.DEFAULT_OPTION,
                null,
                labels.toArray(),
                labels.get(choice.defaultButton())
            );
            showOptionPane(pane, parent, title);

            return pane.getValue() instanceof String label ? labels.indexOf(label) : JOptionPane.CLOSED_OPTION;
        } catch (HeadlessException e) {
            LOG.error("HeadlessException showing choice dialog", e);
            return JOptionPane.CLOSED_OPTION;
        }
    }

    private static void showMessageDialog(
        @Nullable Component parent,
        String title,
        String message,
        MessageType messageType,
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
            var pane = new JOptionPane(message, messageType.value);
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
        // label the first time such a dialog is shown in the session.
        padMessageLabels(pane);

        var dialog = pane.createDialog(parent, title);

        UIUtils.addStandardDialogKeyBindings(dialog);
        UIUtils.positionDialog(dialog, parent);
        dialog.setVisible(true);
        dialog.dispose();
    }

    /**
     * FlatLaf's subpixel glyph ink extends past the integer advance width that BasicLabelUI
     * sizes the labels to, clipping the last character. A measurement cannot reliably recover
     * that overhang on HiDPI, so the padding is a small fixed amount.
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

    private static String resolveMessage(String key, Object[] args) {
        return args.length == 0 ? Strings.get(key) : Strings.get(key, args);
    }

    /** Public so windows not built from {@code JOptionPane} honor the same flag. */
    public static boolean isSuppressed() {
        return GraphicsEnvironment.isHeadless() || suppressDialogs;
    }

    /** The content of a confirm dialog. */
    public record Confirm(
        String titleKey,
        String messageKey,
        ConfirmButtons buttons,
        MessageType messageType,
        Answer suppressedDefault,
        Object... messageArgs
    ) {}

    /** The content of a dialog whose buttons the caller names. */
    public record Choice(
        String titleKey,
        String messageKey,
        MessageType messageType,
        List<String> buttonLabels,
        int defaultButton,
        Object... messageArgs
    ) {
        public Choice {
            buttonLabels = List.copyOf(buttonLabels);
            Objects.checkIndex(defaultButton, buttonLabels.size());
        }
    }

    public enum Answer {
        YES,
        NO,
        CANCEL
    }

    public enum ConfirmButtons {
        YES_NO(JOptionPane.YES_NO_OPTION, Answer.NO),
        YES_NO_CANCEL(JOptionPane.YES_NO_CANCEL_OPTION, Answer.CANCEL);

        private final int value;
        private final Answer dismissedAnswer;

        ConfirmButtons(int value, Answer dismissedAnswer) {
            this.value = value;
            this.dismissedAnswer = dismissedAnswer;
        }

        private Answer answerOf(int optionPaneValue) {
            return switch (optionPaneValue) {
                case JOptionPane.YES_OPTION -> Answer.YES;
                case JOptionPane.NO_OPTION -> Answer.NO;
                case JOptionPane.CANCEL_OPTION -> Answer.CANCEL;
                default -> dismissedAnswer;
            };
        }
    }

    public enum MessageType {
        ERROR(JOptionPane.ERROR_MESSAGE),
        INFORMATION(JOptionPane.INFORMATION_MESSAGE),
        WARNING(JOptionPane.WARNING_MESSAGE),
        QUESTION(JOptionPane.QUESTION_MESSAGE);

        private final int value;

        MessageType(int value) {
            this.value = value;
        }
    }
}
