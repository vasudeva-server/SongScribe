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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementColumnBuilder;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.InsertionSpacingCalculator;

class InsertionSpacingCalculatorTest extends UnitTest {

    /** A line width wide enough that any insertion or fall comfortably fits. */
    private static final double WIDE_LINE_SS = 500;

    /** Extra gap, in staff spaces, before the following element so a fall cannot push it. */
    private static final double DISTANT_LAST_ELEMENT_GAP_SS = 50;

    /** Staff-space offset by which a test layout reports an element to the right of its xOffset. */
    private static final double LAYOUT_SHIFT_SS = 10;

    /** Returns a minimal song mock with the given line width stubbed. */
    private static Song songWithLineWidth(double lineWidthSs) {
        var song = mock(Song.class);
        when(song.isMutationTrackingSuspended()).thenReturn(true);
        when(song.getLineWidthSs()).thenReturn(lineWidthSs);
        return song;
    }

    /**
     * Creates a line with the given number of crotchets, positioned using the
     * standard spacing algorithm.
     */
    private static Line lineWithCrotchets(int count) {
        return lineWithCrotchets(count, detachedLine());
    }

    private static Line lineWithCrotchets(int count, Song song) {
        return lineWithCrotchets(count, new Line(song));
    }

    private static Line lineWithCrotchets(int count, Line line) {
        for (var i = 0; i < count; i++) {
            var element = crotchet();
            var xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, element, null);
            element.setXOffsetPx(ScaleContext.ssToRoundedPx(xSs));
            line.addElement(element);
        }

