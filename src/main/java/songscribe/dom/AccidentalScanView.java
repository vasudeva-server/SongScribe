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

import org.jspecify.annotations.Nullable;

/**
 * The song as the chain walk reads it: its lines by index, the elements on each, the key each
 * line establishes of its own, and the key each line starts in. {@link AccidentalChain} reads
 * nothing about the song except through one of these, so a walk over the live song and a walk
 * over a projection of an edit that has not happened yet cannot disagree about a note.
 * {@link Live} is the song as it stands; {@link ProjectedEdit} holds the one projection, so that
 * a projected view is never paired with an edit on another line.
 *
 * <p>The key a line starts in is the view's answer, not the live line's, because a projection can
 * move it: an edit can leave its line starting in another key, and every line after it that
 * inherits then starts in whatever key the projected lines before it end in. A view answers
 * {@link #ownKey}, {@link #startKey} and the line's elements consistently: a line with its own key
 * starts in it, and a line without one starts in the key in force at the end of the line before
 * it as this view sees that line.
 *
 * <p>Every method takes a line index in {@code 0..lineCount() - 1}; a view is never asked
 * about a line outside its song.
 */
interface AccidentalScanView {

    /**
     * How many lines the song has.
     *
     * @return the line count
     */
    int lineCount();

    /**
     * The live line at {@code lineIndex}: the line a restamp names, whatever elements this
     * view answers for it.
     *
     * @param lineIndex the line's index
     * @return the song's line at that index
     */
    Line line(int lineIndex);

    /**
     * How many elements the line at {@code lineIndex} has, as this view sees it.
     *
     * @param lineIndex the line's index
     * @return the number of indices {@link #elementAt} answers for that line
     */
    int elementCount(int lineIndex);

    /**
     * The element at {@code index} on the line at {@code lineIndex}, as this view sees it.
     *
     * @param lineIndex the line's index
     * @param index     the element's index, in {@code 0..elementCount(lineIndex) - 1}
     * @return the element there
     */
    StaffElement elementAt(int lineIndex, int index);

    /**
     * The key the line at {@code lineIndex} establishes at its own start.
     *
     * @param lineIndex the line's index
     * @return the line's own key, or null when it inherits the key in force at the end of the
     *     line before it
     */
    @Nullable Key ownKey(int lineIndex);

    /**
     * The key the line at {@code lineIndex} starts in, as this view sees the song.
     *
     * @param lineIndex the line's index
     * @return {@link #ownKey} when it is non-null; otherwise the key in force at the end of the
     *     line before, as this view sees that line; {@link Key#DEFAULT} for a first line with no
     *     key of its own
     */
    Key startKey(int lineIndex);

    /**
     * The key in force just before {@code cursor}, as this view sees the song: the key of the last
     * key change on the cursor's line before its index, else the key that line
     * {@link #startKey starts in}. An element standing at {@code cursor} is in this key.
     *
     * @param cursor a place in the song; its index in {@code 0..elementCount(lineIndex)}
     * @return the key in force just before that place
     */
    default Key keyBefore(Cursor cursor) {
        var lineIndex = cursor.lineIndex();

        for (var index = cursor.index() - 1; index >= 0; index--) {
            if (elementAt(lineIndex, index) instanceof KeyChangeElement keyChange) {
                return keyChange.getKey();
            }
        }

        return startKey(lineIndex);
    }

    /**
     * The live song as it stands.
     *
     * @param song the song viewed
     */
    record Live(Song song) implements AccidentalScanView {

        @Override
        public int lineCount() {
            return song.lineCount();
        }

        @Override
        public Line line(int lineIndex) {
            return song.getLine(lineIndex);
        }

        @Override
        public int elementCount(int lineIndex) {
            return song.getLine(lineIndex).elementCount();
        }

        @Override
        public StaffElement elementAt(int lineIndex, int index) {
            return song.getLine(lineIndex).getElement(index);
        }

        @Override
        public @Nullable Key ownKey(int lineIndex) {
            return song.getLine(lineIndex).getKey();
        }

        @Override
        public Key startKey(int lineIndex) {
            return song.getLine(lineIndex).getRunningKey();
        }
    }
}
