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

import java.util.HashSet;
import songscribe.error.RuntimeError;
import java.util.Set;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.ui.OptionDialogs;

/**
 * Attaches a focus-lost guard to a {@link JTextComponent} that prevents
 * the field from being left blank.
 *
 * <p>Two modes of operation:
 * <ul>
 *   <li><b>With a default value</b> — an option dialog offers the user
 *       a choice between applying the default or continuing to edit.</li>
 *   <li><b>Without a default value</b> — a warning is shown and focus
 *       is returned to the field.</li>
 * </ul>
 */
public class NonEmptyGuard {

    private final JTextComponent field;
    private final Component parent;
    private final String dialogTitle;
    private final String message;
    private final @Nullable String defaultValue;
    private final @Nullable String useDefaultLabel;
    private final @Nullable String continueEditingLabel;
    private final Set<Component> exemptComponents = new HashSet<>();

    /**
     * Creates a guard that offers the user a default value when the
     * field is left blank.
     *
     * @param field               the text component to guard
     * @param parent              parent component for dialog positioning
     * @param dialogTitle         title for the option dialog
     * @param message             message body for the option dialog
     * @param defaultValue        value to apply when the user accepts the default
     * @param useDefaultLabel     label for the "use default" button
     * @param continueEditingLabel label for the "continue editing" button
     */
    public NonEmptyGuard(
        JTextComponent field,
        Component parent,
        String dialogTitle,
        String message,
        String defaultValue,
        String useDefaultLabel,
        String continueEditingLabel
    ) {
        this.field = field;
        this.parent = parent;
        this.dialogTitle = dialogTitle;
        this.message = message;
        this.defaultValue = defaultValue;
        this.useDefaultLabel = useDefaultLabel;
        this.continueEditingLabel = continueEditingLabel;
        install();
    }

    /**
     * Creates a guard that warns the user and returns focus when the
     * field is left blank. No default value is offered.
     *
     * @param field       the text component to guard
     * @param parent      parent component for dialog positioning
     * @param dialogTitle title for the warning dialog
     * @param message     message body for the warning dialog
     */
    public NonEmptyGuard(
        JTextComponent field,
        Component parent,
        String dialogTitle,
        String message
    ) {
        this.field = field;
        this.parent = parent;
        this.dialogTitle = dialogTitle;
        this.message = message;
        this.defaultValue = null;
        this.useDefaultLabel = null;
        this.continueEditingLabel = null;
        install();
    }

    /**
     * Registers a component that should not trigger focus-lost
     * validation. Use this for buttons (e.g. OK, Cancel) whose
     * action listeners perform their own validation.
     */
    public void addExemptComponent(Component component) {
        exemptComponents.add(component);
    }

    private void install() {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (e.isTemporary() || exemptComponents.contains(e.getOppositeComponent())) {
                    return;
                }

                handleFocusLost();
            }
        });
    }

    /**
     * Checks whether the guarded field is non-blank, showing the
     * appropriate dialog if it is blank.
     *
     * @return {@code true} if the field contains non-blank text
     *     (either already present or filled by the user accepting
     *     the default); {@code false} if the field is still blank
     */
    public boolean validate() {
        if (!field.getText().isBlank()) {
            return true;
        }

        if (defaultValue != null) {
            return showDefaultValueDialog();
        } else {
            showWarningAndRefocus();
            return false;
        }
    }

    private void handleFocusLost() {
        validate();
    }

    private boolean showDefaultValueDialog() {
        if (useDefaultLabel == null || continueEditingLabel == null || defaultValue == null) {
            throw RuntimeError.exit("default value labels not initialized");
        }

        var useDefault = useDefaultLabel;
        var continueEditing = continueEditingLabel;
        var defaultVal = defaultValue;
        var useDefaultIndex = 1;

        var result = OptionDialogs.showOptionDialog(
            parent,
            dialogTitle,
            message,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new Object[]{ continueEditing, useDefault },
            continueEditing
        );

        if (result == useDefaultIndex) {
            field.setText(defaultVal);
            return true;
        } else {
            // Closed or "continue editing" -- return focus to the field
            SwingUtilities.invokeLater(field::requestFocusInWindow);
            return false;
        }
    }

    private void showWarningAndRefocus() {
        OptionDialogs.showWarningMessage(parent, dialogTitle, message);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
    }
}
