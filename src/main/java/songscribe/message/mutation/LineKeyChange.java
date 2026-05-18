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

import org.jspecify.annotations.Nullable;

import songscribe.model.Line;

/**
 * Mutation recording a key-signature change on a specific line.
 * Used when the key accidental count or key type of a line is updated.
 * The runtime types of {@code oldValue} and {@code newValue} are validated against
 * {@link KeyField#getExpectedType()} at construction time.
 */
public record LineKeyChange(Line line, KeyField field, @Nullable Object oldValue, @Nullable Object newValue)
    implements Mutation, LineScopedMutation {

    public LineKeyChange {
        FieldTypeValidator.validate("LineKeyChange", field, field.getExpectedType(), oldValue, newValue);
    }

    @Override
    public Line getLine() {
        return line;
    }
}
