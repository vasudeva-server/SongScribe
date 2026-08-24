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

import songscribe.util.Copyable;

/**
 * The other end of a {@link DialogOps}: everything one dialog's operations need the document for.
 *
 * <p>The access a dialog gave up is legitimate here. This is not a window — it holds whatever its
 * dialog edits, and it can be asked every one of its questions with nothing on screen. That is what
 * makes the decisions in a dialog's operation testable: they are all on this side, and none of
 * them is behind a widget.
 *
 * <p><strong>It holds no application window.</strong> A controller that resolves the open document
 * — rather than being handed the line and element it edits — extends
 * {@link DocumentDialogController}, which is where the window and the document accessors live. Two
 * controllers need that; the rest are constructed around what they edit and would only be handed a
 * window to ignore.
 *
 * <p><strong>The dialog never sees an instance of this.</strong> It sees the four function
 * references {@link #ops()} assembles, and cannot reach the receiver behind them — see
 * {@link DialogOps} for why those are function references rather than one narrow interface.
 *
 * <p><strong>Bound to what it edits when it is constructed.</strong> A controller whose answers
 * depend on something the user pointed at — an element, an insertion point — is constructed for
 * that gesture and discarded with the dialog. A controller serving a dialog reached from a cached
 * action holds nothing of the document and resolves it in {@link #read()}, which is asked afresh
 * on every opening.
 *
 * @param <I> what the dialog shows, answered by {@link #read()}. {@link Copyable}, because
 *            {@link #ops()} copies it on the way out — see there. Its bound permits
 *            {@code @Nullable} itself, for a family like {@code AttachmentDialogController} whose
 *            input is absent on Add
 * @param <O> what the dialog's controls say on OK, passed to {@link #validate} and {@link #commit}
 */
public abstract class DialogController<I extends @Nullable Copyable<I>, O> {

    /**
     * Reads what the dialog should show.
     *
     * <p>Called once per opening, before the window appears, and once more on OK for any
     * controller whose {@link #dataWasModified} compares against it — so an implementation stays
     * cheap and must not assume it is asked only once. Reads only: the document is left exactly as
     * it was.
     *
     * <p><strong>It may return the document's own object.</strong> Keeping the dialog off it is
     * {@link #ops()}'s job and is done for every controller at once, so an implementation here
     * reads what it holds and copies nothing.
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
     * <p>Called on an OK that changes something, always with the values {@link #commit} will
     * receive if it accepts. An OK the notator changed nothing before never reaches here: there is
     * no proposed change to judge, so {@link #dataWasModified} closes the dialog ahead of this.
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
     * Whether {@code values} say anything the document does not already hold.
     *
     * <p>The first question OK asks, ahead of both {@link #validate} and {@link #commit}:
     * answering {@code false} closes the dialog having written nothing, recorded no undo step and
     * left the document clean. It comes first because values that change nothing are not a
     * proposed change — there is nothing for {@code validate} to judge, and judging anyway would
     * let a rule about a change the notator never made refuse to let them out of the dialog. The
     * OK button itself stays available throughout: refusing to write is this controller's job, not
     * a button state.
     *
     * <p>Asked once per OK. Reads only: the document is left exactly as it was.
     *
     * @param values the values the dialog's controls describe
     * @return {@code true} when committing them would change something
     * @implSpec Answers {@code true}: every commit is performed, whatever the controls say.
     *           Override where the controller can compare {@code values} against what the document
     *           holds. A comparison is only meaningful where {@code I} and {@code O} describe the
     *           same thing — for a controller whose input and output are different shapes, the
     *           default is the honest answer, and a partial no-op guard belongs in {@link #commit}
     *           per write, as {@code SongSettingsController} does.
     */
    protected boolean dataWasModified(O values) {
        return true;
    }

    /**
     * Commits {@code values} to whatever this dialog's OK writes to.
     *
     * <p>Called only after {@link #dataWasModified} answered that these values change something
     * and {@link #validate} answered a valid result for them, so an implementation takes both as
     * preconditions rather than re-deriving either. An implementation that writes the document
     * does so through {@link DocumentDialogController#withModification}, so the whole commit is
     * one undo step.
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
     * Assembles this controller's operations into the bundle its dialog is constructed with.
     *
     * <p>Final, and the only assembly point, so no subclass can hand a dialog a bundle that is
     * partial, that mixes controllers, or that routes {@code commit} past the {@link #validate}
     * this controller declared. A controller varies what the dialog gets by overriding the
     * operations, never by rewiring the bundle.
     *
     * <p>Public because the handoff is the point: whoever opens the dialog constructs the
     * controller and passes this to the constructor, and openers are not all in this package —
     * {@code Actions} registers the cached menu actions from {@code songscribe.ui.action}. Handing
     * the bundle out costs nothing, since it carries function references and no route back to the
     * receiver behind them.
     *
     * <p><strong>What the dialog is shown is a copy.</strong> {@link #read()} may answer the
     * document's own object; what goes into the bundle is {@link Copyable#copy()} of it, so a
     * dialog editing what it was given cannot reach the document through it. Doing it here rather
     * than in each {@link #read()} is what makes it true of every dialog rather than of the ones
     * whose author remembered — and the {@link Copyable} bound on {@code I} is what stops a type
     * that cannot copy itself from being an input at all.
     *
     * @return a bundle carrying exactly this controller's {@link #read()} — copied — together with
     *         its {@link #dataWasModified}, {@link #validate}, {@link #commit} and
     *         {@link #removal()}
     */
    public final DialogOps<I, O> ops() {
        return new DialogOps<>(
            this::readCopy, this::dataWasModified, this::validate, this::commit, removal());
    }

    /**
     * @return {@link #read()}'s answer, copied; {@code null} where it answered null, which for the
     *         attachment family is an element carrying no change yet
     */
    private I readCopy() {
        var values = read();

        // Returns values rather than the null literal: NullAway reads a bare type variable as
        // non-null whatever its bound permits, so a literal null here is rejected while the same
        // null travelling inside I is not. See the nullable-I section of .claude/guides/dialogs.md.
        return values == null ? values : values.copy();
    }
}
