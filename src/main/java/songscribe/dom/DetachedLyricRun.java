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
 * <p>The list is used live, not copied: every repair is applied to the caller's elements.
 * Nothing here inserts or removes an element, so the list may be immutable.
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
     * A terminal barline is a line's to maintain.
     */
    @Override
    public int effectiveElementCount() {
        return elements.size();
    }

    /**
     * A detached run is in no song; where a fragment lands is the destination line's question.
     */
    @Override
    public boolean isSongStartAt(int index) {
        return false;
    }

    /**
     * Records nothing: there is no modification bracket, and no undo entry could point at these
     * clones.
     */
    @Override
    public void modifyElement(int index, ElementField field, Runnable mutator) {
        mutator.run();
    }
}
