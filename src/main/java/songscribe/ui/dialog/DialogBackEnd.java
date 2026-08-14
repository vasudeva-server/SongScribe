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

/**
 * The domain half of a dialog: everything the dialog would otherwise have reached into the score
 * to do. A dialog holds one, already bound to the document state it acts on, and thereby needs no
 * access to that state itself.
 *
 * <p><strong>What the dialog keeps and what it gives up.</strong> The dialog owns its controls,
 * their initial values, their layout and what its buttons say. It gives up querying and mutating
 * anything outside itself. A dialog may still name the domain's <em>static knowledge</em> — note
 * durations, tempo types, alignments, the value records built from them — because knowing what a
 * crotchet is is not the same as holding a handle on a document. What it may not name is live
 * state: a {@code Song}, a {@code Line}, a {@code StaffElement}, a {@code ScoreView}. Those reach
 * only through here.
 *
 * <p><strong>Who binds it.</strong> The free functions behind this interface are written in the
 * shape {@code apply(Song, input)} — the domain object first, so they read and test as domain
 * operations. A dialog calling one directly would need the {@code Song}, which is the coupling
 * this interface removes. The resolution is that the <em>caller that opens the dialog</em> binds
 * the document state and hands over a back end that already holds it: the free function keeps its
 * shape and the dialog still sees no document type.
 * {@link songscribe.ui.dialog.AttachmentEditor} is the worked example.
 *
 * <p><strong>Not every dialog has one.</strong> A dialog with no OK button has no
 * gather-validate-apply cycle to put a boundary across. {@code PreferencesDialog} applies each
 * edit the moment it is made and is decoupled instead by the notification each write to
 * {@code Prefs} posts; giving it a back end would be machinery modelling a cycle it does not have.
 *
 * @param <I> the record of gathered control values — the dialog's output on OK, and this back
 *            end's input. Values only: never a handle on the document
 */
public interface DialogBackEnd<I> {

    /**
     * Decides whether {@code input} may be applied. Displays nothing and changes nothing.
     *
     * <p>Deciding and telling the user are separate on purpose. A back end that showed its own
     * alert could not be called without a live window on screen, which is what makes a fused
     * decide-and-display method testable only by mocking the UI around it. This method answers;
     * the dialog presents.
     *
     * <p>Called before every {@link #apply}, and never with values {@code apply} will not see.
     *
     * @param input the values gathered from the dialog's controls
     * @return {@link ValidationResult#valid()} when the input may be applied, otherwise a result
     *         naming every reason it may not
     * @implSpec Accepts everything. Most dialogs have nothing to refuse — values assembled from
     *           combos, radios and bounded spinners cannot be wrong, so the whole domain is
     *           acceptable and an override would only say so at length. Override where the input
     *           genuinely can be bad: free text that has to parse, a range the controls do not
     *           enforce, or a rule spanning fields that no single control can check.
     */
    default ValidationResult validate(I input) {
        return ValidationResult.valid();
    }

    /**
     * Commits {@code input} to the document.
     *
     * <p>Called only after {@link #validate} answered a valid result for these same values, so an
     * implementation takes their validity as a precondition rather than re-deriving it.
     *
     * <p>The whole commit is <strong>one undoable step</strong>: an implementation opens exactly
     * one modification bracket however many fields it touches, so a single Undo returns the
     * document to its state before OK. See {@code docs/mutations.md}.
     *
     * @param input the values gathered from the dialog's controls, already validated
     */
    void apply(I input);
}
