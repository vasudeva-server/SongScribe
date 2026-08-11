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

package songscribe.undo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.quaver;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.mockito.MockedStatic;

import net.engio.mbassy.listener.Handler;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.RangeQueries;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.ReflectionTestHelper;

/**
 * The undo-side mirror of {@code ScoreViewControllerTest.HandleDelete}'s
 * {@code testHandleDeleteAllButLastFewNotesDoesNotCrashSongDidChangeHandlers}.
 *
 * <p>Forward delete clears the selection itself before shrinking the line, so it never
 * needs the guard in {@code ScoreViewController.songDidChange}. Undo has no such courtesy:
 * undoing an insertion removes elements while the selection is still naming them by index.
 * What keeps that safe is {@link SelectionCoordinator#revalidateElementSelection}, which
 * splices the range through every mutation in the undo batch as it replays — the range goes
 * on naming the same surviving elements it named before, sliding or shrinking as needed, and
 * is only cleared outright once nothing of it survives. This class exercises that splice
 * end to end, through a real undo on the real message bus, rather than merely proving the
 * old crash can no longer happen.
 *
 * <p>What makes this worth testing through the bus rather than by calling the handler
 * directly is the ordering. The splice only helps if it runs before the handlers that read
 * the range, and nothing in {@code ScoreViewController} enforces that — it rests entirely
 * on {@code TUPLET_INFO_CACHE_PRIORITY} outranking every other subscriber to this
 * notification. A direct call cannot see that; it would keep passing if the priority were
 * dropped tomorrow. So {@link #testUndoingAnInsertionDoesNotLeaveAStaleSelectionForLaterHandlers}
 * posts a real undo through the real bus and reads the selection from a subscriber at
 * {@link Message#MEDIUM_PRIORITY} — the priority {@link UIAction#songDidChange} actually
 * uses, which is how the crash this guards against reached users (every {@code UIAction}
 * re-derives its enabled state there, and the trill and tuplet actions do it by walking
 * the selected range).
 *
 * <h2>What this class is responsible for</h2>
 * The live-selection guarantee of {@code docs/undo.md}, tested as the invariant it is —
 * the range goes on naming the same surviving elements — rather than as the absence of the
 * crash that prompted it.
 *
 * <p><b>The classes of mutation the range must survive</b>: one before it (the range slides
 * down, same elements), one inside it (the range shrinks by one, remaining elements
 * unchanged), and one on another line (the range is untouched). Together with the ordering
 * case above, these are the ways a replayed batch can reach a selection.
 *
 * <p><b>The round trip</b> — undo followed by redo returns the range exactly where it
 * started, which is the invariant the individual splices have to compose into.
 *
 * <p><b>Not covered here</b>: the splice itself, case by case, which is
 * {@code SelectionCoordinatorRangeTest}'s subject and includes the clearing of a range
 * nothing survives of. This class covers only what undo owes: that the splice runs, and
 * runs first.
 *
 * <p>Lives in {@code songscribe.undo} because what it asserts is an undo guarantee, not
 * because of any access it needs — {@link UndoController#reset()} is public API since the
 * lifecycle contracts landed.
 */
class UndoStaleSelectionTest extends UnitTest {

    /** Notes placed on the line untracked, so undo cannot remove them. */
    private static final int BASE_NOTE_COUNT = 3;

    /** Notes added in one tracked bracket — the single insertion the test undoes. */
    private static final int INSERTED_NOTE_COUNT = 2;

    /** Last note index while the insertion stands; the selection is extended to here. */
    private static final int SELECTION_END_INDEX = BASE_NOTE_COUNT + INSERTED_NOTE_COUNT - 1;

    private Song song;
    private Line line;
    private MockedStatic<MainFrame> mainFrameMock;

    @Nullable
    private ScoreViewController controller;

