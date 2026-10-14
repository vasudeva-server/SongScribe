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
 * <p>{@code lineIndex} and {@code index} are not negative.
 */
public record Cursor(int lineIndex, int index) implements Comparable<Cursor> {

    @Override
    public int compareTo(Cursor other) {
        var byLine = Integer.compare(lineIndex, other.lineIndex);
        return (byLine != 0) ? byLine : Integer.compare(index, other.index);
    }

    /**
     * @param count {@code 0 <= count}
     */
    public Cursor plus(int count) {
        return new Cursor(lineIndex, index + count);
    }

    public Cursor next() {
        return plus(1);
    }
}
