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

/** The {@link Key} values test classes name, so no two of them spell the same signature twice. */
public final class TestKeys {

    private TestKeys() {}

    /** No accidentals at all — the key every cancellation ends at. */
    public static final Key C_MAJOR = new Key(KeyType.NONE, 0);

    /** One sharp, F♯. */
    public static final Key G_MAJOR = new Key(KeyType.SHARPS, 1);

    /** Two sharps, F♯ and C♯. */
    public static final Key D_MAJOR = new Key(KeyType.SHARPS, 2);

    /** Two flats, B♭ and E♭. */
    public static final Key B_FLAT_MAJOR = new Key(KeyType.FLATS, 2);

    /** Four sharps, which alters every pitch class {@link #D_MAJOR} does and two more. */
    public static final Key E_MAJOR = new Key(KeyType.SHARPS, 4);

    /** A sharp on every pitch class there is, so no note escapes it. */
    public static final Key ALL_SHARPS = new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT);

    /** A flat on every pitch class there is, so no note escapes it. */
    public static final Key ALL_FLATS = new Key(KeyType.FLATS, Key.MAX_ACCIDENTAL_COUNT);
}
