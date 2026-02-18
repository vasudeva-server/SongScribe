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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.NoteType;
import songscribe.music.Note;
import songscribe.music.Tempo;
import songscribe.ui.layout.Bounds;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.TempoAttachment;
import songscribe.ui.layout.Tie;

@DisplayName("LayoutResult")
class LayoutResultTest {

    private LayoutResult.Builder builder;
    private Note note1;
    private Note note2;
    private Note note3;
    private NoteColumn column1;
    private NoteColumn column2;
    private NoteColumn column3;
    private TestElement element1;
    private TestElement element2;
    private Bounds bounds1;
    private Bounds bounds2;

    @BeforeEach
    void setUp() {
        builder = LayoutResult.builder();

        note1 = NoteType.CROTCHET.newInstance();
        note2 = NoteType.CROTCHET.newInstance();
        note3 = NoteType.CROTCHET.newInstance();

        column1 = createColumn(note1, 100.0);
        column2 = createColumn(note2, 200.0);
        column3 = createColumn(note3, 300.0);

        element1 = new TestElement(50, 30);
        element2 = new TestElement(60, 40);

        bounds1 = Bounds.contentOnly(new Rectangle2D.Double(10, 20, 50, 30));
        bounds2 = Bounds.contentOnly(new Rectangle2D.Double(100, 120, 60, 40));
    }

    // ==========================================================================
    // Test Helper Classes
    // ==========================================================================

    /**
     * Test implementation of LineElement.
     */
    private static class TestElement extends LineElement {

        private final double width;
        private final double height;

        public TestElement(double width, double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public double getContentWidth() {
            return width;
        }

        @Override
        public double getContentHeight() {
            return height;
        }
    }

    /**
     * Creates a test note column with a given position.
     */
    private NoteColumn createColumn(Note note, double x) {
        var column = new NoteColumn(
                note,
                Collections.emptyList(),
                -10.0,
                15.0,
                0,
                20,
                null,
                0,
                null
        );
        column.setX(x);
        return column;
    }

    // ==========================================================================
    // Builder Tests
    // ==========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("creates empty LayoutResult")
        void createEmptyResult() {
            var result = builder.build();

            assertNotNull(result);
            assertEquals(0, result.getNoteColumnCount());
            assertEquals(0, result.getElementCount());
            assertEquals(0, result.getLineHeight(), 0.01);
            assertEquals(0, result.getStaffTopY(), 0.01);
            assertEquals(0, result.getStaffBottomY(), 0.01);
            assertEquals(0, result.getLyricBaselineY(), 0.01);
        }

        @Test
        @DisplayName("creates LayoutResult with all fields set")
        void createFullResult() {
            var result = builder
                    .putNoteColumn(note1, column1)
                    .putNoteColumn(note2, column2)
                    .putElementBounds(element1, bounds1)
                    .putElementBounds(element2, bounds2)
                    .setLineHeight(150.0)
                    .setStaffGeometry(50.0, 100.0)
                    .setLyricBaselineY(120.0)
                    .build();

            assertEquals(2, result.getNoteColumnCount());
            assertEquals(2, result.getElementCount());
            assertEquals(150.0, result.getLineHeight(), 0.01);
            assertEquals(50.0, result.getStaffTopY(), 0.01);
            assertEquals(100.0, result.getStaffBottomY(), 0.01);
            assertEquals(120.0, result.getLyricBaselineY(), 0.01);
        }

        @Test
        @DisplayName("allows chaining")
        void allowsChaining() {
            var result = builder
                    .putNoteColumn(note1, column1)
                    .setLineHeight(150.0)
                    .build();

            assertNotNull(result);
            assertEquals(1, result.getNoteColumnCount());
        }
    }

    // ==========================================================================
    // Note Column Access Tests
    // ==========================================================================

    @Nested
    @DisplayName("Note Column Access")
    class NoteColumnAccessTests {

        private LayoutResult result;

