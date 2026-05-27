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
 * Shared validation helper for mutation records that carry {@code Object}-typed
 * old/new values against a field enum with an expected runtime type. Each mutation
 * record invokes {@link #validate} from its canonical constructor so a mismatched
 * value fails loudly at construction time rather than at cast time in an undo
 * replay or subscriber.
 */
final class FieldTypeValidator {

    private FieldTypeValidator() {}

    static void validate(
        String recordName,
        Enum<?> field,
        Class<?> expectedType,
        @Nullable Object oldValue,
        @Nullable Object newValue
    ) {
        check(recordName, field, expectedType, "oldValue", oldValue);
        check(recordName, field, expectedType, "newValue", newValue);
    }

    private static void check(
        String recordName,
        Enum<?> field,
        Class<?> expectedType,
        String parameter,
        @Nullable Object value
    ) {
        if (expectedType.isPrimitive()) {
            throw new IllegalArgumentException(
                recordName + '.' + field.name() + ": expectedType must not be a primitive class; use the boxed type"
            );
        }
        if (value != null && !expectedType.isInstance(value)) {
            throw new IllegalArgumentException(
                recordName + '.' + field.name() + " expected " + parameter
                    + " of type " + expectedType.getSimpleName()
                    + " but got " + value.getClass().getSimpleName()
            );
        }
    }
}
