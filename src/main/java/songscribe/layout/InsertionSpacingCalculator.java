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

import java.util.Collections;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;

/**
 * Spacing calculations for element insertion operations using layout2 algorithms.
 * <p>
 * This class provides simplified spacing calculations for interactive editing operations
 * (adding elements, inserting elements) without requiring full layout recalculation. It creates
 * lightweight ElementColumns internally to leverage the standard spacing algorithms from
 * {@link HorizontalSpacingCalculator}.
 * <ul>
 *   <li>Append element to end of line: {@link #calculateAppendPositionSs(Line, StaffElement, LayoutResult)}</li>
 *   <li>Insert element in middle: {@link #calculateInsertionShiftSs(Line, StaffElement, int, LayoutResult)}</li>
 * </ul>
 */
public final class InsertionSpacingCalculator {

    /**
     * Result of an insertion spacing calculation, providing the X position for the
     * inserted element, the shift amount for subsequent elements, and the projected
     * line width after the insertion.
     *
     * @param insertedElementXSs          X position where the inserted element should be placed
     * @param shiftForSubsequentElementsSs Amount to shift all elements after the insertion point (always >= 0)
     * @param newLineWidthSs              Projected line width after the insertion
     */
    public record InsertionResult(
        double insertedElementXSs,
        double shiftForSubsequentElementsSs,
        double newLineWidthSs
    ) {
        /**
         * Returns whether the insertion fits within the given right margin.
         * Requires at least the default column gap after the last element
         * so notes don't butt up against the end of the line.
         *
         * @param staffRightMarginSs The maximum allowed line width in staff spaces
         * @return {@code true} if the projected line width does not exceed the margin
         */
        public boolean fitsWithinLine(double staffRightMarginSs) {
            return fitsWithinMarginSs(newLineWidthSs, staffRightMarginSs);
        }
    }

    /**
     * The single line-fit rule: a projected width fits when it leaves at least the default
     * column gap before the right margin, so elements don't butt up against the line end.
     */
    private static boolean fitsWithinMarginSs(double projectedWidthSs, double staffRightMarginSs) {
        return projectedWidthSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS <= staffRightMarginSs;
    }

    private InsertionSpacingCalculator() {
        // Prevent instantiation - utility class with static methods only
    }

    private static double elementXSs(StaffElement element, @Nullable LayoutResult layout) {
        return layout != null
            ? layout.getElementXSs(element)
            : ScaleContext.pxToSs(element.getXOffsetPx());
    }

    /**
     * Calculates the X position for appending an element to the end of a line.
     * <p>
     * This method creates lightweight ElementColumns for the last existing element and the
     * element to append, then uses the standard spacing algorithm to determine where the
     * new element should be placed.
     *
     * @param line            The line to append to
     * @param elementToAppend The element being appended
     * @param layout          Layout result for position lookup; null falls back to {@code xOffset}
     * @return X position in staff spaces where the element should be placed
     */
    public static double calculateAppendPositionSs(
        Line line,
        StaffElement elementToAppend,
        @Nullable LayoutResult layout) {

        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount == 0) {
            return HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
        }

        var lastElement = line.getElement(effectiveCount - 1);
        var lastColumn = createLightweightColumn(lastElement);

        var lastXSs = elementXSs(lastElement, layout);
        lastColumn.setXSs(lastXSs);

        var appendColumn = createLightweightColumn(elementToAppend);

