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

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.Trill;
import songscribe.util.GraphicUtils;

/**
 * Renders trill markings (tr symbol + wavy line for extended trills).
 */
public class TrillRenderer extends BaseElementRenderer<Trill> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Trill glyph advance width in pixels, used to position the wavy line start
    private static final double TRILL_ADVANCE_WIDTH_PX;

    // Wavy line segment width from SMuFL repeatOffset (0.948 ss)
    private static final double WIGGLE_SEGMENT_WIDTH_PX = StaffSpaces.toPixels(0.948);

    // Crotchet width
    private static final double CROTCHET_WIDTH_PX = BaseElementRenderer.FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final TrillRenderer INSTANCE = new TrillRenderer();

    static {
        var metadata = SMuFLMetadata.getInstance();
        var advanceWidth = metadata.getAdvanceWidth(SMuFLGlyph.ORNAMENT_TRILL);
        TRILL_ADVANCE_WIDTH_PX = (advanceWidth != null) ? StaffSpaces.toPixels(advanceWidth) : 17.0;
    }

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TrillRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull TrillRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Trill element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorElement();

        if (anchorNote == null) {
            return;
        }

        var endNote = element.getEndElement();
        int trillYPosSs = getEffectiveTrillYPosSs(element, ctx);

        renderTrill(g2, ctx, anchorNote, endNote, trillYPosSs);
    }

    /**
     * Gets the Y position for a trill from layout result.
     */
    private int getEffectiveTrillYPosSs(
        @NotNull Trill element,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.getBounds(element);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for Trill element");
        }

        return (int) (bounds.getTop() - ctx.getMiddleLineYSs());
    }

    /**
     * Renders a trill at a note.
     */
    public void renderTrill(
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx,
        @NotNull StaffElement startNote,
        StaffElement endNote,
        int trillYPosSs
    ) {
        double middleLineYSs = ctx.getMiddleLineYSs();

        double x = GraphicUtils.snapXToDevicePixel(g2, startNote.getXPosSs());
        int y = (int) (middleLineYSs + trillYPosSs);

        drawBravuraGlyph(g2, SMuFLGlyph.ORNAMENT_TRILL, x, y);

        // Draw wavy line extension if there's an end note
        if (endNote != null && endNote != startNote) {
            double wavyStartX = GraphicUtils.snapXToDevicePixel(
                g2, x + TRILL_ADVANCE_WIDTH_PX
            );
            double endX = GraphicUtils.snapXToDevicePixel(
                g2, endNote.getXPosSs() + CROTCHET_WIDTH_PX
            );
            drawWavyLine(g2, wavyStartX, y, endX);
        }
    }

    /**
     * Renders trills from a Line, checking for consecutive trill notes.
     */
    public void renderTrillsFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        int trillYPosPx = line.getTrillYPosPx();

        for (int elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);

            if (!element.isTrill()) {
                continue;
            }

            // Only render if this is the start of a trill sequence
            if (elementIndex > 0 && line.getElement(elementIndex - 1).isTrill()) {
                continue;
            }

            // Find the end of the trill sequence
            int trillEnd = elementIndex + 1;

            while (trillEnd < line.elementCount() && line.getElement(trillEnd).isTrill()) {
                trillEnd++;
            }

            trillEnd--;

            StaffElement endElement = (trillEnd > elementIndex) ? line.getElement(trillEnd) : null;
            renderTrill(g2, ctx, element, endElement, trillYPosPx);
        }
    }

    /**
     * Draws a wavy trill extension line using tiled WIGGLE_TRILL glyphs.
     */
    private void drawWavyLine(
        @NotNull Graphics2D g2,
        double x1,
        int y,
        double x2
    ) {
        double length = x2 - x1;

        if (length <= 0) {
            return;
        }

        int segments = Math.max(1, (int) Math.round(length / WIGGLE_SEGMENT_WIDTH_PX));

        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT, COLOR)) {
            g2.setFont(BRAVURA_FONT);
            g2.setColor(ELEMENT_COLOR);
            g2.translate(x1, y);

            double scale = length / WIGGLE_SEGMENT_WIDTH_PX / segments;
            g2.scale(scale, 1d);

            for (int i = 0; i < segments; i++) {
                g2.drawString(
                    SMuFLGlyph.WIGGLE_TRILL.asString(),
                    (float) (i * WIGGLE_SEGMENT_WIDTH_PX),
                    0f
                );
            }
        }
    }
}
