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

import java.util.EnumSet;

import songscribe.music.Line;
import songscribe.music.StaffElement;

/**
 * Mutation recording field changes on an existing element.
 * {@code fields} identifies which fields changed; it lets subscribers filter
 * without inspecting {@code beforeElement} field-by-field.
 * {@code beforeElement} is a clone of the element captured before the mutation runs
 * and is the source of truth for undo to revert. The post-mutation state is
 * accessible via {@code line.getElement(index)}.
 */
public record ElementModification(Line line, int index, EnumSet<ElementField> fields, StaffElement beforeElement)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
