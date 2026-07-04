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
 * The two arms of a tuplet bracket, split by the central number gap. The left arm runs down the
 * left vertical, up to the corner, then across to the gap; the right arm mirrors it. Each arm is a
 * single polyline so its corner joins cleanly when stroked.
 * <p>
 * All coordinates are plain staff-space values; the caller derives the number gap from the shaped
 * glyph and does the font drawing. This class holds no layout or renderer dependency.
 */
public final class TupletBracketShape {

    private TupletBracketShape() {}

    /**
     * Builds the left bracket arm: down the vertical, up to the corner, across to the gap.
     *
     * @param leftXSs     left edge of the bracket, in staff spaces
     * @param gapLeftXSs  left edge of the number gap, in staff spaces
     * @param bracketYSs  y of the horizontal bracket line, in staff spaces
     * @param armBottomYSs y of the vertical arm's lower end, in staff spaces
     * @return the left arm corner points, in draw order
     */
    public static Point2D[] leftArm(
        double leftXSs,
        double gapLeftXSs,
        double bracketYSs,
        double armBottomYSs
    ) {
        return new Point2D[]{
            new Point2D.Double(leftXSs, armBottomYSs),
            new Point2D.Double(leftXSs, bracketYSs),
            new Point2D.Double(gapLeftXSs, bracketYSs)
        };
    }

    /**
     * Builds the right bracket arm: from the gap across to the right edge, then down the vertical.
     *
     * @param gapRightXSs  right edge of the number gap, in staff spaces
     * @param rightXSs     right edge of the bracket, in staff spaces
     * @param bracketYSs   y of the horizontal bracket line, in staff spaces
     * @param armBottomYSs y of the vertical arm's lower end, in staff spaces
     * @return the right arm corner points, in draw order
     */
    public static Point2D[] rightArm(
        double gapRightXSs,
        double rightXSs,
        double bracketYSs,
        double armBottomYSs
    ) {
        return new Point2D[]{
            new Point2D.Double(gapRightXSs, bracketYSs),
            new Point2D.Double(rightXSs, bracketYSs),
            new Point2D.Double(rightXSs, armBottomYSs)
        };
    }
}