        return line;
    }

    /**
     * Creates a line with the given number of crotchets followed by one grace note,
     * all positioned using the standard spacing algorithm.
     */
    private static Line lineWithGraceAtIndex(int numCrotchetsBefore) {
        return lineWithGraceAtIndex(numCrotchetsBefore, detachedLine());
    }

    private static Line lineWithGraceAtIndex(int numCrotchetsBefore, Song song) {
        return lineWithGraceAtIndex(numCrotchetsBefore, new Line(song));
    }

    private static Line lineWithGraceAtIndex(int numCrotchetsBefore, Line line) {
        lineWithCrotchets(numCrotchetsBefore, line);
        var grace = ElementType.GRACE_QUAVER.newInstance();
        var xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, grace, null);
        grace.setXOffsetPx(ScaleContext.ssToRoundedPx(xSs));
        line.addElement(grace);
        return line;
    }

    /**
     * Computes the expected shift when inserting {@code insertedElement} at index 0, using
     * the same column construction path that InsertionSpacingCalculator uses internally.
     * <p>
     * Shift = calculateNextColumnXSs(insertedColumn@insertedXSs, existingColumn) - existingXSs.
     */
    private static double expectedShiftFromInsertAtZero(
        StaffElement insertedElement, double insertedXSs,
        StaffElement existingElement, double existingXSs) {

        var insertedLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(insertedElement);
        var insertedRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
            insertedElement, false, insertedElement.isUpper());
        var insertedColumn = new ElementColumn(
            insertedElement, Collections.emptyList(),
            insertedLeftExtentSs, insertedRightExtentSs, 0, 0, null, 0, false);
        insertedColumn.setXSs(insertedXSs);

        var existingLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(existingElement);
        var existingRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
            existingElement, false, existingElement.isUpper());
        var existingColumn = new ElementColumn(
            existingElement, Collections.emptyList(),
            existingLeftExtentSs, existingRightExtentSs, 0, 0, null, 0, false);

        var insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(insertedColumn, existingColumn);
        return Math.max(0, insertedToNextSs - existingXSs);
    }

    /**
     * Returns the right edge of the last element on the line.
     */
    private static double lastElementRightEdgeSs(Line line) {
        var last = line.getElement(line.elementCount() - 1);
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(last);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(last, false, last.isUpper());
        var column = new ElementColumn(
            last, Collections.emptyList(), leftExtentSs, rightExtentSs, 0, 0, null, 0, false
        );
        column.setXSs(ScaleContext.pxToSs(last.getXOffsetPx()));
        return column.getRightEdgeXSs();
    }

    /**
     * Returns the right edge of {@code element} at {@code xSs} with a fall temporarily applied
     * and then removed — the same fall reservation {@code hasRoomForFall} measures internally.
     */
    private static double rightEdgeWithFallSs(StaffElement element, double xSs) {
        element.setFall();
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, element.isUpper());
        element.removeSlide();

        var column = new ElementColumn(
            element, Collections.emptyList(), leftExtentSs, rightExtentSs, 0, 0, null, 0, false);
        column.setXSs(xSs);
        return column.getRightEdgeXSs();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateInsertion {

        @Test
        void testNegativeIndexThrowsIllegalArgumentException() {
            var line = lineWithCrotchets(2);
            assertThatIllegalArgumentException()
                .isThrownBy(() -> InsertionSpacingCalculator.calculateInsertion(line, crotchet(), -1, null));
        }

        @Test
        void testIndexBeyondCountThrowsIllegalArgumentException() {
            var line = lineWithCrotchets(2);
            var beyondCount = line.elementCount() + 1;
            assertThatIllegalArgumentException()
                .isThrownBy(() -> InsertionSpacingCalculator.calculateInsertion(line, crotchet(), beyondCount, null));
        }

        @Test
        void testInsertAtIndexZeroReturnsFirstElementX() {
            // Row 39: inserted element at index 0 must land at calculateFirstElementXSs.
            var line = lineWithCrotchets(1);
            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());

            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 0, null);

            assertThat(result.insertedElementXSs()).isEqualTo(expectedXSs);
        }

        @Test
        void testInsertAtIndexZeroShiftsFollowingElement() {
            // Row 39: the shift applied to the element that was at index 0 must equal
            // (insertedToNextSs - existingFirstXSs), where insertedToNextSs is derived from
            // the exact (un-rounded) first-element X, and existingFirstXSs is the px-rounded
            // X stored when lineWithCrotchets placed the crotchet.
            var line = lineWithCrotchets(1);
            var insertedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            var existingElement = line.getElement(0);
            var existingXSs = ScaleContext.pxToSs(existingElement.getXOffsetPx());
            // Build a lightweight column for the inserted crotchet at the exact first-element X.
            var insertedCrotchet = crotchet();
            var expectedShiftSs = expectedShiftFromInsertAtZero(insertedCrotchet, insertedXSs, existingElement, existingXSs);

            var result = InsertionSpacingCalculator.calculateInsertion(line, insertedCrotchet, 0, null);

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(expectedShiftSs);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FitsWithinLine {

        @Test
        void testAppendToEmptyLineReturnsFirstElementX() {
            // Row 35: when the line is empty, calculateAppendPositionSs must equal
            // calculateFirstElementXSs for the line's key-accidental count.
            var line = lineWithCrotchets(0);
            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            var actualXSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, crotchet(), null);
            assertThat(actualXSs).isEqualTo(expectedXSs);
        }

        @Test
        void testAppendToEmptyLineFitsWithinLargeLine() {
            var line = lineWithCrotchets(0);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 0, null);
            assertThat(result.fitsWithinLine(WIDE_LINE_SS)).isTrue();
        }

        @Test
        void testInsertExactGapBoundaryFitsWhenMarginEqualsWidthPlusGap() {
            // Row 36: margin == newLineWidthSs + DEFAULT_COLUMN_GAP_SS is the exact passing boundary.
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null);
            var exactPassingMarginSs = result.newLineWidthSs() + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            assertThat(result.fitsWithinLine(exactPassingMarginSs)).isTrue();
        }

        @Test
        void testInsertExactGapBoundaryFailsWhenMarginIsLineWidthAlone() {
            // Row 36: margin == newLineWidthSs (no budget for the required gap) must return false.
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null);
            assertThat(result.fitsWithinLine(result.newLineWidthSs())).isFalse();
        }

        @Test
        void testInsertIntoNearlyFullLine() {
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null);
            assertThat(result.fitsWithinLine(result.newLineWidthSs() - 1)).isFalse();
        }

        @Test
        void testInsertWithPlentyOfRoom() {
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null);
            assertThat(result.fitsWithinLine(WIDE_LINE_SS)).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForGraceNote {

        @Test
        void testEmptyLine() {
            var line = lineWithCrotchets(0, songWithLineWidth(WIDE_LINE_SS));
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 0, null)).isTrue();
        }

        @Test
        void testLineExactlyFull() {
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(3, song);
            var currentWidthSs = lastElementRightEdgeSs(line);
            when(song.getLineWidthSs()).thenReturn(currentWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(
                line, line.elementCount(), null)).isFalse();
        }

        @Test
        void testLineWithPlentyOfRoom() {
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 1, null)).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForHostNoteAfterGrace {

        @Test
        void testPlentyOfRoom() {
            var line = lineWithGraceAtIndex(0, songWithLineWidth(WIDE_LINE_SS));
            assertThat(InsertionSpacingCalculator.hasRoomForHostNoteAfterGrace(line, 0)).isTrue();
        }

        @Test
        void testNoRoomForHost() {
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithGraceAtIndex(2, song);
            var graceIndex = line.elementCount() - 1;
            var currentWidthSs = lastElementRightEdgeSs(line);

            // Width exactly at grace note's right edge — no room for a host note
            when(song.getLineWidthSs()).thenReturn(currentWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForHostNoteAfterGrace(line, graceIndex)).isFalse();
        }
    }

    // Row 40: mid-insertion shift is max(0, required), never negative.
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertionShift {

        /**
         * Creates a line where two crotchets are placed far apart so that inserting
         * a third crotchet between them requires zero shift (raw required < 0).
         * <p>
         * The second crotchet is placed {@code extraGapSs} beyond the standard
         * first-element X, creating a gap that dwarfs the space needed by the inserted element.
         */
        private static Line lineWithWidelySpacedCrotchets(double secondElementXSs) {
            var line = detachedLine();
            var first = crotchet();
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            first.setXOffsetPx(ScaleContext.ssToRoundedPx(firstXSs));
            line.addElement(first);

            var second = crotchet();
            second.setXOffsetPx(ScaleContext.ssToRoundedPx(secondElementXSs));
            line.addElement(second);

            return line;
        }

        @Test
        void testMidInsertionShiftIsZeroWhenExistingGapIsAlreadyWide() {
            // Row 40 (negative clamp case): if prevElement and nextElement are far apart,
            // the raw required shift is negative; max(0, required) must clamp it to 0.
            var firstXSs = HorizontalSpacingCalculator
                .calculateFirstElementXSs(0);  // no key accidentals
            // Place the second crotchet very far right — far beyond where any inserted element
            // + its neighbour spacing would land.
            var largeGapSs = 50.0;
            var secondXSs = firstXSs + largeGapSs;
            var line = lineWithWidelySpacedCrotchets(secondXSs);

            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null);

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(0.0);
        }

        @Test
        void testMidInsertionShiftIsPinnedPositiveValueForTightLine() {
            // Row 40 (positive case): inserting into a tightly-packed line produces a positive
            // shift, pinned to the exact computed value.
            // Line has 3 crotchets at indices 0, 1, 2. Insert at index 1 (between [0] and [1]).
            var line = lineWithCrotchets(3);
            var prevElement = line.getElement(0);
            var nextElement = line.getElement(1);

            // Compute insertedXSs: same algorithm the production code uses.
            var prevLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(prevElement);
            var prevRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                prevElement, false, prevElement.isUpper());
            var prevXSs = ScaleContext.pxToSs(prevElement.getXOffsetPx());
            var prevColumn = new ElementColumn(
                prevElement, Collections.emptyList(),
                prevLeftExtentSs, prevRightExtentSs, 0, 0, null, 0, false);
            prevColumn.setXSs(prevXSs);

            var inserted = crotchet();
            var insertedLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(inserted);
            var insertedRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                inserted, false, inserted.isUpper());
            var insertedColumn = new ElementColumn(
                inserted, Collections.emptyList(),
                insertedLeftExtentSs, insertedRightExtentSs, 0, 0, null, 0, false);

            var insertedXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, insertedColumn);
            insertedColumn.setXSs(insertedXSs);

            var nextLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(nextElement);
            var nextRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                nextElement, false, nextElement.isUpper());
            var nextColumn = new ElementColumn(
                nextElement, Collections.emptyList(),
                nextLeftExtentSs, nextRightExtentSs, 0, 0, null, 0, false);

            var insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(insertedColumn, nextColumn);
            var nextXSs = ScaleContext.pxToSs(nextElement.getXOffsetPx());
            var expectedShiftSs = Math.max(0, insertedToNextSs - nextXSs);

            // Must be positive (elements are tightly packed — inserting requires a real shift)
            assertThat(expectedShiftSs).isGreaterThan(0.0);

            var result = InsertionSpacingCalculator.calculateInsertion(line, inserted, 1, null);

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(expectedShiftSs);
        }
    }

    // Row 41: calculateNextElementXSs delegates to HorizontalSpacingCalculator.calculateNextColumnXSs.
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateNextElementXSs {

        @Test
        void testDelegatesEquivalentToCalculateNextColumnXSs() {
            // Row 41: calculateNextElementXSs(current, next) must equal
            // HorizontalSpacingCalculator.calculateNextColumnXSs(equivalentColumn, nextColumn)
            // for the same elements and positions — pinned to the delegate's exact output.
            var line = lineWithCrotchets(2);
            var currentElement = line.getElement(0);
            var nextElement = line.getElement(1);

            // Build equivalent columns exactly as the production code does internally.
            var currentLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(currentElement);
            var currentRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                currentElement, false, currentElement.isUpper());
            var currentColumn = new ElementColumn(
                currentElement, Collections.emptyList(),
                currentLeftExtentSs, currentRightExtentSs, 0, 0, null, 0, false);
            currentColumn.setXSs(ScaleContext.pxToSs(currentElement.getXOffsetPx()));

            var nextLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(nextElement);
            var nextRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                nextElement, false, nextElement.isUpper());
            var nextColumn = new ElementColumn(
                nextElement, Collections.emptyList(),
                nextLeftExtentSs, nextRightExtentSs, 0, 0, null, 0, false);

            var expectedXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(currentColumn, nextColumn);

            var actualXSs = InsertionSpacingCalculator.calculateNextElementXSs(currentElement, nextElement);

            assertThat(actualXSs).isEqualTo(expectedXSs);
        }
    }

    // Row 42: InsertionResult.newLineWidthSs equals max(inserted right edge, shifted last right edge).
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertionResultNewLineWidth {

        @Test
        void testNewLineWidthSsIsPinnedMaxOfInsertedAndShiftedLastRightEdge() {
            // Row 42: newLineWidthSs must equal exactly
            //   max(insertedColumn.rightEdgeXSs, lastColumn@(lastXSs + shiftSs).rightEdgeXSs).
            // Use a 3-crotchet line and insert at index 1.
            var line = lineWithCrotchets(3);
            var inserted = crotchet();

            var result = InsertionSpacingCalculator.calculateInsertion(line, inserted, 1, null);

            // Recompute the inserted element's column right edge from scratch.
            var insertedLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(inserted);
            var insertedRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                inserted, false, inserted.isUpper());
            var insertedColumn = new ElementColumn(
                inserted, Collections.emptyList(),
                insertedLeftExtentSs, insertedRightExtentSs, 0, 0, null, 0, false);
            insertedColumn.setXSs(result.insertedElementXSs());
            var insertedRightEdgeSs = insertedColumn.getRightEdgeXSs();

            // The effective last element excludes the FINAL_DOUBLE_BARLINE (auto-maintained),
            // so use effectiveElementCount — for a plain crotchet line this is element count.
            var lastIndex = line.effectiveElementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var lastLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(lastElement);
            var lastRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                lastElement, false, lastElement.isUpper());
            var lastColumn = new ElementColumn(
                lastElement, Collections.emptyList(),
                lastLeftExtentSs, lastRightExtentSs, 0, 0, null, 0, false);
            var lastOriginalXSs = ScaleContext.pxToSs(lastElement.getXOffsetPx());
            lastColumn.setXSs(lastOriginalXSs + result.shiftForSubsequentElementsSs());
            var shiftedLastRightEdgeSs = lastColumn.getRightEdgeXSs();

            var expectedNewLineWidthSs = Math.max(insertedRightEdgeSs, shiftedLastRightEdgeSs);

            assertThat(result.newLineWidthSs()).isEqualTo(expectedNewLineWidthSs);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForFall {

        @Test
        void testFallAlreadyPresentAlwaysFits() {
            // A line too narrow for any element; the source already carries a fall, so applying
            // one is a no-op change in right extent and must short-circuit to true.
            var line = lineWithCrotchets(2, songWithLineWidth(0));
            line.getElement(0).setFall();

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 0, null))
                .as("fall already present short-circuits to fit even on a zero-width line")
                .isTrue();
        }

        @Test
        void testFallAtLastElementFitsAtExactBoundary() {
            // Source is the last element: the fall extends its own right edge only. Margin set to
            // exactly (fall right edge + required gap) is the passing boundary.
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var lastXSs = ScaleContext.pxToSs(lastElement.getXOffsetPx());
            var fallRightEdgeSs = rightEdgeWithFallSs(lastElement, lastXSs);
            var exactBoundarySs = fallRightEdgeSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(exactBoundarySs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, null))
                .as("fall at last element fits when the margin equals its fall right edge plus the gap")
                .isTrue();
        }

        @Test
        void testFallAtLastElementDoesNotFitJustBelowBoundary() {
            // One staff space short of the passing boundary must fail.
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var lastXSs = ScaleContext.pxToSs(lastElement.getXOffsetPx());
            var fallRightEdgeSs = rightEdgeWithFallSs(lastElement, lastXSs);
            var exactBoundarySs = fallRightEdgeSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(exactBoundarySs - 1);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, null))
                .as("fall at last element fails one staff space below the passing boundary")
                .isFalse();
        }

        @Test
        void testFallMidLinePushesLastElementOverMargin() {
            // Source is not the last element: the fall's wider right extent shifts following
            // elements right. With the margin set to exactly fit the line as-is (no fall), that
            // shift pushes the last element past the margin.
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(3, song);
            var snugWidthSs = lastElementRightEdgeSs(line) + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(snugWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall mid-line pushes the last element past a snug margin")
                .isFalse();
        }

        @Test
        void testFallProjectsLastElementNotImmediateNext() {
            // Four elements with the fall at index 1: the immediate next (index 2) is not the last
            // (index 3). The overflow projection must target the LAST element — a margin snug to
            // its current right edge fails once the fall shifts the line right. Were the projection
            // wrongly applied to the immediate next element, its shifted edge would stay inside the
            // margin and the check would wrongly pass.
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(4, song);
            var snugWidthSs = lastElementRightEdgeSs(line) + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(snugWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall shift is projected onto the last element, not the immediate next")
                .isFalse();
        }

        @Test
        void testFallMidLineWithoutPushIgnoresDistantLastElement() {
            // The following element already sits far enough right that the fall cannot push it
            // (shiftSs == 0), so the result must depend only on the source's own fall right edge.
            // If the last-element projection ran regardless of shift, the distant element would
            // overflow the margin and the check would wrongly fail.
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(3, song);

            var source = line.getElement(1);
            var sourceFallRightEdgeSs = rightEdgeWithFallSs(source, ScaleContext.pxToSs(source.getXOffsetPx()));

            var last = line.getElement(2);
            last.setXOffsetPx(ScaleContext.ssToRoundedPx(sourceFallRightEdgeSs + DISTANT_LAST_ELEMENT_GAP_SS));

            var marginSs = sourceFallRightEdgeSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("no push (shiftSs == 0) fits on the source's own fall edge, ignoring the distant last element")
                .isTrue();
        }

        @Test
        void testFallMidLineFitsWithWideLine() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall mid-line fits on a generously wide line")
                .isTrue();
        }

        @Test
        void testFallUsesLayoutPositionWhenProvided() {
            // With a non-null layout the source X comes from the layout map, not xOffsetPx. A layout
            // position to the right of the element's xOffset pushes the fall's right edge past a
            // margin that the xOffset position would clear, proving the layout drives the geometry.
            var song = mock(Song.class);
            when(song.isMutationTrackingSuspended()).thenReturn(true);
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var fallRightEdgeAtOffsetSs =
                rightEdgeWithFallSs(lastElement, ScaleContext.pxToSs(lastElement.getXOffsetPx()));
            var marginSs = fallRightEdgeAtOffsetSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            var layout = mock(LayoutResult.class);
            when(layout.getElementXSs(any())).thenAnswer(invocation ->
                ScaleContext.pxToSs(((StaffElement) invocation.getArgument(0)).getXOffsetPx()) + LAYOUT_SHIFT_SS);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, layout))
                .as("layout position right of xOffset drives the fall geometry, overflowing the margin")
                .isFalse();
        }

        @Test
        void testRestoresNoSlideStateAfterCheck() {
            // The check measures fall geometry on a clone, so an element that had no slide must be
            // left untouched (no spurious fall on the live model element).
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            var source = line.getElement(0);

            InsertionSpacingCalculator.hasRoomForFall(line, 0, null);

            assertThat(source.hasFall()).as("temporary fall removed after check").isFalse();
            assertThat(source.hasGlissando()).as("no slide left after check").isFalse();
        }

        @Test
        void testRestoresGlissandoStateAfterCheck() {
            // An element that carried a glissando must keep it — the check measures a clone and
            // must never replace the live element's slide with the fall it applies to the clone.
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            var source = line.getElement(0);
            source.setGlissando();

            InsertionSpacingCalculator.hasRoomForFall(line, 0, null);

            assertThat(source.hasGlissando()).as("original glissando restored").isTrue();
            assertThat(source.hasFall()).as("fall not left in place").isFalse();
        }
    }
}
