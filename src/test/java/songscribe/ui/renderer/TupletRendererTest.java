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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.font.FontRenderContext;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tuplet;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineThickness;
import songscribe.smufl.Engraving;

class TupletRendererTest extends UnitTest {

    private static final double TOLERANCE = 1e-6;
    private static final TupletRenderer RENDERER = TupletRenderer.getInstance();

    /**
     * Configures a mock Graphics2D so font-related calls in drawTupletNumber and
     * measureNumberAdvanceSs don't throw.
     */
    @SuppressWarnings("unchecked")
    private static Graphics2D mockG2() {
        var g2 = mock(Graphics2D.class);
        var frc = new FontRenderContext(null, false, false);
        when(g2.getFontRenderContext()).thenReturn(frc);
        var fm = mock(FontMetrics.class);
        when(g2.getFontMetrics(any(Font.class))).thenReturn(fm);
        when(fm.stringWidth(any(String.class))).thenReturn(0);
        return g2;
    }

    /**
     * Builds a two-note line with a tuplet spanning both notes (grade 3).
     * If {@code addBeam} is true, a Beam range element covering both notes is added,
     * making allBeamed=true.
     */
    private static LineInvariants buildInvariantsWithTuplet(boolean isUpper, boolean addBeam) {
        var line = detachedLine();
        var anchor = ElementType.QUAVER.newInstance();
        anchor.setUpper(isUpper);
        var end = ElementType.QUAVER.newInstance();
        end.setUpper(isUpper);
        line.addElement(anchor);
        line.addElement(end);

        if (addBeam) {
            line.addRangeElement(new Beam(anchor, end));
        }

        var tuplet = new Tuplet(anchor, end, 3);
        line.addRangeElement(tuplet);

        // decorLayout.xSs() = 1.0, decorLayout.widthSs() = 4.0
        var decorLayout = new LayoutResult.DecorationLayout(1.0, -2.0, 4.0, 1.0, 0.0);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(tuplet, decorLayout)
            .build();

        return RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .build();
    }

    // ======================================================================
    // numberOnly branch tests
    // ======================================================================

    @Test
    void testRenderTupletsFromLine_beamedStemUp_numberOnly_noArmsDrawn() {
        // allBeamed=true, isUpper=true → numberOnly=true → bracket arms NOT drawn
        var invariants = buildInvariantsWithTuplet(true, true);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        // fillHorizontalLine and fillVerticalLine each call g2.draw() — none should be called
        verify(g2, never()).draw(any(Line2D.class));
    }

    @Test
    void testRenderTupletsFromLine_beamedStemUp_numberOnly_numberDrawn() {
        // Even in numberOnly mode the number is drawn
        var invariants = buildInvariantsWithTuplet(true, true);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        // drawTupletNumber calls g2.drawString()
        verify(g2, times(1)).drawString(any(String.class), any(float.class), any(float.class));
    }

    @Test
    void testRenderTupletsFromLine_notBeamed_armsDrawn() {
        // allBeamed=false → numberOnly=false → bracket arms ARE drawn (4 draw() calls)
        var invariants = buildInvariantsWithTuplet(true, false);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        // 2 horizontal arms + 2 vertical arms = 4 draw() calls
        verify(g2, times(4)).draw(any(Line2D.class));
    }

    @Test
    void testRenderTupletsFromLine_beamedStemDown_isUpperFalse_armsDrawn() {
        // allBeamed=true but isUpper=false → numberOnly=false → arms drawn
        var invariants = buildInvariantsWithTuplet(false, true);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        verify(g2, times(4)).draw(any(Line2D.class));
    }

    // ======================================================================
    // Bracket X coordinate tests
    // ======================================================================