    @BeforeEach
    void setUp() {
        UndoController.initialize();
        UndoController.reset();

        song = new Song();
        line = song.getLine(0);

        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        var mainFrame = mock(MainFrame.class);
        when(mainFrame.getScoreView()).thenReturn(scoreView);
        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mainFrame);
    }

    // The controller subscribes itself to the shared message bus in its constructor.
    // MBassador holds subscribers weakly, so leaving it subscribed would let it linger
    // until GC and go on handling later tests' notifications against this test's
    // torn-down mocks. Unsubscribe unconditionally.
    @AfterEach
    void tearDown() {
        if (controller != null) {
            MessageCenter.unsubscribe(controller);
        }

        mainFrameMock.close();
    }

    /**
     * Builds a real controller wired to {@code coordinator} and subscribes it to the real
     * bus, storing it in {@link #controller} so {@link #tearDown} unsubscribes it. The
     * operations are mocked only because the tuplet cache warm-up is beside the point in
     * these tests; the selection every test here watches is the coordinator's, which is
     * real.
     */
    private ScoreViewController buildController(SelectionCoordinator coordinator) {
        controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            coordinator,
            mock(ClipboardManager.class)
        );

        return controller;
    }

    /**
     * Builds the line, wires a real coordinator and controller to it, and leaves one
     * undoable insertion on the stack with the whole line selected — the state a user is
     * in when they select everything and then press undo.
     */
    private SelectionCoordinator selectEverythingOverAnUndoableInsertion() {
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < BASE_NOTE_COUNT; i++) {
                line.addElement(quaver());
            }
        });

        song.withModification(() -> {
            for (var i = 0; i < INSERTED_NOTE_COUNT; i++) {
                line.addElement(quaver());
            }
        });

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        buildController(coordinator);

        ReflectionTestHelper.selectRange(coordinator, 0, SELECTION_END_INDEX);

        if (coordinator.getRange() == null) {
            throw new IllegalStateException("Expected an active selection");
        }

        return coordinator;
    }

    @Test
    void testUndoingAnInsertionDoesNotLeaveAStaleSelectionForLaterHandlers() {
        var coordinator = selectEverythingOverAnUndoableInsertion();

        // The base notes the undo must not touch, captured by identity before the undo
        // runs — the splice is expected to leave the range naming exactly these.
        var baseNotesBeforeUndo = List.copyOf(line.getElements(0, BASE_NOTE_COUNT - 1));

        var probeRan = new boolean[1];
        var caughtDuringNotification = new Exception[1];

        var listener = new Object() {
            @Handler(priority = Message.MEDIUM_PRIORITY)
            void onSongDidChange(SongDidChangeNotification notification) {
                probeRan[0] = true;

                try {
                    // Mirrors UIAction.songDidChange re-deriving an action's enabled state
                    // by walking the selected range while the song is changing.
                    var range = coordinator.getRange();

                    if (range != null) {
                        RangeQueries.canToggleTrill(range.toElementSelection());
                    }
                } catch (Exception e) {
                    caughtDuringNotification[0] = e;
                }
            }
        };

        MessageCenter.subscribe(listener);

        try {
            UndoController.undo();
        } finally {
            MessageCenter.unsubscribe(listener);
        }

        assertThat(probeRan[0])
            .as("the undo must actually reach songDidChange subscribers, or this test proves nothing")
            .isTrue();
        assertThat(line.elementCount())
            .as("precondition: the undo removed the inserted notes, stranding the selected range")
            .isEqualTo(BASE_NOTE_COUNT + 1);
        assertThat(caughtDuringNotification[0])
            .as("songDidChange handlers must not see a selection running past the end of the line")
            .isNull();

        var rangeAfterUndo = coordinator.getRange();
        assertThat(rangeAfterUndo)
            .as("the undone insertion left the base notes selected — the splice must not clear them")
            .isNotNull();
        assertThat(rangeAfterUndo.begin()).isEqualTo(0);
        assertThat(rangeAfterUndo.end()).isEqualTo(BASE_NOTE_COUNT - 1);
        assertThat(line.getElements(rangeAfterUndo.begin(), rangeAfterUndo.end()))
            .as("the range must keep naming the same elements it named before the undo")
            .isEqualTo(baseNotesBeforeUndo);
    }

    @Test
    void testUndoOfInsertionBeforeTheRangeSlidesTheRangeDownButKeepsTheSameElements() {
        // Layout after the tracked insertion: [X, Y, A, B, C] — X and Y are undone; A, B
        // and C are the untouched notes the range must go on naming.
        final var precedingInsertionCount = 2;
        final var rangeNoteCount = 3;

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < rangeNoteCount; i++) {
                line.addElement(quaver());
            }
        });

        var rangeNotesBeforeInsertion = List.copyOf(line.getElements(0, rangeNoteCount - 1));

        song.withModification(() -> {
            for (var i = 0; i < precedingInsertionCount; i++) {
                line.addElement(i, quaver());
            }
        });

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        buildController(coordinator);

        var rangeBegin = precedingInsertionCount;
        var rangeEnd = precedingInsertionCount + rangeNoteCount - 1;
        ReflectionTestHelper.selectRange(coordinator, rangeBegin, rangeEnd);

        UndoController.undo();

        var range = coordinator.getRange();
        assertThat(range).as("expected the range to survive the splice").isNotNull();
        assertThat(range.begin())
            .as("the range slides down by exactly the two elements removed ahead of it")
            .isEqualTo(0);
        assertThat(range.end()).isEqualTo(rangeNoteCount - 1);
        assertThat(line.getElements(range.begin(), range.end()))
            .as("the range must keep naming the same elements it named before the undo")
            .isEqualTo(rangeNotesBeforeInsertion);
    }

    @Test
    void testUndoOfInsertionInsideTheRangeShrinksTheRangeByOne() {
        // Layout after the tracked insertion: [A, X, B] — X is undone; A and B are the
        // untouched notes on either side of it.
        final var insertedNoteIndex = 1;
        final var rangeEndBeforeUndo = 2;

        var noteA = quaver();
        var noteB = quaver();
        song.withoutMutationTracking(() -> {
            line.addElement(noteA);
            line.addElement(noteB);
        });

        song.withModification(
            () -> line.addElement(insertedNoteIndex, quaver()));

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        buildController(coordinator);

        // [A, X, B] — select the whole thing, anchored at the end.
        ReflectionTestHelper.selectRange(coordinator, rangeEndBeforeUndo, 0);

        UndoController.undo();

        var range = coordinator.getRange();
        assertThat(range).as("expected the range to survive the splice").isNotNull();
        assertThat(range.begin()).isEqualTo(0);
        assertThat(range.end())
            .as("the deletion inside the range shrinks it by exactly the one element removed")
            .isEqualTo(rangeEndBeforeUndo - 1);
        assertThat(line.getElement(range.begin())).isSameAs(noteA);
        assertThat(line.getElement(range.end())).isSameAs(noteB);
    }

    @Test
    void testUndoThenRedoReturnsTheRangeExactlyToWhereItStarted() {
        // Layout after the tracked insertion: [A, X, B] — X is inserted, then undone, then
        // redone. The range is anchored on B rather than A, so a round trip that silently
        // swapped the anchor for the opposite end would still pass on begin/end alone.
        final var insertedNoteIndex = 1;
        final var anchor = 2;

        var noteA = quaver();
        var noteB = quaver();
        song.withoutMutationTracking(() -> {
            line.addElement(noteA);
            line.addElement(noteB);
        });

        song.withModification(
            () -> line.addElement(insertedNoteIndex, quaver()));

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        buildController(coordinator);

        ReflectionTestHelper.selectRange(coordinator, anchor, 0);

        var rangeBeforeUndo = coordinator.getRange();
        assertThat(rangeBeforeUndo).as("expected an active selection").isNotNull();

        UndoController.undo();
        UndoController.redo();

        var rangeAfterRedo = coordinator.getRange();
        assertThat(rangeAfterRedo)
            .as("expected the range to survive the undo/redo round trip")
            .isNotNull();
        assertThat(rangeAfterRedo.begin()).isEqualTo(rangeBeforeUndo.begin());
        assertThat(rangeAfterRedo.end()).isEqualTo(rangeBeforeUndo.end());
        assertThat(rangeAfterRedo.anchor())
            .as("the anchor must round-trip along with begin and end")
            .isEqualTo(rangeBeforeUndo.anchor());
        assertThat(line.getElement(rangeAfterRedo.begin())).isSameAs(noteA);
        assertThat(line.getElement(rangeAfterRedo.end())).isSameAs(noteB);
    }

    @Test
    void testUndoOfAnEditOnADifferentLineLeavesTheRangeUntouched() {
        var otherLine = new Line(song);
        song.withoutMutationTracking(() -> song.addLine(1, otherLine));

        final var rangeNoteCount = 3;

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < rangeNoteCount; i++) {
                line.addElement(quaver());
            }
        });

        song.withModification(() -> otherLine.addElement(quaver()));

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        buildController(coordinator);

        var anchor = rangeNoteCount - 1;
        ReflectionTestHelper.selectRange(coordinator, anchor, 0);

        var rangeBeforeUndo = coordinator.getRange();
        assertThat(rangeBeforeUndo).as("expected an active selection").isNotNull();

        UndoController.undo();

        assertThat(coordinator.getRange())
            .as("an edit undone on another line must not touch this line's selection")
            .isEqualTo(rangeBeforeUndo);
    }
}
