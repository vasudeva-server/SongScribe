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
 * What a note knows about its own accidental: decided by the chain walk, a sounding alteration
 * the walk has yet to place, or no pitch of its own yet.
 *
 * <p>A note on a line of a song holds a {@link ChainedAccidental} except while a file is being
 * read and within the one {@code Song.applyChange} call that set its intent.
 *
 * <p>A note gets a {@link ChainedAccidental} only from the walk
 * ({@link AccidentalChain.Restamp#stamp}), from {@link NoteElement.SavedAccidental#restore}, or
 * through {@link StaffElement#copyStateFrom}. Every other write sets an intent, which the next
 * walk replaces.
 */
public sealed interface AccidentalState permits ChainedAccidental, AccidentalState.Chosen, AccidentalState.Adopting {

    /**
     * Ask it only of a state that is not {@link Adopting}, which has no pitch of its own.
     */
    Accidental sounding();

    /**
     * @return {@code null} when the note draws no accidental
     */
    @Nullable Accidental drawn();

    /**
     * Returns the intent under which the next chain walk gives a note this same pitch.
     */
    AccidentalState intent();

    record Chosen(Accidental sounding) implements AccidentalState {

        @Override
        public Accidental drawn() {
            return sounding;
        }

        @Override
        public AccidentalState intent() {
            return this;
        }
    }

    /**
     * A note with no pitch of its own, such as a fresh note or one moved to another staff
     * position, sounds the alteration in force where it lands. A pasted note keeps the pitch it
     * was copied with instead.
     */
    record Adopting() implements AccidentalState {

        @Override
        public Accidental sounding() {
            throw new IllegalStateException("An adopting note has no sounding accidental");
        }

        @Override
        public @Nullable Accidental drawn() {
            return null;
        }

        @Override
        public AccidentalState intent() {
            return this;
        }
    }
}
