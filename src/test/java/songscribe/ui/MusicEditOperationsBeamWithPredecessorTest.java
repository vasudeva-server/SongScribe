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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.TupletAction;
import songscribe.ui.component.ScoreView;
import songscribe.undo.MutationReplayer;

/**
 * Tests {@link MusicEditOperations#toggleBeamWithPredecessor(Line, int)}, the edit-mode
 * "beam with the previous element" operation.
 *
 * <p>The method is static and takes the line explicitly, so these tests run against a real
 * {@link Song}/{@link Line} with no selection coordinator and no UI mocks. Only
 * {@link MessageCenter} is mocked, and only so the emitted mutation batch can be captured
 * for the undo round-trips.
 */
class MusicEditOperationsBeamWithPredecessorTest extends UnitTest {

    /** Index of the third element of the fixtures built by {@link #lineOf}. */
    private static final int THIRD_INDEX = 2;

    /** Index of the fourth element of the fixtures built by {@link #lineOf}. */
    private static final int FOURTH_INDEX = 3;

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

    private static StaffElement semiquaver() {
        return ElementType.SEMIQUAVER.newInstance();
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

    /** Adds a beam spanning [{@code anchorIndex}, {@code endIndex}] without recording it. */
    private void beam(Line line, int anchorIndex, int endIndex) {
        song.withoutMutationTracking(
            () -> line.addBeaming(new Beam(line.getElement(anchorIndex), line.getElement(endIndex))));
    }

    /** The line's beam spans as [anchor, end] index pairs, in range-element order. */
    private static List<List<Integer>> beamSpans(Line line) {
        return line.getRangeElements().stream()
            .filter(Beam.class::isInstance)
            .map(Beam.class::cast)
            .map(b -> List.of(b.getAnchorElementIndex(), b.getEndElementIndex()))
            .toList();
    }

    /** The single mutation batch the operation posted. */
    private List<Mutation> captureMutations() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call lineOf() first");
        }

