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

package songscribe.layout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;

/**
 * Applies the {@link AccidentalReconciliation.Materialization}s of one edit, gated on that edit
 * being accepted, and guarantees that a refusal leaves the line exactly as it was.
 *
 * <h2>Why a gate is needed at all</h2>
 * An edit that inserts elements is refused when the result no longer fits the line, and the
 * refusal must mutate nothing — the caller's modification bracket has to stay empty and no
 * notification may be posted. But the fit gate has to <em>measure</em> the materialized
 * accidentals: {@code ElementColumnBuilder} derives element extents including accidental width and
 * {@link LayoutEngine} treats accidental widths as a layout input, so the accidentals must already
 * be on the notes when the projected column chain is built. Applying before measuring and
 * measuring before deciding therefore pull against each other, and this class is the one place
 * that resolves them.
 *
 * <h2>The sequence</h2>
 * <ol>
 *   <li>Apply every materialization with plain {@link StaffElement#setAccidental}, saving each
 *       surviving note's prior accidental first. The plain setter records nothing — mutation
 *       recording happens only through {@link Line#modifyElement} — so nothing is undoable yet.
 *   <li>Run the gate. Materializations landing on {@code detachedElements} — clones or a preview
 *       element not yet added to the line — need no saving: they carry no line back-reference, so
 *       setting their accidental mutates nothing recordable and there is nothing to roll back.
 *   <li>On refusal, put the saved accidentals back with the same plain setter and report that
 *       nothing was committed. The line reads exactly as it did on entry.
 *   <li>On acceptance, put them back and immediately re-apply each through
 *       {@link Line#modifyElement} with {@link ElementField#ACCIDENTAL}, which captures a
 *       before/after pair so undo can reverse it.
 * </ol>
 *
 * <p>Callers must invoke this <b>before</b> any removal their edit performs, so the recorded
 * indices are pre-deletion: {@code UndoController} replays a step's mutations in reverse, so undo
 * reaches these last, once the removal has been undone and the notes are back at the indices
 * {@code modifyElement} recorded them at.
 *
 * <p>{@link ElementField#ACCIDENTAL} is not duration-affecting, so {@code modifyElement}'s tuplet
 * cleanup does not fire.
 *
 * <p>An edit with no fit gate — a deletion, which can only ever make the line narrower — passes a
 * gate that always accepts, so that the "nothing is mutated on refusal" contract still has exactly
 * one implementation.
 */
public final class AccidentalMaterializer {

    private AccidentalMaterializer() {
        // Prevent instantiation - utility class with static methods only
    }

    /**
     * A note on the line whose accidental was made explicit, with everything needed to put it back
     * or to re-record it.
     *
     * @param index      The note's index on the line, captured before any removal
     * @param note       The note itself
     * @param prior      The accidental it carried before the materialization
     * @param accidental The accidental the materialization gives it
     */
    private record SavedAccidental(
        int index,
        StaffElement note,
        StaffElement.@Nullable Accidental prior,
        StaffElement.Accidental accidental
    ) {
    }

    /**
     * Applies {@code materializations}, runs {@code accepted}, and either commits the accidentals
     * as recorded mutations or restores the line to its entry state. See the class javadoc for the
     * full sequence and for why the gate has to run with the accidentals already applied.
     *
     * <p>Must be called inside a modification bracket, and before any removal the same edit
     * performs.
     *
     * @param line              The line being edited, in its pre-mutation state
     * @param materializations  The accidentals to make explicit, from
     *                          {@link AccidentalReconciliation}
     * @param detachedElements  Elements not yet on the line — clones or a preview element — whose
     *                          materializations need neither saving nor recording
     * @param accepted          The edit's own gate, run with the accidentals applied; false means
     *                          the edit is refused and nothing may be left behind
     * @return True when the edit was accepted and the accidentals were committed
     */
    public static boolean materializeIfAccepted(
        Line line,
        List<AccidentalReconciliation.Materialization> materializations,
        List<StaffElement> detachedElements,
        BooleanSupplier accepted) {

        var saved = apply(line, materializations, detachedElements);

        if (!accepted.getAsBoolean()) {
            restore(saved);
            return false;
        }

        // The accidentals are already on the notes, set plainly so the gate could measure them.
        // Now that the edit is committing they also have to be undoable, so put each note back and
        // re-apply the same value through modifyElement, which records a before/after pair.
        restore(saved);

        for (var savedAccidental : saved) {
            line.modifyElement(
                savedAccidental.index(),
                EnumSet.of(ElementField.ACCIDENTAL),
                () -> savedAccidental.note().setAccidental(savedAccidental.accidental()));
        }

        return true;
    }

    /**
     * Applies every materialization with the plain setter, which records nothing, and returns the
     * ones that landed on notes already on the line so they can be restored or re-recorded.
     */
    private static List<SavedAccidental> apply(
        Line line,
        List<AccidentalReconciliation.Materialization> materializations,
        List<StaffElement> detachedElements) {

        var detachedIdentities = new IdentityHashMap<StaffElement, Boolean>();

        for (var element : detachedElements) {
            detachedIdentities.put(element, Boolean.TRUE);
        }

        var saved = new ArrayList<SavedAccidental>();

        for (var materialization : materializations) {
            var note = materialization.note();
            var accidental = materialization.accidental();

            if (!detachedIdentities.containsKey(note)) {
                saved.add(new SavedAccidental(
                    line.getElementIndex(note), note, note.getAccidental(), accidental));
            }

            note.setAccidental(accidental);
        }

        return saved;
    }

    /**
     * Puts each note's pre-materialization accidental back with a plain setter, so nothing is
     * recorded and the line reads exactly as it did before {@link #apply}.
     */
    private static void restore(List<SavedAccidental> saved) {
        for (var savedAccidental : saved) {
            savedAccidental.note().setAccidental(savedAccidental.prior());
        }
    }
}
