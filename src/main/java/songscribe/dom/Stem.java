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
 * <p>A {@link Forced} stem carries the direction the notator chose, and that choice stands
 * whatever the note's pitch or beam would say. An {@link Auto} stem carries nothing: its
 * direction is read off the note's place in the score by {@link NoteElement#direction()}
 * whenever it is asked for, so there is no cached answer to go stale. One value holds both
 * facts, so a note can never claim to be automatic while carrying a direction someone wrote, or
 * claim a forced direction it does not have.
 */
public sealed interface Stem {

    /** The one automatic stem: the arm carries no state, so every note shares this instance. */
    Auto AUTO = new Auto();

    /**
     * A stem pointing the way the notator chose.
     *
     * @param direction the chosen direction
     */
    record Forced(Direction direction) implements Stem {
    }

    /** A stem whose direction the score decides; see {@link #AUTO}. */
    record Auto() implements Stem {
    }
}
