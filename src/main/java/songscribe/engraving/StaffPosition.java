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
 * A discrete vertical position a note head may occupy on the staff, held in half staff spaces
 * from the middle staff line, Y-down: 0 is the middle line, negative is higher on the page, and
 * consecutive positions alternate between a line and the space adjacent to it.
 * <p>
 * The compact constructor is the only way to obtain an instance, and it refuses anything off
 * the grid {@link #MIN}..{@link #MAX} — the range reachable with the ledger lines the renderer
 * draws — so the invariant cannot be bypassed and no caller range-checks a position again.
 * <p>
 * The half staff space itself is a length, and lives on {@link Staff} as
 * {@link Staff#HALF_SPACE_SS}; a distance measured in half staff spaces converts through
 * {@link Staff#halfSpacesToSs(int)} rather than through this type.
 *
 * @param value the position, in half staff spaces from the middle staff line, Y-down
 * @throws PitchOutOfRangeException if {@code value} lies outside {@link #MIN}..{@link #MAX}
 */
public record StaffPosition(int value) {

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
     * The plain-{@code int} form of {@link #MIN}'s value, kept separate so the canonical
     * constructor can check against it without constructing a {@link StaffPosition} of its
     * own while {@link #MIN} is itself still being initialized.
     */
    private static final int MIN_VALUE_SP =
        -(STAFF_LINES_FROM_MIDDLE_TO_OUTER + LEDGER_LINES_ABOVE) * POSITIONS_PER_STAFF_LINE;

    /** The plain-{@code int} form of {@link #MAX}'s value; see {@link #MIN_VALUE_SP}. */
    private static final int MAX_VALUE_SP =
        (STAFF_LINES_FROM_MIDDLE_TO_OUTER + LEDGER_LINES_BELOW) * POSITIONS_PER_STAFF_LINE;

    /**
     * Minimum (highest-pitched) valid staff position. A note here sits on the outermost ledger
     * line above the staff. A higher pitch is musically ordinary but has no position on this
     * grid, because the renderer draws no further ledger line to put it on.
     */
    public static final StaffPosition MIN = new StaffPosition(MIN_VALUE_SP);

    /**
     * Maximum (lowest-pitched) valid staff position. A note here sits on the outermost ledger
     * line below the staff. A lower pitch is musically ordinary but has no position on this
     * grid, because the renderer draws no further ledger line to put it on.
     */
    public static final StaffPosition MAX = new StaffPosition(MAX_VALUE_SP);

    public StaffPosition {
        if (value < MIN_VALUE_SP || value > MAX_VALUE_SP) {
            throw new PitchOutOfRangeException(value);
        }
    }

    /**
     * Converts this position to its signed distance in staff spaces from the middle staff
     * line, positive downwards.
     *
     * @return the distance from the middle staff line in staff spaces, negative above it
     * @invariant the result is an exact multiple of {@link Staff#HALF_SPACE_SS}
     * @invariant {@link #MIN}'s and {@link #MAX}'s results are not negatives of each other —
     *            the two bounds are not symmetric about the middle line
     */
    public double toSs() {
        return value * Staff.HALF_SPACE_SS;
    }

    /**
     * Returns whether this position falls on a line of the grid rather than in a space,
     * counting the ledger lines outside the staff as lines.
     * <p>
     * Positions alternate line, space, line, space outward from the middle line in
     * both directions, so this is a property of the position alone and holds however
     * far off the staff it lies.
     *
     * @return {@code true} when a note head at this position sits on a line
     */
    public boolean isOnLine() {
        return value % POSITIONS_PER_STAFF_LINE == 0;
    }

    /**
     * Returns whether a note head at this position has to be carried on ledger lines, being
     * further from the middle line than the staff itself reaches.
     * <p>
     * The staff's own five lines and the space just outside each outer line need
     * none. Everything beyond that does, one ledger line for every staff line
     * between the note and the staff.
     *
     * @return {@code true} when the position lies outside the staff and the space
     *         adjacent to it, either side
     */
    public boolean needsLedgerLines() {
        return Math.abs(value) > OUTERMOST_UNLEDGERED_SP;
    }

    /**
     * Converts a distance in staff spaces from the middle staff line to the nearest staff
     * position, or {@code null} when the distance rounds off the grid entirely.
     * <p>
     * A distance that falls between two positions snaps to the nearer of them, and a
     * distance exactly halfway snaps to the lower position — the one further down the
     * page — because rounding is half-up in the Y-down direction. A distance whose rounded
     * position falls outside {@link #MIN}..{@link #MAX} yields {@code null}, naming
     * that it has left the pitch range entirely rather than returning a plausible but wrong
     * position.
     *
     * @param ss the distance from the middle staff line in staff spaces, negative above it
     * @return the staff position nearest {@code ss}, or {@code null} when it rounds outside
     *         {@link #MIN}..{@link #MAX}
     * @invariant a non-null result is always within {@link #MIN}..{@link #MAX}
     */
    public static @Nullable StaffPosition atSs(double ss) {
        var rounded = Math.round(ss / Staff.HALF_SPACE_SS);

        if (rounded < MIN_VALUE_SP || rounded > MAX_VALUE_SP) {
            return null;
        }

        return new StaffPosition((int) rounded);
    }
}
