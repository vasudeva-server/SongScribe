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

import songscribe.ui.component.MainFrame;

/**
 * The controller behind {@link OtherValueDialog}. Writes one {@link OtherValueComboBox} and
 * nothing else; the prompt opens empty, so there is nothing to read.
 *
 * <p><strong>It touches no document.</strong> The combo it serves is a control inside whatever
 * dialog opened it, so an OK here changes what that combo says and nothing more; the document is
 * written, if at all, when that dialog's own OK commits. Nothing in this class reaches the song,
 * the view or the line, and it will not begin to — a prompt that wrote the score would commit an
 * edit the user has not yet accepted in the dialog they are still standing in.
 *
 * <p><strong>It exists because {@link StandardDialog} takes a {@link DialogOps}, and
 * {@link DialogController#ops()} is the only place one is assembled.</strong> Handing the dialog a
 * {@code DialogOps} built at the call site would work and would be shorter, and it would also be a
 * second route around the one place {@link #read()}'s answer is copied — the guarantee that a
 * dialog cannot reach through what it was shown holds only while every bundle comes from here.
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
    OtherValueController(MainFrame mainFrame, OtherValueComboBox combo) {
        super(mainFrame);
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
     * <p>The combo adds a value its list does not already carry, so a value typed here is selected
     * whether or not it was ever offered — see {@link OtherValueComboBox#setSelectedItem}.
     *
     * @param text the text the user entered, already known to be non-blank
     * @effects selects {@code text} in the combo, adding it to the combo's list when the list does
     *     not already contain it
     */
    @Override
    protected void commit(String text) {
        combo.setSelectedItem(text);
    }
}
