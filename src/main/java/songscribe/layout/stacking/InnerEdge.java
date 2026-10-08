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
import songscribe.layout.LayoutResult;

/**
 * Where an element's staff-facing edge lies: the side of the staff, the edge's Y, and the margin
 * recorded with the element's layout.
 *
 * @param direction the side of the staff: {@link Direction#UP} for an element above it, whose inner
 *                  edge is its bottom; {@link Direction#DOWN} for one below it, whose inner edge is
 *                  its top
 * @param ySs       the inner edge's Y, in staff spaces
 * @param marginSs  the margin recorded in the element's {@link LayoutResult.DecorationLayout}: the
 *                  clearance its placement rule keeps from whatever lies inside it
 */
record InnerEdge(Direction direction, double ySs, double marginSs) {
}
