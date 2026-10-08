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
 * The corner points of a first/second ending bracket: up the left leg, across the top, and (when
 * the bracket is closed) down the right leg. The caller strokes the returned polyline.
 * <p>
 * All coordinates are plain staff-space values; the caller resolves them from layout before
 * calling. This class holds no layout or renderer dependency.
 */
public final class EndingBracketShape {

    private EndingBracketShape() {}

    /** Whether an ending bracket ends in a right leg. */
    public enum Closure {
        /** The top arm ends free on the right. */
        OPEN,

        /** The top arm turns down into a right leg. */
        CLOSED
    }

    /**
     * Builds the ending bracket corner points as a single polyline, so the top corners join
     * cleanly when stroked.
     * <p>
     * The left leg runs along the outline's left edge from its bottom to its top, and the top arm
     * along its top edge to its right edge. A closed bracket's right leg then runs down the
     * outline's right edge to its bottom: the caller sizes a closed outline so that its right edge
     * is the leg's X, and an open one so that its right edge is where the free arm ends.
     *
     * @param outlineSs the rectangle the path's corners lie on, in staff spaces: from the left
     *                  leg's X to the top arm's right end, and from the top arm's Y to the legs'
     *                  bottom
     * @param closure   whether the bracket ends in a right leg
     * @return the bracket corner points, in draw order: three for an open bracket, four for a
     *     closed one
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
