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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * The accent (">") marking, in staff-space units. Origin at the bounding-box center, tip
 * toward +x, wide end at -x.
 */
public final class AccentShape {

    private static final double WEDGE_WIDTH_SS = 1.43;

    private static final Shape ACCENT = build();

    private AccentShape() {}

    public static Shape accent() {
        return ACCENT;
    }

    private static Shape build() {
        var rawPath = buildRawPath();
        var bounds = rawPath.getBounds2D();

        var centerX = bounds.getCenterX();
        var centerY = bounds.getCenterY();
        var scale = WEDGE_WIDTH_SS / bounds.getWidth();

        var transform = new AffineTransform();
        transform.scale(scale, scale);
        transform.translate(-centerX, -centerY);

        return transform.createTransformedShape(rawPath);
    }

    private static Path2D buildRawPath() {
        var path = new Path2D.Double();

        path.moveTo(158.7837, 54.2281);
        path.lineTo(108.7974, 47.3887);
        path.lineTo(8.1689, 21.1266);
        path.curveTo(3.0928, 19.8102, 0.0418, 14.6212, 1.3578, 9.5452);
        path.curveTo(2.6748, 4.4702, 7.8638, 1.4192, 12.9389, 2.7356);
        path.lineTo(175.7391, 44.9665);
        path.curveTo(180.0393, 46.0812, 182.8872, 49.9775, 182.8558, 54.229);
        path.curveTo(182.8873, 58.4794, 180.0397, 62.3752, 175.7399, 63.4905);
        path.lineTo(12.9389, 105.7219);
        path.curveTo(7.8643, 107.0387, 2.6748, 103.9871, 1.3582, 98.9116);
        path.curveTo(0.0417, 93.8361, 3.0931, 88.6466, 8.1686, 87.3308);
        path.lineTo(108.7979, 61.0683);
        path.lineTo(158.7837, 54.2281);
        path.closePath();

        return path;
    }
}
