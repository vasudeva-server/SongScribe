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
 * A discrete vertical position a note head may occupy on the staff, in half staff spaces
 * from the middle staff line, Y-down: 0 is the middle line, negative is higher.
 * <p>
 * Every instance lies within {@link #MIN}..{@link #MAX}, the range reachable with the ledger
 * lines the renderer draws; no caller range-checks a position.
 */
public record StaffPosition(int value) {

    private static final int LEDGER_LINES_ABOVE = 3;

    private static final int LEDGER_LINES_BELOW = 4;

    static final int POSITIONS_PER_STAFF_LINE = 2;

    private static final int STAFF_LINES_FROM_MIDDLE_TO_OUTER = 2;

    public static final int OUTERMOST_STAFF_LINE_SP =
        STAFF_LINES_FROM_MIDDLE_TO_OUTER * POSITIONS_PER_STAFF_LINE;

    private static final int OUTERMOST_UNLEDGERED_SP = OUTERMOST_STAFF_LINE_SP + 1;

    /**
     * Plain {@code int}, so the canonical constructor can check against it while {@link #MIN}
     * is still being initialized.
     */
    private static final int MIN_VALUE_SP =
        -(STAFF_LINES_FROM_MIDDLE_TO_OUTER + LEDGER_LINES_ABOVE) * POSITIONS_PER_STAFF_LINE;

    private static final int MAX_VALUE_SP =
        (STAFF_LINES_FROM_MIDDLE_TO_OUTER + LEDGER_LINES_BELOW) * POSITIONS_PER_STAFF_LINE;

    /**
     * The highest-pitched valid position, on the outermost ledger line above the staff. A higher
     * pitch has no position: the renderer draws no further ledger line.
     */
    public static final StaffPosition MIN = new StaffPosition(MIN_VALUE_SP);

    /**
     * The lowest-pitched valid position, on the outermost ledger line below the staff. A lower
     * pitch has no position: the renderer draws no further ledger line.
     */
    public static final StaffPosition MAX = new StaffPosition(MAX_VALUE_SP);

    public StaffPosition {
        if (value < MIN_VALUE_SP || value > MAX_VALUE_SP) {
            throw new PitchOutOfRangeException(value);
        }
    }

    /**
     * {@link #MIN}'s and {@link #MAX}'s results are not negatives of each other —
     * the two bounds are not symmetric about the middle line.
     */
    public double toSs() {
        return value * Staff.HALF_SPACE_SS;
    }

    public boolean isOnLine() {
        return value % POSITIONS_PER_STAFF_LINE == 0;
    }

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
