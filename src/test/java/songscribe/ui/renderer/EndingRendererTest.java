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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Objects;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.layout.Ending;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineThickness;

/**
 * Tests for {@link EndingRenderer#renderEndings}: verifies the skip path when no
 * DecorationLayout is present, and the Y-coordinate translation when one is.
 */
class EndingRendererTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final EndingRenderer RENDERER = EndingRenderer.getInstance();

    /**
     * Builds a real {@link Graphics2D} from a headless buffered image so that
     * font/transform operations inside the renderer do not throw.
     */
    private static Graphics2D realG2() {
        var img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    /**
     * A line paired with its single {@link Ending}.
     */
    private record LineWithEnding(Line line, Ending ending) {}

    /**
     * Creates a line with two crotchet notes and an {@link Ending} spanning them,
     * with bracket ranges pre-computed.
     */
    private LineWithEnding makeLineWithEnding() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();
        line.addElement(note1);
        line.addElement(note2);
        var ending = new Ending(note1, note2);
        line.addRangeElement(ending);
        ending.computeBracketRanges(line, e -> 5.0, LineThickness.getInstance());
        return new LineWithEnding(line, ending);
    }

    // ======================================================================
    // Skip when DecorationLayout is absent
    // ======================================================================

    @Test
    void testRenderEndings_noDecorationLayout_skipsDrawing() {
        var pair = makeLineWithEnding();
        var line = pair.line();

        // No DecorationLayout in the layout result → renderEndings must skip
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(LayoutResult.builder().build())
            .setCurrentLine(line)
            .build();

        var g2 = spy(realG2());
        RENDERER.renderEndings(g2, line, 0, invariants);

        // No paint calls should reach g2: the method continues past the null-layout guard
        verify(g2, never()).fill(org.mockito.ArgumentMatchers.any());
        verify(g2, never()).draw(org.mockito.ArgumentMatchers.any());
    }

    // ======================================================================
    // Y translation when DecorationLayout is present
    // ======================================================================

    @Test
    void testRenderEndings_withDecorationLayout_horizontalLineYEqualsMiddleLinePlusLayoutY() {
        var pair = makeLineWithEnding();
        var line = pair.line();
        var ending = pair.ending();

        // DecorationLayout with a known ySs — the renderer translates it to
        // component Y via: yTopSs = middleLineYSs + decorationLayout.ySs()
        var decorationYSs = -3.0;
        var middleLineYSs = 5.0;
        var expectedTopYSs = middleLineYSs + decorationYSs;

        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(ending, new LayoutResult.DecorationLayout(
                0.0, decorationYSs, 10.0, Ending.VOLTA_TICK_HEIGHT_SS, 0.0))
            .build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setCurrentLine(line)
            .setMiddleLineYSs(middleLineYSs)
            .build();

        // Use a spy on a real Graphics2D so font/transform calls succeed
        var g2 = spy(realG2());
        RENDERER.renderEndings(g2, line, 0, invariants);

        // The horizontal bracket top is drawn via drawLine → g2.draw(Line2D)
        // Capture the drawn shape and verify its Y matches the expected component Y
        var shapeCap = org.mockito.ArgumentCaptor.forClass(Shape.class);
        verify(g2, org.mockito.Mockito.atLeastOnce()).draw(shapeCap.capture());

        var drawnLines = shapeCap.getAllValues().stream()
            .filter(s -> s instanceof Line2D)
            .map(s -> (Line2D) s)
            .toList();

        assertThat(drawnLines).isNotEmpty();
        // The first drawn line is the horizontal bracket top; its Y = expectedTopYSs
        var topLine = drawnLines.getFirst();
        assertThat(topLine.getY1()).isCloseTo(expectedTopYSs, within(TOLERANCE));
        assertThat(topLine.getY2()).isCloseTo(expectedTopYSs, within(TOLERANCE));
    }
}