        @BeforeEach
        void setUp() {
            result = builder
                    .putNoteColumn(note1, column1)
                    .putNoteColumn(note2, column2)
                    .build();
        }

        @Test
        @DisplayName("getNoteColumn returns correct column")
        void getNoteColumn() {
            var column = result.getNoteColumn(note1);

            assertNotNull(column);
            assertEquals(100.0, column.getX(), 0.01);
        }

        @Test
        @DisplayName("getNoteColumn returns null for missing note")
        void getNoteColumnMissing() {
            var column = result.getNoteColumn(note3);

            assertNull(column);
        }

        @Test
        @DisplayName("getNoteX returns correct X position")
        void getNoteX() {
            assertEquals(100.0, result.getNoteX(note1), 0.01);
            assertEquals(200.0, result.getNoteX(note2), 0.01);
        }

        @Test
        @DisplayName("getNoteX returns 0 for missing note")
        void getNoteXMissing() {
            assertEquals(0, result.getNoteX(note3), 0.01);
        }

        @Test
        @DisplayName("hasNote returns true for present note")
        void hasNotePresent() {
            assertTrue(result.hasNote(note1));
            assertTrue(result.hasNote(note2));
        }

        @Test
        @DisplayName("hasNote returns false for missing note")
        void hasNoteMissing() {
            assertFalse(result.hasNote(note3));
        }

        @Test
        @DisplayName("getNoteColumns returns unmodifiable map")
        void getNoteColumns() {
            var columns = result.getNoteColumns();

            assertEquals(2, columns.size());
            assertTrue(columns.containsKey(note1));
            assertTrue(columns.containsKey(note2));
        }
    }

    // ==========================================================================
    // Element Bounds Access Tests
    // ==========================================================================

    @Nested
    @DisplayName("Element Bounds Access")
    class ElementBoundsAccessTests {

        private LayoutResult result;

        @BeforeEach
        void setUp() {
            result = builder
                    .putElementBounds(element1, bounds1)
                    .putElementBounds(element2, bounds2)
                    .build();
        }

        @Test
        @DisplayName("getElementBounds returns correct bounds")
        void getElementBounds() {
            var bounds = result.getElementBounds(element1);

            assertNotNull(bounds);
            assertEquals(10, bounds.getLeft(), 0.01);
            assertEquals(20, bounds.getTop(), 0.01);
        }

        @Test
        @DisplayName("getElementBounds returns null for missing element")
        void getElementBoundsMissing() {
            var missingElement = new TestElement(10, 10);
            var bounds = result.getElementBounds(missingElement);

            assertNull(bounds);
        }

        @Test
        @DisplayName("getElementPosition returns correct position")
        void getElementPosition() {
            var position = result.getElementPosition(element1);

            assertNotNull(position);
            assertEquals(10, position.getX(), 0.01);
            assertEquals(20, position.getY(), 0.01);
        }

        @Test
        @DisplayName("getElementPosition returns null for missing element")
        void getElementPositionMissing() {
            var missingElement = new TestElement(10, 10);
            var position = result.getElementPosition(missingElement);

            assertNull(position);
        }

        @Test
        @DisplayName("hasElement returns true for present element")
        void hasElementPresent() {
            assertTrue(result.hasElement(element1));
            assertTrue(result.hasElement(element2));
        }

        @Test
        @DisplayName("hasElement returns false for missing element")
        void hasElementMissing() {
            var missingElement = new TestElement(10, 10);

            assertFalse(result.hasElement(missingElement));
        }

        @Test
        @DisplayName("getElementBounds (map) returns unmodifiable map")
        void getElementBoundsMap() {
            var boundsMap = result.getElementBounds();

            assertEquals(2, boundsMap.size());
            assertTrue(boundsMap.containsKey(element1));
            assertTrue(boundsMap.containsKey(element2));
        }
    }

    // ==========================================================================
    // Staff Geometry Tests
    // ==========================================================================

