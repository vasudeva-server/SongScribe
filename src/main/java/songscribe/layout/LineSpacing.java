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
 * The vertical spacing constants that govern how staff lines are stacked in a song. A line's own
 * height covers just its own content; the gap between lines is owned solely by the layout manager
 * that positions the lines.
 */
public final class LineSpacing {

    public static final double MIN_INTER_LINE_GAP_SS = 2.0;  // 16px

    public static final double DEFAULT_INTER_LINE_GAP_SS = 4.0;

    public static final double LYRICS_ROW_MARGIN_SS = 0.75;  // 6px

    /**
     * The below-staff extent credited to a line with nothing below its staff, so its lyrics clear
     * the staff-bottom rule by {@link #LYRICS_ROW_MARGIN_SS} plus this. Deliberately a step rather
     * than a floor: a note dipping slightly below the staff puts its lyrics nearer the staff
     * bottom, because the row is then clearing note ink rather than the rule.
     */
    public static final double EMPTY_BELOW_STAFF_LYRIC_INSET_SS = 0.5;  // 4px

    /**
     * Floors a line's <em>bounds</em> only; kept out of the inter-line spacing computation, which
     * follows measured content.
     */
    public static final double MIN_ABOVE_MIDLINE_SS = Staff.STAFF_HALF_SS + Staff.MIN_ABOVE_STAFF_SS;

    public static final double MIN_BELOW_MIDLINE_SS = Staff.STAFF_HALF_SS + Staff.MIN_BELOW_STAFF_SS;

    /**
     * The minimum height of a line component. Carries no inter-line gap term.
     */
    public static final double MIN_LINE_HEIGHT_SS = MIN_ABOVE_MIDLINE_SS + MIN_BELOW_MIDLINE_SS;

    public static double interLineGapSs(double requestedGapSs) {
        return Math.max(requestedGapSs, MIN_INTER_LINE_GAP_SS);
    }

    private LineSpacing() {}
}
