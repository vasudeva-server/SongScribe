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

package songscribe.layout;

import module java.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Attachment;
import songscribe.dom.Beam;
import songscribe.dom.Clef;
import songscribe.dom.CollisionRegion;
import songscribe.dom.KeySignature;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.RangeElement;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;

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
     * Offset for positioning a preview element before the first element in the line (ss).
     */
    private static final double PREVIEW_BEFORE_FIRST_OFFSET_SS = 1.875;  // 15px

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<LineElement, ElementBoundsSs> elementBounds;
    private final Map<Beam, BeamLayout> beamLayouts;
    /**
     * Flat lookup keyed by element for every stem in the line — beamed stems
     * (extracted from {@link BeamLayout#stems()}) and unbeamed stems alike.
     * Built once at construction so per-element render lookups are O(1) instead
     * of O(beam-count).
     */
    private final Map<StaffElement, StemLayout> allStemLayouts;
    private final Map<Tie, TieLayout> tieLayouts;
    private final Map<LineElement, DecorationLayout> decorationLayouts;
    @Nullable
    private final Clef clef;
    @Nullable
    private final KeySignature keySignature;
    private final double lineHeightSs;
    private final double aboveStaffSs;
    private final double belowContentSs;
    private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
    private final List<LyricConnectorLayout> lyricConnectors;
    private final int verseCount;
    private final boolean hasTrailingLyricContinuation;

    /**
     * Creates a layout result with the given data.
     * <p>
     * Use {@link Builder} rather than calling this constructor directly.
     *
     * @param elementColumns   Map of elements to their columns with positions
     * @param elementBounds    Map of line elements to their bounds
     * @param beamLayouts      Map of beam spans to their computed beam geometry
     * @param stemLayouts      Map of unbeamed notes to their computed stem geometry
     * @param tieLayouts       Map of tie spans to their computed tie geometry
     * @param lineHeightSs       Total height of the line in staff spaces (including staff, elements, and lyrics)
     * @param aboveStaffSs       Staff-space amount reserved above the staff top, i.e. the staff top's
     *                           Y position within the line's local coordinate frame
     * @param belowContentSs     Actual extent of staff-element content below the staff bottom, in staff spaces;
     *                           the anchor for placing lyrics, distinct from the layout reservation
     */
    private LayoutResult(
        Map<StaffElement, ElementColumn> elementColumns,
        Map<LineElement, ElementBoundsSs> elementBounds,
        Map<Beam, BeamLayout> beamLayouts,
        Map<StaffElement, StemLayout> stemLayouts,
        Map<Tie, TieLayout> tieLayouts,
        Map<LineElement, DecorationLayout> decorationLayouts,
        @Nullable Clef clef,
        @Nullable KeySignature keySignature,
        double lineHeightSs,
        double aboveStaffSs,
        double belowContentSs,
        Map<StaffElement, List<LyricBoxLayout>> lyricBoxes,
        List<LyricConnectorLayout> lyricConnectors,
        int verseCount,
        boolean hasTrailingLyricContinuation) {
        this.elementColumns = Map.copyOf(elementColumns);
        this.elementBounds = Map.copyOf(elementBounds);
        this.beamLayouts = Map.copyOf(beamLayouts);
        var stemLayouts1 = Map.copyOf(stemLayouts);

        var mergedStems = new HashMap<>(stemLayouts);

        for (var beamLayout : beamLayouts.values()) {
            mergedStems.putAll(beamLayout.stems());
        }

        allStemLayouts = Map.copyOf(mergedStems);
        this.tieLayouts = Map.copyOf(tieLayouts);
        this.decorationLayouts = Map.copyOf(decorationLayouts);
        this.clef = clef;
        this.keySignature = keySignature;
        this.lineHeightSs = lineHeightSs;
        this.aboveStaffSs = aboveStaffSs;
        this.belowContentSs = belowContentSs;
        var copiedBoxes = new HashMap<StaffElement, List<LyricBoxLayout>>(lyricBoxes.size() * 2);

        for (var entry : lyricBoxes.entrySet()) {
            copiedBoxes.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        this.lyricBoxes = Collections.unmodifiableMap(copiedBoxes);
        this.lyricConnectors = List.copyOf(lyricConnectors);
        this.verseCount = verseCount;
        this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
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
     * Returns the beam geometry for a beam span.
     *
     * @param span The beam span to look up
     * @return The beam layout, or null if not computed
     */
    public @Nullable BeamLayout getBeamLayout(Beam beam) {
        return beamLayouts.get(beam);
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
        return allStemLayouts.get(element);
    }

    // ==========================================================================
    // Tie Layout Access
    // ==========================================================================

    /**
     * Returns the tie geometry for a tie span, if it was computed during layout.
     * <p>
     * Returns null when the span was not laid out (e.g., degenerate
     * tie spanning notes that could not be positioned). Callers should skip rendering
     * if the result is null.
     *
     * @param span The tie span to look up
     * @return the tie layout, or null if not computed
     */
    @Nullable
    public TieLayout getTieLayout(Tie tie) {
        return tieLayouts.get(tie);
    }

    // ==========================================================================
    // Decoration + Span Layout Access
    // ==========================================================================

    /**
     * Returns the decoration layout for an above-staff decoration element.
     *
     * @param element The decoration element to look up
     * @return The decoration layout, or null if not computed
     */
    public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
        return decorationLayouts.get(element);
    }

    /**
     * Returns an unmodifiable view of all decoration layouts.
     *
     * @return Map of decoration elements to their layouts
     */
    public Map<LineElement, DecorationLayout> getDecorationLayouts() {
        return decorationLayouts;
    }

    /**
     * Returns all decoration layout entries whose key is an instance of the given type.
     * <p>
     * Used by renderers to iterate all elements of a specific type (e.g., all Trills,
     * all Crescendos) in a single pass, regardless of whether they originated from
     * new range elements or were bridged from legacy flags during layout.
     *
     * @param type The element type to filter by
     * @return List of matching entries (element + layout)
     */
    public <T extends LineElement> List<Map.Entry<T, DecorationLayout>> getDecorationLayoutsByType(
        Class<? extends T> type) {

        var result = new ArrayList<Map.Entry<T, DecorationLayout>>();

        for (var entry : decorationLayouts.entrySet()) {
            if (type.isInstance(entry.getKey())) {
                result.add(Map.entry(type.cast(entry.getKey()), entry.getValue()));
            }
        }

        return result;
    }

    /**
     * Finds the decoration layout for an attachment with the given owner element and type.
     * <p>
     * Used by renderers that need to look up layout results for attachments
     * but don't have direct access to the attachment object created during layout.
     *
     * @param ownerElement   The element the attachment is attached to
     * @param attachmentType The type of attachment to find
     * @return The decoration layout if found, null otherwise
     */
    public @Nullable DecorationLayout findAttachmentDecorationLayout(
        StaffElement ownerElement,
        Class<? extends Attachment> attachmentType) {

        return findByAttachment(decorationLayouts, ownerElement, attachmentType);
    }

    /**
     * Finds the decoration layout for a range element with the given anchor element and type.
     * <p>
     * Used by renderers that need to look up layout results for range elements
     * but don't have direct access to the range element object created during layout.
     *
     * @param anchorElement    The anchor (start) element of the range
     * @param rangeElementType The type of range element to find
     * @return The decoration layout if found, null otherwise
     */
    public @Nullable DecorationLayout findRangeElementDecorationLayout(
        StaffElement anchorElement,
        Class<? extends RangeElement> rangeElementType) {

        for (var entry : decorationLayouts.entrySet()) {
            var element = entry.getKey();

            if (rangeElementType.isInstance(element)) {
                var rangeElement = (RangeElement) element;

                if (rangeElement.getAnchorElement() == anchorElement) {
                    return entry.getValue();
                }
            }
        }

        return null;
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
    public @Nullable ElementBoundsSs getElementBounds(LineElement element) {
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

        return new Point2D.Double(bounds.getLeftSs(), bounds.getTopSs());
    }

    /**
     * Returns an unmodifiable view of all element bounds.
     *
     * @return Map of line elements to their bounds
     */
    public Map<LineElement, ElementBoundsSs> getElementBounds() {
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
    // Header Element Access
    // ==========================================================================

    /**
     * Returns the clef element for this line.
     *
     * @return The clef, or null if not yet created (empty line)
     */
    public @Nullable Clef getClef() {
        return clef;
    }

    /**
     * Returns the key signature element for this line.
     *
     * @return The key signature, or null if not yet created (empty line)
     */
    public @Nullable KeySignature getKeySignature() {
        return keySignature;
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
    public @Nullable ElementBoundsSs getBounds(Object element) {
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
    public @Nullable ElementBoundsSs findAttachmentBounds(
        StaffElement parentElement,
        Class<? extends Attachment> attachmentType) {

        return findByAttachment(elementBounds, parentElement, attachmentType);
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
    public @Nullable <A extends Attachment> A findAttachment(
        StaffElement parentElement,
        Class<A> attachmentType) {

        for (var element : elementBounds.keySet()) {
            if (attachmentType.isInstance(element)) {
                var attachment = (Attachment) element;

                if (attachment.getOwnerElement() == parentElement) {
                    return (A) attachment;
                }
            }
        }

        return null;
    }

    private <T> @Nullable T findByAttachment(
        Map<LineElement, T> map,
        StaffElement ownerElement,
        Class<? extends Attachment> attachmentType) {

        for (var entry : map.entrySet()) {
            var element = entry.getKey();

            if (attachmentType.isInstance(element)) {
                var attachment = (Attachment) element;

                if (attachment.getOwnerElement() == ownerElement) {
                    return entry.getValue();
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
    public @Nullable ElementBoundsSs findRangeElementBounds(
        StaffElement anchorElement,
        StaffElement endElement,
        Class<? extends RangeElement> rangeElementType) {

        for (var entry : elementBounds.entrySet()) {
            var element = entry.getKey();

            if (rangeElementType.isInstance(element)) {
                var rangeElement = (RangeElement) element;

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
        return element instanceof LineElement && elementBounds.containsKey((LineElement) element);
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
     * Returns the staff-space amount reserved above the staff top.
     * <p>
     * This is also the Y position of the staff top within the line's local coordinate
     * frame (component top = y=0). Used by consumers to place the staff vertically
     * within the allocated line height.
     */
    public double getAboveStaffSs() {
        return aboveStaffSs;
    }

    /**
     * Returns the actual extent of staff-element content below the staff bottom, in staff spaces.
     * <p>
     * This is the lyric-positioning anchor: the maximum distance below the staff bottom that
     * any staff element (notehead, stem, decoration) reaches on this line. Distinct from the
     * layout reservation embedded in {@link #getLineHeightSs()}, which floors at
     * {@link StaffExtents#MIN_BELOW_STAFF_SS} for ledger-line capacity.
     */
    public double getBelowContentSs() {
        return belowContentSs;
    }

    /**
     * Returns the below-staff reservation embedded in this line's height, in staff spaces.
     * <p>
     * Equals {@code lineHeightSs - aboveStaffSs - STAFF_HEIGHT_SS}: the part of the line that
     * sits below the staff bottom including the {@link StaffExtents#MIN_BELOW_STAFF_SS}
     * floor and the inter-line margin. Use {@link #getBelowContentSs()} when you need the
     * actual content extent without the floor or margin.
     */
    public double getBelowStaffReservationSs() {
        return lineHeightSs - aboveStaffSs - StaffExtents.STAFF_HEIGHT_SS;
    }

    // ==========================================================================
    // Lyric Layout
    // ==========================================================================

    /**
     * Returns the lyric boxes for an element, one per verse, in verse order.
     *
     * @param element the element to look up
     * @return immutable list of lyric boxes; empty if the element has no lyrics
     */
    public List<LyricBoxLayout> getLyricBoxes(StaffElement element) {
        var boxes = lyricBoxes.get(element);
        return boxes != null ? boxes : List.of();
    }

    public @Nullable LyricHit hitTestLyric(LyricRenderMetrics lyricRenderMetrics, Line line, Point2D pointPx) {
        var rowHeightSs = lyricRenderMetrics.lyricBoxHeightSs();
        var baseYSs = lyricAreaBaseYSs();
        var pointXSs = ScaleContext.pxToSs(pointPx.getX());
        var pointYSs = ScaleContext.pxToSs(pointPx.getY());

        for (var element : line.getElements()) {
            for (var box : getLyricBoxes(element)) {
                var rowTopYSs = baseYSs + (box.verseIndex() - 1) * rowHeightSs;
                var rowBottomYSs = rowTopYSs + rowHeightSs;

                if (pointXSs >= box.xSs()
                    && pointXSs <= box.xSs() + box.widthSs()
                    && pointYSs >= rowTopYSs
                    && pointYSs <= rowBottomYSs) {
                    return new LyricHit(element, box.verseIndex());
                }
            }
        }

        return null;
    }

    /**
     * Returns true if {@code pointYPx} (in pixels) falls within the vertical extent
     * of the lyric area on this line, regardless of X position.
     * Returns false if the line has no lyrics.
     */
    public boolean isYInLyricBounds(LyricRenderMetrics lyricRenderMetrics, double pointYPx) {
        if (verseCount == 0) {
            return false;
        }

        var rowHeightSs = lyricRenderMetrics.lyricBoxHeightSs();
        var baseYSs = lyricAreaBaseYSs();
        var pointYSs = ScaleContext.pxToSs(pointYPx);

        return pointYSs >= baseYSs && pointYSs <= baseYSs + verseCount * rowHeightSs;
    }

    // Package-private for direct unit testing of the formula.
    double lyricAreaBaseYSs() {
        return aboveStaffSs + StaffExtents.STAFF_HEIGHT_SS + belowContentSs + SongLayoutMetricsBuilder.LYRICS_ROW_MARGIN_SS;
    }

    /**
     * Returns the center-X and baseline-Y anchor for the lyric editor on {@code element}.
     * <p>
     * Uses the verse-1 lyric box when one exists (box-anchored branch); otherwise falls back to
     * the element column's horizontal center (column-anchored branch). Throws
     * {@link IllegalStateException} when neither a box nor a column is available — that indicates
     * a broken layout state, not a recoverable condition.
     *
     * @param element           the element to anchor on
     * @param songLayoutMetrics song-wide metrics providing the verse-1 baseline Y
     * @return the lyric anchor for positioning the editor
     * @throws IllegalStateException if neither a lyric box nor an element column exists for the element
     */
    public LyricAnchor getLyricAnchor(StaffElement element, SongLayoutMetrics songLayoutMetrics) {
        var boxes = getLyricBoxes(element);
        var baselineYSs = songLayoutMetrics.verseYSsInLine(1);

        if (!boxes.isEmpty()) {
            var box = boxes.getFirst();
            var centerXSs = box.xSs() + box.widthSs() / 2.0;
            return new LyricAnchor(centerXSs, baselineYSs);
        }

        var column = getElementColumn(element);

        if (column == null) {
            throw new IllegalStateException(
                "getLyricAnchor: no lyric box and no column for element " + element);
        }

        var centerXSs = column.getXSs() + column.getRightExtentSs() / 2.0;
        return new LyricAnchor(centerXSs, baselineYSs);
    }

    /**
     * Returns all lyric connectors (hyphens, melisma extenders) on this line.
     *
     * @return immutable list of connectors in the order they were emitted
     */
    public List<LyricConnectorLayout> getLyricConnectors() {
        return lyricConnectors;
    }

    /**
     * Returns the highest verse index present on this line, or 0 if no lyrics.
     */
    public int verseCount() {
        return verseCount;
    }

    /**
     * Returns true when at least one melisma extender on this line spans past the last
     * note and continues onto the following line. Layout passes that build the next line
     * use this to know whether to emit a leading-stub extender from the line's left edge
     * to its first lyric-bearing element.
     */
    public boolean hasTrailingLyricContinuation() {
        return hasTrailingLyricContinuation;
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
            var rightEdge = column.getRightEdgeXSs();

            if (rightEdge > maxX) {
                maxX = rightEdge;
            }
        }

        return maxX;
    }

    // ==========================================================================
    // Preview Element Positioning (Edit Mode)
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
    public int findElementAtXSs(double mouseXSs, Line line) {
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
    public int findInsertionIndex(double mouseXSs, Line line) {
        // Exclude the auto-maintained terminal: insertions always occur before it,
        // and the gap between the last real element and the barline should behave as
        // "after the last element" rather than a between-elements midpoint.
        var elementCount = line.effectiveElementCount();

        if (elementCount == 0) {
            return 0;
        }

        // Check each element to see if mouse is within its head bounds
        var elementAtX = findElementAtXSs(mouseXSs, line);

        if (elementAtX >= 0 && elementAtX < elementCount) {
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
     * Calculates the X position for rendering a preview element at a given index.
     * <p>
     * If the mouse is within the horizontal bounds of an element head, snaps to that element's position.
     * Otherwise, positions between elements or after the last element as appropriate.
     *
     * @param insertionIndex   The insertion index (0 to elementCount inclusive)
     * @param mouseXSs         Mouse X coordinate in staff-space units (used to detect if over an element head)
     * @param previewElement The element to be inserted (used to calculate extents for after-last positioning)
     * @param line             The line containing the elements
     * @return X position in staff-space units for rendering the preview element
     */
    public double calculateInsertionXSs(
        int insertionIndex,
        double mouseXSs,
        StaffElement previewElement,
        Line line) {

        // Exclude the auto-maintained terminal from positioning decisions —
        // it sits at the line's right edge and must not be treated as a real
        // neighbour for spacing the preview.
        var elementCount = line.effectiveElementCount();

        // Empty line - use first element position (clef + key signature + offset)
        if (elementCount == 0) {
            return HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
        }

        // Check if mouse is over any element head - if so, snap to that element's position.
        // Include the terminal so hovering over it snaps the preview to its position.
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);
            var column = elementColumns.get(element);

            if (column == null) {
                continue;
            }

            var elementX = column.getXSs();

            if (mouseXSs >= elementX && mouseXSs <= elementX + column.getRightExtentSs()) {
                // Mouse is over this element head - snap to its position.
                // For the terminal, right-align the preview to the terminal's right edge
                // so a wider replacement (e.g. REPEAT_RIGHT replacing FINAL_DOUBLE_BARLINE)
                // doesn't overflow the staff boundary.
                if (line.getSong().isAutoMaintainedTerminal(element, line)) {
                    var previewRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                            previewElement, false, true);
                    return elementX + column.getRightExtentSs() - previewRightExtentSs;
                }

                return elementX;
            }
        }

        // Mouse is not over an element head - handle insertion

        // Before first element - position to the left
        if (insertionIndex == 0) {
            var firstElement = line.getElement(0);
            var firstColumn = elementColumns.get(firstElement);

            if (firstColumn == null) {
                return HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
            }

            return firstColumn.getXSs() - PREVIEW_BEFORE_FIRST_OFFSET_SS;
        }

        // After last element - use same spacing logic as layout engine
        if (insertionIndex >= elementCount) {
            var lastElement = line.getElement(elementCount - 1);
            var lastColumn = elementColumns.get(lastElement);

            if (lastColumn == null) {
                return HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
            }

            // Build a temporary column for the preview element to calculate proper spacing
            var insertionColumn = new ElementColumn(
                previewElement,
                Collections.emptyList(),
                ElementColumnBuilder.calculateLeftExtentSs(previewElement),
                ElementColumnBuilder.calculateRightExtentSs(previewElement, false, true),
                ElementColumnBuilder.calculateRightExtentExcludingDotsSs(previewElement, false, true),
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
            return HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
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
        private final Map<LineElement, ElementBoundsSs> elementBounds;
        private final Map<Beam, BeamLayout> beamLayouts;
        private final Map<StaffElement, StemLayout> stemLayouts;
        private final Map<Tie, TieLayout> tieLayouts;
        private final Map<LineElement, DecorationLayout> decorationLayouts;
        @Nullable
        private Clef clef;
        @Nullable
        private KeySignature keySignature;
        private double lineHeightSs = 0;
        private double aboveStaffSs = 0;
        private double belowContentSs = 0;
        private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
        private final List<LyricConnectorLayout> lyricConnectors;
        private int verseCount = 0;
        private boolean hasTrailingLyricContinuation = false;

        public Builder() {
            elementColumns = new HashMap<>();
            elementBounds = new HashMap<>();
            beamLayouts = new HashMap<>();
            stemLayouts = new HashMap<>();
            tieLayouts = new HashMap<>();
            decorationLayouts = new HashMap<>();
            lyricBoxes = new HashMap<>();
            lyricConnectors = new ArrayList<>();
        }

        /**
         * Sets the clef element for this line.
         *
         * @param clef The clef element
         * @return This builder for chaining
         */
        public Builder setClef(Clef clef) {
            this.clef = clef;
            return this;
        }

        /**
         * Sets the key signature element for this line.
         *
         * @param keySignature The key signature element
         * @return This builder for chaining
         */
        public Builder setKeySignature(KeySignature keySignature) {
            this.keySignature = keySignature;
            return this;
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
        public Builder putElementBounds(LineElement element, ElementBoundsSs bounds) {
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
         * Sets the staff-space amount reserved above the staff top.
         *
         * @param aboveStaffSs Staff-space amount above the staff top (i.e. the staff top's
         *                     Y position within the line's local coordinate frame)
         * @return This builder for chaining
         */
        public Builder setAboveStaffSs(double aboveStaffSs) {
            this.aboveStaffSs = aboveStaffSs;
            return this;
        }

        /**
         * Sets the actual extent of staff-element content below the staff bottom.
         *
         * @param belowContentSs Distance below the staff bottom in staff-space units
         * @return This builder for chaining
         */
        public Builder setBelowContentSs(double belowContentSs) {
            this.belowContentSs = belowContentSs;
            return this;
        }

        /**
         * Adds a lyric box for an element. Multiple boxes per element are allowed (one per verse);
         * insertion order is preserved.
         *
         * @param element the element the box is anchored to
         * @param box     the lyric box layout
         * @return this builder for chaining
         */
        public Builder addLyricBox(StaffElement element, LyricBoxLayout box) {
            lyricBoxes.computeIfAbsent(element, e -> new ArrayList<>()).add(box);
            return this;
        }

        /**
         * Adds a lyric connector (hyphen or extender) to the line.
         *
         * @param connector the connector layout
         * @return this builder for chaining
         */
        public Builder addLyricConnector(LyricConnectorLayout connector) {
            lyricConnectors.add(connector);
            return this;
        }

        /**
         * Sets the highest verse index present on this line.
         *
         * @param verseCount the verse count (0 if no lyrics)
         * @return this builder for chaining
         */
        public Builder setVerseCount(int verseCount) {
            this.verseCount = verseCount;
            return this;
        }

        /**
         * Marks whether this line ends with an active melisma extender that continues onto
         * the next line.
         *
         * @param hasTrailingLyricContinuation true if continuation continues past the last note
         * @return this builder for chaining
         */
        public Builder setHasTrailingLyricContinuation(boolean hasTrailingLyricContinuation) {
            this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
            return this;
        }

        /**
         * Adds computed beam geometry for a beam span.
         *
         * @param span       The beam span
         * @param beamLayout The computed beam geometry
         * @return This builder for chaining
         */
        public Builder putBeamLayout(Beam beam, BeamLayout beamLayout) {
            beamLayouts.put(beam, beamLayout);
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
         * Adds computed tie geometry for a tie span.
         *
         * @param span      The tie span
         * @param tieLayout The computed tie geometry
         * @return This builder for chaining
         */
        public Builder putTieLayout(Tie tie, TieLayout tieLayout) {
            tieLayouts.put(tie, tieLayout);
            return this;
        }

        /**
         * Adds computed decoration layout for an above-staff decoration element.
         *
         * @param element          The decoration element
         * @param decorationLayout The computed decoration geometry
         * @return This builder for chaining
         */
        public Builder putDecorationLayout(LineElement element, DecorationLayout decorationLayout) {
            decorationLayouts.put(element, decorationLayout);
            return this;
        }

        /**
         * Returns the decoration layout entries for iteration.
         * <p>
         * Used by the post-layout offset application pass to read and update
         * decoration positions with manual user offsets.
         *
         * @return The decoration layout entry set (mutable — callers may update via
         *         {@link #putDecorationLayout})
         */
        public Set<Map.Entry<LineElement, DecorationLayout>> getDecorationLayoutEntries() {
            return decorationLayouts.entrySet();
        }

        /**
         * Returns the decoration layout for a specific element, or null if not yet computed.
         *
         * @param element The decoration element to look up
         * @return The decoration layout, or null if not present
         */
        public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
            return decorationLayouts.get(element);
        }

        /**
         * Returns the tie layout for a tie span from the builder's accumulated data.
         *
         * @param span The tie span to look up
         * @return The tie layout, or null if not yet computed
         */
        public @Nullable TieLayout getTieLayout(Tie tie) {
            return tieLayouts.get(tie);
        }

        /**
         * Returns the stem layout for an element from the builder's accumulated data.
         * <p>
         * Checks beamed stem layouts first, then standalone stem layouts.
         * Used by the vertical stacking calculator to seed note bounding areas.
         *
         * @param element The element to look up
         * @return The stem layout, or null if not yet computed
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
                decorationLayouts,
                clef,
                keySignature,
                lineHeightSs,
                aboveStaffSs,
                belowContentSs,
                lyricBoxes,
                lyricConnectors,
                verseCount,
                hasTrailingLyricContinuation
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
            "LayoutResult{columns=%d, elements=%d, decorations=%d, height=%.1f, aboveStaff=%.1f}",
            elementColumns.size(),
            elementBounds.size(),
            decorationLayouts.size(),
            lineHeightSs,
            aboveStaffSs
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

    /**
     * Immutable positioned bounds of a single above-staff decoration, computed during layout.
     * <p>
     * All values are in staff-space units.
     *
     * @param xSs      X position (left edge) of the decoration
     * @param ySs      Y position (top edge) of the decoration
     * @param widthSs  Width of the decoration
     * @param heightSs Height of the decoration
     * @param marginSs Bottom margin (space between element bottom and the tier below)
     * @param regions  Collision sub-regions for composite elements, empty for simple elements
     */
    public record DecorationLayout(
        double xSs,
        double ySs,
        double widthSs,
        double heightSs,
        double marginSs,
        List<CollisionRegion> regions) {

        /**
         * Creates a DecorationLayout without collision sub-regions (simple elements).
         */
        public DecorationLayout(
            double xSs, double ySs, double widthSs,
            double heightSs, double marginSs
        ) {
            this(xSs, ySs, widthSs, heightSs, marginSs, List.of());
        }
    }

    /**
     * Center-X and baseline-Y anchor returned by {@link #getLyricAnchor}.
     * <p>
     * All values are in staff-space units.
     *
     * @param centerXSs    horizontal center of the lyric box (or element column) in staff spaces
     * @param baselineYSs  verse-1 text baseline Y in staff spaces
     */
    public record LyricAnchor(double centerXSs, double baselineYSs) {}
    public record LyricHit(StaffElement element, int verse) {}
}
