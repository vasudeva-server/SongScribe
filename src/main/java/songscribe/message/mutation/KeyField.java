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

package songscribe.message.mutation;

import songscribe.music.KeyType;

/**
 * Identifies which key-signature field changed in a {@link LineKeyChange} mutation.
 * Each constant carries the expected runtime type of {@code oldValue} and {@code newValue};
 * {@link LineKeyChange}'s canonical constructor validates values against this type.
 * {@code KEY_TYPE} accepts null (an unset key signature).
 */
public enum KeyField {
    ACCIDENTAL_COUNT(Integer.class),
    KEY_TYPE(KeyType.class);

    private final Class<?> expectedType;

    KeyField(Class<?> expectedType) {
        this.expectedType = expectedType;
    }

    public Class<?> getExpectedType() {
        return expectedType;
    }
}
