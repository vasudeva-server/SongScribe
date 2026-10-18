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
 * The second phase of a load: every inherited key is settled, and the marks are not yet placed.
 *
 * @param marks each mark on a line of {@code song}
 */
record KeyedLoad(Song song, PendingMarks marks) {

    /**
     * The accidental stamp precedes the tie placement of {@link StampedLoad#place}, because
     * {@link Tie#judge} compares the sounding alteration of the two notes, which a bare note has
     * only once stamped.
     */
    StampedLoad stamp() {
        TupletLoadPass.run(song, marks.tuplets());
        SpanLoadPass.run(song, marks.endings());
        SpanLoadPass.run(song, marks.hairpins());
        song.stampAccidentalsAfterParsing();
        return new StampedLoad(song, marks);
    }
}
