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

import module java.desktop;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.music.Interval;
import songscribe.music.StaffElement;
import songscribe.ui.layout.Bounds;
import songscribe.ui.layout.LineElement;

/**
 * Immutable result of the layout engine containing all positioned elements for rendering.
 * <p>
 * All positions and dimensions are in staff-space units.
 * <p>
 * The LayoutResult provides rendering code with final positions for all elements in a line,
 * eliminating the need for any position calculations during rendering. It contains:
 * <ul>
 *   <li>Note columns with their horizontal positions</li>
 *   <li>Line elements (attachments, articulations) with their bounds</li>
 *   <li>Staff geometry (top, bottom, lyric baseline)</li>
 *   <li>Total line height for vertical spacing</li>
 * </ul>
 * <p>
 * This class is immutable after construction. Use {@link Builder} to create instances.
 */
public final class LayoutResult {

    /**
     * Offset for positioning an insertion element before the first element in the line (ss).
     */
    private static final double INSERTION_BEFORE_FIRST_OFFSET_SS = 1.875;  // 15px

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<LineElement, Bounds> elementBounds;
    private final Map<Interval, BeamLayout> beamLayouts;
    private final Map<StaffElement, StemLayout> stemLayouts;
    private final Map<Interval, TieLayout> tieLayouts;
    private final double lineHeightSs;
    private final double staffTopYSs;
    private final double staffBottomYSs;
    private final double lyricBaselineYSs;

    /**
     * Creates a layout result with the given data.
     * <p>
     * Use {@link Builder} rather than calling this constructor directly.
     *
     * @param elementColumns   Map of elements to their columns with positions
     * @param elementBounds    Map of line elements to their bounds
     * @param beamLayouts      Map of beam intervals to their computed beam geometry
     * @param stemLayouts      Map of unbeamed notes to their computed stem geometry
     * @param tieLayouts       Map of tie intervals to their computed tie geometry
     * @param lineHeightSs       Total height of the line in staff spaces (including staff, elements, and lyrics)
     * @param staffTopYSs        Y position of the top staff line in staff spaces
     * @param staffBottomYSs     Y position of the bottom staff line in staff spaces
     * @param lyricBaselineYSs   Y position of the lyric baseline in staff spaces (0 if no lyrics)
     */
    private LayoutResult(
        Map<StaffElement, ElementColumn> elementColumns,
        Map<LineElement, Bounds> elementBounds,
        Map<Interval, BeamLayout> beamLayouts,
        Map<StaffElement, StemLayout> stemLayouts,
        Map<Interval, TieLayout> tieLayouts,
        double lineHeightSs,
        double staffTopYSs,
        double staffBottomYSs,
        double lyricBaselineYSs) {
        this.elementColumns = Map.copyOf(elementColumns);
        this.elementBounds = Map.copyOf(elementBounds);
        this.beamLayouts = Map.copyOf(beamLayouts);
        this.stemLayouts = Map.copyOf(stemLayouts);
        this.tieLayouts = Map.copyOf(tieLayouts);
        this.lineHeightSs = lineHeightSs;
        this.staffTopYSs = staffTopYSs;
        this.staffBottomYSs = staffBottomYSs;
        this.lyricBaselineYSs = lyricBaselineYSs;
    }

    // ==========================================================================
    // Note Column Access
    // ==========================================================================

    /**
     * Returns the element column for a specific element.
     *
     * @param element The element to look up
     * @return The element column, or null if the element was not laid out
     */
    public @Nullable ElementColumn getElementColumn(StaffElement element) {
        return elementColumns.get(element);
    }

    /**
     * Returns the X position of an element's head left edge (glyph origin).
     *
     * @param element The element to look up
     * @return The X position, or 0 if the element was not laid out
     */
    public double getElementXSs(StaffElement element) {
        var column = elementColumns.get(element);
        return column != null ? column.getXSs() : 0;
    }

    /**
     * Returns an unmodifiable view of all element columns.
     *
     * @return Map of elements to their columns
     */
    public Map<StaffElement, ElementColumn> getElementColumns() {
        return elementColumns;
    }

    /**
     * Returns whether an element was laid out.
     *
     * @param element The element to check
     * @return true if the element has a column
     */
    public boolean hasElement(StaffElement element) {
        return elementColumns.containsKey(element);
    }

