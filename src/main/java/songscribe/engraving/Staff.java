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
 * Canonical staff geometry constants and staff-position/staff-space conversions.
 * <p>
 * All values are in staff-space units unless noted otherwise. Staff positions
 * are in half staff-space units, with 0 at the middle staff line and Y-down
 * orientation (smaller = higher on the page).
 */
public final class Staff {

    /** Height of the 5-line staff (4 gaps of 1 ss each) in staff spaces. */
    public static final double STAFF_HEIGHT_SS = 4.0;
    public static final double STAFF_HALF_SS = STAFF_HEIGHT_SS / 2.0;

    /**
     * Staff position offset: half of one staff space.
     * Used to convert between staff positions and Y coordinates.
     */
    public static final double STAFF_POSITION_OFFSET_SS = 0.5;

    /** Staff lines above middle line for ledger lines. */
    public static final int STAFF_LINES_ABOVE = 3;
    /** Minimum (highest-pitched) valid staff position, in half staff-space units. */
    public static final int MIN_STAFF_POSITION_SP = -(STAFF_LINES_ABOVE + 2) * 2;
    /** Minimum staff-space amount reserved above the staff top, derived from MIN_STAFF_POSITION_SP. */
    public static final double MIN_ABOVE_STAFF_SS =
        Math.abs(MIN_STAFF_POSITION_SP) * STAFF_POSITION_OFFSET_SS - STAFF_HALF_SS;

    /** Staff lines below middle line for ledger lines. */
    public static final int STAFF_LINES_BELOW = 4;
    /** Maximum (lowest-pitched) valid staff position, in half staff-space units. */
    public static final int MAX_STAFF_POSITION_SP = (STAFF_LINES_BELOW + 2) * 2;
    /** Minimum staff-space amount reserved below the staff bottom, derived from MAX_STAFF_POSITION_SP. */
    public static final double MIN_BELOW_STAFF_SS =
        MAX_STAFF_POSITION_SP * STAFF_POSITION_OFFSET_SS - STAFF_HALF_SS;

    private Staff() {}

    /** Converts a staff position (half staff-space units) to staff spaces. */
    public static double spToSs(int staffPositionSp) {
        return staffPositionSp * STAFF_POSITION_OFFSET_SS;
    }

    /** Converts staff spaces to a staff position (half staff-space units). */
    public static int ssToSp(double ss) {
        return (int) Math.round(ss / STAFF_POSITION_OFFSET_SS);
    }
}
