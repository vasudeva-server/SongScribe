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
package songscribe.ui.dialog;

/**
 * The date the words were written, when it is not the date the music was.
 *
 * <p>The three components travel together because {@code SongMetadata} takes them flat, where
 * {@code month} and {@code day} are adjacent {@code int}s a call site could transpose without
 * the compiler noticing.
 *
 * <p>{@link #NONE} is the sole spelling of "no separate words date" —
 * {@code SongMetadata}'s own contract treats an empty year the same way, so a partially filled
 * one is not a state this type ever produces.
 *
 * @param year  the year as typed, or empty for {@link #NONE}
 * @param month {@code 1}–{@code 12}, or {@code 0} for none
 * @param day   {@code 1}–{@code 31}, or {@code 0} for none
 */
record WordsDate(String year, int month, int day) {

    /** No separate words date: the words are dated with the music. */
    static final WordsDate NONE = new WordsDate("", 0, 0);
}
