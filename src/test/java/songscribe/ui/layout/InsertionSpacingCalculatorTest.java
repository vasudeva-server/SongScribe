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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

class InsertionSpacingCalculatorTest extends UnitTest {

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    /**
     * Creates a mock composition with the given line width (in staff spaces) and attaches it to the line.
     */
    private static void setLineWidth(Line line, double lineWidthSs) {
        var composition = mock(Composition.class);
        when(composition.getLineWidthSs()).thenReturn(lineWidthSs);
        line.setComposition(composition);
    }

    /**
     * Creates a line with the given number of crotchets, positioned using the
     * standard spacing algorithm.
     */
    private static Line lineWithCrotchets(int count) {
        var line = new Line();

        for (var i = 0; i < count; i++) {
            var element = crotchet();
            double xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, element);
            element.setXPosSs(ScaleContext.getInstance().toRoundedPixels(xSs));
            line.addElement(element);
        }

        return line;
    }

    /**
     * Returns the right edge of the last element on the line.
     */
    private static double lastElementRightEdgeSs(Line line) {
        var last = line.getElement(line.elementCount() - 1);
        double leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(last);
        double rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(last, false, last.isUpper());
        var column = new ElementColumn(
            last, Collections.emptyList(), leftExtentSs, rightExtentSs, 0, 0, null, 0, false
        );
        column.setXSs(ScaleContext.getInstance().fromPixels(last.getXPosSs()));
        return column.getRightEdgeXSs();
    }

    @Nested
    class FitsWithinLine {

        @Test
        void testAppendToEmptyLine() {
            var line = lineWithCrotchets(0);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 0);
            assertThat(result.fitsWithinLine(500)).isTrue();
        }

        @Test
        void testInsertIntoNearlyFullLine() {
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1);
            assertThat(result.fitsWithinLine(result.newLineWidthSs() - 1)).isFalse();
        }

        @Test
        void testInsertWithPlentyOfRoom() {
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1);
            assertThat(result.fitsWithinLine(500)).isTrue();
        }
    }

    @Nested
    class HasRoomForGraceNotePair {

        @Test
        void testEmptyLine() {
            var line = lineWithCrotchets(0);
            setLineWidth(line, 500);
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNotePair(line, 0)).isTrue();
        }

        @Test
        void testLineExactlyFull() {
            var line = lineWithCrotchets(3);
            double currentWidthSs = lastElementRightEdgeSs(line);
            setLineWidth(line, currentWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForGraceNotePair(
                line, line.elementCount())).isFalse();
        }

        @Test
        void testLineNearlyFull() {
            var line = lineWithCrotchets(3);
            double currentWidthSs = lastElementRightEdgeSs(line);

            // Allow room for just a small amount beyond current content — not enough for a pair
            setLineWidth(line, currentWidthSs + 3);

            assertThat(InsertionSpacingCalculator.hasRoomForGraceNotePair(
                line, line.elementCount())).isFalse();
        }

        @Test
        void testLineWithPlentyOfRoom() {
            var line = lineWithCrotchets(2);
            setLineWidth(line, 500);
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNotePair(line, 1)).isTrue();
        }

        @Test
        void testNullCompositionReturnsFalse() {
            var line = lineWithCrotchets(0);
            // No composition set
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNotePair(line, 0)).isFalse();
        }
    }
}
