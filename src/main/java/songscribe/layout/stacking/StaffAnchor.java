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

/**
 * The anchored ceiling or floor an element stacks against: the side of the staff, the staff
 * position the anchor is taken from, and the margin the element keeps beyond it.
 *
 * @param direction     the side of the staff: {@link Direction#UP} stacks against
 *                      {@link StackingUtils#anchorCeilingSs}, {@link Direction#DOWN} against
 *                      {@link StackingUtils#anchorFloorSs}
 * @param staffPosition the staff position the anchor is computed from
 * @param marginSs      the gap the element keeps beyond the anchor or the reservations under its
 *                      footprint, whichever lies further out
 */
record StaffAnchor(Direction direction, int staffPosition, double marginSs) {
}
