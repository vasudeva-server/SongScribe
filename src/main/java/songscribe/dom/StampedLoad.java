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
 * The third phase of a load: every note holds a stamped accidental, and the ties, beams and
 * trills are not yet placed.
 *
 * @param marks each mark on a line of {@code song}
 */
record StampedLoad(Song song, PendingMarks marks) {

    /**
     * The tie placement precedes the beam placement: {@link Beam#spanning} refuses a beam over a
     * tied pair, so a file that beams two tied notes loses the beam and keeps the tie, as
     * {@link Tie}'s class invariant requires. In the other order nothing removes the beam,
     * because {@link TieRule} runs only when a bracket closes, and a load closes none.
     *
     * <p>The marks end here. A mark names its endpoints as element instances, and the later
     * stages remove and replace elements.
     */
    PlacedLoad place() {
        TieLoadPass.run(song, marks.ties());
        SpanLoadPass.run(song, marks.beams());
        SpanLoadPass.run(song, marks.trills());
        return new PlacedLoad(song);
    }
}
