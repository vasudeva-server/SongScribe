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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;

@DisplayName("LineJustificationCalculator")
class LineJustificationCalculatorTest {

    private LineJustificationCalculator calculator;
    private Graphics2D g2;
    private Font lyricsFont;
    private NoteColumnBuilder builder;

    @BeforeEach
    void setUp() {
        calculator = new LineJustificationCalculator();

        // Create real Graphics2D and Font for accurate measurements
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        g2 = image.createGraphics();
        lyricsFont = new Font("SansSerif", Font.PLAIN, 12);
        builder = new NoteColumnBuilder(g2, lyricsFont);
    }

    // ==========================================================================
    // Test Helpers
    // ==========================================================================

    /**
     * Creates a test line.
     */
    private Line createLine() {
        return new Line();
    }

    /**
     * Creates a note with optional syllable.
     */
    private Note createNote(String syllable) {
        var note = NoteType.CROTCHET.newInstance();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        return note;
    }

    /**
     * Creates a simple column with fixed extents and optional syllable.
     */
    private NoteColumn createSimpleColumn(double leftExtentSs, double rightExtentSs, String syllable) {
        var note = createNote(syllable);
        var line = createLine();
        line.addNote(note);

        double syllableWidthSs = 0;

        if (syllable != null) {
            var metrics = g2.getFontMetrics(lyricsFont);
            syllableWidthSs = metrics.stringWidth(syllable);
        }

        return new NoteColumn(
            note,
            List.of(),
            leftExtentSs,
            rightExtentSs,
            0,
            0,
            syllable,
            syllableWidthSs,
            null
        );
    }

    /**
     * Sets up a line of columns with specified positions.
     */
    private List<NoteColumn> createPositionedColumns(double... xPositions) {
        var columns = new ArrayList<NoteColumn>();

        for (var x : xPositions) {
            var column = createSimpleColumn(-5.0, 5.0, null);
            column.setXSs(x);
            columns.add(column);
        }

        return columns;
    }

    /**
     * Creates positioned columns with syllables for lyric spacing tests.
     */
    private List<NoteColumn> createLyricColumns(String... syllables) {
        var columns = new ArrayList<NoteColumn>();
        double x = 100.0;

        for (var syllable : syllables) {
            var column = createSimpleColumn(-5.0, 5.0, syllable);
            column.setXSs(x);
            columns.add(column);
            x += 50.0; // Fixed spacing
        }

        return columns;
    }

    // ==========================================================================
    // Empty Line Tests
    // ==========================================================================

    @Nested
    @DisplayName("justifyLine with empty line")
    class EmptyLineTests {

        @Test
        @DisplayName("handles empty column list")
        void emptyColumnList() {
            var columns = new ArrayList<NoteColumn>();
            var result = calculator.justifyLine(columns, 500.0);

            assertTrue(result.isSuccess());
            assertFalse(result.wasCompressionApplied());
        }
    }

    // ==========================================================================
    // Line Fits (No Compression Needed)
    // ==========================================================================

    @Nested
    @DisplayName("line fits within margin")
    class LineFitsTests {

        @Test
        @DisplayName("line that fits requires no compression")
        void lineFits() {
            // Create columns positioned at 100, 150, 200
            var columns = createPositionedColumns(100.0, 150.0, 200.0);
            var staffRightMargin = 300.0; // Plenty of room

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertFalse(result.wasCompressionApplied());

            // Positions should remain unchanged
            assertEquals(100.0, columns.get(0).getXSs(), 0.01);
            assertEquals(150.0, columns.get(1).getXSs(), 0.01);
            assertEquals(200.0, columns.get(2).getXSs(), 0.01);
        }

        @Test
        @DisplayName("line exactly at margin requires no compression")
        void lineExactlyAtMargin() {
            var columns = createPositionedColumns(100.0, 150.0, 200.0);
            var lastColumn = columns.get(columns.size() - 1);
            var staffRightMargin = lastColumn.getRightEdgeXSs(); // Exact fit

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertFalse(result.wasCompressionApplied());
        }

        @Test
        @DisplayName("single column that fits")
        void singleColumnFits() {
            var columns = createPositionedColumns(100.0);
            var staffRightMargin = 200.0;

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertFalse(result.wasCompressionApplied());
        }
    }

    // ==========================================================================
    // Successful Compression
    // ==========================================================================

    @Nested
    @DisplayName("successful compression")
    class SuccessfulCompressionTests {

