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
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.engraving.SMuFLConstants;
import songscribe.layout.ElementColumn;
import songscribe.layout.Ending;
import songscribe.layout.LayoutResult;

/**
 * Tests for {@link EndingRenderer#renderEndings} (the skip path when no DecorationLayout is
 * present, and the Y-coordinate translation when one is) and {@link EndingRenderer#hitTest}
 * (bracket/margin/label bounding-box containment).
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
     * Creates a line with two crotchet notes, a REPEAT_RIGHT split between them (every
     * ending must have a split), and an {@link Ending} spanning them, with bracket ranges
     * pre-computed.
     */
    private LineWithEnding makeLineWithEnding() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        var split = ElementType.REPEAT_RIGHT.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();
        line.addElement(note1);
        line.addElement(split);
        line.addElement(note2);
        var ending = new Ending(note1, note2);
        line.addRangeElement(ending);
        // Exact geometry does not matter for these tests (only Y-translation is asserted),
        // so every element maps to an identical stub column with a note-head-width extent.
        ending.computeBracketRanges(line, e -> new ElementColumn(
            e, List.of(), 0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0, 0, null, 0, false));
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
        verify(g2, never()).fill(any());
        verify(g2, never()).draw(any());
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

        // drawPath passes a Path2D directly to draw() without touching the graphics transform,
        // so capture the path as-is.
        var placedShapes = new ArrayList<Shape>();
        doAnswer(answerVoid((Shape local) -> {
            placedShapes.add(local);
        })).when(g2).draw(any(Shape.class));

        RENDERER.renderEndings(g2, line, 0, invariants);

        // The bracket path's minimum Y is the top of the bracket (the horizontal segment).
        assertThat(placedShapes).isNotEmpty();
        var bracketBounds = placedShapes.getFirst().getBounds2D();
        assertThat(bracketBounds.getMinY()).isCloseTo(expectedTopYSs, within(TOLERANCE));
    }

    // ======================================================================
    // hitTest
    // ======================================================================

    private static final double DECORATION_X_SS = 2.0;
    private static final double DECORATION_Y_SS = -2.0;
    private static final double DECORATION_WIDTH_SS = 10.0;
    private static final double DECORATION_HEIGHT_SS = 3.0;
    private static final double DECORATION_MARGIN_SS = 1.0;
    private static final double MIDDLE_LINE_Y_SS = 5.0;

    /** Top of the hit box in component space, for the layout built by {@link #layoutResultWithDecoration}. */
    private static final double HIT_BOX_TOP_Y_SS = MIDDLE_LINE_Y_SS + DECORATION_Y_SS;

    /** Bottom of the hit box: the bracket content plus the margin band below it. */
    private static final double HIT_BOX_BOTTOM_Y_SS =
        HIT_BOX_TOP_Y_SS + DECORATION_HEIGHT_SS + DECORATION_MARGIN_SS;

    private static final double HIT_BOX_RIGHT_X_SS = DECORATION_X_SS + DECORATION_WIDTH_SS;

    /**
     * Hit-tests the given staff-space point against the line, on the shared middle-line Y.
     */
    private static @Nullable Ending hitTestEnding(
        double xSs,
        double ySs,
        Line line,
        @Nullable LayoutResult layoutResult
    ) {
        return RENDERER.hitTestEnding(xSs, ySs, line, layoutResult, MIDDLE_LINE_Y_SS);
    }

    private LayoutResult layoutResultWithDecoration(Ending ending, double ySs) {
        return LayoutResult.builder()
            .putDecorationLayout(ending, new LayoutResult.DecorationLayout(
                DECORATION_X_SS, ySs, DECORATION_WIDTH_SS, DECORATION_HEIGHT_SS, DECORATION_MARGIN_SS))
            .build();
    }

    @Test
    void testHitTestEnding_pointInsideBracketBox_returnsEnding() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(DECORATION_X_SS + 1, HIT_BOX_TOP_Y_SS + 1, pair.line(), layoutResult);

        assertThat(result).isSameAs(ending);
    }

    @Test
    void testHitTestEnding_pointInMarginBandBelowBracket_returnsEnding() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);
        // Point below the bracket's content height but within the margin band.
        var yInMarginSs = HIT_BOX_TOP_Y_SS + DECORATION_HEIGHT_SS + (DECORATION_MARGIN_SS / 2);

        var result = hitTestEnding(DECORATION_X_SS + 1, yInMarginSs, pair.line(), layoutResult);

        assertThat(result).isSameAs(ending);
    }

    @Test
    void testHitTestEnding_pointBelowBox_returnsNull() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(DECORATION_X_SS + 1, HIT_BOX_BOTTOM_Y_SS + 1, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

    @Test
    void testHitTestEnding_pointAboveBox_returnsNull() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(DECORATION_X_SS + 1, HIT_BOX_TOP_Y_SS - 1, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

    @Test
    void testHitTestEnding_pointLeftOfBox_returnsNull() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(DECORATION_X_SS - 1, HIT_BOX_TOP_Y_SS + 1, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

    @Test
    void testHitTestEnding_pointRightOfBox_returnsNull() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(HIT_BOX_RIGHT_X_SS + 1, HIT_BOX_TOP_Y_SS + 1, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

    // Rectangle2D.contains is left/top inclusive, so a click exactly on the top-left corner hits.
    @Test
    void testHitTestEnding_pointOnTopLeftCorner_returnsEnding() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(DECORATION_X_SS, HIT_BOX_TOP_Y_SS, pair.line(), layoutResult);

        assertThat(result).isSameAs(ending);
    }

    // Rectangle2D.contains is right/bottom exclusive, so the far edges are misses.
    @Test
    void testHitTestEnding_pointOnRightEdge_returnsNull() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(HIT_BOX_RIGHT_X_SS, HIT_BOX_TOP_Y_SS + 1, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

    @Test
    void testHitTestEnding_pointOnBottomEdge_returnsNull() {
        var pair = makeLineWithEnding();
        var ending = pair.ending();
        var layoutResult = layoutResultWithDecoration(ending, DECORATION_Y_SS);

        var result = hitTestEnding(DECORATION_X_SS + 1, HIT_BOX_BOTTOM_Y_SS, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

    // When two endings' hit boxes overlap, document order decides the winner.
    @Test
    void testHitTestEnding_overlappingEndings_returnsFirstInDocumentOrder() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        var split = ElementType.REPEAT_RIGHT.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();
        line.addElement(note1);
        line.addElement(split);
        line.addElement(note2);

        var firstEnding = new Ending(note1, note2);
        var secondEnding = new Ending(note1, note2);
        line.addRangeElement(firstEnding);
        line.addRangeElement(secondEnding);

        // Identical geometry for both, so the point falls inside each one's box.
        var decoration = new LayoutResult.DecorationLayout(
            DECORATION_X_SS, DECORATION_Y_SS, DECORATION_WIDTH_SS, DECORATION_HEIGHT_SS, DECORATION_MARGIN_SS);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(firstEnding, decoration)
            .putDecorationLayout(secondEnding, decoration)
            .build();

        var result = hitTestEnding(DECORATION_X_SS + 1, HIT_BOX_TOP_Y_SS + 1, line, layoutResult);

        assertThat(result).isSameAs(firstEnding);
    }

    @Test
    void testHitTestEnding_nullLayoutResult_returnsNull() {
        var pair = makeLineWithEnding();

        var result = hitTestEnding(DECORATION_X_SS, MIDDLE_LINE_Y_SS, pair.line(), null);

        assertThat(result).isNull();
    }

    @Test
    void testHitTestEnding_noDecorationLayoutForEnding_returnsNull() {
        var pair = makeLineWithEnding();
        var layoutResult = LayoutResult.builder().build();

        var result = hitTestEnding(DECORATION_X_SS, MIDDLE_LINE_Y_SS, pair.line(), layoutResult);

        assertThat(result).isNull();
    }

}
