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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import songscribe.dom.Ending;
import songscribe.engraving.EngravingConstants;
import songscribe.font.TextMeasurement;
import songscribe.hit.HitTarget;
import songscribe.layout.EndingBracketGeometry;
import songscribe.shape.EndingBracketShape;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

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
     * @param g2         Graphics context
     * @param invariants Line invariants
     */
    public void renderEndings(Graphics2D g2, LineInvariants invariants) {
        var line = invariants.requireCurrentLine();

        for (var ending : line.findEndings()) {
            var decorationLayout = invariants.getLayoutResult().getDecorationLayout(ending);

            // StructuralStacker.stackEndings writes no layout for an ending whose
            // anchor/end note is missing or whose bracket ranges are empty. This loop
            // iterates the same model collection (findSpans(Ending.class)), so a
            // null layout here is an expected incomplete ending, not an error — skip it.
            if (decorationLayout == null) {
                continue;
            }

            var yTopSs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

            // The color depends only on the ending, so resolve it once rather than per bracket.
            // An ending belongs to no single note, so it has no owner whose color it could take.
            var color = RenderingUtils.decorationColor(
                new HitTarget.Ending(ending), null, invariants, ElementFrame.LINE_LEVEL);

            for (var bracket : ending.getBracketRanges()) {
                drawEnding(g2, ending, bracket, yTopSs, color);
            }
        }
    }

    /**
     * Draws a single ending bracket.
     *
     * @param g2      Graphics context
     * @param ending  The ending element
     * @param bracket The bracket range to draw
     * @param yTopSs  Top Y of the bracket in component staff-space units
     * @param color   The color to draw the bracket and its label in
     */
    private void drawEnding(
        Graphics2D g2,
        Ending ending,
        Ending.BracketRange bracket,
        double yTopSs,
        Color color
    ) {
        var yBottomSs = yTopSs + ending.getContentHeightSs();

        var thicknessSs = EngravingConstants.VOLTA_BRACKET_SS;

        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(color);

            // Bracket as a single path so the top corners join cleanly: up the left leg, across
            // the top, and (when present) down the right leg.
            var bracketPoints = EndingBracketShape.points(bracket, yTopSs, yBottomSs);

            GraphicUtils.drawPath(g2, bracketPoints, thicknessSs);

            // Draw ending label (e.g. "1." or "2.") using Bravura volta glyphs.
            // Baseline = bracket top + glyph height + visual offset below bracket.
            g2.setFont(EndingBracketGeometry.ENDING_FONT);
            var label = bracket.label();
            // Deliberately the paint-time render context rather than TextMeasurement's: the
            // digit-to-period gap below is widened by one device pixel, read from this
            // graphics' own transform, and the vector is drawn into this same graphics. Both
            // the gap and the ink must land in the coordinate space this transform defines,
            // which is the space only this context describes.
            var glyphVector = EndingBracketGeometry.ENDING_FONT.createGlyphVector(
                g2.getFontRenderContext(), label);
            var glyphHeightSs = TextMeasurement.inkHeight(glyphVector.getVisualBounds());

            // Add 1 device-pixel gap between the digit and the period
            var onePixelSs = 1.0 / g2.getTransform().getScaleX();
            var periodPos = glyphVector.getGlyphPosition(1);
            glyphVector.setGlyphPosition(1, new Point2D.Double(
                periodPos.getX() + onePixelSs, periodPos.getY()));

            g2.drawGlyphVector(
                glyphVector,
                (float) (bracket.x1Ss() + Ending.LABEL_X_INSET_SS),
                (float) (yTopSs + glyphHeightSs + Ending.LABEL_Y_OFFSET_SS));
        }
    }
}
