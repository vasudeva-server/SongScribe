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
import songscribe.engraving.Staff;

/**
 * Pins a line's per-line height for the cases called out in
 * {@code plans/dynamic-line-height.md} — empty lines must produce the minimum
 * content-fitted height, and lines with decorations above or notes extending
 * below the staff must grow accordingly.
 * <p>
 * A line's height covers only its own content: the measured
 * {@link LayoutResult#lineHeightSs} is the staff plus its true content extents plus its own
 * lyrics band, while {@link LayoutResult#paintLineHeightSs} additionally applies the
 * ledger-line floors. Neither carries an inter-line gap — that belongs to the layout manager
 * that stacks the lines.
 */
@SuppressWarnings("DataFlowIssue")
class LineHeightTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 0.001;

    private static final Font LYRICS_FONT = new Font("Dialog", Font.PLAIN, 12);

    /**
     * No test here asserts a verse baseline, so the staff-to-lyrics gap is irrelevant and
     * left at zero; the lyrics band height does not depend on it.
     */
    private static final LyricRenderMetrics LYRIC_METRICS = new LyricRenderMetrics(
        LYRICS_FONT,
        ScaleContext.scaleFont(LYRICS_FONT),
        ScaleContext.textWidthSs(LYRICS_FONT, "-").value(),
        ScaleContext.textWidthSs(LYRICS_FONT, " ").value(),
        0.0);

    /**
     * The lyrics band every line reserves, derived from the font rather than a constant —
     * a verse row is the measured ink height of the lyric extent reference.
     */
    private static final double LYRICS_BAND_HEIGHT_SS =
        LineSpacing.LYRICS_ROW_MARGIN_SS
        + LineSpacing.MIN_RESERVED_VERSE_ROWS * LyricRenderMetrics.fontHeightSs(LYRICS_FONT);

    private static LayoutEngine engine() {
        return new LayoutEngine(LYRIC_METRICS, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    /** The measured height a line of the given content extents must report. */
    private static double expectedLineHeightSs(double aboveStaffSs, double belowStaffSs) {
        return Staff.STAFF_HEIGHT_SS + aboveStaffSs + belowStaffSs + LYRICS_BAND_HEIGHT_SS;
    }

    /**
     * Asserts the ledger-line floors, not the measured content, govern this line's painted
     * height — the precondition every "minimum height" assertion below depends on.
     */
    private static void assertFloorsGovern(LayoutResult result) {
        assertThat(result.aboveMidlineSs())
            .describedAs("measured content above the midline must sit inside its floor")
            .isLessThanOrEqualTo(LineSpacing.MIN_ABOVE_MIDLINE_SS);
        assertThat(result.belowMidlineSs(LYRIC_METRICS))
            .describedAs("the lyrics band must fit inside the below-midline floor")
            .isLessThanOrEqualTo(LineSpacing.MIN_BELOW_MIDLINE_SS);
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

        assertThat(result.getContentAboveStaffSs()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.getContentBelowStaffSs()).isCloseTo(0.0, within(TOLERANCE));

        assertFloorsGovern(result);
        assertThat(result.paintLineHeightSs(LYRIC_METRICS))
            .isCloseTo(LineSpacing.MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testEmptyNonLastLineReturnsMinimumHeight() {
        var song = new Song();
        song.addLine(new Line(song));

        var result = require(engine().layout(song.getLine(1), false), "LayoutResult");

        assertThat(result.getContentAboveStaffSs()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.getContentBelowStaffSs()).isCloseTo(0.0, within(TOLERANCE));

        assertFloorsGovern(result);
        assertThat(result.paintLineHeightSs(LYRIC_METRICS))
            .isCloseTo(LineSpacing.MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testHighNoteAboveStaffExtendsAboveReservationToNoteheadTop() {
        var song = new Song();
        var line = song.getLine(0);

        addNote(song, line, crotchet(Staff.MIN_STAFF_POSITION_SP));

        var result = require(engine().layout(line, true), "LayoutResult");

        // A note this high above the staff has a stem pointing down, so its topmost
        // point is the notehead top: the note center (sp→ss) plus the notehead's
        // (negative) top offset. The above-staff reservation is the distance from
        // that point down to the staff top (one staff-half below y=0).
        var noteheadTopSs = Staff.spToSs(Staff.MIN_STAFF_POSITION_SP)
            + ElementType.CROTCHET.getNoteheadTopOffsetSs();
        var expectedAboveStaffSs = -noteheadTopSs - Staff.STAFF_HALF_SS;

        // Fixture precondition: this position must actually exercise the extension
        // path, i.e. push the reservation past its floor (otherwise a broken
        // extension that returns the floor would still pass).
        assertThat(expectedAboveStaffSs)
            .describedAs("staff position must exceed the above-staff floor")
            .isGreaterThan(Staff.MIN_ABOVE_STAFF_SS);

        assertThat(result.getContentAboveStaffSs())
            .describedAs("above-staff reservation tracks the notehead top")
            .isCloseTo(expectedAboveStaffSs, within(TOLERANCE));

        // A high note grows the line only above the staff. Its stem points down but stops at
        // the staff center, so nothing reaches past the staff bottom and the below-staff
        // content extent — now unfloored — stays at zero.
        assertThat(result.getContentBelowStaffSs())
            .describedAs("a high note must not extend content below the staff")
            .isCloseTo(0.0, within(TOLERANCE));

        assertThat(result.lineHeightSs(LYRIC_METRICS))
            .describedAs("line height reflects the extended above-staff content")
            .isCloseTo(expectedLineHeightSs(expectedAboveStaffSs, 0.0), within(TOLERANCE));
    }

    @Test
    void testLowNoteBelowStaffExtendsBelowReservationToNoteheadBottom() {
        var song = new Song();
        var line = song.getLine(0);

        addNote(song, line, crotchet(Staff.MAX_STAFF_POSITION_SP));

        var result = require(engine().layout(line, true), "LayoutResult");

        // A note this low below the staff has a stem pointing up, so its bottommost
        // point is the notehead bottom: note center plus notehead top offset plus
        // the full notehead height. The below-staff reservation is the distance from
        // that point down past the staff bottom (one staff-half below y=0).
        var noteheadBotSs = Staff.spToSs(Staff.MAX_STAFF_POSITION_SP)
            + ElementType.CROTCHET.getNoteheadTopOffsetSs()
            + ElementType.CROTCHET.getFullElementHeightSs();
        var expectedBelowStaffSs = noteheadBotSs - Staff.STAFF_HALF_SS;

        // Fixture precondition: this position must push the reservation past its floor.
        assertThat(expectedBelowStaffSs)
            .describedAs("staff position must exceed the below-staff floor")
            .isGreaterThan(Staff.MIN_BELOW_STAFF_SS);

        assertThat(result.getContentBelowStaffSs())
            .describedAs("below-staff content tracks the notehead bottom")
            .isCloseTo(expectedBelowStaffSs, within(TOLERANCE));

        // A low note grows the line only below the staff. Its stem points up but stops at the
        // staff center, so nothing reaches past the staff top and the above-staff content
        // extent — now unfloored — stays at zero.
        assertThat(result.getContentAboveStaffSs())
            .describedAs("a low note must not extend content above the staff")
            .isCloseTo(0.0, within(TOLERANCE));

        assertThat(result.lineHeightSs(LYRIC_METRICS))
            .describedAs("line height reflects the extended below-staff content")
            .isCloseTo(expectedLineHeightSs(0.0, expectedBelowStaffSs), within(TOLERANCE));
    }
}
