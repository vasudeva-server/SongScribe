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

import org.jspecify.annotations.Nullable;

/**
 * Mutation recording a change to a composition metadata field.
 * The runtime types of {@code oldValue} and {@code newValue} are validated against
 * {@link MetadataField#getExpectedType()} at construction time.
 */
public record MetadataChange(MetadataField field, @Nullable Object oldValue, @Nullable Object newValue)
    implements Mutation {

    public MetadataChange {
        FieldTypeValidator.validate("MetadataChange", field, field.getExpectedType(), oldValue, newValue);
    }
}
