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
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;

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
    private static final int TOP_STAFF_POSITION = Staff.MIN_STAFF_POSITION_SP;
    // Bottom of the valid staff range — makes botSs + centerYSs exceed the default STAFF_HEIGHT_SS.
    private static final int BOTTOM_STAFF_POSITION = Staff.MAX_STAFF_POSITION_SP;

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
        var centerYSs = Staff.spToSs(note.getStaffPosition());

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
        var centerYSs = Staff.spToSs(TOP_STAFF_POSITION);
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
        var centerYSs = Staff.spToSs(BOTTOM_STAFF_POSITION);
        var expectedBotAbsoluteYSs = bounds.botSs() + centerYSs;

        // Fixture precondition: seeded value must exceed the default bottom (STAFF_HEIGHT_SS)
        // so that it, not the default, is what yGet returns.
        assertThat(expectedBotAbsoluteYSs)
            .describedAs("seeded bot must exceed default STAFF_HEIGHT_SS floor")
            .isGreaterThan(Staff.STAFF_HEIGHT_SS);

        var structural = new StaffExtents(LINE_WIDTH_SS);

        VerticalStackingCalculator.seedAccidentalsIntoStructural(
            List.of(mockColumnFor(note)), structural);

        var sampleXSs = COLUMN_X_SS + bounds.leftSs() + SAMPLE_WIDTH_SS;
        assertThat(structural.yGet(false, sampleXSs, SAMPLE_WIDTH_SS))
            .isEqualTo(expectedBotAbsoluteYSs);
    }

    @Test
    void testCalculateBelowContentUsesDownwardStemExtent() {
        // A downward-stem note whose stem tip is below the staff bottom must produce
        // a non-zero belowContent that is exactly (stemBottomYSs - STAFF_HALF_SS).
        // This pins the formula: belowContentSs = max(0, botContentExtentSs - STAFF_HALF_SS).
        // A sign flip or wrong term in the production expression would cause the value to
        // differ from the independently computed expected value.
        //
        // Stem tip chosen at STEM_BOT_SS (staff-space Y from center) so that:
        //   botContentExtentSs = STEM_BOT_SS  (stem dominates; notehead at center is well above)
        //   belowContentSs     = STEM_BOT_SS - STAFF_HALF_SS
        //
        // belowContentSs is distinct from belowStaff (which floors at MIN_BELOW_STAFF_SS = 4.0).
        final double stemBotSs = 5.0;
        final double expectedBelowContentSs = stemBotSs - Staff.STAFF_HALF_SS;

        var note = ElementType.CROTCHET.newInstance();
        // Staff position 0 keeps centerYSs = 0, so notehead bot << STAFF_HALF_SS;
        // the stem tip at stemBotSs wins in max(stemLayout.bottomYSs(), noteheadBotSs).

        var column = mock(ElementColumn.class);
        when(column.getElement()).thenReturn(note);
        when(column.getXSs()).thenReturn(COLUMN_X_SS);

        var builder = LayoutResult.builder();
        // Provide a StemLayout so seedNoteBounds uses stemLayout.bottomYSs() as botSs.
        // topYSs is above center (negative); bottomYSs is the downward stem tip.
        builder.putStemLayout(note, new LayoutResult.StemLayout(-2.0, stemBotSs, 0.0, 0.0, false));

        var calculator = new VerticalStackingCalculator();
        calculator.calculate(
            List.of(column),
            detachedLine(),
            builder,
            LINE_WIDTH_SS,
            mock(DocumentFontsHolder.class));

        var result = builder.build();
        assertThat(result.getContentBelowStaffSs()).isEqualTo(expectedBelowContentSs);
    }

    // ======================================================================
    // applyDecorationOffsets -- drag preservation of a sloped tuplet bracket
    // ======================================================================

    @Test
    void testApplyDecorationOffsetsShiftsYSsButPreservesDySsForSlopedTuplet() {
        // A dragged (user-offset) sloped tuplet must translate rigidly: ySs shifts by the
        // drag offset, but dySs (the slope itself) is untouched -- the whole bracket moves,
        // it does not re-tilt.
        final double originalYSs = -4.0;
        final double slopeDySs = 1.5;
        final int dragOffsetSs = -2;

        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        var line = detachedLine();
        line.addElement(anchor);
        line.addElement(end);
        var tuplet = new Tuplet(anchor, end, 3);
        line.addRangeElement(tuplet);
        tuplet.setVerticalPositionSs(dragOffsetSs);

        var originalLayout = new LayoutResult.DecorationLayout(
            1.0, originalYSs, slopeDySs, 4.0, 1.0, 0.5, List.of());
        var builder = LayoutResult.builder();
        builder.putDecorationLayout(tuplet, originalLayout);

        new VerticalStackingCalculator().applyDecorationOffsets(builder);

        var shiftedLayout = require(builder.getDecorationLayout(tuplet), "shifted tuplet decoration layout");
        assertThat(shiftedLayout.ySs()).isEqualTo(originalYSs + dragOffsetSs);
        assertThat(shiftedLayout.dySs()).isEqualTo(slopeDySs);
    }
}
