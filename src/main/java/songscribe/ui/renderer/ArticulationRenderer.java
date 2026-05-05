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
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.stacking.NoteAttachedStacker;

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

    // SMuFL bbox-derived widths in staff-space units
    private static final double ACCENT_BBOX_LEFT_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_ABOVE).left();

    private static final double ACCENT_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_ABOVE).width();

    private static final double STACCATO_BBOX_LEFT_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE).left();

    private static final double STACCATO_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE).width();

    private static final double ACCENT_STACCATO_BBOX_LEFT_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE).left();

    private static final double ACCENT_STACCATO_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE).width();

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

        LayoutResult layoutResult;

        if (ctx.hasOverrideElementX()) {
            // Insertion note preview: compute layouts using the same stacking logic.
            layoutResult = NoteAttachedStacker.computePreviewDecorationLayouts(
                element, ctx.getOverrideElementXSs());
        } else {
            layoutResult = ctx.getLayoutResult();
        }

        applyDecorationColor(g2, element, ctx);

        var hasStaccato = false;
        var hasAccent = false;

        for (var a : element.getArticulations()) {
            if (a.isStaccato()) {
                hasStaccato = true;
            } else if (a.isAccent()) {
                hasAccent = true;
            }
        }

        var isCombo = hasStaccato && hasAccent;

        for (var articulation : element.getArticulations()) {
            var layout = layoutResult.getDecorationLayout(articulation);

            if (layout == null) {
                // In combo mode, the accent articulation has no layout entry — skip it.
                continue;
            }

            var componentTopYSs = layoutYToComponentYSs(layout.ySs(), ctx);

            if (isCombo && articulation.isStaccato()) {
                var x = centeredGlyphX(layout.xSs(), element,
                    ACCENT_STACCATO_BBOX_LEFT_SS, ACCENT_STACCATO_WIDTH_SS);
                var y = glyphOriginYFromLayoutTop(componentTopYSs,
                    SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE);
                drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE, x, y, true);
            } else if (articulation.isStaccato()) {
                var x = centeredGlyphX(layout.xSs(), element,
                    STACCATO_BBOX_LEFT_SS, STACCATO_WIDTH_SS);
                var y = glyphOriginYFromLayoutTop(componentTopYSs, SMuFLGlyph.ARTIC_STACCATO_ABOVE);
                drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_STACCATO_ABOVE, x, y, true);
            } else if (articulation.isAccent()) {
                var x = centeredGlyphX(layout.xSs(), element,
                    ACCENT_BBOX_LEFT_SS, ACCENT_WIDTH_SS);
                var y = glyphOriginYFromLayoutTop(componentTopYSs, SMuFLGlyph.ARTIC_ACCENT_ABOVE);
                drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_ACCENT_ABOVE, x, y, true);
            }
        }
    }
}
