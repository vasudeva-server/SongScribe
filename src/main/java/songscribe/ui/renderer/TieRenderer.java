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
 * A tie across a line break is drawn as one half per line by the same path as a whole tie, so
 * drawing never branches on which edge is open; only layout knows where the edges are.
 */
public final class TieRenderer {

    // Singleton instance
    private static final TieRenderer INSTANCE = new TieRenderer();

    private static final BasicStroke TIE_OUTLINE_STROKE = new BasicStroke(
        (float) LayoutEngine.TIE_OUTLINE_THICKNESS_SS,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    );

    private TieRenderer() {
    }

    public static TieRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * @param g2 Graphics context (staff-space coordinate system)
     */
    public void renderTie(Graphics2D g2, LayoutResult.TieLayout layout, LineInvariants invariants) {
        try (var _ = GraphicsState.save(g2, TRANSFORM, COLOR, STROKE)) {
            g2.translate(0, invariants.getMiddleLineYSs());
            g2.setColor(determineTieColor(layout.placement(), invariants));

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
     * A tie is colored if either note is playing or selected, which carries an index-range
     * selection onto the ties inside it, or if the tie is itself the selected target. A tie
     * crossing a line boundary takes color only from its endpoint in the line being painted.
     */
    private static Color determineTieColor(Line.TiePlacement placement, LineInvariants invariants) {
        var range = placement.range();

        if (placement.holdsAnchor()) {
            var anchorColor = endpointColor(range.begin(), invariants);

            if (anchorColor != null) {
                return anchorColor;
            }
        }

        if (placement.holdsEnd()) {
            var endColor = endpointColor(range.end(), invariants);

            if (endColor != null) {
                return endColor;
            }
        }

        var tieColor = invariants.colorFor(new HitTarget.Tie(placement.tie()));

        if (!LineInvariants.isDefaultColor(tieColor)) {
            return tieColor;
        }

        return RenderingUtils.ELEMENT_COLOR;
    }

    /**
     * Null when the note is drawn in the default color and so has nothing to impose.
     */
    private static @Nullable Color endpointColor(int index, LineInvariants invariants) {
        var color = invariants.getElementColor(index);

        if (LineInvariants.isDefaultColor(color)) {
            return null;
        }

        return color;
    }
}
