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

import songscribe.engraving.Staff;

/**
 * The vertical spacing constants that govern how staff lines are stacked in a song.
 * <p>
 * These are deliberately the <em>only</em> inter-line spacing constants in the codebase.
 * A line's own height covers just its own content; the gap between lines is owned solely
 * by the layout manager that positions the lines, never baked into a line's height.
 */
public final class LineSpacing {

    /**
     * The minimum vertical gap between the lowest content of one line and the highest
     * content of the next.
     */
    public static final double MIN_INTER_LINE_GAP_SS = 2.0;  // 16px

    /**
     * The visual gap from the bottom of a line's below-staff content to the top of its
     * first verse row.
     */
    public static final double LYRICS_ROW_MARGIN_SS = 0.5;  // 8px

    /**
     * The minimum height of a line component, used for a line with no content so its staff
     * still has room above and below for ledger lines. Carries no inter-line gap term —
     * that belongs to the layout manager.
     */
    public static final double MIN_LINE_HEIGHT_SS =
        Staff.STAFF_HEIGHT_SS + Staff.MIN_ABOVE_STAFF_SS + Staff.MIN_BELOW_STAFF_SS;

    private LineSpacing() {}
}
