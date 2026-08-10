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
import static songscribe.dom.StaffElementFactory.minim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.mutation.TieRemoval;
import songscribe.undo.UndoTestSupport;

/**
 * Verifies that deleting or replacing an endpoint of a tie whose two notes sit in
 * different lines keeps both lines in step.
 * <p>
 * A cross-line tie is one object in two lines' {@code spans} lists, so the deletion
 * sweeps in {@link Line#removeElement}, {@link Line#removeRange} and
 * {@link Line#setElement} can each see it from either side. The risks these tests
 * guard are the two ways that can go wrong: a removal that clears only the line that
 * swept it, leaving the other line holding a tie with a deleted endpoint; and a second
 * sweep recording a second removal mutation for a tie the first sweep already took out,
 * which would make one deletion need two undo presses to restore.
 */
class CrossLineTieDeletionTest extends UnitTest {

    private static final int NOTES_PER_LINE = 3;

    /** The tie's anchor is the last element of the first line, and its last note. */
    private static final int ANCHOR_INDEX = NOTES_PER_LINE - 1;

    /** The tie's end is the first note of the second line. */
    private static final int END_INDEX = 0;

    /** A range in the first line that swallows the anchor together with the note before it. */
    private static final int RANGE_FROM = ANCHOR_INDEX - 1;

    private Song song;
    private Line firstLine;
    private Line secondLine;
    private StaffElement anchorNote;
    private StaffElement endNote;
    private Tie tie;

    @BeforeEach
    void setUp() {
        song = new Song();
        firstLine = song.getLine(0);
        secondLine = new Line(song);

        song.withoutMutationTracking(() -> {
            // Fill the first line while it is still the song's last, so each note is inserted
            // before its auto-maintained terminal barline rather than appended after it. Adding
            // secondLine first would make firstLine a non-last line, Line.addElement would stop
            // recognizing that barline as the terminal, and the notes would land behind it,
            // leaving the anchor mid-line with a note trailing it.
            for (var i = 0; i < NOTES_PER_LINE; i++) {
                firstLine.addElement(crotchet());
            }

            song.addLine(secondLine);

            // Drop the barline the first line inherited from being the song's last. Nothing
            // maintains it here — the terminal hand-off runs only when mutation tracking is
            // live — and a line closed by a FINAL_DOUBLE_BARLINE is one no cross-line tie can
            // leave, since Tie.isLegalSeparator excludes it and RangeQueries.boundaryTieAt
            // therefore stops the walk there. Removing it leaves the anchor genuinely last.
            firstLine.removeElement(NOTES_PER_LINE);

            for (var i = 0; i < NOTES_PER_LINE; i++) {
                secondLine.addElement(crotchet());
            }
        });

        anchorNote = firstLine.getElement(ANCHOR_INDEX);
        endNote = secondLine.getElement(END_INDEX);
        tie = new Tie(anchorNote, endNote);

        song.withoutMutationTracking(() -> firstLine.addTie(tie));
    }

    /** Asserts that the tie is in both lines' lists, once each. */
    private void assertTieInBothLines() {
        assertThat(firstLine.getSpans()).containsOnlyOnce(tie);
        assertThat(secondLine.getSpans()).containsOnlyOnce(tie);
    }

    /** Asserts that the tie is in neither line's list. */
    private void assertTieInNeitherLine() {
        assertThat(firstLine.getSpans()).doesNotContain(tie);
        assertThat(secondLine.getSpans()).doesNotContain(tie);
    }

    @Test
    void testDeletingTheAnchorNoteRemovesTheTieFromBothLines() {
        song.withModification(() -> firstLine.removeElement(ANCHOR_INDEX));

        assertTieInNeitherLine();
    }

    @Test
    void testDeletingTheEndNoteRemovesTheTieFromBothLines() {
        // The far line's sweep is the one that only sees the tie because parentage is
        // derived from the endpoints; before that it would not have been in this list.
        song.withModification(() -> secondLine.removeElement(END_INDEX));

        assertTieInNeitherLine();
    }

    @Test
    void testRangeDeletingOverTheAnchorRemovesTheTieFromBothLines() {
        song.withModification(() -> firstLine.removeRange(RANGE_FROM, ANCHOR_INDEX));

        assertTieInNeitherLine();
    }

    @Test
    void testDeletingANoteThatIsNotAnEndpointLeavesTheTieInBothLines() {
        // The second line now sweeps a tie it does not own the anchor of. Its own
        // deletion of an unrelated note must not take that tie with it.
        song.withModification(() -> secondLine.removeElement(NOTES_PER_LINE - 1));

        assertTieInBothLines();
    }

    @Test
    void testReplacingTheAnchorNoteRepointsTheTieInBothLines() {
        var replacement = sameSoundingNote();

        song.withModification(() -> firstLine.setElement(ANCHOR_INDEX, replacement));

        assertTieInBothLines();
        assertThat(tie.getAnchorElement()).isSameAs(replacement);
        assertThat(tie.getEndElement()).isSameAs(endNote);
    }

    @Test
    void testReplacingTheEndNoteRepointsTheTieInBothLines() {
        var replacement = sameSoundingNote();

        song.withModification(() -> secondLine.setElement(END_INDEX, replacement));

        assertTieInBothLines();
        assertThat(tie.getAnchorElement()).isSameAs(anchorNote);
        assertThat(tie.getEndElement()).isSameAs(replacement);
    }

    @Test
    void testUndoRestoresTheTieIntoBothLinesAndRedoRemovesItFromBoth() {
        var batch = UndoTestSupport.captureBatch(song, () -> firstLine.removeElement(ANCHOR_INDEX));
        assertTieInNeitherLine();

        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertTieInBothLines();

        UndoTestSupport.replayRedo(scoreView, batch);
        assertTieInNeitherLine();
    }

    @Test
    void testADeletionBothLinesSweepRecordsOnlyOneTieRemoval() {
        // Both endpoints go in one bracket, so both lines run their sweep. The first
        // removal already took the tie out of both lists, so the second sweep must find
        // nothing to remove and record nothing — a second TieRemoval would make undo add
        // the tie back twice, leaving it in each list twice.
        var batch = UndoTestSupport.captureBatch(song, () -> {
            firstLine.removeElement(ANCHOR_INDEX);
            secondLine.removeElement(END_INDEX);
        });

        assertThat(batch).filteredOn(TieRemoval.class::isInstance).hasSize(1);

        UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);

        assertTieInBothLines();
    }

    /**
     * A note the tie may keep as an endpoint: it sounds what the note it replaces sounded,
     * so only the written duration changes.
     */
    private static StaffElement sameSoundingNote() {
        return minim();
    }
}
