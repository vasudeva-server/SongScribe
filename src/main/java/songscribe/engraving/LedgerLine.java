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

import java.util.function.DoubleConsumer;

/**
 * Which ledger lines a note head needs, where each sits relative to it, and how each
 * is drawn, in staff spaces.
 */
public final class LedgerLine {
    private static final double LEDGER_LINE_MULTIPLIER = 2.0;

    public static final double THICKNESS_SS =
        EngravingConstants.LILYPOND_BASE_THICKNESS_SS * LEDGER_LINE_MULTIPLIER;

    /**
     * Fraction of the notehead width each ledger line extends beyond it on each side; LilyPond's
     * {@code length-fraction} default.
     */
    public static final double LENGTH_FRACTION = 0.25;

    private LedgerLine() {}

    /**
     * Passes each ledger line a note head at {@code staffPosition} needs to
     * {@code consumer}, as a Y offset in staff spaces from that note head, working
     * inward from the line furthest from the staff.
     * <p>
     * A note on a line takes the run of lines from its own outward; a note in a space
     * takes the same run starting from the line between it and the staff, which is why
     * its offsets are half a staff space out of step with the note. A note the staff
     * reaches gets nothing.
     *
     * @param staffPosition the note head's staff position
     * @param consumer      called once per ledger line, furthest from the staff first
     * @invariant nothing is passed unless
     *            {@link StaffPosition#needsLedgerLines()} holds for {@code staffPosition}
     * @invariant every offset is an exact multiple of {@link Staff#HALF_SPACE_SS}, and
     *            successive offsets differ by one staff space
     */
    public static void forEachOffsetSs(StaffPosition staffPosition, DoubleConsumer consumer) {
        var towardStaff = staffPosition.value() > 0 ? -1 : 1;
        var linePosition = staffPosition.isOnLine()
            ? staffPosition
            : new StaffPosition(staffPosition.value() + towardStaff);

        while (linePosition.needsLedgerLines()) {
            consumer.accept(Staff.halfSpacesToSs(linePosition.value() - staffPosition.value()));
            linePosition = new StaffPosition(linePosition.value() + towardStaff * StaffPosition.POSITIONS_PER_STAFF_LINE);
        }
    }
}
