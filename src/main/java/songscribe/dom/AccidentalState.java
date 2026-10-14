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

    record Chosen(Accidental sounding) implements AccidentalState {}

    record Adopting() implements AccidentalState {}
}
