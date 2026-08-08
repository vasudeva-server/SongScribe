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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.InitialTempoTestSupport.ORIGINAL_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.TARGET_TEMPO_BPM;
import static songscribe.dom.InitialTempoTestSupport.attachBeatChange;
import static songscribe.dom.InitialTempoTestSupport.attachTempo;
import static songscribe.dom.InitialTempoTestSupport.tempoOf;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.ui.InitialTempoConfirmsTestSupport.CANCEL_INDEX;
import static songscribe.ui.InitialTempoConfirmsTestSupport.NO_INDEX;
import static songscribe.ui.InitialTempoConfirmsTestSupport.YES_INDEX;
import static songscribe.ui.InitialTempoConfirmsTestSupport.stubAnswer;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.hit.HitTarget;
import songscribe.layout.NoteGeometry;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.mutation.Mutation;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.PasteboardAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.undo.UndoTestSupport;

/**
 * Integration tests for the {@code InitialTempoConfirms} wiring in
 * {@link ScoreViewController#handleDelete}, {@link ScoreViewController#handlePaste} and
 * {@link ScoreViewController#tryInsertFragment}. See {@code docs/initial-tempo.md} for the
 * behavior these call sites implement and why the answer has to land inside the caller's
 * bracket.
 *
 * <p>{@code songscribe.ui.edit.PasteModeManager.placeAtTarget} is the fourth call site (the
 * click-to-place paste path). It is a genuinely separate call site — not the same code reached
 * another way — so it is covered where it lives, in {@code PasteModeManagerTest}, and nothing
 * here stands in for it.
 */
class InitialTempoWiringTest extends UnitTest {

    // Wide enough that a one-note paste/insert never trips the LINE_FULL fit gate.
    private static final double WIDE_LINE_WIDTH_SS = 500;

    // Measuring a projected line reads accidental widths out of a static table that has to be
    // built first. Without this, tryInsertFragment's fit gate crashes the JVM through
    // RuntimeError.exit whenever this class runs before whatever else happens to have built it.
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static Song wideSong() {
        var song = new Song();
        song.withoutMutationTracking(() -> song.setLineWidthSs(WIDE_LINE_WIDTH_SS));
        return song;
    }

    /**
     * A one-element clipboard fragment captured from index 1 of a two-note source line, so
     * {@link Fragment}'s anchor-stripping rule (which only strips a capture starting at index 0
     * of the source's own first line) never applies to it. {@code decorate} attaches whatever
     * the captured element should carry into the destination.
     */
    private static Fragment fragmentFromSecondNote(Consumer<StaffElement> decorate) {
        var sourceLine = detachedLine();
        var sourceFirst = crotchet();
        var sourceSecond = crotchet();
        sourceLine.addElement(sourceFirst);
        sourceLine.addElement(sourceSecond);
        decorate.accept(sourceSecond);

        return Fragment.capture(sourceLine, 1, 1);
    }

    /** A fragment whose sole element brings a tempo change of its own — the colliding case. */
    private static Fragment fragmentWithOwnTempo() {
        return fragmentFromSecondNote(element -> attachTempo(element, tempoOf(TARGET_TEMPO_BPM)));
    }

    /** A fragment whose sole element brings nothing — the ordinary, silent-transfer case. */
    private static Fragment fragmentWithNoTempo() {
        return fragmentFromSecondNote(element -> { });
    }

    /** A fragment whose sole element brings both a tempo change and a beat change. */
    private static Fragment fragmentWithTempoAndBeatChange() {
        return fragmentFromSecondNote(element -> {
            attachTempo(element, tempoOf(TARGET_TEMPO_BPM));
            attachBeatChange(element);
        });
    }

    /** A {@link ScoreView} mock reporting {@code song} and holding focus. */
    private static ScoreView focusedScoreMock(Song song) {
        var scoreMock = mock(ScoreView.class);
        when(scoreMock.getSong()).thenReturn(song);
        when(scoreMock.isFocusOwner()).thenReturn(true);
        return scoreMock;
    }

    /**
     * Opens a {@code mockStatic(OptionDialogs.class)} that answers no question at all — for the
     * cases that must not ask one. {@code showOptionDialog} is left unstubbed so the assertion
     * is about it never being reached, not about what it would have returned.
     */
    private static MockedStatic<OptionDialogs> stubNoAnswer() {
        return mockStatic(OptionDialogs.class);
    }

