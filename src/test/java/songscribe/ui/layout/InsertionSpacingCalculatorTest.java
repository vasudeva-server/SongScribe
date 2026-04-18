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
import static songscribe.music.StaffElementFactory.crotchet;

import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;

class InsertionSpacingCalculatorTest extends UnitTest {

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
            double xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, element, null);
            element.setXOffsetPx(ScaleContext.getInstance().toRoundedPixels(xSs));
            line.addElement(element);
        }

        return line;
    }

    /**
     * Creates a line with the given number of crotchets followed by one grace note,
     * all positioned using the standard spacing algorithm.
     */
    private static Line lineWithGraceAtIndex(int numCrotchetsBefore) {
        var line = lineWithCrotchets(numCrotchetsBefore);
        var grace = ElementType.GRACE_QUAVER.newInstance();
        double xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, grace, null);
        grace.setXOffsetPx(ScaleContext.getInstance().toRoundedPixels(xSs));
        line.addElement(grace);
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
        column.setXSs(ScaleContext.getInstance().fromPixels(last.getXOffsetPx()));
        return column.getRightEdgeXSs();
    }

    @Nested
    class FitsWithinLine {

        @Test
        void testAppendToEmptyLine() {
            var line = lineWithCrotchets(0);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 0, null);
            assertThat(result.fitsWithinLine(500)).isTrue();
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

    @Nested
    class HasRoomForGraceNote {

        @Test
        void testEmptyLine() {
            var line = lineWithCrotchets(0);
            setLineWidth(line, 500);
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 0, null)).isTrue();
        }

        @Test
        void testLineExactlyFull() {
            var line = lineWithCrotchets(3);
            double currentWidthSs = lastElementRightEdgeSs(line);
            setLineWidth(line, currentWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(
                line, line.elementCount(), null)).isFalse();
        }

        @Test
        void testLineWithPlentyOfRoom() {
            var line = lineWithCrotchets(2);
            setLineWidth(line, 500);
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 1, null)).isTrue();
        }
    }

    @Nested
    class HasRoomForHostNoteAfterGrace {

        @Test
        void testPlentyOfRoom() {
            var line = lineWithGraceAtIndex(0);
            setLineWidth(line, 500);
            assertThat(InsertionSpacingCalculator.hasRoomForHostNoteAfterGrace(line, 0)).isTrue();
        }

        @Test
        void testNoRoomForHost() {
            var line = lineWithGraceAtIndex(2);
            int graceIndex = line.elementCount() - 1;
            double currentWidthSs = lastElementRightEdgeSs(line);

            // Width exactly at grace note's right edge — no room for a host note
            setLineWidth(line, currentWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForHostNoteAfterGrace(line, graceIndex)).isFalse();
        }
    }
}
