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
 * nothing else; the prompt opens empty, so there is nothing to read.
 *
 * <p><strong>It touches no document.</strong> The combo it serves is a control inside whatever
 * dialog opened it, so an OK here changes what that combo says and nothing more; the document is
 * written, if at all, when that dialog's own OK commits. A prompt that wrote the score would commit
 * an edit the user has not yet accepted in the dialog they are still standing in.
 *
 * <p>Constructed per gesture, by the combo whose {@code Other…} row was chosen, and discarded with
 * the dialog.
 */
final class OtherValueController extends DialogController<OtherValue, String> {

    private final OtherValueComboBox combo;

    /**
     * @param mainFrame the application window, for parenting the prompt
     * @param combo     the combo the prompt writes its answer back to
     */
    OtherValueController(OtherValueComboBox combo) {
        this.combo = combo;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The combo is not consulted. The prompt is reached by choosing {@code Other…}, which asks
     * for a value the list does not offer, so whatever is selected is by definition not the value
     * being asked for — seeding it would make every use begin by clearing text.
     *
     * @return an empty value, so the prompt opens on an empty field
     */
    @Override
    protected OtherValue read() {
        return new OtherValue("");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refuses a value the combo already shows, so the list never ends up with two rows the user
     * cannot tell apart. Comparison is against what the rows display, which is what makes the
     * {@code (none)} and {@code Other…} labels unavailable as values.
     *
     * @param text the text the user entered, already known to be non-blank
     * @return {@link ValidationResult#valid()} unless some row already shows {@code text}
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
     * {@inheritDoc}
     *
     * @param text the text the user entered, already known to be non-blank and not already in use
     * @effects selects {@code text} in the combo, adding it to the combo's list when the list does
     *     not already contain it — see {@link OtherValueComboBox#setSelectedItem}
     */
    @Override
    protected void commit(String text) {
        combo.setSelectedItem(text);
    }
}
