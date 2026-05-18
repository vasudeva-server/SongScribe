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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;

/**
 * Abstract base class for elements that span multiple elements.
 * <p>
 * Range elements include ties, trills, crescendo/diminuendo hairpins,
 * tuplet brackets, and first/second endings.
 * <p>
 * Each range element has:
 * <ul>
 *   <li>An anchor element (first element in the range)</li>
 *   <li>Methods to determine the range extent</li>
 * </ul>
 * <p>
 * Concrete subclasses will be implemented in Phase 4.
 */
public abstract class RangeElement extends LineElement {

    /**
     * Creates a range element spanning from anchor to end element.
     *
     * @param anchorElement The first element in the range
     * @param endElement    The last element in the range
     */
    protected RangeElement(StaffElement anchorElement, StaffElement endElement) {
        this.anchorElement = anchorElement;
        this.endElement = endElement;
    }

    /** The first element in this range. */
    private @Nullable StaffElement anchorElement;

    /** The last element in this range. */
    private @Nullable StaffElement endElement;

    /**
     * Returns the first element in this range.
     */
    public @Nullable StaffElement getAnchorElement() {
        return anchorElement;
    }

    /**
     * Sets the first element in this range.
     */
    public void setAnchorElement(@Nullable StaffElement anchorElement) {
        this.anchorElement = anchorElement;
    }

    /**
     * Returns the last element in this range.
     */
    public @Nullable StaffElement getEndElement() {
        return endElement;
    }

    /**
     * Sets the last element in this range.
     */
    public void setEndElement(@Nullable StaffElement endElement) {
        this.endElement = endElement;
    }

    /**
     * Returns the number of elements in this range.
     * Returns 0 if the range is not properly defined.
     */
    public int getElementCount() {
        if (anchorElement == null || endElement == null) {
            return 0;
        }

        var startIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        if (startIndex < 0 || endIndex < 0) {
            return 0;
        }

        return endIndex - startIndex + 1;
    }

    /**
     * Returns whether this range element is invalidated by the given deletion.
     * <p>
     * A range element is invalidated when its anchor or end element is among the deleted elements,
     * because the range can no longer be rendered without both endpoints. Subclasses may override
     * this method if their invalidation condition is more nuanced.
     *
     * @param deletedElements the elements that were removed from the line
     * @return {@code true} if this range element should be removed as a result of the deletion
     */
    public boolean isInvalidatedBy(List<StaffElement> deletedElements) {
        return deletedElements.contains(anchorElement) || deletedElements.contains(endElement);
    }

    /**
     * Returns whether this range element is above the staff.
     */
    public boolean isAbove() {
        // By default, range elements are above the staff. Subclasses can override this if needed.
        return true;
    }

    /**
     * Returns the width of this range element in staff-space units.
     * <p>
     * Computed as the distance from the anchor element's X position to the
     * right edge of the end element, all in staff spaces.
     */
    @Override
    public double getContentWidthSs() {
        var anchor = anchorElement;
        var endElement = this.endElement;

        if (anchor == null || endElement == null) {
            return 0;
        }

        return Math.abs(endElement.getXSs() - anchor.getXSs()) + endElement.getContentWidthSs();
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.ssToPx(getContentWidthSs());
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.ssToPx(getContentHeightSs());
    }

    /**
     * Returns the horizontal span width for collision detection in staff-space units.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    public abstract double getSpanWidthSs(double anchorXSs, double endXSs);

    /**
     * Returns the index of the anchor element within its line.
     * Returns -1 if the anchor element is not set or not in a line.
     */
    public int getAnchorElementIndex() {
        if (anchorElement == null) {
            return -1;
        }

        return anchorElement.getLine().getElementIndex(anchorElement);
    }

    /**
     * Returns the index of the end element within its line.
     * Returns -1 if the end element is not set or not in a line.
     */
    public int getEndElementIndex() {
        if (endElement == null) {
            return -1;
        }

        return endElement.getLine().getElementIndex(endElement);
    }
}
