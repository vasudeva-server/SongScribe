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

import module java.desktop;


import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.layout.LayoutResult;
import songscribe.shape.HairpinShape;
import songscribe.util.GraphicsState;

/**
 * Renders crescendo and diminuendo hairpins.
 * <p>
 * Crescendo: opens from left to right (gets louder)
 * Diminuendo: opens from right to left (gets softer)
 */
public final class DynamicsRenderer {

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

    /**
     * Renders a single hairpin from its decoration layout.
     *
     * @param layout      The pre-computed layout (with offsets already applied)
     * @param isCrescendo True for crescendo, false for diminuendo
     * @param g2          Graphics context with scale transform
     * @param ctx         Render context
     */
    static Line2D.Double[] computeHairpinLines(
        LayoutResult.DecorationLayout layout,
        boolean isCrescendo,
        LineInvariants invariants
    ) {
        var x1 = layout.xSs();
        var x2 = x1 + layout.widthSs();
        var topYSs = RenderingUtils.layoutYToComponentYSs(layout.ySs(), invariants);
        var bottomYSs = topYSs + layout.heightSs();
        var middleYSs = topYSs + layout.heightSs() / 2.0;

        return HairpinShape.lines(x1, x2, topYSs, bottomYSs, middleYSs, isCrescendo);
    }

    private void renderSingleHairpin(
        LayoutResult.DecorationLayout layout,
        boolean isCrescendo,
        Graphics2D g2,
        LineInvariants invariants
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR, STROKE)) {
            g2.setColor(RenderingUtils.ELEMENT_COLOR);
            // CAP_ROUND is intentional: its cap extends past the endpoint, so both lines
            // overlap at the narrow tip and fill it solidly. GraphicUtils.drawRoundedLine
            // keeps ends within endpoints, which leaves the tip visually unclosed.
            g2.setStroke(new BasicStroke(
                (float) invariants.getLineThickness().hairpinSs(),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            ));

            for (var line : computeHairpinLines(layout, isCrescendo, invariants)) {
                g2.draw(line);
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
        LineInvariants invariants
    ) {
        var layoutResult = invariants.getLayoutResult();

        for (var entry : layoutResult.getDecorationLayoutsByType(Crescendo.class)) {
            renderSingleHairpin(entry.getValue(), true, g2, invariants);
        }

        for (var entry : layoutResult.getDecorationLayoutsByType(Diminuendo.class)) {
            renderSingleHairpin(entry.getValue(), false, g2, invariants);
        }
    }
}