    @Test
    void testRenderTupletsFromLine_stemUp_leftXSsCorrect() {
        // isUpper=true, not beamed → full bracket drawn
        // leftXSs = anchorXSs(1.0) + NOTE_HEAD_WIDTH_SS - stemSs - ARM_EXTENSION_SS
        var invariants = buildInvariantsWithTuplet(true, false);
        var g2 = mockG2();
        var stemSs = LineThickness.getInstance().stemSs();
        var expectedLeftX = 1.0 + Engraving.NOTE_HEAD_WIDTH_SS - stemSs - Tuplet.ARM_EXTENSION_SS;

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(4)).draw(lineCap.capture());
        // First draw call is the left horizontal arm: x1 = leftXSs
        var leftHorizontalArm = (Line2D.Double) lineCap.getAllValues().get(0);
        assertThat(leftHorizontalArm.x1).isCloseTo(expectedLeftX, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_stemDown_leftXSsCorrect() {
        // isUpper=false → leftXSs = anchorXSs(1.0) - ARM_EXTENSION_SS
        var invariants = buildInvariantsWithTuplet(false, false);
        var g2 = mockG2();
        var expectedLeftX = 1.0 - Tuplet.ARM_EXTENSION_SS;

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(4)).draw(lineCap.capture());
        var leftHorizontalArm = (Line2D.Double) lineCap.getAllValues().get(0);
        assertThat(leftHorizontalArm.x1).isCloseTo(expectedLeftX, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_rightXSsCorrect() {
        // rightXSs = endXSs(1.0+4.0) + NOTE_HEAD_WIDTH_SS + ARM_EXTENSION_SS (same for both stem dirs)
        var invariants = buildInvariantsWithTuplet(true, false);
        var g2 = mockG2();
        var endXSs = 1.0 + 4.0;
        var expectedRightX = endXSs + Engraving.NOTE_HEAD_WIDTH_SS + Tuplet.ARM_EXTENSION_SS;

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var lineCap = ArgumentCaptor.forClass(Shape.class);
        verify(g2, times(4)).draw(lineCap.capture());
        // Second draw call is the right horizontal arm: x2 = rightXSs
        var rightHorizontalArm = (Line2D.Double) lineCap.getAllValues().get(1);
        assertThat(rightHorizontalArm.x2).isCloseTo(expectedRightX, within(TOLERANCE));
    }

    // ======================================================================
    // Null-guard branch tests
    // ======================================================================

    @Test
    void testRenderTupletsFromLine_nullDecorLayout_skipsWithoutDrawing() {
        // A tuplet with no decoration layout must be skipped silently
        var line = detachedLine();
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        line.addElement(anchor);
        line.addElement(end);
        var tuplet = new Tuplet(anchor, end, 3);
        line.addRangeElement(tuplet);

        // LayoutResult has no entry for this tuplet — getDecorationLayout returns null
        var layoutResult = LayoutResult.builder().build();
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .build();
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, line, invariants, ElementFrame.LINE_LEVEL);

        // Nothing should be drawn since the tuplet's layout is absent
        verify(g2, never()).draw(any(Shape.class));
        verify(g2, never()).drawString(any(String.class), any(float.class), any(float.class));
    }

    @Test
    void testRenderTupletsFromLine_nullAnchorNote_skipsWithoutDrawing() {
        // A tuplet whose anchor element is null must be skipped silently
        var line = detachedLine();
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        line.addElement(anchor);
        line.addElement(end);
        var tuplet = new Tuplet(anchor, end, 3);
        line.addRangeElement(tuplet);

        var decorLayout = new LayoutResult.DecorationLayout(1.0, -2.0, 4.0, 1.0, 0.0);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(tuplet, decorLayout)
            .build();
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .build();

        // Null out the anchor after the layout has been built
        tuplet.setAnchorElement(null);

        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, line, invariants, ElementFrame.LINE_LEVEL);

        // Nothing should be drawn since anchor is null
        verify(g2, never()).draw(any(Shape.class));
        verify(g2, never()).drawString(any(String.class), any(float.class), any(float.class));
    }
}
