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

package songscribe.layout;

import songscribe.shape.AccentShape;

/**
 * The closed-form geometry of an accent seated on a round staccato dot, derived from LilyPond's
 * placement rule rather than measured from SongScribe's output.
 * <p>
 * Shared by {@code AccentOverStaccatoTest}, which pins the closed form directly against
 * {@link StaffExtents}, and {@code ArticulationStackingTest}, which needs the same quantity to
 * predict where the full stacking pipeline puts the accent. Deriving it twice invited the two copies
 * to drift apart with nothing to notice.
 */
final class AccentDotGeometry {

    private AccentDotGeometry() {
    }

    /**
     * The slope of the accent's inner edge at {@code xSs}, measured from the wedge's left edge. The
     * arm is one straight path segment, so the slope is constant wherever a real support meets it.
     */
    static double accentArmSlopeAtSs(boolean above, double xSs) {
        for (var segment : ShapeProfile.innerEdge(AccentShape.accent(), above).segments()) {
            if (xSs >= segment.xStartSs() && xSs <= segment.xEndSs()) {
                return segment.slopeSs();
            }
        }

        throw new AssertionError("the accent's inner edge does not span x = " + xSs);
    }

    /**
     * How much closer a round dot lets the accent seat than the flat top of the dot's box would.
     * <p>
     * {@code Skyline::padded} (skyline.cc) dilates every building of the support by the horizon
     * padding, so the accent's straight arm — slope {@code m} — descends onto the dot's dilated
     * circle. The binding x is where the circle's own slope reaches {@code m}, at
     * {@code v = r(1 − m/√(1+m²))} from its apex; there the circle has dropped
     * {@code r(1 − 1/√(1+m²))} and the arm has climbed {@code m·v}. The two sum to a result that
     * depends on neither the horizon padding nor the dot's position:
     * <pre>
     *   gain = r · (1 + m − √(1 + m²))
     * </pre>
     * The same formula, evaluated with Feta's dot ({@code r = 0.2}) and its steeper sforzato arm
     * ({@code m ≈ 0.30}), reproduces the 0.0511 ss that LilyPond 2.26 itself moves an accent when the
     * dot's stencil is boxified — which is how this was checked against the engine rather than
     * against SongScribe's own output.
     *
     * @param supportWindowStartSs where the dilated dot first meets the accent's arm, measured from
     *                             the wedge's left edge
     */
    static double roundDotGainSs(boolean above, double supportWindowStartSs, double dotWidthSs) {
        var radiusSs = dotWidthSs / 2.0;
        var armSlopeSs = accentArmSlopeAtSs(above, supportWindowStartSs);

        return radiusSs * (1.0 + armSlopeSs - Math.hypot(1.0, armSlopeSs));
    }
}
