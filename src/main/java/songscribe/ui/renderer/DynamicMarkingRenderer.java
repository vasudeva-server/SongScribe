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

import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.stacking.NoteAttachedStacker;

/**
 * Renders point dynamic markings (pp, p, mp, mf, f, ff) below the staff.
 * <p>
 * Each dynamic is drawn as a single SMuFL glyph from the Bravura font,
 * centered over the notehead. This renderer is separate from
 * {@link DynamicsRenderer}, which handles hairpin (crescendo/diminuendo) lines.
 */
public final class DynamicMarkingRenderer extends BaseElementRenderer<StaffElement> {

    // Singleton instance
    private static final DynamicMarkingRenderer INSTANCE = new DynamicMarkingRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private DynamicMarkingRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static DynamicMarkingRenderer getInstance() {
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
        var attachment = element.findAttachment(DynamicAttachment.class);

        if (attachment == null) {
            return;
        }

        var dynamicType = attachment.getType();
        var glyph = dynamicType.getGlyph();

        if (glyph == null) {
            return;
        }

        LayoutResult layoutResult;

        if (ctx.hasOverrideElementX()) {
            // Insertion note preview: compute layouts using the same stacking logic.
            layoutResult = NoteAttachedStacker.computePreviewDecorationLayouts(
                element, ctx.getOverrideElementXSs());
        } else {
            layoutResult = ctx.getLayoutResult();
        }

        var decorationLayout = layoutResult.findAttachmentDecorationLayout(
            element, DynamicAttachment.class);

        if (decorationLayout == null) {
            return;
        }

        var dynamicTopYSs = layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        var dynamicBBox = SMuFLMetadata.requireBBox(glyph);
        // Layout xSs is already centered over the notehead by the stacking calculator
        var x = decorationLayout.xSs() - dynamicBBox.left();
        var y = glyphOriginYFromLayoutTop(dynamicTopYSs, glyph);

        applyDecorationColor(g2, element, ctx);
        drawBravuraGlyph(g2, glyph, x, y, true);
    }

}
