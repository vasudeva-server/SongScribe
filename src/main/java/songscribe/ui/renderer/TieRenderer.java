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

import java.awt.*;
import java.awt.geom.*;

import org.jetbrains.annotations.NotNull;

import songscribe.data.Interval;
import songscribe.ui.layout2.LayoutResult;

/**
 * Renders tie arcs between two notes of the same pitch.
 * <p>
 * Reads pre-computed cubic Bezier geometry from {@link LayoutResult.TieLayout}
 * and draws a filled lens shape using an outer and inner cubic Bezier curve.
 */
public class TieRenderer {

    private static final Color NOTE_COLOR = Color.BLACK;

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
    public static @NotNull TieRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders a tie for the given interval.
     * <p>
     * Reads pre-computed geometry from {@link LayoutResult.TieLayout}. If no
     * layout was computed for this interval, the method returns without rendering.
     *
     * @param g2       Graphics context (staff-space coordinate system)
     * @param interval The tie interval
     * @param ctx      Render context
     */
    public void renderTie(
        @NotNull Graphics2D g2,
        @NotNull Interval interval,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            return;
        }

        var layoutOpt = layoutResult.getTieLayout(interval);

        if (layoutOpt.isEmpty()) {
            return;
        }

        var layout = layoutOpt.get();

        try (var ignored = GraphicsState.save(g2, TRANSFORM, COLOR)) {
            g2.translate(0, ctx.getMiddleLineYSs());
            g2.setColor(NOTE_COLOR);

            var tie = new GeneralPath(Path2D.WIND_NON_ZERO, 4);

            // Outer cubic Bezier: start → end
            tie.moveTo(layout.startXSs(), layout.startYSs());
            tie.curveTo(
                layout.cp1XSs(), layout.cp1YSs(),
                layout.cp2XSs(), layout.cp2YSs(),
                layout.endXSs(), layout.endYSs()
            );

            // Inner cubic Bezier (reversed): end → start, forming the lens shape.
            // Both curves share start/end points, creating natural tapering.
            tie.curveTo(
                layout.innerCp2XSs(), layout.innerCp2YSs(),
                layout.innerCp1XSs(), layout.innerCp1YSs(),
                layout.startXSs(), layout.startYSs()
            );

            tie.closePath();
            g2.fill(tie);
        }
    }
}
