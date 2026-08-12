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

import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

/**
 * A {@link StandardDialog} whose OK commits values read from its controls.
 *
 * <p><strong>The values validated are the values committed.</strong> OK reads the controls exactly
 * once, and hands that one value first to {@link #validate} and then to {@link #commit}. Neither
 * step can therefore see controls the other did not, and neither can be reached without the other
 * having run: this class is the only caller of all three, and {@link #commitOnOk()} is final. What
 * a dialog would otherwise have to remember to do — read the controls a second time before
 * committing — it now cannot forget, and cannot get wrong.
 *
 * <p><strong>Nothing is committed when validation refuses.</strong> A refused OK leaves the
 * document untouched and the dialog up with its controls exactly as the user left them, so the
 * failure can be corrected and OK pressed again. Cancel is unaffected: it never reaches this class.
 *
 * <p><strong>Deciding and telling the user are separate.</strong> {@link #validate} answers and
 * displays nothing; this class does the displaying, for every dialog, through
 * {@link OptionDialogs}. A dialog that shows its own validation alert has fused the two again and
 * is the defect this seam removes — see {@link DialogBackEnd#validate}.
 *
 * <p>A dialog whose OK commits nothing extends {@link StandardDialog} directly. There is nothing
 * for it to gather, so there is no value for this class's promises to be about.
 *
 * @param <I> the values gathered from the controls: the dialog's output on OK, and the input to
 *            whatever commits it. Values only — never a handle on the document
 */
public abstract class CommitDialog<I> extends StandardDialog {

    protected CommitDialog(MainFrame mainFrame, String title) {
        super(mainFrame, title);
    }

    protected CommitDialog(MainFrame mainFrame, String title, boolean isModal) {
        super(mainFrame, title, isModal);
    }

    protected CommitDialog(MainFrame mainFrame, String title, boolean isModal, DialogCategory category) {
        super(mainFrame, title, isModal, category);
    }

    /**
     * Reads the controls and returns the values they currently describe.
     *
     * <p>Reads only: calling it leaves the controls untouched. It is total over every state the
     * controls can reach through the UI — there is no combination of selections for which an
     * implementation may decline to produce a value. Whether the values are acceptable is
     * {@link #validate}'s question, not this one's.
     *
     * <p>Called exactly once per OK, before validation.
     *
     * @return the values described by the controls as they now stand
     */
    protected abstract I gather();

    /**
     * Decides whether {@code values} may be committed. Displays nothing and changes nothing.
     *
     * <p>Called on every OK, always with the values {@link #commit} will receive if it answers a
     * valid result. Presenting the failures is this class's job, not an implementation's.
     *
     * @param values the values just gathered from the controls
     * @return {@link ValidationResult#valid()} when the values may be committed, otherwise a result
     *         naming every reason they may not, most important first
     * @implSpec Accepts everything. Most dialogs have nothing to refuse — values assembled from
     *           combos, radios and bounded spinners cannot be wrong. Override where the values
     *           genuinely can be bad, typically by delegating to a {@link DialogBackEnd}.
     */
    protected ValidationResult validate(I values) {
        return ValidationResult.valid();
    }

    /**
     * Commits {@code values} — to the document through a {@link DialogBackEnd}, or to whatever else
     * this dialog's OK writes to.
     *
     * <p>Called only after {@link #validate} answered a valid result for these same values, so an
     * implementation takes their validity as a precondition rather than re-deriving it. Called
     * before the window is dismissed, and only when it is about to be.
     *
     * @param values the values gathered from the controls, already validated
     */
    protected abstract void commit(I values);

    /**
     * {@inheritDoc}
     *
     * <p>Gathers once, validates, and commits only what validation accepted. When it refuses, the
     * user is shown the <strong>first</strong> failure and the dialog stays up: stacking one modal
     * alert per failure is worse than under-reporting, and {@link ValidationResult} promises the
     * failures arrive in the order they should be read, so the first is the one to lead with. A
     * valid result carries no failures and shows nothing.
     *
     * <p>Final: the order of the three steps, and the fact that all three see the same values, are
     * this class's promises rather than each dialog's. A dialog varies them by overriding
     * {@link #gather}, {@link #validate} and {@link #commit}.
     *
     * @return {@code true} once {@link #commit} has run, {@code false} when validation refused and
     *         the user has been told why
     */
    @Override
    protected final boolean commitOnOk() {
        var values = gather();
        var result = validate(values);

        if (!result.isValid()) {
            var failure = result.failures().getFirst();
            var message = failure.message();
            OptionDialogs.showErrorMessage(
                contentPanel, failure.titleKey(), message.key(), message.args().toArray()
            );

            return false;
        }

        commit(values);
        return true;
    }
}
