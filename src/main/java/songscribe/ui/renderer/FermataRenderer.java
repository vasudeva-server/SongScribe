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

import org.jetbrains.annotations.NotNull;

import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.ScaleContext;
import songscribe.util.GraphicUtils;

/**
 * Renders fermata symbols above or below notes.
 * <p>
 * A fermata indicates that a note should be held longer than its written duration.
 * The symbol consists of a dot under an arc (like an eyebrow over an eye).
 */
public class FermataRenderer extends BaseElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // SMuFL bbox-derived fermata dimensions (in pixels) for positioning.
    private static final double FERMATA_WIDTH_PX;
    private static final double FERMATA_HEIGHT_PX;

    static {
        var bbox = SMuFLMetadata.getInstance().getBBox(SMuFLGlyph.FERMATA_ABOVE);
        FERMATA_WIDTH_PX = StaffSpaces.toPixels(bbox.width());
        FERMATA_HEIGHT_PX = StaffSpaces.toPixels(bbox.height());
    }

    // Singleton instance
    private static final FermataRenderer INSTANCE = new FermataRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private FermataRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull FermataRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull StaffElement element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        if (!element.isFermata()) {
            return;
        }

        int noteX = element.getXPosSs();
        int fermataY = getEffectiveFermataYPosPx(element, ctx);

        // Center horizontally over the notehead
        double noteHeadHalfWidth = BaseElementRenderer.FONT_SIZE / 3.6056337d / 2.0;
        double x = GraphicUtils.snapXToDevicePixel(
            g2, noteX + noteHeadHalfWidth - FERMATA_WIDTH_PX / 2.0
        );

        // SMuFL fermata glyph origin is at the baseline (bottom of glyph).
        // fermataY is the top of the fermata, so offset down by the full height.
        double y = fermataY + FERMATA_HEIGHT_PX;

        drawBravuraGlyph(g2, SMuFLGlyph.FERMATA_ABOVE, x, y);
    }

    /**
     * Gets the Y position for a fermata from layout result,
     * falling back to a computed position when no layout is available
     * (e.g. for the insertion note preview).
     */
    private int getEffectiveFermataYPosPx(
        @NotNull StaffElement note,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult != null) {
            var bounds = layoutResult.findAttachmentBounds(note, FermataAttachment.class);

            if (bounds != null) {
                return (int) bounds.getTop();
            }
        }

        // Fallback: compute position directly from note position
        int fermataStaffPosition = getFermataStaffPosition(note);
        return (int) (ctx.getMiddleLineYSs() + ScaleContext.getInstance().toRoundedPixels(fermataStaffPosition * LayoutStylesheet.STAFF_POSITION_OFFSET_SS));
    }

    /**
     * Renders a fermata for a note if it has one.
     *
     * @param g2          Graphics context
     * @param note        The note to check
     * @param ctx         Render context
     */
    public void renderFermata(
        @NotNull Graphics2D g2,
        @NotNull StaffElement note,
        @NotNull ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Calculates the Y position for the fermata based on note position.
     * Fermata is placed above the note, further up for higher notes.
     */
    private int getFermataStaffPosition(@NotNull StaffElement note) {
        int staffPosition = note.getStaffPosition();

        // For notes above the staff, place fermata higher
        if (staffPosition < -4) {
            return staffPosition - 3;
        }

        // Default position above the staff
        return -7;
    }
}
