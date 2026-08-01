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
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.layout.LayoutResult;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class HairpinRendererTest extends UnitTest {

    // A hairpin with a clear left, top, height, and width so we can compute
    // all expected endpoint coordinates from first principles.
    private static final double HAIRPIN_X_SS = 2.0;
    private static final double HAIRPIN_LAYOUT_Y_SS = -1.0;  // layout-space Y
    private static final double HAIRPIN_WIDTH_SS = 8.0;
    private static final double HAIRPIN_HEIGHT_SS = 2.0;

    private static LayoutResult.DecorationLayout hairpinLayout() {
        return new LayoutResult.DecorationLayout(
            HAIRPIN_X_SS, HAIRPIN_LAYOUT_Y_SS, HAIRPIN_WIDTH_SS, HAIRPIN_HEIGHT_SS, 0.0);
    }

    // ==========================================================================
    // computeHairpinLines — crescendo vs diminuendo endpoints (row 40)
    // ==========================================================================

    @Test
    void testComputeHairpinLinesCrescendoStartsAtMiddleY() {
        // Crescendo: both lines originate at the middle Y of the left edge.
        // middleYSs = topYSs + height/2
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        // middleLineYSs=0 → topYSs = 0 + HAIRPIN_LAYOUT_Y_SS
        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var middleYSs = topYSs + HAIRPIN_HEIGHT_SS / 2.0;

        var lines = HairpinRenderer.computeHairpinLines(layout, true, invariants);

        assertThat(lines).hasSize(2);
        assertThat(lines[0].y1).isEqualTo(middleYSs);
        assertThat(lines[1].y1).isEqualTo(middleYSs);
    }

    @Test
    void testComputeHairpinLinesCrescendoEndsAtTopAndBottomY() {
        // Crescendo: upper line ends at topYSs, lower line ends at bottomYSs.
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var bottomYSs = topYSs + HAIRPIN_HEIGHT_SS;

        var lines = HairpinRenderer.computeHairpinLines(layout, true, invariants);

        assertThat(lines[0].y2).isEqualTo(topYSs);
        assertThat(lines[1].y2).isEqualTo(bottomYSs);
    }

    @Test
    void testComputeHairpinLinesDiminuendoEndsAtMiddleY() {
        // Diminuendo: both lines converge at the middle Y of the right edge.
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var middleYSs = topYSs + HAIRPIN_HEIGHT_SS / 2.0;

        var lines = HairpinRenderer.computeHairpinLines(layout, false, invariants);

        assertThat(lines).hasSize(2);
        assertThat(lines[0].y2).isEqualTo(middleYSs);
        assertThat(lines[1].y2).isEqualTo(middleYSs);
    }

    @Test
    void testComputeHairpinLinesDiminuendoStartsAtTopAndBottomY() {
        // Diminuendo: upper line starts at topYSs, lower line starts at bottomYSs.
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var topYSs = invariants.getMiddleLineYSs() + HAIRPIN_LAYOUT_Y_SS;
        var bottomYSs = topYSs + HAIRPIN_HEIGHT_SS;

        var lines = HairpinRenderer.computeHairpinLines(layout, false, invariants);

        assertThat(lines[0].y1).isEqualTo(topYSs);
        assertThat(lines[1].y1).isEqualTo(bottomYSs);
    }

    @Test
    void testComputeHairpinLinesBothTypesShareSameXCoordinates() {
        // Both crescendo and diminuendo span the same X range (x1 to x2 = x1 + width).
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();
        var expectedX2 = HAIRPIN_X_SS + HAIRPIN_WIDTH_SS;

        var crescendoLines = HairpinRenderer.computeHairpinLines(layout, true, invariants);
        var diminuendoLines = HairpinRenderer.computeHairpinLines(layout, false, invariants);

        for (var line : crescendoLines) {
            assertThat(line.x1).isEqualTo(HAIRPIN_X_SS);
            assertThat(line.x2).isEqualTo(expectedX2);
        }

        for (var line : diminuendoLines) {
            assertThat(line.x1).isEqualTo(HAIRPIN_X_SS);
            assertThat(line.x2).isEqualTo(expectedX2);
        }
    }

    @Test
    void testComputeHairpinLinesCrescendoAndDiminuendoEndpointsAreMirrored() {
        // The two shapes are mirror images: crescendo opens right, diminuendo opens left.
        // crescendo line[0]: (x1,mid)→(x2,top)  vs  diminuendo line[0]: (x1,top)→(x2,mid)
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var layout = hairpinLayout();

        var crescendoLines = HairpinRenderer.computeHairpinLines(layout, true, invariants);
        var diminuendoLines = HairpinRenderer.computeHairpinLines(layout, false, invariants);

        // Upper line: crescendo starts at middle, diminuendo starts at top (differs)
        assertThat(crescendoLines[0].y1).isNotEqualTo(diminuendoLines[0].y1);

        // Upper line: crescendo ends at top, diminuendo ends at middle (differs)
        assertThat(crescendoLines[0].y2).isNotEqualTo(diminuendoLines[0].y2);

        // The start of crescendo == the end of diminuendo (mirror symmetry)
        assertThat(crescendoLines[0].y1).isEqualTo(diminuendoLines[0].y2);
        assertThat(crescendoLines[0].y2).isEqualTo(diminuendoLines[0].y1);
    }

    // ==========================================================================
    // hitTestHairpin — wedge bounding-box containment
    // ==========================================================================

    private static final HairpinRenderer RENDERER = HairpinRenderer.getInstance();

    private static final double HIT_X_SS = 2.0;
    private static final double HIT_LAYOUT_Y_SS = -3.0;
    private static final double HIT_WIDTH_SS = 10.0;
    private static final double HIT_HEIGHT_SS = 1.25;
    private static final double HIT_MARGIN_SS = 0.5;
    private static final double MIDDLE_LINE_Y_SS = 5.0;

    /** Top of the hit box in component space. */
    private static final double HIT_BOX_TOP_Y_SS = MIDDLE_LINE_Y_SS + HIT_LAYOUT_Y_SS;

    /** A Y comfortably inside the box, clear of both the top and bottom edges. */
    private static final double Y_INSIDE_BOX_SS = HIT_BOX_TOP_Y_SS + HIT_HEIGHT_SS / 2.0;

    /** A Y below the box: past the wedge height and its margin band. */
    private static final double Y_BELOW_BOX_SS = HIT_BOX_TOP_Y_SS + HIT_HEIGHT_SS + HIT_MARGIN_SS + 1.0;

    /** An X inside the box, clear of the left edge. */
    private static final double X_INSIDE_BOX_SS = HIT_X_SS + 1.0;

    /** An X to the right of the box. */
    private static final double X_RIGHT_OF_BOX_SS = HIT_X_SS + HIT_WIDTH_SS + 1.0;

    private static LayoutResult.DecorationLayout hitBoxLayout() {
        return new LayoutResult.DecorationLayout(
            HIT_X_SS, HIT_LAYOUT_Y_SS, HIT_WIDTH_SS, HIT_HEIGHT_SS, HIT_MARGIN_SS);
    }

    /** A detached line paired with the single hairpin it carries as a range element. */
    private record LineWithHairpin(Line line, Hairpin hairpin) {}

    /**
     * Builds a detached two-note line carrying a crescendo as a range element, so
     * {@code hitTestHairpin}'s scan over {@link Line#getRangeElements()} can find it.
     */
    private LineWithHairpin lineWithCrescendo() {
        var line = twoNoteLine();
        var crescendo = new Crescendo(line.getElement(0), line.getElement(1));
        line.addRangeElement(crescendo);
        return new LineWithHairpin(line, crescendo);
    }

    /** As {@link #lineWithCrescendo()}, but with a diminuendo. */
    private LineWithHairpin lineWithDiminuendo() {
        var line = twoNoteLine();
        var diminuendo = new Diminuendo(line.getElement(0), line.getElement(1));
        line.addRangeElement(diminuendo);
        return new LineWithHairpin(line, diminuendo);
    }

    /** Creates a crescendo spanning a fresh detached line's two notes. */
    private Crescendo newCrescendo() {
        var line = twoNoteLine();
        return new Crescendo(line.getElement(0), line.getElement(1));
    }

    private Line twoNoteLine() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        return line;
    }

    private static @Nullable Hairpin hitTest(
        double xSs,
        double ySs,
        Line line,
        @Nullable LayoutResult layoutResult
    ) {
        return RENDERER.hitTestHairpin(xSs, ySs, line, layoutResult, MIDDLE_LINE_Y_SS);
    }

    private static LayoutResult layoutResultFor(Hairpin hairpin) {
        return LayoutResult.builder().putDecorationLayout(hairpin, hitBoxLayout()).build();
    }

    @Test
    void testHitTestHairpinPointInsideCrescendoBoxReturnsIt() {
        var fixture = lineWithCrescendo();

        var result = hitTest(
            X_INSIDE_BOX_SS, Y_INSIDE_BOX_SS, fixture.line(), layoutResultFor(fixture.hairpin()));

        assertThat(result).isSameAs(fixture.hairpin());
    }

    // The bounding box is direction-agnostic, but a diminuendo must be found by the same
    // scan — the type filter rejects everything that is not a Hairpin subclass.
    @Test
    void testHitTestHairpinPointInsideDiminuendoBoxReturnsIt() {
        var fixture = lineWithDiminuendo();

        var result = hitTest(
            X_INSIDE_BOX_SS, Y_INSIDE_BOX_SS, fixture.line(), layoutResultFor(fixture.hairpin()));

        assertThat(result).isSameAs(fixture.hairpin());
    }

    @Test
    void testHitTestHairpinPointBelowBoxReturnsNull() {
        var fixture = lineWithCrescendo();

        var result = hitTest(
            X_INSIDE_BOX_SS, Y_BELOW_BOX_SS, fixture.line(), layoutResultFor(fixture.hairpin()));

        assertThat(result).isNull();
    }

    @Test
    void testHitTestHairpinPointRightOfBoxReturnsNull() {
        var fixture = lineWithCrescendo();

        var result = hitTest(
            X_RIGHT_OF_BOX_SS, Y_INSIDE_BOX_SS, fixture.line(), layoutResultFor(fixture.hairpin()));

        assertThat(result).isNull();
    }

    // The margin band below the wedge is part of the hit box, so a click just under the
    // wedge still selects it.
    @Test
    void testHitTestHairpinPointInMarginBandReturnsHairpin() {
        var fixture = lineWithCrescendo();
        var yInMarginSs = HIT_BOX_TOP_Y_SS + HIT_HEIGHT_SS + HIT_MARGIN_SS / 2.0;

        var result = hitTest(
            X_INSIDE_BOX_SS, yInMarginSs, fixture.line(), layoutResultFor(fixture.hairpin()));

        assertThat(result).isSameAs(fixture.hairpin());
    }

    // Hit-testing can run before the first layout pass, so a null layout result must be
    // answered with a miss rather than a NullPointerException.
    @Test
    void testHitTestHairpinNullLayoutResultReturnsNull() {
        var fixture = lineWithCrescendo();

        var result = hitTest(X_INSIDE_BOX_SS, Y_INSIDE_BOX_SS, fixture.line(), null);

        assertThat(result).isNull();
    }

    // A hairpin whose anchor or end note is missing gets no layout entry. It must be
    // skipped, not dereferenced.
    @Test
    void testHitTestHairpinWithoutDecorationLayoutIsSkipped() {
        var fixture = lineWithCrescendo();

        var result = hitTest(
            X_INSIDE_BOX_SS, Y_INSIDE_BOX_SS, fixture.line(), LayoutResult.builder().build());

        assertThat(result).isNull();
    }

    // A hairpin with no layout must not stop the scan: the next one still gets tested.
    @Test
    void testHitTestHairpinSkipsUnlaidOutHairpinAndFindsTheNextOne() {
        var line = twoNoteLine();
        var unlaidOut = new Crescendo(line.getElement(0), line.getElement(1));
        var laidOut = new Diminuendo(line.getElement(0), line.getElement(1));
        line.addRangeElement(unlaidOut);
        line.addRangeElement(laidOut);

        var result = hitTest(X_INSIDE_BOX_SS, Y_INSIDE_BOX_SS, line, layoutResultFor(laidOut));

        assertThat(result).isSameAs(laidOut);
    }

    // ==========================================================================
    // renderHairpinsFromLine — selection color
    // ==========================================================================

    /**
     * Renders {@code hairpin} and returns the colors the renderer had installed on the
     * graphics context at each {@code draw} call — the color the wedge lines are painted in.
     */
    private static List<Color> drawColorsFor(Hairpin hairpin, boolean selected) {
        var builder = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResultFor(hairpin))
            .setMiddleLineYSs(MIDDLE_LINE_Y_SS);

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isDecorationSelected(hairpin, 0)).thenReturn(selected);
        builder.setSelectionProvider(selectionProvider);

        var g2 = spy(RenderContextTestHelper.realG2());
        var drawColors = new ArrayList<Color>();
        doAnswer(answerVoid((Shape shape) -> drawColors.add(g2.getColor()))).when(g2).draw(any(Shape.class));

        HairpinRenderer.getInstance().renderHairpinsFromLine(g2, builder.build());

        return drawColors;
    }

    @Test
    void testRenderHairpinsDrawsSelectedHairpinInSelectionColor() {
        var colors = drawColorsFor(newCrescendo(), true);

        assertThat(colors)
            .as("both wedge lines are drawn")
            .hasSize(2);
        assertThat(colors).containsOnly(ScoreView.getSelectionColor());
    }

    @Test
    void testRenderHairpinsDrawsUnselectedHairpinInTheElementColor() {
        var colors = drawColorsFor(newCrescendo(), false);

        assertThat(colors).hasSize(2);
        assertThat(colors).containsOnly(Color.BLACK);
    }
}
