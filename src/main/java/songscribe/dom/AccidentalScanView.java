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
 * The song as the chain walk reads it: its lines by index, the elements on each, and the key
 * each line establishes of its own. {@link AccidentalChain} reads nothing about the song
 * except through one of these, so a walk over the live song and a walk over a projection of
 * an edit that has not happened yet cannot disagree about a note. {@link Live} is the song as
 * it stands; {@link ProjectedEdit} holds the one projection, so that a projected view is never
 * paired with an edit on another line.
 *
 * <p>The key a line starts in is never answered here. The walk asks it only of the line an edit
 * starts on, and a projection changes neither that line's own key nor any line before it, so the
 * live line's running key is the answer under every view. A projection never changes a line's own
 * key: an edit to that is a {@link ChainEdit#lineRekeyed rekey} of the live line.
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
    }
}
