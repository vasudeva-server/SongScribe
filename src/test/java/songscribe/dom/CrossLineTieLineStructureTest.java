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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.undo.UndoTestSupport;

/**
 * Verifies that adding and removing whole lines keeps a cross-line tie honest.
 * <p>
 * A tie across a line break only means anything while its two lines are neighbors. Two
 * line-level edits break that: deleting one of the two lines, which leaves the other
 * holding half a tie to a line no longer in the song, and inserting a line between them,
 * which pushes the endpoints apart. Both must remove the tie, and both must do it through
 * the tracked path so undo brings the tie back into both lines' span lists.
 * <p>
 * The deleted line keeps its own elements and its own span list — the deletion record
 * holds it intact for undo — so the sweep only takes the tie out of the lines that survive.
 */
class CrossLineTieLineStructureTest extends UnitTest {

    private Song song;

    /**
     * Holds the tie's anchor as its last note. The line's last element is the terminal barline
     * it kept from having been the song's only line, which sits after the anchor and is
     * irrelevant to the line-structure mutations these tests drive.
     */
    private Line firstLine;

    /** Holds the tie's end as its first element. */
    private Line secondLine;

    /** A third line, whose own same-line tie must survive everything done to the others. */
    private Line unrelatedLine;

    private Tie crossLineTie;
    private Tie sameLineTie;

    @BeforeEach
    void setUp() {
        song = new Song();
        firstLine = song.getLine(0);
        secondLine = new Line(song);
        unrelatedLine = new Line(song);

        var anchorNote = ElementType.CROTCHET.newInstance();
        var endNote = ElementType.CROTCHET.newInstance();
        var unrelatedAnchorNote = ElementType.CROTCHET.newInstance();
        var unrelatedEndNote = ElementType.CROTCHET.newInstance();

        crossLineTie = new Tie(anchorNote, endNote);
        sameLineTie = new Tie(unrelatedAnchorNote, unrelatedEndNote);

        song.withoutMutationTracking(() -> {
            firstLine.addElement(ElementType.CROTCHET.newInstance());
            firstLine.addElement(anchorNote);

            song.addLine(secondLine);
            secondLine.addElement(endNote);
            secondLine.addElement(ElementType.CROTCHET.newInstance());

            song.addLine(unrelatedLine);
            unrelatedLine.addElement(unrelatedAnchorNote);
            unrelatedLine.addElement(unrelatedEndNote);

            firstLine.addTie(crossLineTie);
            unrelatedLine.addTie(sameLineTie);
        });
    }

    /** Asserts the state the fixture starts in: three lines, the tie in the first two. */
    private void assertTieSpansTheBreak() {
        assertThat(song.getLines()).containsExactly(firstLine, secondLine, unrelatedLine);
        assertThat(firstLine.getSpans()).containsOnlyOnce(crossLineTie);
        assertThat(secondLine.getSpans()).containsOnlyOnce(crossLineTie);
        assertUnrelatedTieUntouched();
    }

    /**
     * Asserts the tie is in no line's list, including the deleted line's when a deletion is
     * what removed it — a removal takes a cross-line span out of both halves together.
     */
    private void assertTieRemovedEverywhere() {
        assertThat(firstLine.getSpans()).doesNotContain(crossLineTie);
        assertThat(secondLine.getSpans()).doesNotContain(crossLineTie);
        assertUnrelatedTieUntouched();
    }

    /**
     * A tie with both endpoints in one line says nothing about a relationship between two
     * lines, so no line-level edit may disturb it. {@code containsOnlyOnce} also catches a
     * replayed removal being undone twice.
     */
    private void assertUnrelatedTieUntouched() {
        assertThat(unrelatedLine.getSpans()).containsOnlyOnce(sameLineTie);
        assertThat(song.getLines()).contains(unrelatedLine);
    }

    @Test
    void testDeletingTheAnchorLineRemovesTheTieFromTheEndLine() {
        song.removeLine(song.indexOfLine(firstLine));

        assertThat(song.getLines()).containsExactly(secondLine, unrelatedLine);
        assertTieRemovedEverywhere();
    }

    @Test
    void testDeletingTheEndLineRemovesTheTieFromTheAnchorLine() {
        song.removeLine(song.indexOfLine(secondLine));

        assertThat(song.getLines()).containsExactly(firstLine, unrelatedLine);
        assertTieRemovedEverywhere();
    }

    @Test
    void testUndoOfADeletedLineRestoresTheLineAndTheTieIntoBoth() {
        var batch = UndoTestSupport.captureBatch(song, () -> song.removeLine(song.indexOfLine(secondLine)));
        assertTieRemovedEverywhere();

        UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);

        assertTieSpansTheBreak();
    }

    @Test
    void testInsertingALineBetweenTheTiedLinesRemovesTheTie() {
        song.addLine(song.indexOfLine(secondLine), new Line(song));

        assertThat(song.getLines()).hasSize(4);
        assertTieRemovedEverywhere();
    }

    @Test
    void testUndoOfAnInsertedLineRestoresTheTie() {
        var insertedLine = new Line(song);
        var batch = UndoTestSupport.captureBatch(song,
            () -> song.addLine(song.indexOfLine(secondLine), insertedLine));
        assertTieRemovedEverywhere();

        UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);

        assertTieSpansTheBreak();
    }

    @Test
    void testInsertingALineAfterTheTiedPairLeavesTheTieAlone() {
        // Every pair after the insertion point shifts by the same amount, so only the pair
        // the new line lands between can come apart.
        song.addLine(song.indexOfLine(unrelatedLine), new Line(song));

        assertThat(firstLine.getSpans()).containsOnlyOnce(crossLineTie);
        assertThat(secondLine.getSpans()).containsOnlyOnce(crossLineTie);
        assertUnrelatedTieUntouched();
    }

    @Test
    void testUndoRedoUndoOfALineDeletionReturnsToTheSameState() {
        // Replay drives the recorded span removals itself, so the sweep must not fire again
        // during replay — a second sweep would record a removal undo cannot pair off, and a
        // second restore would leave the tie in a line's list twice.
        var batch = UndoTestSupport.captureBatch(song, () -> song.removeLine(song.indexOfLine(secondLine)));
        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertTieSpansTheBreak();

        UndoTestSupport.replayRedo(scoreView, batch);
        assertThat(song.getLines()).containsExactly(firstLine, unrelatedLine);
        assertTieRemovedEverywhere();

        UndoTestSupport.replayUndo(scoreView, batch);
        assertTieSpansTheBreak();
    }

    @Test
    void testUndoRedoUndoOfALineInsertionReturnsToTheSameState() {
        var insertedLine = new Line(song);
        var batch = UndoTestSupport.captureBatch(song,
            () -> song.addLine(song.indexOfLine(secondLine), insertedLine));
        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertTieSpansTheBreak();

        UndoTestSupport.replayRedo(scoreView, batch);
        assertThat(song.getLines()).containsExactly(firstLine, insertedLine, secondLine, unrelatedLine);
        assertTieRemovedEverywhere();

        UndoTestSupport.replayUndo(scoreView, batch);
        assertTieSpansTheBreak();
    }
}
