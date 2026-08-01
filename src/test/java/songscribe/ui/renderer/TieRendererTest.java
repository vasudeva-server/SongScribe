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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
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

    /** Returns the line {@code tie} was built on. */
    private static Line lineOf(Tie tie) {
        var anchor = tie.getAnchorElement();

        if (anchor == null) {
            throw new IllegalStateException("tie fixture has no anchor element");
        }

        var line = anchor.getParentLine();

        if (line == null) {
            throw new IllegalStateException("tie fixture is not on a line");
        }

        return line;
    }

    /**
     * Builds invariants for {@code tie}'s line. The line has to be set: an element's color is
     * resolved by naming the element as a {@link HitTarget}, which means mapping the index the
     * tie reports back to the element sitting there.
     */
    private static LineInvariants.Builder baseBuilder(Tie tie) {
        return RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(lineOf(tie))
            .setSelectionColor(SELECTION_COLOR);
    }

    // ======================================================================
    // determineTieColor tests
    // ======================================================================

    @Test
    void testDetermineTieColor_neitherNoteSelected_returnsElementColor() {
        var tie = makeTie();
        var invariants = baseBuilder(tie).build();

        // Without a selection provider or playing note, both endpoints return BLACK
        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }

    @Test
    void testDetermineTieColor_startNoteSelected_returnsSelectionColor() {
        // Selecting the start note (index 0) should return the selection color
        var tie = makeTie();
        var builder = baseBuilder(tie);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Element(lineOf(tie).getElement(0)), 0))
            .thenReturn(true);
        builder.setSelectionProvider(selectionProvider);
        var invariants = builder.build();

        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_endNoteSelected_returnsSelectionColor() {
        // If start note is not selected but end note is, the end color is used
        var tie = makeTie();
        var builder = baseBuilder(tie);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Element(lineOf(tie).getElement(1)), 0))
            .thenReturn(true);
        builder.setSelectionProvider(selectionProvider);
        var invariants = builder.build();

        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_startNoteSelectedTakesPriorityOverEnd() {
        // Both notes selected: start-note color is returned first (start-takes-priority)
        var tie = makeTie();
        var builder = baseBuilder(tie);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Element(lineOf(tie).getElement(0)), 0))
            .thenReturn(true);
        when(selectionProvider.isSelected(new HitTarget.Element(lineOf(tie).getElement(1)), 0))
            .thenReturn(true);
        builder.setSelectionProvider(selectionProvider);
        var invariants = builder.build();

        // determineTieColor checks start first; start is non-BLACK, so it returns immediately
        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_startNotePlaying_returnsPlayingColor() {
        var tie = makeTie();
        // Playing note index 0 = anchor note
        var invariants = baseBuilder(tie)
            .setPlayingNoteIndex(0)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    @Test
    void testDetermineTieColor_endNotePlaying_returnsPlayingColor() {
        var tie = makeTie();
        // Playing note index 1 = end note; start note returns BLACK (not playing)
        var invariants = baseBuilder(tie)
            .setPlayingNoteIndex(1)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(ScoreView.getPlayingNoteColor());
    }

    @Test
    void testDetermineTieColor_tieItselfSelected_returnsSelectionColor() {
        // Neither endpoint is selected: the tie is the selected target in its own right.
        var tie = makeTie();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Tie(tie), 0)).thenReturn(true);
        var invariants = baseBuilder(tie)
            .setSelectionProvider(selectionProvider)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants)).isEqualTo(SELECTION_COLOR);
    }

    @Test
    void testDetermineTieColor_anotherTieSelected_returnsElementColor() {
        var tie = makeTie();
        var other = makeTie();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Tie(other), 0)).thenReturn(true);
        var invariants = baseBuilder(tie)
            .setSelectionProvider(selectionProvider)
            .build();

        assertThat(RENDERER.determineTieColor(tie, invariants))
            .isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }

    @Test
    void testRenderTieSetsTheSelectionColorOnTheGraphics() {
        var tie = makeTie();
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Tie(tie), 0)).thenReturn(true);

        var layoutResult = LayoutResult.builder()
            .putTieLayout(tie, new LayoutResult.TieLayout(
                0.0, 0.0, 1.0, -1.0, 2.0, -1.0, 3.0, 0.0, 1.0, -0.5, 2.0, -0.5))
            .build();

        var invariants = baseBuilder(tie)
            .setSelectionProvider(selectionProvider)
            .setLayoutResult(layoutResult)
            .build();

        var g2 = spy(RenderContextTestHelper.realG2());
        var fillColors = new ArrayList<Color>();
        doAnswer(invocation -> {
            fillColors.add(g2.getColor());
            return null;
        }).when(g2).fill(any(Shape.class));

        RENDERER.renderTie(g2, tie, invariants, ElementFrame.LINE_LEVEL);

        assertThat(fillColors).containsOnly(SELECTION_COLOR);
    }
}
