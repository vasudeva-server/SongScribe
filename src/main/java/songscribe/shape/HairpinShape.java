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
 * The two straight segments of a dynamics hairpin. A crescendo opens from a narrow tip on the left
 * to a wide mouth on the right; a diminuendo is the mirror image, opening to the left.
 * <p>
 * All coordinates are plain staff-space values; the caller resolves them from layout before
 * calling. This class holds no layout or renderer dependency.
 */
public final class HairpinShape {

    private HairpinShape() {}

    /**
     * Builds the two hairpin segments.
     *
     * @param x1          left x, in staff spaces
     * @param x2          right x, in staff spaces
     * @param topYSs      top y, in staff spaces
     * @param bottomYSs   bottom y, in staff spaces
     * @param middleYSs   vertical midpoint, in staff spaces (the tip side)
     * @param isCrescendo true for a crescendo (tip on the left), false for a diminuendo
     * @return the two segments
     */
    public static Line2D.Double[] lines(
        double x1,
        double x2,
        double topYSs,
        double bottomYSs,
        double middleYSs,
        boolean isCrescendo
    ) {
        if (isCrescendo) {
            return new Line2D.Double[]{
                new Line2D.Double(x1, middleYSs, x2, topYSs),
                new Line2D.Double(x1, middleYSs, x2, bottomYSs)
            };
        }

        return new Line2D.Double[]{
            new Line2D.Double(x1, topYSs, x2, middleYSs),
            new Line2D.Double(x1, bottomYSs, x2, middleYSs)
        };
    }
}
