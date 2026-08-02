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
     * content of the next. A user-chosen gap is clamped to this by
     * {@link #interLineGapSs(double)}.
     */
    public static final double MIN_INTER_LINE_GAP_SS = 2.0;  // 16px

    /**
     * The vertical gap between the lowest content of one line and the highest content of
     * the next, used when the song does not specify one of its own.
     */
    public static final double DEFAULT_INTER_LINE_GAP_SS = 4.0;

    /**
     * The visual gap from the bottom of a line's below-staff content to the top of its
     * lyric row.
     */
    public static final double LYRICS_ROW_MARGIN_SS = 0.75;  // 6px

    /**
     * The below-staff extent a line is credited with when nothing on it reaches below the staff
     * at all, so its lyrics clear the staff bottom by {@link #LYRICS_ROW_MARGIN_SS} plus this
     * rather than by the margin alone.
     * <p>
     * What the lyric row sits under decides how close it may sit. Note ink — a stem, a notehead,
     * a downward tie — is thin and intermittent, and lyrics tolerate it at the bare margin. The
     * staff bottom is a hard rule running the full width of the line, and at that same margin it
     * reads as cramped. A line with nothing below its staff has only that rule beneath it, so the
     * row is pushed down by this much more.
     * <p>
     * The step is deliberately abrupt rather than a floor on the anchor: a note dipping slightly
     * below the staff puts its lyrics nearer the staff bottom than a line with no dip at all, and
     * that is correct, because by then the row is clearing note ink rather than the rule.
     */
    public static final double EMPTY_BELOW_STAFF_LYRIC_INSET_SS = 0.5;  // 4px

    /**
     * The least a line component may reach above its own staff midline: half the staff plus
     * the full legal staff-position range above it.
     * <p>
     * This floors a line's <em>bounds</em> only. It is deliberately kept out of the inter-line
     * spacing computation: spacing follows measured content, so a line whose ink stops at the
     * staff top still packs tightly against its neighbour, while its bounds stay large enough
     * that Swing does not clip the staff or its ledger lines. The hover preview is not a
     * consideration here — it is painted at page level, not by the line.
     */
    public static final double MIN_ABOVE_MIDLINE_SS = Staff.STAFF_HALF_SS + Staff.MIN_ABOVE_STAFF_SS;

    /** The below-midline counterpart of {@link #MIN_ABOVE_MIDLINE_SS}. */
    public static final double MIN_BELOW_MIDLINE_SS = Staff.STAFF_HALF_SS + Staff.MIN_BELOW_STAFF_SS;

    /**
     * The minimum height of a line component, used for a line with no content so its staff
     * still has room above and below for ledger lines. Carries no inter-line gap term —
     * that belongs to the layout manager.
     */
    public static final double MIN_LINE_HEIGHT_SS = MIN_ABOVE_MIDLINE_SS + MIN_BELOW_MIDLINE_SS;

    /**
     * The usable inter-line gap for a requested gap, floored at
     * {@link #MIN_INTER_LINE_GAP_SS} so no setting can pack lines tighter than the minimum.
     *
     * @param requestedGapSs the requested gap in staff spaces
     * @return the requested gap, or the minimum if the request falls below it
     */
    public static double interLineGapSs(double requestedGapSs) {
        return Math.max(requestedGapSs, MIN_INTER_LINE_GAP_SS);
    }

    private LineSpacing() {}
}
