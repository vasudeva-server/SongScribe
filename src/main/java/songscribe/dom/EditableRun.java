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

package songscribe.dom;

import java.util.List;

import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementRangeDeletion;

/**
 * A {@link LyricRun} whose elements can be inserted and removed, and that repairs what such an
 * edit breaks: the lyric chains running through the edit's neighbors and the glissando left
 * pointing at nothing.
 *
 * <p>Three primitives are what an implementor supplies — {@link #addElement}, {@link #removeElement}
 * and {@link #removeRange} — and they are structural only: they put an element into the run or
 * take one out and repair nothing around it. The edits an editor performs, {@link #insertRun},
 * {@link #deleteRange} and {@link #deleteRanges}, are built here on top of them together with
 * {@link LyricRun}'s chain repairs, so the sequence of repairs around an insertion or a deletion
 * is written once and every run edits by it.
 *
 * <p>This interface promises nothing about undo. Whether an edit is recorded, and what it is
 * recorded into, is the implementor's: a {@link Line}, the one run that is edited, posts each
 * primitive and each repair through its song's modification bracket, and its primitives state
 * that rule and what they throw. The edits here inherit it from the primitives they call.
 */
public interface EditableRun extends LyricRun {

    // -------------------------------------------------------------------------
    // What a run must answer for itself
    // -------------------------------------------------------------------------

    /**
     * Inserts {@code element} at {@code index}, shifting the elements from {@code index} on one
     * place to the right. Structural only: repairs nothing around the insertion.
     *
     * @param index   the position {@code element} will occupy, {@code 0..}{@link #elementCount()}
     * @param element the element to insert
     * @throws IndexOutOfBoundsException if {@code index} is negative or greater than
     *     {@link #elementCount()}
     */
    void addElement(int index, StaffElement element);

    /**
     * Removes the element at {@code index}. Structural only: repairs nothing around the removal.
     *
     * @throws IndexOutOfBoundsException if no element stands at {@code index}
     */
    void removeElement(int index);

    /**
     * Removes every element in the contiguous {@code range}, both ends included. Structural only:
     * repairs nothing around the removal.
     *
     * @throws IndexOutOfBoundsException if {@code range} reaches past {@link #elementCount()}
     */
    void removeRange(ElementRange range);

    // -------------------------------------------------------------------------
    // Edits
    // -------------------------------------------------------------------------

    /**
     * Inserts {@code run} at {@code index} as one run: adds the elements in order and repairs
     * the seams the run breaks on either side.
     *
     * <p><b>This is the whole of a multi-element insertion, and it exists so that no caller
     * assembles one out of parts.</b> The steps are not independent — {@link
     * LyricRun#repairNeighborsBeforeInsertion} reads pre-insertion indices, so it must run before
     * the first {@link #addElement}, and {@link LyricRun#repairNeighborsAfterInsertion} keys its
     * two halves off opposite ends of the run, so it must run after the last. A caller that
     * open-codes the sequence can leave out a step, and leaving one out shows up as a glissando
     * pointing at a barline or a melisma pointing at an element that is no longer the host,
     * neither of which fails anywhere near the insertion.
     *
     * @param index the index the first element lands at, {@code 0..}{@link #elementCount()}
     * @param run the elements to insert, in the order they land; must not be empty
     * @effects inserts the elements and applies the neighbor repairs to this run, each through
     *     the primitives and {@link #modifyElement}, so an implementor that records gets one
     *     record per step
     */
    default void insertRun(int index, List<? extends StaffElement> run) {
        repairNeighborsBeforeInsertion(index, run);

        for (var i = 0; i < run.size(); i++) {
            addElement(index + i, run.get(i));
        }

        repairNeighborsAfterInsertion(index, run);
    }

    /**
     * Deletes the elements {@code range} covers and repairs what the removal leaves behind: the
     * x-offsets of the elements that close the gap, an incoming glissando whose target is going,
     * and the syllable relations and melisma extends of the neighbors.
     * <p>
     * This is the deletion an edit performs. {@link #removeRange} and {@link #removeElement} are
     * the primitives beneath it, and repair nothing.
     * <p>
     * Deleting several ranges on one run means working from the last to the first: removing an
     * earlier range shifts the indices of every later one. {@link #deleteRanges} discharges that.
     * <p>
     * Sweeps no stranded key change and asks nothing. A deletion that moves a key owes a stranding
     * sweep over every line that key reaches, and this line's share of it names the indices it was
     * computed at, so the caller runs it before calling this.
     *
     * @param effectiveRange the range to delete
     * @effects removes the elements through one {@link #removeRange}, or one
     *     {@link #removeElement} per element when a paired grace note at the range's head makes
     *     the removal non-contiguous, and applies the neighbor repairs through
     *     {@link #modifyElement}; an implementor that records gets one record per step
     */
    default void deleteRange(EffectiveRange effectiveRange) {
        var range = effectiveRange.range();

        // A paired grace note at the head of the range hands its syllable back to the note it
        // decorated, and a pair further along the range does the same — neither of which a single
        // range removal expresses. Fall back to the per-element loop, which re-derives each
        // element's own pairing as it reaches it.
        if (isPairedGraceNote(range.begin())) {
            deleteRangeElementwise(range.begin(), range.end());
            return;
        }

        // The whole range is removed here, both ends included. The grace-note case is the other
        // branch's business, so what reaches this one is a range widened over a breath mark, or
        // over the barline a key change sits behind.
        var rangeBegin = range.begin();
        var rangeEnd = range.end();

        // Recorded like every other change here — stripping it raw would leave undo restoring the
        // deleted elements but not the glissando.
        removeGlissandoInto(rangeBegin);

        // Adjust syllable relations and melisma extends on neighbors before removing. Both helpers
        // require the target elements to still be present in the list.
        adjustSyllablesForNeighborChange(rangeBegin - 1, getElement(rangeBegin));

        // When the range was widened to include a trailing breath mark or a paired barline, this
        // loop also runs over it. Neither carries lyrics, so adjustExtendsForDeletion is a
        // harmless no-op for them.
        for (var i = rangeBegin; i <= rangeEnd; i++) {
            adjustExtendsForDeletion(i);
        }

        removeRange(range);
    }

