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

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.undo.UndoTestSupport;

/**
 * Verifies that a span's parentage is derived from its endpoints rather than stored.
 * <p>
 * A span is never attached to a line, so its inherited {@code parentLine} stays
 * {@code null} for its whole life and {@link Span#isIn} answers from the lines its two
 * endpoints sit in. That is what lets a tie spanning a line break be in both lines at
 * once — in both of their {@code spans} lists, so each line's sweeps and layout see the
 * half that belongs to it.
 */
class SpanParentageTest extends UnitTest {

    private Song song;
    private Line firstLine;
    private Line secondLine;

    /** The tie's anchor: the last note of the first line. */
    private StaffElement anchorNote;

    /** The tie's end: the first note of the second line. */
    private StaffElement endNote;

    @BeforeEach
    void setUp() {
        song = new Song();
        firstLine = song.getLine(0);
        secondLine = new Line(song);
        anchorNote = crotchet();
        endNote = crotchet();

        song.withoutMutationTracking(() -> {
            firstLine.addElement(crotchet());
            firstLine.addElement(anchorNote);
            song.addLine(secondLine);
            secondLine.addElement(endNote);
            secondLine.addElement(crotchet());
        });
    }

    private Tie crossLineTie() {
        return new Tie(anchorNote, endNote);
    }

    /** Asserts that {@code tie} is in both lines by both the derived answer and the lists. */
    private void assertInBothLines(Tie tie) {
        assertThat(tie.isIn(firstLine)).isTrue();
        assertThat(tie.isIn(secondLine)).isTrue();
        assertThat(firstLine.getSpans()).containsOnlyOnce(tie);
        assertThat(secondLine.getSpans()).containsOnlyOnce(tie);
    }

    /**
     * Asserts that {@code tie} is in neither line's list. The derived answer is not asserted
     * here: both endpoints survive a span removal, so {@code isIn} keeps saying yes — list
     * membership is the whole of what a removal changes.
     */
    private void assertInNeitherList(Tie tie) {
        assertThat(firstLine.getSpans()).doesNotContain(tie);
        assertThat(secondLine.getSpans()).doesNotContain(tie);
    }

    @Test
    void testCrossLineTieIsInBothOfItsEndpointLines() {
        var tie = crossLineTie();

        song.withoutMutationTracking(() -> firstLine.addTie(tie));

        assertInBothLines(tie);
    }

    @Test
    void testCrossLineTieAddedFromTheSecondLineIsStillInBoth() {
        // Which line the addition is called on cannot matter: parentage comes from the
        // endpoints, and appendChild fills in whichever line of the pair is not the receiver.
        var tie = crossLineTie();

        song.withoutMutationTracking(() -> secondLine.addTie(tie));

        assertInBothLines(tie);
    }

    @Test
    void testRemovingACrossLineTieFromEitherLineTakesItOutOfBoth() {
        var tie = crossLineTie();

        song.withoutMutationTracking(() -> firstLine.addTie(tie));
        song.withoutMutationTracking(() -> secondLine.removeTie(tie));

        assertInNeitherList(tie);
    }

    @Test
    void testSpanNeverHasAParentLine() {
        var tie = crossLineTie();
        assertThat(tie.getParentLine()).isNull();

        song.withoutMutationTracking(() -> firstLine.addTie(tie));
        assertThat(tie.getParentLine()).isNull();

        song.withoutMutationTracking(() -> firstLine.removeTie(tie));
        assertThat(tie.getParentLine()).isNull();
    }

    @Test
    void testTieWithDetachedEndpointsIsInNoLineYetStaysInTheListItWasAddedTo() {
        // The invariant is one-directional — isIn(L) implies L.spans holds the span, not the
        // converse — so a span whose endpoints are in no line stays where it was put.
        var tie = new Tie(crotchet(), crotchet());

        song.withoutMutationTracking(() -> firstLine.addTie(tie));

        assertThat(tie.isIn(firstLine)).isFalse();
        assertThat(tie.isIn(secondLine)).isFalse();
        assertThat(firstLine.getSpans()).containsOnlyOnce(tie);
        assertThat(secondLine.getSpans()).doesNotContain(tie);
    }

    @Test
    void testDetachingOneEndpointDropsThatLineFromTheDerivedAnswer() {
        var tie = crossLineTie();
        song.withoutMutationTracking(() -> firstLine.addTie(tie));

        song.withoutMutationTracking(() -> firstLine.removeElement(firstLine.getElementIndex(anchorNote)));

        assertThat(tie.isIn(firstLine)).isFalse();
        assertThat(tie.isIn(secondLine)).isTrue();
    }

    @Test
    void testUndoAndRedoOfACrossLineTieAdditionKeepBothListsInStep() {
        var tie = crossLineTie();
        var batch = UndoTestSupport.captureBatch(song, () -> firstLine.addTie(tie));
        assertInBothLines(tie);

        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertInNeitherList(tie);

        UndoTestSupport.replayRedo(scoreView, batch);
        assertInBothLines(tie);
    }

    @Test
    void testUndoAndRedoOfACrossLineTieRemovalKeepBothListsInStep() {
        var tie = crossLineTie();
        song.withoutMutationTracking(() -> firstLine.addTie(tie));

        var batch = UndoTestSupport.captureBatch(song, () -> firstLine.removeTie(tie));
        assertInNeitherList(tie);

        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertInBothLines(tie);

        UndoTestSupport.replayRedo(scoreView, batch);
        assertInNeitherList(tie);
    }
}
