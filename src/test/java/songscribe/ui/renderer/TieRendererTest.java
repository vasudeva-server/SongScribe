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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class TieRendererTest extends UnitTest {

    private static final TieRenderer RENDERER = TieRenderer.getInstance();
    private static final Color SELECTION_COLOR = Color.BLUE;

    /**
     * Builds a two-note line and a tie between notes at the given indices,
     * returning the tie. The notes are added to the line so that
     * getAnchorElementIndex() and getEndElementIndex() return correct values.
     */
    private static Tie makeTie() {
        var line = detachedLine();
        var anchorNote = ElementType.CROTCHET.newInstance();
        var endNote = ElementType.CROTCHET.newInstance();
        line.addElement(anchorNote);
        line.addElement(endNote);
        return new Tie(anchorNote, endNote);
    }

    private static LineInvariants.Builder baseBuilder() {
        return RenderContextTestHelper.newContext(new Song())
            .setEditMode(true)
            .setSelectionColor(SELECTION_COLOR);
    }

    // ======================================================================
    // determineTieColor tests
    // ======================================================================

    @Test
    void testDetermineTieColor_neitherNoteSelected_returnsElementColor() {
        var tie = makeTie();
        var invariants = baseBuilder().build();

        // Without a selection provider or playing note, both endpoints return BLACK
        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }

    @Test
    void testDetermineTieColor_startNoteSelected_returnsSelectionColor() {
        // Selecting the start note (index 0) should return the selection color
        var tie = makeTie();
        var builder = baseBuilder();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isElementSelected(0, 0)).thenReturn(true);
        builder.setSelectionProvider(selectionProvider);
        var invariants = builder.build();

        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_endNoteSelected_returnsSelectionColor() {
        // If start note is not selected but end note is, the end color is used
        var tie = makeTie();
        var builder = baseBuilder();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isElementSelected(1, 0)).thenReturn(true);
        builder.setSelectionProvider(selectionProvider);
        var invariants = builder.build();

        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_startNoteSelectedTakesPriorityOverEnd() {
        // Both notes selected: start-note color is returned first (start-takes-priority)
        var tie = makeTie();
        var builder = baseBuilder();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isElementSelected(0, 0)).thenReturn(true);
        when(selectionProvider.isElementSelected(1, 0)).thenReturn(true);
        builder.setSelectionProvider(selectionProvider);
        var invariants = builder.build();

        // determineTieColor checks start first; start is non-BLACK, so it returns immediately
        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_nonEditMode_returnsElementColor() {
        // Outside edit mode, getElementColor always returns BLACK → fallback to ELEMENT_COLOR
        var tie = makeTie();
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setEditMode(false)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }

    @Test
    void testDetermineTieColor_startNotePlaying_returnsPlayingColor() {
        var tie = makeTie();
        // Playing note index 0 = anchor note
        var invariants = baseBuilder()
            .setPlayingNoteIndex(0)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    @Test
    void testDetermineTieColor_endNotePlaying_returnsPlayingColor() {
        var tie = makeTie();
        // Playing note index 1 = end note; start note returns BLACK (not playing)
        var invariants = baseBuilder()
            .setPlayingNoteIndex(1)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }
}
