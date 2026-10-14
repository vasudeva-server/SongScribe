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

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;

/**
 * LilyPond's {@code bezier-bow.cc} bow shape, shared by ties and slurs, in staff-space values.
 * It holds no tie- or slur-specific constants; the caller supplies them.
 */
public final class BezierBow {

    private BezierBow() {}

    private static double f01(double x) {
        return 2 / Math.PI * Math.atan(Math.PI * x / 2);
    }

    public static double height(double widthSs, double ratio, double heightLimitSs) {
        return f01(widthSs * ratio / heightLimitSs) * heightLimitSs;
    }

    public static double indent(double widthSs, double heightLimitSs, double maxFraction) {
        var q = 2 * heightLimitSs / maxFraction;

        return 2 * heightLimitSs - q * q * maxFraction / (widthSs + q);
    }

    public static Shape lens(
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
        var path = new GeneralPath(Path2D.WIND_NON_ZERO, 4);

        path.moveTo(startX, startY);
        path.curveTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY);

        // The reversed inner curve closes the lens; sharing both end points makes the tips taper.
        path.curveTo(innerCp2X, innerCp2Y, innerCp1X, innerCp1Y, startX, startY);

        path.closePath();

        return path;
    }
}
