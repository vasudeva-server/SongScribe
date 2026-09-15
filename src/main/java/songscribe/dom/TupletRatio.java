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
 * The time a tuplet's notes occupy: the group plays in the time of {@code normalNotes} notes of
 * the written value {@code noteValue}.
 * <p>
 * The printed grade is not part of the ratio. A tuplet read from a file that states no ratio
 * knows its grade before the load pass derives the ratio, so the two are held apart.
 *
 * @param normalNotes M, the count of {@code noteValue} notes whose time the group occupies
 * @param noteValue   V, the written value of those notes
 * @invariant {@code normalNotes} is positive
 */
public record TupletRatio(int normalNotes, NotatableValue noteValue) {

    /**
     * @throws IllegalArgumentException if {@code normalNotes} is not positive
     */
    public TupletRatio {
        if (normalNotes <= 0) {
            throw new IllegalArgumentException(
                "A tuplet ratio needs a positive normal-notes count, got " + normalNotes);
        }
    }
}
