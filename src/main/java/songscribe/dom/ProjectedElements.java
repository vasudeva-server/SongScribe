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
package songscribe.dom;

import java.util.AbstractList;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * A read-only view of a line's elements as a pending {@link ElementChange} will leave
 * them: it maps each projected position onto the pre-change line instead of copying the
 * list. Construction and {@link #get} are both O(1), whatever the shape of the change.
 * <p>
 * The view reads the live line, so it is valid only as long as {@link ElementChange}'s class
 * Javadoc states.
 * <p>
 * Each shape of change supplies its own {@code elementAt} mapping, since only the shape
 * knows how positions move; this class holds what every shape shares — the projected size,
 * the bounds check, and the {@link java.util.List} surface {@link AbstractList} derives
 * from those two.
 */
final class ProjectedElements extends AbstractList<StaffElement> {

    private final int size;

    /** Maps a projected position to the element that will occupy it. */
    private final IntFunction<StaffElement> elementAt;

    ProjectedElements(int size, IntFunction<StaffElement> elementAt) {
        this.size = size;
        this.elementAt = elementAt;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public StaffElement get(int index) {
        // Checked here rather than left to the underlying line: a projected position past
        // the projected end can still be a valid position in the line being mapped onto,
        // and would otherwise return an element that is not there.
        Objects.checkIndex(index, size);

        return elementAt.apply(index);
    }
}
