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
 * Canonical geometry of the 5-line staff, and the half staff space, the unit in
 * which vertical distances on it are counted.
 * <p>
 * All values are in staff-space units unless noted otherwise, measured Y-down
 * from the middle staff line, so a negative distance is higher on the page.
 * <p>
 * The pitch grid this geometry carries — the discrete positions a note head may
 * occupy, their bounds, and the conversion between a position and a distance —
 * belongs to {@link StaffPosition}.
 */
public final class Staff {

    /** Height of the 5-line staff (4 gaps of 1 ss each) in staff spaces. */
    public static final double STAFF_HEIGHT_SS = 4.0;
    public static final double STAFF_HALF_SS = STAFF_HEIGHT_SS / 2.0;

    /**
     * Half of one staff space, the unit in which vertical distances on the staff
     * are counted: the gap between a staff line and the space adjacent to it.
     */
    public static final double HALF_SPACE_SS = 0.5;

    /** Minimum staff-space amount reserved above the staff top, derived from {@link StaffPosition#MIN_SP}. */
    public static final double MIN_ABOVE_STAFF_SS =
        Math.abs(StaffPosition.MIN_SP) * HALF_SPACE_SS - STAFF_HALF_SS;

    /** Minimum staff-space amount reserved below the staff bottom, derived from {@link StaffPosition#MAX_SP}. */
    public static final double MIN_BELOW_STAFF_SS = StaffPosition.MAX_SP * HALF_SPACE_SS - STAFF_HALF_SS;

    private Staff() {}

    /**
     * Converts a count of half staff spaces to staff spaces.
     * <p>
     * The argument is a distance — a difference between two staff positions, or an
     * offset expressed in half staff spaces — never a staff position itself. A
     * position is converted by {@link StaffPosition#toSs(int)}. The two are the same
     * arithmetic, and only the name says which of them a call site means, so a site
     * that measures from the middle staff line belongs in the other method however
     * well this one compiles.
     *
     * @param halfSpaces a distance in half staff spaces
     * @return that distance in staff spaces
     * @invariant the result is an exact multiple of {@link #HALF_SPACE_SS}
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
