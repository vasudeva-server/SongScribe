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

import songscribe.model.Line;
import songscribe.model.StaffElement;

/**
 * Mutation recording an atomic swap of the element at a specific index on a line.
 * Both the pre-swap element and the post-swap element are captured so that undo
 * can restore the previous occupant and redo can reinstall the replacement without
 * needing to recreate either.
 */
public record ElementReplacement(Line line, int index, StaffElement oldElement, StaffElement newElement)
    implements Mutation, LineScopedMutation {

    @Override
    public Line getLine() {
        return line;
    }
}
