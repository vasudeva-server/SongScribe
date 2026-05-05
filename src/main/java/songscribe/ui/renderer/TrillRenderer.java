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
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LayoutResult;
import songscribe.smufl.Engraving;
import songscribe.ui.layout.Trill;

/**
 * Renders trill markings (tr symbol + wavy line for extended trills).
 */
public final class TrillRenderer extends BaseElementRenderer<Trill> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Trill glyph advance width in staff-space units, used to position the wavy line start
    private static final double TRILL_ADVANCE_WIDTH_SS;

    // Wavy line segment width from SMuFL repeatOffset (0.948 ss)
    private static final double WIGGLE_SEGMENT_WIDTH_SS = 0.948;

    // Default fallback advance width in staff-space units (~2.125 ss)
    private static final double DEFAULT_TRILL_ADVANCE_WIDTH_SS = 2.125;

    // Singleton instance
    private static final TrillRenderer INSTANCE = new TrillRenderer();

    static {
        var advanceWidth = SMuFLMetadata.getInstance().getAdvanceWidth(SMuFLGlyph.ORNAMENT_TRILL);
        TRILL_ADVANCE_WIDTH_SS = (advanceWidth != null) ? advanceWidth : DEFAULT_TRILL_ADVANCE_WIDTH_SS;
    }

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TrillRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static TrillRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        Trill element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorElement();

        if (anchorNote == null) {
            return;
        }

        var color = getDecorationColor(anchorNote, ctx);
        var decorationLayout = ctx.getLayoutResult().getDecorationLayout(element);

        if (decorationLayout == null) {
            return;
        }

        var trillTopYSs = layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        var layoutXSs = decorationLayout.xSs();

        renderTrillAtPosition(
            g2, anchorNote, element.getEndElement(),
            layoutXSs, trillTopYSs, color, ctx.getLayoutResult());
    }

    /**
     * Resolves the trill X position and end X position, then delegates to
     * {@link #renderTrill(Graphics2D, double, double, double, Color)}.
     */
    private void renderTrillAtPosition(
        Graphics2D g2,
        StaffElement anchor,
        @Nullable StaffElement endNote,
        double layoutXSs,
        double trillTopYSs,
        Color color,
        LayoutResult layoutResult
    ) {
        if (endNote != null && endNote != anchor) {
            var endXSs = layoutResult.getElementXSs(endNote) + Engraving.NOTE_HEAD_WIDTH_SS;
            renderTrill(g2, layoutXSs, endXSs, trillTopYSs, color);
        } else {
            var trillXSs = centeredGlyphX(layoutXSs,
                anchor, 0, TRILL_ADVANCE_WIDTH_SS);
            renderTrill(g2, trillXSs, Double.NaN, trillTopYSs, color);
        }
    }

    /**
     * Renders a trill glyph plus optional wavy line extension.
     * All coordinates in staff-space units (Graphics2D has scale transform applied).
     *
     * @param g2          Graphics context with scale transform
     * @param xSs         X position of the trill glyph in staff-space units
     * @param endXSs      Right edge of the wavy line extension, or {@code NaN} for single-note trills
     * @param trillTopYSs Y position of the trill top edge in component staff-space coordinates
     * @param color       Color to use for the trill glyph and wavy line
     */
    private void renderTrill(
        Graphics2D g2,
        double xSs,
        double endXSs,
        double trillTopYSs,
        Color color
    ) {
        // SMuFL ornamentTrill glyph origin is at the baseline (bottom of glyph).
        // trillTopYSs is the top, so offset down by the glyph height.
        var bbox = SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ORNAMENT_TRILL);
        var y = trillTopYSs + bbox.height();

        g2.setColor(color);
        drawBravuraGlyph(g2, SMuFLGlyph.ORNAMENT_TRILL, xSs, y, true);

        // Draw wavy line extension for multi-note trills
        if (!Double.isNaN(endXSs)) {
            var wavyStartX = xSs + TRILL_ADVANCE_WIDTH_SS;
            var wavyEndX = endXSs;
            drawWavyLine(g2, wavyStartX, y, wavyEndX, color);
        }
    }

    /**
     * Renders all trills for a line using layout results.
     * <p>
     * Iterates all {@link Trill} entries in the layout (both new range elements
     * and those bridged from legacy {@code isTrill()} flags during layout).
     */
    public void renderTrillsFromLine(
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        for (var entry : layoutResult.getDecorationLayoutsByType(Trill.class)) {
            var trill = entry.getKey();
            var layout = entry.getValue();
            var anchor = trill.getAnchorElement();

            if (anchor == null) {
                continue;
            }

            var color = getDecorationColor(anchor, ctx);
            var layoutXSs = layout.xSs();
            var trillTopYSs = layoutYToComponentYSs(layout.ySs(), ctx);

            renderTrillAtPosition(
                g2, anchor, trill.getEndElement(),
                layoutXSs, trillTopYSs, color, layoutResult);
        }
    }

    /**
     * Draws a wavy trill extension line using tiled WIGGLE_TRILL glyphs.
     * All coordinates in staff-space units.
     */
    private void drawWavyLine(
        Graphics2D g2,
        double x1,
        double y,
        double x2,
        Color color
    ) {
        var length = x2 - x1;

        if (length <= 0) {
            return;
        }

        var segments = Math.max(1, (int) Math.round(length / WIGGLE_SEGMENT_WIDTH_SS));

        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT, COLOR)) {
            g2.setFont(MUSIC_FONT);
            g2.setColor(color);
            g2.translate(x1, y);

            var scale = length / WIGGLE_SEGMENT_WIDTH_SS / segments;
            g2.scale(scale, 1d);

            for (var i = 0; i < segments; i++) {
                g2.drawString(
                    SMuFLGlyph.WIGGLE_TRILL.asString(),
                    (float) (i * WIGGLE_SEGMENT_WIDTH_SS),
                    0f
                );
            }
        }
    }
}
