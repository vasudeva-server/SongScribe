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

import module java.desktop;



import songscribe.music.ArticulationType;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * Renders articulation markings on notes (staccato, accent).
 * <p>
 * Articulations modify how a note is played:
 * <ul>
 *   <li>Staccato - short, detached (dot above/below note)</li>
 *   <li>Accent - emphasized attack (> symbol)</li>
 * </ul>
 */
public class ArticulationRenderer extends BaseElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // SMuFL bbox-derived dimensions (in pixels) for accent and staccato glyphs.
    // Used by calculateAccentY/calculateStaccatoY for vertical positioning.
    private static final int ACCENT_HALF_HEIGHT_PX;
    private static final double ACCENT_WIDTH_PX;
    private static final int STACCATO_HALF_HEIGHT_PX;
    private static final double STACCATO_WIDTH_PX;

    static {
        var metadata = SMuFLMetadata.getInstance();

        var accentBBox = metadata.requireBBox(SMuFLGlyph.ARTIC_ACCENT_ABOVE);
        ACCENT_HALF_HEIGHT_PX = (int) Math.round(StaffSpaces.toPixels(accentBBox.height()) / 2.0);
        ACCENT_WIDTH_PX = StaffSpaces.toPixels(accentBBox.width());

        var staccatoBBox = metadata.requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE);
        STACCATO_HALF_HEIGHT_PX = (int) Math.round(StaffSpaces.toPixels(staccatoBBox.height()) / 2.0);
        STACCATO_WIDTH_PX = StaffSpaces.toPixels(staccatoBBox.width());
    }

    // Singleton instance
    private static final ArticulationRenderer INSTANCE = new ArticulationRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private ArticulationRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static ArticulationRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        if (element.getArticulations().isEmpty()) {
            return;
        }

        var layoutResult = ctx.getLayoutResult();

        if (layoutResult != null) {
            renderFromLayout(element, g2, ctx, layoutResult);
        } else {
            renderFallback(element, g2, ctx);
        }
    }

    /**
     * Renders articulations using pre-computed positions from the layout engine.
     */
    private void renderFromLayout(
        StaffElement note,
        Graphics2D g2,
        ElementRenderContext ctx,
        LayoutResult layoutResult
    ) {
        for (var articulation : note.getArticulations()) {
            var bounds = layoutResult.getElementBounds(articulation);

            if (bounds == null) {
                continue;
            }

            int componentY = (int) layoutYToComponentYSs(bounds, ctx);

            if (articulation.isStaccato()) {
                drawStaccatoFromLayout(note, g2, componentY);
            } else if (articulation.isAccent()) {
                int accentCenterY = componentY + ACCENT_HALF_HEIGHT_PX;
                drawAccent(note, g2, accentCenterY);
            }
        }
    }

    /**
     * Renders a staccato dot at a layout-computed Y position.
     * The Y is the top of the content box from the layout engine.
     */
    private void drawStaccatoFromLayout(
        StaffElement note,
        Graphics2D g2,
        int contentTopY
    ) {
        double halfNoteWidth = getHalfNoteWidthForTiePx(note);
        double x = GraphicUtils.snapXToDevicePixel(
            g2, note.getXPosSs() + halfNoteWidth - STACCATO_WIDTH_PX / 2.0
        );

        // SMuFL "above" glyph origin is at the bottom; offset by glyph height
        double y = contentTopY + StaffSpaces.toPixels(
            SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE).height()
        );

        drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_STACCATO_ABOVE, x, y);
    }

    /**
     * Fallback rendering for when no layout result is available
     * (e.g. insertion note preview).
     */
    private void renderFallback(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var hasAccent = element.hasArticulation(ArticulationType.ACCENT);
        var hasStaccato = element.hasArticulation(ArticulationType.STACCATO);

        if (!hasAccent && !hasStaccato) {
            return;
        }

        double middleLineYSs = ctx.getMiddleLineYSs();
        int staccatoY = hasStaccato ? calculateStaccatoYPx(element, (int) middleLineYSs) : 0;

        if (hasAccent) {
            int accentY = calculateAccentYPx(element, (int) middleLineYSs, staccatoY, hasStaccato);
            drawAccent(element, g2, accentY);
        }

        if (hasStaccato) {
            renderStaccato(element, g2, (int) middleLineYSs);
        }
    }

    /**
     * Calculates the Y center for an accent marking.
     * <p>
     * For notes within the staff, the accent anchors to the staff edge.
     * For ledger-line notes, it anchors to the note head.
     * When a staccato is present, the accent stacks beyond it with a 1px gap.
     * <p>
     * Pass {@code middleLineYPx=0} for layout-space coordinates,
     * or the actual middleLineYPx for component-space coordinates.
     */
    public static int calculateAccentYPx(
        StaffElement note, int middleLineYPx, int staccatoY, boolean hasStaccato
    ) {
        int dir = note.isUpper() ? 1 : -1;
        int staffPosition = note.getStaffPosition();
        int margin = ScaleContext.getInstance().toRoundedPixels(0.375);

        // If staccato is present, stack accent beyond it with 1px gap
        if (hasStaccato) {
            return staccatoY + dir * (STACCATO_HALF_HEIGHT_PX + 1 + ACCENT_HALF_HEIGHT_PX);
        }

        // Accent alone.
        // Offset = accent visual half-height + margin so the painted edge clears the reference.
        if (Math.abs(staffPosition) < 4) {
            // Within staff (not on edge lines) -- anchor to staff edge
            int staffEdgeY = middleLineYPx + ScaleContext.getInstance().toRoundedPixels(dir * 4 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);

            return staffEdgeY + dir * (ACCENT_HALF_HEIGHT_PX + margin);
        } else {
            // On staff edge or beyond (ledger lines) -- anchor to note head
            int noteHeadY = middleLineYPx + ScaleContext.getInstance().toRoundedPixels(staffPosition * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
            int noteHeadRadius = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.STAFF_POSITION_OFFSET_SS);

            return noteHeadY + dir * (noteHeadRadius + margin + ACCENT_HALF_HEIGHT_PX);
        }
    }

    /**
     * Draws an accent glyph centered vertically at the given Y position.
     */
    private void drawAccent(StaffElement note, Graphics2D g2, int accentY) {
        double halfNoteWidth = getHalfNoteWidthForTiePx(note);
        double x = GraphicUtils.snapXToDevicePixel(
            g2, note.getXPosSs() + halfNoteWidth - ACCENT_WIDTH_PX / 2.0
        );

        // SMuFL "above" glyph: origin is at the baseline (bottom of glyph).
        // accentY is the vertical center, so offset down by half-height to get baseline.
        double y = accentY + ACCENT_HALF_HEIGHT_PX;

        drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_ACCENT_ABOVE, x, y);
    }

    /**
     * Calculates the Y center of a staccato dot for a note.
     * <p>
     * Normal stems: notes near the middle get a fixed staff space;
     * notes approaching the edge use the standard margin from the staff edge;
     * ledger-line notes use the standard margin from the note head.
     * <p>
     * Inverted stems: the staccato is always placed in a fixed staff space,
     * positioned toward the center of the staff.
     * <p>
     * Pass {@code middleLineYPx=0} for layout-space coordinates,
     * or the actual middleLineYPx for component-space coordinates.
     */
    public static int calculateStaccatoYPx(StaffElement note, int middleLineYPx) {
        boolean isUpper = note.isUpper();
        int dir = isUpper ? 1 : -1;
        int staffPosition = note.getStaffPosition();
        boolean normalStem = (staffPosition > 0) == isUpper;

        if (normalStem) {
            int margin = ScaleContext.getInstance().toRoundedPixels(0.375);

            return switch (Math.abs(staffPosition)) {
                // B4/C5 or A4: fixed space near opposite staff edge
                case 0, 1 ->
                    middleLineYPx + ScaleContext.getInstance().toRoundedPixels(dir * 3 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);

                // D5/E5 or G4/F4: standard margin from staff edge
                case 2, 3 -> {
                    int staffEdgeY = middleLineYPx + ScaleContext.getInstance().toRoundedPixels(dir * 4 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
                    yield staffEdgeY + dir * (STACCATO_HALF_HEIGHT_PX + margin);
                }

                // F5+ or E4+: standard margin from note head
                default -> {
                    int noteHeadY = middleLineYPx + ScaleContext.getInstance().toRoundedPixels(staffPosition * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
                    int noteHeadRadius = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
                    yield noteHeadY + dir * (noteHeadRadius + margin + STACCATO_HALF_HEIGHT_PX);
                }
            };
        }

        // Inverted stem: staccato always in a fixed staff space, toward center
        int targetStaffPosition = switch (staffPosition) {
            case 0 -> 3;           // B4 inverted → F4 space
            case -1, -2 -> 1;     // C5/D5 inverted → A4 space
            case -3, -4 -> -1;    // E5/F5 inverted → C5 space
            case 1, 2 -> -1;      // A4/G4 inverted → C5 space
            case 3, 4 -> 1;       // F4/E4 inverted → A4 space
            default -> staffPosition < 0 ? -3 : 3;  // beyond staff → E5 or F4 space
        };

        return middleLineYPx + ScaleContext.getInstance().toRoundedPixels(targetStaffPosition * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
    }

    /**
     * Renders a staccato glyph centered vertically at the computed Y position.
     */
    private void renderStaccato(
        StaffElement note,
        Graphics2D g2,
        int middleLineYPx
    ) {
        int centerY = calculateStaccatoYPx(note, middleLineYPx);
        double halfNoteWidth = getHalfNoteWidthForTiePx(note);
        double x = GraphicUtils.snapXToDevicePixel(
            g2, note.getXPosSs() + halfNoteWidth - STACCATO_WIDTH_PX / 2.0
        );

        // SMuFL "above" glyph: origin is at the baseline (bottom of glyph).
        // centerY is the vertical center, so offset down by half-height to get baseline.
        double y = centerY + STACCATO_HALF_HEIGHT_PX;

        drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_STACCATO_ABOVE, x, y);
    }

    /**
     * Returns half the width of a note for positioning.
     */
    private double getHalfNoteWidthForTiePx(StaffElement note) {
        return note.getContentCenterX();
    }
}
