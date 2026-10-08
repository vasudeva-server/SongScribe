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

import songscribe.dom.StaffElement;

/**
 * A staff element and the absolute X of its column: what a glyph drawn over the element's
 * notehead is positioned from.
 *
 * @param <E>       the element's type, so a caller that needs a note gets one back
 * @param element   the element
 * @param columnXSs the absolute X of the element's column, in staff spaces
 */
public record ElementAtColumn<E extends StaffElement>(E element, double columnXSs) {
}