    // ==========================================================================
    // Beam + Stem Layout Access
    // ==========================================================================

    /**
     * Returns the beam geometry for a beam interval.
     *
     * @param interval The beam interval to look up
     * @return The beam layout, or null if not computed
     */
    public @Nullable BeamLayout getBeamLayout(Interval interval) {
        return beamLayouts.get(interval);
    }

    /**
     * Returns the stem geometry for an element.
     * <p>
     * Checks beamed stem layouts first (elements inside a beam group), then falls back
     * to the standalone stem layouts for unbeamed elements.
     *
     * @param element The element to look up
     * @return The stem layout, or null if not computed
     */
    public @Nullable StemLayout getStemLayout(StaffElement element) {
        for (var beamLayout : beamLayouts.values()) {
            var stemLayout = beamLayout.stems().get(element);

            if (stemLayout != null) {
                return stemLayout;
            }
        }

        return stemLayouts.get(element);
    }

    // ==========================================================================
    // Tie Layout Access
    // ==========================================================================

    /**
     * Returns the tie geometry for a tie interval, if it was computed during layout.
     * <p>
     * Returns null when the interval was not laid out (e.g., degenerate
     * tie spanning notes that could not be positioned). Callers should skip rendering
     * if the result is null.
     *
     * @param interval The tie interval to look up
     * @return the tie layout, or null if not computed
     */
    @Nullable
    public TieLayout getTieLayout(Interval interval) {
        return tieLayouts.get(interval);
    }

    // ==========================================================================
    // Line Element Access
    // ==========================================================================

    /**
     * Returns the bounds for a specific line element.
     *
     * @param element The element to look up
     * @return The bounds, or null if the element was not laid out
     */
    public @Nullable Bounds getElementBounds(LineElement element) {
        return elementBounds.get(element);
    }

    /**
     * Returns the position (top-left corner) of a specific line element.
     *
     * @param element The element to look up
     * @return The position, or null if the element was not laid out
     */
    public @Nullable Point2D getElementPosition(LineElement element) {
        var bounds = elementBounds.get(element);

        if (bounds == null) {
            return null;
        }

        return new Point2D.Double(bounds.getLeft(), bounds.getTop());
    }

    /**
     * Returns an unmodifiable view of all element bounds.
     *
     * @return Map of line elements to their bounds
     */
    public Map<LineElement, Bounds> getElementBounds() {
        return elementBounds;
    }

    /**
     * Returns whether a line element was laid out.
     *
     * @param element The element to check
     * @return true if the element has bounds
     */
    public boolean hasElement(LineElement element) {
        return elementBounds.containsKey(element);
    }

    // ==========================================================================
    // Compatibility Methods (for renderers expecting LineElementLayoutResult interface)
    // ==========================================================================

    /**
     * Returns the bounds for a specific element.
     * <p>
     * This method provides compatibility with code expecting LineElementLayoutResult.
     * Accepts Object for flexibility but element should be a LineElement.
     *
     * @param element The element to look up
     * @return The bounds, or null if the element was not laid out
     */
    public @Nullable Bounds getBounds(Object element) {
        if (element instanceof LineElement) {
            return elementBounds.get((LineElement) element);
        }

        return null;
    }

