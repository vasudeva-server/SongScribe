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

package songscribe.ui.clipboard;

import java.util.ArrayList;

import songscribe.model.ElementType;
import songscribe.model.StaffElement;

/**
 * Manages clipboard state for copy/paste operations.
 */
public final class ClipboardManager {

    // Copied elements waiting to be pasted
    private final ArrayList<StaffElement> pasteboard = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Pasteboard accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the number of elements in the pasteboard.
     */
    public int getSize() {
        return pasteboard.size();
    }

    /**
     * Returns whether the pasteboard is empty.
     */
    public boolean isEmpty() {
        return pasteboard.isEmpty();
    }

    /**
     * Returns the element at the specified index.
     *
     * @param index The index of the element to retrieve
     * @return The element at the specified index
     */
    public StaffElement getElement(int index) {
        return pasteboard.get(index);
    }

    /**
     * Returns the first element in the pasteboard.
     *
     * @return The first element
     */
    public StaffElement getFirstElement() {
        return pasteboard.getFirst();
    }

    /**
     * Returns the last element in the pasteboard.
     *
     * @return The last element
     */
    public StaffElement getLastElement() {
        return pasteboard.getLast();
    }

    // -------------------------------------------------------------------------
    // Pasteboard mutators
    // -------------------------------------------------------------------------

    /**
     * Clears the pasteboard and prepares for a new copy operation.
     */
    public void clear() {
        pasteboard.clear();
    }

    /**
     * Adds an element to the pasteboard. Any {@code FINAL_DOUBLE_BARLINE} is
     * normalized to {@code DOUBLE_BARLINE} so pasting clipboard content can
     * never violate the song-owned invariant.
     *
     * @param element The element to add
     */
    public void addElement(StaffElement element) {
        if (element.getType() == ElementType.FINAL_DOUBLE_BARLINE) {
            element = ElementType.DOUBLE_BARLINE.newInstance();
        }

        pasteboard.add(element);
    }
}
