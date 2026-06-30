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

import songscribe.dom.Line;
import songscribe.smufl.Engraving;
import songscribe.layout.LayoutResult;
import songscribe.dom.Tuplet;
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

            if (anchorNote == null) {
                continue;
            }

            var anchorXSs = decorLayout.xSs();
            var endXSs = anchorXSs + decorLayout.widthSs();
            var isUpper = anchorNote.getDirection().isUp();
            var stemSs = invariants.getLineThickness().stemSs();
            var leftXSs = anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS
                - (isUpper ? stemSs : Engraving.NOTE_HEAD_WIDTH_SS)
                - Tuplet.ARM_EXTENSION_SS;
            var rightXSs = endXSs + Engraving.NOTE_HEAD_WIDTH_SS + Tuplet.ARM_EXTENSION_SS;

            var numberOnly = tuplet.isNumberOnly(line);

            renderTuplet(g2, invariants, leftXSs, rightXSs, decorLayout.ySs(), tuplet.getGrade(), numberOnly);
        }
    }

    /**
     * Renders a single tuplet bracket (or number-only for beamed groups) using
     * pre-computed bracket coordinates in layout-relative staff spaces.
     *
     * @param g2         graphics context (scale transform already applied)
     * @param invariants        line invariants
     * @param leftXSs    left edge of the visual bracket
     * @param rightXSs   right edge of the visual bracket
     * @param ySs        Y of the reserved box top in layout space
     * @param grade      tuplet number (3 for triplet, 5 for quintuplet, etc.)
     * @param numberOnly true to draw only the number (no bracket)
     */
    private void renderTuplet(
        Graphics2D g2,
        LineInvariants invariants,
        double leftXSs,
        double rightXSs,
        double ySs,
        int grade,
        boolean numberOnly
    ) {
        // Convert layout Y to component Y — this is the top of the reserved box
        var boxTopYSs = RenderingUtils.layoutYToComponentYSs(ySs, invariants);
        var centerXSs = (leftXSs + rightXSs) / 2.0;

        // Shape the number once; its bounds drive both the bracket gap and centering
        var glyphVector = Tuplet.TUPLET_FONT.createGlyphVector(
            g2.getFontRenderContext(), String.valueOf(grade));
        var inkBounds = glyphVector.getVisualBounds();
        var halfGapSs = glyphVector.getLogicalBounds().getWidth() / 2.0 + TUPLET_NUMBER_GAP_SS;

        // With a bracket, shift the gap center rightward to account for italic slant
        var gapCenterXSs = numberOnly
            ? centerXSs
            : centerXSs + TUPLET_GAP_ITALIC_CORRECTION_SS / 2.0;

        var thicknessSs = invariants.getLineThickness().tupletBracketSs();

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(RenderingUtils.ELEMENT_COLOR);

            double numberBaselineYSs;

            if (numberOnly) {
                // Number-only: ink top sits at the box top
                numberBaselineYSs = boxTopYSs - inkBounds.getY();
            } else {
                // Bracketed: bracket line sits inside the box at inkH/2 from the top
                var bracketYSs = boxTopYSs + Tuplet.bracketLineOffsetSs();
                var armHeightSs = Tuplet.BRACKET_ARM_HEIGHT_SS;
                var gapLeftXSs = gapCenterXSs - halfGapSs;
                var gapRightXSs = gapCenterXSs + halfGapSs;

                var armBottomYSs = bracketYSs + armHeightSs;

                // Each side is a single path so its arm corner joins cleanly: down the vertical
                // arm, up to the corner, then across to the number gap. The number gap splits the
                // bracket into two separate paths.
                GraphicUtils.drawPath(g2, new Point2D[]{
                    new Point2D.Double(leftXSs, armBottomYSs),
                    new Point2D.Double(leftXSs, bracketYSs),
                    new Point2D.Double(gapLeftXSs, bracketYSs)
                }, thicknessSs);

                GraphicUtils.drawPath(g2, new Point2D[]{
                    new Point2D.Double(gapRightXSs, bracketYSs),
                    new Point2D.Double(rightXSs, bracketYSs),
                    new Point2D.Double(rightXSs, armBottomYSs)
                }, thicknessSs);

                // Number is centered on the bracket line
                numberBaselineYSs = bracketYSs - inkBounds.getCenterY();
            }

            // Center the number horizontally on the gap, then draw the shaped glyphs
            var numberXSs = (float) (gapCenterXSs - inkBounds.getWidth() / 2.0);
            g2.drawGlyphVector(glyphVector, numberXSs, (float) numberBaselineYSs);
        }
    }

}
