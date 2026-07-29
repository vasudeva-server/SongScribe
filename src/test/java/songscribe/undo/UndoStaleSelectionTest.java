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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.mockito.MockedStatic;

import net.engio.mbassy.listener.Handler;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
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
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * The undo-side mirror of {@code ScoreViewControllerTest.HandleDelete}'s
 * {@code testHandleDeleteAllButLastFewNotesDoesNotCrashSongDidChangeHandlers}.
 *
 * <p>Forward delete clears the selection itself before shrinking the line, so it never
 * needs the guard in {@code ScoreViewController.songDidChange}. Undo has no such courtesy:
 * undoing an insertion removes elements and leaves the selection exactly as it was, so the
 * selected range is left running past the end of the line. The first handler that walks
 * that range indexes off the end and throws.
 *
 * <p>What makes this worth testing through the bus rather than by calling the handler
 * directly is the ordering. The guard only helps if it runs before the handlers that read
 * the range, and nothing in {@code ScoreViewController} enforces that — it rests entirely
 * on {@code TUPLET_INFO_CACHE_PRIORITY} outranking every other subscriber to this
 * notification. A direct call cannot see that; it would keep passing if the priority were
 * dropped tomorrow. So this test posts a real undo through the real bus and reads the
 * selection from a subscriber at {@link Message#MEDIUM_PRIORITY} — the priority
 * {@link UIAction#songDidChange} actually uses, which is how the crash reached users
 * (every {@code UIAction} re-derives its enabled state there, and the trill and tuplet
 * actions do it by walking the selected range).
 *
 * <p>Lives in {@code songscribe.undo} rather than beside the other selection tests because
 * it needs {@link UndoController#resetForTest()}, which is package-private.
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
        UndoController.resetForTest();

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
        UndoController.resetForTest();
    }

    /**
     * Builds the line, wires a real coordinator and controller to it, and leaves one
     * undoable insertion on the stack with the whole line selected — the state a user is
     * in when they select everything and then press undo.
     */
    private LineSelectionState selectEverythingOverAnUndoableInsertion() {
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < BASE_NOTE_COUNT; i++) {
                line.addElement(ElementType.QUAVER.newInstance());
            }
        });

        song.withModification(() -> {
            for (var i = 0; i < INSERTED_NOTE_COUNT; i++) {
                line.addElement(ElementType.QUAVER.newInstance());
            }
        });

        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);

        // A real controller, subscribed to the real bus by its own constructor. The
        // operations are mocked only because the tuplet cache warm-up is beside the point
        // here; the selection this test watches is the coordinator's, which is real.
        controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            coordinator,
            mock(ClipboardManager.class)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, SELECTION_END_INDEX);

        var state = coordinator.getActiveSelection();

        if (state == null) {
            throw new IllegalStateException("Expected an active selection");
        }

        return state;
    }

    @Test
    void testUndoingAnInsertionDoesNotLeaveAStaleSelectionForLaterHandlers() {
        var state = selectEverythingOverAnUndoableInsertion();

        var probeRan = new boolean[1];
        var caughtDuringNotification = new Exception[1];

        var listener = new Object() {
            @Handler(priority = Message.MEDIUM_PRIORITY)
            void onSongDidChange(SongDidChangeNotification notification) {
                probeRan[0] = true;

                try {
                    // Mirrors UIAction.songDidChange re-deriving an action's enabled state
                    // by walking the selected range while the song is changing.
                    state.canToggleTrill();
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
        assertThat(state.hasElementSelection())
            .as("the stranded selection is dropped, not merely survived")
            .isFalse();
    }
}
