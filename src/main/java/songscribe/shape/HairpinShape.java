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
import java.awt.geom.Rectangle2D;

/**
 * The two straight segments of a dynamics hairpin, in staff-space values.
 */
public final class HairpinShape {

    private HairpinShape() {}

    public enum Tip {
        /** Tip on the left, mouth on the right: a crescendo. */
        LEFT,

        /** Tip on the right, mouth on the left: a diminuendo. */
        RIGHT
    }

    /** The segment meeting the top edge comes first. */
    public static Line2D.Double[] lines(Rectangle2D boundsSs, Tip tip) {
        var leftXSs = boundsSs.getMinX();
        var rightXSs = boundsSs.getMaxX();
        var topYSs = boundsSs.getMinY();
        var bottomYSs = boundsSs.getMaxY();
        var middleYSs = boundsSs.getCenterY();

        return switch (tip) {
            case LEFT -> new Line2D.Double[]{
                new Line2D.Double(leftXSs, middleYSs, rightXSs, topYSs),
                new Line2D.Double(leftXSs, middleYSs, rightXSs, bottomYSs)
            };
            case RIGHT -> new Line2D.Double[]{
                new Line2D.Double(leftXSs, topYSs, rightXSs, middleYSs),
                new Line2D.Double(leftXSs, bottomYSs, rightXSs, middleYSs)
            };
        };
    }
}
