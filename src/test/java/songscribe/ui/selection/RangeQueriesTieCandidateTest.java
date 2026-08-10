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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Unit tests for {@link RangeQueries#tieCandidateWithPredecessor(Line, int)} (#706): the
 * candidate-selection query that turns "the note just placed" into the range a tie toggle
 * should act on, or {@code null} when nothing qualifies.
 * <p>
 * A pure function of {@code (Line, int)}, so every fixture builds a line directly rather than
 * going through a coordinator or a selection — matching how {@link RangeQueriesTest} exercises
 * the same-line tie rules the candidate selection reuses via {@link RangeQueries#canToggleTie}.
 */
class RangeQueriesTieCandidateTest extends UnitTest {

    private static final int STAFF_POSITION_A = 0;
    private static final int STAFF_POSITION_B = 2;

    /** Builds a detached line from {@code elements}, in order. */
    private static Line lineOf(StaffElement... elements) {
        var line = detachedLine();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    @Nested
    class SameLineCandidates {

        @Test
        void testAdjacentSamePitchNotesReturnsThePair() {
            var line = lineOf(crotchet(), crotchet());

            var candidate = RangeQueries.tieCandidateWithPredecessor(line, 1);

            assertThat(candidate).isNotNull();
            assertThat(candidate.begin()).isEqualTo(0);
            assertThat(candidate.end()).isEqualTo(1);
        }

        @Test
        void testDifferentPitchReturnsNull() {
            var line = lineOf(
                createNote(STAFF_POSITION_A, true), createNote(STAFF_POSITION_B, true));

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 1)).isNull();
        }

        @Test
        void testRestAsTargetReturnsNull() {
            var line = lineOf(crotchet(), crotchetRest());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 1)).isNull();
        }

        @Test
        void testRestAsPredecessorReturnsNull() {
            var line = lineOf(crotchetRest(), crotchet());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 1)).isNull();
        }

        @Test
        void testGraceNoteAsTargetReturnsNull() {
            var line = lineOf(crotchet(), graceQuaver());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 1)).isNull();
        }

        @Test
        void testGraceNoteBetweenThePairReturnsNull() {
            // A grace note is not a legal separator, unlike a barline or repeat, so it does not
            // fall back to the separated-pair shape either.
            var line = lineOf(crotchet(), graceQuaver(), crotchet());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 2)).isNull();
        }

        @Test
        void testBarlineBetweenThePairReturnsTheSeparatedPair() {
            var line = lineOf(crotchet(), singleBarline(), crotchet());

            var candidate = RangeQueries.tieCandidateWithPredecessor(line, 2);

            assertThat(candidate).isNotNull();
            assertThat(candidate.begin()).isEqualTo(0);
            assertThat(candidate.end()).isEqualTo(2);
        }

        @Test
        void testBeamAlreadySpanningThePairReturnsNull() {
            // The pair is otherwise a legal tie candidate, but a beam already connects it, and
            // tieCandidateWithPredecessor never proposes a shape canToggleTie would reject.
            var line = lineOf(crotchet(), crotchet());
            line.addBeaming(new Beam(line.getElement(0), line.getElement(1)));

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 1)).isNull();
        }

        @Test
        void testFirstElementOfTheSongsFirstLineReturnsNull() {
            var line = lineOf(crotchet(), crotchet());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, 0)).isNull();
        }

        @Test
        void testNegativeIndexReturnsNull() {
            var line = lineOf(crotchet(), crotchet());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, -1)).isNull();
        }

        @Test
        void testIndexEqualToElementCountReturnsNull() {
            var line = lineOf(crotchet(), crotchet());

            assertThat(RangeQueries.tieCandidateWithPredecessor(line, line.elementCount())).isNull();
        }
    }

    @Nested
    class CrossLineCandidates {

        // song.getLine(0) always starts with one pre-existing terminal element (the
        // auto-maintained final barline). Line.addElement(StaffElement) inserts before that
        // terminal only while the receiving line is still the song's last line, so a later line
        // is attached first — leaving the earlier line's terminal in place — before its own last
        // note is appended after it (see CrossLineTieToggleTest for the full account).
        private static final int FIRST_LINE_LAST_INDEX = 1;
        private static final int SECOND_LINE_FIRST_INDEX = 0;

        private record TwoLineFixture(Line firstLine, Line secondLine) {}

        /**
         * Builds a two-line song where {@code firstLineLast} sits at the true end of the first
         * line and {@code secondLineFirst} sits at the true start of the second — the two
         * elements a cross-line tie would join.
         */
        private static TwoLineFixture twoLineFixture(
            StaffElement firstLineLast, StaffElement secondLineFirst
        ) {
            var song = new Song();
            var firstLine = song.getLine(0);
            var secondLine = new Line(song);

            song.withoutMutationTracking(() -> {
                song.addLine(secondLine);
                firstLine.addElement(firstLineLast);
                secondLine.addElement(secondLineFirst);
            });

            return new TwoLineFixture(firstLine, secondLine);
        }

        @Test
        void testPreviousLineEndingOnSamePitchReturnsTheBoundarySingle() {
            var fixture = twoLineFixture(
                createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

            var candidate = RangeQueries.tieCandidateWithPredecessor(
                fixture.secondLine(), SECOND_LINE_FIRST_INDEX);

            assertThat(candidate).isNotNull();
            assertThat(candidate.begin()).isEqualTo(SECOND_LINE_FIRST_INDEX);
            assertThat(candidate.end()).isEqualTo(SECOND_LINE_FIRST_INDEX);
        }

        /**
         * Without the direction guard, {@code boundaryTieAt} would still return a candidate
         * here — it finds the same forward pair — and the toggle would silently draw a tie
         * from a note the user has not placed anything after yet.
         */
        @Test
        void testDirectionGuardAtFirstLineOfSongReturnsNull() {
            var fixture = twoLineFixture(
                createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

            var candidate = RangeQueries.tieCandidateWithPredecessor(
                fixture.firstLine(), FIRST_LINE_LAST_INDEX);

            assertThat(candidate).isNull();
        }

        /**
         * The mid-song counterpart to the first-line guard: the placed note's own line offers
         * no in-line partner (its predecessor is a different pitch), so without the direction
         * guard the only remaining candidate would be the forward, cross-line one into the
         * line after it — exactly the tie the guard exists to refuse.
         */
        @Test
        void testDirectionGuardMidSongReturnsNull() {
            var song = new Song();
            var firstLine = song.getLine(0);
            var secondLine = new Line(song);
            var thirdLine = new Line(song);
            var differentPitchPredecessor = createNote(STAFF_POSITION_A, true);
            var target = createNote(STAFF_POSITION_B, true);

            song.withoutMutationTracking(() -> {
                song.addLine(secondLine);
                song.addLine(thirdLine);
                firstLine.addElement(crotchet());
                secondLine.addElement(differentPitchPredecessor);
                secondLine.addElement(target);
                thirdLine.addElement(createNote(STAFF_POSITION_B, true));
            });

            var targetIndex = secondLine.elementCount() - 1;
            var candidate = RangeQueries.tieCandidateWithPredecessor(secondLine, targetIndex);

            assertThat(candidate).isNull();
        }
    }
}
