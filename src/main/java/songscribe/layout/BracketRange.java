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

import songscribe.engraving.EngravingConstants;

/**
 * The x-range and properties of a single visual bracket of an ending (the first or the second),
 * in the line's layout space.
 *
 * @param x1Ss             left edge X in staff spaces
 * @param x2Ss             right edge X in staff spaces
 * @param number           bracket number (1 or 2)
 * @param hasClosingStroke whether the bracket has a right vertical tick
 */
public record BracketRange(double x1Ss, double x2Ss, int number, boolean hasClosingStroke) {

    /**
     * Returns the X the bracket's drawn ink reaches on the right: {@link #x2Ss} itself when a
     * closing stroke gives the top arm a join to rest on, or half a volta stroke width past it
     * when an open arm's round end-cap pushes the ink out to match where a closed bracket's
     * right leg would reach.
     *
     * @return the X the bracket's ink reaches on the right, in staff spaces
     */
    public double inkRightXSs() {
        return hasClosingStroke
            ? x2Ss
            : x2Ss + EngravingConstants.VOLTA_BRACKET_SS / 2;
    }

    /**
     * Returns the label text for this bracket (e.g. "1." or "2.").
     *
     * @return the bracket number followed by a period
     */
    public String label() {
        return number + ".";
    }

    /**
     * Returns this bracket moved horizontally, its left edge by {@code dx1Ss} and its right edge
     * by {@code dx2Ss}.
     *
     * @param dx1Ss shift applied to {@link #x1Ss}
     * @param dx2Ss shift applied to {@link #x2Ss}
     * @return the shifted bracket, with the same number and closing stroke
     */
    BracketRange shiftedBy(double dx1Ss, double dx2Ss) {
        return new BracketRange(x1Ss + dx1Ss, x2Ss + dx2Ss, number, hasClosingStroke);
    }
}