    @Nested
    @DisplayName("Staff Geometry")
    class StaffGeometryTests {

        @Test
        @DisplayName("getStaffTopY returns correct value")
        void getStaffTopY() {
            var result = builder
                    .setStaffGeometry(50.0, 100.0)
                    .build();

            assertEquals(50.0, result.getStaffTopY(), 0.01);
        }

        @Test
        @DisplayName("getStaffBottomY returns correct value")
        void getStaffBottomY() {
            var result = builder
                    .setStaffGeometry(50.0, 100.0)
                    .build();

            assertEquals(100.0, result.getStaffBottomY(), 0.01);
        }

        @Test
        @DisplayName("getLineHeight returns correct value")
        void getLineHeight() {
            var result = builder
                    .setLineHeight(150.0)
                    .build();

            assertEquals(150.0, result.getLineHeight(), 0.01);
        }

        @Test
        @DisplayName("getLineWidth returns rightmost column edge")
        void getLineWidth() {
            var result = builder
                    .putNoteColumn(note1, column1)
                    .putNoteColumn(note2, column2)
                    .putNoteColumn(note3, column3)
                    .build();

            double expectedWidth = column3.getRightEdgeX();

            assertEquals(expectedWidth, result.getLineWidth(), 0.01);
        }

        @Test
        @DisplayName("getLineWidth returns 0 for empty result")
        void getLineWidthEmpty() {
            var result = builder.build();

            assertEquals(0, result.getLineWidth(), 0.01);
        }
    }

    // ==========================================================================
    // Lyric Tests
    // ==========================================================================

    @Nested
    @DisplayName("Lyrics")
    class LyricTests {

        @Test
        @DisplayName("getLyricBaselineY returns correct value when lyrics present")
        void getLyricBaselineY() {
            var result = builder
                    .setLyricBaselineY(120.0)
                    .build();

            assertEquals(120.0, result.getLyricBaselineY(), 0.01);
        }

        @Test
        @DisplayName("hasLyrics returns true when lyrics present")
        void hasLyricsPresent() {
            var result = builder
                    .setLyricBaselineY(120.0)
                    .build();

            assertTrue(result.hasLyrics());
        }

        @Test
        @DisplayName("hasLyrics returns false when no lyrics")
        void hasLyricsAbsent() {
            var result = builder
                    .setLyricBaselineY(0)
                    .build();

            assertFalse(result.hasLyrics());
        }

        @Test
        @DisplayName("hasLyrics returns false for default value")
        void hasLyricsDefault() {
            var result = builder.build();

            assertFalse(result.hasLyrics());
        }
    }

    // ==========================================================================
    // Compatibility Method Tests
    // ==========================================================================

    @Nested
    @DisplayName("Compatibility Methods")
    class CompatibilityMethodTests {

        private LayoutResult result;

        @BeforeEach
        void setUp() {
            result = builder
                    .putElementBounds(element1, bounds1)
                    .putElementBounds(element2, bounds2)
                    .build();
        }

        @Test
        @DisplayName("getBounds works with LineElement")
        void getBoundsWithLineElement() {
            var bounds = result.getBounds(element1);

            assertNotNull(bounds);
            assertEquals(10, bounds.getLeft(), 0.01);
            assertEquals(20, bounds.getTop(), 0.01);
        }

        @Test
        @DisplayName("getBounds returns null for non-LineElement")
        void getBoundsWithNonLineElement() {
            var bounds = result.getBounds("not a line element");

            assertNull(bounds);
        }

        @Test
        @DisplayName("getBounds returns null for missing element")
        void getBoundsMissing() {
            var missingElement = new TestElement(10, 10);
            var bounds = result.getBounds(missingElement);

            assertNull(bounds);
        }

        @Test
        @DisplayName("contains works with LineElement")
        void containsWithLineElement() {
            assertTrue(result.contains(element1));
            assertTrue(result.contains(element2));
        }

