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
 * What a note knows about its own accidental: whether the chain walk has decided it, whether
 * the note has a sounding alteration the walk has yet to place in a chain, or whether it has no
 * pitch of its own yet.
 *
 * <p>A note is constructed {@link Adopting}. The song runs the chain walk after every recorded
 * change, so a note on a line of a song is {@link Stamped} except while a file is being read —
 * the load pass stamps every note at its end — and within the one {@code Song.applyChange} call
 * that set its intent. {@link Chosen} and {@link Adopting} otherwise live on notes not on a
 * line: the preview note, a selection action's stand-in, a detached replacement. A gate that
 * measures such a note for fit may stamp it plainly.
 *
 * <p>Only {@link Adopting} has no sounding alteration: a {@link Stamped} note sounds its chained
 * accidental's alteration and a {@link Chosen} note sounds what it was pitched with.
 */
public sealed interface AccidentalState {

    /**
     * The chain walk has decided this note.
     *
     * @param chained the walk's decision: the alteration the note sounds and its place in its
     *                chain
     */
    record Stamped(ChainedAccidental chained) implements AccidentalState {}

    /**
     * Pitched by the notator, a selection action or a file; not yet walked.
     *
     * @param sounding the alteration the note sounds
     */
    record Chosen(Accidental sounding) implements AccidentalState {}

    /** No pitch of its own: it will sound what is in force where it lands. */
    record Adopting() implements AccidentalState {}
}
