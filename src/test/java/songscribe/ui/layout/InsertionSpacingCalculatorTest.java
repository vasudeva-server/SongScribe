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
import static songscribe.model.StaffElementFactory.crotchet;

import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.model.Song;
import songscribe.model.ElementType;
import songscribe.model.Line;

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
