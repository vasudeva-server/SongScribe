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
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.engraving.EngravingConstants;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.shape.HairpinShape;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.STROKE;

/**
 * Renders crescendo and diminuendo hairpins.
 * <p>
 * Crescendo: opens from left to right (gets louder)
 * Diminuendo: opens from right to left (gets softer)
 */
public final class HairpinRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** The hairpin stroke width as a multiple of the LilyPond base thickness. */
    private static final double HAIRPIN_MULTIPLIER = 1.0;

    private static final double HAIRPIN_SS = EngravingConstants.LILYPOND_BASE_THICKNESS_SS * HAIRPIN_MULTIPLIER;

    // Singleton instance
    private static final HairpinRenderer INSTANCE = new HairpinRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private HairpinRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static HairpinRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Computes the two wedge lines of a hairpin from its decoration layout.
     *
     * @param layout      The pre-computed layout (with offsets already applied)
     * @param kind        Which hairpin this is — decides the wedge direction
     * @param invariants  The line's rendering invariants
     */
    static Line2D.Double[] computeHairpinLines(
        LayoutResult.DecorationLayout layout,
        Hairpin.Kind kind,
        LineInvariants invariants
    ) {
        var x1 = layout.xSs();
        var x2 = x1 + layout.widthSs();
        var topYSs = RenderingUtils.layoutYToComponentYSs(layout.ySs(), invariants);
        var bottomYSs = topYSs + layout.heightSs();
        var middleYSs = topYSs + layout.heightSs() / 2.0;

        // HairpinShape.lines' boolean means "tip on the left," not "is a crescendo" —
        // songscribe.shape is pure geometry and must not depend on songscribe.dom.
        return HairpinShape.lines(x1, x2, topYSs, bottomYSs, middleYSs, kind == Hairpin.Kind.CRESCENDO);
    }

    /**
     * Renders a single hairpin from its decoration layout, in the selection color when
     * {@code hairpin} is the selected decoration.
     *
     * @param hairpin    The hairpin being rendered; its subtype decides the wedge direction
     * @param layout     The pre-computed layout (with offsets already applied)
     * @param g2         Graphics context with scale transform
     * @param invariants The line's rendering invariants
     */
    private void renderSingleHairpin(
        Hairpin hairpin,
        LayoutResult.DecorationLayout layout,
        Graphics2D g2,
        LineInvariants invariants
    ) {
        try (var _ = GraphicsState.save(g2, COLOR, STROKE)) {
            // A hairpin belongs to no single note, so it has no owner whose color it could take.
            g2.setColor(RenderingUtils.decorationColor(
                new HitTarget.Hairpin(hairpin), null, invariants, ElementFrame.LINE_LEVEL));
            // CAP_ROUND is intentional: its cap extends past the endpoint, so both lines
            // overlap at the narrow tip and fill it solidly. GraphicUtils.drawRoundedLine
            // keeps ends within endpoints, which leaves the tip visually unclosed.
            g2.setStroke(new BasicStroke(
                (float) HAIRPIN_SS,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            ));

            for (var line : computeHairpinLines(layout, hairpin.getKind(), invariants)) {
                g2.draw(line);
            }
        }
    }

    /**
     * Renders all hairpins (crescendo and diminuendo) for a line using layout results.
     * <p>
     * Iterates all {@link Crescendo} and {@link Diminuendo} entries in the layout
     * (both new spans and those bridged from legacy intervals during layout).
     */
    public void renderHairpinsFromLine(
        Graphics2D g2,
        LineInvariants invariants
    ) {
        var layoutResult = invariants.getLayoutResult();

        for (var entry : layoutResult.getDecorationLayoutsByType(Crescendo.class)) {
            renderSingleHairpin(entry.getKey(), entry.getValue(), g2, invariants);
        }

        for (var entry : layoutResult.getDecorationLayoutsByType(Diminuendo.class)) {
            renderSingleHairpin(entry.getKey(), entry.getValue(), g2, invariants);
        }
    }

}
