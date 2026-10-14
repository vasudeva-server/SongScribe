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
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The corner points of a first/second ending bracket, in staff-space values. The caller
 * strokes the returned polyline.
 */
public final class EndingBracketShape {

    private EndingBracketShape() {}

    public enum Closure {
        OPEN,
        CLOSED
    }

    /**
     * The points form a single polyline, so the top corners join cleanly when stroked.
     *
     * @param outlineSs a closed outline's right edge is the leg's X; an open one's is where the
     *                  free arm ends
     */
    public static Point2D[] points(Rectangle2D outlineSs, Closure closure) {
        var leftXSs = outlineSs.getMinX();
        var rightXSs = outlineSs.getMaxX();
        var topYSs = outlineSs.getMinY();
        var bottomYSs = outlineSs.getMaxY();

        var bracketPoints = new ArrayList<Point2D>(List.of(
            new Point2D.Double(leftXSs, bottomYSs),
            new Point2D.Double(leftXSs, topYSs),
            new Point2D.Double(rightXSs, topYSs)));

        if (closure == Closure.CLOSED) {
            bracketPoints.add(new Point2D.Double(rightXSs, bottomYSs));
        }

        return bracketPoints.toArray(new Point2D[0]);
    }
}
