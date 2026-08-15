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

package songscribe.shape;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;

/**
 * LilyPond's {@code bezier-bow.cc} single-curve bow shape: the arc-height/indent math shared by
 * ties and slurs, plus the filled two-curve lens outline built from those control points. Ratio,
 * height-limit, and max-fraction are caller-supplied parameters — this class holds no tie- or
 * slur-specific constants.
 * <p>
 * All coordinates and dimensions are plain staff-space values; the caller resolves them from
 * layout before calling. This class holds no layout or renderer dependency.
 * <p>
 * {@code slur_shape} control points — P1 = (indent, height), P2 = (width − indent, height) —
 * bow the outer curve away from the baseline, with the inner curve offset back toward it to
 * form the tapered lens. Both curves share the start and end points, so the lens closes to a
 * point at each end.
 */
public final class BezierBow {

    private BezierBow() {}

    /**
     * LilyPond {@code bezier-bow.cc F0_1}: maps [0, ∞) → [0, 1). Unitless.
     */
    private static double f01(double x) {
        return 2 / Math.PI * Math.atan(Math.PI * x / 2);
    }

    /**
     * LilyPond {@code bezier-bow.cc slur_height}: the bow's arc height for the given width, ratio,
     * and asymptotic height limit. Width and result are in staff spaces; ratio is unitless.
     *
     * @param widthSs      the bow's endpoint-to-endpoint width
     * @param ratio        height-vs-width growth ratio ({@code r_0})
     * @param heightLimitSs asymptotic max arc height ({@code h_inf})
     */
    public static double height(double widthSs, double ratio, double heightLimitSs) {
        return f01(widthSs * ratio / heightLimitSs) * heightLimitSs;
    }

    /**
     * LilyPond {@code bezier-bow.cc slur_shape} control-point indent: the horizontal inset of the
     * two interior Bézier control points from the bow's endpoints. Width and result are in staff
     * spaces; height-limit and max-fraction are unitless/staff-space parameters matching
     * {@code slur_shape}'s {@code h_inf} and {@code max_fraction}.
     *
     * @param widthSs       the bow's endpoint-to-endpoint width
     * @param heightLimitSs asymptotic max arc height ({@code h_inf})
     * @param maxFraction   control-point indent factor ({@code max_fraction})
     */
    public static double indent(double widthSs, double heightLimitSs, double maxFraction) {
        var q = 2 * heightLimitSs / maxFraction;

        return 2 * heightLimitSs - q * q * maxFraction / (widthSs + q);
    }

    /**
     * Builds the filled bow lens from its cubic Bézier control points: an outer curve
     * (start → end) and a reversed inner curve (end → start), sharing start/end points. The
     * shared endpoints create the natural tapering at the ends. The outer curve's two control
     * points bow the lens away from the chord; the inner curve's two bow it back, less far, so the
     * two meet at the shared start and end points.
     *
     * @param startX    outer curve start x
     * @param startY    outer curve start y
     * @param cp1X      outer curve first control-point x
     * @param cp1Y      outer curve first control-point y
     * @param cp2X      outer curve second control-point x
     * @param cp2Y      outer curve second control-point y
     * @param endX      outer curve end x
     * @param endY      outer curve end y
     * @param innerCp1X inner curve first control-point x
     * @param innerCp1Y inner curve first control-point y
     * @param innerCp2X inner curve second control-point x
     * @param innerCp2Y inner curve second control-point y
     * @return the closed lens {@link Shape}
     */
    public static Shape lens(
        double startX,
        double startY,
        double cp1X,
        double cp1Y,
        double cp2X,
        double cp2Y,
        double endX,
        double endY,
        double innerCp1X,
        double innerCp1Y,
        double innerCp2X,
        double innerCp2Y
    ) {
        var path = new GeneralPath(Path2D.WIND_NON_ZERO, 4);

        // Outer cubic Bezier: start → end
        path.moveTo(startX, startY);
        path.curveTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY);

        // Inner cubic Bezier (reversed): end → start, forming the lens shape.
        // Both curves share start/end points, creating natural tapering.
        path.curveTo(innerCp2X, innerCp2Y, innerCp1X, innerCp1Y, startX, startY);

        path.closePath();

        return path;
    }
}
