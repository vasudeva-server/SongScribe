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
 * Exercises the <em>one bracket, one Undo</em> guarantee of {@code docs/undo.md} for the
 * case that makes it a design decision rather than a mechanism: a commit that changes
 * several fields at once. {@code SongSettingsDialog.setData()} wraps its whole body in one
 * bracket, so its sub-commits collapse into a single step and one OK is one undoable
 * operation.
 *
 * <p><b>The step count</b> — two distinct metadata mutations inside one labeled outer
 * bracket produce exactly one step, asserted by undoing once and finding the stack empty.
 * A per-field bracket passes any assertion about the document and fails this one.
 *
 * <p><b>The modified flag follows</b> — one undo returns the document to clean, which is
 * what the user experiences as the commit having been fully undone.
 *
 * <p>The bracket is driven directly rather than through the Swing dialog: the promise is
 * about bracket nesting, and constructing the dialog would test its widgets instead. What
 * this cannot show is that the dialog still opens that bracket — that is wiring, and belongs
 * to the dialog's own e2e case.
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
