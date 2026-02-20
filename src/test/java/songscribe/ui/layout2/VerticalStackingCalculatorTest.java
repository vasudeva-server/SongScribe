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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.*;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.music.ArticulationType;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.FermataAttachment;

/**
 * Tests for {@link VerticalStackingCalculator}.
 */
public class VerticalStackingCalculatorTest {

    private VerticalStackingCalculator calculator;
    private Line line;

    @BeforeEach
    public void setUp() {
        calculator = new VerticalStackingCalculator();
        line = new Line();
    }

    /**
     * Test empty column (no articulations or attachments).
     * Should just calculate based on note bounds.
     */
    @Test
    public void testEmptyColumn() {
        var note = createNote();
        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        assertNotNull(result);
        assertEquals(1, result.getElementPositions().size());
        assertTrue(result.getMaxHeightAboveStaffPx() <= 0);
        assertTrue(result.getLyricsBaselineYPx() > 0);
        assertTrue(result.getLineHeightPx() > 0);
    }

    /**
     * Test single articulation positioning.
     * Articulation should be positioned above note with ARTICULATION_MARGIN.
     */
    @Test
    public void testSingleArticulation() {
        var note = createNote();
        var articulation = new Articulation(ArticulationType.STACCATO);
        note.addArticulation(articulation);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        assertNotNull(result);

        var articulationPos = result.getElementPosition(note, articulation);
        assertNotNull(articulationPos);

        // Articulation should be above the note (negative Y)
        assertTrue(articulationPos.getY() < column.getStemTopSs());

        // Should have proper margin
        var expectedMargin = LayoutConstants.toPixels(LayoutConstants.ARTICULATION_MARGIN_SS);
        var actualMargin = column.getStemTopSs() - articulationPos.getY() - articulation.getContentHeight();
        assertTrue(actualMargin >= expectedMargin - 0.1);
    }

    /**
     * Test multiple articulations on same note.
     * Should stack vertically with proper spacing.
     */
    @Test
    public void testMultipleArticulations() {
        var note = createNote();
        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);

        note.addArticulation(staccato);
        note.addArticulation(accent);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        var staccatoPos = result.getElementPosition(note, staccato);
        var accentPos = result.getElementPosition(note, accent);

        assertNotNull(staccatoPos);
        assertNotNull(accentPos);

        // Both should be above the note
        assertTrue(staccatoPos.getY() < column.getStemTopSs());
        assertTrue(accentPos.getY() < column.getStemTopSs());

