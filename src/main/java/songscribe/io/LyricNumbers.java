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

package songscribe.io;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;

/**
 * Converts between a {@link Song.Script} and the lyric number a file stores it as. A lyric number
 * never reaches code outside {@code songscribe.io}.
 */
public final class LyricNumbers {

    // The lyric number of the first script; each later script takes the next number.
    private static final int FIRST_LYRIC_NUMBER = 1;

    private LyricNumbers() {
    }

    /**
     * @return {@code null} when no script has that lyric number
     */
    public static Song.@Nullable Script toScript(int number) {
        var scripts = Song.Script.values();
        var ordinal = number - FIRST_LYRIC_NUMBER;

        if (ordinal < 0 || ordinal >= scripts.length) {
            return null;
        }

        return scripts[ordinal];
    }

    public static int number(Song.Script script) {
        return script.ordinal() + FIRST_LYRIC_NUMBER;
    }
}
