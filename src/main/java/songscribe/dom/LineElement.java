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
 * <p><b>The offsets.</b> Where the notator dragged the element from the position layout gave it,
 * in staff spaces: X positive to the right, Y positive downward, both 0 until adjusted. The
 * drawn position is the calculated position plus the offset.
 */
public abstract class LineElement {

    private double userXOffsetSs = 0;

    private double userYOffsetSs = 0;

    public double getUserXOffsetSs() {
        return userXOffsetSs;
    }

    public void setUserXOffsetSs(double userXOffsetSs) {
        this.userXOffsetSs = userXOffsetSs;
    }

    public double getUserYOffsetSs() {
        return userYOffsetSs;
    }

    public void setUserYOffsetSs(double userYOffsetSs) {
        this.userYOffsetSs = userYOffsetSs;
    }

    /**
     * Every route that copies one element's state onto another ends up here, so a field added to
     * this class is carried by all of them and no route can omit it.
     */
    public final void copyBaseStateFrom(LineElement source) {
        userXOffsetSs = source.userXOffsetSs;
        userYOffsetSs = source.userYOffsetSs;
    }
}
