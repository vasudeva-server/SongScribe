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

package songscribe.ui.hit;

import java.awt.Point;
import org.jspecify.annotations.Nullable;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;

/**
 * Inputs shared by every {@link HitTester} in a hit-test cascade.
 *
 * @param pointPx            the hit-test point in document pixels
 * @param line               the line being hit-tested
 * @param layoutResult       the line's layout result, or {@code null} if unavailable
 * @param middleLineYSs      the y-coordinate of the staff's middle line, in staff-spaces
 * @param lyricRenderMetrics the song-wide lyric render metrics the lyric hit test needs for the
 *                           lyric row's height, or {@code null} when unavailable
 */
public record HitTestContext(
    Point pointPx,
    Line line,
    @Nullable LayoutResult layoutResult,
    double middleLineYSs,
    @Nullable LyricRenderMetrics lyricRenderMetrics
) {
    /**
     * Copies {@code pointPx} because {@link Point} is mutable: without this, a caller that
     * reuses one point across several hit tests would retroactively change the point every
     * previously built context reports.
     */
    public HitTestContext {
        pointPx = new Point(pointPx);
    }

    /** Returns a copy, so a caller cannot reach in and move this context's point. */
    @Override
    public Point pointPx() {
        return new Point(pointPx);
    }

    /** The hit-test point's x-coordinate in staff spaces. */
    public double xSs() {
        return ScaleContext.pxToSs(pointPx.x);
    }

    /** The hit-test point's y-coordinate in staff spaces. */
    public double ySs() {
        return ScaleContext.pxToSs(pointPx.y);
    }
}
