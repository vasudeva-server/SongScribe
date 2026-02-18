/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LayoutAccumulator")
class LayoutAccumulatorTest {

    private LayoutAccumulator accumulator;

    @BeforeEach
    void setUp() {
        accumulator = new LayoutAccumulator();
    }

    @Nested
    @DisplayName("add and intersects")
    class AddAndIntersects {

        @Test
        @DisplayName("detects intersection with accumulated rectangle")
        void detectsIntersection() {
            accumulator.add(new Rectangle2D.Double(100, 50, 20, 30));

            // Area that overlaps the accumulated area
            var testArea = new Area(new Rectangle2D.Double(110, 60, 20, 30));
            assertTrue(accumulator.intersects(testArea));
        }

        @Test
        @DisplayName("detects no intersection when areas don't overlap")
        void detectsNoIntersection() {
            accumulator.add(new Rectangle2D.Double(100, 50, 20, 30));

            // Area that doesn't overlap
            var testArea = new Area(new Rectangle2D.Double(200, 200, 20, 30));
            assertFalse(accumulator.intersects(testArea));
        }

        @Test
        @DisplayName("accumulates multiple areas")
        void accumulatesMultipleAreas() {
            accumulator.add(new Rectangle2D.Double(100, 50, 20, 30));
            accumulator.add(new Rectangle2D.Double(400, 100, 20, 30));

            // Test intersection with first area
            assertTrue(accumulator.intersects(new Rectangle2D.Double(110, 60, 10, 10)));

            // Test intersection with second area
            assertTrue(accumulator.intersects(new Rectangle2D.Double(410, 110, 10, 10)));
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("resets all accumulated areas")
        void resetsAccumulatedAreas() {
            accumulator.add(new Rectangle2D.Double(100, 50, 20, 30));
            accumulator.add(new Rectangle2D.Double(400, 100, 20, 30));

            accumulator.clear();

            assertTrue(accumulator.isEmpty());
            assertFalse(accumulator.intersects(new Rectangle2D.Double(110, 60, 10, 10)));
        }
    }

}
