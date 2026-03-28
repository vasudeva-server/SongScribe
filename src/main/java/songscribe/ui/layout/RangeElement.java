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

package songscribe.ui.layout;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;

/**
 * Abstract base class for elements that span multiple notes.
 * <p>
 * Range elements include ties, trills, crescendo/diminuendo hairpins,
 * tuplet brackets, and first/second endings.
 * <p>
 * Each range element has:
 * <ul>
 *   <li>An anchor note (first note in the range)</li>
 *   <li>Methods to determine the range extent</li>
 * </ul>
 * <p>
 * Concrete subclasses will be implemented in Phase 4.
 */
public abstract class RangeElement extends LineElement {

    /** The first note in this range. */
    private @Nullable StaffElement anchorNote;

    /**
     * Returns the first note in this range.
     */
    public @Nullable StaffElement getAnchorElement() {
        return anchorNote;
    }

    /**
     * Sets the first note in this range.
     */
    public void setAnchorElement(@Nullable StaffElement anchorNote) {
        this.anchorNote = anchorNote;
    }

    /**
     * Returns the last note in this range.
     * Subclasses must implement based on their specific range definition.
     */
    public abstract @Nullable StaffElement getEndElement();

    /**
     * Returns the number of notes in this range.
     * Returns 0 if the range is not properly defined.
     */
    public abstract int getNoteCount();

    /**
     * Returns whether this range element is above the staff.
     */
    public abstract boolean isAbove();

    /**
     * Returns the content height of this range element in staff-space units.
     */
    public abstract double getContentHeightSs();

    /**
     * Returns the horizontal span width for collision detection in staff-space units.
     *
     * @param anchorXSs X position of the anchor note in staff-space units
     * @param endXSs    X position of the end note in staff-space units
     * @return span width in staff-space units
     */
    public abstract double getSpanWidthSs(double anchorXSs, double endXSs);

    /**
     * Returns the index of the anchor note within its line.
     * Returns -1 if the anchor note is not set or not in a line.
     */
    public int getAnchorElementIndex() {
        if (anchorNote == null) {
            return -1;
        }

        return anchorNote.getLine().getElementIndex(anchorNote);
    }

    /**
     * Returns the index of the end note within its line.
     * Returns -1 if the end note is not set or not in a line.
     */
    public int getEndElementIndex() {
        var endNote = getEndElement();

        if (endNote == null) {
            return -1;
        }

        return endNote.getLine().getElementIndex(endNote);
    }
}
