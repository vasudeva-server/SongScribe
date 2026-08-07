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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.crotchetRest;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.mutation.Mutation;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.RangeQueries;
import songscribe.ui.selection.Selection;
import songscribe.undo.MutationReplayer;

/**
 * Tests {@link SlideOperations}, the kernel both slide toggles run through: the select-mode
 * entry points that act on a {@link Selection.Range} and the edit-mode ones that act on the
 * last insertion.
 *
 * <p>The operations are static and take the line explicitly, so these tests run against a real
 * {@link Song}/{@link Line} with no selection coordinator. {@link MessageCenter} is mocked so
 * the emitted mutation batch can be captured — which is how the undo label and the
 * one-step-per-toggle shape are observed — and {@link EditModeManager} so the arming the
 * edit-mode paths perform can be verified without a live singleton.
 *
 * <p>Every assertion on an outcome is against {@link SlideOperations.Result} rather than a
 * boolean: {@code REFUSED} and {@code REPORTED} both modify nothing, and the difference between
 * them is whether the caller still owes the user a beep or the operation has already put an
 * error dialog on screen.
 */
class SlideOperationsTest extends UnitTest {

    /** Index of the third element of the fixtures built by {@link #lineOf}. */
    private static final int THIRD_INDEX = 2;

    /** Index of the fourth element of the fixtures built by {@link #lineOf}. */
    private static final int FOURTH_INDEX = 3;

    /** A line width too narrow for any slide these fixtures could add. */
    private static final double NO_ROOM_LINE_WIDTH_SS = 1;

    /** Verse number used by {@link #pairedGraceLineOf}'s syllable. */
    private static final int VERSE = 1;

    /** Syllable text used by {@link #pairedGraceLineOf}. */
    private static final String SYLLABLE = "glo";

    /** Index of the paired grace note built by {@link #pairedGraceLineOf}. */
    private static final int GRACE_INDEX = 0;

    /** Index of the paired grace note's host, built by {@link #pairedGraceLineOf}. */
    private static final int HOST_INDEX = 1;

    private Song song;

    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    @Nullable private MockedStatic<EditModeManager> editModeManagerMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so constructor-internal bus interactions go to the
        // real (unobserved) bus, not the mock.
        song = new Song();

