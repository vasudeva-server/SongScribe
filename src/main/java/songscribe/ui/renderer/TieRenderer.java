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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Tie;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.shape.BezierBow;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.STROKE;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

/**
 * Renders tie arcs between two notes of the same pitch.
 * <p>
 * Reads pre-computed cubic Bezier geometry from {@link LayoutResult.TieLayout}
 * and draws a filled lens shape using an outer and inner cubic Bezier curve.
 * <p>
 * A tie whose notes sit in different lines is laid out as one half per line, and each half is
 * drawn by the same path as a whole tie: an open half is a complete arc over its own width that
 * returns to the baseline at the staff edge, so nothing here branches on
 * {@link LayoutResult.TieLayout#openSide()}. That distinction is decided in the layout phase,
 * which is the only phase that knows where the edges are.
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
     * Renders the given tie, or the half of it the line being painted owns.
     * <p>
     * Reads pre-computed geometry from {@link LayoutResult.TieLayout}. If no
     * layout was computed for this tie, the method returns without rendering.
     *
     * @param g2         Graphics context (staff-space coordinate system)
     * @param tie        The tie
     * @param invariants the line being painted and its render state
     * @param frame      Element frame
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

        try (var _ = GraphicsState.save(g2, TRANSFORM, COLOR, STROKE)) {
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
     * <p>
     * A tie crossing a line boundary has only one endpoint in the line being painted. The
     * other is not an element here and so contributes no color; the line that holds it colors
     * its own half from the same two checks.
     */
    Color determineTieColor(Tie tie, LineInvariants invariants) {
        var line = invariants.requireCurrentLine();
        var anchorColor = endpointColor(tie.anchor(), line, invariants);

        if (anchorColor != null) {
            return anchorColor;
        }

        var endColor = endpointColor(tie.end(), line, invariants);

        if (endColor != null) {
            return endColor;
        }

        var tieColor = invariants.colorFor(new HitTarget.Tie(tie));

        if (!LineInvariants.isDefaultColor(tieColor)) {
            return tieColor;
        }

        return RenderingUtils.ELEMENT_COLOR;
    }

    /**
     * Returns the color {@code endpoint} imposes on the tie, or null when it imposes none —
     * either because the endpoint is not in {@code line}, the line being painted, or because it
     * is drawn in the default color and so has nothing to impose.
     * <p>
     * {@link LineInvariants#getElementColor} indexes the line being painted, so an endpoint in
     * another line has no index to ask it about.
     */
    private static @Nullable Color endpointColor(
        NoteElement endpoint, Line line, LineInvariants invariants) {
        if (!line.contains(endpoint)) {
            return null;
        }

        var color = invariants.getElementColor(line.getElementIndex(endpoint));

        if (LineInvariants.isDefaultColor(color)) {
            return null;
        }

        return color;
    }
}
