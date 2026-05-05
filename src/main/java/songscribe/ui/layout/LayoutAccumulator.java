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

package songscribe.ui.layout;

import module java.desktop;


/**
 * Accumulates layout bounds using java.awt.geom.Area for complex shape handling.
 * <p>
 * Used during the arrange pass to track occupied space and detect collisions.
 * Supports both rectangular and irregular shapes (e.g., L-shaped ending brackets).
 */
public class LayoutAccumulator {

    // Debug flag for collision detection logging (can be enabled via reflection or configuration)
    private static final boolean DEBUG_COLLISION = Boolean.parseBoolean(
        System.getProperty("songscribe.debug.collision", "false")
    );

    private final Area accumulatedArea;

    public LayoutAccumulator() {
        this.accumulatedArea = new Area();
    }

    /**
     * Adds a rectangular region to the accumulated area.
     *
     * @param rect The rectangle to add
     */
    public void add(Rectangle2D rect) {
        var area = new Area(rect);
        accumulatedArea.add(area);

        if (DEBUG_COLLISION) {
            System.err.printf("[Collision] Added rect: [%.1f, %.1f, %.1f×%.1f]%n",
                rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        }
    }

    /**
     * Adds an arbitrary area to the accumulated area.
     *
     * @param area The area to add
     */
    public void add(Area area) {
        accumulatedArea.add(area);

        if (DEBUG_COLLISION) {
            var bounds = area.getBounds2D();
            System.err.printf("[Collision] Added area: [%.1f, %.1f, %.1f×%.1f]%n",
                bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        }
    }

    /**
     * Tests if the given area intersects with the accumulated area.
     *
     * @param area The area to test
     * @return true if the areas intersect
     */
    public boolean intersects(Area area) {
        var testArea = new Area(area);
        testArea.intersect(accumulatedArea);
        var result = !testArea.isEmpty();

        if (DEBUG_COLLISION) {
            var bounds = area.getBounds2D();
            System.err.printf("[Collision] intersects(area@[%.1f,%.1f]): %s%n",
                bounds.getX(), bounds.getY(), result);
        }

        return result;
    }

    /**
     * Tests if the given rectangle intersects with the accumulated area.
     *
     * @param rect The rectangle to test
     * @return true if the rectangle intersects
     */
    public boolean intersects(Rectangle2D rect) {
        return intersects(new Area(rect));
    }

    /**
     * Clears all accumulated areas.
     */
    public void clear() {
        accumulatedArea.reset();
    }

    /**
     * Returns whether the accumulated area is empty.
     *
     * @return true if no areas have been added or if cleared
     */
    public boolean isEmpty() {
        return accumulatedArea.isEmpty();
    }

    /**
     * Returns a copy of the accumulated area.
     *
     * @return A new Area containing the accumulated bounds
     */
    public Area getArea() {
        return new Area(accumulatedArea);
    }
}
