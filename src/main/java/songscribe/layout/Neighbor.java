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

/**
 * Identifies what kind of element occupies a step of a {@link StaffExtents}
 * skyline, so a decoration being placed can look up its clearance margin
 * against the actual neighbor it would touch.
 * <p>
 * Listed inner→outer for readers only. The declaration order is cosmetic:
 * a decoration's real neighbor is resolved geometrically by
 * {@link StaffExtents#contact} — never by ordinal.
 */
public enum Neighbor {

    /** The outer staff line itself (the built-in floor {@link StaffExtents#contact} seeds). */
    STAFF_LINE,

    /** A notehead (including ledger-line noteheads that protrude past the staff). */
    NOTEHEAD,

    /** A tie arc. */
    TIE,

    /** A staccato dot. */
    STACCATO,

    /** An accent mark. */
    ACCENT,

    /** A trill marking. */
    TRILL,

    /** A fermata symbol. */
    FERMATA
}
