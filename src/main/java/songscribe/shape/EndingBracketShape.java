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

import songscribe.dom.Ending;

/**
 * The corner points of a first/second ending bracket: up the left leg, across the top, and (when
 * present) down the right leg. The caller strokes the returned polyline; this class holds the
 * geometry and reads the bracket's own range from the domain model, with no layout or renderer
 * dependency.
 */
public final class EndingBracketShape {

    private EndingBracketShape() {}

    /**
     * Builds the ending bracket corner points as a single polyline, so the top corners join
     * cleanly when stroked.
     *
     * <p>The horizontal arm ends where the bracket's ink reaches on the right, which is
     * {@link Ending.BracketRange#inkRightXSs()}. A closed bracket's arm ends at an interior corner,
     * where a stroker leaves a join, so its ink reaches the bracket's own right edge. An open
     * arm ends at a terminal cap, which {@code GraphicUtils.drawPath} pulls in half a stroke
     * width; pushing the endpoint out by that same half puts the open arm's ink at the X a
     * closed bracket's right leg would reach.
     *
     * @param bracket   the bracket range being drawn
     * @param yTopSs    top of the bracket, in staff spaces
     * @param yBottomSs bottom of the legs, in staff spaces
     * @return the bracket corner points, in draw order
     */
    public static Point2D[] points(Ending.BracketRange bracket, double yTopSs, double yBottomSs) {
        var armEndXSs = bracket.inkRightXSs();

        var bracketPoints = new ArrayList<Point2D>(List.of(
            new Point2D.Double(bracket.x1Ss(), yBottomSs),
            new Point2D.Double(bracket.x1Ss(), yTopSs),
            new Point2D.Double(armEndXSs, yTopSs)));

        if (bracket.hasClosingStroke()) {
            bracketPoints.add(new Point2D.Double(bracket.x2Ss(), yBottomSs));
        }

        return bracketPoints.toArray(new Point2D[0]);
    }
}
