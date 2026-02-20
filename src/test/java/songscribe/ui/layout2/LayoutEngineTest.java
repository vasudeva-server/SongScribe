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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout.BeamGroup;

@DisplayName("LayoutEngine Integration Tests")
class LayoutEngineTest {

    private LayoutEngine engine;
    private Graphics2D g2;
    private Font lyricsFont;
    private double staffRightMargin;

    @BeforeEach
    void setUp() {
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        g2 = image.createGraphics();
        lyricsFont = new Font("SansSerif", Font.PLAIN, 12);
        staffRightMargin = 62.5;  // 500px / 8.0 ppss

        engine = new LayoutEngine(g2, lyricsFont, staffRightMargin);
    }

    // ==========================================================================
    // Test Helpers
    // ==========================================================================

    private Line createLine(int keyAccidentalCount) {
        var line = new Line();

        line.setKeyAccidentalCount(keyAccidentalCount);

        if (keyAccidentalCount > 0) {
            line.setKeyType(KeyType.SHARPS);
        }

        return line;
    }

    private Note createNote(String syllable, Note.Accidental accidental, int dots) {
        var note = NoteType.CROTCHET.newInstance();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        if (accidental != null) {
            note.setAccidental(accidental);
        }

        note.setDotCount(dots);

        return note;
    }

    private Note createQuaver(String syllable) {
        var note = NoteType.QUAVER.newInstance();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        return note;
    }

    private void assertLayoutResultValid(LayoutResult result, Line line) {
        assertNotNull(result, "Layout result should not be null");
        assertEquals(line.noteCount(), result.getNoteColumnCount(),
            "Result should contain a column for each note");
        assertTrue(result.getLineHeightSs() > 0, "Line height should be positive");
        assertTrue(result.getStaffBottomYSs() > result.getStaffTopYSs(),
            "Staff bottom should be below staff top");
    }

    private void assertNotePositioned(LayoutResult result, Note note) {
        var column = result.getNoteColumn(note);
        assertNotNull(column, "Note should have a column in result");
        assertTrue(column.getXSs() > 0, "Note X position should be positive");
    }

    private void assertNotesMonotonicallyIncreasing(LayoutResult result, List<Note> notes) {
        double prevX = 0;

        for (var i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            var x = result.getNoteXSs(note);
            assertTrue(x > prevX,
                String.format("Note %d (x=%.2f) should be right of previous note (x=%.2f)",
                    i, x, prevX));
            prevX = x;
        }
    }

    private void assertMinimumSpacing(LayoutResult result, Note note1, Note note2) {
        var col1 = result.getNoteColumn(note1);
        var col2 = result.getNoteColumn(note2);
        assertNotNull(col1);
        assertNotNull(col2);

        double gap = col2.getLeftEdgeXSs() - col1.getRightEdgeXSs();
        double minGap = LayoutConstants.MIN_COLUMN_GAP_SS;

        assertTrue(gap >= minGap - 0.01,
            String.format("Gap between notes (%.2f) should be >= minimum (%.2f)",
                gap, minGap));
    }

    // ==========================================================================
    // Empty/Null Input Tests
    // ==========================================================================

    @Nested
    @DisplayName("Empty/Null Input Tests")
    class EmptyInputTests {

        @Test
        @DisplayName("handles empty line")
        void emptyLine() {
            var line = createLine(0);

            var result = engine.layout(line);

            assertNotNull(result);
            assertEquals(0, result.getNoteColumnCount());
            assertTrue(result.getLineHeightSs() > 0);
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("handles line with no notes but has key signature")
        void emptyLineWithKeySignature() {
            var line = createLine(3);

            var result = engine.layout(line);

            assertNotNull(result);
            assertEquals(0, result.getNoteColumnCount());
            assertTrue(result.getLineHeightSs() > 0);
            assertNull(engine.getLastError());
        }
    }

    // ==========================================================================
    // Single Note Tests
    // ==========================================================================

    @Nested
    @DisplayName("Single Note Tests")
    class SingleNoteTests {