        @Test
        @DisplayName("contains returns false for non-LineElement")
        void containsWithNonLineElement() {
            assertFalse(result.contains("not a line element"));
        }

        @Test
        @DisplayName("contains returns false for missing element")
        void containsMissing() {
            var missingElement = new TestElement(10, 10);

            assertFalse(result.contains(missingElement));
        }
    }

    // ==========================================================================
    // Search Method Tests
    // ==========================================================================

    @Nested
    @DisplayName("Search Methods")
    class SearchMethodTests {

        private Note parentNote;
        private TempoAttachment tempoAttachment;
        private Bounds tempoBounds;
        private LayoutResult result;

        @BeforeEach
        void setUp() {
            parentNote = NoteType.CROTCHET.newInstance();

            var tempo = new Tempo();
            tempo.setVisibleTempo(120);

            tempoAttachment = new TempoAttachment(parentNote, tempo);
            tempoBounds = Bounds.contentOnly(new Rectangle2D.Double(50, 10, 60, 20));

            result = builder
                    .putElementBounds(tempoAttachment, tempoBounds)
                    .putElementBounds(element1, bounds1)
                    .build();
        }

        @Test
        @DisplayName("findAttachmentBounds finds correct attachment")
        void findAttachmentBounds() {
            var bounds = result.findAttachmentBounds(parentNote, TempoAttachment.class);

            assertNotNull(bounds);
            assertEquals(50, bounds.getLeft(), 0.01);
            assertEquals(10, bounds.getTop(), 0.01);
        }

        @Test
        @DisplayName("findAttachmentBounds returns null for missing attachment")
        void findAttachmentBoundsMissing() {
            var otherNote = NoteType.CROTCHET.newInstance();
            var bounds = result.findAttachmentBounds(otherNote, TempoAttachment.class);

            assertNull(bounds);
        }

        @Test
        @DisplayName("findAttachment finds correct attachment object")
        void findAttachment() {
            var attachment = result.findAttachment(parentNote, TempoAttachment.class);

            assertNotNull(attachment);
            assertEquals(parentNote, attachment.getParentNote());
        }

        @Test
        @DisplayName("findAttachment returns null for missing attachment")
        void findAttachmentMissing() {
            var otherNote = NoteType.CROTCHET.newInstance();
            var attachment = result.findAttachment(otherNote, TempoAttachment.class);

            assertNull(attachment);
        }

        @Test
        @DisplayName("findRangeElementBounds finds correct range element")
        void findRangeElementBounds() {
            var anchorNote = NoteType.CROTCHET.newInstance();
            var endNote = NoteType.CROTCHET.newInstance();
            var tie = new Tie(anchorNote, endNote);
            var tieBounds = Bounds.contentOnly(new Rectangle2D.Double(100, 50, 80, 8));

            var resultWithTie = LayoutResult.builder()
                    .putElementBounds(tie, tieBounds)
                    .build();

            var bounds = resultWithTie.findRangeElementBounds(anchorNote, endNote, Tie.class);

            assertNotNull(bounds);
            assertEquals(100, bounds.getLeft(), 0.01);
            assertEquals(50, bounds.getTop(), 0.01);
        }

        @Test
        @DisplayName("findRangeElementBounds returns null for missing range element")
        void findRangeElementBoundsMissing() {
            var anchorNote = NoteType.CROTCHET.newInstance();
            var endNote = NoteType.CROTCHET.newInstance();

            var bounds = result.findRangeElementBounds(anchorNote, endNote, Tie.class);

            assertNull(bounds);
        }

