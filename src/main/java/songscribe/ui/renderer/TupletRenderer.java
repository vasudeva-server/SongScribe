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

import static songscribe.util.GraphicsState.Property.COLOR;

import module java.desktop;

import java.util.function.DoubleUnaryOperator;

import songscribe.dom.Line;
import songscribe.layout.LayoutResult;
import songscribe.engraving.LineThickness;
import songscribe.dom.Tuplet;
import songscribe.hit.HitTarget;
import songscribe.shape.TupletBracketShape;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Renders tuplet brackets with numbers using staff-space coordinates
 * from {@link LayoutResult.DecorationLayout}.
 * <p>
 * Bracket styling follows LilyPond conventions: round caps/joins,
 * vertical arms pointing toward notes, and an italic serif number.
 */
public final class TupletRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Clearance on each side of tuplet number in bracket (LilyPond: 0.5ss per side) */
    private static final double TUPLET_NUMBER_GAP_SS = 0.5;  // 4px

    /** Rightward italic correction for tuplet number gap (LilyPond: +0.1ss) */
    private static final double TUPLET_GAP_ITALIC_CORRECTION_SS = 0.1;  // 0.8px

    // Singleton instance
    private static final TupletRenderer INSTANCE = new TupletRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TupletRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static TupletRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders all tuplets for a line by iterating {@link Tuplet} range elements
     * and reading pre-computed positions from {@link LayoutResult.DecorationLayout}.
     */
    public void renderTupletsFromLine(
        Graphics2D g2,
        Line line,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var layoutResult = invariants.getLayoutResult();

        for (var tuplet : line.findRangeElements(Tuplet.class)) {
            var decorLayout = layoutResult.getDecorationLayout(tuplet);

            // StructuralStacker.stackSpanElement writes no layout for a tuplet whose
            // anchor/end note (or their column) is missing. This loop iterates the same
            // model collection, so a null layout here is an expected incomplete tuplet — skip it.
            if (decorLayout == null) {
                continue;
            }

            var anchorNote = tuplet.getAnchorElement();
            var endNote = tuplet.getEndElement();

            if (anchorNote == null || endNote == null) {
                continue;
            }

            var anchorXSs = decorLayout.xSs();
            var endXSs = anchorXSs + decorLayout.widthSs();
            var anchorType = anchorNote.getType();
            var isUpper = anchorNote.getDirection().isUp();
            var leftXSs = Tuplet.bracketLeftEdgeXSs(anchorXSs, anchorType.isNoteWithStem(),
                isUpper, LineThickness.STEM_SS, anchorType.getElementWidthSs());
            var rightXSs = Tuplet.bracketRightEdgeXSs(endXSs,
                endNote.getType().getElementWidthSs());

            var numberOnly = tuplet.isNumberOnly(line);
            var bracketLine = new BracketLine(anchorXSs, decorLayout.widthSs(),
                decorLayout.ySs(), decorLayout.dySs());

            renderTuplet(g2, invariants, frame, tuplet, bracketLine, leftXSs, rightXSs, numberOnly);
        }
    }

    /**
     * The tuplet bracket line's slope origin and rise, in layout-relative staff spaces.
     *
     * @param anchorXSs X of the anchor element column — the slope origin the line is measured from
     * @param widthSs   anchor→end element run that {@code dySs} is the rise over
     * @param ySs       Y of the reserved box top (left endpoint, at {@code anchorXSs}) in layout space
     * @param dySs      rise of the bracket line over {@code widthSs} (negative = ascending contour)
     */
    private record BracketLine(double anchorXSs, double widthSs, double ySs, double dySs) {
    }

    /**
     * Renders a single tuplet bracket (or number-only for beamed groups) using
     * pre-computed bracket coordinates in layout-relative staff spaces.
     *
     * @param g2          graphics context (scale transform already applied)
     * @param invariants  line invariants
     * @param frame       the element frame of the enclosing pass
     * @param tuplet      the tuplet being drawn, as a click would address it
     * @param bracketLine slope origin and rise of the bracket line
     * @param leftXSs     left edge of the visual bracket
     * @param rightXSs    right edge of the visual bracket
     * @param numberOnly  true to draw only the number (no bracket)
     */
    private void renderTuplet(
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame,
        Tuplet tuplet,
        BracketLine bracketLine,
        double leftXSs,
        double rightXSs,
        boolean numberOnly
    ) {
        var anchorXSs = bracketLine.anchorXSs();
        var grade = tuplet.getGrade();

        // Convert layout Y to component Y — this is the top of the reserved box at the anchor X.
        // The conversion is a pure translation, so evaluating the sloped line in component space
        // (boxTop + slope * dx) matches converting each endpoint's layout Y individually.
        var boxTopYSs = RenderingUtils.layoutYToComponentYSs(bracketLine.ySs(), invariants);
        var centerXSs = (leftXSs + rightXSs) / 2.0;

        // Slope stays constant across the wider arm span; extrapolate from the anchor origin over
        // the same run dySs was fit to. widthSs is always positive (anchor→end element run).
        var slope = bracketLine.dySs() / bracketLine.widthSs();

        // Shape the number once; its bounds drive both the bracket gap and centering
        var glyphVector = Tuplet.TUPLET_FONT.createGlyphVector(
            g2.getFontRenderContext(), String.valueOf(grade));
        var inkBounds = glyphVector.getVisualBounds();
        var halfGapSs = glyphVector.getLogicalBounds().getWidth() / 2.0 + TUPLET_NUMBER_GAP_SS;

        // With a bracket, shift the gap center rightward to account for italic slant
        var gapCenterXSs = numberOnly
            ? centerXSs
            : centerXSs + TUPLET_GAP_ITALIC_CORRECTION_SS / 2.0;

        var thicknessSs = LineThickness.TUPLET_BRACKET_SS;

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            // A tuplet spans notes rather than hanging off one, so it has no owner whose color it
            // could take: it is plain black unless it is itself the selection.
            g2.setColor(RenderingUtils.decorationColor(
                new HitTarget.Tuplet(tuplet), null, invariants, frame));

            double numberBaselineYSs;

            if (numberOnly) {
                // Number-only: no arms, but the box top is reserved sloped, so evaluate it at the
                // gap center along the slope. Placing the ink top at the flat left-endpoint Y instead
                // drops the number onto an ascending beam at the center (issue #556).
                var boxTopAtCenterYSs = boxTopYSs + slope * (gapCenterXSs - anchorXSs);
                numberBaselineYSs = boxTopAtCenterYSs - inkBounds.getY();
            } else {
                // Bracketed: the bracket line sits inside the box at inkH/2 from the top, then
                // tilts by the slope. Evaluate the line at each corner X from the anchor origin.
                var bracketLineOffsetSs = Tuplet.bracketLineOffsetSs();

                // LilyPond's arm is a line of BRACKET_ARM_HEIGHT_SS from the bracket line's
                // centerline whose round cap bulges a further half line-width past that end
                // (bracket.cc, make_bracket). drawPath instead lands ink exactly on the endpoint it
                // is given, so ask it for the cap-inclusive tip to reach the same ink extent.
                var armHeightSs = Tuplet.BRACKET_ARM_HEIGHT_SS + thicknessSs / 2.0;

                var gapLeftXSs = gapCenterXSs - halfGapSs;
                var gapRightXSs = gapCenterXSs + halfGapSs;

                DoubleUnaryOperator lineYAtSs =
                    xSs -> boxTopYSs + bracketLineOffsetSs + slope * (xSs - anchorXSs);

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
                    TupletBracketShape.leftArm(leftXSs, gapLeftXSs, leftBracketYSs, gapLeftYSs,
                        leftArmBottomYSs),
                    thicknessSs);

                GraphicUtils.drawPath(g2,
                    TupletBracketShape.rightArm(gapRightXSs, rightXSs, gapRightYSs, rightBracketYSs,
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
