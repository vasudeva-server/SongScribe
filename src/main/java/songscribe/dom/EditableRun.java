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

/**
 * A {@link LyricRun} whose elements can be inserted and removed, and that repairs what such an
 * edit breaks.
 *
 * <p>An implementor supplies two structural primitives, {@link #addElement} and
 * {@link #removeRange}; {@link #insertRun}, {@link #deleteRange} and {@link #deleteRanges} are
 * built on them here, so the sequence of repairs is written once.
 *
 * <p>This interface promises nothing about undo: whether an edit is recorded, and into what, is
 * the implementor's.
 */
public interface EditableRun extends LyricRun {

    /**
     * Structural only: repairs nothing around the insertion.
     *
     * @param index the position {@code element} will occupy, {@code 0..}{@link #elementCount()}
     */
    void addElement(int index, StaffElement element);

    /**
     * Structural only: repairs nothing around the removal.
     */
    void removeRange(ElementRange range);

    default void removeElement(int index) {
        removeRange(ElementRange.single(index));
    }

    /**
     * Inserts {@code run} at {@code index} as one run: adds the elements in order and repairs
     * the seams the run breaks on either side.
     *
     * <p><b>This is the whole of a multi-element insertion, and it exists so that no caller
     * assembles one out of parts.</b> The steps are not independent — {@link
     * LyricRun#repairNeighborsBeforeInsertion} reads pre-insertion indices, so it must run before
     * the first {@link #addElement}, and {@link LyricRun#repairNeighborsAfterInsertion} reads
     * the run's first element where it landed, so it must run after the last. A caller that
     * open-codes the sequence can leave out a step, and leaving one out shows up as a glissando
     * pointing at a barline or a word hyphenated into an empty note, neither of which fails
     * anywhere near the insertion.
     *
     * @param index the index the first element lands at, {@code 0..}{@link #elementCount()}
     * @param run the elements to insert, in the order they land; must not be empty
     * @invariant {@code index} satisfies {@link #canInsertElementAt}, so no insertion lands
     *     between a mid-line key change and its barline, or between a paired grace note and
     *     its host
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
     * Deletes the elements {@code range} covers and repairs what the removal leaves behind: an
     * incoming glissando whose target is going, and the words and melismas the range ran
     * through.
     * <p>
     * A syllable goes with its note, a paired grace note's syllable included: deleting the grace
     * note alone leaves its host an ordinary note with no lyric.
     * <p>
     * The range is the one {@link StaffElementRun#effectiveRange} widened, so the breath mark
     * directly after the caller's elements goes with them. Nothing past the widened range goes:
     * no path puts a second breath mark behind the first, since a breath mark stands only
     * directly after a duration element.
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
     * @invariant in every script, a word that lost syllables to the range closes up around the
     *     gap: one that lost its end ends on the syllable before the range, one that lost its
     *     beginning begins on the syllable after it, one that lost only middle syllables stays
     *     one word, and one left with a single syllable is a single-syllable word
     * @invariant in every script, a melisma that lost its syllable to the range loses its
     *     extender, and the notes it ran under are left with no lyric; one that lost only notes
     *     under its extender closes on the last note that remains under it, and one left with
     *     none is an ordinary syllable
     * @invariant an element holding no lyric is no neighbor to a word or a melisma: deleting a
     *     rest, a barline, a breath mark or a key change changes no lyric, and one standing
     *     beside the range does not stop the two repairs above
     * @invariant the note in front of the range keeps no glissando into it
     * @effects removes the elements through one {@link #removeRange} and applies the repairs
     *     through {@link #modifyElement}; an implementor that records gets one record per step
     */
    default void deleteRange(EffectiveRange effectiveRange) {
        var range = effectiveRange.range();
        var scriptsWithWordEnd = scriptsWithWordEndIn(range);

        // Recorded like every other change here — stripping it raw would leave undo restoring the
        // deleted elements but not the glissando.
        removeGlissandoInto(range.begin());

        removeRange(range);
        repairChainsAfterDeletion(range.begin(), scriptsWithWordEnd);
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
}
