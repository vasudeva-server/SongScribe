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

package songscribe.smufl;

/**
 * A point on a glyph that something else attaches to, in staff spaces, Y-down (screen)
 * convention, relative to the pen origin the glyph draws from.
 *
 * @param xSs distance right of the pen origin
 * @param ySs distance below the pen origin
 */
public record Anchor(double xSs, double ySs) {

    /**
     * Creates an anchor from a SMuFL metadata coordinate pair, flipping Y from the
     * spec's Y-up convention to the screen Y-down convention the rest of the
     * application uses.
     *
     * @param xSs the metadata x, identical in both conventions
     * @param ySs the metadata y, positive upward
     * @return the anchor in Y-down convention
     */
    static Anchor fromSMuFL(double xSs, double ySs) {
        return new Anchor(xSs, -ySs);
    }
}
