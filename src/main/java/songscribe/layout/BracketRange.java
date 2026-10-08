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
 * The x-range and properties of a single visual bracket of an ending, in the line's layout space.
 * Which of the ending's two brackets it is follows from the slot of the {@link Pair} that holds it.
 *
 * @param x1Ss             X of the left leg's path, in staff spaces
 * @param x2Ss             X of the right edge's path — where the right leg is drawn when there is
 *                         one — in staff spaces
 * @param hasClosingStroke whether the bracket has a right vertical tick
 */
public record BracketRange(double x1Ss, double x2Ss, boolean hasClosingStroke) {

    /** Which of an ending's two visual brackets: a slot of a {@link Pair}. */
    public enum Bracket {
        FIRST,
        SECOND;

        /**
         * Returns the label text for this bracket (e.g. "1." or "2.").
         *
         * @return the bracket number followed by a period
         */
        public String label() {
            return (this == FIRST ? "1" : "2") + ".";
        }
    }

    /**
     * The two brackets of an ending, in the line's layout space.
     *
     * @param first  the first bracket, from the element the ending opens on to the split
     * @param second the second bracket, from the split to the ending's end
     */
    public record Pair(BracketRange first, BracketRange second) {

        /**
         * Returns the bracket in {@code slot}.
         *
         * @param slot which of the two brackets
         * @return {@link #first} for {@link Bracket#FIRST}, {@link #second} for
         *     {@link Bracket#SECOND}
         */
        public BracketRange get(Bracket slot) {
            return switch (slot) {
                case FIRST -> first;
                case SECOND -> second;
            };
        }

        /**
         * Returns both brackets moved horizontally by {@code dxSs}.
         *
         * @param dxSs shift applied to every X of both brackets
         * @return the shifted pair
         */
        Pair shiftedBy(double dxSs) {
            return new Pair(first.shiftedBy(dxSs), second.shiftedBy(dxSs));
        }
    }

    /**
     * Returns the X where the drawn path's top arm ends on the right: {@link #x2Ss} itself when a
     * closing stroke turns the arm down into the right leg, or half a volta stroke width past it
     * when the arm ends open.
     * <p>
     * This is the end of the path, not of the ink. An open arm ends in a round cap that the stroker
     * pulls in onto the endpoint, so its ink stops here. A closed bracket's right leg is centered on
     * {@link #x2Ss}, so its ink reaches half a stroke past {@link #x2Ss} — the same X an open arm's
     * ink reaches.
     *
     * @return the X of the top arm's right end, in staff spaces
     */
    public double armEndXSs() {
        return hasClosingStroke
            ? x2Ss
            : x2Ss + EngravingConstants.VOLTA_BRACKET_SS / 2;
    }

    /**
     * Returns this bracket moved horizontally by {@code dxSs}.
     *
     * @param dxSs shift applied to both {@link #x1Ss} and {@link #x2Ss}
     * @return the shifted bracket, with the same closing stroke
     */
    BracketRange shiftedBy(double dxSs) {
        return new BracketRange(x1Ss + dxSs, x2Ss + dxSs, hasClosingStroke);
    }
}
