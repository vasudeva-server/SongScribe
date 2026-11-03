/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

/**
 * The lyric that {@link LyricRun#writeLyricForScript} writes on one element: its text, its
 * extend, and how it relates to the next syllable.
 *
 * @param text blank for a carrier or an erased syllable
 */
public record LyricWrite(String text, Lyric.Extend extend, SyllableEnd syllableEnd) {
    public boolean isCarrier() {
        return Lyric.isCarrier(extend);
    }

    /**
     * Blank text on a non-carrier is an erasure rather than a syllable. Whitespace-only text
     * erases the syllable exactly as an empty string does.
     */
    public boolean erases() {
        return !isCarrier() && text.isBlank();
    }

    /**
     * Stricter than {@code syllableEnd} alone: a carrier continues nothing, whatever the caller
     * asked for.
     */
    public boolean continuesIntoNext() {
        return !isCarrier() && syllableEnd.continuesWord();
    }

    public boolean compound() {
        return continuesIntoNext() && syllableEnd.isCompound();
    }
}
