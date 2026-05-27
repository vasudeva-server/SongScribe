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
            assertThat(result.fitsWithinLine(500)).isTrue();
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
            assertThat(result.fitsWithinLine(500)).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForGraceNote {

        @Test
        void testEmptyLine() {
            var line = lineWithCrotchets(0, songWithLineWidth(500));
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
            var line = lineWithCrotchets(2, songWithLineWidth(500));
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 1, null)).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForHostNoteAfterGrace {

        @Test
        void testPlentyOfRoom() {
            var line = lineWithGraceAtIndex(0, songWithLineWidth(500));
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
}
