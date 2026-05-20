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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;

import module java.desktop;

import songscribe.dom.Line;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.dom.LineElement;
import songscribe.util.GraphicUtils;

/**
 * Renders first and second ending brackets.
 * <p>
 * Endings are bracket lines that indicate which measures to play during
 * different iterations of a repeat. First ending is played the first time,
 * second ending is played on the repeat.
 */
public final class EndingRenderer extends BaseElementRenderer<LineElement> {

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

    @Override
    protected void renderElement(
        LineInvariants invariants,
        ElementFrame frame,
        LineElement element,
        Graphics2D g2
    ) {
        // EndingRenderer is called directly with Line, not through element interface
        // This method is a placeholder for the interface requirement
    }

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
            for (var bracket : ending.getBracketRanges()) {
                drawEnding(g2, invariants, ending, bracket);
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
     */
    private void drawEnding(
        Graphics2D g2,
        LineInvariants invariants,
        Ending ending,
        Ending.BracketRange bracket
    ) {
        var x1 = bracket.x1Ss();
        var x2 = bracket.x2Ss();
        var yTopSs = getEffectiveEndingYSs(invariants, ending);
        var yBottomSs = yTopSs + ending.getContentHeightSs();

        var thicknessSs = invariants.getLineThickness().voltaBracketSs();

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(ELEMENT_COLOR);

            // Horizontal top
            GraphicUtils.fillHorizontalLine(g2, x1, x2, yTopSs, thicknessSs);

            // Left vertical leg — round cap at top tucks inside the horizontal line
            GraphicUtils.fillVerticalLine(g2, x1, yTopSs, yBottomSs, thicknessSs);

            if (bracket.hasClosingStroke()) {
                GraphicUtils.fillVerticalLine(g2, x2, yTopSs, yBottomSs, thicknessSs);
            }

            // Draw ending label (e.g. "1." or "2.") using Bravura volta glyphs.
            // Baseline = bracket top + glyph height + visual offset below bracket.
            g2.setFont(Ending.ENDING_FONT);
            var label = bracket.label();
            var glyphVector = Ending.ENDING_FONT.createGlyphVector(g2.getFontRenderContext(), label);
            var glyphHeightSs = -glyphVector.getVisualBounds().getY();

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


    // ==========================================================================
    // Layout access
    // ==========================================================================

    /**
     * Returns the top Y coordinate for an ending bracket in component staff-space units.
     */
    private double getEffectiveEndingYSs(LineInvariants invariants, Ending ending) {
        var decorationLayout = invariants.getLayoutResult().getDecorationLayout(ending);

        if (decorationLayout != null) {
            return layoutYToComponentYSs(decorationLayout.ySs(), invariants);
        }

        throw new IllegalStateException("No layout found for Ending element");
    }
}
