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
 * Converts between a {@link Song.Script} and the lyric number a file stores it as. This is the
 * one definition of that mapping: {@link Song.Script#LATIN} is lyric number 1 and
 * {@link Song.Script#BENGALI} is 2.
 *
 * <p>The I/O readers convert a lyric number read from a file with {@link #toScript}; the MusicXML
 * writer writes a script with {@link #number}. No code outside {@code songscribe.io} and its
 * subpackages imports this class, so a lyric number never reaches the rest of the application.
 */
public final class LyricNumbers {

    // The lyric number of the first script; each later script takes the next number.
    private static final int FIRST_LYRIC_NUMBER = 1;

    private LyricNumbers() {
    }

    /**
     * Returns the script a file's lyric number names.
     *
     * @param number a lyric number read from a file
     * @return the script with that lyric number, or {@code null} when no script has it
     * @invariant {@code toScript(number(script)) == script} for every script
     */
    public static Song.@Nullable Script toScript(int number) {
        var scripts = Song.Script.values();
        var ordinal = number - FIRST_LYRIC_NUMBER;

        if (ordinal < 0 || ordinal >= scripts.length) {
            return null;
        }

        return scripts[ordinal];
    }

    /**
     * Returns the lyric number a file stores {@code script} as.
     *
     * @param script the script to write
     * @return its lyric number, at least 1
     */
    public static int number(Song.Script script) {
        return script.ordinal() + FIRST_LYRIC_NUMBER;
    }
}
