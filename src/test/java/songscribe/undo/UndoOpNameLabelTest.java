/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.undo.OpNames;

/**
 * End-to-end tests for the Edit-menu Undo/Redo label. Each test drives a real edit on a
 * real {@link Song} through the real message bus into the {@link UndoController} singleton,
 * then asserts {@link UndoController#undoLabel()} / {@link UndoController#redoLabel()}.
 *
 * <p>A declared op-name (Tier A pending or Tier B labeled bracket) is used verbatim; an
 * undeclared edit falls back to the type-based label — never a bare {@code Undo}. The undo
 * stack is reset before each test by posting a {@link DocumentDidLoadNotification}, the same
 * path the app uses on file load.
 */
class UndoOpNameLabelTest extends UnitTest {

    private Song song;
    private MockedStatic<MainFrame> mainFrameMock;

    @BeforeEach
    void setUp() {
        song = new Song();

        var mockScore = mock(ScoreView.class);
        when(mockScore.getSong()).thenReturn(song);
        var mockFrame = mock(MainFrame.class);
        when(mockFrame.getScoreView()).thenReturn(mockScore);

        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);

        // Force the singleton to load and subscribe, then reset its stacks to a known
        // empty baseline so a prior test's steps do not leak in.
        UndoController.initialize();
        UndoController.resetForTest();
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    private String expectedUndo(String opName) {
        return Strings.get(Strings.ACTION_EDIT_UNDO_LABELED, opName);
    }

    private String expectedRedo(String opName) {
        return Strings.get(Strings.ACTION_EDIT_REDO_LABELED, opName);
    }

    @Test
    void testDeclaredTierBLabelIsUsedVerbatim() {
        var label = Strings.get(Strings.ACTION_EDIT_OP_SONG_SETTINGS);
        song.withModification(label, () -> song.setTempo(new Tempo()));

        assertThat(UndoController.undoLabel()).isEqualTo(expectedUndo(label));
    }

    @Test
    void testDeclaredOpNamesLabelIsUsedVerbatim() {
        // OpNames.addLabel is the same helper the insertion sites use.
        var label = OpNames.addLabel(ElementType.CROTCHET);
        song.withModification(label, () -> song.setTempo(new Tempo()));

        assertThat(UndoController.undoLabel()).isEqualTo(expectedUndo(label));
    }

    @Test
    void testUndeclaredEditFallsBackToTypeBasedLabel() {
        // No pending name, no labeled bracket — the fallback derives the label from the
        // dominant mutation (a TEMPO metadata change → "Change Tempo").
        song.setTempo(new Tempo());

        assertThat(UndoController.undoLabel())
            .isEqualTo(expectedUndo(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_TEMPO)));
    }

    @Test
    void testEmptyStackYieldsBareUndoAndRedo() {
        // Reset left both stacks empty; the label collapses to the plain verb.
        assertThat(UndoController.undoLabel()).isEqualTo(Strings.get(Strings.ACTION_EDIT_UNDO));
        assertThat(UndoController.redoLabel()).isEqualTo(Strings.get(Strings.ACTION_EDIT_REDO));
    }

    @Test
    void testRedoLabelCarriesDeclaredNameAfterUndo() {
        var label = OpNames.addLabel(ElementType.CROTCHET);
        song.withModification(label, () -> song.setTempo(new Tempo()));

        UndoController.undo();

        assertThat(UndoController.redoLabel()).isEqualTo(expectedRedo(label));
    }
}
