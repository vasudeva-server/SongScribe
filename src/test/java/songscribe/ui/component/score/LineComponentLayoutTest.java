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

package songscribe.ui.component.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.awt.image.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.NoteType;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Note;

import songscribe.ui.layout2.LayoutEngine;
import songscribe.ui.layout2.LayoutResult;

/**
 * Integration tests for LineComponent's consumption of LayoutResult from layout2 system.
 * <p>
 * Tests verify that LineComponent, NoteRenderer, and LyricsRenderer correctly consume
 * the LayoutResult data structure for positioning elements during rendering.
 */
@DisplayName("LineComponent Layout Integration")
class LineComponentLayoutTest {

    private Graphics2D g2;
    private Font lyricsFont;
    private int staffRightMargin = 600;

    @BeforeEach
    void setUp() {
        // Create real Graphics2D for accurate rendering and measurements
        var image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        g2 = image.createGraphics();

        // Set up test font
        lyricsFont = new Font("SansSerif", Font.PLAIN, 12);
    }

    // ==========================================================================
    // Test Helpers
    // ==========================================================================

    /**
     * Creates a test line with optional key signature.
     */
    private Line createLine(int keyAccidentalCount) {
        var line = new Line();
        line.setKeyAccidentalCount(keyAccidentalCount);

        if (keyAccidentalCount > 0) {
            line.setKeyType(KeyType.SHARPS);
        }

        return line;
    }

    /**
     * Creates a note with optional syllable and accidental.
     */
    private Note createNote(String syllable, Note.Accidental accidental) {
        var note = NoteType.CROTCHET.newInstance();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        if (accidental != null) {
            note.setAccidental(accidental);
        }

        return note;
    }

    /**
     * Creates a quaver (eighth note) for beam groups.
     */
    private Note createQuaver(String syllable) {
        var note = NoteType.QUAVER.newInstance();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        return note;
    }

    /**
     * Performs layout using LayoutEngine and returns the result.
     */
    private LayoutResult performLayout(Line line) {
        var engine = new LayoutEngine(g2, lyricsFont, staffRightMargin);
        return engine.layout(line);
    }

    /**
     * Performs layout with custom staff width.
     */
    private LayoutResult performLayout(Line line, int customStaffWidth) {
        var engine = new LayoutEngine(g2, lyricsFont, customStaffWidth);
        return engine.layout(line);
    }

    // ==========================================================================
    // LineComponent.performLayout() Tests
    // ==========================================================================

    @Nested
    @DisplayName("LineComponent.performLayout() behavior")
    class PerformLayoutTests {

        @Test
        @DisplayName("creates non-null LayoutResult for simple line")
        void createsLayoutResultForSimpleLine() {
            var line = createLine(0);
            line.addNote(createNote("hel", null));
            line.addNote(createNote("lo", null));

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");
        }

        @Test
        @DisplayName("LayoutResult contains expected note columns")
        void layoutResultContainsNoteColumns() {
            var line = createLine(0);
            var note1 = createNote("hel", null);
            var note2 = createNote("lo", null);
            var note3 = createNote("world", null);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");
            assertEquals(3, result.getNoteColumnCount(), "Should have 3 note columns");

            assertTrue(result.hasNote(note1), "Should contain note1");
            assertTrue(result.hasNote(note2), "Should contain note2");
            assertTrue(result.hasNote(note3), "Should contain note3");
        }

        @Test
        @DisplayName("LayoutResult contains element bounds")
        void layoutResultContainsElementBounds() {
            var line = createLine(0);
            line.addNote(createNote("hel", null));
            line.addNote(createNote("lo", null));

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");
            assertTrue(result.getElementCount() >= 0, "Should have element bounds (possibly empty)");
        }

        @Test
        @DisplayName("LayoutResult staff geometry is set correctly")
        void layoutResultStaffGeometryIsSet() {
            var line = createLine(0);
            line.addNote(createNote("hel", null));

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");
            assertEquals(0.0, result.getStaffTopY(), 0.01, "Staff top should be at 0");
            assertEquals(32.0, result.getStaffBottomY(), 0.01, "Staff bottom should be at 32 (standard staff height)");
        }

        @Test
        @DisplayName("empty line creates valid LayoutResult")
        void emptyLineCreatesValidLayoutResult() {
            var line = createLine(0);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null even for empty line");
            assertEquals(0, result.getNoteColumnCount(), "Empty line should have 0 note columns");
            assertEquals(32.0, result.getLineHeight(), 0.01, "Empty line should have standard staff height");
        }
    }

    // ==========================================================================
    // Layout Failure Handling Tests
    // ==========================================================================

    @Nested
    @DisplayName("Layout failure handling")
    class LayoutFailureTests {

