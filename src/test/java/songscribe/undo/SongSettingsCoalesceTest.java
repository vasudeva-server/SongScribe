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
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Guards the Phase 1a coalescing fix: wrapping the whole {@code SongSettingsDialog.setData()}
 * body in one modification bracket makes its several sub-commits collapse into a single undo
 * step, so one OK is one undoable operation and undoing it once returns to the clean state.
 *
 * <p>This drives the coalescing contract directly — a single labeled outer bracket wrapping
 * two distinct metadata mutations — through the real {@link UndoController}, rather than
 * constructing the Swing dialog.
 */
class SongSettingsCoalesceTest extends UnitTest {

    private static final String A_FOOTNOTE = "a footnote";

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

        UndoController.initialize();
        UndoController.reset();
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    /** Mirrors setData(): one labeled bracket wrapping multiple committing sub-edits. */
    private void commitSettings() {
        song.withModification(Strings.get(Strings.ACTION_EDIT_OP_SONG_SETTINGS), () -> {
            song.setTempo(new Tempo());
            song.setFootnotes(A_FOOTNOTE);
        });
    }

    @Test
    void testTwoSubCommitsProduceExactlyOneUndoStep() {
        commitSettings();

        assertThat(UndoController.canUndo())
            .as("the coalesced commit is undoable")
            .isTrue();

        UndoController.undo();

        assertThat(UndoController.canUndo())
            .as("both sub-commits collapsed into a single step, so one undo empties the stack")
            .isFalse();
    }

    @Test
    void testUndoOnceClearsModifiedFlag() {
        commitSettings();

        assertThat(song.isModified())
            .as("committing settings marks the song modified")
            .isTrue();

        UndoController.undo();

        assertThat(song.isModified())
            .as("undoing the single step returns to the clean baseline")
            .isFalse();
    }
}
