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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

/**
 * Covers the color decision in {@link DynamicMarkingRenderer#render}: a dynamic is selectable
 * in its own right, so its color is keyed on the attachment rather than on the note it hangs off.
 */
class DynamicMarkingRendererTest extends UnitTest {

    private static final DynamicMarkingRenderer RENDERER = DynamicMarkingRenderer.getInstance();

    private static final double DECORATION_X_SS = 3.0;
    private static final double DECORATION_Y_SS = 4.0;
    private static final double DECORATION_WIDTH_SS = 2.0;
    private static final double DECORATION_HEIGHT_SS = 1.5;

    /**
     * Renders a forte marking with its attachment reported as selected or not, and returns the
     * color in force when the glyph was drawn.
     */
    private static Color renderedGlyphColor(boolean selected) {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);

        var attachment = new DynamicAttachment(DynamicAttachment.DynamicType.FORTE);
        note.addAttachment(attachment);

        var decorationLayout = new LayoutResult.DecorationLayout(
            DECORATION_X_SS, DECORATION_Y_SS, DECORATION_WIDTH_SS, DECORATION_HEIGHT_SS, 0.0);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(attachment, decorationLayout)
            .build();

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Attachment(attachment), 0))
            .thenReturn(selected);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setCurrentLine(line)
            .setSelectionProvider(selectionProvider)
            .build();

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var drawnColors = new ArrayList<Color>();
        doAnswer(invocation -> {
            drawnColors.add(g2Spy.getColor());
            return null;
        }).when(g2Spy).drawString(anyString(), anyFloat(), anyFloat());

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL.withElement(0, Double.NaN), note, g2Spy);

        assertThat(drawnColors).hasSize(1);
        return drawnColors.getFirst();
    }

    @Test
    void testRenderSelectedDynamicDrawsInTheSelectionColor() {
        assertThat(renderedGlyphColor(true)).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testRenderUnselectedDynamicDrawsInTheElementColor() {
        assertThat(renderedGlyphColor(false)).isEqualTo(RenderingUtils.ELEMENT_COLOR);
    }
}
