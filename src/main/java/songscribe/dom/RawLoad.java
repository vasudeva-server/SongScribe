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
 * The first phase of a load: the song as its reader built it, with the marks the reader left for
 * the load to place.
 *
 * @param marks each mark on a line of {@code song}
 */
record RawLoad(Song song, PendingMarks marks) {

    /**
     * Precedes every load stage, because the stranding check of
     * {@link KeyInheritance#settleAfterParsing} judges the song as the file wrote it. Example:
     * line 0 is in G, and the file starts with a barline and a key change to G. If
     * {@link LeadingBarLoadPass} ran first, it would remove that pair, and the file would be
     * accepted.
     */
    KeyedLoad settle() throws StrandedKeyChangeException {
        song.keyInheritance().settleAfterParsing();
        return new KeyedLoad(song, marks);
    }
}