    /** Asserts the transfer confirm was never raised. */
    private static void assertNoTransferPrompt(MockedStatic<OptionDialogs> dialogs) {
        dialogs.verify(() -> OptionDialogs.showOptionDialog(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any()), never());
    }

    private static void assertTempoIs(StaffElement element, int expectedBpm) {
        var attachment = element.findAttachment(TempoChangeAttachment.class);
        assertThat(attachment).isNotNull();
        assertThat(attachment.getTempo().getVisibleTempo()).isEqualTo(expectedBpm);
    }

    private static void assertSongTempoIs(Song song, int expectedBpm) {
        var songTempo = song.getTempo();
        assertThat(songTempo).isNotNull();
        assertThat(songTempo.getVisibleTempo()).isEqualTo(expectedBpm);
    }

    /**
     * Captures {@code edit} as the single undo step it must be. Every edit driven here opens its
     * own modification bracket, so no bracket is opened around it — one wrapped here would hide
     * an edit that came apart into two steps, which is the failure the outer-bracket design
     * exists to prevent.
     */
    private static List<Mutation> captureOneStep(Song song, Runnable edit) {
        return UndoTestSupport.captureSingleBatch(song, edit);
    }

    // -------------------------------------------------------------------------
    // handleDelete — range-delete branch
    // -------------------------------------------------------------------------

    @Nested
    class RangeDelete {

        private record Fixture(
            Song song, Line line, StaffElement oldAnchor, StaffElement newAnchor,
            SelectionCoordinator coordinator, ScoreViewController controller) {
        }

        /** The colliding case: the element that becomes first brings a tempo of its own. */
        private Fixture buildFixture() {
            return buildFixture(true);
        }

        private Fixture buildFixture(boolean newAnchorHasOwnTempo) {
            var song = new Song();
            var line = song.getLine(0);
            var oldAnchor = crotchet();
            var newAnchor = crotchet();
            var trailing = crotchet();
            song.withoutMutationTracking(() -> {
                line.addElement(oldAnchor);
                line.addElement(newAnchor);
                line.addElement(trailing);
            });
            attachTempo(oldAnchor, tempoOf(ORIGINAL_TEMPO_BPM));

            if (newAnchorHasOwnTempo) {
                attachTempo(newAnchor, tempoOf(TARGET_TEMPO_BPM));
            }

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var controller = new ScoreViewController(
                focusedScoreMock(song), mock(MusicEditOperations.class), coordinator,
                mock(ClipboardManager.class));

            return new Fixture(song, line, oldAnchor, newAnchor, coordinator, controller);
        }

        @Test
        void testCancelLeavesEverythingUntouched() {
            var fixture = buildFixture();
            var elementCountBefore = fixture.line().elementCount();

            try (var stub = stubAnswer(CANCEL_INDEX)) {
                fixture.controller().handleDelete();
            }

            assertThat(fixture.line().elementCount()).isEqualTo(elementCountBefore);
            assertThat(fixture.line().getElementIndex(fixture.oldAnchor())).isEqualTo(0);
            assertThat(fixture.oldAnchor().findAttachment(TempoChangeAttachment.class)).isNotNull();
            assertThat(fixture.coordinator().getRange())
                .as("the selection survives a cancelled tempo prompt")
                .isNotNull();
        }

        @Test
        void testNoProceedsAndTheNewFirstElementKeepsItsOwnTempo() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(NO_INDEX)) {
                fixture.controller().handleDelete();
            }

