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
package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class LayoutAccumulatorTest extends UnitTest {

    private static final double UNIT = 10.0;
    // Half-unit shift guarantees a genuine (non-zero-area) overlap, not just a touching edge.
    private static final double OVERLAP_SHIFT = UNIT / 2;
    // Far enough that a unit square here cannot overlap one at the origin.
    private static final double DISJOINT_ORIGIN = 100.0;
    // Leaves a clear gap (UNIT..2*UNIT) between the left rect and this one.
    private static final double RIGHT_RECT_X = UNIT * 2;
    // Spans from the origin across both the left rect and the right rect.
    private static final double SPAN_WIDTH = RIGHT_RECT_X + UNIT;
    // Lies strictly inside the gap between the two rects.
    private static final double GAP_PROBE_X = UNIT + UNIT / 4;
    private static final double GAP_PROBE_WIDTH = UNIT / 2;

    private static Rectangle2D unitSquareAt(double x, double y) {
        return new Rectangle2D.Double(x, y, UNIT, UNIT);
    }

    @Test
    void testIntersectsRectangleReturnsTrueWhenRectanglesOverlap() {
        var accumulator = new LayoutAccumulator();
        accumulator.add(unitSquareAt(0, 0));

        assertThat(accumulator.intersects(unitSquareAt(OVERLAP_SHIFT, OVERLAP_SHIFT))).isTrue();
    }

    @Test
    void testIntersectsRectangleReturnsFalseWhenRectanglesDisjoint() {
        var accumulator = new LayoutAccumulator();
        accumulator.add(unitSquareAt(0, 0));

        assertThat(accumulator.intersects(unitSquareAt(DISJOINT_ORIGIN, DISJOINT_ORIGIN))).isFalse();
    }

    @Test
    void testIntersectsAreaReturnsTrueWhenAreasOverlap() {
        var accumulator = new LayoutAccumulator();
        accumulator.add(new Area(unitSquareAt(0, 0)));

        assertThat(accumulator.intersects(new Area(unitSquareAt(OVERLAP_SHIFT, OVERLAP_SHIFT)))).isTrue();
    }

    @Test
    void testIntersectsAreaReturnsFalseWhenAreasDisjoint() {
        var accumulator = new LayoutAccumulator();
        accumulator.add(new Area(unitSquareAt(0, 0)));

        assertThat(accumulator.intersects(new Area(unitSquareAt(DISJOINT_ORIGIN, DISJOINT_ORIGIN)))).isFalse();
    }

    @Test
    void testFreshAccumulatorIsEmpty() {
        assertThat(new LayoutAccumulator().isEmpty()).isTrue();
    }

    @Test
    void testClearMakesAccumulatorEmptyAndStopsIntersecting() {
        var accumulator = new LayoutAccumulator();
        var rect = unitSquareAt(0, 0);
        accumulator.add(rect);
        assertThat(accumulator.isEmpty()).isFalse();

        accumulator.clear();

        assertThat(accumulator.isEmpty()).isTrue();
        assertThat(accumulator.intersects(rect)).isFalse();
    }

    @Test
    void testGetAreaReturnsDefensiveCopy() {
        var accumulator = new LayoutAccumulator();
        accumulator.add(unitSquareAt(0, 0));

        var copy = accumulator.getArea();
        assertThat(copy.isEmpty()).isFalse();

        // Mutating the returned copy must not bleed back into the accumulator.
        copy.add(new Area(unitSquareAt(DISJOINT_ORIGIN, DISJOINT_ORIGIN)));

        assertThat(accumulator.intersects(unitSquareAt(DISJOINT_ORIGIN, DISJOINT_ORIGIN))).isFalse();
    }

    @Test
    void testUnionOfTwoRectanglesIntersectsSpanningRectangle() {
        var accumulator = new LayoutAccumulator();
        accumulator.add(unitSquareAt(0, 0));
        var rightRect = unitSquareAt(RIGHT_RECT_X, 0);
        accumulator.add(rightRect);

        var spanning = new Rectangle2D.Double(0, 0, SPAN_WIDTH, UNIT);
        assertThat(accumulator.intersects(spanning)).isTrue();

        // The second add must have registered independently of the first.
        assertThat(accumulator.intersects(rightRect)).isTrue();

        // The union must not spuriously fill the gap between the two rects.
        var gapProbe = new Rectangle2D.Double(GAP_PROBE_X, 0, GAP_PROBE_WIDTH, UNIT);
        assertThat(accumulator.intersects(gapProbe)).isFalse();
    }
}
