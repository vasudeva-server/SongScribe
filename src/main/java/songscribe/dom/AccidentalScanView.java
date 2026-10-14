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
 * The song as the chain walk reads it, so a walk over the live song and a walk over a
 * projection of an edit that has not happened yet cannot disagree about a note.
 *
 * <p>The key a line starts in is the view's answer, not the live line's, because a projection can
 * move it. A line with its own key starts in it; a line without one starts in the key in force at
 * the end of the line before it, as this view sees that line.
 *
 * <p>Every method takes a line index in {@code 0..lineCount() - 1}.
 */
interface AccidentalScanView {

    int lineCount();

    /**
     * The live line a restamp names, whatever elements this view answers for it.
     */
    Line line(int lineIndex);

    int elementCount(int lineIndex);

    /**
     * @param index the element's index, in {@code 0..elementCount(lineIndex) - 1}
     */
    StaffElement elementAt(int lineIndex, int index);

    /**
     * @return the line's own key, or null when it inherits one
     */
    @Nullable Key ownKey(int lineIndex);

    Key startKey(int lineIndex);

    /**
     * @param cursor a place in the song; its index in {@code 0..elementCount(lineIndex)}
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
