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
 * An inclusive run of elements in a song, from {@code start} to {@code end}, each named by its
 * {@link ElementLocation}. Both locations hold zero-based model indices.
 *
 * <p>A range may cross lines: a tie from the last note of one line to the first note of the next
 * is one range, whose {@code start} and {@code end} name different lines. A range naming one
 * element has {@code start} equal to {@code end}; {@link #single} builds one.
 *
 * <p>The range is a value: it names positions as they stood when it was built, and nothing keeps
 * it in step with later edits to the song.
 *
 * @invariant {@code start} is at or before {@code end} in song order: its line index is lower,
 *     or the line indices are equal and its element index is not higher
 *
 * @param start the first element of the range
 * @param end   the last element of the range
 */
public record ElementLocationRange(ElementLocation start, ElementLocation end) {

    /**
     * Creates a range from {@code start} to {@code end}.
     *
     * @throws IllegalArgumentException if {@code end} comes before {@code start} in song order
     */
    public ElementLocationRange {
        var startsAfterEnd = start.lineIndex() > end.lineIndex()
            || (start.lineIndex() == end.lineIndex() && start.elementIndex() > end.elementIndex());

        if (startsAfterEnd) {
            throw new IllegalArgumentException("Range start " + start + " is after its end " + end);
        }
    }

    /**
     * Returns the range naming only the element at {@code location}.
     *
     * @return a range whose {@code start} and {@code end} are both {@code location}
     */
    public static ElementLocationRange single(ElementLocation location) {
        return new ElementLocationRange(location, location);
    }
}