    /**
     * Deletes every range in {@code ranges} through {@link #deleteRange}, working from the last to
     * the first so that no removal invalidates the indices of one still to come.
     *
     * <p>The ordering is the obligation {@link #deleteRange} states and
     * {@link StaffElementRun#redundantKeyChangeRanges(Key)} repeats, discharged once here rather
     * than at each caller: a caller that walks the list forward corrupts every range after the
     * first, and nothing in the types says so.
     *
     * @param ranges the ranges to delete, ascending and pairwise disjoint
     * @effects one {@link #deleteRange} per range, last to first
     */
    default void deleteRanges(List<EffectiveRange> ranges) {
        for (var i = ranges.size() - 1; i >= 0; i--) {
            deleteRange(ranges.get(i));
        }
    }

    /**
     * Deletes {@code begin} through {@code end} one element at a time, for the range whose removal
     * is non-contiguous. The bounds are already widened; each element's own pairing is re-derived
     * by {@link #deleteElement} as the loop reaches it.
     */
    private void deleteRangeElementwise(int begin, int end) {
        for (var i = end; i >= begin; i--) {
            var removedCount = deleteElement(i);

            // When deleteElement also removes a preceding paired grace note,
            // skip the extra index so we don't process an already-removed element.
            i -= (removedCount - 1);
        }
    }

    /**
     * Deletes the element at {@code index} and, if the preceding element is a paired grace note,
     * removes that as well. After the primary removal, if the surviving element at the deletion's
     * left edge is a breath mark, it is cascade-deleted through a recursive call so all gap-fill,
     * glissando, and syllable/extend logic is reused.
     *
     * @return the number of elements removed (1 or 2), not counting any cascade-deleted trailing
     *     breath mark
     */
    private int deleteElement(int index) {
        // If the preceding note is a paired grace note, it becomes orphaned when
        // this note is deleted and must be removed along with it.
        var hasPrecedingPairedGraceNote = isHostOfPairedGraceNote(index);

        // Determine the left edge of the deletion — if a paired grace note precedes
        // the deleted note, it is also being removed, so the gap starts there.
        var firstDeletedIndex = hasPrecedingPairedGraceNote ? index - 1 : index;

        // firstDeletedIndex is the deletion's left edge, so it is the index a glissando
        // reaching in from the element before it targets.
        removeGlissandoInto(firstDeletedIndex);

        // Adjust syllable relations and melisma extends before removing —
        // both methods require the element at index to still be in the list.
        // This must run before the hand-back below: it decides whether to break the
        // predecessor's word by reading the deleted element's own lyric, which the
        // transfer would have already moved away.
        adjustSyllablesForNeighborChange(firstDeletedIndex - 1, getElement(index));

        // Deleting a paired grace note on its own hands its syllable back to the host,
        // which becomes an ordinary note again and is eligible to carry a lyric. Runs
        // before adjustExtendsForDeletion so it sees the final lyric state: the transfer
        // takes the melisma START off the grace and drops the host's STOP carrier, so
        // there is no longer a chain to unwind.
        if (isPairedGraceNote(index)) {
            transferLyrics(index, index + 1);
        }

        adjustExtendsForDeletion(index);

        // Remove the host note first (higher index), then the orphaned grace note.
        // Removing the higher index first keeps index - 1 valid.
        removeElement(index);

        int removed;

        if (hasPrecedingPairedGraceNote) {
            removeElement(index - 1);
            removed = 2;
        } else {
            removed = 1;
        }

        // Cascade-delete a breath mark that immediately follows the deleted element.
        // After removal the successor lands at firstDeletedIndex. Recurse through
        // deleteElement (not a bare removeElement) so gap-fill, glissando strip, and
        // syllable/extend adjustments are reused. The cascade is excluded from
        // `removed` because deleteRangeElementwise's loop counts down, so the breath
        // mark (a higher index, visited on an earlier iteration) is already accounted
        // for and must not shift the loop's index a second time.
        if (isBreathMarkAt(firstDeletedIndex)) {
            deleteElement(firstDeletedIndex);
        }

        return removed;
    }
}
