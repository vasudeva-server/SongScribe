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

import module java.desktop;

/**
 * The filled lens outline of a tie arc, built from two cubic Béziers that share start and end
 * points: an outer curve (start → end) and a reversed inner curve (end → start). The shared
 * endpoints create the natural tapering of the tie.
 * <p>
 * All coordinates are plain staff-space values; the caller resolves them from layout before
 * calling. This class holds no layout or renderer dependency.
 */
public final class TieShape {

    private TieShape() {}

    /**
     * Builds the filled tie lens from its cubic Bézier control points.
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
    public static Shape build(
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
        var tiePath = new GeneralPath(Path2D.WIND_NON_ZERO, 4);

        // Outer cubic Bezier: start → end
        tiePath.moveTo(startX, startY);
        tiePath.curveTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY);

        // Inner cubic Bezier (reversed): end → start, forming the lens shape.
        // Both curves share start/end points, creating natural tapering.
        tiePath.curveTo(innerCp2X, innerCp2Y, innerCp1X, innerCp1Y, startX, startY);

        tiePath.closePath();

        return tiePath;
    }
}
