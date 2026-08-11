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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.quaver;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Song;
import songscribe.dom.Tuplet;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class TupletRendererTest extends UnitTest {

    private static final double TOLERANCE = 1e-6;
    private static final TupletRenderer RENDERER = TupletRenderer.getInstance();

    /** Three notes in the time of two — the grade the color fixture uses. */
    private static final int TRIPLET_GRADE = 3;

    // The tuplet's reserved box in the color fixture; the color path never reads the values back.
    private static final double DECO_X_SS = 1.0;
    private static final double DECO_Y_SS = -2.0;
    private static final double DECO_WIDTH_SS = 4.0;
    private static final double DECO_HEIGHT_SS = 1.0;
    private static final double NO_MARGIN_SS = 0.0;

    /**
     * Configures a mock Graphics2D so font-related calls in drawTupletNumber and
     * measureNumberAdvanceSs don't throw.
     */
    private static Graphics2D mockG2() {
        var g2 = mock(Graphics2D.class);
        var frc = new FontRenderContext(null, false, false);
        when(g2.getFontRenderContext()).thenReturn(frc);
        var fm = mock(FontMetrics.class);
        when(g2.getFontMetrics(any(Font.class))).thenReturn(fm);
        when(fm.stringWidth(any(String.class))).thenReturn(0);
        return g2;
    }

    /** A mock Graphics2D paired with the device-space shapes it filled. */
    private record RecordingG2(Graphics2D g2, List<Shape> placedShapes) {}

    /**
     * Extends {@link #mockG2} with shape capture so the paths passed to {@code draw} can be
     * inspected. {@code drawPath} passes a {@code Path2D} directly to {@code draw} without
     * modifying the graphics transform, so no transform tracking is needed.
     */
    private static RecordingG2 recordingG2() {
        var g2 = mockG2();
        var transform = new AffineTransform();
        var placedShapes = new ArrayList<Shape>();

        when(g2.getTransform()).thenAnswer(invocation -> new AffineTransform(transform));
        doAnswer(answerVoid(transform::translate)).when(g2).translate(anyDouble(), anyDouble());
        doAnswer(answerVoid((Double theta) -> transform.rotate(theta))).when(g2).rotate(anyDouble());
        doAnswer(answerVoid((AffineTransform newTransform) -> transform.setTransform(newTransform))).when(g2).setTransform(any(AffineTransform.class));
        doAnswer(answerVoid((Shape local) -> placedShapes.add(transform.createTransformedShape(local)))).when(g2).draw(any(Shape.class));

        return new RecordingG2(g2, placedShapes);
    }

    /**
     * Builds a two-note line with a tuplet spanning both notes (grade 3).
     * If {@code addBeam} is true, a Beam span covering both notes is added,
     * making allBeamed=true.
     */
    private static LineInvariants buildInvariantsWithTuplet(boolean isUpper, boolean addBeam) {
        var line = detachedLine();
        var anchor = quaver();
        anchor.setUpper(isUpper);
        var end = quaver();
        end.setUpper(isUpper);
        line.addElement(anchor);
        line.addElement(end);

        if (addBeam) {
            line.addSpan(new Beam(anchor, end));
        }

        var tuplet = Tuplet.withUnresolvedRatio(anchor, end, 3);
        line.addSpan(tuplet);

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
    // Selection color
    // ======================================================================

    /**
     * Renders a bracketed tuplet with it reported as selected or not, and returns the color the
     * renderer set before drawing. A tuplet spans notes rather than hanging off one, so it takes
     * no color from an owning note: the only two answers are plain black and the selection color.
     */
    private static Color renderedTupletColor(boolean selected) {
        var line = detachedLine();
        var anchor = quaver();
        anchor.setUpper(true);
        var end = quaver();
        end.setUpper(true);
        line.addElement(anchor);
        line.addElement(end);

        var tuplet = Tuplet.withUnresolvedRatio(anchor, end, TRIPLET_GRADE);
        line.addSpan(tuplet);

        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(tuplet, new LayoutResult.DecorationLayout(
                DECO_X_SS, DECO_Y_SS, DECO_WIDTH_SS, DECO_HEIGHT_SS, NO_MARGIN_SS))
            .build();

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Tuplet(tuplet), 0)).thenReturn(selected);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .setSelectionProvider(selectionProvider)
            .build();

        var g2 = mockG2();
        RENDERER.renderTupletsFromLine(g2, line, invariants, ElementFrame.LINE_LEVEL);

        var colorCaptor = ArgumentCaptor.forClass(Color.class);
        verify(g2).setColor(colorCaptor.capture());

        return colorCaptor.getValue();
    }

    @Test
    void testSelectedTupletDrawsInTheSelectionColor() {
        assertThat(renderedTupletColor(true)).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testUnselectedTupletDrawsInTheElementColor() {
        assertThat(renderedTupletColor(false)).isEqualTo(RenderingUtils.ELEMENT_COLOR);
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

        // drawRoundedLine calls g2.fill() — none should be called
        verify(g2, never()).fill(any(Shape.class));
    }

    @Test
    void testRenderTupletsFromLine_beamedStemUp_numberOnly_numberDrawn() {
        // Even in numberOnly mode the number is drawn
        var invariants = buildInvariantsWithTuplet(true, true);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        // The number is drawn via a shaped glyph vector
        verify(g2, times(1)).drawGlyphVector(any(GlyphVector.class), any(float.class), any(float.class));
    }

    @Test
    void testRenderTupletsFromLine_notBeamed_armsDrawn() {
        // allBeamed=false → numberOnly=false → bracket arms ARE drawn (2 draw() calls)
        var invariants = buildInvariantsWithTuplet(true, false);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        // left-side L-path + right-side L-path = 2 draw() calls
        verify(g2, times(2)).draw(any(Shape.class));
    }

    @Test
    void testRenderTupletsFromLine_beamedStemDown_isUpperFalse_armsDrawn() {
        // allBeamed=true but isUpper=false → numberOnly=false → arms drawn
        var invariants = buildInvariantsWithTuplet(false, true);
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        verify(g2, times(2)).draw(any(Shape.class));
    }

    // ======================================================================
    // Bracket X coordinate tests
    // ======================================================================

    @Test
    void testRenderTupletsFromLine_stemUp_leftXSsCorrect() {
        // isUpper=true, not beamed → full bracket drawn
        // leftXSs = anchorXSs(1.0) + NOTE_HEAD_WIDTH_SS - stemSs - ARM_EXTENSION_SS
        var invariants = buildInvariantsWithTuplet(true, false);
        var recording = recordingG2();
        var g2 = recording.g2();
        var stemSs = LineThickness.STEM_SS;
        var expectedLeftX = 1.0 + SMuFLConstants.NOTE_HEAD_WIDTH_SS - stemSs - Tuplet.ARM_EXTENSION_SS;

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        verify(g2, times(2)).draw(any(Shape.class));
        // First draw is the left-side bracket path: min X = leftXSs
        var leftPath = recording.placedShapes().getFirst().getBounds2D();
        assertThat(leftPath.getMinX()).isCloseTo(expectedLeftX, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_stemDown_leftXSsCorrect() {
        // isUpper=false → leftXSs = anchorXSs(1.0) - ARM_EXTENSION_SS
        var invariants = buildInvariantsWithTuplet(false, false);
        var recording = recordingG2();
        var g2 = recording.g2();
        var expectedLeftX = 1.0 - Tuplet.ARM_EXTENSION_SS;

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        verify(g2, times(2)).draw(any(Shape.class));
        var leftPath = recording.placedShapes().getFirst().getBounds2D();
        assertThat(leftPath.getMinX()).isCloseTo(expectedLeftX, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_rightXSsCorrect() {
        // rightXSs = endXSs(1.0+4.0) + NOTE_HEAD_WIDTH_SS + ARM_EXTENSION_SS (same for both stem dirs)
        var invariants = buildInvariantsWithTuplet(true, false);
        var recording = recordingG2();
        var g2 = recording.g2();
        var endXSs = 1.0 + 4.0;
        var expectedRightX = endXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS + Tuplet.ARM_EXTENSION_SS;

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        verify(g2, times(2)).draw(any(Shape.class));
        // Second draw is the right-side bracket path: max X = rightXSs
        var rightPath = recording.placedShapes().get(1).getBounds2D();
        assertThat(rightPath.getMaxX()).isCloseTo(expectedRightX, within(TOLERANCE));
    }

    // ======================================================================
    // Null-guard branch tests
    // ======================================================================

    @Test
    void testRenderTupletsFromLine_nullDecorLayout_skipsWithoutDrawing() {
        // A tuplet with no decoration layout must be skipped silently
        var line = detachedLine();
        var anchor = quaver();
        var end = quaver();
        line.addElement(anchor);
        line.addElement(end);
        var tuplet = Tuplet.withUnresolvedRatio(anchor, end, 3);
        line.addSpan(tuplet);

        // LayoutResult has no entry for this tuplet — getDecorationLayout returns null
        var layoutResult = LayoutResult.builder().build();
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .build();
        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, line, invariants, ElementFrame.LINE_LEVEL);

        // Nothing should be drawn since the tuplet's layout is absent
        verify(g2, never()).fill(any(Shape.class));
        verify(g2, never()).drawString(any(String.class), any(float.class), any(float.class));
    }

    // ======================================================================
    // Sloped bracket tests (dySs)
    // ======================================================================

    /**
     * Builds a two-note line with a tuplet whose {@link LayoutResult.DecorationLayout} carries
     * an explicit slope {@code dySs}. Unbeamed and stem-up so both bracket arms are drawn.
     */
    private static LineInvariants buildInvariantsWithSlopedTuplet(double dySs) {
        return buildInvariantsWithSlopedTuplet(dySs, false);
    }

    /**
     * As {@link #buildInvariantsWithSlopedTuplet(double)}, but optionally beamed (stem-up) so the
     * tuplet renders number-only.
     */
    private static LineInvariants buildInvariantsWithSlopedTuplet(double dySs, boolean addBeam) {
        var line = detachedLine();
        var anchor = quaver();
        anchor.setUpper(true);
        var end = quaver();
        end.setUpper(true);
        line.addElement(anchor);
        line.addElement(end);

        if (addBeam) {
            line.addSpan(new Beam(anchor, end));
        }

        var tuplet = Tuplet.withUnresolvedRatio(anchor, end, 3);
        line.addSpan(tuplet);

        // decorLayout.xSs() = 1.0 (anchorXSs), decorLayout.widthSs() = 4.0
        var decorLayout = new LayoutResult.DecorationLayout(1.0, -2.0, dySs, 4.0, 1.0, 0.0, null);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(tuplet, decorLayout)
            .build();

        return RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .build();
    }

    /**
     * Extracts the exact (unmodified) sloped-corner point at path segment {@code segmentIndex} of
     * a drawn bracket-arm {@code Shape}. {@link songscribe.util.GraphicUtils#drawPath} only insets
     * the first and last points for round-cap compensation; interior points (the sloped corner)
     * pass through unchanged, so segment index 1 of a 3-point arm is the exact corner Y.
     */
    private static double cornerYSs(Shape shape, int segmentIndex) {
        var iterator = shape.getPathIterator(null);
        var coords = new double[6];
        var index = 0;

        while (!iterator.isDone()) {
            iterator.currentSegment(coords);

            if (index == segmentIndex) {
                return coords[1];
            }

            index++;
            iterator.next();
        }

        throw new IllegalStateException("Segment index " + segmentIndex + " not found in path");
    }

    @Test
    void testRenderTupletsFromLine_ascendingContour_rightBracketHigherThanLeft() {
        // Negative dySs is the documented ascending-contour convention (right end higher,
        // smaller/more-negative layout Y).
        var recording = recordingG2();
        var g2 = recording.g2();
        var invariants = buildInvariantsWithSlopedTuplet(-1.0);

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var leftCornerYSs = cornerYSs(recording.placedShapes().get(0), 1);
        var rightCornerYSs = cornerYSs(recording.placedShapes().get(1), 1);

        assertThat(rightCornerYSs).isLessThan(leftCornerYSs);
    }

    @Test
    void testRenderTupletsFromLine_flatContour_bracketCornersEqual() {
        var recording = recordingG2();
        var g2 = recording.g2();
        var invariants = buildInvariantsWithSlopedTuplet(0.0);

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var leftCornerYSs = cornerYSs(recording.placedShapes().get(0), 1);
        var rightCornerYSs = cornerYSs(recording.placedShapes().get(1), 1);

        assertThat(rightCornerYSs).isCloseTo(leftCornerYSs, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_slopedBracket_extrapolatesFromAnchorOverWidth() {
        // Pins the exact Step 2->4 reconciliation: the renderer must re-derive slope as
        // dySs / widthSs and evaluate the line from anchorXSs, extrapolating out to the arm
        // X positions (which sit past the anchor/end columns by ARM_EXTENSION_SS) rather than
        // re-fitting dySs across the wider arm span.
        var dySs = -2.0;
        var invariants = buildInvariantsWithSlopedTuplet(dySs);
        var recording = recordingG2();
        var g2 = recording.g2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var anchorXSs = 1.0;
        var widthSs = 4.0;
        var endXSs = anchorXSs + widthSs;
        var slope = dySs / widthSs;
        var stemSs = LineThickness.STEM_SS;
        var leftXSs = anchorXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS - stemSs - Tuplet.ARM_EXTENSION_SS;
        var rightXSs = endXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS + Tuplet.ARM_EXTENSION_SS;

        var boxTopYSs = -2.0;  // middleLineYSs defaults to 0, so component Y == layout ySs
        var bracketLineOffsetSs = Tuplet.bracketLineOffsetSs();
        var expectedLeftYSs = boxTopYSs + bracketLineOffsetSs + slope * (leftXSs - anchorXSs);
        var expectedRightYSs = boxTopYSs + bracketLineOffsetSs + slope * (rightXSs - anchorXSs);

        var actualLeftYSs = cornerYSs(recording.placedShapes().get(0), 1);
        var actualRightYSs = cornerYSs(recording.placedShapes().get(1), 1);

        assertThat(actualLeftYSs).isCloseTo(expectedLeftYSs, within(TOLERANCE));
        assertThat(actualRightYSs).isCloseTo(expectedRightYSs, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_bracketArmBottomReachesCapInclusiveInkExtent() {
        // The arm's rendered (round-cap-inset) bottom must land exactly BRACKET_ARM_HEIGHT_SS
        // below the sloped corner: armHeightSs bakes in an extra thicknessSs/2.0 (issue #556)
        // specifically to cancel drawPath's round-cap inset at that endpoint, so the ink extent
        // matches LilyPond's un-inset arm height exactly rather than falling short by half the
        // bracket's line thickness.
        var invariants = buildInvariantsWithSlopedTuplet(-2.0);
        var recording = recordingG2();
        var g2 = recording.g2();

        RENDERER.renderTupletsFromLine(g2, invariants.requireCurrentLine(), invariants,
            ElementFrame.LINE_LEVEL);

        var leftCornerYSs = cornerYSs(recording.placedShapes().getFirst(), 1);
        var leftArmBottomYSs = cornerYSs(recording.placedShapes().getFirst(), 0);

        assertThat(leftArmBottomYSs - leftCornerYSs)
            .isCloseTo(Tuplet.BRACKET_ARM_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_numberOnly_baselineEvaluatesSlopeAtGapCenter() {
        // Number-only mode still reserves a sloped box; the baseline must evaluate the slope at
        // the gap center, not sit at the flat left-endpoint Y, or the number drops onto an
        // ascending beam at the center (issue #556). Comparing a flat and a sloped render isolates
        // the slope contribution without depending on font-metric internals for the absolute value.
        var dySs = -2.0;
        var flatInvariants = buildInvariantsWithSlopedTuplet(0.0, true);
        var slopedInvariants = buildInvariantsWithSlopedTuplet(dySs, true);

        var flatG2 = mockG2();
        RENDERER.renderTupletsFromLine(flatG2, flatInvariants.requireCurrentLine(), flatInvariants,
            ElementFrame.LINE_LEVEL);
        var flatYCaptor = ArgumentCaptor.forClass(Float.class);
        verify(flatG2).drawGlyphVector(any(GlyphVector.class), any(Float.class), flatYCaptor.capture());

        var slopedG2 = mockG2();
        RENDERER.renderTupletsFromLine(slopedG2, slopedInvariants.requireCurrentLine(), slopedInvariants,
            ElementFrame.LINE_LEVEL);
        var slopedYCaptor = ArgumentCaptor.forClass(Float.class);
        verify(slopedG2).drawGlyphVector(any(GlyphVector.class), any(Float.class), slopedYCaptor.capture());

        // centerXSs is the midpoint of the ARM edges (leftXSs/rightXSs), not the anchor/end
        // columns directly -- see testRenderTupletsFromLine_slopedBracket_extrapolatesFromAnchorOverWidth.
        var anchorXSs = 1.0;
        var widthSs = 4.0;
        var endXSs = anchorXSs + widthSs;
        var stemSs = LineThickness.STEM_SS;
        var leftXSs = anchorXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS - stemSs - Tuplet.ARM_EXTENSION_SS;
        var rightXSs = endXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS + Tuplet.ARM_EXTENSION_SS;
        var centerXSs = (leftXSs + rightXSs) / 2.0;  // numberOnly: gapCenterXSs == centerXSs
        var slope = dySs / widthSs;
        var expectedDeltaYSs = slope * (centerXSs - anchorXSs);
        var actualDeltaYSs = slopedYCaptor.getValue() - flatYCaptor.getValue();

        assertThat((double) actualDeltaYSs).isCloseTo(expectedDeltaYSs, within(TOLERANCE));
    }

    @Test
    void testRenderTupletsFromLine_nullAnchorNote_skipsWithoutDrawing() {
        // A tuplet whose anchor element is null must be skipped silently
        var line = detachedLine();
        var anchor = quaver();
        var end = quaver();
        line.addElement(anchor);
        line.addElement(end);
        var tuplet = Tuplet.withUnresolvedRatio(anchor, end, 3);
        line.addSpan(tuplet);

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
        verify(g2, never()).fill(any(Shape.class));
        verify(g2, never()).drawString(any(String.class), any(float.class), any(float.class));
    }

    @Test
    void testRenderTupletsFromLine_nullEndNote_skipsWithoutDrawing() {
        // The right arm is positioned from the end note's own head width, so the renderer must have
        // an end note to measure. A tuplet whose end element has gone away — deleted, or its column
        // pruned — must be skipped silently rather than throwing while painting the score.
        var line = detachedLine();
        var anchor = quaver();
        var end = quaver();
        line.addElement(anchor);
        line.addElement(end);
        var tuplet = Tuplet.withUnresolvedRatio(anchor, end, 3);
        line.addSpan(tuplet);

        var decorLayout = new LayoutResult.DecorationLayout(1.0, -2.0, 4.0, 1.0, 0.0);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(tuplet, decorLayout)
            .build();
        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setLayoutResult(layoutResult)
            .build();

        // Null out the end element after the layout has been built
        tuplet.setEndElement(null);

        var g2 = mockG2();

        RENDERER.renderTupletsFromLine(g2, line, invariants, ElementFrame.LINE_LEVEL);

        verify(g2, never()).fill(any(Shape.class));
        verify(g2, never()).drawString(any(String.class), any(float.class), any(float.class));
    }
}
