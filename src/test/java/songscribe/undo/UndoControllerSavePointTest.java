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
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Save-point (clean-position) tracking in {@link UndoController}: the reference-based
 * clean marker survives undo/redo, is invalidated when the clean step is evicted, and
 * is reset by New/Open. The document's {@code modified} flag is recomputed against it.
 */
class UndoControllerSavePointTest extends UnitTest {

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

    private void addNoteStep() {
        song.withModification(() -> line.addElement(UndoTestSupport.crotchet()));
    }

    private static void save() {
        MessageCenter.post(new DocumentWasSavedNotification());
    }

    @Test
    void testSaveClearsModifiedAtSavePosition() {
        addNoteStep();
        assertThat(song.isModified()).isTrue();

        save();

        assertThat(song.isModified()).as("saving marks the current position clean").isFalse();
    }

    @Test
    void testUndoAndRedoAcrossSavePointToggleModified() {
        addNoteStep();
        save();
        assertThat(song.isModified()).isFalse();

        UndoController.undo();
        assertThat(song.isModified())
            .as("undoing away from the saved position dirties the document")
            .isTrue();

        UndoController.redo();
        assertThat(song.isModified())
            .as("redoing back to the saved position cleans it again")
            .isFalse();
    }

    @Test
    void testEvictingTheCleanStepInvalidatesTheMarker() {
        // Save with the clean step at the bottom of the stack, then push enough edits
        // to evict it. Once the clean step is gone, the saved state is unreachable and
        // the document stays modified until the next save.
        addNoteStep();
        save();
        assertThat(song.isModified()).isFalse();

        for (var i = 0; i < UndoController.DEFAULT_UNDO_STACK_MAX_DEPTH; i++) {
            addNoteStep();
        }

        assertThat(song.isModified())
            .as("evicting the clean step forces modified")
            .isTrue();

        // Undoing every retained step cannot restore cleanliness — the marker is invalid.
        while (UndoController.canUndo()) {
            UndoController.undo();
        }

        assertThat(song.isModified())
            .as("no reachable position is clean after the clean step was evicted")
            .isTrue();
    }

    @Test
    void testDocumentDidLoadClearsBothStacks() {
        addNoteStep();
        addNoteStep();
        assertThat(UndoController.canUndo()).isTrue();

        MessageCenter.post(new DocumentDidLoadNotification(song));

        assertThat(UndoController.canUndo()).as("New/Open clears the undo stack").isFalse();
        assertThat(UndoController.canRedo()).as("New/Open clears the redo stack").isFalse();

        // After load the baseline is the clean position: a save with an empty stack keeps
        // the document clean.
        save();
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testRedoClearDestroyingCleanStepLeavesDocumentPermanentlyModified() {
        // Saved position on top, then undo past it so the clean step sits on the redo
        // stack; a new forward edit clears the redo stack and destroys the clean step.
        addNoteStep();
        addNoteStep();
        save();
        assertThat(song.isModified()).isFalse();

        UndoController.undo();
        assertThat(song.isModified()).isTrue();

        // New edit clears redo — the clean step is now unreachable.
        addNoteStep();
        assertThat(song.isModified()).isTrue();

        // No amount of undoing can recover the saved state.
        while (UndoController.canUndo()) {
            UndoController.undo();
        }

        assertThat(song.isModified())
            .as("the saved state became unreachable under linear redo, so modified stays true")
            .isTrue();
    }
}
