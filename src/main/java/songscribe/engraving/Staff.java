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

package songscribe.engraving;

/**
 * Canonical geometry of the 5-line staff, and the half staff space. Values are in staff
 * spaces, Y-down from the middle staff line.
 */
public final class Staff {

    public static final double STAFF_HEIGHT_SS = 4.0;
    public static final double STAFF_HALF_SS = STAFF_HEIGHT_SS / 2.0;
    public static final double HALF_SPACE_SS = 0.5;
    public static final double MIN_ABOVE_STAFF_SS =
        Math.abs(StaffPosition.MIN.value()) * HALF_SPACE_SS - STAFF_HALF_SS;
    public static final double MIN_BELOW_STAFF_SS = StaffPosition.MAX.value() * HALF_SPACE_SS - STAFF_HALF_SS;

    private Staff() {}

    /**
     * @param halfSpaces a distance, never a staff position; a position converts through
     *                   {@link StaffPosition#toSs()}
     */
    public static double halfSpacesToSs(int halfSpaces) {
        return halfSpaces * HALF_SPACE_SS;
    }

    /**
     * Converts a distance in staff spaces to the nearest whole count of half staff
     * spaces.
     * <p>
     * The argument is a distance — a drag's vertical travel, an engraving offset —
     * never a distance measured from the middle staff line. One measured from the
     * middle line names a position and converts through
     * {@link StaffPosition#atSs(double)}, which additionally answers that a value
     * off the position grid is no position at all. Range-checking a distance would be
     * wrong: a distance is not bounded by the grid, and whatever bound applies to it
     * depends on where it is applied from, which only its caller knows.
     * <p>
     * A distance that falls between two half spaces snaps to the nearer of them, and
     * one exactly halfway snaps toward positive — further down the page — matching
     * {@link StaffPosition#atSs(double)} so that a count and a position resolve a
     * boundary the same way.
     *
     * @param ss a distance in staff spaces
     * @return that distance as a count of half staff spaces, with no bound applied
     * @invariant {@code ssToHalfSpaces(halfSpacesToSs(n))} is {@code n} for any
     *            {@code n}
     * @invariant the result is monotonic in {@code ss}: a larger distance never
     *            yields a smaller count
     */
    public static int ssToHalfSpaces(double ss) {
        return (int) Math.round(ss / HALF_SPACE_SS);
    }
}
