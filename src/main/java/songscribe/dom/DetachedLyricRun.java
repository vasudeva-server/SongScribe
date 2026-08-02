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

package songscribe.dom;

import java.util.List;

import songscribe.message.mutation.ElementField;

/**
 * A {@link LyricRun} over a bare list of elements belonging to no {@link Line} — the form a
 * clipboard {@link songscribe.ui.clipboard.Fragment} takes, and the reason the lyric chain
 * rules live on an interface rather than on {@code Line}.
 *
 * <p>The list is used live, not copied: every repair is applied to the caller's elements,
 * which is the point of handing them over.
 *
 * @param elements The run's elements, in order
 */
public record DetachedLyricRun(List<StaffElement> elements) implements LyricRun {

    @Override
    public StaffElement getElement(int index) {
        return elements.get(index);
    }

    @Override
    public int elementCount() {
        return elements.size();
    }

    /**
     * The same as {@link #elementCount()}. A terminal barline is a line's to maintain; a
     * detached run holds only what it was given.
     */
    @Override
    public int effectiveElementCount() {
        return elements.size();
    }

    /**
     * Runs {@code mutator} and records nothing. A detached run belongs to no document: there
     * is no open modification bracket to record into, and nothing an undo entry could point
     * at — these elements are clones no line holds.
     */
    @Override
    public void modifyElement(int index, ElementField field, Runnable mutator) {
        mutator.run();
    }
}
