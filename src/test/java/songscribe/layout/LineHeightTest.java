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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.font.DocumentFonts;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.SongLayoutMetricsBuilder;
import songscribe.layout.StaffExtents;

/**
 * Pins {@link LayoutResult#getLineHeightSs()} for the cases called out in
 * {@code plans/dynamic-line-height.md} — empty lines must produce the minimum
 * content-fitted height, and lines with decorations above or notes extending
 * below the staff must grow accordingly.
 */
@SuppressWarnings("DataFlowIssue")
class LineHeightTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 0.001;

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var hyphenWidthSs = ScaleContext.textWidthSs(lyricsFont, "-");
        var spaceWidthSs = ScaleContext.textWidthSs(lyricsFont, " ");
        var metrics = new LyricRenderMetrics(
            lyricsFont, ScaleContext.scaleFont(lyricsFont), hyphenWidthSs, spaceWidthSs);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    private static StaffElement crotchet(int staffPosition) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosition);
        return note;
    }

    private static void addNote(Song song, Line line, StaffElement note) {
        // Insert before the terminal barline.
        song.withoutMutationTracking(
            () -> line.addElement(line.elementCount() - 1, note));
    }

    @Test
    void testEmptyLineZeroReturnsMinimumHeight() {
        var song = new Song();
        var result = require(engine().layout(song.getLine(0), true), "LayoutResult");

        assertThat(result.getLineHeightSs())
            .isCloseTo(SongLayoutMetricsBuilder.MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testEmptyNonLastLineReturnsMinimumHeight() {
        var song = new Song();
        song.addLine(new Line(song));

        var result = require(engine().layout(song.getLine(1), false), "LayoutResult");

        assertThat(result.getLineHeightSs())
            .isCloseTo(SongLayoutMetricsBuilder.MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    /** Below-staff reservation only: the line-height term minus the inter-line margin. */
    private static double belowStaffSsOf(LayoutResult result) {
        return result.getBelowStaffReservationSs() - SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;
    }

    @Test
    void testHighNoteAboveStaffExtendsAboveReservationToNoteheadTop() {
        var song = new Song();
        var line = song.getLine(0);

        addNote(song, line, crotchet(StaffExtents.MIN_STAFF_POSITION_SP));

        var result = require(engine().layout(line, true), "LayoutResult");

        // A note this high above the staff has a stem pointing down, so its topmost
        // point is the notehead top: the note center (sp→ss) plus the notehead's
        // (negative) top offset. The above-staff reservation is the distance from
        // that point down to the staff top (one staff-half below y=0).
        var noteheadTopSs = StaffExtents.spToSs(StaffExtents.MIN_STAFF_POSITION_SP)
            + ElementType.CROTCHET.getNoteheadTopOffsetSs();
        var expectedAboveStaffSs = -noteheadTopSs - StaffExtents.STAFF_HALF_SS;

        // Fixture precondition: this position must actually exercise the extension
        // path, i.e. push the reservation past its floor (otherwise a broken
        // extension that returns the floor would still pass).
        assertThat(expectedAboveStaffSs)
            .describedAs("staff position must exceed the above-staff floor")
            .isGreaterThan(StaffExtents.MIN_ABOVE_STAFF_SS);

        assertThat(result.getAboveStaffSs())
            .describedAs("above-staff reservation tracks the notehead top")
            .isCloseTo(expectedAboveStaffSs, within(TOLERANCE));

        // A high note grows the line only above the staff; the below-staff
        // reservation stays at its floor.
        assertThat(belowStaffSsOf(result))
            .describedAs("a high note must not change the below-staff reservation")
            .isCloseTo(StaffExtents.MIN_BELOW_STAFF_SS, within(TOLERANCE));

        var expectedLineHeightSs = StaffExtents.STAFF_HEIGHT_SS
            + expectedAboveStaffSs
            + StaffExtents.MIN_BELOW_STAFF_SS
            + SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;
        assertThat(result.getLineHeightSs())
            .describedAs("line height reflects the extended above-staff reservation")
            .isCloseTo(expectedLineHeightSs, within(TOLERANCE));
    }

    @Test
    void testLowNoteBelowStaffExtendsBelowReservationToNoteheadBottom() {
        var song = new Song();
        var line = song.getLine(0);

        addNote(song, line, crotchet(StaffExtents.MAX_STAFF_POSITION_SP));

        var result = require(engine().layout(line, true), "LayoutResult");

        // A note this low below the staff has a stem pointing up, so its bottommost
        // point is the notehead bottom: note center plus notehead top offset plus
        // the full notehead height. The below-staff reservation is the distance from
        // that point down past the staff bottom (one staff-half below y=0).
        var noteheadBotSs = StaffExtents.spToSs(StaffExtents.MAX_STAFF_POSITION_SP)
            + ElementType.CROTCHET.getNoteheadTopOffsetSs()
            + ElementType.CROTCHET.getFullElementHeightSs();
        var expectedBelowStaffSs = noteheadBotSs - StaffExtents.STAFF_HALF_SS;

        // Fixture precondition: this position must push the reservation past its floor.
        assertThat(expectedBelowStaffSs)
            .describedAs("staff position must exceed the below-staff floor")
            .isGreaterThan(StaffExtents.MIN_BELOW_STAFF_SS);

        assertThat(belowStaffSsOf(result))
            .describedAs("below-staff reservation tracks the notehead bottom")
            .isCloseTo(expectedBelowStaffSs, within(TOLERANCE));

        // A low note grows the line only below the staff; the above-staff
        // reservation stays at its floor.
        assertThat(result.getAboveStaffSs())
            .describedAs("a low note must not change the above-staff reservation")
            .isCloseTo(StaffExtents.MIN_ABOVE_STAFF_SS, within(TOLERANCE));

        var expectedLineHeightSs = StaffExtents.STAFF_HEIGHT_SS
            + StaffExtents.MIN_ABOVE_STAFF_SS
            + expectedBelowStaffSs
            + SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;
        assertThat(result.getLineHeightSs())
            .describedAs("line height reflects the extended below-staff reservation")
            .isCloseTo(expectedLineHeightSs, within(TOLERANCE));
    }
}
