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

import java.util.Arrays;

/**
 * Segmented y-extent array for above/below-staff collision detection.
 * <p>
 * Ported from abc2svg's {@code y_get}/{@code y_set} approach. The horizontal
 * span of a staff line is divided into {@link StaffExtents#YSTEP} equal steps.
 * Two arrays track the highest occupied Y (above) and lowest occupied Y (below)
 * at each step. All values are in staff-space units with Y-down orientation
 * (smaller Y = higher on the page).
 * <p>
 * Typical usage:
 * <ol>
 *   <li>{@link #yGet} to find the current extent at a horizontal range</li>
 *   <li>Position the element relative to that extent (applying a margin)</li>
 *   <li>{@link #ySet} to reserve the space the element occupies</li>
 * </ol>
 */
public class StaffExtents {

    /**
     * Number of horizontal steps in the y-extent array used for collision detection.
     * Matches abc2svg's step count. Each step covers lineWidth / YSTEP staff-space units.
     */
    public static final int YSTEP = 128;
    /**
     * Height of 5-line staff (4 gaps of 1 ss each)
     */
    public static final double STAFF_HEIGHT_SS = 4.0;  // 32px
    public static final double STAFF_HALF_SS = STAFF_HEIGHT_SS / 2.0;
    /**
     * Staff position offset: half of one staff space.
     * Used to convert between staff positions and Y coordinates.
     */
    public static final double STAFF_POSITION_OFFSET_SS = 0.5;  // 4px
    /**
     * Staff lines above middle line for ledger lines
     */
    public static final int STAFF_LINES_ABOVE = 3;
    /** Minimum (highest-pitched) valid staff position, in half staff-space units. */
    public static final int MIN_STAFF_POSITION_SP = -(STAFF_LINES_ABOVE + 2) * 2;
    /** Minimum staff-space amount reserved above the staff top, derived from MIN_STAFF_POSITION_SP. */
    public static final double MIN_ABOVE_STAFF_SS =
        Math.abs(MIN_STAFF_POSITION_SP) * STAFF_POSITION_OFFSET_SS - STAFF_HALF_SS;
    /**
     * Staff lines below middle line for ledger lines
     */
    public static final int STAFF_LINES_BELOW = 4;
    /** Maximum (lowest-pitched) valid staff position, in half staff-space units. */
    public static final int MAX_STAFF_POSITION_SP = (STAFF_LINES_BELOW + 2) * 2;
    /** Minimum staff-space amount reserved below the staff bottom, derived from MAX_STAFF_POSITION_SP. */
    public static final double MIN_BELOW_STAFF_SS =
        MAX_STAFF_POSITION_SP * STAFF_POSITION_OFFSET_SS - STAFF_HALF_SS;
    private final double[] top;
    private final double[] bot;
    private final double lineWidthSs;

    /**
     * Creates a new StaffExtents with default initialization.
     * <p>
     * The top array is initialized to staff top (0.0 ss) and the bottom array
     * to staff bottom ({@link StaffExtents#STAFF_HEIGHT_SS}).
     *
     * @param lineWidthSs total width of the staff line in staff-space units
     */
    public StaffExtents(double lineWidthSs) {
        this.lineWidthSs = lineWidthSs;
        this.top = new double[YSTEP];
        this.bot = new double[YSTEP];
        Arrays.fill(bot, STAFF_HEIGHT_SS);
    }

    /** Converts a staff position (half staff-space units) to staff spaces. */
    public static double spToSs(int staffPositionSp) {
        return staffPositionSp * STAFF_POSITION_OFFSET_SS;
    }

    /** Converts staff spaces to a staff position (half staff-space units). */
    public static int ssToSp(double ss) {
        return (int) Math.round(ss / STAFF_POSITION_OFFSET_SS);
    }

    /**
     * Reserves vertical space at the given horizontal range.
     * <p>
     * For above ({@code above=true}): updates {@code top[i]} to the minimum of
     * its current value and {@code ySs} (Y-down: smaller = higher on the page).
     * <p>
     * For below ({@code above=false}): updates {@code bot[i]} to the maximum of
     * its current value and {@code ySs} (Y-down: larger = lower on the page).
     *
     * @param above   true to reserve above-staff space, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @param ySs     the Y extent to reserve in staff-space units
     */
    public void ySet(boolean above, double xSs, double widthSs, double ySs) {
        int startStep = xToStep(xSs);
        int endStep = xToStep(xSs + widthSs);

        if (above) {
            for (int i = startStep; i <= endStep; i++) {
                top[i] = Math.min(top[i], ySs);
            }
        }
        else {
            for (int i = startStep; i <= endStep; i++) {
                bot[i] = Math.max(bot[i], ySs);
            }
        }
    }

    /**
     * Queries the current vertical extent at the given horizontal range.
     * <p>
     * For above ({@code above=true}): returns the minimum of {@code top[i]}
     * across the range (the highest occupied point, Y-down).
     * <p>
     * For below ({@code above=false}): returns the maximum of {@code bot[i]}
     * across the range (the lowest occupied point, Y-down).
     *
     * @param above   true to query above-staff extent, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @return the extreme Y value across the queried range, in staff-space units
     */
    public double yGet(boolean above, double xSs, double widthSs) {
        int startStep = xToStep(xSs);
        int endStep = xToStep(xSs + widthSs);

        if (above) {
            double minY = top[startStep];

            for (int i = startStep + 1; i <= endStep; i++) {
                minY = Math.min(minY, top[i]);
            }

            return minY;
        }
        else {
            double maxY = bot[startStep];

            for (int i = startStep + 1; i <= endStep; i++) {
                maxY = Math.max(maxY, bot[i]);
            }

            return maxY;
        }
    }

    /**
     * Copies the top extent array from another {@code StaffExtents} instance.
     * Used when initializing a higher tier from a lower tier's reservations
     * (e.g., structural layer starts from note-attached layer's top extents).
     *
     * @param source the instance to copy top extents from
     */
    public void copyTopFrom(StaffExtents source) {
        System.arraycopy(source.top, 0, top, 0, YSTEP);
    }

    /**
     * Converts a horizontal position in staff-space units to a step index,
     * clamped to the valid range [0, YSTEP - 1].
     */
    private int xToStep(double xSs) {
        int step = (int) (xSs * YSTEP / lineWidthSs);
        return Math.clamp(step, 0, YSTEP - 1);
    }
}
