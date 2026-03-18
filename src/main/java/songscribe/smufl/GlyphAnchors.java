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

import org.jspecify.annotations.Nullable;

/**
 * Anchor points for a SMuFL glyph, in staff spaces with Y-down (screen) convention.
 * All anchor points are nullable because not every glyph defines every anchor.
 *
 * @param stemUpSE   stem attachment point for stem-up notes (south-east of notehead)
 * @param stemDownNW stem attachment point for stem-down notes (north-west of notehead)
 * @param cutOutNW   cut-out for second-in-chord positioning (north-west)
 * @param cutOutSE   cut-out for second-in-chord positioning (south-east)
 */
public record GlyphAnchors(
        @Nullable Anchor stemUpSE,
        @Nullable Anchor stemDownNW,
        @Nullable Anchor cutOutNW,
        @Nullable Anchor cutOutSE
) {
    /**
     * A single anchor point in staff spaces, Y-down convention.
     */
    public record Anchor(double x, double y) {

        /**
         * Creates an Anchor from SMuFL metadata values, flipping Y from Y-up to Y-down.
         */
        public static Anchor fromSMuFL(double x, double y) {
            return new Anchor(x, -y);
        }
    }
}
