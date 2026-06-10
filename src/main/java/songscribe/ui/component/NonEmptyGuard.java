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

import songscribe.error.RuntimeError;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

/**
 * An {@link InputVerifier} for a {@link JTextComponent} that prevents the
 * field from being left blank. Install it with
 * {@code field.setInputVerifier(new NonEmptyGuard(field, ...))}. While the
 * field is blank it refuses to yield focus, so a button that triggers it
 * (e.g. an OK button) will not fire. To let a button bypass the guard
 * (e.g. Cancel), call {@code button.setVerifyInputWhenFocusTarget(false)}.
 *
 * <p>Two modes of operation:
 * <ul>
 *   <li><b>With a default value</b> — an option dialog offers the user
 *       a choice between applying the default or continuing to edit.</li>
 *   <li><b>Without a default value</b> — a warning is shown and focus
 *       is kept on the field.</li>
 * </ul>
 */
public class NonEmptyGuard extends InputVerifier {

    private final JTextComponent field;
    private final Component parent;
    private final String dialogTitleKey;
    private final String messageKey;
    private final @Nullable String defaultValueKey;
    private final @Nullable String useDefaultLabelKey;
    private final @Nullable String continueEditingLabelKey;

    /**
     * Creates a guard that offers the user a default value when the
     * field is left blank.
     *
     * @param field                the text component to guard
     * @param parent               parent component for dialog positioning
     * @param dialogTitleKey       strings key for the option dialog title
     * @param messageKey           strings key for the option dialog message body
     * @param defaultValueKey      strings key for the value to apply when the user accepts the default
     * @param useDefaultLabelKey   strings key for the "use default" button label
     * @param continueEditingLabelKey strings key for the "continue editing" button label
     */
    public NonEmptyGuard(
        JTextComponent field,
        Component parent,
        String dialogTitleKey,
        String messageKey,
        String defaultValueKey,
        String useDefaultLabelKey,
        String continueEditingLabelKey
    ) {
        this.field = field;
        this.parent = parent;
        this.dialogTitleKey = dialogTitleKey;
        this.messageKey = messageKey;
        this.defaultValueKey = defaultValueKey;
        this.useDefaultLabelKey = useDefaultLabelKey;
        this.continueEditingLabelKey = continueEditingLabelKey;
    }

    /**
     * Creates a guard that warns the user and keeps focus when the
     * field is left blank. No default value is offered.
     *
     * @param field          the text component to guard
     * @param parent         parent component for dialog positioning
     * @param dialogTitleKey strings key for the warning dialog title
     * @param messageKey     strings key for the warning dialog message body
     */
    public NonEmptyGuard(
        JTextComponent field,
        Component parent,
        String dialogTitleKey,
        String messageKey
    ) {
        this.field = field;
        this.parent = parent;
        this.dialogTitleKey = dialogTitleKey;
        this.messageKey = messageKey;
        defaultValueKey = null;
        useDefaultLabelKey = null;
        continueEditingLabelKey = null;
    }

    @Override
    public boolean verify(JComponent input) {
        return !field.getText().isBlank();
    }

    @Override
    public boolean shouldYieldFocus(JComponent source, JComponent target) {
        if (verify(source)) {
            return true;
        }

        if (defaultValueKey != null) {
            return showDefaultValueDialog();
        }

        // Returning false keeps focus on the field; show the reason first.
        OptionDialogs.showWarningMessage(parent, dialogTitleKey, messageKey);
        return false;
    }

    private boolean showDefaultValueDialog() {
        if (useDefaultLabelKey == null || continueEditingLabelKey == null || defaultValueKey == null) {
            throw RuntimeError.exit("default value labels not initialized");
        }

        var useDefault = Strings.get(useDefaultLabelKey);
        var continueEditing = Strings.get(continueEditingLabelKey);
        var defaultVal = Strings.get(defaultValueKey);
        var useDefaultIndex = 1;

        var result = OptionDialogs.showOptionDialog(
            parent,
            dialogTitleKey,
            messageKey,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new Object[]{ continueEditing, useDefault },
            continueEditing
        );

        if (result == useDefaultIndex) {
            field.setText(defaultVal);
            return true;
        }

        // Closed or "continue editing" -- keep focus on the field
        return false;
    }
}
