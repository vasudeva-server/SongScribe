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
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import module java.desktop;


import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.LineElement;

/**
 * Renders crescendo and diminuendo hairpins.
 * <p>
 * Crescendo: opens from left to right (gets louder)
 * Diminuendo: opens from right to left (gets softer)
 */
public class DynamicsRenderer extends BaseElementRenderer<LineElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Singleton instance
    private static final DynamicsRenderer INSTANCE = new DynamicsRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private DynamicsRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static DynamicsRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        LineElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var layout = ctx.getLayoutResult().getDecorationLayout(element);

        if (layout == null) {
            return;
        }

        renderSingleHairpin(layout, element instanceof Crescendo, g2, ctx);
    }

    /**
     * Renders a single hairpin from its decoration layout.
     *
     * @param layout      The pre-computed layout (with offsets already applied)
     * @param isCrescendo True for crescendo, false for diminuendo
     * @param g2          Graphics context with scale transform
     * @param ctx         Render context
     */
    private void renderSingleHairpin(
        songscribe.ui.layout.LayoutResult.DecorationLayout layout,
        boolean isCrescendo,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        double x1 = layout.xSs();
        double x2 = x1 + layout.widthSs();
        double topYSs = layoutYToComponentYSs(layout.ySs(), ctx);
        double bottomYSs = topYSs + layout.heightSs();
        double middleYSs = topYSs + layout.heightSs() / 2.0;

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE)) {
            g2.setColor(ELEMENT_COLOR);
            g2.setStroke(new BasicStroke(
                (float) ctx.getLineThickness().hairpinSs(),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            ));

            if (isCrescendo) {
                g2.draw(new java.awt.geom.Line2D.Double(x1, middleYSs, x2, topYSs));
                g2.draw(new java.awt.geom.Line2D.Double(x1, middleYSs, x2, bottomYSs));
            } else {
                g2.draw(new java.awt.geom.Line2D.Double(x1, topYSs, x2, middleYSs));
                g2.draw(new java.awt.geom.Line2D.Double(x1, bottomYSs, x2, middleYSs));
            }
        }
    }

    /**
     * Renders all hairpins (crescendo and diminuendo) for a line using layout results.
     * <p>
     * Iterates all {@link Crescendo} and {@link Diminuendo} entries in the layout
     * (both new range elements and those bridged from legacy intervals during layout).
     */
    public void renderHairpinsFromLine(
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        for (var entry : layoutResult.getDecorationLayoutsByType(Crescendo.class)) {
            renderSingleHairpin(entry.getValue(), true, g2, ctx);
        }

        for (var entry : layoutResult.getDecorationLayoutsByType(Diminuendo.class)) {
            renderSingleHairpin(entry.getValue(), false, g2, ctx);
        }
    }
}
