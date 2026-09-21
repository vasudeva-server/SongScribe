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

import java.util.List;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Mutation recording the deletion of a contiguous range of elements from a line; a single
 * element is a range of one. The elements are carried because after the deletion they are no
 * longer reachable through the line.
 *
 * @param line            the line the elements were deleted from
 * @param range           the indices the elements held before the deletion
 * @param deletedElements the removed elements, in order; {@code range.size()} of them
 */
public record ElementDeletion(Line line, ElementRange range, List<StaffElement> deletedElements)
    implements Mutation, LineScopedMutation {

    public ElementDeletion {
        deletedElements = List.copyOf(deletedElements);
    }

    @Override
    public Line getLine() {
        return line;
    }
}
