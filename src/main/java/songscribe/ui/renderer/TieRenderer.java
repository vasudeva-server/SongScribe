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
import static songscribe.util.GraphicsState.Property.STROKE;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

import module java.desktop;


import songscribe.hit.HitTarget;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.dom.Tie;
import songscribe.shape.BezierBow;
import songscribe.util.GraphicsState;

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
     * Round pen used to outline the filled lens, blunting the sharp cusps where the outer and
     * inner curves meet. Immutable, so a single shared instance suffices across all repaints.
     */
    private static final BasicStroke TIE_OUTLINE_STROKE = new BasicStroke(
        (float) LayoutEngine.TIE_OUTLINE_THICKNESS_SS,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    );

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
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var layout = invariants.getLayoutResult().getTieLayout(tie);

        if (layout == null) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, TRANSFORM, COLOR, STROKE)) {
            g2.translate(0, invariants.getMiddleLineYSs());
            g2.setColor(determineTieColor(tie, invariants));

            var tiePath = BezierBow.lens(
                layout.startXSs(), layout.startYSs(),
                layout.cp1XSs(), layout.cp1YSs(),
                layout.cp2XSs(), layout.cp2YSs(),
                layout.endXSs(), layout.endYSs(),
                layout.innerCp1XSs(), layout.innerCp1YSs(),
                layout.innerCp2XSs(), layout.innerCp2YSs()
            );

            g2.fill(tiePath);

            // Round the tapered ends: LilyPond outlines the bezier sandwich with a round pen of
            // line-thickness, which blunts the otherwise-sharp cusps where the two curves meet.
            g2.setStroke(TIE_OUTLINE_STROKE);
            g2.draw(tiePath);
        }
    }

    /**
     * Determines the tie color by checking both endpoints, then the tie itself.
     * <p>
     * A tie is colored if either its start or end note is playing or selected — the
     * endpoint checks are what carries an index-range selection onto the ties inside it —
     * and, failing that, if the tie is itself the selected target.
     */
    Color determineTieColor(Tie tie, LineInvariants invariants) {
        var startColor = invariants.getElementColor(tie.getAnchorElementIndex());

        if (!LineInvariants.isDefaultColor(startColor)) {
            return startColor;
        }

        var endColor = invariants.getElementColor(tie.getEndElementIndex());

        if (!LineInvariants.isDefaultColor(endColor)) {
            return endColor;
        }

        var tieColor = invariants.colorFor(new HitTarget.Tie(tie), LineInvariants.NO_ELEMENT_INDEX);

        if (!LineInvariants.isDefaultColor(tieColor)) {
            return tieColor;
        }

        return RenderingUtils.ELEMENT_COLOR;
    }
}
