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
import static songscribe.util.GraphicsState.Property.FONT;

import module java.desktop;

import songscribe.dom.Line;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.engraving.LineThickness;
import songscribe.shape.EndingBracketShape;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Renders first and second ending brackets.
 * <p>
 * Endings are bracket lines that indicate which measures to play during
 * different iterations of a repeat. First ending is played the first time,
 * second ending is played on the repeat.
 */
public final class EndingRenderer {

    // Singleton instance
    private static final EndingRenderer INSTANCE = new EndingRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private EndingRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static EndingRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders all first/second endings for a line.
     *
     * @param g2        Graphics context
     * @param line      The line
     * @param lineIndex Line index
     * @param invariants       Line invariants
     */
    public void renderEndings(
        Graphics2D g2,
        Line line,
        int lineIndex,
        LineInvariants invariants
    ) {
        for (var ending : LineEndingSupport.findEndings(line)) {
            var decorationLayout = invariants.getLayoutResult().getDecorationLayout(ending);

            // StructuralStacker.stackEndings writes no layout for an ending whose
            // anchor/end note is missing or whose bracket ranges are empty. This loop
            // iterates the same model collection (findRangeElements(Ending.class)), so a
            // null layout here is an expected incomplete ending, not an error — skip it.
            if (decorationLayout == null) {
                continue;
            }

            var yTopSs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

            for (var bracket : ending.getBracketRanges()) {
                drawEnding(g2, invariants, ending, bracket, yTopSs);
            }
        }
    }

    /**
     * Draws a single ending bracket.
     *
     * @param g2      Graphics context
     * @param invariants     Line invariants
     * @param ending  The ending element
     * @param bracket The bracket range to draw
     * @param yTopSs  Top Y of the bracket in component staff-space units
     */
    private void drawEnding(
        Graphics2D g2,
        LineInvariants invariants,
        Ending ending,
        Ending.BracketRange bracket,
        double yTopSs
    ) {
        var x1 = bracket.x1Ss();
        var x2 = bracket.x2Ss();
        var yBottomSs = yTopSs + ending.getContentHeightSs();

        var thicknessSs = LineThickness.VOLTA_BRACKET_SS;

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(RenderingUtils.ELEMENT_COLOR);

            // Bracket as a single path so the top corners join cleanly: up the left leg, across
            // the top, and (when present) down the right leg.
            var bracketPoints = EndingBracketShape.points(
                x1, x2, yTopSs, yBottomSs, bracket.hasClosingStroke());

            GraphicUtils.drawPath(g2, bracketPoints, thicknessSs);

            // Draw ending label (e.g. "1." or "2.") using Bravura volta glyphs.
            // Baseline = bracket top + glyph height + visual offset below bracket.
            g2.setFont(Ending.ENDING_FONT);
            var label = bracket.label();
            var glyphVector = Ending.ENDING_FONT.createGlyphVector(g2.getFontRenderContext(), label);
            var glyphHeightSs = GraphicUtils.inkHeight(glyphVector.getVisualBounds());

            // Add 1 device-pixel gap between the digit and the period
            var onePixelSs = 1.0 / g2.getTransform().getScaleX();
            var periodPos = glyphVector.getGlyphPosition(1);
            glyphVector.setGlyphPosition(1, new Point2D.Double(
                periodPos.getX() + onePixelSs, periodPos.getY()));

            g2.drawGlyphVector(
                glyphVector,
                (float) (x1 + Ending.LABEL_X_INSET_SS),
                (float) (yTopSs + glyphHeightSs + Ending.LABEL_Y_OFFSET_SS));
        }
    }
}