        @Test
        @DisplayName("findRangeElementBounds returns null for wrong notes")
        void findRangeElementBoundsWrongNotes() {
            var anchorNote = NoteType.CROTCHET.newInstance();
            var endNote = NoteType.CROTCHET.newInstance();
            var tie = new Tie(anchorNote, endNote);
            var tieBounds = Bounds.contentOnly(new Rectangle2D.Double(100, 50, 80, 8));

            var resultWithTie = LayoutResult.builder()
                    .putElementBounds(tie, tieBounds)
                    .build();

            var wrongNote = NoteType.CROTCHET.newInstance();
            var bounds = resultWithTie.findRangeElementBounds(wrongNote, endNote, Tie.class);

            assertNull(bounds);
        }
    }

    // ==========================================================================
    // Statistics Tests
    // ==========================================================================

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("getNoteColumnCount returns correct count")
        void getNoteColumnCount() {
            var result = builder
                    .putNoteColumn(note1, column1)
                    .putNoteColumn(note2, column2)
                    .putNoteColumn(note3, column3)
                    .build();

            assertEquals(3, result.getNoteColumnCount());
        }

        @Test
        @DisplayName("getElementCount returns correct count")
        void getElementCount() {
            var result = builder
                    .putElementBounds(element1, bounds1)
                    .putElementBounds(element2, bounds2)
                    .build();

            assertEquals(2, result.getElementCount());
        }

