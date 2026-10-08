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

package songscribe.dom;

/**
 * The root of everything drawn on or around a staff line: a {@link StaffElement} occupying a
 * column, what hangs on one — an {@link Articulation}, an {@link Attachment} — a {@link Span}
 * joining two columns, and the song-level {@link SongTempoMark} and {@link Attribution}. What
 * all of them share is the notator's manual offset from wherever layout puts them. An element's
 * position itself lives in the layout result, not on the element.
 *
 * <p>Which line an element is in is not stated here, because only part of the hierarchy is in
 * a line's element tree, and each part answers it its own way: a staff element holds its line
 * ({@link StaffElement#getParentLine()}), a child derives it from its host
 * ({@link Attachment#getParentLine()}, {@link Articulation#getParentLine()}), and a span derives
 * the lines it is in from its endpoints.
 *
 * <p><b>The offsets.</b> Where the notator dragged the element from the position layout gave it,
 * in staff spaces: X positive to the right, Y positive downward, both 0 until adjusted. The
 * drawn position is the calculated position plus the offset.
 */
public abstract class LineElement {

    private double userXOffsetSs = 0;

    private double userYOffsetSs = 0;

    // ========================================================================
    // User Offsets
    // ========================================================================

    /**
     * Returns the notator's horizontal offset from the calculated position.
     *
     * @return the offset in staff spaces, positive to the right; 0 until adjusted
     */
    public double getUserXOffsetSs() {
        return userXOffsetSs;
    }

    /**
     * Sets the notator's horizontal offset from the calculated position.
     *
     * @param userXOffsetSs the offset in staff spaces, positive to the right
     */
    public void setUserXOffsetSs(double userXOffsetSs) {
        this.userXOffsetSs = userXOffsetSs;
    }

    /**
     * Returns the notator's vertical offset from the calculated position.
     *
     * @return the offset in staff spaces, positive downward; 0 until adjusted
     */
    public double getUserYOffsetSs() {
        return userYOffsetSs;
    }

    /**
     * Sets the notator's vertical offset from the calculated position.
     *
     * @param userYOffsetSs the offset in staff spaces, positive downward
     */
    public void setUserYOffsetSs(double userYOffsetSs) {
        this.userYOffsetSs = userYOffsetSs;
    }

    // ========================================================================
    // State Copying
    // ========================================================================

    /**
     * Copies this class's own state — the two user offsets — from {@code source} onto this
     * element.
     * <p>
     * Every route that copies one element's state onto another ends up here, so a field added
     * to this class is carried by all of them at once and no route can omit it. The routes are
     * {@link StaffElement#copyStateFrom}, {@link StaffElement#convertedFrom},
     * {@link Span#copy}, {@link Attachment#copy}, {@link Articulation#copy} and the paste
     * reconciliation in {@code PasteSpanReconciliation}.
     *
     * @param source the element whose base state to take
     * @effects replaces this element's base state; leaves its identity and its place in the
     *          document alone
     */
    public final void copyBaseStateFrom(LineElement source) {
        userXOffsetSs = source.userXOffsetSs;
        userYOffsetSs = source.userYOffsetSs;
    }
}
