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

import songscribe.music.Line;
import songscribe.music.TupletSpan;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.Tuplet;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Renders tuplet brackets with numbers using staff-space coordinates
 * from {@link LayoutResult.SpanLayout}.
 * <p>
 * Bracket styling follows LilyPond conventions: round caps/joins,
 * vertical arms pointing toward notes, and an italic serif number.
 */
public final class TupletRenderer extends BaseElementRenderer<Tuplet> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Italic serif font for tuplet numbers, matching LilyPond's italic style. */
    private static final Font TUPLET_FONT = MyFontUtils.getLocalFont("C059-Italic.otf", 1.8f);

    /** Clearance on each side of tuplet number in bracket (LilyPond: 0.5ss per side) */
    private static final double TUPLET_NUMBER_GAP_SS = 0.5;  // 4px

    /** Vertical arm height of bracket endpoints (LilyPond: 0.7ss) */
    private static final double TUPLET_BRACKET_OVERHANG_SS = 0.7;  // 5.6px

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

    @Override
    protected void renderElement(
        Tuplet element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        // Tuplets are rendered via renderTupletsFromLine(), not through
        // the per-element interface
    }

    /**
     * Renders all tuplets for a line by iterating legacy {@link TupletSpan}
     * data and reading pre-computed positions from {@link LayoutResult.SpanLayout}.
     */
    public void renderTupletsFromLine(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            return;
        }

        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var span = iter.next();
            var spanLayout = layoutResult.getSpanLayout(span);

            if (spanLayout == null) {
                continue;
            }

            var startNote = line.getElement(span.getStart());
            var isUpper = startNote.isUpper();

            // Beamed + stems up: number only (beam already provides visual grouping)
            var allBeamed = line.getBeamings().findSpan(span.getStart()) != null
                && line.getBeamings().findSpan(span.getEnd()) != null;
            var numberOnly = allBeamed && isUpper;

            renderTuplet(g2, ctx, spanLayout, span.getGrade(), numberOnly);
        }
    }

    /**
     * Renders a single tuplet bracket (or number-only for beamed groups) using
     * pre-computed positions from the layout result.
     *
     * @param g2         graphics context (scale transform already applied)
     * @param ctx        render context
     * @param spanLayout pre-computed bracket position in layout-relative staff spaces
     * @param grade      tuplet number (3 for triplet, 5 for quintuplet, etc.)
     * @param isUpper    true if the first note has an up stem
     * @param numberOnly true to draw only the number (no bracket)
     */
    private void renderTuplet(
        Graphics2D g2,
        ElementRenderContext ctx,
        LayoutResult.SpanLayout spanLayout,
        int grade,
        boolean numberOnly
    ) {
        // Convert layout Y to component Y
        var bracketYSs = layoutYToComponentYSs(spanLayout.ySs(), ctx);

        // spanLayout stores the actual visual bracket bounds
        var leftXSs = spanLayout.startXSs();
        var rightXSs = spanLayout.endXSs();
        var centerXSs = (leftXSs + rightXSs) / 2.0;

        // Measure number width for gap calculation
        var numberAdvanceSs = measureNumberAdvanceSs(g2, grade);
        var halfGapSs = numberAdvanceSs / 2.0 + TUPLET_NUMBER_GAP_SS;

        // With a bracket, shift the gap center rightward to account for italic slant
        var gapCenterXSs = numberOnly
            ? centerXSs
            : centerXSs + TUPLET_GAP_ITALIC_CORRECTION_SS / 2.0;

        var thicknessSs = ctx.getLineThickness().tupletBracketSs();

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setColor(ELEMENT_COLOR);

            if (!numberOnly) {
                var armHeightSs = TUPLET_BRACKET_OVERHANG_SS;
                var gapLeftXSs = gapCenterXSs - halfGapSs;
                var gapRightXSs = gapCenterXSs + halfGapSs;

                // Left bracket arm (from left endpoint to gap)
                GraphicUtils.fillHorizontalLine(g2, leftXSs, gapLeftXSs, bracketYSs, thicknessSs);

                // Right bracket arm (from gap to right endpoint)
                GraphicUtils.fillHorizontalLine(g2, gapRightXSs, rightXSs, bracketYSs, thicknessSs);

                // Left vertical arm — round cap at top tucks inside the horizontal line
                GraphicUtils.fillVerticalLine(g2, leftXSs,
                    bracketYSs, bracketYSs + armHeightSs, thicknessSs);

                // Right vertical arm — round cap at top tucks inside the horizontal line
                GraphicUtils.fillVerticalLine(g2, rightXSs,
                    bracketYSs, bracketYSs + armHeightSs, thicknessSs);
            }

            // Draw tuplet number centered on the bracket line
            drawTupletNumber(g2, grade, gapCenterXSs, bracketYSs);
        }
    }

    // ==========================================================================
    // Number drawing
    // ==========================================================================

    /**
     * Draws the tuplet number centered horizontally at {@code centerXSs} and vertically
     * centered on {@code bracketYSs} using the glyph's ink bounds.
     */
    private static void drawTupletNumber(
        Graphics2D g2,
        int grade,
        double centerXSs,
        double bracketYSs
    ) {
        g2.setFont(TUPLET_FONT);
        var text = String.valueOf(grade);
        var glyphVector = TUPLET_FONT.createGlyphVector(g2.getFontRenderContext(), text);
        var inkBounds = glyphVector.getVisualBounds();

        // Center ink bounding box on the bracket line
        float x = (float) (centerXSs - inkBounds.getWidth() / 2.0);
        float baseline = (float) (bracketYSs - inkBounds.getCenterY());
        g2.drawString(text, x, baseline);
    }

    /**
     * Measures the advance width of a tuplet number in staff spaces.
     */
    private static double measureNumberAdvanceSs(Graphics2D g2, int grade) {
        return g2.getFontMetrics(TUPLET_FONT).stringWidth(String.valueOf(grade));
    }
}