    /**
     * Finds bounds for an attachment with the given parent element and type.
     * <p>
     * Used by renderers that need to look up layout results for attachments
     * but don't have direct access to the attachment object created during layout.
     *
     * @param parentElement  The element the attachment is attached to
     * @param attachmentType The type of attachment to find
     * @return The bounds if found, null otherwise
     */
    public @Nullable Bounds findAttachmentBounds(
        StaffElement parentElement,
        Class<? extends songscribe.ui.layout.Attachment> attachmentType) {

        for (var entry : elementBounds.entrySet()) {
            var element = entry.getKey();

            if (attachmentType.isInstance(element)) {
                var attachment = (songscribe.ui.layout.Attachment) element;

                if (attachment.getOwnerElement() == parentElement) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Finds the attachment object with the given parent element and type.
     * <p>
     * Used by renderers that need access to the attachment object created during layout.
     *
     * @param parentElement  The element the attachment is attached to
     * @param attachmentType The type of attachment to find
     * @param <A>            The attachment type
     * @return The attachment if found, null otherwise
     */
    @SuppressWarnings("unchecked")
    public @Nullable <A extends songscribe.ui.layout.Attachment> A findAttachment(
        StaffElement parentElement,
        Class<A> attachmentType) {

        for (var element : elementBounds.keySet()) {
            if (attachmentType.isInstance(element)) {
                var attachment = (songscribe.ui.layout.Attachment) element;

                if (attachment.getOwnerElement() == parentElement) {
                    return (A) attachment;
                }
            }
        }

        return null;
    }

    /**
     * Finds bounds for a range element with the given anchor and end elements.
     * <p>
     * Used by renderers that need to look up layout results for range elements
     * but don't have direct access to the range element object created during layout.
     *
     * @param anchorElement    The anchor (start) element of the range
     * @param endElement       The end element of the range
     * @param rangeElementType The type of range element to find
     * @return The bounds if found, null otherwise
     */
    public @Nullable Bounds findRangeElementBounds(
        StaffElement anchorElement,
        StaffElement endElement,
        Class<? extends songscribe.ui.layout.RangeElement> rangeElementType) {

        for (var entry : elementBounds.entrySet()) {
            var element = entry.getKey();

            if (rangeElementType.isInstance(element)) {
                var rangeElement = (songscribe.ui.layout.RangeElement) element;

                if (rangeElement.getAnchorElement() == anchorElement &&
                    rangeElement.getEndElement() == endElement) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Returns whether the result contains bounds for the given element.
     * <p>
     * This method provides compatibility with code expecting LineElementLayoutResult.
     *
     * @param element The element to check
     * @return true if bounds exist for this element
     */
    public boolean contains(Object element) {
        if (element instanceof LineElement) {
            return elementBounds.containsKey((LineElement) element);
        }

        return false;
    }

    // ==========================================================================
    // Staff Geometry
    // ==========================================================================

    /**
     * Returns the total height of this line (staff + elements + lyrics), in staff spaces.
     */
    public double getLineHeightSs() {
        return lineHeightSs;
    }

    /**
     * Returns the Y position of the top staff line, in staff spaces.
     */
    public double getStaffTopYSs() {
        return staffTopYSs;
    }

    /**
     * Returns the Y position of the bottom staff line, in staff spaces.
     */
    public double getStaffBottomYSs() {
        return staffBottomYSs;
    }

    /**
     * Returns the Y position of the lyric baseline, in staff spaces.
     *
     * @return Lyric baseline Y in staff spaces, or 0 if no lyrics on this line
     */
    public double getLyricBaselineYSs() {
        return lyricBaselineYSs;
    }

    /**
     * Returns whether this line has lyrics.
     */
    public boolean hasLyrics() {
        return lyricBaselineYSs > 0;
    }

    /**
     * Returns the width of this line (rightmost X position).
     * <p>
     * Calculates the rightmost edge of all element columns in the line.
     *
     * @return Line width in staff-space units, or 0 if no columns
     */
    public double getLineWidthSs() {
        double maxX = 0;

        for (var column : elementColumns.values()) {
            double rightEdge = column.getRightEdgeXSs();

            if (rightEdge > maxX) {
                maxX = rightEdge;
            }
        }

        return maxX;
    }

    // ==========================================================================
    // Insertion Element Positioning (Edit Mode)
    // ==========================================================================

    /**
     * Returns the index of the element whose head contains {@code mouseXSs}, or {@code -1} if none.
     * <p>
     * Only the horizontal (X) dimension is checked; Y position is ignored.
     * The hit zone is the actual element head body: {@code [xSs, xSs + rightExtentSs]}.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the elements
     * @return Element index, or {@code -1} if mouseXSs is not within any element head's horizontal bounds
     */
    public int findElementAtXSs(double mouseXSs, songscribe.music.Line line) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);
            var column = elementColumns.get(element);

            if (column == null) {
                continue;
            }

            var elementX = column.getXSs();

            if (mouseXSs >= elementX && mouseXSs <= elementX + column.getRightExtentSs()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Finds which insertion slot a mouse X coordinate falls into.
     * <p>
     * Insertion slots are the positions where an element can be inserted or replaced:
     * <ul>
     *   <li>Index 0 to elementCount-1: over an existing element (for replacement)</li>
     *   <li>Index elementCount: after the last element (for appending)</li>
     * </ul>
     * <p>
     * If the mouse is within the horizontal bounds of an element head, returns that element's index
     * to indicate replacement. Otherwise, returns the insertion slot between elements.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the elements
     * @return Insertion index (0 to elementCount inclusive)
     */
    public int findInsertionIndex(double mouseXSs, songscribe.music.Line line) {
        int elementCount = line.elementCount();

        if (elementCount == 0) {
            return 0;
        }

        // Check each element to see if mouse is within its head bounds
        int elementAtX = findElementAtXSs(mouseXSs, line);

        if (elementAtX >= 0) {
            return elementAtX;
        }

        // Mouse is not over any element head - find insertion slot between elements

        // Check if before first element
        var firstElement = line.getElement(0);
        var firstColumn = elementColumns.get(firstElement);

        if (firstColumn == null) {
            return 0;
        }

        if (mouseXSs < firstColumn.getXSs()) {
            return 0;
        }

        // Check if after last element
        var lastElement = line.getElement(elementCount - 1);
        var lastColumn = elementColumns.get(lastElement);

        if (lastColumn == null) {
            return elementCount;
        }

        if (mouseXSs > lastColumn.getRightEdgeXSs()) {
            return elementCount;
        }

        // Find the slot between elements (excluding element head bounds)
        for (var i = 0; i < elementCount - 1; i++) {
            var currentElement = line.getElement(i);
            var nextElement = line.getElement(i + 1);

            var currentColumn = elementColumns.get(currentElement);
            var nextColumn = elementColumns.get(nextElement);

            if (currentColumn == null || nextColumn == null) {
                continue;
            }

            var currentRight = currentColumn.getRightEdgeXSs();
            var nextLeft = nextColumn.getXSs();

            // Check if mouseXSs is in the gap between element heads
            if (mouseXSs > currentRight && mouseXSs < nextLeft) {
                return i + 1;
            }
        }

        // Fallback: return position after last element
        return elementCount;
    }

    /**
     * Calculates the X position for rendering an insertion element at a given index.
     * <p>
     * If the mouse is within the horizontal bounds of an element head, snaps to that element's position.
     * Otherwise, positions between elements or after the last element as appropriate.
     *
     * @param insertionIndex   The insertion index (0 to elementCount inclusive)
     * @param mouseXSs         Mouse X coordinate in staff-space units (used to detect if over an element head)
     * @param insertionElement The element to be inserted (used to calculate extents for after-last positioning)
     * @param line             The line containing the elements
     * @return X position in staff-space units for rendering the insertion element
     */
    public double calculateInsertionXSs(
        int insertionIndex,
        double mouseXSs,
        StaffElement insertionElement,
        songscribe.music.Line line) {

        int elementCount = line.elementCount();

        // Empty line - use first element position (clef + key signature + offset)
        if (elementCount == 0) {
            return LayoutConstants.calculateFirstElementXSs(line.getKeyAccidentalCount());
        }

        // Check if mouse is over any element head - if so, snap to that element's position
        for (var i = 0; i < elementCount; i++) {
            var element = line.getElement(i);
            var column = elementColumns.get(element);

            if (column == null) {
                continue;
            }

            var elementX = column.getXSs();

            if (mouseXSs >= elementX && mouseXSs <= elementX + column.getRightExtentSs()) {
                // Mouse is over this element head - snap to its position
                return elementX;
            }
        }

        // Mouse is not over an element head - handle insertion

        // Before first element - position to the left
        if (insertionIndex == 0) {
            var firstElement = line.getElement(0);
            var firstColumn = elementColumns.get(firstElement);

            if (firstColumn == null) {
                return LayoutConstants.FIRST_NOTE_OFFSET_SS;
            }

            return firstColumn.getXSs() - INSERTION_BEFORE_FIRST_OFFSET_SS;
        }

        // After last element - use same spacing logic as layout engine
        if (insertionIndex >= elementCount) {
            var lastElement = line.getElement(elementCount - 1);
            var lastColumn = elementColumns.get(lastElement);

            if (lastColumn == null) {
                return LayoutConstants.FIRST_NOTE_OFFSET_SS;
            }

            // Build a temporary column for the insertion element to calculate proper spacing
            var insertionColumn = new ElementColumn(
                insertionElement,
                java.util.Collections.emptyList(),
                ElementColumnBuilder.calculateLeftExtentSs(insertionElement),
                ElementColumnBuilder.calculateRightExtentSs(insertionElement, false, true),
                0,
                0,
                null,
                0,
                false
            );

            // Use the same spacing calculation as HorizontalSpacingCalculator
            return HorizontalSpacingCalculator.calculateNextColumnXSs(lastColumn, insertionColumn);
        }

        // Between elements - use midpoint
        var prevElement = line.getElement(insertionIndex - 1);
        var currElement = line.getElement(insertionIndex);

        var prevColumn = elementColumns.get(prevElement);
        var currColumn = elementColumns.get(currElement);

        if (prevColumn == null || currColumn == null) {
            return LayoutConstants.FIRST_NOTE_OFFSET_SS;
        }

        return (prevColumn.getXSs() + currColumn.getXSs()) / 2.0;
    }

    // ==========================================================================
    // Statistics
    // ==========================================================================

    /**
     * Returns the number of element columns in this result.
     */
    public int getElementColumnCount() {
        return elementColumns.size();
    }

    /**
     * Returns the number of line elements in this result.
     */
    public int getElementCount() {
        return elementBounds.size();
    }

    // ==========================================================================
    // Builder
    // ==========================================================================

    /**
     * Builder for creating LayoutResult instances incrementally.
     */
    public static class Builder {

        private final Map<StaffElement, ElementColumn> elementColumns;
        private final Map<LineElement, Bounds> elementBounds;
        private final Map<Interval, BeamLayout> beamLayouts;
        private final Map<StaffElement, StemLayout> stemLayouts;
        private final Map<Interval, TieLayout> tieLayouts;
        private double lineHeightSs = 0;
        private double staffTopYSs = 0;
        private double staffBottomYSs = 0;
        private double lyricBaselineYSs = 0;

        public Builder() {
            this.elementColumns = new HashMap<>();
            this.elementBounds = new HashMap<>();
            this.beamLayouts = new HashMap<>();
            this.stemLayouts = new HashMap<>();
            this.tieLayouts = new HashMap<>();
        }

        /**
         * Adds an element column to the result.
         *
         * @param element The element
         * @param column  The element's column with position
         * @return This builder for chaining
         */
        public Builder putElementColumn(StaffElement element, ElementColumn column) {
            elementColumns.put(element, column);
            return this;
        }

        /**
         * Adds a line element with its bounds to the result.
         *
         * @param element The element
         * @param bounds  The element's bounds
         * @return This builder for chaining
         */
        public Builder putElementBounds(LineElement element, Bounds bounds) {
            elementBounds.put(element, bounds);
            return this;
        }

        /**
         * Sets the total line height.
         *
         * @param lineHeightSs Height in staff-space units
         * @return This builder for chaining
         */
        public Builder setLineHeightSs(double lineHeightSs) {
            this.lineHeightSs = lineHeightSs;
            return this;
        }

        /**
         * Sets the staff geometry.
         *
         * @param staffTopYSs    Y position of top staff line in staff spaces
         * @param staffBottomYSs Y position of bottom staff line in staff spaces
         * @return This builder for chaining
         */
        public Builder setStaffGeometrySs(double staffTopYSs, double staffBottomYSs) {
            this.staffTopYSs = staffTopYSs;
            this.staffBottomYSs = staffBottomYSs;
            return this;
        }

        /**
         * Sets the lyric baseline Y position.
         *
         * @param lyricBaselineYSs Y position in staff spaces (0 if no lyrics)
         * @return This builder for chaining
         */
        public Builder setLyricBaselineYSs(double lyricBaselineYSs) {
            this.lyricBaselineYSs = lyricBaselineYSs;
            return this;
        }

        /**
         * Adds computed beam geometry for a beam interval.
         *
         * @param interval   The beam interval
         * @param beamLayout The computed beam geometry
         * @return This builder for chaining
         */
        public Builder putBeamLayout(Interval interval, BeamLayout beamLayout) {
            beamLayouts.put(interval, beamLayout);
            return this;
        }

        /**
         * Adds computed stem geometry for an unbeamed element.
         *
         * @param element    The element
         * @param stemLayout The computed stem geometry
         * @return This builder for chaining
         */
        public Builder putStemLayout(StaffElement element, StemLayout stemLayout) {
            stemLayouts.put(element, stemLayout);
            return this;
        }

        /**
         * Adds computed tie geometry for a tie interval.
         *
         * @param interval  The tie interval
         * @param tieLayout The computed tie geometry
         * @return This builder for chaining
         */
        public Builder putTieLayout(Interval interval, TieLayout tieLayout) {
            tieLayouts.put(interval, tieLayout);
            return this;
        }

        /**
         * Builds the immutable result.
         *
         * @return The layout result
         */
        public LayoutResult build() {
            return new LayoutResult(
                elementColumns,
                elementBounds,
                beamLayouts,
                stemLayouts,
                tieLayouts,
                lineHeightSs,
                staffTopYSs,
                staffBottomYSs,
                lyricBaselineYSs
            );
        }
    }

    /**
     * Creates a new builder for LayoutResult.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format(
            "LayoutResult{columns=%d, elements=%d, height=%.1f, staff=[%.1f, %.1f], lyrics=%.1f}",
            elementColumns.size(),
            elementBounds.size(),
            lineHeightSs,
            staffTopYSs,
            staffBottomYSs,
            lyricBaselineYSs
        );
    }

    // ==========================================================================
    // Layout Records
    // ==========================================================================

    /**
     * Immutable stem geometry for a single element, computed during layout.
     * <p>
     * All values are in staff-space units.
     *
     * @param topYSs       Y position of the top of the stem
     * @param bottomYSs    Y position of the bottom of the stem
     * @param lengtheningSs Extra stem extension beyond the minimum required to reach the beam (≥ 0)
     * @param stubRight  For partial-beam notes: true if the stub extends to the right, false to the left.
     *                   Meaningless for full-beam and unbeamed notes.
     */
    public record StemLayout(
        double topYSs,
        double bottomYSs,
        double lengtheningSs,
        boolean stubRight) {}

    /**
     * Immutable beam geometry for a beam group, computed during layout.
     * <p>
     * All values are in staff-space units unless noted.
     *
     * @param slope      Beam slope in staff-space units per staff-space unit (dimensionless)
     * @param startYSs     Beam Y position at the first element's X coordinate
     * @param stemsUp    True if stems point upward (beam below noteheads)
     * @param thickeningSs Extra beam thickness from the {@code 1/cos(angle)} raster correction (ss);
     *                   added symmetrically to the nominal {@code BEAM_DEPTH}
     * @param stems      Stem geometry keyed by element, for every element in this beam group
     */
    public record BeamLayout(
        double slope,
        double startYSs,
        boolean stemsUp,
        double thickeningSs,
        Map<StaffElement, StemLayout> stems) {}

    /**
     * Immutable tie geometry, computed during layout.
     * <p>
     * All values are in staff-space units. The outer and inner curves form a filled
     * lens shape when rendered as a closed path: draw the outer cubic Bezier from
     * start to end, then draw the inner cubic Bezier in reverse (end back to start),
     * then close and fill.
     *
     * @param startXSs    Tie start X position
     * @param startYSs    Tie start Y position
     * @param endXSs      Tie end X position
     * @param endYSs      Tie end Y position
     * @param cp1XSs      Outer curve control point 1 X
     * @param cp1YSs      Outer curve control point 1 Y
     * @param cp2XSs      Outer curve control point 2 X
     * @param cp2YSs      Outer curve control point 2 Y
     * @param innerCp1XSs Inner curve control point 1 X
     * @param innerCp1YSs Inner curve control point 1 Y
     * @param innerCp2XSs Inner curve control point 2 X
     * @param innerCp2YSs Inner curve control point 2 Y
     */
    public record TieLayout(
        double startXSs, double startYSs,
        double endXSs, double endYSs,
        double cp1XSs, double cp1YSs,
        double cp2XSs, double cp2YSs,
        double innerCp1XSs, double innerCp1YSs,
        double innerCp2XSs, double innerCp2YSs) {}
}
