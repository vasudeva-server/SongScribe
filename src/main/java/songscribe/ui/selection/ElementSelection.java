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

package songscribe.ui.selection;

import songscribe.dom.Line;

/**
 * A run of elements within a staff line, named by index.
 * <p>
 * What every question about a run of elements is asked in terms of — {@link RangeQueries} and
 * the edit operations take this rather than {@link Selection.Range}, because none of them reads
 * the anchor a {@code Range} additionally carries. An operation reached from a key press has no
 * anchor to give: nothing was clicked.
 *
 * @param line  The staff line containing the selection
 * @param begin The index of the first selected element
 * @param end   The index of the last selected element (inclusive)
 */
public record ElementSelection(Line line, int begin, int end) {

    /** The single element at {@code elementIndex}. */
    public static ElementSelection single(Line line, int elementIndex) {
        return new ElementSelection(line, elementIndex, elementIndex);
    }

    /** The number of elements the selection covers. */
    public int size() {
        return (end - begin) + 1;
    }
}
