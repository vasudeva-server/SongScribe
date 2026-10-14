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
 * It can be asked every one of its questions with nothing on screen.
 *
 * <p><strong>It holds no application window.</strong> A controller that resolves the open
 * document, rather than being handed what it edits, extends {@link DocumentDialogController}.
 *
 * <p><strong>The dialog never sees an instance of this</strong>, only the function references
 * {@link #ops()} assembles.
 *
 * <p><strong>Bound to what it edits when it is constructed.</strong> A controller whose answers
 * depend on something the user pointed at is constructed for that gesture and discarded with the
 * dialog. One serving a dialog reached from a cached action holds nothing of the document and
 * resolves it in {@link #read()}.
 *
 * @param <I> what the dialog shows. Its bound permits {@code @Nullable} itself, for a family like
 *            {@code AttachmentDialogController} whose input is absent on Add
 * @param <O> what the dialog's controls say on OK
 */
public abstract class DialogController<I extends @Nullable Copyable<I>, O extends @Nullable Object> {

    /**
     * Asked once per opening and once more on OK for any controller whose
     * {@link #dataWasModified} compares against it, so it stays cheap. Reads only.
     *
     * <p>It may return the document's own object; {@link #ops()} copies it.
     */
    protected abstract I read();

    /**
     * Displays nothing and changes nothing; {@link StandardDialog} presents. Called only on an OK
     * that changes something, always with the values {@link #commit} will receive if it accepts.
     *
     * @return {@link ValidationResult#valid()} when the values may be committed, otherwise every
     *         reason they may not, most important first
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
     * Called only after {@link #dataWasModified} answered that these values change something and
     * {@link #validate} accepted them, so an implementation takes both as preconditions. One that
     * writes the document does so through {@link DocumentDialogController#withModification}.
     */
    protected abstract void commit(O values);

    /**
     * Asked once, when {@link #ops()} assembles the bundle, so the answer decides whether a
     * Remove button is built at all. A controller whose answer depends on the document is
     * therefore constructed for the gesture it serves.
     *
     * @return the removal to run, or {@code null} when this dialog has no Remove button
     */
    protected @Nullable Runnable removal() {
        return null;
    }

    /**
     * Final, and the only assembly point, so no subclass can hand a dialog a partial bundle, one
     * that mixes controllers, or one that routes {@code commit} past {@link #validate}.
     *
     * <p>Public because openers are not all in this package.
     *
     * <p><strong>What the dialog is shown is a copy:</strong> {@link Copyable#copy()} of what
     * {@link #read()} answered, so a dialog editing it cannot reach the document.
     */
    public final DialogOps<I, O> ops() {
        return new DialogOps<>(
            this::readCopy, this::dataWasModified, this::validate, this::commit, removal());
    }

    /**
     * @return {@link #read()}'s answer, copied; {@code null} where it answered null
     */
    private I readCopy() {
        var values = read();

        // Returns values rather than the null literal: NullAway reads a bare type variable as
        // non-null whatever its bound permits, so a literal null here is rejected while the same
        // null travelling inside I is not. The same shape serves any generic method that passes a
        // nullable type variable through.
        return values == null ? values : values.copy();
    }
}