        @Test
        @DisplayName("single note without lyrics")
        void singleNoteNoLyrics() {
            var line = createLine(0);
            var note = createNote(null, null, 0);
            line.addNote(note);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotePositioned(result, note);
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("single note with lyric syllable")
        void singleNoteWithLyric() {
            var line = createLine(0);
            var note = createNote("Hello", null, 0);
            line.addNote(note);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotePositioned(result, note);

            var column = result.getNoteColumn(note);
            assertTrue(column.hasSyllable(), "Note should have syllable");
            assertEquals("Hello", column.getSyllable());
            assertTrue(result.getLyricBaselineYSs() > 0,
                "Lyric baseline should be positive");
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("single note with accidental")
        void singleNoteWithAccidental() {
            var line = createLine(0);
            var note = createNote(null, Note.Accidental.SHARP, 0);
            line.addNote(note);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotePositioned(result, note);

            var column = result.getNoteColumn(note);
            assertTrue(column.getLeftExtentSs() < -NoteColumnBuilder.HALF_NOTE_HEAD_SS,
                "Left extent should include accidental width");
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("single note with dots")
        void singleNoteWithDots() {
            var line = createLine(0);
            var note = createNote(null, null, 2);
            line.addNote(note);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotePositioned(result, note);

            var column = result.getNoteColumn(note);
            assertTrue(column.getRightExtentSs() > NoteColumnBuilder.HALF_NOTE_HEAD_SS,
                "Right extent should include dot widths");
            assertNull(engine.getLastError());
        }
    }

    // ==========================================================================
    // Multi-Note Tests
    // ==========================================================================

    @Nested
    @DisplayName("Multi-Note Tests")
    class MultiNoteTests {

        @Test
        @DisplayName("multiple notes without lyrics use minimum spacing")
        void multipleNotesNoLyrics() {
            var line = createLine(0);
            var note1 = createNote(null, null, 0);
            var note2 = createNote(null, null, 0);
            var note3 = createNote(null, null, 0);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result, List.of(note1, note2, note3));
            assertMinimumSpacing(result, note1, note2);
            assertMinimumSpacing(result, note2, note3);

            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            var col3 = result.getNoteColumn(note3);
            assertFalse(col1.hasSyllable());
            assertFalse(col2.hasSyllable());
            assertFalse(col3.hasSyllable());
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("multiple notes with lyrics use lyric-driven spacing")
        void multipleNotesWithLyrics() {
            var line = createLine(0);
            var note1 = createNote("wonderful", null, 0);
            var note2 = createNote("amazing", null, 0);
            var note3 = createNote("beautiful", null, 0);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result, List.of(note1, note2, note3));

            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            var col3 = result.getNoteColumn(note3);

            assertTrue(col1.hasSyllable());
            assertTrue(col2.hasSyllable());
            assertTrue(col3.hasSyllable());

            double spacing = col2.getXSs() - col1.getXSs();
            double minSpacing = col1.getRightExtentSs() +
                LayoutConstants.MIN_COLUMN_GAP_SS +
                Math.abs(col2.getLeftExtentSs());

            assertTrue(spacing >= minSpacing,
                "Lyric spacing should be at least minimum note spacing");
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("mixed notes with and without lyrics")
        void mixedLyrics() {
            var line = createLine(0);
            var note1 = createNote("Hello", null, 0);
            var note2 = createNote(null, null, 0);
            var note3 = createNote("world", null, 0);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result, List.of(note1, note2, note3));

            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            var col3 = result.getNoteColumn(note3);

            assertTrue(col1.hasSyllable());
            assertFalse(col2.hasSyllable());
            assertTrue(col3.hasSyllable());
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("short syllables still maintain minimum spacing")
        void shortSyllables() {
            var line = createLine(0);
            var note1 = createNote("a", null, 0);
            var note2 = createNote("I", null, 0);
            var note3 = createNote("o", null, 0);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertMinimumSpacing(result, note1, note2);
            assertMinimumSpacing(result, note2, note3);
            assertNull(engine.getLastError());
        }
    }

    // ==========================================================================
    // Complex Scenario Tests
    // ==========================================================================

    @Nested
    @DisplayName("Complex Scenario Tests")
    class ComplexScenarioTests {

        @Test
        @DisplayName("notes with accidentals maintain proper clearance")
        void notesWithAccidentals() {
            var line = createLine(0);
            var note1 = createNote("Hello", null, 0);
            var note2 = createNote("world", Note.Accidental.SHARP, 0);
            var note3 = createNote("today", Note.Accidental.FLAT, 0);
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result, List.of(note1, note2, note3));

            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            double clearance = col2.getLeftEdgeXSs() - col1.getRightEdgeXSs();
            double minClearance = LayoutConstants.ACCIDENTAL_CLEARANCE_SS;

            assertTrue(clearance >= minClearance - 0.01,
                "Accidental should have proper clearance");
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("beam group without lyrics uses tight spacing")
        void beamGroupNoLyrics() {
            var line = createLine(0);
            var note1 = createQuaver(null);
            var note2 = createQuaver(null);
            var note3 = createQuaver(null);
            var note4 = createQuaver(null);

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);

            var beamGroup = new BeamGroup();
            beamGroup.addNote(note1);
            beamGroup.addNote(note2);
            beamGroup.addNote(note3);
            beamGroup.addNote(note4);
            line.addBeamGroup(beamGroup);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result, List.of(note1, note2, note3, note4));

            var col1 = result.getNoteColumn(note1);
            var col2 = result.getNoteColumn(note2);
            assertNotNull(col1.getBeamGroup());
            assertNotNull(col2.getBeamGroup());
            assertEquals(col1.getBeamGroup(), col2.getBeamGroup());
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("beam group with lyrics expands spacing")
        void beamGroupWithLyrics() {
            var line = createLine(0);
            var note1 = createQuaver("won");
            var note2 = createQuaver("der");
            var note3 = createQuaver("ful");
            var note4 = createQuaver("day");

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);

            var beamGroup = new BeamGroup();
            beamGroup.addNote(note1);
            beamGroup.addNote(note2);
            beamGroup.addNote(note3);
            beamGroup.addNote(note4);
            line.addBeamGroup(beamGroup);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result, List.of(note1, note2, note3, note4));

            for (var i = 0; i < 4; i++) {
                var note = line.getNote(i);
                var col = result.getNoteColumn(note);
                assertTrue(col.hasSyllable());
            }

            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("line with key signature positions first note correctly")
        void lineWithKeySignature() {
            var line = createLine(3);
            var note1 = createNote("Test", null, 0);
            var note2 = createNote("note", null, 0);
            line.addNote(note1);
            line.addNote(note2);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);

            var col1 = result.getNoteColumn(note1);
            double expectedMinX = LayoutConstants.calculateFirstNoteXSs(3);

            assertTrue(col1.getXSs() >= expectedMinX - 0.01,
                "First note should account for key signature width");
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("complex line with all features")
        void complexLineAllFeatures() {
            var line = createLine(2);

            var note1 = createNote("Hel", null, 0);
            var note2 = createNote("lo", Note.Accidental.SHARP, 1);
            var note3 = createQuaver("won");
            var note4 = createQuaver("der");
            var note5 = createNote(null, null, 0);
            var note6 = createNote("ful", null, 2);

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);
            line.addNote(note5);
            line.addNote(note6);

            var beamGroup = new BeamGroup();
            beamGroup.addNote(note3);
            beamGroup.addNote(note4);
            line.addBeamGroup(beamGroup);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result,
                List.of(note1, note2, note3, note4, note5, note6));