        @Test
        @DisplayName("counts are 0 for empty result")
        void emptyResult() {
            var result = builder.build();

            assertEquals(0, result.getNoteColumnCount());
            assertEquals(0, result.getElementCount());
        }
    }

    // ==========================================================================
    // Insertion Note Positioning Tests
    // ==========================================================================

    @Nested
    @DisplayName("Insertion Note Positioning")
    class InsertionNotePositioningTests {

        private songscribe.music.Line line;
        private LayoutResult result;

        @BeforeEach
        void setUp() {
            line = new songscribe.music.Line();
            line.addNote(note1);
            line.addNote(note2);
            line.addNote(note3);

            result = builder
                    .putNoteColumn(note1, column1)
                    .putNoteColumn(note2, column2)
                    .putNoteColumn(note3, column3)
                    .build();
        }

        @Nested
        @DisplayName("findInsertionIndex")
        class FindInsertionIndexTests {

            @Test
            @DisplayName("returns 0 for empty line")
            void emptyLine() {
                var emptyLine = new songscribe.music.Line();
                var emptyResult = LayoutResult.builder().build();

                assertEquals(0, emptyResult.findInsertionIndex(50, emptyLine));
            }

            @Test
            @DisplayName("returns 0 when mouse is before first note")
            void beforeFirstNote() {
                assertEquals(0, result.findInsertionIndex(50, line));
            }

            @Test
            @DisplayName("returns 0 when mouse is exactly at first note")
            void atFirstNote() {
                assertEquals(0, result.findInsertionIndex(100, line));
            }

            @Test
            @DisplayName("returns noteCount when mouse is after last note")
            void afterLastNote() {
                assertEquals(3, result.findInsertionIndex(400, line));
            }

            @Test
            @DisplayName("returns correct index when mouse is between first and second note")
            void betweenFirstAndSecond() {
                assertEquals(1, result.findInsertionIndex(150, line));
            }

            @Test
            @DisplayName("returns correct index when mouse is between second and third note")
            void betweenSecondAndThird() {
                assertEquals(2, result.findInsertionIndex(250, line));
            }

            @Test
            @DisplayName("handles single note line - before note")
            void singleNoteBeforeNote() {
                var singleLine = new songscribe.music.Line();
                singleLine.addNote(note1);

                var singleResult = LayoutResult.builder()
                        .putNoteColumn(note1, column1)
                        .build();

                assertEquals(0, singleResult.findInsertionIndex(50, singleLine));
            }

            @Test
            @DisplayName("handles single note line - after note")
            void singleNoteAfterNote() {
                var singleLine = new songscribe.music.Line();
                singleLine.addNote(note1);

                var singleResult = LayoutResult.builder()
                        .putNoteColumn(note1, column1)
                        .build();

                assertEquals(1, singleResult.findInsertionIndex(150, singleLine));
            }

            @Test
            @DisplayName("handles missing column gracefully")
            void missingColumn() {
                var lineWithMissingColumn = new songscribe.music.Line();
                lineWithMissingColumn.addNote(note1);

                var resultWithoutColumn = LayoutResult.builder().build();

                assertEquals(0, resultWithoutColumn.findInsertionIndex(150, lineWithMissingColumn));
            }

            @Test
            @DisplayName("returns 0 when all columns are null")
            void allColumnsNull() {
                var lineWithNotes = new songscribe.music.Line();
                lineWithNotes.addNote(note1);
                lineWithNotes.addNote(note2);

                var resultWithoutColumns = LayoutResult.builder().build();

                assertEquals(0, resultWithoutColumns.findInsertionIndex(150, lineWithNotes));
            }
        }

        @Nested
        @DisplayName("calculateInsertionX")
        class CalculateInsertionXTests {

            private Note insertionNote;

            @BeforeEach
            void setUp() {
                // Create a standard insertion note (Crotchet with no accidentals)
                insertionNote = NoteType.CROTCHET.newInstance();
            }

            @Test
            @DisplayName("returns first note offset for empty line")
            void emptyLine() {
                var emptyLine = new songscribe.music.Line();
                var emptyResult = LayoutResult.builder().build();

                var expectedX = LayoutConstants.calculateFirstNoteX(emptyLine.getKeyAccidentalCount());

                assertEquals(expectedX, emptyResult.calculateInsertionX(0, 0, insertionNote, emptyLine), 0.01);
            }

            @Test
            @DisplayName("returns position 15px left of first note when mouse is before note head")
            void beforeFirstNote() {
                var mouseX = 50.0;  // Well before first note at 100
                var expectedX = 100.0 - 15.0;

                assertEquals(expectedX, result.calculateInsertionX(0, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("snaps to first note position when mouse is over note head")
            void overFirstNote() {
                var mouseX = 100.0;  // Exactly on first note
                var expectedX = 100.0;

                assertEquals(expectedX, result.calculateInsertionX(0, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("uses proper spacing calculation for position after last note")
            void afterLastNote() {
                // Mouse is well after last note
                var mouseX = 400.0;

                // Build temporary column to use HorizontalSpacingCalculator
                var insertionColumn = new NoteColumn(
                        insertionNote,
                        java.util.Collections.emptyList(),
                        NoteColumnBuilder.calculateLeftExtent(insertionNote),
                        NoteColumnBuilder.calculateRightExtent(insertionNote),
                        0,
                        0,
                        null,
                        0,
                        null
                );

                var expectedX = HorizontalSpacingCalculator.calculateNextColumnX(column3, insertionColumn);

                assertEquals(expectedX, result.calculateInsertionX(3, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("uses proper spacing calculation for index > noteCount")
            void beyondNoteCount() {
                // Mouse is well after last note
                var mouseX = 500.0;

                // Same as afterLastNote - uses last column
                var insertionColumn = new NoteColumn(
                        insertionNote,
                        java.util.Collections.emptyList(),
                        NoteColumnBuilder.calculateLeftExtent(insertionNote),
                        NoteColumnBuilder.calculateRightExtent(insertionNote),
                        0,
                        0,
                        null,
                        0,
                        null
                );

                var expectedX = HorizontalSpacingCalculator.calculateNextColumnX(column3, insertionColumn);

                assertEquals(expectedX, result.calculateInsertionX(5, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("uses midpoint when inserting between notes")
            void betweenFirstAndSecond() {
                // Mouse is between notes (not over note head)
                var mouseX = 150.0;

                // Between notes: use midpoint
                var expectedX = (100.0 + 200.0) / 2.0;

                assertEquals(expectedX, result.calculateInsertionX(1, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("uses midpoint for second to third position")
            void betweenSecondAndThird() {
                // Mouse is between notes (not over note head)
                var mouseX = 250.0;

                // Between notes: use midpoint
                var expectedX = (200.0 + 300.0) / 2.0;

                assertEquals(expectedX, result.calculateInsertionX(2, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("snaps to note when mouse is over note head")
            void snapToNoteWhenOverNoteHead() {
                // Mouse is directly over second note at X=200
                var mouseX = 200.0;
                var expectedX = 200.0;

                assertEquals(expectedX, result.calculateInsertionX(1, mouseX, insertionNote, line), 0.01);
            }

            @Test
            @DisplayName("handles single note line - before note")
            void singleNoteBeforeNote() {
                var singleLine = new songscribe.music.Line();
                singleLine.addNote(note1);

                var singleResult = LayoutResult.builder()
                        .putNoteColumn(note1, column1)
                        .build();

                var mouseX = 50.0;  // Before note
                var expectedX = 100.0 - 15.0;

                assertEquals(expectedX, singleResult.calculateInsertionX(0, mouseX, insertionNote, singleLine), 0.01);
            }

            @Test
            @DisplayName("handles single note line - after note")
            void singleNoteAfterNote() {
                var singleLine = new songscribe.music.Line();
                singleLine.addNote(note1);

                var singleResult = LayoutResult.builder()
                        .putNoteColumn(note1, column1)
                        .build();

                var mouseX = 150.0;  // After note

                // After last note: use HorizontalSpacingCalculator
                var insertionColumn = new NoteColumn(
                        insertionNote,
                        java.util.Collections.emptyList(),
                        NoteColumnBuilder.calculateLeftExtent(insertionNote),
                        NoteColumnBuilder.calculateRightExtent(insertionNote),
                        0,
                        0,
                        null,
                        0,
                        null
                );

                var expectedX = HorizontalSpacingCalculator.calculateNextColumnX(column1, insertionColumn);

                assertEquals(expectedX, singleResult.calculateInsertionX(1, mouseX, insertionNote, singleLine), 0.01);
            }

            @Test
            @DisplayName("handles missing column gracefully - returns first note offset")
            void missingColumn() {
                var lineWithMissingColumn = new songscribe.music.Line();
                lineWithMissingColumn.addNote(note1);
                lineWithMissingColumn.addNote(note2);

                var resultWithoutColumn = LayoutResult.builder().build();

                var mouseX = 150.0;
                var expectedX = LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);

                assertEquals(expectedX, resultWithoutColumn.calculateInsertionX(1, mouseX, insertionNote, lineWithMissingColumn), 0.01);
            }

            @Test
            @DisplayName("accounts for insertion note with accidental")
            void withAccidental() {
                // Create a note with a sharp (wider left extent)
                var noteWithSharp = NoteType.CROTCHET.newInstance();
                noteWithSharp.setAccidental(Note.Accidental.SHARP);

                var mouseX = 400.0;  // After last note

                // Build temporary column
                var insertionColumn = new NoteColumn(
                        noteWithSharp,
                        java.util.Collections.emptyList(),
                        NoteColumnBuilder.calculateLeftExtent(noteWithSharp),
                        NoteColumnBuilder.calculateRightExtent(noteWithSharp),
                        0,
                        0,
                        null,
                        0,
                        null
                );

                var expectedX = HorizontalSpacingCalculator.calculateNextColumnX(column3, insertionColumn);

                assertEquals(expectedX, result.calculateInsertionX(3, mouseX, noteWithSharp, line), 0.01);
            }

            @Test
            @DisplayName("handles columns with different spacings")
            void differentSpacings() {
                var wideColumn1 = createColumn(note1, 100.0);
                var wideColumn2 = createColumn(note2, 300.0);
                var wideColumn3 = createColumn(note3, 600.0);

                var wideResult = LayoutResult.builder()
                        .putNoteColumn(note1, wideColumn1)
                        .putNoteColumn(note2, wideColumn2)
                        .putNoteColumn(note3, wideColumn3)
                        .build();

                // Mouse positions between notes
                var mouseX1 = 200.0;
                var mouseX2 = 450.0;

                // Between notes: use midpoint
                var expected1 = (100.0 + 300.0) / 2.0;
                var expected2 = (300.0 + 600.0) / 2.0;

                assertEquals(expected1, wideResult.calculateInsertionX(1, mouseX1, insertionNote, line), 0.01);
                assertEquals(expected2, wideResult.calculateInsertionX(2, mouseX2, insertionNote, line), 0.01);
            }
        }
    }

    // ==========================================================================
    // Edge Cases
    // ==========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("result is immutable")
        void resultIsImmutable() {
            var result = builder
                    .putNoteColumn(note1, column1)
                    .putElementBounds(element1, bounds1)
                    .build();

            var columns = result.getNoteColumns();
            var elements = result.getElementBounds();

            assertEquals(1, columns.size());
            assertEquals(1, elements.size());
        }

        @Test
        @DisplayName("toString returns meaningful representation")
        void toStringRepresentation() {
            var result = builder
                    .putNoteColumn(note1, column1)
                    .putNoteColumn(note2, column2)
                    .putElementBounds(element1, bounds1)
                    .setLineHeight(150.0)
                    .setStaffGeometry(50.0, 100.0)
                    .setLyricBaselineY(120.0)
                    .build();

            var str = result.toString();

            assertNotNull(str);
            assertTrue(str.contains("columns=2"));
            assertTrue(str.contains("elements=1"));
            assertTrue(str.contains("height=150"));
            assertTrue(str.contains("staff=[50"));
            assertTrue(str.contains("100"));
            assertTrue(str.contains("lyrics=120"));
        }

        @Test
        @DisplayName("handles multiple columns at same position")
        void multipleColumnsAtSamePosition() {
            var column1a = createColumn(note1, 100.0);
            var column2a = createColumn(note2, 100.0);

            var result = builder
                    .putNoteColumn(note1, column1a)
                    .putNoteColumn(note2, column2a)
                    .build();

            assertEquals(100.0, result.getNoteX(note1), 0.01);
            assertEquals(100.0, result.getNoteX(note2), 0.01);
        }

        @Test
        @DisplayName("handles replacing note column")
        void replaceNoteColumn() {
            var column1a = createColumn(note1, 100.0);
            var column1b = createColumn(note1, 150.0);

            var result = builder
                    .putNoteColumn(note1, column1a)
                    .putNoteColumn(note1, column1b)
                    .build();

            assertEquals(150.0, result.getNoteX(note1), 0.01);
            assertEquals(1, result.getNoteColumnCount());
        }

        @Test
        @DisplayName("handles replacing element bounds")
        void replaceElementBounds() {
            var bounds1a = Bounds.contentOnly(new Rectangle2D.Double(10, 20, 50, 30));
            var bounds1b = Bounds.contentOnly(new Rectangle2D.Double(100, 120, 60, 40));

            var result = builder
                    .putElementBounds(element1, bounds1a)
                    .putElementBounds(element1, bounds1b)
                    .build();

            var bounds = result.getElementBounds(element1);

            assertNotNull(bounds);
            assertEquals(100, bounds.getLeft(), 0.01);
            assertEquals(1, result.getElementCount());
        }

        @Test
        @DisplayName("getLineWidth handles negative extents")
        void getLineWidthWithNegativeExtents() {
            var columnWithNegativeExtent = new NoteColumn(
                    note1,
                    Collections.emptyList(),
                    -25.0,
                    10.0,
                    0,
                    20,
                    null,
                    0,
                    null
            );
            columnWithNegativeExtent.setX(100.0);

            var result = builder
                    .putNoteColumn(note1, columnWithNegativeExtent)
                    .build();

            assertEquals(110.0, result.getLineWidth(), 0.01);
        }
    }
}
