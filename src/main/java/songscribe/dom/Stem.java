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
 * Who decides which way a note's stem points: the notator, or the score.
 *
 * <p>A {@link Forced} stem's direction stands whatever the note's pitch or beam would say. An
 * {@link Auto} stem carries nothing: {@link NoteElement#direction()} reads it off the score each
 * time, so no cached answer goes stale.
 */
public sealed interface Stem {

    Auto AUTO = new Auto();

    record Forced(Direction direction) implements Stem {
    }

    record Auto() implements Stem {
    }
}