            for (var i = 0; i < line.noteCount(); i++) {
                assertNotePositioned(result, line.getNote(i));
            }

            assertTrue(result.getLineWidthSs() < staffRightMargin,
                "Line should fit within margin");
            assertNull(engine.getLastError());
        }
    }

    // ==========================================================================
    // Error Handling Tests
    // ==========================================================================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("line that cannot fit within margin returns null")
        void lineTooWide() {
            var tinyMargin = 12.5;  // 100px / 8.0 ppss
            var tinyEngine = new LayoutEngine(g2, lyricsFont, tinyMargin);

            var line = createLine(0);

            for (var i = 0; i < 20; i++) {
                line.addNote(createNote("wonderful", null, 0));
            }

            var result = tinyEngine.layout(line);

            assertNull(result, "Layout should fail for line that cannot fit");
            assertNotNull(tinyEngine.getLastError(),
                "Error message should be set when layout fails");
            assertTrue(tinyEngine.getLastError().length() > 0,
                "Error message should not be empty");
        }

        @Test
        @DisplayName("line requiring compression succeeds if possible")
        void lineRequiringCompression() {
            var moderateMargin = 31.25;  // 250px / 8.0 ppss
            var moderateEngine = new LayoutEngine(g2, lyricsFont, moderateMargin);

            var line = createLine(0);

            for (var i = 0; i < 5; i++) {
                line.addNote(createNote("test", null, 0));
            }

            var result = moderateEngine.layout(line);

            if (result != null) {
                assertLayoutResultValid(result, line);
                assertTrue(result.getLineWidthSs() <= moderateMargin + 0.01,
                    "Compressed line should fit within margin");
                assertNull(moderateEngine.getLastError());
            } else {
                assertNotNull(moderateEngine.getLastError());
            }
        }

