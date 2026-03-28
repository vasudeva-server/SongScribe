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

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.Trill;
import songscribe.util.GraphicUtils;

/**
 * Renders trill markings (tr symbol + wavy line for extended trills).
 */
public class TrillRenderer extends BaseElementRenderer<Trill> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Trill glyph advance width in staff-space units, used to position the wavy line start
    private static final double TRILL_ADVANCE_WIDTH_SS;

    // Wavy line segment width from SMuFL repeatOffset (0.948 ss)
    private static final double WIGGLE_SEGMENT_WIDTH_SS = 0.948;

    // Default fallback advance width in staff-space units (~2.125 ss)
    private static final double DEFAULT_TRILL_ADVANCE_WIDTH_SS = 2.125;

    // Notehead center X for horizontal alignment
    private static final double NOTE_HEAD_WIDTH_SS;

    // Singleton instance
    private static final TrillRenderer INSTANCE = new TrillRenderer();

    static {
        var metadata = SMuFLMetadata.getInstance();
        var advanceWidth = metadata.getAdvanceWidth(SMuFLGlyph.ORNAMENT_TRILL);
        TRILL_ADVANCE_WIDTH_SS = (advanceWidth != null) ? advanceWidth : DEFAULT_TRILL_ADVANCE_WIDTH_SS;
        NOTE_HEAD_WIDTH_SS = metadata.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();
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

        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            return;
        }

        var decorationLayout = layoutResult.getDecorationLayout(element);

        if (decorationLayout == null) {
            return;
        }

        double trillTopYSs = layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        double layoutXSs = decorationLayout.xSs();

        var endNote = element.getEndElement();

        if (endNote != null && endNote != anchorNote) {
            double trillXSs = GraphicUtils.snapXToDevicePixel(g2, layoutXSs);
            double endXSs = layoutResult.getElementXSs(endNote) + NOTE_HEAD_WIDTH_SS;
            renderTrill(g2, trillXSs, endXSs, trillTopYSs);
        } else {
            double trillXSs = centeredGlyphX(g2, layoutXSs,
                anchorNote, TRILL_ADVANCE_WIDTH_SS);
            renderTrill(g2, trillXSs, Double.NaN, trillTopYSs);
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
     */
    private void renderTrill(
        Graphics2D g2,
        double xSs,
        double endXSs,
        double trillTopYSs
    ) {
        // SMuFL ornamentTrill glyph origin is at the baseline (bottom of glyph).
        // trillTopYSs is the top, so offset down by the glyph height.
        var bbox = SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ORNAMENT_TRILL);
        double y = trillTopYSs + bbox.height();

        drawBravuraGlyph(g2, SMuFLGlyph.ORNAMENT_TRILL, xSs, y, true);

        // Draw wavy line extension for multi-note trills
        if (!Double.isNaN(endXSs)) {
            double wavyStartX = GraphicUtils.snapXToDevicePixel(
                g2, xSs + TRILL_ADVANCE_WIDTH_SS
            );
            double wavyEndX = GraphicUtils.snapXToDevicePixel(g2, endXSs);
            drawWavyLine(g2, wavyStartX, y, wavyEndX);
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

        if (layoutResult == null) {
            return;
        }

        for (var entry : layoutResult.getDecorationLayoutsByType(Trill.class)) {
            var trill = entry.getKey();
            var layout = entry.getValue();
            var anchor = trill.getAnchorElement();

            if (anchor == null) {
                continue;
            }

            double layoutXSs = layout.xSs();
            double trillTopYSs = layoutYToComponentYSs(layout.ySs(), ctx);
            var endNote = trill.getEndElement();

            if (endNote != null && endNote != anchor) {
                double trillXSs = GraphicUtils.snapXToDevicePixel(g2, layoutXSs);
                double endXSs = layoutResult.getElementXSs(endNote) + NOTE_HEAD_WIDTH_SS;
                renderTrill(g2, trillXSs, endXSs, trillTopYSs);
            } else {
                double trillXSs = centeredGlyphX(g2, layoutXSs,
                    anchor, TRILL_ADVANCE_WIDTH_SS);
                renderTrill(g2, trillXSs, Double.NaN, trillTopYSs);
            }
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
        double x2
    ) {
        double length = x2 - x1;

        if (length <= 0) {
            return;
        }

        int segments = Math.max(1, (int) Math.round(length / WIGGLE_SEGMENT_WIDTH_SS));

        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT, COLOR)) {
            g2.setFont(MUSIC_FONT);
            g2.setColor(ELEMENT_COLOR);
            g2.translate(x1, y);

            double scale = length / WIGGLE_SEGMENT_WIDTH_SS / segments;
            g2.scale(scale, 1d);

            for (int i = 0; i < segments; i++) {
                g2.drawString(
                    SMuFLGlyph.WIGGLE_TRILL.asString(),
                    (float) (i * WIGGLE_SEGMENT_WIDTH_SS),
                    0f
                );
            }
        }
    }
}