        // Should be stacked (one above the other)
        assertNotEquals(staccatoPos.getY(), accentPos.getY());
    }

    /**
     * Test fermata only (using new attachment hierarchy).
     * Fermata should be positioned with FERMATA_MARGIN.
     */
    @Test
    public void testFermataOnly() {
        var note = createNote();
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        var fermataPos = result.getElementPosition(note, fermata);
        assertNotNull(fermataPos);

        // Fermata should be above the note
        assertTrue(fermataPos.getY() < column.getStemTopSs());
    }

    /**
     * Test legacy fermata flag.
     * Should reserve space for fermata even without attachment object.
     */
    @Test
    public void testLegacyFermataFlag() {
        var note = createNote();
        note.setFermata(true);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        assertNotNull(result);

        // Should have space reserved above note
        assertTrue(result.getMaxHeightAboveStaffPx() < 0);
    }

    /**
     * Test legacy trill flag.
     * Should reserve space for trill.
     */
    @Test
    public void testLegacyTrillFlag() {
        var note = createNote();
        note.setTrill(true);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        assertNotNull(result);

        // Should have space reserved above note
        assertTrue(result.getMaxHeightAboveStaffPx() < 0);
    }

    /**
     * Test complex stacking: note + articulation + fermata.
     * Should stack in correct order with proper margins.
     */
    @Test
    public void testComplexStacking() {
        var note = createNote();
        var articulation = new Articulation(ArticulationType.STACCATO);
        var fermata = new FermataAttachment(note);

        note.addArticulation(articulation);
        note.addAttachment(fermata);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        var articulationPos = result.getElementPosition(note, articulation);
        var fermataPos = result.getElementPosition(note, fermata);

        assertNotNull(articulationPos);
        assertNotNull(fermataPos);

        // Articulation should be above note
        assertTrue(articulationPos.getY() < column.getStemTopSs());

        // Fermata should be above articulation
        assertTrue(fermataPos.getY() < articulationPos.getY());

        // Should maintain proper stacking order
        var noteTop = column.getStemTopSs();
        var artTop = articulationPos.getY();
        var fermataTop = fermataPos.getY();

        assertTrue(fermataTop < artTop);
        assertTrue(artTop < noteTop);
    }

    /**
     * Test line height calculation.
     * Should include staff height + elements above + potential lyrics.
     */
    @Test
    public void testLineHeightCalculation() {
        var note = createNote();
        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        // Line height should be at least the staff height
        assertTrue(result.getLineHeightPx() >= LayoutConstants.toPixels(LayoutConstants.STAFF_HEIGHT_SS));

        // Should include space above staff
        var expectedHeight = LayoutConstants.toPixels(LayoutConstants.STAFF_HEIGHT_SS) + Math.abs(result.getMaxHeightAboveStaffPx());
        assertTrue(result.getLineHeightPx() >= expectedHeight);
    }

    /**
     * Test lyrics baseline calculation.
     * Should be LYRICS_BASELINE_OFFSET below lowest note.
     */
    @Test
    public void testLyricsBaselineCalculation() {
        var note = createNote();
        var column = createColumn(note, 100.0, "test");
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        // Lyrics baseline should be below the staff
        assertTrue(result.getLyricsBaselineYPx() > LayoutConstants.toPixels(LayoutConstants.STAFF_HEIGHT_SS));

        // Should be approximately LYRICS_BASELINE_OFFSET below note
        var expectedBaseline = column.getStemBottomSs() + LayoutConstants.toPixels(LayoutConstants.LYRICS_BASELINE_OFFSET_SS);
        assertEquals(expectedBaseline, result.getLyricsBaselineYPx(), 1.0);
    }

    /**
     * Test multiple columns.
     * Should process all columns and track maximum height.
     */
    @Test
    public void testMultipleColumns() {
        var note1 = createNote();
        var note2 = createNote();

        var articulation1 = new Articulation(ArticulationType.STACCATO);
        var fermata2 = new FermataAttachment(note2);

        note1.addArticulation(articulation1);
        note2.addAttachment(fermata2);

        var column1 = createColumn(note1, 100.0);
        var column2 = createColumn(note2, 200.0);
        var columns = List.of(column1, column2);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        assertNotNull(result);
        assertEquals(2, result.getElementPositions().size());

        var art1Pos = result.getElementPosition(note1, articulation1);
        var fermata2Pos = result.getElementPosition(note2, fermata2);

        assertNotNull(art1Pos);
        assertNotNull(fermata2Pos);
    }

    /**
     * Test collision avoidance.
     * Elements should never overlap in accumulated bounding area.
     */
    @Test
    public void testCollisionAvoidance() {
        var note = createNote();

        // Add two articulations that would overlap without collision detection
        var art1 = new Articulation(ArticulationType.STACCATO);
        var art2 = new Articulation(ArticulationType.ACCENT);

        note.addArticulation(art1);
        note.addArticulation(art2);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        var pos1 = result.getElementPosition(note, art1);
        var pos2 = result.getElementPosition(note, art2);

        assertNotNull(pos1);
        assertNotNull(pos2);

        // Elements should not overlap
        var bottom1 = pos1.getY() + art1.getContentHeight();
        var top2 = pos2.getY();

        // Either art1 is above art2, or art2 is above art1
        assertTrue(bottom1 <= top2 || pos2.getY() + art2.getContentHeight() <= pos1.getY());
    }

    /**
     * Test mixed legacy and new hierarchy.
     * Should handle both systems during transition.
     */
    @Test
    public void testMixedLegacyAndNew() {
        var note = createNote();

        // Legacy trill flag
        note.setTrill(true);

        // New articulation hierarchy
        var articulation = new Articulation(ArticulationType.STACCATO);
        note.addArticulation(articulation);

        var column = createColumn(note, 100.0);
        var columns = List.of(column);

        var result = calculator.calculateVerticalPositions(columns, line, null);

        assertNotNull(result);

        // Should have space for both trill and articulation
        assertTrue(result.getMaxHeightAboveStaffPx() < 0);

        var artPos = result.getElementPosition(note, articulation);
        assertNotNull(artPos);
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    /**
     * Creates a test note with default values.
     */
    private Note createNote() {
        var note = NoteType.CROTCHET.newInstance();
        note.setStaffPosition(2); // Middle of staff
        return note;
    }

    /**
     * Creates a test column with positioned note.
     *
     * @param note The note
     * @param x    X position
     * @return NoteColumn with X position set
     */
    private NoteColumn createColumn(Note note, double x) {
        return createColumn(note, x, null);
    }

    /**
     * Creates a test column with positioned note and optional syllable.
     *
     * @param note     The note
     * @param x        X position
     * @param syllable Optional syllable text
     * @return NoteColumn with X position set
     */
    private NoteColumn createColumn(Note note, double x, String syllable) {
        // Calculate extents (simplified)
        var leftExtentSs = -4.0;  // Half note head width
        var rightExtentSs = 4.0;

        // Calculate stem positions (simplified)
        // Staff is 32px tall, note at middle would be around y=16
        // Stem goes up for this note, so stemTop is above note, stemBottom is at note
        var stemTopSs = 10.0;  // Top of stem (above the note head)
        var stemBottomSs = 40.0; // Bottom of stem (at note head, below staff)

        var column = new NoteColumn(
            note,
            List.of(),
            leftExtentSs,
            rightExtentSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllable != null ? 30.0 : 0.0,
            null
        );

        // Set X position (simulating HorizontalSpacingCalculator)
        column.setXSs(x);

        return column;
    }

    /**
     * Asserts that an element's Y position is within tolerance of expected value.
     *
     * @param element     The element
     * @param expectedY   Expected Y position
     * @param tolerance   Tolerance in pixels
     * @param elementName Name for error messages
     */
    private void assertElementY(Point2D position, double expectedY, double tolerance, String elementName) {
        assertNotNull(position, elementName + " position should not be null");
        assertEquals(expectedY, position.getY(), tolerance,
            elementName + " Y position should be within tolerance");
    }
}
