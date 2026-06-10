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

import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.dom.FermataAttachment;
import songscribe.layout.LayoutResult;
import songscribe.layout.stacking.NoteAttachedStacker;
import songscribe.util.GraphicsState;

/**
 * Renders fermata symbols above or below notes.
 * <p>
 * A fermata indicates that a note should be held longer than its written duration.
 * The symbol consists of a dot under an arc (like an eyebrow over an eye).
 */
public final class FermataRenderer implements ElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // SMuFL bbox-derived fermata width in staff-space units
    private static final double FERMATA_BBOX_LEFT_SS =
        SMuFLMetadata.requireBBox(SMuFLGlyph.FERMATA_ABOVE).left();

    private static final double FERMATA_WIDTH_SS =
        SMuFLMetadata.requireBBox(SMuFLGlyph.FERMATA_ABOVE).width();

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
    public static FermataRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        // Guard: only render if a fermata attachment is present
        if (element.findAttachment(FermataAttachment.class) == null) {
            return;
        }

        LayoutResult layoutResult;

        if (frame.hasOverrideElementX()) {
            // Insertion note preview: compute layouts using the same stacking logic.
            layoutResult = NoteAttachedStacker.computePreviewDecorationLayouts(
                element, frame.overrideElementXSs());
        } else {
            layoutResult = invariants.getLayoutResult();
        }

        var decorationLayout = layoutResult.findAttachmentDecorationLayout(
            element, FermataAttachment.class);

        if (decorationLayout == null) {
            return;
        }

        var fermataTopYSs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants);

        var x = RenderingUtils.centeredGlyphX(decorationLayout.xSs(), element,
            FERMATA_BBOX_LEFT_SS, FERMATA_WIDTH_SS);

        var y = RenderingUtils.glyphOriginYFromLayoutTop(fermataTopYSs, SMuFLGlyph.FERMATA_ABOVE);

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            RenderingUtils.applyDecorationColor(g2, element, invariants, frame);
            RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.FERMATA_ABOVE, x, y, true);
        }
    }

    /**
     * Renders a fermata for a note if it has one.
     *
     * @param g2    Graphics context
     * @param note  The note to check
     * @param invariants   Line invariants
     * @param frame Per-element frame
     */
    public void renderFermata(
        Graphics2D g2,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        render(invariants, frame, note, g2);
    }
}