        @Test
        @DisplayName("getLastError returns null after successful layout")
        void lastErrorClearedOnSuccess() {
            var tinyMargin = 6.25;  // 50px / 8.0 ppss
            var testEngine = new LayoutEngine(g2, lyricsFont, tinyMargin);

            var line1 = createLine(0);

            for (var i = 0; i < 20; i++) {
                line1.addNote(createNote("wonderful", null, 0));
            }

            var result1 = testEngine.layout(line1);
            assertNull(result1);
            assertNotNull(testEngine.getLastError());

            var line2 = createLine(0);
            line2.addNote(createNote("a", null, 0));

            var result2 = testEngine.layout(line2);

            if (result2 != null) {
                assertNull(testEngine.getLastError(),
                    "Error should be cleared after successful layout");
            }
        }
    }

    // ==========================================================================
    // Integration Scenarios
    // ==========================================================================

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("realistic song line layout")
        void realisticSongLine() {
            var line = createLine(0);
            var lyrics = List.of("A", "won", "der", "ful", "day", "to", "day");

            for (var syllable : lyrics) {
                line.addNote(createNote(syllable, null, 0));
            }

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertEquals(lyrics.size(), result.getNoteColumnCount());

            var notes = new ArrayList<Note>();

            for (var i = 0; i < line.noteCount(); i++) {
                var note = line.getNote(i);
                notes.add(note);

                var col = result.getNoteColumn(note);
                assertTrue(col.hasSyllable());
            }

            assertNotesMonotonicallyIncreasing(result, notes);
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("line with multiple beam groups")
        void multipleBeamGroups() {
            var line = createLine(0);

            var note1 = createQuaver("fast");
            var note2 = createQuaver("notes");
            var note3 = createNote("then", null, 0);
            var note4 = createQuaver("more");
            var note5 = createQuaver("fast");

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);
            line.addNote(note5);

            var beamGroup1 = new BeamGroup();
            beamGroup1.addNote(note1);
            beamGroup1.addNote(note2);
            line.addBeamGroup(beamGroup1);

            var beamGroup2 = new BeamGroup();
            beamGroup2.addNote(note4);
            beamGroup2.addNote(note5);
            line.addBeamGroup(beamGroup2);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertNotesMonotonicallyIncreasing(result,
                List.of(note1, note2, note3, note4, note5));
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("line with varying note features")
        void varyingNoteFeatures() {
            var line = createLine(1);

            var note1 = createNote("Start", null, 0);
            var note2 = createNote("sharp", Note.Accidental.SHARP, 0);
            var note3 = createNote("flat", Note.Accidental.FLAT, 0);
            var note4 = createNote("dot", null, 1);
            var note5 = createNote("two", null, 2);
            var note6 = createNote(null, null, 0);
            var note7 = createNote("end", null, 0);

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);
            line.addNote(note5);
            line.addNote(note6);
            line.addNote(note7);

            var result = engine.layout(line);

            assertLayoutResultValid(result, line);
            assertEquals(7, result.getNoteColumnCount());
            assertNotesMonotonicallyIncreasing(result,
                List.of(note1, note2, note3, note4, note5, note6, note7));
            assertNull(engine.getLastError());
        }

        @Test
        @DisplayName("engine properties are accessible")
        void engineProperties() {
            assertEquals(g2, engine.getGraphics());
            assertEquals(lyricsFont, engine.getLyricsFont());
            assertEquals(staffRightMargin, engine.getStaffRightMarginSs());
        }
    }
}
