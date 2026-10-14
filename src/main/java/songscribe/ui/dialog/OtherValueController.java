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

import songscribe.Strings;
import songscribe.ui.component.MainFrame;

/**
 * The controller behind {@link OtherValueDialog}. Writes one {@link OtherValueComboBox} and
 * nothing else.
 *
 * <p><strong>It touches no document</strong>: a prompt that wrote the score would commit an edit
 * the user has not yet accepted in the dialog they are still standing in.
 *
 * <p>Constructed per gesture, by the combo whose {@code Other…} row was chosen, and discarded with
 * the dialog.
 */
final class OtherValueController extends DialogController<OtherValue, String> {

    private final OtherValueComboBox combo;

    OtherValueController(OtherValueComboBox combo) {
        this.combo = combo;
    }

    /**
     * The combo is not consulted: whatever is selected is by definition not the value being asked
     * for, so seeding it would make every use begin by clearing text.
     */
    @Override
    protected OtherValue read() {
        return new OtherValue("");
    }

    /**
     * Refuses a value the combo already shows, so the list never has two rows the user cannot
     * tell apart; the {@code (none)} and {@code Other…} labels are unavailable as values.
     *
     * @param text the text the user entered, already known to be non-blank
     */
    @Override
    protected ValidationResult validate(String text) {
        if (combo.isValueInUse(text)) {
            return ValidationResult.invalid(new ValidationFailure(
                Strings.ALERT_TITLE_INVALID_ENTRY,
                new LocalizedMessage(Strings.ERROR_VALUE_IN_USE)
            ));
        }

        return ValidationResult.valid();
    }

    /**
     * @param text the text the user entered, already known to be non-blank and not already in use
     */
    @Override
    protected void commit(String text) {
        combo.setSelectedItem(text);
    }
}
