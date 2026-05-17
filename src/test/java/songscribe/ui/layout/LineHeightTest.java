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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

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

    /**
     * Minimum line height: the staff itself plus the inter-line margin.
     * No above- or below-staff extents, no lyrics.
     */
    private static final double MIN_LINE_HEIGHT_SS =
        StaffExtents.STAFF_HEIGHT_SS
        + StaffExtents.MIN_ABOVE_STAFF_SS
        + StaffExtents.MIN_BELOW_STAFF_SS
        + SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var scale = ScaleContext.getInstance();
        var hyphenWidthSs = scale.textWidthSs(lyricsFont, "-");
        var spaceWidthSs = scale.textWidthSs(lyricsFont, " ");
        var metrics = new LyricRenderMetrics(
            lyricsFont, scale.scaleFont(lyricsFont), hyphenWidthSs, spaceWidthSs);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultsFromPrefs());
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
            .isCloseTo(MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testEmptyNonLastLineReturnsMinimumHeight() {
        var song = new Song();
        song.addLine(new Line(song));

        var result = require(engine().layout(song.getLine(1), false), "LayoutResult");

        assertThat(result.getLineHeightSs())
            .isCloseTo(MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    @Test
    void testHighNoteAboveStaffIncreasesLineHeight() {
        var song = new Song();
        var line = song.getLine(0);

        addNote(song, line, crotchet(StaffExtents.MIN_STAFF_POSITION_SP));

        var result = require(engine().layout(line, true), "LayoutResult");

        assertThat(result.getLineHeightSs())
            .describedAs("high note above staff must grow the line height")
            .isGreaterThanOrEqualTo(MIN_LINE_HEIGHT_SS);
    }

    @Test
    void testLowNoteBelowStaffIncreasesLineHeight() {
        var song = new Song();
        var line = song.getLine(0);

        // Regression guard for the below-staff term added in step 2 of the plan.
        addNote(song, line, crotchet(StaffExtents.MAX_STAFF_POSITION_SP));

        var result = require(engine().layout(line, true), "LayoutResult");

        assertThat(result.getLineHeightSs())
            .describedAs("low note below staff must grow the line height")
            .isGreaterThanOrEqualTo(MIN_LINE_HEIGHT_SS);
    }
}
