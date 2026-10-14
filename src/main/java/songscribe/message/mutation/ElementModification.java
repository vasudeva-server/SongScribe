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

import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * Mutation recording field changes on an existing element. {@code beforeElement} and
 * {@code afterElement} are clones, the source of truth for undo and redo; replay restores them
 * in place, preserving element identity.
 */
public record ElementModification(Line line, int index, EnumSet<ElementField> fields,
                                  StaffElement beforeElement, StaffElement afterElement)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
