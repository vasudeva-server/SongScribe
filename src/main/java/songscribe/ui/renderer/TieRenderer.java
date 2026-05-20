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
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import module java.desktop;


import songscribe.layout.LayoutResult;
import songscribe.dom.Tie;

/**
 * Renders tie arcs between two notes of the same pitch.
 * <p>
 * Reads pre-computed cubic Bezier geometry from {@link LayoutResult.TieLayout}
 * and draws a filled lens shape using an outer and inner cubic Bezier curve.
 */
public final class TieRenderer {

    // Singleton instance
    private static final TieRenderer INSTANCE = new TieRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TieRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static TieRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders a tie for the given {@link Tie} range element.
     * <p>
     * Reads pre-computed geometry from {@link LayoutResult.TieLayout}. If no
     * layout was computed for this tie, the method returns without rendering.
     *
     * @param g2  Graphics context (staff-space coordinate system)
     * @param tie The tie range element
     * @param ctx Render context
     */
    public void renderTie(
        Graphics2D g2,
        Tie tie,
        ElementRenderContext ctx
    ) {
        var layout = ctx.getLayoutResult().getTieLayout(tie);

        if (layout == null) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, TRANSFORM, COLOR)) {
            g2.translate(0, ctx.getMiddleLineYSs());
            g2.setColor(determineTieColor(tie, ctx));

            var tiePath = new GeneralPath(Path2D.WIND_NON_ZERO, 4);

            // Outer cubic Bezier: start → end
            tiePath.moveTo(layout.startXSs(), layout.startYSs());
            tiePath.curveTo(
                layout.cp1XSs(), layout.cp1YSs(),
                layout.cp2XSs(), layout.cp2YSs(),
                layout.endXSs(), layout.endYSs()
            );

            // Inner cubic Bezier (reversed): end → start, forming the lens shape.
            // Both curves share start/end points, creating natural tapering.
            tiePath.curveTo(
                layout.innerCp2XSs(), layout.innerCp2YSs(),
                layout.innerCp1XSs(), layout.innerCp1YSs(),
                layout.startXSs(), layout.startYSs()
            );

            tiePath.closePath();
            g2.fill(tiePath);
        }
    }

    /**
     * Determines the tie color by checking both endpoints.
     * <p>
     * A tie is colored if either its start or end note is playing or selected.
     */
    private Color determineTieColor(Tie tie, ElementRenderContext ctx) {
        var startColor = ctx.getElementColor(tie.getAnchorElementIndex());

        if (startColor != Color.BLACK) {
            return startColor;
        }

        var endColor = ctx.getElementColor(tie.getEndElementIndex());

        if (endColor != Color.BLACK) {
            return endColor;
        }

        return BaseElementRenderer.ELEMENT_COLOR;
    }
}
