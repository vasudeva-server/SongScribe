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
package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.singleBarline;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.message.MessageCenter;
import songscribe.undo.UndoTestSupport;

/**
 * Tests {@link MusicEditOperations#toggleTieWithPredecessor(Line, int)}, the edit-mode
 * "tie with the previous element" operation (#706).
 *
 * <p>{@link songscribe.ui.selection.RangeQueriesTieCandidateTest} already covers which pair
 * the operation refuses; this class covers what actually changes when it accepts one: the
 * toggle itself and the undo round-trip. Modeled on {@link
 * MusicEditOperationsBeamWithPredecessorTest}, adjusted for the ways ties differ from beams —
 * no widen-or-break branch, a legal separator between the pair, and a candidate that can cross
 * a line break.
 *
 * <p>The operation takes a bare {@link Song}/{@link Line} and touches no singleton, so nothing
 * here has to be stubbed out. Where the key points after the toggle is decided by the handler,
 * from the returned outcome — see {@code ScoreViewControllerCommandHandlerTest}.
 */
class MusicEditOperationsTieWithPredecessorTest extends UnitTest {

    /** Index of the third element of the fixtures built by {@link #lineOf}. */
    private static final int THIRD_INDEX = 2;

    /** Staff position shared by every pitched note the fixtures build, so they all tie. */
    private static final int STAFF_POSITION = 2;

    // song.getLine(0) always starts with one pre-existing terminal element (the auto-maintained
    // final barline). Line.addElement(StaffElement) inserts before that terminal only while the
    // receiving line is still the song's last line, so secondLine is attached first — leaving
    // firstLine's terminal in place — before firstLineLast is appended after it, landing at
    // index 1, which is also firstLine's last index. secondLine starts empty, so its boundary
    // note — added first, with a filler after it — lands at index 0.
    private static final int FIRST_LINE_BOUNDARY_INDEX = 1;
    private static final int SECOND_LINE_BOUNDARY_INDEX = 0;