            assertThat(fixture.line().getElementIndex(fixture.oldAnchor())).isEqualTo(-1);
            assertTempoIs(fixture.newAnchor(), TARGET_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), TARGET_TEMPO_BPM);
        }

        @Test
        void testYesProceedsAndRestoresTheOriginalStartingTempo() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(YES_INDEX)) {
                fixture.controller().handleDelete();
            }

            assertTempoIs(fixture.newAnchor(), ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testNoCollisionTransfersSilentlyWithoutAskingAnything() {
            // The ordinary case, and the one every other fixture here deliberately avoids: the
            // incoming first element has no tempo of its own, so there is nothing to choose
            // between and asking would be a spurious prompt on an everyday deletion.
            var fixture = buildFixture(false);

            try (var dialogs = stubNoAnswer()) {
                fixture.controller().handleDelete();

                assertNoTransferPrompt(dialogs);
            }

            assertTempoIs(fixture.newAnchor(), ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testUndoAfterYesRestoresThePreEditStateInOneStep() {
            var fixture = buildFixture();
            var before = UndoTestSupport.serialize(fixture.song());

            List<Mutation> batch;

            try (var stub = stubAnswer(YES_INDEX)) {
                batch = captureOneStep(
                    fixture.song(), () -> fixture.controller().handleDelete());
            }

            var after = UndoTestSupport.serialize(fixture.song());
            assertThat(after).isNotEqualTo(before);

            var scoreView = UndoTestSupport.scoreViewFor(fixture.song());
            UndoTestSupport.replayUndo(scoreView, batch);
            assertThat(UndoTestSupport.serialize(fixture.song()))
                .as("undo must restore the exact pre-edit state, in one step")
                .isEqualTo(before);

            UndoTestSupport.replayRedo(scoreView, batch);
            assertThat(UndoTestSupport.serialize(fixture.song())).isEqualTo(after);
        }
    }

    // -------------------------------------------------------------------------
    // handleDelete — line-delete branch
    // -------------------------------------------------------------------------

    @Nested
    class LineDelete {

        private record Fixture(
            Song song, StaffElement oldAnchor, StaffElement newFirstElement,
            ScoreView scoreMock, ScoreViewController controller) {
        }

        /** The colliding case: the element that becomes first brings a tempo of its own. */
        private Fixture buildFixture() {
            return buildFixture(true);
        }

        private Fixture buildFixture(boolean newFirstElementHasOwnTempo) {
            var song = new Song();
            var line0 = song.getLine(0);
            var oldAnchor = crotchet();
            song.withoutMutationTracking(() -> line0.addElement(oldAnchor));
            attachTempo(oldAnchor, tempoOf(ORIGINAL_TEMPO_BPM));

            var newFirstElement = crotchet();
            song.withoutMutationTracking(() -> {
                var line1 = new Line(song);
                line1.addElement(newFirstElement);
                song.addLine(line1);
            });

            if (newFirstElementHasOwnTempo) {
                attachTempo(newFirstElement, tempoOf(TARGET_TEMPO_BPM));
            }

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line0);
            coordinator.select(new HitTarget.StaffLine());

            var scoreMock = focusedScoreMock(song);
            when(scoreMock.canDeleteLine()).thenReturn(true);

            var controller = new ScoreViewController(
                scoreMock, mock(MusicEditOperations.class), coordinator, mock(ClipboardManager.class));

            return new Fixture(song, oldAnchor, newFirstElement, scoreMock, controller);
        }

        @Test
        void testCancelLeavesEverythingUntouched() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(CANCEL_INDEX)) {
                fixture.controller().handleDelete();
            }

            assertThat(fixture.song().lineCount()).isEqualTo(2);
            assertThat(fixture.song().getLine(0).getElement(0)).isSameAs(fixture.oldAnchor());
            // handleDelete's shared tail (which would otherwise drop the selection via
            // score.deselect()) is skipped entirely on a cancelled tempo prompt — the only
            // observable proof available here, since scoreMock is fully mocked.
            verify(fixture.scoreMock(), never()).deselect();
        }

        @Test
        void testNoProceedsAndTheNewFirstElementKeepsItsOwnTempo() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(NO_INDEX)) {
                fixture.controller().handleDelete();
            }

            assertThat(fixture.song().lineCount()).isEqualTo(1);
            assertThat(fixture.song().getLine(0).getElement(0)).isSameAs(fixture.newFirstElement());
            assertTempoIs(fixture.newFirstElement(), TARGET_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), TARGET_TEMPO_BPM);
            verify(fixture.scoreMock()).deselect();
        }

        @Test
        void testYesProceedsAndRestoresTheOriginalStartingTempo() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(YES_INDEX)) {
                fixture.controller().handleDelete();
            }

            assertTempoIs(fixture.newFirstElement(), ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
            verify(fixture.scoreMock()).deselect();
        }

        @Test
        void testNoCollisionTransfersSilentlyWithoutAskingAnything() {
            var fixture = buildFixture(false);

            try (var dialogs = stubNoAnswer()) {
                fixture.controller().handleDelete();

                assertNoTransferPrompt(dialogs);
            }

            assertTempoIs(fixture.newFirstElement(), ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testUndoAfterYesRestoresThePreEditStateInOneStep() {
            var fixture = buildFixture();
            var before = UndoTestSupport.serialize(fixture.song());

            List<Mutation> batch;

            try (var stub = stubAnswer(YES_INDEX)) {
                batch = captureOneStep(
                    fixture.song(), () -> fixture.controller().handleDelete());
            }

            var after = UndoTestSupport.serialize(fixture.song());
            assertThat(after).isNotEqualTo(before);

            var scoreView = UndoTestSupport.scoreViewFor(fixture.song());
            UndoTestSupport.replayUndo(scoreView, batch);
            assertThat(UndoTestSupport.serialize(fixture.song()))
                .as("undo must restore the exact pre-edit state, in one step")
                .isEqualTo(before);

            UndoTestSupport.replayRedo(scoreView, batch);
            assertThat(UndoTestSupport.serialize(fixture.song())).isEqualTo(after);
        }
    }

    // -------------------------------------------------------------------------
    // handlePaste — paste-over-a-selection branch
    // -------------------------------------------------------------------------

    @Nested
    class PasteOverSelection {

        private record Fixture(
            Song song, Line line, StaffElement oldAnchor,
            SelectionCoordinator coordinator, ScoreViewController controller) {
        }

        /** The colliding case: the pasted element brings a tempo of its own. */
        private Fixture buildFixture() {
            return buildFixture(fragmentWithOwnTempo());
        }

        private Fixture buildFixture(Fragment fragment) {
            var song = wideSong();
            var line = song.getLine(0);
            var oldAnchor = crotchet();
            var trailing = crotchet();
            song.withoutMutationTracking(() -> {
                line.addElement(oldAnchor);
                line.addElement(trailing);
            });
            attachTempo(oldAnchor, tempoOf(ORIGINAL_TEMPO_BPM));

            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(fragment);

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 0);

            var controller = new ScoreViewController(
                focusedScoreMock(song), mock(MusicEditOperations.class), coordinator, clipboardManager);

            return new Fixture(song, line, oldAnchor, coordinator, controller);
        }

        private static void paste(ScoreViewController controller) {
            controller.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.PASTE));
        }

        @Test
        void testCancelLeavesEverythingUntouched() {
            var fixture = buildFixture();
            var elementCountBefore = fixture.line().elementCount();

            try (var stub = stubAnswer(CANCEL_INDEX)) {
                paste(fixture.controller());
            }

            assertThat(fixture.line().elementCount()).isEqualTo(elementCountBefore);
            assertThat(fixture.line().getElementIndex(fixture.oldAnchor())).isEqualTo(0);
            assertThat(fixture.oldAnchor().findAttachment(TempoChangeAttachment.class)).isNotNull();
            assertThat(fixture.coordinator().getRange())
                .as("the selection survives a cancelled tempo prompt")
                .isNotNull();
        }

        @Test
        void testNoProceedsAndTheNewFirstElementKeepsItsOwnTempo() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(NO_INDEX)) {
                paste(fixture.controller());
            }

            var newAnchor = fixture.line().getElement(0);
            assertThat(newAnchor).isNotSameAs(fixture.oldAnchor());
            assertTempoIs(newAnchor, TARGET_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), TARGET_TEMPO_BPM);
        }

        @Test
        void testYesProceedsAndRestoresTheOriginalStartingTempo() {
            var fixture = buildFixture();

            try (var stub = stubAnswer(YES_INDEX)) {
                paste(fixture.controller());
            }

            assertTempoIs(fixture.line().getElement(0), ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testNoCollisionTransfersSilentlyWithoutAskingAnything() {
            var fixture = buildFixture(fragmentWithNoTempo());

            try (var dialogs = stubNoAnswer()) {
                paste(fixture.controller());

                assertNoTransferPrompt(dialogs);
            }

            var newAnchor = fixture.line().getElement(0);
            assertThat(newAnchor).isNotSameAs(fixture.oldAnchor());
            assertTempoIs(newAnchor, ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testUndoAfterYesRestoresThePreEditStateInOneStep() {
            // A raw XML byte-compare (as the delete branches above use) is too strict for a
            // paste: InsertionSpacingCalculator's trailing-element x-offset shift is applied as
            // a plain field write, not a tracked Mutation, so it is not restored by undo replay
            // alone — it is corrected by the next real layout pass in the live app instead.
            // Structural identity and the tempo attachment are what the outer-bracket design
            // actually promises to restore in one step, so those are what this asserts.
            var fixture = buildFixture();
            var elementCountBefore = fixture.line().elementCount();

            List<Mutation> batch;

            try (var stub = stubAnswer(YES_INDEX)) {
                batch = captureOneStep(fixture.song(), () -> paste(fixture.controller()));
            }

            assertThat(fixture.line().getElement(0)).isNotSameAs(fixture.oldAnchor());

            var scoreView = UndoTestSupport.scoreViewFor(fixture.song());
            UndoTestSupport.replayUndo(scoreView, batch);

            assertThat(fixture.line().elementCount())
                .as("undo must restore the exact pre-edit element count, in one step")
                .isEqualTo(elementCountBefore);
            assertThat(fixture.line().getElement(0))
                .as("undo must restore the original anchor element itself")
                .isSameAs(fixture.oldAnchor());
            assertTempoIs(fixture.oldAnchor(), ORIGINAL_TEMPO_BPM);

            UndoTestSupport.replayRedo(scoreView, batch);

            assertThat(fixture.line().getElement(0))
                .as("redo must re-apply the paste")
                .isNotSameAs(fixture.oldAnchor());
            assertTempoIs(fixture.line().getElement(0), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testWarnIfTempoAndBeatChangeFiresAfterTheBracketClosesWhenTheNewFirstElementEndsUpWithBoth() {
            // The pasted element brings a beat change along with its tempo, so whichever tempo
            // wins, the song's new first element ends up carrying both.
            var fixture = buildFixture(fragmentWithTempoAndBeatChange());

            try (var dialogs = stubAnswer(NO_INDEX)) {
                paste(fixture.controller());

                dialogs.verify(() -> OptionDialogs.showWarningMessage(any(), any(), any()));
            }
        }

        @Test
        void testNoWarningWhenThePasteLeavesTheSongsFirstElementAlone() {
            // The song's first note already has both a tempo and a beat change — a state the
            // beat-change dialog can produce with no paste involved. A paste elsewhere in the
            // piece changed nothing about it and must not blame the user for it.
            var fixture = buildFixture(fragmentWithNoTempo());
            attachBeatChange(fixture.oldAnchor());
            ReflectionTestHelper.selectRange(fixture.coordinator(), 1, 1);

            try (var dialogs = stubNoAnswer()) {
                paste(fixture.controller());

                dialogs.verify(
                    () -> OptionDialogs.showWarningMessage(any(), any(), any()), never());
            }

            assertThat(fixture.line().getElement(0))
                .as("the paste landed past the song's first element, leaving it in place")
                .isSameAs(fixture.oldAnchor());
        }
    }

    // -------------------------------------------------------------------------
    // tryInsertFragment — pure insertion before the current first element
    // -------------------------------------------------------------------------

    @Nested
    class PureInsertion {

        private record Fixture(
            Song song, Line line, StaffElement oldAnchor, ScoreViewController controller) {
        }

        /** The colliding case: the pasted element brings a tempo of its own. */
        private Fixture buildFixture() {
            return buildFixture(fragmentWithOwnTempo());
        }

        private Fixture buildFixture(Fragment fragment) {
            var song = wideSong();
            var line = song.getLine(0);
            var oldAnchor = crotchet();
            song.withoutMutationTracking(() -> line.addElement(oldAnchor));
            attachTempo(oldAnchor, tempoOf(ORIGINAL_TEMPO_BPM));

            return new Fixture(song, line, oldAnchor, controllerFor(song, fragment));
        }

        /**
         * The same fixture with a genuinely empty line pushed in front of it, so the anchor sits
         * on line 1 while the insertion goes into line 0 — the leading-empty-line case, where an
         * insertion displaces a tempo that is not on the insertion's own line at all.
         */
        private Fixture buildLeadingEmptyLineFixture(Fragment fragment) {
            var base = buildFixture(fragment);
            var song = base.song();
            var emptyFirstLine = new Line(song);
            song.withoutMutationTracking(() -> song.addLine(0, emptyFirstLine));

            return new Fixture(song, emptyFirstLine, base.oldAnchor(), base.controller());
        }

        private ScoreViewController controllerFor(Song song, Fragment fragment) {
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(fragment);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);

            return new ScoreViewController(
                scoreMock, mock(MusicEditOperations.class), mock(SelectionCoordinator.class),
                clipboardManager);
        }

        private static ScoreViewController.FragmentInsertOutcome insertAtFront(Fixture fixture) {
            return fixture.song().withModificationResult(
                () -> fixture.controller().tryInsertFragment(fixture.line(), 0, null));
        }

        @Test
        void testCancelLeavesTheLineUntouched() {
            var fixture = buildFixture();
            var elementCountBefore = fixture.line().elementCount();
            ScoreViewController.FragmentInsertOutcome outcome;

            try (var stub = stubAnswer(CANCEL_INDEX)) {
                outcome = insertAtFront(fixture);
            }

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.CANCELLED);
            assertThat(fixture.line().elementCount()).isEqualTo(elementCountBefore);
            assertThat(fixture.line().getElement(0)).isSameAs(fixture.oldAnchor());
        }

        @Test
        void testNoProceedsAndTheIncomingElementKeepsItsOwnTempo() {
            var fixture = buildFixture();
            ScoreViewController.FragmentInsertOutcome outcome;

            try (var stub = stubAnswer(NO_INDEX)) {
                outcome = insertAtFront(fixture);
            }

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            var newAnchor = fixture.line().getElement(0);
            assertThat(newAnchor).isNotSameAs(fixture.oldAnchor());
            assertTempoIs(newAnchor, TARGET_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), TARGET_TEMPO_BPM);
        }

        @Test
        void testYesProceedsAndRestoresTheOriginalStartingTempo() {
            var fixture = buildFixture();
            ScoreViewController.FragmentInsertOutcome outcome;

            try (var stub = stubAnswer(YES_INDEX)) {
                outcome = insertAtFront(fixture);
            }

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            assertTempoIs(fixture.line().getElement(0), ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testNoCollisionTransfersSilentlyWithoutAskingAnything() {
            var fixture = buildFixture(fragmentWithNoTempo());
            ScoreViewController.FragmentInsertOutcome outcome;

            try (var dialogs = stubNoAnswer()) {
                outcome = insertAtFront(fixture);

                assertNoTransferPrompt(dialogs);
            }

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            var newAnchor = fixture.line().getElement(0);
            assertThat(newAnchor).isNotSameAs(fixture.oldAnchor());
            assertTempoIs(newAnchor, ORIGINAL_TEMPO_BPM);
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }

        @Test
        void testInsertingIntoALeadingEmptyLineTakesTheTempoFromTheLineBelow() {
            // The controller has to recognize that inserting into an empty line 0 displaces an
            // anchor that lives on line 1 — a case isInitialTempoAnchor alone cannot see, since
            // an empty line is never the anchor line.
            var fixture = buildLeadingEmptyLineFixture(fragmentWithOwnTempo());
            assertThat(fixture.line().isEmpty())
                .as("pre-condition: the insertion's own line holds nothing, not even a terminal")
                .isTrue();
            assertThat(fixture.song().initialTempoAnchor())
                .as("pre-condition: the anchor is on the line below")
                .isSameAs(fixture.oldAnchor());

            ScoreViewController.FragmentInsertOutcome outcome;

            try (var stub = stubAnswer(YES_INDEX)) {
                outcome = insertAtFront(fixture);
            }

            assertThat(outcome).isEqualTo(ScoreViewController.FragmentInsertOutcome.INSERTED);
            assertTempoIs(fixture.line().getElement(0), ORIGINAL_TEMPO_BPM);
            assertThat(fixture.oldAnchor().findAttachment(TempoChangeAttachment.class))
                .as("the tempo left the line below rather than being duplicated")
                .isNull();
            assertSongTempoIs(fixture.song(), ORIGINAL_TEMPO_BPM);
        }
    }
}
