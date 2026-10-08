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

package songscribe.layout.stacking;

import songscribe.dom.Direction;
import songscribe.layout.StaffExtents;
import songscribe.layout.SupportPadding;

/**
 * The inputs LilyPond's {@code Side_position_interface::aligned_side} places an element by: the
 * side of the staff it sits on and its three paddings.
 *
 * @param direction        the side of the staff: {@link Direction#UP} above, {@link Direction#DOWN}
 *                         below
 * @param paddingSs        the gap the element keeps from whatever its footprint contacts
 * @param staffPaddingSs   the minimum gap the element keeps from the staff line when the staff
 *                         clamp is the outward constraint
 * @param horizonPaddingSs how far beyond its own footprint the element looks for a support
 */
record AlignedSide(
    Direction direction, double paddingSs, double staffPaddingSs, double horizonPaddingSs) {

    /**
     * Returns the two paddings a {@link StaffExtents#clearance} query reads: the element's own
     * padding and its horizon padding.
     *
     * @return {@link #paddingSs()} and {@link #horizonPaddingSs()} as a {@link SupportPadding}
     */
    SupportPadding supportPadding() {
        return new SupportPadding(paddingSs, horizonPaddingSs);
    }
}
