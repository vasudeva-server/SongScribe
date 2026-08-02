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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.engraving.LineThickness;
import songscribe.shape.HairpinShape;
import songscribe.util.GraphicsState;

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
     * @param isCrescendo True for crescendo, false for diminuendo
     * @param invariants  The line's rendering invariants
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
                (float) LineThickness.HAIRPIN_SS,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            ));

            for (var line : computeHairpinLines(layout, hairpin instanceof Crescendo, invariants)) {
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
            renderSingleHairpin(entry.getKey(), entry.getValue(), g2, invariants);
        }

        for (var entry : layoutResult.getDecorationLayoutsByType(Diminuendo.class)) {
            renderSingleHairpin(entry.getKey(), entry.getValue(), g2, invariants);
        }
    }

    // ==========================================================================
    // Hit testing
    // ==========================================================================

    /**
     * Hit-tests a click point against all hairpins on {@code line}, returning the hairpin
     * whose wedge-and-margin bounding box contains the point, or {@code null} if none.
     * <p>
     * The box needs no extra tolerance band: a hairpin is only
     * {@link Hairpin#HAIRPIN_OPENING_HEIGHT_SS} tall, so it is already about as
     * forgiving as a tolerance band would be.
     * <p>
     * The box and the overlap rule come from
     * {@link RenderingUtils#hitTestDecoration}, shared with every other decoration.
     *
     * @param clickXSs      Click X in staff spaces (line-local, same space as DecorationLayout.xSs)
     * @param clickYSs      Click Y in staff spaces (component space, relative to the component top)
     * @param line          The line
     * @param layoutResult  The line's layout result, or null if layout has not run yet
     * @param middleLineYSs The line's middle-staff-line Y in component space (staff spaces)
     */
    public @Nullable Hairpin hitTestHairpin(
        double clickXSs,
        double clickYSs,
        Line line,
        @Nullable LayoutResult layoutResult,
        double middleLineYSs
    ) {
        return RenderingUtils.hitTestDecoration(
            line.findRangeElements(Hairpin.class), clickXSs, clickYSs, layoutResult, middleLineYSs);
    }
}
