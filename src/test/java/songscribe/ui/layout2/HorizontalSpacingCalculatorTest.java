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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.Crotchet;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.Quaver;
import songscribe.ui.layout.BeamGroup;

@DisplayName("HorizontalSpacingCalculator")
class HorizontalSpacingCalculatorTest {

    private HorizontalSpacingCalculator calculator;
    private Graphics2D g2;
    private Font lyricsFont;
    private NoteColumnBuilder builder;

    @BeforeEach
    void setUp() {
        calculator = new HorizontalSpacingCalculator();

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
    private Note createNote(String syllable, Note.Accidental accidental, int dots) {
        var note = new Crotchet();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        if (accidental != null) {
            note.setAccidental(accidental);
        }

        note.setDotCount(dots);

        return note;
    }

    /**
     * Creates a quaver (eighth note) for beam groups.
     */
    private Note createQuaver(String syllable) {
        var note = new Quaver();

        if (syllable != null) {
            note.properties.syllable = syllable;
        }

        return note;
    }

    /**
     * Builds columns from a list of notes and a line.
     */
    private List<NoteColumn> buildColumns(List<Note> notes, Line line) {
        var columns = new ArrayList<NoteColumn>();

        for (var note : notes) {
            line.addNote(note);
            var column = builder.buildColumn(note, line);
            columns.add(column);
        }

        return columns;
    }

    /**
     * Asserts that spacing between two columns is at least a minimum value.
     */
    private void assertMinimumSpacing(NoteColumn prev, NoteColumn curr, double expectedMin) {
        double actualSpacing = curr.getX() - prev.getX();
        assertTrue(
            actualSpacing >= expectedMin - 0.01, // Allow small floating point error
            String.format(
                "Expected spacing >= %.2f, but was %.2f",
                expectedMin,
                actualSpacing
            )
        );
    }

    // ==========================================================================
    // Empty Line Tests
    // ==========================================================================

    @Nested
    @DisplayName("calculatePositions with empty line")
    class EmptyLineTests {

        @Test
        @DisplayName("handles empty column list")
        void emptyColumnList() {
            var line = createLine(0);
            var columns = new ArrayList<NoteColumn>();

            calculator.calculatePositions(columns, line);

            // Should not throw exception
            assertEquals(0, columns.size());
        }
    }

    // ==========================================================================
    // Single Column Tests
    // ==========================================================================

    @Nested
    @DisplayName("calculatePositions with single column")
    class SingleColumnTests {

        @Test
        @DisplayName("positions single note at correct first note offset")
        void singleNoteNoKeySignature() {
            var line = createLine(0);
            var note = createNote(null, null, 0);
            var columns = buildColumns(List.of(note), line);

            calculator.calculatePositions(columns, line);

            // Expected: clefWidth(28) + keySignatureWidth(0) + FIRST_NOTE_OFFSET(11.5 MU = 46px)
            double expectedX = 28.0 + 0 + LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);
            assertEquals(expectedX, columns.get(0).getX(), 0.01);
        }

        @Test
        @DisplayName("accounts for key signature width in first note position")
        void singleNoteWithKeySignature() {
            var line = createLine(3); // 3 sharps
            var note = createNote(null, null, 0);
            var columns = buildColumns(List.of(note), line);

            calculator.calculatePositions(columns, line);

            // Expected: clefWidth(28) + keySignatureWidth(3 * 8 = 24) + FIRST_NOTE_OFFSET(46px)
            double expectedX = 28.0 + (3 * 8.0) + LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);
            assertEquals(expectedX, columns.get(0).getX(), 0.01);
        }
    }

    // ==========================================================================
    // Basic Spacing Tests (No Lyrics)
    // ==========================================================================

    @Nested
    @DisplayName("basic column spacing without lyrics")
    class BasicSpacingTests {

        @Test
        @DisplayName("maintains minimum column gap between notes")
        void minimumColumnGap() {
            var line = createLine(0);
            var note1 = createNote(null, null, 0);
            var note2 = createNote(null, null, 0);
            var columns = buildColumns(List.of(note1, note2), line);

            calculator.calculatePositions(columns, line);

            // Spacing should be based on rightExtent + MIN_COLUMN_GAP + abs(leftExtent)
            double expectedMinSpacing = columns.get(0).getRightExtent()
                + LayoutConstants.px(LayoutConstants.MIN_COLUMN_GAP)
                + Math.abs(columns.get(1).getLeftExtent());

            double actualSpacing = columns.get(1).getX() - columns.get(0).getX();
            assertEquals(expectedMinSpacing, actualSpacing, 0.01);
        }

        @Test
        @DisplayName("handles multiple notes with varying extents")
        void multipleNotesVaryingExtents() {
            var line = createLine(0);
            var note1 = createNote(null, null, 0);      // No dots
            var note2 = createNote(null, null, 1);      // One dot (extends right)
            var note3 = createNote(null, Note.Accidental.SHARP, 0); // Accidental (extends left)
            var columns = buildColumns(List.of(note1, note2, note3), line);

            calculator.calculatePositions(columns, line);

            // Each gap should be >= MIN_COLUMN_GAP
            double minGap = LayoutConstants.px(LayoutConstants.MIN_COLUMN_GAP);

            for (var i = 1; i < columns.size(); i++) {
                var prev = columns.get(i - 1);
                var curr = columns.get(i);
                double spacing = curr.getX() - prev.getX();
                double requiredMin = prev.getRightExtent() + minGap + Math.abs(curr.getLeftExtent());

                assertTrue(
                    spacing >= requiredMin - 0.01,
                    String.format("Column %d spacing %.2f < required %.2f", i, spacing, requiredMin)
                );
            }
        }
    }

    // ==========================================================================
    // Lyric-Driven Spacing Tests
    // ==========================================================================

    @Nested
    @DisplayName("lyric-driven spacing")
    class LyricSpacingTests {

        @Test
        @DisplayName("expands spacing when syllables require it")
        void lyricDrivenExpansion() {
            var line = createLine(0);
            var note1 = createNote("wonderful", null, 0);
            var note2 = createNote("amazing", null, 0);
            var columns = buildColumns(List.of(note1, note2), line);

            calculator.calculatePositions(columns, line);

            // Calculate expected lyric spacing
            double syllable1Width = columns.get(0).getSyllableWidth();
            double syllable2Width = columns.get(1).getSyllableWidth();
            double expectedLyricSpacing = (syllable1Width / 2.0)
                + LayoutConstants.px(LayoutConstants.MIN_SYLLABLE_GAP)
                + (syllable2Width / 2.0);

            double actualSpacing = columns.get(1).getX() - columns.get(0).getX();

            // Actual spacing should be at least the lyric requirement
            assertTrue(
                actualSpacing >= expectedLyricSpacing - 0.01,
                String.format("Expected lyric spacing >= %.2f, got %.2f", expectedLyricSpacing, actualSpacing)
            );
        }

        @Test
        @DisplayName("uses minimum spacing when syllables are short")
        void shortSyllablesUseMinimum() {
            var line = createLine(0);
            var note1 = createNote("a", null, 0);
            var note2 = createNote("I", null, 0);
            var columns = buildColumns(List.of(note1, note2), line);

            calculator.calculatePositions(columns, line);

            // With very short syllables, minimum spacing should dominate
            double minSpacing = columns.get(0).getRightExtent()
                + LayoutConstants.px(LayoutConstants.MIN_COLUMN_GAP)
                + Math.abs(columns.get(1).getLeftExtent());

            double actualSpacing = columns.get(1).getX() - columns.get(0).getX();

            // Spacing should be close to minimum (within lyric tolerance)
            assertTrue(
                actualSpacing >= minSpacing - 0.01,
                String.format("Expected spacing >= %.2f, got %.2f", minSpacing, actualSpacing)
            );
        }

        @Test
        @DisplayName("handles mixed notes with and without syllables")
        void mixedSyllables() {
            var line = createLine(0);
            var note1 = createNote("hello", null, 0);
            var note2 = createNote(null, null, 0);      // No syllable
            var note3 = createNote("world", null, 0);
            var columns = buildColumns(List.of(note1, note2, note3), line);

            calculator.calculatePositions(columns, line);

            // Gap 1-2: first note has syllable, second doesn't - should use minimum spacing
            // Gap 2-3: second note has no syllable, third does - should consider third's syllable

            // All spacings should be valid (>= minimum)
            for (var i = 1; i < columns.size(); i++) {
                assertMinimumSpacing(
                    columns.get(i - 1),
                    columns.get(i),
                    LayoutConstants.px(LayoutConstants.MIN_COLUMN_GAP)
                );
            }
        }
    }

    // ==========================================================================
    // Accidental Clearance Tests
    // ==========================================================================

    @Nested
    @DisplayName("accidental clearance")
    class AccidentalClearanceTests {

        @Test
        @DisplayName("pushes note right when accidental would collide")
        void accidentalClearancePush() {
            var line = createLine(0);
            var note1 = createNote(null, null, 0);
            var note2 = createNote(null, Note.Accidental.SHARP, 0);
            var columns = buildColumns(List.of(note1, note2), line);

            calculator.calculatePositions(columns, line);

            // Verify accidental has clearance
            double prevRightEdge = columns.get(0).getRightEdgeX();
            double currAccidentalLeft = columns.get(1).getLeftEdgeX();
            double clearance = currAccidentalLeft - prevRightEdge;

            assertTrue(
                clearance >= LayoutConstants.px(LayoutConstants.ACCIDENTAL_CLEARANCE) - 0.01,
                String.format("Accidental clearance %.2f < required %.2f",
                    clearance,
                    LayoutConstants.px(LayoutConstants.ACCIDENTAL_CLEARANCE))
            );
        }

        @Test
        @DisplayName("no push needed when accidental has clearance")
        void accidentalHasClearance() {
            var line = createLine(0);
            var note1 = createNote("wonderful", null, 0); // Long syllable creates space
            var note2 = createNote("amazing", Note.Accidental.SHARP, 0);
            var columns = buildColumns(List.of(note1, note2), line);

            calculator.calculatePositions(columns, line);

            // With long syllables, accidental should have plenty of clearance
            double prevRightEdge = columns.get(0).getRightEdgeX();
            double currAccidentalLeft = columns.get(1).getLeftEdgeX();
            double clearance = currAccidentalLeft - prevRightEdge;

            assertTrue(
                clearance >= LayoutConstants.px(LayoutConstants.ACCIDENTAL_CLEARANCE) - 0.01,
                "Accidental should have clearance"
            );
        }
    }

    // ==========================================================================
    // Beam Group Tests
    // ==========================================================================

    @Nested
    @DisplayName("beam group spacing")
    class BeamGroupTests {

        @Test
        @DisplayName("applies tight spacing within beam group without lyrics")
        void beamGroupTightSpacing() {
            var line = createLine(0);
            var note1 = createQuaver(null);
            var note2 = createQuaver(null);
            var note3 = createQuaver(null);
            var note4 = createQuaver(null);

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);

            // Create beam group
            var beamGroup = new BeamGroup();
            beamGroup.addNote(note1);
            beamGroup.addNote(note2);
            beamGroup.addNote(note3);
            beamGroup.addNote(note4);
            line.addBeamGroup(beamGroup);

            var columns = new ArrayList<NoteColumn>();

            for (var i = 0; i < line.noteCount(); i++) {
                columns.add(builder.buildColumn(line.getNote(i), line));
            }

            calculator.calculatePositions(columns, line);

            // Internal gaps should be approximately BEAM_GROUP_MIN_INTERNAL_GAP
            double tightGap = LayoutConstants.px(LayoutConstants.BEAM_GROUP_MIN_INTERNAL_GAP);

            for (var i = 1; i < columns.size(); i++) {
                var prev = columns.get(i - 1);
                var curr = columns.get(i);
                double spacing = curr.getX() - prev.getX();

                // Spacing should be close to tight gap (with some tolerance for extent calculations)
                assertTrue(
                    spacing >= tightGap - 5.0,
                    String.format("Beam group spacing %.2f should be close to tight gap %.2f", spacing, tightGap)
                );
            }
        }

        @Test
        @DisplayName("expands beam group when lyrics require space")
        void beamGroupLyricExpansion() {
            var line = createLine(0);
            var note1 = createQuaver("wonderful");
            var note2 = createQuaver("amazing");
            var note3 = createQuaver("beautiful");
            var note4 = createQuaver("glorious");

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);

            // Create beam group
            var beamGroup = new BeamGroup();
            beamGroup.addNote(note1);
            beamGroup.addNote(note2);
            beamGroup.addNote(note3);
            beamGroup.addNote(note4);
            line.addBeamGroup(beamGroup);

            var columns = new ArrayList<NoteColumn>();

            for (var i = 0; i < line.noteCount(); i++) {
                columns.add(builder.buildColumn(line.getNote(i), line));
            }

            calculator.calculatePositions(columns, line);

            // With wide syllables, spacing should be expanded beyond tight gap
            double tightGap = LayoutConstants.px(LayoutConstants.BEAM_GROUP_MIN_INTERNAL_GAP);

            // At least one gap should be wider than tight spacing
            boolean hasExpandedGap = false;

            for (var i = 1; i < columns.size(); i++) {
                double spacing = columns.get(i).getX() - columns.get(i - 1).getX();

                if (spacing > tightGap + 1.0) { // Allow 1px tolerance
                    hasExpandedGap = true;
                    break;
                }
            }

            assertTrue(hasExpandedGap, "Beam group should expand when lyrics require space");
        }

        @Test
        @DisplayName("handles adjacent beam groups")
        void adjacentBeamGroups() {
            var line = createLine(0);

            // First beam group
            var note1 = createQuaver(null);
            var note2 = createQuaver(null);

            // Gap
            var note3 = createNote(null, null, 0);

            // Second beam group
            var note4 = createQuaver(null);
            var note5 = createQuaver(null);

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

            var columns = new ArrayList<NoteColumn>();

            for (var i = 0; i < line.noteCount(); i++) {
                columns.add(builder.buildColumn(line.getNote(i), line));
            }

            calculator.calculatePositions(columns, line);

            // Verify all columns are positioned
            for (var i = 0; i < columns.size(); i++) {
                assertTrue(
                    columns.get(i).getX() > 0,
                    String.format("Column %d should be positioned", i)
                );
            }

            // Verify monotonic increase
            for (var i = 1; i < columns.size(); i++) {
                assertTrue(
                    columns.get(i).getX() > columns.get(i - 1).getX(),
                    String.format("Column %d should be right of column %d", i, i - 1)
                );
            }
        }
    }

    // ==========================================================================
    // Integration Tests
    // ==========================================================================

    @Nested
    @DisplayName("complex integration scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("handles complex line with all features")
        void complexLineWithAllFeatures() {
            var line = createLine(2); // 2 sharps

            // Mix of notes with various features
            var note1 = createNote("Hel", null, 0);
            var note2 = createNote("lo", Note.Accidental.SHARP, 1); // Accidental + dot
            var note3 = createQuaver("won");
            var note4 = createQuaver("der");
            var note5 = createNote(null, null, 0); // No syllable
            var note6 = createNote("ful", null, 2); // Two dots

            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);
            line.addNote(note4);
            line.addNote(note5);
            line.addNote(note6);

            // Add beam group for notes 3-4
            var beamGroup = new BeamGroup();
            beamGroup.addNote(note3);
            beamGroup.addNote(note4);
            line.addBeamGroup(beamGroup);

            var columns = new ArrayList<NoteColumn>();

            for (var i = 0; i < line.noteCount(); i++) {
                columns.add(builder.buildColumn(line.getNote(i), line));
            }

            calculator.calculatePositions(columns, line);

            // Verify all columns are positioned
            for (var i = 0; i < columns.size(); i++) {
                assertTrue(
                    columns.get(i).getX() > 0,
                    String.format("Column %d should be positioned", i)
                );
            }

            // Verify first note accounts for key signature
            double expectedFirstX = 28.0 + (2 * 8.0) + LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);
            assertEquals(expectedFirstX, columns.get(0).getX(), 0.01);

            // Verify monotonic increase in X positions
            for (var i = 1; i < columns.size(); i++) {
                assertTrue(
                    columns.get(i).getX() > columns.get(i - 1).getX(),
                    String.format("Column %d (%.2f) should be right of column %d (%.2f)",
                        i, columns.get(i).getX(),
                        i - 1, columns.get(i - 1).getX())
                );
            }

            // Verify minimum spacing is maintained everywhere
            double minGap = LayoutConstants.px(LayoutConstants.MIN_COLUMN_GAP);

            for (var i = 1; i < columns.size(); i++) {
                var prev = columns.get(i - 1);
                var curr = columns.get(i);
                double prevRight = prev.getRightEdgeX();
                double currLeft = curr.getLeftEdgeX();
                double gap = currLeft - prevRight;

                assertTrue(
                    gap >= minGap - 0.01,
                    String.format("Gap between columns %d and %d (%.2f) should be >= %.2f",
                        i - 1, i, gap, minGap)
                );
            }
        }
    }
}
