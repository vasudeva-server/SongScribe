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

package songscribe.layout;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;

/**
 * Whether a note belongs to a beam group, which decides whether it draws a flag — and so where its
 * augmentation dots sit.
 */
public enum Beaming {

    /** The note is a member of a beam group: its flag is suppressed. */
    BEAMED,

    /** The note stands alone: a flagged type draws its flag. */
    UNBEAMED;

    /**
     * Returns the beaming of a note given the beam it belongs to.
     *
     * @param beam the beam the note belongs to, or {@code null} when it belongs to none
     * @return {@link #BEAMED} when {@code beam} is non-null, otherwise {@link #UNBEAMED}
     */
    public static Beaming of(@Nullable Beam beam) {
        return beam != null ? BEAMED : UNBEAMED;
    }
}
