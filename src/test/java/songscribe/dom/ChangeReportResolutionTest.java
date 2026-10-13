/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where {@link ChangeReport.Builder#build()} says a change was made: the line indices of the song
 * as it stands when the report is built, not as it stood when the change was recorded.
 */
class ChangeReportResolutionTest extends UnitTest {

    private static final String NO_NOTES = "";

    private static final int FIRST_LINE = 0;

    private static final int SECOND_LINE = 1;

    @Test
    void testALineInsertedInFrontOfARecordedLineMovesTheReportedIndex() {
        var song = songOfEmptyLines();
        var recordedLine = song.getLine(FIRST_LINE);
        var builder = new ChangeReport.Builder(song);

        builder.record(changeOn(ChangeLines.on(recordedLine)));
        song.withoutMutationTracking(() -> song.addLine(FIRST_LINE, new Line(song)));

        var reported = builder.build().changes();

        assertThat(reported).singleElement()
            .extracting(ReportedChange::where)
            .isEqualTo(new LineRange(SECOND_LINE, SECOND_LINE));
    }

    @Test
    void testALineRemovedInFrontOfARecordedLineMovesTheReportedIndexDown() {
        var song = songOfEmptyLines();
        var recordedLine = song.getLine(SECOND_LINE);
        var builder = new ChangeReport.Builder(song);

        builder.record(changeOn(ChangeLines.on(recordedLine)));
        song.withoutMutationTracking(() -> song.removeLine(FIRST_LINE));

        var reported = builder.build().changes();

        assertThat(reported).singleElement()
            .extracting(ReportedChange::where)
            .isEqualTo(new LineRange(FIRST_LINE, FIRST_LINE));
    }

    @Test
    void testAnEntryWithNoPlaceHasNoLineRange() {
        var song = songOfEmptyLines();
        var builder = new ChangeReport.Builder(song);

        builder.record(changeOn(null));

        var reported = builder.build().changes();

        assertThat(reported).singleElement()
            .extracting(ReportedChange::where)
            .isNull();
    }

    @Test
    void testATieAcrossALineBreakReportsBothLines() {
        var song = songOfEmptyLines();
        var builder = new ChangeReport.Builder(song);

        builder.record(changeOn(new ChangeLines(song.getLine(FIRST_LINE), song.getLine(SECOND_LINE))));

        var reported = builder.build().changes();

        assertThat(reported).singleElement()
            .extracting(ReportedChange::where)
            .isEqualTo(new LineRange(FIRST_LINE, SECOND_LINE));
    }

    private static Song songOfEmptyLines() {
        return SongFactory.compactSong(Key.NO_ACCIDENTALS, NO_NOTES, NO_NOTES);
    }

    private static RecordedChange changeOn(@Nullable ChangeLines where) {
        return new RecordedChange(Strings.REPORT_TIES_REMOVED, Strings.get(Strings.REPORT_REASON_TIE_NOT_HELD), where);
    }
}