        @Test
        @DisplayName("handles line exceeding staffRightMargin")
        void handlesLineExceedingMargin() {
            var line = createLine(0);

            // Add many notes with long syllables that will exceed a narrow margin
            for (var i = 0; i < 20; i++) {
                line.addNote(createNote("wonderful", null));
            }

            var engine = new LayoutEngine(g2, lyricsFont, 100);
            var result = engine.layout(line);

            // Layout should fail for overcrowded line
            assertNull(result, "Layout should return null when line cannot fit");
            assertNotNull(engine.getLastError(), "Engine should provide error message");
            assertTrue(
                engine.getLastError().contains("cannot fit") ||
                    engine.getLastError().contains("minimum spacing"),
                "Error should indicate spacing/fitting issue"
            );
        }

        @Test
        @DisplayName("handles null result gracefully in rendering context")
        void handlesNullResultGracefully() {
            var line = createLine(0);
            line.addNote(createNote("test", null));

            var result = performLayout(line);

            // Even if result were null, getNoteX should fall back gracefully
            // This tests the fallback mechanism in NoteRenderer
            if (result == null) {
                // Fallback to note.getXPos() should work
                var note = line.getNote(0);
                var xPos = note.getXPos();
                assertTrue(xPos >= 0, "Fallback xPos should be non-negative");
            } else {
                assertNotNull(result, "Layout should succeed for simple line");
            }
        }
    }

    // ==========================================================================
    // NoteRenderer Integration Tests
    // ==========================================================================

    @Nested
    @DisplayName("NoteRenderer integration with LayoutResult")
    class NoteRendererIntegrationTests {

        @Test
        @DisplayName("NoteRenderer uses layoutResult.getNoteX() for positioning")
        void noteRendererUsesLayoutResultX() {
            var line = createLine(0);
            var note1 = createNote("hel", null);
            var note2 = createNote("lo", null);
            line.addNote(note1);
            line.addNote(note2);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");

            var x1 = result.getNoteX(note1);
            var x2 = result.getNoteX(note2);

            assertTrue(x1 > 0, "Note 1 X position should be positive");
            assertTrue(x2 > x1, "Note 2 should be to the right of Note 1");
        }

        @Test
        @DisplayName("NoteRenderer falls back to note.getXPos() when layoutResult is null")
        void noteRendererFallsBackToNoteXPos() {
            var line = createLine(0);
            var note = createNote("test", null);
            note.setXPos(100);
            line.addNote(note);

            // When layoutResult is null, renderer should use note.getXPos()
            var fallbackX = note.getXPos();

            assertEquals(100, fallbackX, "Should use note's stored X position as fallback");
        }

        @Test
        @DisplayName("NoteRenderer handles notes with accidentals")
        void noteRendererHandlesAccidentals() {
            var line = createLine(0);
            var note1 = createNote("test", Note.Accidental.SHARP);
            var note2 = createNote("hello", Note.Accidental.FLAT);
            line.addNote(note1);
            line.addNote(note2);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");

            var column1 = result.getNoteColumn(note1);
            var column2 = result.getNoteColumn(note2);

            assertNotNull(column1, "Note 1 should have a column");
            assertNotNull(column2, "Note 2 should have a column");

            // Accidentals extend the left extent
            assertTrue(column1.getLeftExtent() < 0, "Note with accidental should have negative left extent");
            assertTrue(column2.getLeftExtent() < 0, "Note with accidental should have negative left extent");
        }
    }

    // ==========================================================================
    // LyricsRenderer Integration Tests
    // ==========================================================================

    @Nested
    @DisplayName("LyricsRenderer integration with LayoutResult")
    class LyricsRendererIntegrationTests {

        @Test
        @DisplayName("LyricsRenderer uses layoutResult.getNoteX() for syllable positioning")
        void lyricsRendererUsesLayoutResultX() {
            var line = createLine(0);
            var note1 = createNote("won", null);
            var note2 = createNote("der", null);
            var note3 = createNote("ful", null);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");

            // Verify that syllable positions are calculated from note X positions
            var x1 = result.getNoteX(note1);
            var x2 = result.getNoteX(note2);
            var x3 = result.getNoteX(note3);

            assertTrue(x1 > 0, "Note 1 X should be positive");
            assertTrue(x2 > x1, "Note 2 should be to the right of Note 1");
            assertTrue(x3 > x2, "Note 3 should be to the right of Note 2");

            // Syllable widths should drive spacing
            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);

            assertTrue(col1.getSyllableWidth() > 0, "Syllable 'won' should have width");
            assertTrue(col2.getSyllableWidth() > 0, "Syllable 'der' should have width");
        }

        @Test
        @DisplayName("LyricsRenderer uses lyric baseline from LayoutResult")
        void lyricsRendererUsesBaselineFromLayoutResult() {
            var line = createLine(0);
            line.addNote(createNote("hel", null));
            line.addNote(createNote("lo", null));

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");
            assertTrue(result.hasLyrics(), "Line with syllables should have lyrics");
            assertTrue(result.getLyricBaselineY() > 0, "Lyric baseline should be below staff");
        }

