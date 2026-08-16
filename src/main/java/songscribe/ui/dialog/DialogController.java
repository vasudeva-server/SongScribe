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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * The other end of a {@link DialogOps}: everything one dialog's operations need the document for.
 *
 * <p>The access a dialog gave up is legitimate here. This is not a window — it holds the score,
 * the view and whatever else its dialog edits, and it can be asked all four of its questions with
 * nothing on screen. That is what makes the decisions in a dialog's operation testable: they are
 * all on this side, and none of them is behind a widget.
 *
 * <p><strong>The dialog never sees an instance of this.</strong> It sees the four function
 * references {@link #ops()} assembles, and cannot reach the receiver behind them — see
 * {@link DialogOps} for why that is four references rather than one narrow interface.
 *
 * <p><strong>Bound to what it edits when it is constructed.</strong> A controller whose answers
 * depend on something the user pointed at — an element, an insertion point — is constructed for
 * that gesture and discarded with the dialog. A controller serving a dialog reached from a cached
 * action holds only the {@link MainFrame} and resolves the document in {@link #read()}, which is
 * asked afresh on every opening.
 *
 * @param <I> what the dialog shows, answered by {@link #read()}
 * @param <O> what the dialog's controls say on OK, passed to {@link #validate} and {@link #commit}
 */
public abstract class DialogController<I, O> {

    private final MainFrame mainFrame;

    /**
     * @param mainFrame the application window, which is also how the open document is reached
     */
    protected DialogController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * @return the application window, for parenting an alert a commit has to raise before it can
     *         proceed — not for reaching the document, which the accessors below do directly
     */
    protected final MainFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * @return the view onto the open document, for the two document-wide settings that are written
     *         through the view rather than onto the song
     */
    protected final ScoreView requireScoreView() {
        return mainFrame.requireScoreView();
    }

    /**
     * @return the song now open, which is a different song each time a document is opened — so a
     *         controller that outlives one document asks again rather than holding the answer
     */
    protected final Song getSong() {
        return requireScoreView().getSong();
    }

    /**
     * Runs {@code mutator} against the open song inside one modification bracket named
     * {@code label}.
     *
     * <p>However many fields {@code mutator} touches, the whole of it is <strong>one undoable
     * step</strong> and posts one {@code SongDidChangeNotification}. Nesting is safe: a mutator
     * that opens further brackets still produces one step, per {@code docs/mutations.md}.
     *
     * @param label   the undo-step name, already resolved to display text, as it will read in the
     *                Edit menu after {@code Undo}
     * @param mutator the change to make
     */
    protected final void withModification(String label, Runnable mutator) {
        getSong().withModification(label, mutator);
    }

    /**
     * Reads what the dialog should show.
     *
     * <p>Called once per opening, before the window appears, and never while it is up. Reads only:
     * the document is left exactly as it was, and what comes back holds no live handle on it, so a
     * dialog editing the returned value cannot reach the document through it.
     *
     * @return the values to show, as the document now stands
     */
    protected abstract I read();

    /**
     * Decides whether {@code values} may be committed. Displays nothing and changes nothing.
     *
     * <p>Deciding and telling the user are separate on purpose: an implementation that showed its
     * own alert could not be called without a window on screen, which is exactly what makes a
     * fused decide-and-display method testable only by mocking the UI around it. This answers;
     * {@link StandardDialog} presents.
     *
     * <p>Called on every OK, always with the values {@link #commit} will receive if it accepts.
     *
     * @param values the values just gathered from the dialog's controls
     * @return {@link ValidationResult#valid()} when the values may be committed, otherwise a
     *         result naming every reason they may not, most important first
     * @implSpec Accepts everything. Most dialogs have nothing to refuse — values assembled from
     *           combos, radios and bounded spinners cannot be wrong. Override where the values
     *           genuinely can be bad: free text that has to parse, a range the controls do not
     *           enforce, or a rule spanning controls that no single one can check.
     */
    protected ValidationResult validate(O values) {
        return ValidationResult.valid();
    }

    /**
     * Commits {@code values} to whatever this dialog's OK writes to.
     *
     * <p>Called only after {@link #validate} answered a valid result for these same values, so an
     * implementation takes their validity as a precondition rather than re-deriving it. An
     * implementation that writes the document does so through {@link #withModification}, so the
     * whole commit is one undo step.
     *
     * @param values the values gathered from the dialog's controls, already validated
     */
    protected abstract void commit(O values);

    /**
     * What the dialog's Remove button does, or nothing when it offers none.
     *
     * <p>Asked once, when {@link #ops()} assembles the bundle, and never again — so the answer
     * decides whether a Remove button is built at all, not merely whether it is enabled. A
     * controller whose answer depends on the document is therefore constructed for the gesture it
     * serves, which is what keeps the button off the screen when there is nothing to take away.
     *
     * @return the removal to run, or {@code null} when this dialog has no Remove button
     * @implSpec Answers {@code null}. Remove is a framework affordance rather than a property of
     *           any one dialog family; override to offer it.
     */
    protected @Nullable Runnable removal() {
        return null;
    }

    /**
     * Assembles this controller's four operations into the bundle its dialog is constructed with.
     *
     * <p>Final, and the only assembly point, so no subclass can hand a dialog a bundle that is
     * partial, that mixes controllers, or that routes {@code commit} past the {@link #validate}
     * this controller declared. A controller varies what the dialog gets by overriding the four
     * operations, never by rewiring the bundle.
     *
     * <p>Public because the handoff is the point: whoever opens the dialog constructs the
     * controller and passes this to the constructor, and openers are not all in this package —
     * {@code Actions} registers the cached menu actions from {@code songscribe.ui.action}. Handing
     * the bundle out costs nothing, since it carries four function references and no route back to
     * the receiver behind them.
     *
     * @return a bundle carrying exactly this controller's {@link #read()}, {@link #validate},
     *         {@link #commit} and {@link #removal()}
     */
    public final DialogOps<I, O> ops() {
        return new DialogOps<>(this::read, this::validate, this::commit, removal());
    }
}
