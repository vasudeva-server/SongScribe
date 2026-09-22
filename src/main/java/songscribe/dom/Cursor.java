/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.dom;

/**
 * A place in a song: an index on a line, ordered in song order. A cursor names a place, not an
 * element: the index may be one past the last element of its line, where an insertion lands or a
 * removal took place.
 *
 * @param lineIndex the line's index among the song's lines
 * @param index     the index on that line
 * @invariant {@code 0 <= lineIndex} and {@code 0 <= index}
 */
public record Cursor(int lineIndex, int index) implements Comparable<Cursor> {

    /**
     * Orders cursors in song order: by line, then by index within the line.
     *
     * @param other the cursor to compare with
     * @return negative, zero or positive as this cursor comes before, at or after {@code other}
     */
    @Override
    public int compareTo(Cursor other) {
        var byLine = Integer.compare(lineIndex, other.lineIndex);
        return (byLine != 0) ? byLine : Integer.compare(index, other.index);
    }

    /**
     * The place {@code count} indices further along this cursor's line.
     *
     * @param count how many indices to move; {@code 0 <= count}
     * @return the cursor on the same line at {@code index + count}
     */
    public Cursor plus(int count) {
        return new Cursor(lineIndex, index + count);
    }

    /**
     * The place after this one on its line.
     *
     * @return {@code plus(1)}
     */
    public Cursor next() {
        return plus(1);
    }
}