    private Song song;

    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so constructor-internal bus interactions go to the
        // real (unobserved) bus, not the mock.
        song = new Song();
    }

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
        }
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** A pitched note at {@link #STAFF_POSITION} — every fixture note shares this pitch. */
    private static StaffElement tiableNote() {
        return createNote(STAFF_POSITION, true);
    }

    /**
     * Builds a line holding {@code elements} and attaches it to the song, then starts
     * mocking {@link MessageCenter} so the operation's own batch is the only thing observed.
     *
     * <p>Setup runs untracked so the line's terminal invariant is not enforced while the
     * fixture is being assembled.
     */
    private Line lineOf(StaffElement... elements) {
        var line = new Line(song);

        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }

            song.addLine(line);
        });

        messageCenterMock = mockStatic(MessageCenter.class);
        return line;
    }

    /** The line's tie spans as [anchor, end] index pairs, in span order. */
    private static List<List<Integer>> tieSpans(Line line) {
        return line.getSpans().stream()
            .filter(Tie.class::isInstance)
            .map(Tie.class::cast)
            .map(tie -> List.of(tie.getAnchorElementIndex(), tie.getEndElementIndex()))
            .toList();
    }

    private record CrossLineFixture(Song song, Line firstLine, Line secondLine) {}

    /**
     * Builds a two-line song: {@code firstLine} is {@code [<existing terminal>, firstLineLast]}
     * and {@code secondLine} is {@code [secondLineFirst, filler]}, so the two given elements sit
     * exactly at the line boundary between them. Mirrors {@code
     * CrossLineTieToggleTest.twoLineFixture}.
     */
    private CrossLineFixture twoLineFixture(StaffElement firstLineLast, StaffElement secondLineFirst) {
        var crossLineSong = new Song();
        var firstLine = crossLineSong.getLine(0);
        var secondLine = new Line(crossLineSong);

        crossLineSong.withoutMutationTracking(() -> {
            crossLineSong.addLine(secondLine);
            firstLine.addElement(firstLineLast);
            secondLine.addElement(secondLineFirst);
            secondLine.addElement(tiableNote());
        });

        messageCenterMock = mockStatic(MessageCenter.class);
        return new CrossLineFixture(crossLineSong, firstLine, secondLine);
    }

    // -----------------------------------------------------------------------
    // Adjacent pair
    // -----------------------------------------------------------------------

    @Nested
    class WhenTheTargetIsAdjacentToItsPredecessor {

        @Test
        void testAdjacentSamePitchNotesTieThenUntie() {
            var line = lineOf(tiableNote(), tiableNote());

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, 1)).isTrue();
            assertThat(tieSpans(line))
                .as("one tie span over the pair").containsExactly(List.of(0, 1));

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, 1))
                .as("a second call unties").isTrue();
            assertThat(tieSpans(line)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Pair separated by a barline
    // -----------------------------------------------------------------------

    @Nested
    class WhenABarlineSitsBetweenThePair {

        @Test
        void testTieSpansTheNotesNotTheBarlineThenUnties() {
            var line = lineOf(tiableNote(), singleBarline(), tiableNote());

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, THIRD_INDEX)).isTrue();
            assertThat(tieSpans(line))
                .as("the tie spans the two notes, not the barline between them")
                .containsExactly(List.of(0, THIRD_INDEX));

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, THIRD_INDEX))
                .as("a second call removes it").isTrue();
            assertThat(tieSpans(line)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Conflict with an existing beam
    // -----------------------------------------------------------------------

    @Nested
    class WhenABeamAlreadySpansThePair {

        @Test
        void testBeamBlocksAddingATie() {
            var line = lineOf(tiableNote(), tiableNote());
            song.withoutMutationTracking(
                () -> line.addBeaming(new Beam(line.getElement(0), line.getElement(1))));

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, 1))
                .as("tying may not connect what a beam already connects").isFalse();
            assertThat(tieSpans(line)).isEmpty();
        }

        @Test
        void testBeamDoesNotBlockRemovingATie() {
            var line = lineOf(tiableNote(), tiableNote());
            song.withoutMutationTracking(() -> {
                line.addSpan(new Tie(line.getElement(0), line.getElement(1)));
                line.addBeaming(new Beam(line.getElement(0), line.getElement(1)));
            });

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, 1))
                .as("the beam-conflict guard blocks the add branch only").isTrue();
            assertThat(tieSpans(line)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Cross-line pair
    // -----------------------------------------------------------------------

    @Nested
    class WhenThePredecessorIsOnThePreviousLine {

        @Test
        void testCrossLineTieIsCreatedThenRemoved() {
            var fixture = twoLineFixture(tiableNote(), tiableNote());

            assertThat(MusicEditOperations.toggleTieWithPredecessor(
                fixture.secondLine(), SECOND_LINE_BOUNDARY_INDEX))
                .as("ties across the line break to the previous line's matching last note")
                .isTrue();

            var tie = fixture.secondLine().findTieAt(SECOND_LINE_BOUNDARY_INDEX);
            assertThat(tie).isNotNull();
            assertThat(fixture.firstLine().getSpans())
                .as("the tie is in the first line too").containsOnlyOnce(tie);
            assertThat(fixture.secondLine().getSpans()).containsOnlyOnce(tie);

            assertThat(MusicEditOperations.toggleTieWithPredecessor(
                fixture.secondLine(), SECOND_LINE_BOUNDARY_INDEX))
                .as("a second call removes it").isTrue();
            assertThat(fixture.firstLine().getSpans())
                .as("removed from the first line too").doesNotContain(tie);
            assertThat(fixture.secondLine().getSpans()).doesNotContain(tie);
        }
    }

    // -----------------------------------------------------------------------
    // Chained ties
    // -----------------------------------------------------------------------

    @Nested
    class WhenThePredecessorIsAlreadyTiedToItsOwnPredecessor {

        @Test
        void testTyingTheMiddleNoteForwardLeavesBothTiesInPlace() {
            var line = lineOf(tiableNote(), tiableNote(), tiableNote());
            song.withoutMutationTracking(
                () -> line.addSpan(new Tie(line.getElement(0), line.getElement(1))));

            assertThat(MusicEditOperations.toggleTieWithPredecessor(line, THIRD_INDEX)).isTrue();
            assertThat(tieSpans(line))
                .as("the new tie joins the chain without disturbing the existing one")
                .containsExactlyInAnyOrder(List.of(0, 1), List.of(1, THIRD_INDEX));
        }
    }

    // -----------------------------------------------------------------------
    // Undo
    // -----------------------------------------------------------------------

    @Test
    void testOneUndoRestoresThePreToggleSpans() {
        // No lineOf() here: UndoTestSupport.captureBatch subscribes to the real
        // MessageCenter bus, which a mocked static class would swallow.
        var line = new Line(song);

        song.withoutMutationTracking(() -> {
            line.addElement(tiableNote());
            line.addElement(tiableNote());
            song.addLine(line);
        });

        var batch = UndoTestSupport.captureBatch(
            song, () -> MusicEditOperations.toggleTieWithPredecessor(line, 1));

        assertThat(tieSpans(line))
            .as("the toggle created the tie").containsExactly(List.of(0, 1));

        UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);

        assertThat(tieSpans(line))
            .as("one undo restores the pre-toggle spans").isEmpty();
    }
}