        return HorizontalSpacingCalculator.calculateNextColumnXSs(lastColumn, appendColumn);
    }

    /**
     * Calculates the X position for {@code nextElement} when placed after {@code currentElement},
     * using the current X position of {@code currentElement}.
     * <p>
     * This is useful for paste operations where elements have already been positioned and
     * you need to determine where the next element in sequence should go.
     *
     * @param currentElement An element with its X position already set
     * @param nextElement    The element to be placed after currentElement
     * @return X position where nextElement should be placed
     */
    public static double calculateNextElementXSs(StaffElement currentElement, StaffElement nextElement) {
        var currentColumn = createLightweightColumn(currentElement);
        currentColumn.setXSs(elementXSs(currentElement, null));
        var nextColumn = createLightweightColumn(nextElement);
        return HorizontalSpacingCalculator.calculateNextColumnXSs(currentColumn, nextColumn);
    }

    /**
     * Calculates both the X position for an inserted element and the shift amount for subsequent elements.
     * <p>
     * This method must be called before the element is added to the line, since it examines the
     * line's current state to determine proper spacing.
     *
     * @param line            The line being modified (before insertion)
     * @param insertedElement The element being inserted
     * @param insertIndex     The index where the element will be inserted (0-based)
     * @param layout          Layout result for position lookup; null falls back to {@code xOffset}
     * @return An {@link InsertionResult} with the element's X position and the shift for subsequent elements
     */
    public static InsertionResult calculateInsertion(
        Line line,
        StaffElement insertedElement,
        int insertIndex,
        @Nullable LayoutResult layout) {

        var elementCount = line.elementCount();

        // Validate index
        if (insertIndex < 0 || insertIndex > elementCount) {
            throw new IllegalArgumentException(
                "insertIndex " + insertIndex + " out of bounds [0, " + elementCount + ']');
        }

        // If inserting at end, no shift needed (use calculateAppendPosition instead)
        if (insertIndex == elementCount) {
            var appendXSs = calculateAppendPositionSs(line, insertedElement, layout);
            var appendColumn = createLightweightColumn(insertedElement);
            appendColumn.setXSs(appendXSs);
            return new InsertionResult(appendXSs, 0, appendColumn.getRightEdgeXSs());
        }

        // Create column for inserted element
        var insertedColumn = createLightweightColumn(insertedElement);

        double insertedElementXSs;
        double requiredSpaceSs;

        if (insertIndex == 0) {
            // Inserting at beginning - calculate space from line start
            insertedElementXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            var nextElement = line.getElement(0);
            var nextColumn = createLightweightColumn(nextElement);

            // Space needed: firstElementX → inserted element → existing first element
            insertedColumn.setXSs(insertedElementXSs);
            var insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                insertedColumn, nextColumn);

            // Shift = (where first element needs to be) - (where it currently is)
            requiredSpaceSs = insertedToNextSs - elementXSs(nextElement, layout);
        } else {
            // Inserting in middle - calculate space between prev and next
            var prevElement = line.getElement(insertIndex - 1);
            var nextElement = line.getElement(insertIndex);

            var prevColumn = createLightweightColumn(prevElement);
            var nextColumn = createLightweightColumn(nextElement);

            prevColumn.setXSs(elementXSs(prevElement, layout));

            // Calculate: prev → inserted → next
            insertedElementXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                prevColumn, insertedColumn);
            insertedColumn.setXSs(insertedElementXSs);

            var insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                insertedColumn, nextColumn);

            // Shift = (where next needs to be) - (where it currently is)
            requiredSpaceSs = insertedToNextSs - elementXSs(nextElement, layout);
        }

        var shiftSs = Math.max(0, requiredSpaceSs);

        // Compute projected line width: max of inserted element's right edge
        // and the last real element's right edge shifted by the insertion shift.
        // Exclude the auto-maintained FINAL_DOUBLE_BARLINE — its position is fixed.
        insertedColumn.setXSs(insertedElementXSs);
        var newLineWidthSs = projectedWidthWithLastShiftSs(line, insertedColumn.getRightEdgeXSs(), shiftSs, layout);

        return new InsertionResult(insertedElementXSs, shiftSs, newLineWidthSs);
    }

    /**
     * Calculates the horizontal shift amount needed when inserting an element at a given index.
     * <p>
     * This determines how much existing elements after the insertion point need to shift right
     * to accommodate the inserted element with proper spacing.
     *
     * @param line            The line being modified
     * @param insertedElement The element being inserted
     * @param insertIndex     The index where the element is being inserted (0-based)
     * @param layout          Layout result for position lookup; null falls back to {@code xOffset}
     * @return Shift amount in staff spaces (positive = shift right)
     */
    public static double calculateInsertionShiftSs(
        Line line,
        StaffElement insertedElement,
        int insertIndex,
        @Nullable LayoutResult layout) {

        return calculateInsertion(line, insertedElement, insertIndex, layout).shiftForSubsequentElementsSs();
    }

    /**
     * Determines whether a grace note will fit on a line when inserted at the given index.
     *
     * @param line    The line to check (before insertion)
     * @param atIndex The index where the grace note would be inserted
     * @param layout  Layout result for position lookup; null falls back to {@code xOffset}
     * @return {@code true} if the grace note fits on the line
     */
    public static boolean hasRoomForGraceNote(Line line, int atIndex, @Nullable LayoutResult layout) {
        var staffRightMarginSs = line.getSong().getLineWidthSs();
        // Shared singleton is safe: calculateInsertion only reads geometry from the element.
        var graceNote = ElementType.GRACE_QUAVER.getInstance();
        return calculateInsertion(line, graceNote, atIndex, layout).fitsWithinLine(staffRightMarginSs);
    }

    /**
     * Determines whether a host note (crotchet) will fit on a line immediately after the grace
     * note at the given index. The grace note must already be in the line.
     * <p>
     * Uses element xOffset values since the layout may be stale after grace note insertion.
     *
     * @param line           The line containing the grace note (after insertion)
     * @param graceNoteIndex The index of the grace note already in the line
     * @return {@code true} if a host note fits after the grace note
     */
    public static boolean hasRoomForHostNoteAfterGrace(Line line, int graceNoteIndex) {
        var staffRightMarginSs = line.getSong().getLineWidthSs();
        // Shared singleton is safe: calculateInsertion only reads geometry from the element.
        var hostNote = ElementType.CROTCHET.getInstance();
        return calculateInsertion(line, hostNote, graceNoteIndex + 1, null).fitsWithinLine(staffRightMarginSs);
    }

    /**
     * Determines whether a fall will fit on a line when applied to the element at the given index.
     * <p>
     * A fall extends the source element's right extent. When that extension is large enough to push
     * subsequent elements right, this method checks whether the last element on the line would
     * overflow the right margin.
     *
     * @param line        The line to check
     * @param sourceIndex The index of the element the fall would be applied to
     * @param layout      Layout result for position lookup; null falls back to {@code xOffset}
     * @return {@code true} if the fall fits on the line
     */
    public static boolean hasRoomForFall(Line line, int sourceIndex, @Nullable LayoutResult layout) {
        var sourceElement = line.getElement(sourceIndex);

        // Fall already present — no change in right extent, always fits
        if (sourceElement.hasFall()) {
            return true;
        }

        // Measure a clone carrying the fall so the live model element is never mutated,
        // mirroring the read-only geometry contract of hasRoomForGraceNote.
        var sourceWithFall = sourceElement.clone();
        sourceWithFall.setFall();
        var sourceColumnWithFall = createLightweightColumn(sourceWithFall);

        // The clone is absent from the layout map, so look up its X by the original element.
        sourceColumnWithFall.setXSs(elementXSs(sourceElement, layout));

        var effectiveCount = line.effectiveElementCount();
        var projectedWidthSs = sourceColumnWithFall.getRightEdgeXSs();

        // If there are effective elements after the source, the fall's larger right extent
        // may push them right — check whether the last element would overflow the margin.
        if (sourceIndex + 1 < effectiveCount) {
            var nextElement = line.getElement(sourceIndex + 1);
            var nextColumn = createLightweightColumn(nextElement);
            var currentNextXSs = elementXSs(nextElement, layout);
            var requiredNextXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(sourceColumnWithFall, nextColumn);
            var shiftSs = Math.max(0, requiredNextXSs - currentNextXSs);

            if (shiftSs > 0) {
                projectedWidthSs = projectedWidthWithLastShiftSs(line, projectedWidthSs, shiftSs, layout);
            }
        }

        return fitsWithinMarginSs(projectedWidthSs, line.getSong().getLineWidthSs());
    }

    /**
     * Projects the line width after shifting the last effective element right by {@code shiftSs},
     * taking the larger of {@code baseWidthSs} and that element's shifted right edge. Returns
     * {@code baseWidthSs} unchanged when the line has no effective elements.
     */
    private static double projectedWidthWithLastShiftSs(
        Line line, double baseWidthSs, double shiftSs, @Nullable LayoutResult layout) {

        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount == 0) {
            return baseWidthSs;
        }

        var lastElement = line.getElement(effectiveCount - 1);
        var lastColumn = createLightweightColumn(lastElement);
        lastColumn.setXSs(elementXSs(lastElement, layout) + shiftSs);

        return Math.max(baseWidthSs, lastColumn.getRightEdgeXSs());
    }

    /**
     * Creates a lightweight ElementColumn for spacing calculations.
     * <p>
     * This column has accurate geometric extents but no syllable information, since
     * insertion operations typically happen during editing before lyrics are finalized.
     *
     * @param element The element to create a column for
     * @return An ElementColumn with geometric extents but no syllable data
     */
    private static ElementColumn createLightweightColumn(StaffElement element) {
        // Calculate geometric extents using ElementColumnBuilder's static methods
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, element.isUpper());
        var rightExtentExcludingAugmentationSs = ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(element, false, element.isUpper());

        // For insertion operations, we don't need stem positions or beam group info
        // since we're only calculating horizontal spacing
        double stemTopSs = 0;
        double stemBottomSs = 0;

        // No syllable information for lightweight columns
        String syllable = null;
        double syllableWidthSs = 0;

        //noinspection ConstantValue
        return new ElementColumn(
            element,
            Collections.emptyList(),  // No grace notes
            leftExtentSs,
            rightExtentSs,
            rightExtentExcludingAugmentationSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllableWidthSs,
            false
        );
    }
}
