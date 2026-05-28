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

package songscribe.layout.stacking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;
import songscribe.layout.ElementColumn;
import songscribe.layout.NoteGeometry;
import songscribe.layout.StaffExtents;
import songscribe.layout.stacking.VerticalStackingCalculator;

class VerticalStackingCalculatorTest extends UnitTest {

    @BeforeAll
    static void initializeAccidentalWidths() {
        NoteGeometry.initializeAccidentalWidths();
    }

    private static final double LINE_WIDTH_SS = 100.0;
    private static final double COLUMN_X_SS = 10.0;
    // Sampling width inside the accidental's horizontal range — small enough to
    // remain within the accidental's footprint regardless of glyph.
    private static final double SAMPLE_WIDTH_SS = 0.1;
    // Distance to sample past the notehead origin to confirm no extent bleeds there.
    private static final double FAR_RIGHT_OFFSET_SS = 5.0;
    // Top of the valid staff range — worst-case for the centerYSs top-translation bug.
    private static final int TOP_STAFF_POSITION = StaffExtents.MIN_STAFF_POSITION_SP;
    // Bottom of the valid staff range — makes botSs + centerYSs exceed the default STAFF_HEIGHT_SS.
    private static final int BOTTOM_STAFF_POSITION = StaffExtents.MAX_STAFF_POSITION_SP;

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    private static ElementColumn mockColumnFor(StaffElement element) {
        var column = mock(ElementColumn.class);
        when(column.getElement()).thenReturn(element);
        when(column.getXSs()).thenReturn(COLUMN_X_SS);
        return column;
    }

    @Test
    void testSeedAccidentalsDoesNotAffectAreaPastNotehead() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(Accidental.SHARP);

        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(note)), structural);

        // Well past the notehead — no accidental ink there, so the default 0 top stays.
        var farRightXSs = COLUMN_X_SS + FAR_RIGHT_OFFSET_SS;
        assertThat(structural.yGet(true, farRightXSs, SAMPLE_WIDTH_SS)).isEqualTo(0.0);
    }

    @Test
    void testSeedAccidentalsIgnoresColumnsWithoutAccidental() {
        var note = ElementType.CROTCHET.newInstance();
        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(note)), structural);

        // Default top is 0; with no accidental, nothing should reserve above-staff space.
        assertThat(structural.yGet(true, 0, LINE_WIDTH_SS)).isEqualTo(0.0);
    }

    @Test
    void testSeedAccidentalsIgnoresGraceNotes() {
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setAccidental(Accidental.SHARP);
        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(grace)), structural);

        assertThat(structural.yGet(true, 0, LINE_WIDTH_SS)).isEqualTo(0.0);
    }

    @Test
    void testSeedAccidentalsReservesSpaceAtAccidentalXForSharp() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(Accidental.SHARP);

        var bounds = require(NoteGeometry.getAccidentalBoundsSs(note), "sharp bounds");
        var centerYSs = StaffExtents.spToSs(note.getStaffPosition());

        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(note)), structural);

        // Sample just inside the accidental's left edge; top should match the bounds
        // top translated by centerYSs. Bot: the default staff-bottom (STAFF_HEIGHT_SS)
        // exceeds the seeded value for a note at position 0, so the seeded bot is
        // verified with a low-note test below.
        var sampleXSs = COLUMN_X_SS + bounds.leftSs() + SAMPLE_WIDTH_SS;
        assertThat(structural.yGet(true, sampleXSs, SAMPLE_WIDTH_SS))
            .isEqualTo(bounds.topSs() + centerYSs);
        assertThat(structural.yGet(false, sampleXSs, SAMPLE_WIDTH_SS))
            .isGreaterThanOrEqualTo(bounds.botSs() + centerYSs);
    }

    @Test
    void testSeedAccidentalsTranslatesToStaffCoordinatesForHighNote() {
        // Regression test for the centerYSs translation bug: a high-staff-position note
        // had its accidental bounds seeded in note-relative coordinates, so the seeded
        // top was near y=0 instead of being well above the staff. The bug was invisible
        // at staff position 0 (centerYSs = 0); only non-zero positions exposed it.
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(TOP_STAFF_POSITION);
        note.setAccidental(Accidental.FLAT);

        var bounds = require(NoteGeometry.getAccidentalBoundsSs(note), "flat bounds");
        var centerYSs = StaffExtents.spToSs(TOP_STAFF_POSITION);
        var expectedTopAbsoluteYSs = centerYSs + bounds.topSs();

        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(note)), structural);

        var sampleXSs = COLUMN_X_SS + bounds.leftSs() + SAMPLE_WIDTH_SS;
        assertThat(structural.yGet(true, sampleXSs, SAMPLE_WIDTH_SS))
            .isEqualTo(expectedTopAbsoluteYSs);
    }

    @Test
    void testSeedAccidentalsBotTranslatedToStaffCoordinatesForLowNote() {
        // For a note well below the staff, centerYSs > 0 pushes botSs+centerYSs above
        // the default STAFF_HEIGHT_SS floor, so the seeded value wins and we can pin
        // the exact absolute Y. This verifies that centerYSs is added to botSs (same
        // formula as topSs), not omitted.
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(BOTTOM_STAFF_POSITION);
        note.setAccidental(Accidental.SHARP);

        var bounds = require(NoteGeometry.getAccidentalBoundsSs(note), "sharp bounds");
        var centerYSs = StaffExtents.spToSs(BOTTOM_STAFF_POSITION);
        var expectedBotAbsoluteYSs = bounds.botSs() + centerYSs;

        // Fixture precondition: seeded value must exceed the default bottom (STAFF_HEIGHT_SS)
        // so that it, not the default, is what yGet returns.
        assertThat(expectedBotAbsoluteYSs)
            .describedAs("seeded bot must exceed default STAFF_HEIGHT_SS floor")
            .isGreaterThan(StaffExtents.STAFF_HEIGHT_SS);

        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(note)), structural);

        var sampleXSs = COLUMN_X_SS + bounds.leftSs() + SAMPLE_WIDTH_SS;
        assertThat(structural.yGet(false, sampleXSs, SAMPLE_WIDTH_SS))
            .isEqualTo(expectedBotAbsoluteYSs);
    }
}