        @Test
        @DisplayName("compresses line that slightly exceeds margin")
        void compressLineSlightlyOverMargin() {
            // Create columns positioned at 100, 200, 300
            var columns = createPositionedColumns(100.0, 200.0, 300.0);
            var staffRightMargin = 280.0; // Slightly under the last column's right edge

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertTrue(result.wasCompressionApplied());

            // First column should remain at original position
            assertEquals(100.0, columns.get(0).getXSs(), 0.01);

            // Later columns should be compressed
            assertTrue(columns.get(1).getXSs() < 200.0, "Column 1 should be compressed");
            assertTrue(columns.get(2).getXSs() < 300.0, "Column 2 should be compressed");

            // Line should now fit within margin
            var lastColumn = columns.get(columns.size() - 1);
            assertTrue(lastColumn.getRightEdgeXSs() <= staffRightMargin + 0.01);
        }

        @Test
        @DisplayName("compression maintains relative spacing")
        void compressionMaintainsRelativeSpacing() {
            var columns = createPositionedColumns(100.0, 150.0, 250.0);
            var staffRightMargin = 230.0;

            // Calculate original spacing ratios
            double originalGap1 = columns.get(1).getXSs() - columns.get(0).getXSs();
            double originalGap2 = columns.get(2).getXSs() - columns.get(1).getXSs();
            double originalRatio = originalGap2 / originalGap1;

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertTrue(result.wasCompressionApplied());

            // Calculate new spacing ratios
            double newGap1 = columns.get(1).getXSs() - columns.get(0).getXSs();
            double newGap2 = columns.get(2).getXSs() - columns.get(1).getXSs();
            double newRatio = newGap2 / newGap1;

            // Ratios should be preserved (uniform compression)
            assertEquals(originalRatio, newRatio, 0.01,
                "Compression should be uniform - spacing ratios should be preserved");
        }

        @Test
        @DisplayName("moderate compression of evenly spaced columns")
        void moderateCompressionEvenSpacing() {
            // Create 5 columns evenly spaced
            var columns = createPositionedColumns(100.0, 150.0, 200.0, 250.0, 300.0);
            var staffRightMargin = 270.0; // Needs moderate compression

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertTrue(result.wasCompressionApplied());

            // Verify all columns are positioned correctly
            assertEquals(100.0, columns.get(0).getXSs(), 0.01);

            // Verify monotonic increase
            for (var i = 1; i < columns.size(); i++) {
                assertTrue(columns.get(i).getXSs() > columns.get(i - 1).getXSs(),
                    String.format("Column %d should be right of column %d", i, i - 1));
            }

            // Verify final position fits
            var lastColumn = columns.get(columns.size() - 1);
            assertTrue(lastColumn.getRightEdgeXSs() <= staffRightMargin + 0.01);
        }
    }

    // ==========================================================================
    // Compression Validation Failures
    // ==========================================================================

    @Nested
    @DisplayName("compression validation failures")
    class CompressionValidationFailureTests {

        @Test
        @DisplayName("rejects when compression would violate minimum column gap")
        void rejectsMinimumColumnGapViolation() {
            // Create tightly spaced columns
            var columns = createPositionedColumns(100.0, 100.0 + 20.0, 100.0 + 40.0);

            // Try to compress to a margin that would violate minimum gap
            var staffRightMargin = 110.0; // Very tight

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("minimum spacing"),
                "Error message should mention minimum spacing");
        }

        @Test
        @DisplayName("rejects when compression would violate minimum syllable gap")
        void rejectsMinimumSyllableGapViolation() {
            // Create columns with wide syllables, moderately spaced
            var columns = createLyricColumns("wonderful", "amazing", "beautiful");

            // Try to compress to a very tight margin that would violate syllable gap
            var staffRightMargin = 120.0; // Very tight for wide syllables

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
            assertTrue(
                result.getErrorMessage().contains("syllable") ||
                    result.getErrorMessage().contains("spacing"),
                "Error message should mention syllable or spacing issue");
        }

