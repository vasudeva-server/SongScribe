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

import java.util.Arrays;

import songscribe.engraving.Staff;

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
 *   <li>{@link #yGet} to find the current extent at a horizontal range, or
 *       {@link #contact} for the tagged extent (including the neighbor type)</li>
 *   <li>Position the element relative to that extent (applying a margin)</li>
 *   <li>{@link #ySet} to reserve the space the element occupies, tagged with
 *       the {@link Neighbor} that now occupies it</li>
 * </ol>
 */
public class StaffExtents {

    /**
     * Number of horizontal steps in the y-extent array used for collision detection.
     * Matches abc2svg's step count. Each step covers lineWidth / YSTEP staff-space units.
     */
    public static final int YSTEP = 128;
    private final double[] top;
    private final double[] bot;
    private final Neighbor[] topTag;
    private final Neighbor[] botTag;
    private final double lineWidthSs;

    /**
     * Creates a new StaffExtents with default initialization.
     * <p>
     * Both numeric arrays default to the middle staff line (0.0 ss), matching the Y-down,
     * middle-line-relative coordinate system used throughout layout. The tag arrays default to
     * {@link Neighbor#STAFF_LINE}; the staff-edge floor itself is supplied by {@link #contact}, not
     * by this baseline.
     *
     * @param lineWidthSs total width of the staff line in staff-space units
     */
    public StaffExtents(double lineWidthSs) {
        this.lineWidthSs = lineWidthSs;
        top = new double[YSTEP];
        bot = new double[YSTEP];
        topTag = new Neighbor[YSTEP];
        botTag = new Neighbor[YSTEP];
        Arrays.fill(topTag, Neighbor.STAFF_LINE);
        Arrays.fill(botTag, Neighbor.STAFF_LINE);
    }

    /**
     * Reserves vertical space at the given horizontal range, tagged with
     * {@link Neighbor#STAFF_LINE}.
     * <p>
     * Delegates to the tagged {@link #ySet(boolean, double, double, double, Neighbor)}
     * overload; see that method for the update rule.
     *
     * @param above   true to reserve above-staff space, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @param ySs     the Y extent to reserve in staff-space units
     */
    public void ySet(boolean above, double xSs, double widthSs, double ySs) {
        ySet(above, xSs, widthSs, ySs, Neighbor.STAFF_LINE);
    }

    /**
     * Reserves vertical space at the given horizontal range, tagging each step
     * whose extent is replaced with the given {@link Neighbor}.
     * <p>
     * For above ({@code above=true}): updates {@code top[i]} to the minimum of
     * its current value and {@code ySs} (Y-down: smaller = higher on the page).
     * <p>
     * For below ({@code above=false}): updates {@code bot[i]} to the maximum of
     * its current value and {@code ySs} (Y-down: larger = lower on the page).
     * <p>
     * A step's tag is overwritten whenever {@code ySs} strictly wins against
     * the step's current value, or ties it — a tie means the last writer's
     * tag wins, matching the numeric update being a no-op on ties.
     *
     * @param above   true to reserve above-staff space, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @param ySs     the Y extent to reserve in staff-space units
     * @param tag     the neighbor now occupying the reserved steps
     */
    public void ySet(boolean above, double xSs, double widthSs, double ySs, Neighbor tag) {
        var startStep = xToStep(xSs);
        var endStep = xToStep(xSs + widthSs);

        if (above) {
            for (var i = startStep; i <= endStep; i++) {
                if (ySs <= top[i]) {
                    top[i] = ySs;
                    topTag[i] = tag;
                }
            }
        }
        else {
            for (var i = startStep; i <= endStep; i++) {
                if (ySs >= bot[i]) {
                    bot[i] = ySs;
                    botTag[i] = tag;
                }
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
     * <p>
     * Walks the raw arrays, whose {@code 0.0} default means "empty" to untagged callers. Unlike
     * {@link #contact}, it does not fall back to the staff edge, so an empty region reports
     * {@code 0.0} rather than the staff line.
     *
     * @param above   true to query above-staff extent, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @return the extreme Y value across the queried range, in staff-space units
     */
    public double yGet(boolean above, double xSs, double widthSs) {
        var startStep = xToStep(xSs);
        var endStep = xToStep(xSs + widthSs);

        if (above) {
            var minY = top[startStep];

            for (var i = startStep + 1; i <= endStep; i++) {
                minY = Math.min(minY, top[i]);
            }

            return minY;
        }

        var maxY = bot[startStep];

        for (var i = startStep + 1; i <= endStep; i++) {
            maxY = Math.max(maxY, bot[i]);
        }

        return maxY;
    }

    /**
     * A tagged vertical contact point: the extreme Y value across a queried
     * range, and the {@link Neighbor} occupying the step where it occurs.
     *
     * @param ySs the extreme Y value, in staff-space units
     * @param tag the neighbor occupying the step where {@code ySs} occurs
     */
    public record Contact(double ySs, Neighbor tag) {}

    /**
     * Queries the tagged vertical contact at the given horizontal range —
     * the most-outward reservation, or the outer staff line itself if nothing
     * in range protrudes past it.
     * <p>
     * Seeds the argmax with the staff edge as a built-in {@link Neighbor#STAFF_LINE} candidate
     * ({@code above ? -Staff.STAFF_HALF_SS : Staff.STAFF_HALF_SS}) before walking the step range, so it always
     * has a floor. Empty steps hold the numeric default {@code 0.0}, which is less outward than the
     * staff edge and so never wins: an empty region returns the staff edge, and a real reservation
     * wins only when it protrudes past the edge, reporting the tag of the most-outward step.
     *
     * @param above   true to query above-staff extent, false for below-staff
     * @param xSs     horizontal start position in staff-space units
     * @param widthSs horizontal width in staff-space units
     * @return the most-outward {@code (ySs, tag)} pair across the queried range
     */
    public Contact contact(boolean above, double xSs, double widthSs) {
        var startStep = xToStep(xSs);
        var endStep = xToStep(xSs + widthSs);

        if (above) {
            var bestY = -Staff.STAFF_HALF_SS;
            var bestTag = Neighbor.STAFF_LINE;

            for (var i = startStep; i <= endStep; i++) {
                if (top[i] < bestY) {
                    bestY = top[i];
                    bestTag = topTag[i];
                }
            }

            return new Contact(bestY, bestTag);
        }

        var bestY = Staff.STAFF_HALF_SS;
        var bestTag = Neighbor.STAFF_LINE;

        for (var i = startStep; i <= endStep; i++) {
            if (bot[i] > bestY) {
                bestY = bot[i];
                bestTag = botTag[i];
            }
        }

        return new Contact(bestY, bestTag);
    }

    /**
     * Copies the top extent array (and its tags) from another
     * {@code StaffExtents} instance. Used when initializing a higher tier
     * from a lower tier's reservations (e.g., structural layer starts from
     * note-attached layer's top extents).
     *
     * @param source the instance to copy top extents from
     */
    public void copyTopFrom(StaffExtents source) {
        System.arraycopy(source.top, 0, top, 0, YSTEP);
        System.arraycopy(source.topTag, 0, topTag, 0, YSTEP);
    }

    /**
     * Converts a horizontal position in staff-space units to a step index,
     * clamped to the valid range [0, YSTEP - 1].
     */
    private int xToStep(double xSs) {
        var step = (int) (xSs * YSTEP / lineWidthSs);
        return Math.clamp(step, 0, YSTEP - 1);
    }
}
