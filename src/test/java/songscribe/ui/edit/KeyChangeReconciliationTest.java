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

package songscribe.ui.edit;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.layout.AccidentalReconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The mutation half of a key-moving edit: removing the mid-line key changes the move strands, each
 * with the element it is paired with.
 *
 * <p>Every reached line is described the same way, so the ordinary sweep is one walk over the
 * reach and needs no notion of which line the edit itself landed on. What does need a distinction
 * is <em>when</em>: a paste goes on to move the elements of the line it lands on, so that line's
 * ranges name indices the paste is about to invalidate. It defers that one line and sweeps it
 * afterwards at the indices its elements moved to.
 *
 * <p>Each fixture line runs in {@link #KEY_BEFORE} and holds a key change to {@link #LINE_KEY},
 * which is a real change until the reach re-keys the line to {@link #LINE_KEY} and strands it —
 * the only way such a line can arise, since a key change restating the key a line already runs in
 * is a state no document reaches.
 */
class KeyChangeReconciliationTest extends UnitTest {

    /** The key a fixture line is re-keyed to, and the one its key change then restates. */
    private static final Key LINE_KEY = Key.NO_ACCIDENTALS;

    /** The key a fixture line runs in beforehand, so its key change is a real change. */
    private static final Key KEY_BEFORE = Key.ONE_SHARP;

    /** Where the pair stands on a reached line: right after the first note. */
    private static final int REACH_PAIR_BARLINE_INDEX = 1;

    /** Where a deferred line's ranges say the pair stands, before the edit moved it. */
    private static final int DEFERRED_PAIR_BARLINE_INDEX = 4;

    /** How many elements the edit is supposed to have inserted ahead of those ranges. */
    private static final int INDEX_SHIFT = 2;

    /** Where the pair actually stands once the edit has moved it. */
    private static final int SHIFTED_PAIR_BARLINE_INDEX = DEFERRED_PAIR_BARLINE_INDEX + INDEX_SHIFT;

    /** The index an insertion that inserts nothing is anchored at. */
    private static final int INSERT_INDEX = 1;

    /**
     * A line and the notes on it, so a sweep can be asserted against exactly what should survive.
     *
     * @param line  the line
     * @param notes its notes, in order, with no barline or key change among them
     */
    private record PairedLine(Line line, List<StaffElement> notes) {}

    /**
     * A line in {@link #KEY_BEFORE} holding notes, then a barline and a key change to
     * {@link #LINE_KEY} at {@code barlineIndex}, then one more note.
     *
     * @param barlineIndex the index the barline stands at, and so the first index of the pair
     * @return the line and its notes
     */
    private static PairedLine lineWithPairAt(int barlineIndex) {
        var line = detachedLine();
        var notes = new ArrayList<StaffElement>();

        line.setKey(KEY_BEFORE);

        for (var index = 0; index < barlineIndex; index++) {
            var note = crotchet();

            notes.add(note);
            line.addElement(note);
        }

        line.addElement(singleBarline());
        line.addElement(keyChange(LINE_KEY));

        var trailing = crotchet();

        notes.add(trailing);
        line.addElement(trailing);

        return new PairedLine(line, notes);
    }

    /** The elements standing on {@code line}, in order, for an identity comparison. */
    private static List<StaffElement> elementsOf(Line line) {
        var standing = new ArrayList<StaffElement>();

        for (var index = 0; index < line.effectiveElementCount(); index++) {
            standing.add(line.getElement(index));
        }

        return standing;
    }

    private static KeyChangeReconciliation.Confirmed confirmed(
        List<AccidentalReconciliation.ReachedLine> reach) {

        return new KeyChangeReconciliation.Confirmed(
            AccidentalRestatements.Decision.PROCEED, reach, List.of(), null);
    }

    /**
     * A reached line that inserts and removes nothing of its own, carrying only {@code stranded} —
     * ranges naming indices other than the ones its pair actually stands at, which is the state a
     * paste's own line is in once the insertion has moved it.
     */
    private static AccidentalReconciliation.ReachedLine reachedStranding(
        Line line, List<StaffElementRun.EffectiveRange> stranded) {

        return AccidentalReconciliation.ReachedLine.receiving(
            line,
            new AccidentalReconciliation.Insertion(
                INSERT_INDEX, null, AccidentalReconciliation.ArrivingElements.NONE),
            stranded);
    }

    @Test
    void testSweepingRemovesTheStrandedPairFromEveryReachedLine() {
        var first = lineWithPairAt(REACH_PAIR_BARLINE_INDEX);
        var second = lineWithPairAt(REACH_PAIR_BARLINE_INDEX);

        confirmed(List.of(
            AccidentalReconciliation.ReachedLine.reKeyed(first.line(), LINE_KEY),
            AccidentalReconciliation.ReachedLine.reKeyed(second.line(), LINE_KEY)))
            .sweep();

        assertThat(elementsOf(first.line())).containsExactlyElementsOf(first.notes());
        assertThat(elementsOf(second.line())).containsExactlyElementsOf(second.notes());
    }

    @Test
    void testADeferredLineIsLeftStandingUntilItsRangesAreShiftedToWhereItsElementsMoved() {
        var swept = lineWithPairAt(REACH_PAIR_BARLINE_INDEX);
        var deferred = lineWithPairAt(SHIFTED_PAIR_BARLINE_INDEX);
        var deferredBefore = elementsOf(deferred.line());

        var reconciliation = confirmed(List.of(
            AccidentalReconciliation.ReachedLine.reKeyed(swept.line(), LINE_KEY),
            reachedStranding(deferred.line(), List.of(new StaffElementRun.EffectiveRange(
                DEFERRED_PAIR_BARLINE_INDEX, DEFERRED_PAIR_BARLINE_INDEX + 1)))));

        reconciliation.sweepExcept(deferred.line());

        assertThat(elementsOf(swept.line())).containsExactlyElementsOf(swept.notes());
        assertThat(elementsOf(deferred.line()))
            .as("the deferred line's ranges name pre-edit indices, so removing them now would take "
                + "the wrong elements")
            .containsExactlyElementsOf(deferredBefore);

        reconciliation.sweepDeferred(deferred.line(), INDEX_SHIFT);

        assertThat(elementsOf(deferred.line()))
            .as("shifted by what the edit moved those elements by, the ranges name the pair again")
            .containsExactlyElementsOf(deferred.notes());
    }
}