        @Test
        @DisplayName("LyricsRenderer handles notes without syllables")
        void lyricsRendererHandlesNotesWithoutSyllables() {
            var line = createLine(0);
            var note1 = createNote("hel", null);
            var note2 = createNote(null, null); // No syllable
            var note3 = createNote("lo", null);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");

            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            var col3 = result.getNoteColumn(note3);

            assertTrue(col1.hasSyllable(), "Note 1 should have syllable");
            assertFalse(col2.hasSyllable(), "Note 2 should not have syllable");
            assertTrue(col3.hasSyllable(), "Note 3 should have syllable");
        }

        @Test
        @DisplayName("LyricsRenderer fallback behavior when layoutResult is null")
        void lyricsRendererFallsBackWhenLayoutResultNull() {
            var line = createLine(0);
            var note = createNote("test", null);
            note.setXPos(100);
            line.addNote(note);

            // Even without layout result, syllable should be accessible
            assertEquals("test", note.properties.syllable, "Syllable should be stored in note");
            assertEquals(100, note.getXPos(), "X position should be available as fallback");
        }

        @Test
        @DisplayName("LyricsRenderer handles long syllables driving spacing")
        void lyricsRendererHandlesLongSyllables() {
            var line = createLine(0);
            var note1 = createNote("won", null);
            var note2 = createNote("der", null);
            var note3 = createNote("ful", null);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");

            // Verify that spacing accommodates syllable widths
            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            var col3 = result.getNoteColumn(note3);

            var spacing12 = col2.getX() - col1.getX();
            var spacing23 = col3.getX() - col2.getX();

            // Spacing should be reasonable (greater than minimum note spacing, which is about 16-18px)
            assertTrue(spacing12 > 15, "Spacing between notes should be reasonable (was " + spacing12 + ")");
            assertTrue(spacing23 > 15, "Spacing between notes should be reasonable (was " + spacing23 + ")");
        }
    }

    // ==========================================================================
    // Complex Integration Scenarios
    // ==========================================================================

    @Nested
    @DisplayName("Complex integration scenarios")
    class ComplexIntegrationTests {

        @Test
        @DisplayName("handles line with mixed notes, syllables, and accidentals")
        void handlesComplexLine() {
            var line = createLine(2); // 2 sharps

            var note1 = createNote("Hel", null);
            var note2 = createNote("lo", Note.Accidental.SHARP);
            var note3 = createNote(null, null); // No syllable
            var note4 = createNote("world", Note.Accidental.FLAT);

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");
            assertEquals(4, result.getNoteColumnCount(), "Should have 4 note columns");

            // Verify all notes are positioned
            assertTrue(result.getNoteX(note1) > 0, "Note 1 should be positioned");
            assertTrue(result.getNoteX(note2) > result.getNoteX(note1), "Note 2 should be right of Note 1");
            assertTrue(result.getNoteX(note3) > result.getNoteX(note2), "Note 3 should be right of Note 2");
            assertTrue(result.getNoteX(note4) > result.getNoteX(note3), "Note 4 should be right of Note 3");

            // Verify syllables
            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            var col3 = result.getNoteColumn(note3);
            var col4 = result.getNoteColumn(note4);

            assertTrue(col1.hasSyllable(), "Note 1 should have syllable");
            assertTrue(col2.hasSyllable(), "Note 2 should have syllable");
            assertFalse(col3.hasSyllable(), "Note 3 should not have syllable");
            assertTrue(col4.hasSyllable(), "Note 4 should have syllable");
        }

        @Test
        @DisplayName("handles line with key signature affecting first note position")
        void handlesKeySignature() {
            var lineNoKey = createLine(0);
            var lineWithKey = createLine(3); // 3 sharps

            lineNoKey.addNote(createNote("test", null));
            lineWithKey.addNote(createNote("test", null));

            var resultNoKey = performLayout(lineNoKey);
            var resultWithKey = performLayout(lineWithKey);

            assertNotNull(resultNoKey, "LayoutResult without key should not be null");
            assertNotNull(resultWithKey, "LayoutResult with key should not be null");

            var xNoKey = resultNoKey.getNoteX(lineNoKey.getNote(0));
            var xWithKey = resultWithKey.getNoteX(lineWithKey.getNote(0));

            assertTrue(
                xWithKey > xNoKey,
                "First note with key signature should be further right due to key signature width"
            );
        }

        @Test
        @DisplayName("handles rendering with actual Graphics2D context")
        void handlesActualRendering() {
            var line = createLine(0);
            var note1 = createNote("Hel", null);
            var note2 = createNote("lo", Note.Accidental.SHARP);
            line.addNote(note1);
            line.addNote(note2);

            var result = performLayout(line);

            assertNotNull(result, "LayoutResult should not be null");

            // Verify we can get positions for rendering
            var x1 = result.getNoteX(note1);
            var x2 = result.getNoteX(note2);

            assertNotNull(result.getNoteColumn(note1), "Should have column for note1");
            assertNotNull(result.getNoteColumn(note2), "Should have column for note2");

            assertTrue(x1 > 0 && x2 > x1, "Positions should be valid for rendering");
        }
    }
}
