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
 * An element whose vertical extent is fixed by the element itself, independent of layout.
 * <p>
 * The placement methods that take it read the height off the element rather than accepting
 * one, so a caller cannot reserve a box that disagrees with the element it is reserving for.
 * An element whose height layout computes stays outside it and supplies its height explicitly
 * to a placement method that accepts one.
 * <p>
 * Width is deliberately not here: several implementors reserve a width that is not their own,
 * so a declared width would be a promise half the members could not keep.
 */
public interface IntrinsicHeight {

    /**
     * Never derived from layout state, so it is the same answer at every point in a layout pass.
     */
    double intrinsicHeightSs();
}