        @Test
        @DisplayName("rejects extreme compression scenarios")
        void rejectsExtremeCompression() {
            // Create tightly spaced columns to ensure extreme compression fails
            var columns = createPositionedColumns(100.0, 120.0, 140.0, 160.0, 180.0);
            var staffRightMargin = 110.0; // Very extreme compression needed

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
        }
    }

    // ==========================================================================
    // Edge Cases
    // ==========================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("handles two columns")
        void twoColumns() {
            var columns = createPositionedColumns(100.0, 200.0);
            var staffRightMargin = 180.0;

            var result = calculator.justifyLine(columns, staffRightMargin);

            // Should either succeed with compression or fail gracefully
            if (!result.isSuccess()) {
                assertNotNull(result.getErrorMessage());
            } else if (result.wasCompressionApplied()) {
                assertEquals(100.0, columns.get(0).getXSs(), 0.01);
                assertTrue(columns.get(1).getXSs() < 200.0);
                assertTrue(columns.get(1).getRightEdgeXSs() <= staffRightMargin + 0.01);
            }
        }

        @Test
        @DisplayName("handles very small margin excess")
        void verySmallMarginExcess() {
            var columns = createPositionedColumns(100.0, 150.0, 200.0);
            var lastColumn = columns.get(columns.size() - 1);
            var staffRightMargin = lastColumn.getRightEdgeXSs() - 1.0; // 1px over

            var result = calculator.justifyLine(columns, staffRightMargin);

            // Should either succeed or fail gracefully
            if (!result.isSuccess()) {
                assertNotNull(result.getErrorMessage());
            }
        }

        @Test
        @DisplayName("handles columns with varying extents")
        void varyingExtents() {
            var columns = new ArrayList<NoteColumn>();

            // Column with large left extent (accidental)
            var col1 = createSimpleColumn(-15.0, 5.0, null);
            col1.setXSs(100.0);
            columns.add(col1);

            // Column with large right extent (dots)
            var col2 = createSimpleColumn(-5.0, 15.0, null);
            col2.setXSs(200.0);
            columns.add(col2);

            // Normal column
            var col3 = createSimpleColumn(-5.0, 5.0, null);
            col3.setXSs(300.0);
            columns.add(col3);

            var staffRightMargin = 280.0;

            var result = calculator.justifyLine(columns, staffRightMargin);

            // Should handle varying extents correctly
            if (!result.isSuccess()) {
                assertNotNull(result.getErrorMessage());
            } else if (result.wasCompressionApplied()) {
                assertTrue(columns.get(2).getRightEdgeXSs() <= staffRightMargin + 0.01);
            }
        }
    }

    // ==========================================================================
    // Integration Scenarios
    // ==========================================================================

    @Nested
    @DisplayName("integration scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("realistic score line with mixed content")
        void realisticScoreLine() {
            var line = createLine();

            // Create realistic columns with syllables
            var note1 = createNote("Hel");
            var note2 = createNote("lo");
            var note3 = createNote("won");
            var note4 = createNote("der");
            var note5 = createNote("ful");

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);
            line.addNote(note5);

            var columns = new ArrayList<NoteColumn>();

            for (var i = 0; i < line.noteCount(); i++) {
                columns.add(builder.buildColumn(line.getNote(i), line));
            }

            // Position them with HorizontalSpacingCalculator
            var spacingCalculator = new HorizontalSpacingCalculator();
            spacingCalculator.calculatePositions(columns, line);

            // Get the calculated width
            var lastColumn = columns.get(columns.size() - 1);
            var calculatedWidth = lastColumn.getRightEdgeXSs();

            // Try to compress to 90% of calculated width
            var staffRightMargin = calculatedWidth * 0.9;

            var result = calculator.justifyLine(columns, staffRightMargin);

            // Should either succeed or fail gracefully with error message
            if (result.isSuccess()) {
                assertTrue(lastColumn.getRightEdgeXSs() <= staffRightMargin + 0.01);
            } else {
                assertNotNull(result.getErrorMessage());
            }
        }

        @Test
        @DisplayName("no compression needed for sparse line")
        void noCompressionForSparseLine() {
            var line = createLine();
            var note1 = createNote("a");
            var note2 = createNote("b");

            line.addNote(note1);
            line.addNote(note2);

            var columns = new ArrayList<NoteColumn>();

            for (var i = 0; i < line.noteCount(); i++) {
                columns.add(builder.buildColumn(line.getNote(i), line));
            }

            var spacingCalculator = new HorizontalSpacingCalculator();
            spacingCalculator.calculatePositions(columns, line);

            var lastColumn = columns.get(columns.size() - 1);
            var staffRightMargin = lastColumn.getRightEdgeXSs() + 100.0; // Plenty of room

            var result = calculator.justifyLine(columns, staffRightMargin);

            assertTrue(result.isSuccess());
            assertFalse(result.wasCompressionApplied());
        }
    }
}