        var captor = ArgumentCaptor.forClass(Message.class);
        mock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(SongDidChangeNotification.class::isInstance)
            .map(SongDidChangeNotification.class::cast)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.getFirst().getMutations();
    }

    /** Replays {@code mutations} in reverse order under replay mode, exactly as undo does. */
    private void replayUndo(List<Mutation> mutations) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        song.withModification(() -> song.withReplay(() -> {
            for (var i = mutations.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, mutations.get(i));
            }
        }));
    }

    // -----------------------------------------------------------------------
    // The behavior table: what "b" does to the element just placed
    // -----------------------------------------------------------------------

    @Nested
    class WhenArmingTheElementJustPlaced {

        @Test
        void testFirstElementInLineIsRefused() {
            var line = lineOf(quaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 0))
                .as("nothing precedes the first element, so there is nothing to beam to")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testQuaverAfterQuaverBeamsWithPrevious() {
            var line = lineOf(quaver(), quaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1)).isTrue();
            assertThat(beamSpans(line)).containsExactly(List.of(0, 1));
        }

        @Test
        void testRestTargetIsRefused() {
            var line = lineOf(quaver(), crotchetRest());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("a rest is not beamable")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testCrotchetTargetIsRefused() {
            var line = lineOf(quaver(), crotchet());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("a crotchet has no flag to beam")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testBeamableTargetAfterCrotchetIsRefused() {
            var line = lineOf(crotchet(), semiquaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("the predecessor is not beamable, so there is nothing to beam to")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testSemiquaverAfterQuaverBeamsWithPrevious() {
            var line = lineOf(quaver(), semiquaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1)).isTrue();
            assertThat(beamSpans(line)).containsExactly(List.of(0, 1));
        }
    }

    // -----------------------------------------------------------------------
    // Joining an existing beam
    // -----------------------------------------------------------------------

    @Nested
    class WhenTheTargetJoinsAnExistingBeam {

        @Test
        void testJoiningProducesOneSpanNotTwo() {
            var line = lineOf(quaver(), quaver(), semiquaver());
            beam(line, 0, 1);

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX)).isTrue();
            assertThat(beamSpans(line))
                .as("the new beam absorbs the one it overlaps rather than sitting beside it")
                .containsExactly(List.of(0, THIRD_INDEX));
        }

        @Test
        void testOneUndoRestoresTheOriginalTwoElementSpan() {
            var line = lineOf(quaver(), quaver(), semiquaver());
            beam(line, 0, 1);

            MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX);

            // Captured before the replay, which posts a notification of its own.
            var mutations = captureMutations();
            replayUndo(mutations);

            assertThat(beamSpans(line))
                .as("the removal of the subsumed span must ride in the same undo batch as "
                    + "the widened span's addition")
                .containsExactly(List.of(0, 1));
        }
    }

    // -----------------------------------------------------------------------
    // Breaking an existing beam
    // -----------------------------------------------------------------------

    @Nested
    class WhenOneBeamAlreadyCoversBoth {

        @Test
        void testBreakingAtTheEndKeepsOnlyTheHead() {
            var line = lineOf(quaver(), quaver(), quaver());
            beam(line, 0, THIRD_INDEX);

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX)).isTrue();
            assertThat(beamSpans(line))
                .as("the one-element tail is dropped — a beam needs two members")
                .containsExactly(List.of(0, 1));
        }

        @Test
        void testBreakingAtTheAnchorSuccessorKeepsOnlyTheTail() {
            var line = lineOf(quaver(), quaver(), quaver());
            beam(line, 0, THIRD_INDEX);

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1)).isTrue();
            assertThat(beamSpans(line))
                .as("the one-element head is dropped — a beam needs two members")
                .containsExactly(List.of(1, THIRD_INDEX));
        }

        @Test
        void testBreakingATwoElementBeamLeavesNoBeam() {
            var line = lineOf(quaver(), quaver());
            beam(line, 0, 1);

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1)).isTrue();
            assertThat(beamSpans(line))
                .as("both remainders are single elements, so nothing survives")
                .isEmpty();
        }

        @Test
        void testBreakingInTheMiddleYieldsTwoSpans() {
            var line = lineOf(quaver(), quaver(), quaver(), quaver());
            beam(line, 0, FOURTH_INDEX);

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX)).isTrue();
            assertThat(beamSpans(line))
                .as("the two halves must not re-merge — addBeaming absorbs overlap, not adjacency")
                .containsExactlyInAnyOrder(List.of(0, 1), List.of(THIRD_INDEX, FOURTH_INDEX));
        }

        @Test
        void testOneUndoRestoresTheOriginalSingleSpan() {
            var line = lineOf(quaver(), quaver(), quaver(), quaver());
            beam(line, 0, FOURTH_INDEX);

            MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX);

            // Captured before the replay, which posts a notification of its own.
            var mutations = captureMutations();
            replayUndo(mutations);

            assertThat(beamSpans(line))
                .as("the removal and both re-additions must ride in one undo batch")
                .containsExactly(List.of(0, FOURTH_INDEX));
        }
    }

    // -----------------------------------------------------------------------
    // Grace notes
    // -----------------------------------------------------------------------

    @Nested
    class WhenGraceNotesAreInvolved {

        @Test
        void testInterveningGraceNoteIsSkippedAndSpannedButNotAnEndpoint() {
            var line = lineOf(quaver(), graceQuaver(), quaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX)).isTrue();
            assertThat(beamSpans(line))
                .as("the walk backward skips the grace note, which then sits inside the span "
                    + "without being one of its endpoints")
                .containsExactly(List.of(0, THIRD_INDEX));
        }

        @Test
        void testGraceNoteTargetIsRefused() {
            var line = lineOf(quaver(), graceQuaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("a grace note is never a beam member")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testLoneGraceNotePredecessorIsRefused() {
            var line = lineOf(graceQuaver(), quaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("skipping the grace note walks off the front of the line")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Conflicts and guards
    // -----------------------------------------------------------------------

    @Nested
    class WhenOtherSpansOrBadIndicesAreInvolved {

        @Test
        void testTieBlocksAddingABeam() {
            var line = lineOf(quaver(), quaver());
            song.withoutMutationTracking(
                () -> line.addRangeElement(new Tie(line.getElement(0), line.getElement(1))));

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("beaming may not connect what a tie already connects")
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testTieDoesNotBlockBreakingABeam() {
            var line = lineOf(quaver(), quaver(), quaver());
            song.withoutMutationTracking(() -> {
                line.addRangeElement(new Tie(line.getElement(1), line.getElement(THIRD_INDEX)));
                line.addBeaming(new Beam(line.getElement(0), line.getElement(THIRD_INDEX)));
            });

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX))
                .as("the tie check guards the add branch only")
                .isTrue();
            assertThat(beamSpans(line)).containsExactly(List.of(0, 1));
        }

        @Test
        void testTupletDoesNotBlockBeaming() {
            var line = lineOf(quaver(), quaver(), quaver());
            song.withoutMutationTracking(() -> line.addTuplet(new Tuplet(
                line.getElement(0),
                line.getElement(THIRD_INDEX),
                TupletAction.Tuplet.TRIPLET.getSize()
            )));

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, 1))
                .as("there is deliberately no tuplet guard")
                .isTrue();
            assertThat(beamSpans(line)).containsExactly(List.of(0, 1));
        }

        @Test
        void testNegativeIndexIsRefused() {
            var line = lineOf(quaver(), quaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, -1)).isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }

        @Test
        void testPastTheEndIndexIsRefused() {
            var line = lineOf(quaver(), quaver());

            assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, line.elementCount()))
                .isFalse();
            assertThat(beamSpans(line)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Fusion of two adjacent beam groups — intended, not a bug
    // -----------------------------------------------------------------------

    @Test
    void testBeamingAcrossTwoAdjacentGroupsFusesThemIntoOne() {
        var line = lineOf(quaver(), quaver(), quaver(), quaver());
        beam(line, 0, 1);
        beam(line, THIRD_INDEX, FOURTH_INDEX);

        assertThat(MusicEditOperations.toggleBeamWithPredecessor(line, THIRD_INDEX)).isTrue();
        assertThat(beamSpans(line))
            .as("addBeaming widens at both ends, so the two groups fuse — the same result "
                + "the select-mode toggle produces from this call")
            .containsExactly(List.of(0, FOURTH_INDEX));
    }
}
