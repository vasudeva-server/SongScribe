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
 * The two arms of a tuplet bracket, split by the central number gap. The left arm runs down the
 * left vertical, up to the corner, then across to the gap; the right arm mirrors it. Each arm is a
 * single polyline so its corner joins cleanly when stroked.
 * <p>
 * A sloped bracket gives each corner its own Y, so the horizontal segments tilt with the note
 * contour. The verticals still hang straight down from their (sloped) corners by a fixed arm
 * height. Each arm's stretch of the sloped bracket line is supplied by the caller as a segment;
 * this class only orders the points. Each arm therefore runs from its corner's X at the arm bottom
 * up to the corner, and in along the segment to the gap edge. See {@code docs/layout-geometry.md}
 * for the annotated diagram.
 * <p>
 * All coordinates are plain staff-space values; the caller derives the number gap from the shaped
 * glyph and does the font drawing. This class holds no layout or renderer dependency.
 */
public final class TupletBracketShape {

    private TupletBracketShape() {}

    /**
     * Builds the left bracket arm: down the vertical, up to the sloped corner, across to the gap.
     *
     * @param lineSs       the bracket line's left stretch, in staff spaces: from the left corner
     *                     ({@code P1}) to the number gap's left edge ({@code P2})
     * @param armBottomYSs y of the vertical arm's lower end, in staff spaces
     * @return the left arm corner points, in draw order
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
     * Builds the right bracket arm: from the gap across to the sloped corner, then down the vertical.
     *
     * @param lineSs       the bracket line's right stretch, in staff spaces: from the number gap's
     *                     right edge ({@code P1}) to the right corner ({@code P2})
     * @param armBottomYSs y of the vertical arm's lower end, in staff spaces
     * @return the right arm corner points, in draw order
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