        // The edit-mode entry points arm the insertion target on the branches that modify the
        // line, which needs a live EditModeManager singleton. Stubbing the class out both
        // supplies one and makes the arm — and its absence on the refusing branches —
        // observable. EditModeManagerTest covers what the arming then does.
        editModeManagerMock = mockStatic(EditModeManager.class);
    }

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
        }

        if (editModeManagerMock != null) {
            editModeManagerMock.close();
        }
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** A note the fixtures pair with {@link #higherNote} so a glissando's two pitches differ. */
    private static StaffElement lowerNote() {
        return createNote(0, false);
    }

    /** A note one staff position above {@link #lowerNote}, so a glissando has distance to cover. */
    private static StaffElement higherNote() {
        return createNote(1, false);
    }

    /**
     * Builds a line holding {@code elements} and attaches it to the song, then starts mocking
     * {@link MessageCenter} so the operation's own batch is the only thing observed.
     *
     * <p>Setup runs untracked so the line's terminal invariant is not enforced while the fixture
     * is being assembled.
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

    /** The same fixture on a line with no horizontal room left for a slide of any kind. */
    private Line crampedLineOf(StaffElement... elements) {
        var line = new Line(song);

        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }

            song.addLine(line);
            song.setLineWidthSs(NO_ROOM_LINE_WIDTH_SS);
        });

        messageCenterMock = mockStatic(MessageCenter.class);
        return line;
    }

    /**
     * A line holding a grace note paired to its host — {@code [grace, host]} — with the
     * automatic grace-host melisma established: {@link Lyric.Extend#START} on the grace and a
     * text-less {@link Lyric.Extend#STOP} carrier on the host.
     */
    private Line pairedGraceLineOf() {
        var line = new Line(song);

        song.withoutMutationTracking(() -> {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando();
            grace.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, SYLLABLE, Lyric.Extend.NONE);
            line.addElement(grace);
            line.addElement(ElementType.CROTCHET.newInstance());
            line.syncGraceHostMelisma(GRACE_INDEX);

            song.addLine(line);
        });

        messageCenterMock = mockStatic(MessageCenter.class);
        return line;
    }

    /** A range over {@code begin..end} of {@code line}, anchored at {@code begin}. */
    private static Selection.Range rangeOver(Line line, int begin, int end) {
        return new Selection.Range(line, begin, end, begin);
    }

    /** Whether each element of {@code line}, in order, carries a fall. */
    private static List<Boolean> falls(Line line) {
        return IntStream.range(0, line.elementCount())
            .mapToObj(i -> line.getElement(i).hasFall())
            .toList();
    }

    /** Every song change the operation posted — empty when it modified nothing. */
    private List<SongDidChangeNotification> songDidChanges() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call lineOf() first");
        }

        var captor = ArgumentCaptor.forClass(Message.class);
        // atLeast(0): a refusing branch posts nothing at all, which is itself an outcome to assert.
        mock.verify(() -> MessageCenter.post(captor.capture()), atLeast(0));

        return captor.getAllValues().stream()
            .filter(SongDidChangeNotification.class::isInstance)
            .map(SongDidChangeNotification.class::cast)
            .toList();
    }

    /** The single song change the operation posted — one per toggle, however many notes it touched. */
    private SongDidChangeNotification captureSongDidChange() {
        var didChanges = songDidChanges();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.getFirst();
    }

    /** Replays {@code mutations} in reverse order under replay mode, exactly as undo does. */
    private void replayUndo(List<? extends Mutation> mutations) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        song.withModification(() -> song.withReplay(() -> {
            for (var i = mutations.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, mutations.get(i));
            }
        }));
    }

    /** Asserts that no edit-mode insertion was armed. */
    private void assertNothingArmed() {
        var mock = editModeManagerMock;

        if (mock == null) {
            throw new IllegalStateException("editModeManagerMock not set");
        }

        mock.verify(() -> EditModeManager.armInsertion(any(Line.class), anyInt()), never());
    }

    /** Asserts that {@code elementIndex} — and nothing else — was armed as the pending insertion. */
    private void assertArmed(Line line, int elementIndex) {
        var mock = editModeManagerMock;

        if (mock == null) {
            throw new IllegalStateException("editModeManagerMock not set");
        }

        mock.verify(() -> EditModeManager.armInsertion(line, elementIndex));
    }

    // -----------------------------------------------------------------------
    // Select mode: glissando
    // -----------------------------------------------------------------------

    @Nested
    class TogglingAGlissandoOverASelection {

        @Test
        void testAPairOfNotesGainsThenLosesTheGlissando() {
            var line = lineOf(lowerNote(), higherNote());
            var range = rangeOver(line, 0, 1);

            assertThat(SlideOperations.toggleGlissando(range, null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasGlissando()).isTrue();

            assertThat(SlideOperations.toggleGlissando(range, null))
                .as("the same selection takes it back off")
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasGlissando()).isFalse();
        }

        @Test
        void testASingleSelectedNoteConnectsBackToItsPredecessor() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(SlideOperations.toggleGlissando(Selection.Range.single(line, 1), null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasGlissando())
                .as("the glissando lives on the source, which precedes the selected target")
                .isTrue();
        }

        @Test
        void testTheAdditionIsOneUndoStepNamedAddGlissando() {
            var line = lineOf(lowerNote(), higherNote());

            SlideOperations.toggleGlissando(rangeOver(line, 0, 1), null);

            assertThat(captureSongDidChange().getOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_GLISSANDO));
        }

        @Test
        void testTheRemovalIsOneUndoStepNamedRemoveGlissando() {
            var line = lineOf(lowerNote(), higherNote());
            song.withoutMutationTracking(() -> line.getElement(0).setGlissando());

            SlideOperations.toggleGlissando(rangeOver(line, 0, 1), null);

            assertThat(captureSongDidChange().getOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_GLISSANDO));
        }

        @Test
        void testAnIneligibleSelectionIsRefusedSilently() {
            var line = lineOf(lowerNote(), lowerNote());

            assertThat(SlideOperations.toggleGlissando(rangeOver(line, 0, 1), null))
                .as("two notes at one pitch have no distance for a glissando to traverse")
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertThat(line.getElement(0).hasGlissando()).isFalse();
            assertThat(songDidChanges()).as("nothing was modified").isEmpty();
        }

        /**
         * A connecting glissando puts a floor under the gap it reaches across, so it can make a
         * line infeasible even though it widens no column of its own (refs #717).
         */
        @Test
        void testNoRoomIsReportedAndTheLineIsUnchanged() {
            var line = crampedLineOf(lowerNote(), higherNote());

            assertThat(SlideOperations.toggleGlissando(rangeOver(line, 0, 1), null))
                .as("the error dialog has been shown, so the caller must stay quiet")
                .isEqualTo(SlideOperations.Result.REPORTED);
            assertThat(line.getElement(0).hasGlissando()).isFalse();
            assertThat(songDidChanges()).isEmpty();
        }

        @Test
        void testARemovalIsNeverRefusedForWantOfRoom() {
            var line = crampedLineOf(lowerNote(), higherNote());
            song.withoutMutationTracking(() -> line.getElement(0).setGlissando());

            assertThat(SlideOperations.toggleGlissando(rangeOver(line, 0, 1), null))
                .as("taking a slide off claims no space, so the room gate must not run")
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasGlissando()).isFalse();
        }

        /**
         * Regression test (#717): {@link RangeQueries#glissandoSourceIndex} deliberately lets a
         * removal resolve without the add-only pitched-note gate, so an ordinary 2-element
         * selection over a paired grace note and its host reaches the removal branch even though
         * the source is a grace note. That un-pairs the grace note, and {@code applySlides} must
         * dissolve the automatic melisma the pairing carries — otherwise the grace note is left
         * stuck extending and the host keeps a stale, text-less lyric carrier.
         */
        @Test
        void testRemovingAGlissandoFromAPairedGraceNoteTearsDownTheMelisma() {
            var line = pairedGraceLineOf();

            assertThat(line.getElement(GRACE_INDEX).getLyricForVerse(VERSE))
                .as("pre-condition: the melisma starts on the grace note").isNotNull();
            assertThat(line.getElement(HOST_INDEX).getLyricForVerse(VERSE))
                .as("pre-condition: the host carries the melisma's STOP carrier").isNotNull();

            assertThat(SlideOperations.toggleGlissando(rangeOver(line, GRACE_INDEX, HOST_INDEX), null))
                .isEqualTo(SlideOperations.Result.MODIFIED);

            var graceElement = line.getElement(GRACE_INDEX);
            assertThat(graceElement.hasGlissando())
                .as("the toggle removed the glissando, un-pairing the grace note").isFalse();

            var graceLyric = graceElement.getLyricForVerse(VERSE);
            assertThat(graceLyric)
                .as("the syllable stays on the now-ordinary former grace note").isNotNull();
            assertThat(graceLyric.extend())
                .as("the melisma START reverted to NONE").isEqualTo(Lyric.Extend.NONE);

            assertThat(line.getElement(HOST_INDEX).getLyricForVerse(VERSE))
                .as("the host's carrier is removed outright, leaving no empty-lyric residue").isNull();
        }
    }

    // -----------------------------------------------------------------------
    // Select mode: falls
    // -----------------------------------------------------------------------

    @Nested
    class TogglingFallsOverASelection {

        @Test
        void testEveryPitchedNoteInTheRangeGainsAFallAndNothingElseDoes() {
            var line = lineOf(lowerNote(), crotchetRest(), higherNote());

            assertThat(SlideOperations.toggleFall(rangeOver(line, 0, THIRD_INDEX), null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(falls(line)).containsExactly(true, false, true);
        }

        @Test
        void testASecondToggleClearsThemAll() {
            var line = lineOf(lowerNote(), crotchetRest(), higherNote());
            var range = rangeOver(line, 0, THIRD_INDEX);

            SlideOperations.toggleFall(range, null);

            assertThat(SlideOperations.toggleFall(range, null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(falls(line)).containsExactly(false, false, false);
        }

        @Test
        void testAMixedSelectionAddsRatherThanClears() {
            var line = lineOf(lowerNote(), higherNote(), lowerNote());
            song.withoutMutationTracking(() -> line.getElement(1).setFall());

            assertThat(SlideOperations.toggleFall(rangeOver(line, 0, THIRD_INDEX), null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(falls(line))
                .as("a toggle never clears a selection it has not filled first")
                .containsExactly(true, true, true);
        }

        @Test
        void testARangeWithNoPitchedNoteIsRefused() {
            var line = lineOf(crotchetRest(), crotchetRest());

            assertThat(SlideOperations.toggleFall(rangeOver(line, 0, 1), null))
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertThat(songDidChanges()).isEmpty();
        }

        @Test
        void testTheAdditionIsNamedAddFallAndTheRemovalRemoveFall() {
            var line = lineOf(lowerNote(), higherNote());
            var range = rangeOver(line, 0, 1);

            SlideOperations.toggleFall(range, null);
            assertThat(captureSongDidChange().getOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_ADD_FALL));

            SlideOperations.toggleFall(range, null);
            assertThat(songDidChanges().getLast().getOpName())
                .isEqualTo(Strings.get(Strings.ACTION_EDIT_OP_DELETE_FALL));
        }

        /**
         * All or nothing: the batched room check is the whole reason several falls cannot each
         * pass a check against the unchanged line and then jointly overrun it, and a partial
         * application would be the same bug wearing a different hat.
         */
        @Test
        void testABatchWithNoRoomAddsNoFallAtAll() {
            var line = crampedLineOf(lowerNote(), higherNote(), lowerNote());

            assertThat(SlideOperations.toggleFall(rangeOver(line, 0, THIRD_INDEX), null))
                .isEqualTo(SlideOperations.Result.REPORTED);
            assertThat(falls(line)).containsExactly(false, false, false);
            assertThat(songDidChanges()).isEmpty();
        }

        /**
         * {@link songscribe.dom.SlideZone#applyTo} replaces whatever slide an element already
         * carries, so a note can never hold both kinds at once — a fall toggle over a
         * glissando-carrying note adds the fall by silently discarding the glissando rather than
         * refusing (#717).
         */
        @Test
        void testAddingAFallReplacesAnExistingGlissando() {
            var line = lineOf(lowerNote(), higherNote());
            song.withoutMutationTracking(() -> line.getElement(0).setGlissando());

            assertThat(SlideOperations.toggleFall(Selection.Range.single(line, 0), null))
                .as("the source carries no fall yet, so this is the add branch")
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasFall()).isTrue();
            assertThat(line.getElement(0).hasGlissando())
                .as("the glissando is gone once the fall replaces it").isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Edit mode: the last insertion, and what gets armed
    // -----------------------------------------------------------------------

    @Nested
    class TogglingFromEditMode {

        @Test
        void testGlissandoArmsTheInsertedIndexNotTheSource() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(SlideOperations.toggleGlissandoWithPredecessor(line, 1, null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasGlissando())
                .as("the glissando lands on the source, one before the note just placed")
                .isTrue();
            assertArmed(line, 1);
        }

        @Test
        void testASecondPressTakesTheGlissandoBackOff() {
            var line = lineOf(lowerNote(), higherNote());

            SlideOperations.toggleGlissandoWithPredecessor(line, 1, null);

            assertThat(SlideOperations.toggleGlissandoWithPredecessor(line, 1, null))
                .as("arming the placed note is what keeps the same pair reachable")
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(line.getElement(0).hasGlissando()).isFalse();
        }

        @Test
        void testAnIndexOutsideTheLineIsRefusedAndArmsNothing() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(SlideOperations.toggleGlissandoWithPredecessor(line, -1, null))
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertThat(SlideOperations.toggleGlissandoWithPredecessor(line, line.elementCount(), null))
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertThat(SlideOperations.toggleFallOnLastInsertion(line, line.elementCount(), null))
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertNothingArmed();
        }

        /**
         * An arm on a path that changes nothing is a bug in its own right: a pending insertion is
         * not discarded, so the next song change of any kind promotes it into a stale target.
         */
        @Test
        void testAnIneligibleGlissandoArmsNothing() {
            var line = lineOf(lowerNote(), lowerNote());

            assertThat(SlideOperations.toggleGlissandoWithPredecessor(line, 1, null))
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertNothingArmed();
        }

        @Test
        void testFallOnTheLastInsertionAddsAndArmsThatIndex() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(SlideOperations.toggleFallOnLastInsertion(line, 1, null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(falls(line)).containsExactly(false, true);
            assertArmed(line, 1);
        }

        @Test
        void testASecondPressTakesTheFallBackOff() {
            var line = lineOf(lowerNote(), higherNote());

            SlideOperations.toggleFallOnLastInsertion(line, 1, null);

            assertThat(SlideOperations.toggleFallOnLastInsertion(line, 1, null))
                .isEqualTo(SlideOperations.Result.MODIFIED);
            assertThat(falls(line)).containsExactly(false, false);
        }

        @Test
        void testAFallOnARestIsRefusedAndArmsNothing() {
            var line = lineOf(lowerNote(), crotchetRest());

            assertThat(SlideOperations.toggleFallOnLastInsertion(line, 1, null))
                .isEqualTo(SlideOperations.Result.REFUSED);
            assertThat(falls(line)).containsExactly(false, false);
            assertNothingArmed();
        }

        @Test
        void testAFallWithNoRoomIsReportedAndArmsNothing() {
            var line = crampedLineOf(lowerNote(), higherNote());

            assertThat(SlideOperations.toggleFallOnLastInsertion(line, 1, null))
                .isEqualTo(SlideOperations.Result.REPORTED);
            assertThat(falls(line)).containsExactly(false, false);
            assertNothingArmed();
        }
    }

    // -----------------------------------------------------------------------
    // Undo shape: one step per toggle, whatever it touched
    // -----------------------------------------------------------------------

    @Nested
    class TheUndoStepAToggleProduces {

        @Test
        void testAFallAcrossAMultiNoteRangeIsOneStepThatUndoesWhole() {
            var line = lineOf(lowerNote(), higherNote(), lowerNote(), higherNote());

            SlideOperations.toggleFall(rangeOver(line, 0, FOURTH_INDEX), null);

            // Captured before the replay, which posts a notification of its own.
            var mutations = captureSongDidChange().getMutations();
            assertThat(falls(line)).containsExactly(true, true, true, true);

            replayUndo(mutations);

            assertThat(falls(line))
                .as("four falls in one bracket is one undo step, not four")
                .containsExactly(false, false, false, false);
        }

        @Test
        void testUndoingARemovalPutsTheFallsBack() {
            var line = lineOf(lowerNote(), higherNote());
            song.withoutMutationTracking(() -> {
                line.getElement(0).setFall();
                line.getElement(1).setFall();
            });

            SlideOperations.toggleFall(rangeOver(line, 0, 1), null);

            var mutations = captureSongDidChange().getMutations();
            assertThat(falls(line)).containsExactly(false, false);

            replayUndo(mutations);

            assertThat(falls(line)).containsExactly(true, true);
        }

        @Test
        void testUndoingAGlissandoAdditionRemovesIt() {
            var line = lineOf(lowerNote(), higherNote());

            SlideOperations.toggleGlissando(rangeOver(line, 0, 1), null);

            var mutations = captureSongDidChange().getMutations();

            replayUndo(mutations);

            assertThat(line.getElement(0).hasGlissando()).isFalse();
        }
    }
}
