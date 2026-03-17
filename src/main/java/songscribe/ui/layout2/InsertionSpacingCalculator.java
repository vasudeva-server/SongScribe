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

package songscribe.ui.layout2;

import java.util.Collections;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

/**
 * Spacing calculations for element insertion operations using layout2 algorithms.
 * <p>
 * This class provides simplified spacing calculations for interactive editing operations
 * (adding elements, inserting elements) without requiring full layout recalculation. It creates
 * lightweight ElementColumns internally to leverage the standard spacing algorithms from
 * {@link HorizontalSpacingCalculator}.
 * <ul>
 *   <li>Append element to end of line: {@link #calculateAppendPositionSs(Line, StaffElement)}</li>
 *   <li>Insert element in middle: {@link #calculateInsertionShiftSs(Line, StaffElement, int)}</li>
 * </ul>
 */
public class InsertionSpacingCalculator {

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
            return newLineWidthSs + LayoutConstants.DEFAULT_COLUMN_GAP_SS <= staffRightMarginSs;
        }
    }

    private InsertionSpacingCalculator() {
        // Prevent instantiation - utility class with static methods only
    }

    /**
     * Converts an element's pixel position (stored in {@code getXPosSs()}, a legacy misnomer)
     * to true staff spaces.
     */
    private static double elementXSs(@NotNull StaffElement element) {
        return ScaleContext.getInstance().fromPixels(element.getXPosSs());
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
     * @return X position in staff spaces where the element should be placed
     */
    public static double calculateAppendPositionSs(@NotNull Line line, @NotNull StaffElement elementToAppend) {
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            // First element on the line - use standard first element positioning
            return LayoutConstants.calculateFirstElementXSs(line.getKeyAccidentalCount());
        }

        // Get the last element and create a column for it
        var lastElement = line.getElement(elementCount - 1);
        var lastColumn = createLightweightColumn(lastElement);

        // Convert element's pixel position to staff spaces
        lastColumn.setXSs(elementXSs(lastElement));

        // Create column for element to append
        var appendColumn = createLightweightColumn(elementToAppend);

        // Calculate where the new element should go
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
    public static double calculateNextElementXSs(@NotNull StaffElement currentElement, @NotNull StaffElement nextElement) {
        var currentColumn = createLightweightColumn(currentElement);
        currentColumn.setXSs(elementXSs(currentElement));
        var nextColumn = createLightweightColumn(nextElement);
        return HorizontalSpacingCalculator.calculateNextColumnXSs(currentColumn, nextColumn);
    }

    /**
     * Calculates both the X position for an inserted element and the shift amount for subsequent elements.
     * <p>
     * This method must be called before the element is added to the line, since it examines the
     * line's current state to determine proper spacing.
     *
     * @param line          The line being modified (before insertion)
     * @param insertedElement The element being inserted
     * @param insertIndex   The index where the element will be inserted (0-based)
     * @return An {@link InsertionResult} with the element's X position and the shift for subsequent elements
     */
    public static @NotNull InsertionResult calculateInsertion(
        @NotNull Line line,
        @NotNull StaffElement insertedElement,
        int insertIndex) {

        var elementCount = line.elementCount();

        // Validate index
        if (insertIndex < 0 || insertIndex > elementCount) {
            throw new IllegalArgumentException(
                "insertIndex " + insertIndex + " out of bounds [0, " + elementCount + "]");
        }

        // If inserting at end, no shift needed (use calculateAppendPosition instead)
        if (insertIndex == elementCount) {
            double appendXSs = calculateAppendPositionSs(line, insertedElement);
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
            insertedElementXSs = LayoutConstants.calculateFirstElementXSs(line.getKeyAccidentalCount());
            var nextElement = line.getElement(0);
            var nextColumn = createLightweightColumn(nextElement);

            // Space needed: firstElementX → inserted element → existing first element
            insertedColumn.setXSs(insertedElementXSs);
            double insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                insertedColumn, nextColumn);

            // Shift = (where first element needs to be) - (where it currently is)
            requiredSpaceSs = insertedToNextSs - elementXSs(nextElement);
        } else {
            // Inserting in middle - calculate space between prev and next
            var prevElement = line.getElement(insertIndex - 1);
            var nextElement = line.getElement(insertIndex);

            var prevColumn = createLightweightColumn(prevElement);
            var nextColumn = createLightweightColumn(nextElement);

            prevColumn.setXSs(elementXSs(prevElement));

            // Calculate: prev → inserted → next
            insertedElementXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                prevColumn, insertedColumn);
            insertedColumn.setXSs(insertedElementXSs);

            double insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                insertedColumn, nextColumn);

            // Shift = (where next needs to be) - (where it currently is)
            requiredSpaceSs = insertedToNextSs - elementXSs(nextElement);
        }

        double shiftSs = Math.max(0, requiredSpaceSs);

        // Compute projected line width: max of inserted element's right edge
        // and the last element's right edge shifted by the insertion shift
        insertedColumn.setXSs(insertedElementXSs);
        double newLineWidthSs = insertedColumn.getRightEdgeXSs();

        if (elementCount > 0) {
            var lastElement = line.getElement(elementCount - 1);
            var lastColumn = createLightweightColumn(lastElement);
            lastColumn.setXSs(elementXSs(lastElement) + shiftSs);
            newLineWidthSs = Math.max(newLineWidthSs, lastColumn.getRightEdgeXSs());
        }

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
     * @return Shift amount in pixels (positive = shift right)
     */
    public static double calculateInsertionShiftSs(
        @NotNull Line line,
        @NotNull StaffElement insertedElement,
        int insertIndex) {

        return calculateInsertion(line, insertedElement, insertIndex).shiftForSubsequentElementsSs();
    }

    /**
     * Determines whether a grace note and its host note will both fit on a line
     * when inserted at the given index.
     * <p>
     * This creates representative grace and host note elements, computes the spacing
     * for both insertions, and checks whether the projected line width fits within
     * the composition's line width.
     *
     * @param line    The line to check (before insertion)
     * @param atIndex The index where the grace note would be inserted
     * @return {@code true} if both the grace note and host note fit on the line
     */
    public static boolean hasRoomForGraceNotePair(@NotNull Line line, int atIndex) {
        Composition composition = line.getComposition();

        if (composition == null) {
            return false;
        }

        double staffRightMarginSs = composition.getLineWidthSs();
        int elementCount = line.elementCount();

        // Create representative elements
        var graceNote = ElementType.GRACE_QUAVER.newInstance();
        var hostNote = ElementType.CROTCHET.newInstance();

        // Check if the grace note fits
        var graceResult = calculateInsertion(line, graceNote, atIndex);

        if (!graceResult.fitsWithinLine(staffRightMarginSs)) {
            return false;
        }

        // Position the grace column to compute host note placement
        var graceColumn = createLightweightColumn(graceNote);
        graceColumn.setXSs(graceResult.insertedElementXSs());

        var hostColumn = createLightweightColumn(hostNote);

        // Grace-to-host spacing: grace right extent + gap + host left extent
        double hostXSs = graceColumn.getXSs()
            + graceColumn.getRightExtentSs()
            + LayoutConstants.GRACE_NOTE_GAP_SS
            + Math.abs(hostColumn.getLeftExtentSs());

        hostColumn.setXSs(hostXSs);

        // Compute projected line width after both insertions.
        // The element currently at atIndex will follow the host note.
        double newLineWidthSs;

        if (atIndex >= elementCount) {
            // Both are appended — host right edge is the new width
            newLineWidthSs = hostColumn.getRightEdgeXSs();
        } else {
            // Middle insert — account for shift of existing elements
            double graceShiftSs = graceResult.shiftForSubsequentElementsSs();

            // Additional shift: where the element at atIndex needs to be after the host
            var nextElement = line.getElement(atIndex);
            var nextColumn = createLightweightColumn(nextElement);
            double nextCurrentXSs = elementXSs(nextElement) + graceShiftSs;
            double hostToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                hostColumn, nextColumn);
            double hostShiftSs = Math.max(0, hostToNextSs - nextCurrentXSs);
            double totalShiftSs = graceShiftSs + hostShiftSs;

            // Projected width: max of host right edge and shifted last element right edge
            var lastElement = line.getElement(elementCount - 1);
            var lastColumn = createLightweightColumn(lastElement);
            lastColumn.setXSs(elementXSs(lastElement) + totalShiftSs);
            newLineWidthSs = Math.max(hostColumn.getRightEdgeXSs(), lastColumn.getRightEdgeXSs());
        }

        return newLineWidthSs + LayoutConstants.DEFAULT_COLUMN_GAP_SS <= staffRightMarginSs;
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
    private static @NotNull ElementColumn createLightweightColumn(@NotNull StaffElement element) {
        // Calculate geometric extents using ElementColumnBuilder's static methods
        double leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        double rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, element.isUpper());

        // For insertion operations, we don't need stem positions or beam group info
        // since we're only calculating horizontal spacing
        double stemTopSs = 0;
        double stemBottomSs = 0;

        // No syllable information for lightweight columns
        String syllable = null;
        double syllableWidthSs = 0;

        return new ElementColumn(
            element,
            Collections.emptyList(),  // No grace notes
            leftExtentSs,
            rightExtentSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllableWidthSs,
            false
        );
    }
}
