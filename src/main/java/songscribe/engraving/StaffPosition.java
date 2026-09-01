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

import org.jspecify.annotations.Nullable;

/**
 * The staff's pitch grid: the discrete vertical positions a note head may occupy,
 * their bounds, and the conversion between a position and a distance from the
 * middle staff line.
 * <p>
 * A staff position is an {@code int} counted in half staff spaces from the middle
 * staff line, Y-down: 0 is the middle line, negative is higher on the page, and
 * consecutive positions alternate between a line and the space adjacent to it.
 * Positions run from {@link #MIN_SP} to {@link #MAX_SP} inclusive, the range
 * reachable with the ledger lines the renderer draws.
 * <p>
 * The half staff space itself is a length, and lives on {@link Staff} as
 * {@link Staff#HALF_SPACE_SS}; a distance measured in half staff spaces converts
 * through {@link Staff#halfSpacesToSs(int)} rather than through this class.
 */
public final class StaffPosition {

    /** Ledger lines the renderer draws above the staff. */
    private static final int LEDGER_LINES_ABOVE = 3;

    /** Ledger lines the renderer draws below the staff. */
    private static final int LEDGER_LINES_BELOW = 4;

    /**
     * Staff positions spanned by one staff line and the space adjacent to it, which
     * is also the step from one line of the grid to the next.
     */
    static final int POSITIONS_PER_STAFF_LINE = 2;

    /**
     * Staff lines between the middle line and the staff's outer line, either side of
     * it: the 5-line staff has two above the middle line and two below.
     */
    private static final int STAFF_LINES_FROM_MIDDLE_TO_OUTER = 2;

    /** Staff position of the staff's outer line, either side of the middle line. */
    public static final int OUTERMOST_STAFF_LINE_SP =
        STAFF_LINES_FROM_MIDDLE_TO_OUTER * POSITIONS_PER_STAFF_LINE;

    /**
     * The outermost position, either side of the middle line, a note head may occupy
     * without a ledger line: the space adjacent to the staff's outer line.
     */
    private static final int OUTERMOST_UNLEDGERED_SP = OUTERMOST_STAFF_LINE_SP + 1;

    /**
     * Minimum (highest-pitched) valid staff position, in half staff-space units. A
     * note here sits on the outermost ledger line above the staff; nothing lies past
     * it.
     */
    public static final int MIN_SP =
        -(STAFF_LINES_FROM_MIDDLE_TO_OUTER + LEDGER_LINES_ABOVE) * POSITIONS_PER_STAFF_LINE;

    /**
     * Maximum (lowest-pitched) valid staff position, in half staff-space units. A note
     * here sits on the outermost ledger line below the staff; nothing lies past it.
     */
    public static final int MAX_SP =
        (STAFF_LINES_FROM_MIDDLE_TO_OUTER + LEDGER_LINES_BELOW) * POSITIONS_PER_STAFF_LINE;

    private StaffPosition() {}

    /**
     * Converts a staff position to its signed distance in staff spaces from the
     * middle staff line, positive downwards.
     * <p>
     * Any {@code int} is accepted: the conversion is pure arithmetic and does not
     * range-check, so a caller holding a position it has not yet validated against
     * {@link #MIN_SP}..{@link #MAX_SP} gets the arithmetically consistent answer
     * rather than an exception. Validation, where it is wanted, is the caller's.
     *
     * @param staffPositionSp a staff position in half staff spaces from the middle line
     * @return the distance from the middle staff line in staff spaces, negative above it
     * @invariant the result is an exact multiple of {@link Staff#HALF_SPACE_SS}
     * @invariant {@code toSs(0)} is 0, and {@code toSs(-p)} is {@code -toSs(p)}
     * @invariant for any position in {@link #MIN_SP}..{@link #MAX_SP},
     *            {@code atSs(toSs(p)) == p}
     */
    public static double toSs(int staffPositionSp) {
        return staffPositionSp * Staff.HALF_SPACE_SS;
    }

    /**
     * Converts a distance in staff spaces from the middle staff line to the nearest staff
     * position, or {@code null} when the distance rounds off the grid entirely.
     * <p>
     * A distance that falls between two positions snaps to the nearer of them, and a
     * distance exactly halfway snaps to the lower position — the one further down the
     * page — because rounding is half-up in the Y-down direction. A distance whose rounded
     * position falls outside {@link #MIN_SP}..{@link #MAX_SP} yields {@code null}, naming
     * that it has left the pitch range entirely rather than returning a plausible but wrong
     * position.
     *
     * @param ss the distance from the middle staff line in staff spaces, negative above it
     * @return the staff position nearest {@code ss}, or {@code null} when it rounds outside
     *         {@link #MIN_SP}..{@link #MAX_SP}
     * @invariant a non-null result is always within {@link #MIN_SP}..{@link #MAX_SP}
     */
    public static @Nullable Integer atSs(double ss) {
        var positionSp = roundToSp(ss);

        if (positionSp < MIN_SP || positionSp > MAX_SP) {
            return null;
        }

        return (int) positionSp;
    }

    /**
     * Returns whether a staff position falls on a line of the grid rather than in a
     * space, counting the ledger lines outside the staff as lines.
     * <p>
     * Positions alternate line, space, line, space outward from the middle line in
     * both directions, so this is a property of the position alone and holds however
     * far off the staff it lies.
     *
     * @param staffPositionSp a staff position in half staff spaces from the middle line
     * @return {@code true} when a note head at this position sits on a line
     */
    public static boolean isOnLine(int staffPositionSp) {
        return staffPositionSp % POSITIONS_PER_STAFF_LINE == 0;
    }

    /**
     * Returns whether a note head at this staff position has to be carried on ledger
     * lines, being further from the middle line than the staff itself reaches.
     * <p>
     * The staff's own five lines and the space just outside each outer line need
     * none. Everything beyond that does, one ledger line for every staff line
     * between the note and the staff.
     *
     * @param staffPositionSp a staff position in half staff spaces from the middle line
     * @return {@code true} when the position lies outside the staff and the space
     *         adjacent to it, either side
     */
    public static boolean needsLedgerLines(int staffPositionSp) {
        return Math.abs(staffPositionSp) > OUTERMOST_UNLEDGERED_SP;
    }

    /**
     * Rounds a distance from the middle staff line to the nearest position on the
     * grid, without clamping, so that the result can be tested against the bounds.
     */
    private static long roundToSp(double ss) {
        return Math.round(ss / Staff.HALF_SPACE_SS);
    }
}
