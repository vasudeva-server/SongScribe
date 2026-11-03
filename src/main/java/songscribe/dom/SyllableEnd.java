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
 * How a syllable that is written relates to the next syllable in its script.
 */
public enum SyllableEnd {
    /** The syllable ends its word: SINGLE or END, not compound. */
    WORD_END,
    /** The word continues into the next syllable through a hyphen: BEGIN or MIDDLE, not compound. */
    CONTINUES,
    /** The word continues into the next syllable through a compound join: BEGIN or MIDDLE, compound. */
    CONTINUES_COMPOUND;

    public boolean continuesWord() {
        return this != WORD_END;
    }

    public boolean isCompound() {
        return this == CONTINUES_COMPOUND;
    }
}
