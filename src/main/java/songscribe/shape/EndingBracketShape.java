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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The corner points of a first/second ending bracket: up the left leg, across the top, and (when
 * present) down the right leg. The caller strokes the returned polyline; this class holds only the
 * geometry, no layout or renderer dependency.
 */
public final class EndingBracketShape {

    private EndingBracketShape() {}

    /**
     * Builds the ending bracket corner points as a single polyline, so the top corners join
     * cleanly when stroked.
     *
     * @param x1               left leg x, in staff spaces
     * @param x2               right leg x, in staff spaces
     * @param yTopSs           top of the bracket, in staff spaces
     * @param yBottomSs        bottom of the legs, in staff spaces
     * @param hasClosingStroke whether the right leg is drawn down to {@code yBottomSs}
     * @return the bracket corner points, in draw order
     */
    public static Point2D[] points(
        double x1,
        double x2,
        double yTopSs,
        double yBottomSs,
        boolean hasClosingStroke
    ) {
        var bracketPoints = new ArrayList<Point2D>(List.of(
            new Point2D.Double(x1, yBottomSs),
            new Point2D.Double(x1, yTopSs),
            new Point2D.Double(x2, yTopSs)));

        if (hasClosingStroke) {
            bracketPoints.add(new Point2D.Double(x2, yBottomSs));
        }

        return bracketPoints.toArray(new Point2D[0]);
    }
}
