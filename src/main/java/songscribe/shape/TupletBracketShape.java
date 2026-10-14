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

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * The two arms of a tuplet bracket, split by the central number gap, in staff-space values.
 * Each arm is a single polyline so its corner joins cleanly when stroked. See
 * {@code docs/layout-geometry.md}.
 */
public final class TupletBracketShape {

    private TupletBracketShape() {}

    /**
     * @param lineSs the bracket line's left stretch: from the left corner
     *               ({@code P1}) to the number gap's left edge ({@code P2})
     */
    public static Point2D[] leftArm(Line2D lineSs, double armBottomYSs) {
        var cornerSs = lineSs.getP1();

        return new Point2D[]{
            new Point2D.Double(cornerSs.getX(), armBottomYSs),
            cornerSs,
            lineSs.getP2()
        };
    }

    /**
     * @param lineSs the bracket line's right stretch: from the number gap's
     *               right edge ({@code P1}) to the right corner ({@code P2})
     */
    public static Point2D[] rightArm(Line2D lineSs, double armBottomYSs) {
        var cornerSs = lineSs.getP2();

        return new Point2D[]{
            lineSs.getP1(),
            cornerSs,
            new Point2D.Double(cornerSs.getX(), armBottomYSs)
        };
    }
}
