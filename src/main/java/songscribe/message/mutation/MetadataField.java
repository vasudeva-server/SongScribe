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

package songscribe.message.mutation;

import songscribe.dom.KeyType;
import songscribe.dom.Tempo;

/**
 * Identifies which metadata field changed in a {@link MetadataChange} mutation.
 * Each constant carries the expected runtime type of {@code oldValue} and
 * {@code newValue} in the mutation record; {@link MetadataChange}'s canonical
 * constructor validates values against this type.
 */
public enum MetadataField {
    TITLE(String.class),
    PLACE(String.class),
    YEAR(String.class),
    MONTH(Integer.class),
    DAY(Integer.class),
    ATTRIBUTION(String.class),
    NUMBER(String.class),
    TEMPO(Tempo.class),
    DEFAULT_KEY_ACCIDENTAL_COUNT(Integer.class),
    DEFAULT_KEY_TYPE(KeyType.class),
    FOOTNOTES(String.class),
    UNOFFICIAL_TRANSLATION(Boolean.class);

    private final Class<?> expectedType;

    MetadataField(Class<?> expectedType) {
        this.expectedType = expectedType;
    }

    public Class<?> getExpectedType() {
        return expectedType;
    }
}
