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

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Stack-behavior tests for {@link UndoController}: push/undo/redo transitions,
 * linear-redo clearing, FIFO eviction at the depth limit, the reentrancy guard, and
 * the fail-safe path when a replay throws.
 *
 * <p>Steps are produced by driving real edits on a {@link Song} (the singleton records
 * them via its {@code @Handler}); {@link UndoController#undo()}/{@code redo()} fetch the
 * active document through {@link MainFrame#getInstance()}, which is mocked here to return
 * a {@link ScoreView} over that same song.
 */
class UndoControllerTest extends UnitTest {

    private Song song;
    private Line line;
    private MockedStatic<MainFrame> mainFrameMock;

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

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    /** Appends one crotchet in its own modification bracket, producing one undo step. */
    private void addNoteStep() {
        song.withModification(() -> line.addElement(UndoTestSupport.crotchet()));
    }

    @Test
    void testForwardEditEnablesUndoButNotRedo() {
        addNoteStep();

        assertThat(UndoController.canUndo()).isTrue();
        assertThat(UndoController.canRedo()).isFalse();
    }

    @Test
    void testUndoThenRedoRestoresState() {
        addNoteStep();
        var afterEdit = line.effectiveElementCount();

        UndoController.undo();
        assertThat(UndoController.canUndo()).isFalse();
        assertThat(UndoController.canRedo()).isTrue();
        assertThat(line.effectiveElementCount()).isEqualTo(afterEdit - 1);

        UndoController.redo();
        assertThat(UndoController.canUndo()).isTrue();
        assertThat(UndoController.canRedo()).isFalse();
        assertThat(line.effectiveElementCount()).isEqualTo(afterEdit);
    }

    @Test
    void testNewForwardEditClearsRedoStack() {
        addNoteStep();
        addNoteStep();
        UndoController.undo();
        assertThat(UndoController.canRedo()).isTrue();

        // A fresh forward edit must discard the redo stack (linear redo model).
        addNoteStep();

        assertThat(UndoController.canRedo()).isFalse();
        assertThat(UndoController.canUndo()).isTrue();
    }

    @Test
    void testReplayBatchDoesNotPushANewUndoStep() {
        addNoteStep();

        // Undo replays the batch inside a bracket, which posts its own
        // SongDidChangeNotification. The reentrancy guard must stop that notification
        // from being recorded as a new step — otherwise undo could never empty the stack.
        UndoController.undo();

        assertThat(UndoController.canUndo()).isFalse();
        assertThat(UndoController.canRedo()).isTrue();
    }

    @Test
    void testFifoEvictionDropsOldestBeyondDepthLimit() {
        var pushed = UndoController.UNDO_STACK_MAX_DEPTH + 1;

        for (var i = 0; i < pushed; i++) {
            addNoteStep();
        }

        assertThat(line.effectiveElementCount()).isEqualTo(pushed);

        // Only DEFAULT_UNDO_STACK_MAX_DEPTH steps are retained; the oldest (first) was
        // evicted, so it can never be undone.
        var undoable = 0;
        while (UndoController.canUndo()) {
            UndoController.undo();
            undoable++;
        }

        assertThat(undoable).isEqualTo(UndoController.UNDO_STACK_MAX_DEPTH);
        // The one note added by the evicted step remains — undo could not reach it.
        assertThat(line.effectiveElementCount()).isEqualTo(1);
    }

    @Test
    void testReplayFailureClearsBothStacksAndForcesModified() {
        // A step whose inverse throws: undoing an ElementDeletion re-inserts the element
        // at its stored index — an out-of-range index makes the replay throw.
        var badMutation = new ElementDeletion(line, 999, UndoTestSupport.crotchet());
        MessageCenter.post(new SongDidChangeNotification(List.of(badMutation), song));
        assertThat(UndoController.canUndo()).isTrue();

        UndoController.undo();

        assertThat(UndoController.canUndo()).as("fail-safe clears the undo stack").isFalse();
        assertThat(UndoController.canRedo()).as("fail-safe clears the redo stack").isFalse();
        assertThat(song.isModified())
            .as("invalidating the clean marker forces modified so the user is prompted to save")
            .isTrue();
    }

    @Test
    void testRedoReplayFailureClearsBothStacksAndForcesModified() {
        // Three real steps, then undo the top one so its insertion (stored index 2)
        // sits on the redo stack.
        addNoteStep();
        addNoteStep();
        addNoteStep();
        UndoController.undo();
        assertThat(UndoController.canRedo()).isTrue();

        // Empty the line outside the undo system: the redone step's stored insert
        // index is now strictly past the end, so the forward replay throws and the
        // fail-safe engages via redo()'s handleReplayFailure path.
        song.withoutMutationTracking(() -> {
            while (line.effectiveElementCount() > 0) {
                line.removeElement(line.effectiveElementCount() - 1);
            }
        });
        assertThat(line.effectiveElementCount()).as("sabotage emptied the line").isZero();

        UndoController.redo();

        assertThat(UndoController.canUndo()).as("fail-safe clears the undo stack").isFalse();
        assertThat(UndoController.canRedo()).as("fail-safe clears the redo stack").isFalse();
        assertThat(song.isModified())
            .as("invalidating the clean marker forces modified so the user is prompted to save")
            .isTrue();
    }
}
