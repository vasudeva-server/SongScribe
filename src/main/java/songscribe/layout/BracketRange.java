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
 * The x-range of a single visual bracket of an ending, in the line's layout space.
 *
 * @param x1Ss X of the left leg's path, in staff spaces
 * @param x2Ss X of the right edge's path, where the right leg is drawn when there is one
 */
public record BracketRange(double x1Ss, double x2Ss, boolean hasClosingStroke) {

    public enum Bracket {
        FIRST,
        SECOND;

        public String label() {
            return (this == FIRST ? "1" : "2") + ".";
        }
    }

    /**
     * @param first the first bracket, from the element the ending opens on to the split
     * @param second the second bracket, from the split to the ending's end
     */
    public record Pair(BracketRange first, BracketRange second) {

        public BracketRange get(Bracket slot) {
            return switch (slot) {
                case FIRST -> first;
                case SECOND -> second;
            };
        }

        Pair shiftedBy(double dxSs) {
            return new Pair(first.shiftedBy(dxSs), second.shiftedBy(dxSs));
        }
    }

    /**
     * The end of the path, not of the ink: a closed bracket's right leg is centered on
     * {@link #x2Ss}, so its ink reaches half a stroke past it, the same X an open arm's ink reaches.
     */
    public double armEndXSs() {
        return hasClosingStroke
            ? x2Ss
            : x2Ss + EngravingConstants.VOLTA_BRACKET_SS / 2;
    }

    BracketRange shiftedBy(double dxSs) {
        return new BracketRange(x1Ss + dxSs, x2Ss + dxSs, hasClosingStroke);
    }
}
