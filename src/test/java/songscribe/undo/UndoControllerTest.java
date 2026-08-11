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
 * Exercises {@link UndoController}'s stack contract — what each of
 * {@link UndoController#songDidChange}, {@link UndoController#undo()} and
 * {@link UndoController#redo()} promises about what is available to undo or redo next.
 * The clean marker and the labels are contracts of the same class exercised elsewhere:
 * {@link UndoControllerSavePointTest} and {@link UndoOpNameLabelTest}.
 *
 * <p><b>State transitions</b> — the three moves that change what is available, asserted
 * through {@link UndoController#canUndo()}/{@link UndoController#canRedo()} and the
 * document itself: a forward edit makes undo available and redo not; undo makes the step
 * redoable and restores the prior document; redo restores the edited one.
 *
 * <p><b>Redo is a line, not a tree</b> — a forward edit made after an undo discards the
 * redo branch rather than growing one.
 *
 * <p><b>Replay is invisible to recording</b> — the notification the engine's own replay
 * bracket posts is not itself recorded, so a single undo empties a one-step stack instead
 * of pushing a step for its own inverse. The class invariant, not a branch.
 *
 * <p><b>The depth boundary</b> — {@value UndoController#UNDO_STACK_MAX_DEPTH} steps are
 * retained and pushing past it evicts the oldest, tested one step past the limit and
 * asserted by exhausting the stack: exactly the limit is undoable, and the evicted edit
 * remains in the document because nothing can reach it.
 *
 * <p><b>The fail-safe</b> — a replay that throws is an engine bug, so both stacks are
 * cleared and the document forced modified, from the undo direction and the redo
 * direction. Arranged with a mutation whose replay cannot succeed.
 *
 * <p><b>Not covered here</b>: that undo and redo are no-ops posting nothing when their
 * stack is empty or no document is open — reachable only through
 * {@link MainFrame#getInstance()}, and belonging with the save-point tests that already
 * drive that seam.
 *
 * <p>Steps are produced by driving real edits on a {@link Song} rather than by handing the
 * controller a hand-built batch, so what is recorded is what production records, companion
 * mutations included. {@link MainFrame#getInstance()} is mocked to return a
 * {@link ScoreView} over that same song, which is how undo/redo find the active document.
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
