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

/**
 * Represents margins for layout elements.
 * <p>
 * Uses left/bottom/right margins only (no top margin, no margin collapsing).
 * All values are in staff spaces.
 */
public record Margin(double leftSs, double bottomSs, double rightSs) {

    /** Zero margin constant */
    public static final Margin NONE = new Margin(0, 0, 0);

    /**
     * Creates a uniform margin with the same value on all sides.
     *
     * @param m The margin value in staff spaces
     * @return A new Margin with uniform values
     */
    public static Margin uniform(double m) {
        return new Margin(m, m, m);
    }
}
