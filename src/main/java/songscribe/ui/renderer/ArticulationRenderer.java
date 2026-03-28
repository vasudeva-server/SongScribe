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
import songscribe.ui.layout.VerticalStackingCalculator;
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

    // SMuFL bbox-derived widths in staff-space units
    private static final double ACCENT_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_ABOVE).width();

    private static final double STACCATO_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE).width();

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

        if (layoutResult == null) {
            // Insertion note preview: compute layouts using the same stacking logic.
            // Use the override X for precise positioning, falling back to xPosSs.
            double xSs = ctx.hasOverrideElementX()
                ? ctx.getOverrideElementXSs() : element.getXPosSs();
            layoutResult = VerticalStackingCalculator.computePreviewDecorationLayouts(
                element, xSs);
        }

        for (var articulation : element.getArticulations()) {
            var layout = layoutResult.getDecorationLayout(articulation);

            if (layout == null) {
                continue;
            }

            double componentTopYSs = layoutYToComponentYSs(layout.ySs(), ctx);

            if (articulation.isStaccato()) {
                double x = centeredGlyphX(g2, layout.xSs(), element, STACCATO_WIDTH_SS);
                double y = glyphOriginYFromLayoutTop(componentTopYSs, SMuFLGlyph.ARTIC_STACCATO_ABOVE);
                drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_STACCATO_ABOVE, x, y, true);
            } else if (articulation.isAccent()) {
                double x = centeredGlyphX(g2, layout.xSs(), element, ACCENT_WIDTH_SS);
                double y = glyphOriginYFromLayoutTop(componentTopYSs, SMuFLGlyph.ARTIC_ACCENT_ABOVE);
                drawBravuraGlyph(g2, SMuFLGlyph.ARTIC_ACCENT_ABOVE, x, y, true);
            }
        }
    }
}
