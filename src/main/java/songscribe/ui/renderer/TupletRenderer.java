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

import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.function.DoubleUnaryOperator;

import songscribe.dom.Tuplet;
import songscribe.engraving.EngravingConstants;
import songscribe.engraving.TupletMetrics;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.TupletGeometry;
import songscribe.shape.TupletBracketShape;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;

public final class TupletRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final double TUPLET_NUMBER_GAP_SS = 0.5;

    private static final double TUPLET_GAP_ITALIC_CORRECTION_SS = 0.1;

    /** The tuplet bracket stroke width as a multiple of the LilyPond base thickness. */
    private static final double TUPLET_BRACKET_MULTIPLIER = 1.61;

    private static final double TUPLET_BRACKET_SS =
        EngravingConstants.LILYPOND_BASE_THICKNESS_SS * TUPLET_BRACKET_MULTIPLIER;

    // Singleton instance
    private static final TupletRenderer INSTANCE = new TupletRenderer();

    private TupletRenderer() {
    }

    public static TupletRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    public void renderTupletsFromLine(
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var layoutResult = invariants.getLayoutResult();

        for (var entry : layoutResult.decorationLayouts().tuplets().entrySet()) {
            var tuplet = entry.getKey();
            var bracketFrame = TupletGeometry.BracketFrame.of(tuplet, entry.getValue(), layoutResult);

            renderTuplet(g2, invariants, frame, tuplet, bracketFrame);
        }
    }


    /**
     * @param g2           graphics context (scale transform already applied)
     * @param tuplet      the tuplet being drawn, as a click would address it
     * @param bracketFrame the tuplet's bracket edges and sloped top, in layout space
     */
    private void renderTuplet(
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame,
        Tuplet tuplet,
        TupletGeometry.BracketFrame bracketFrame
    ) {
        var grade = tuplet.getGrade().number();
        var style = TupletGeometry.Style.of(tuplet);
        var leftXSs = bracketFrame.leftEdgeXSs();
        var rightXSs = bracketFrame.rightEdgeXSs();
        var centerXSs = bracketFrame.centerXSs();

        // Shape the number once; its bounds drive both the bracket gap and centering
        var glyphVector = TupletGeometry.TUPLET_FONT.createGlyphVector(
            g2.getFontRenderContext(), String.valueOf(grade));
        var inkBounds = glyphVector.getVisualBounds();
        var halfGapSs = glyphVector.getLogicalBounds().getWidth() / 2.0 + TUPLET_NUMBER_GAP_SS;

        // With a bracket, shift the gap center rightward to account for italic slant
        var gapCenterXSs = switch (style) {
            case NUMBER_ONLY -> centerXSs;
            case BRACKETED -> centerXSs + TUPLET_GAP_ITALIC_CORRECTION_SS / 2.0;
        };

        var thicknessSs = TUPLET_BRACKET_SS;

        try (var _ = GraphicsState.save(g2, COLOR)) {
            // A tuplet spans notes rather than hanging off one, so it has no owner whose color it
            // could take: it is plain black unless it is itself the selection.
            g2.setColor(RenderingUtils.decorationColor(
                new HitTarget.Tuplet(tuplet), null, invariants, frame));

            double numberBaselineYSs;

            if (style == TupletGeometry.Style.NUMBER_ONLY) {
                // Number-only: no arms, but the box top is reserved sloped, so evaluate it at the
                // gap center along the slope. Placing the ink top at the flat left-endpoint Y instead
                // drops the number onto an ascending beam at the center.
                var boxTopAtCenterYSs = RenderingUtils.layoutYToComponentYSs(
                    bracketFrame.boxTopYAtSs(gapCenterXSs), invariants);
                numberBaselineYSs = boxTopAtCenterYSs - inkBounds.getY();
            } else {
                // Bracketed: the bracket line sits inside the box at inkH/2 from the top, then
                // tilts by the slope. Evaluate the line at each corner X.
                // LilyPond's arm is a line of BRACKET_ARM_HEIGHT_SS from the bracket line's
                // centerline whose round cap bulges a further half line-width past that end
                // (bracket.cc, make_bracket). drawPath instead lands ink exactly on the endpoint it
                // is given, so ask it for the cap-inclusive tip to reach the same ink extent.
                var armHeightSs = TupletMetrics.BRACKET_ARM_HEIGHT_SS + thicknessSs / 2.0;

                var gapLeftXSs = gapCenterXSs - halfGapSs;
                var gapRightXSs = gapCenterXSs + halfGapSs;

                // The layout-to-component conversion is a pure translation, so converting each Y
                // the frame gives keeps the line straight.
                DoubleUnaryOperator lineYAtSs = xSs -> RenderingUtils.layoutYToComponentYSs(
                    bracketFrame.bracketLineYAtSs(xSs), invariants);

                var leftBracketYSs = lineYAtSs.applyAsDouble(leftXSs);
                var rightBracketYSs = lineYAtSs.applyAsDouble(rightXSs);
                var gapLeftYSs = lineYAtSs.applyAsDouble(gapLeftXSs);
                var gapRightYSs = lineYAtSs.applyAsDouble(gapRightXSs);

                // Each vertical arm hangs straight down from its own sloped corner.
                var leftArmBottomYSs = leftBracketYSs + armHeightSs;
                var rightArmBottomYSs = rightBracketYSs + armHeightSs;

                // Each side is a single path so its arm corner joins cleanly: down the vertical
                // arm, up to the corner, then across to the number gap. The number gap splits the
                // bracket into two separate paths.
                GraphicUtils.drawPath(g2,
                    TupletBracketShape.leftArm(
                        new Line2D.Double(leftXSs, leftBracketYSs, gapLeftXSs, gapLeftYSs),
                        leftArmBottomYSs),
                    thicknessSs);

                GraphicUtils.drawPath(g2,
                    TupletBracketShape.rightArm(
                        new Line2D.Double(gapRightXSs, gapRightYSs, rightXSs, rightBracketYSs),
                        rightArmBottomYSs),
                    thicknessSs);

                // Number is centered on the sloped line at the gap center (off the geometric
                // midpoint by the italic correction), so it sits on the bracket as it tilts.
                var numberYSs = lineYAtSs.applyAsDouble(gapCenterXSs);
                numberBaselineYSs = numberYSs - inkBounds.getCenterY();
            }

            // Center the number horizontally on the gap, then draw the shaped glyphs
            var numberXSs = (float) (gapCenterXSs - inkBounds.getWidth() / 2.0);
            g2.drawGlyphVector(glyphVector, numberXSs, (float) numberBaselineYSs);
        }
    }

}
