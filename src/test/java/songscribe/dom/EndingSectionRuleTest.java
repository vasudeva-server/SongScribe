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

package songscribe.dom;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.undo.MutationReplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.BatchReplay.NO_FONT_CHANGES;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The section rule of {@link Ending}, run when the outermost modification bracket closes: an
 * ending whose split an edit leaves closing no open repeat section is removed and reported, and
 * the removal is undone with the edit that caused it. The rule reads the song across lines, so an
 * opening repeat on an earlier line keeps an ending, and deleting the line that holds one removes
 * it.
 *
 * <p>Every fixture ends in the ending under test, on the song's last line, and opens with a
 * closing repeat: a song's start opens a section by itself, so an ending is only broken by an edit
 * when a closing bar stands before the repeat that opens its section.
 */
class EndingSectionRuleTest extends UnitTest {

    /**
     * The one-line fixture: a closing repeat, the opening repeat the ending's split closes to, two
     * notes, and the ending — anchor, split, one note, end.
     */
    private static final String ONE_LINE = ":| |: 3 3 :| 3 3";
    private static final int OPENER_INDEX = 1;
    private static final int ONE_LINE_ANCHOR_INDEX = 3;

    /** The last line of a fixture whose opening repeat is on an earlier line: the ending's line. */
    private static final String ENDING_LINE = "3 3 :| 3 3";
    private static final int ENDING_LINE_ANCHOR_INDEX = 1;

    /** Between an ending's anchor and its end, in a line where nothing else stands in between. */
    private static final int END_OFFSET_FROM_ANCHOR = 3;

    private static final int FIRST_LINE = 0;

    /** In the three-line fixture, the line that holds the opening repeat. */
    private static final int OPENER_LINE = 1;

    /** In the two-line fixture, the ending's line. */
    private static final int SECOND_LINE = 1;

    /** A song with one ending, on its last line. */
    private record Fixture(Song song, Ending ending) {

        Line endingLine() {
            return song.getLine(song.lineCount() - 1);
        }
    }

    private static Fixture fixtureOf(int anchorIndex, String... lines) {
        var song = compactSong(Key.NO_ACCIDENTALS, lines);
        var line = song.getLine(song.lineCount() - 1);
        var range = new ElementRange(anchorIndex, anchorIndex + END_OFFSET_FROM_ANCHOR);
        var ending = addWithoutTracking(song, () -> endingOver(line, range));

        return new Fixture(song, ending);
    }

    /** The report's place for a removed ending: the one line it stood on, in the song as the edit left it. */
    private static LineRange endingOn(int lineIndex) {
        return new LineRange(lineIndex, lineIndex);
    }

    /**
     * One edit to a fixture, and where it leaves the ending's report.
     *
     * @param description  the edit, as the test's display name
     * @param anchorIndex  the ending's anchor index in the fixture's last line
     * @param lines        the fixture's lines in compact notation
     * @param edit         makes the edit, inside a modification bracket
     * @param reportedAt   the line the report places the removed ending on, in the song as
     *                     the edit leaves it; {@code null} when the ending is kept and nothing is reported
     */
    private record SectionCase(
        String description, int anchorIndex, String[] lines, Consumer<Song> edit,
        @Nullable LineRange reportedAt
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("sectionCases")
    void testAnEditThatLeavesTheSplitNoOpenSectionRemovesAndReportsTheEnding(SectionCase testCase) {
        var fixture = fixtureOf(testCase.anchorIndex(), testCase.lines());
        var song = fixture.song();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(song));

            var endingsAfter = fixture.endingLine().spanRegistry().findEndings();

            if (testCase.reportedAt() != null) {
                assertThat(endingsAfter).isEmpty();
                assertThat(reports.reports()).singleElement().isEqualTo(List.of(new ReportedChange(
                    Strings.REPORT_ENDINGS_REMOVED,
                    Strings.get(Strings.REPORT_REASON_ENDING_NO_OPEN_SECTION),
                    testCase.reportedAt())));
            } else {
                assertThat(endingsAfter).singleElement().isSameAs(fixture.ending());
                assertThat(reports.reports()).isEmpty();
            }
        });
    }

    static Stream<SectionCase> sectionCases() {
        return Stream.of(
            new SectionCase("deleting the opening repeat removes the ending",
                ONE_LINE_ANCHOR_INDEX, new String[] {ONE_LINE},
                song -> song.getLine(FIRST_LINE).removeRange(ElementRange.single(OPENER_INDEX)),
                endingOn(FIRST_LINE)),
            new SectionCase("inserting a closing repeat after the opening repeat removes the ending",
                ONE_LINE_ANCHOR_INDEX, new String[] {ONE_LINE},
                song -> song.getLine(FIRST_LINE).addElement(OPENER_INDEX + 1, repeatRight()),
                endingOn(FIRST_LINE)),
            new SectionCase("inserting a double barline after the opening repeat removes the ending",
                ONE_LINE_ANCHOR_INDEX, new String[] {ONE_LINE},
                song -> song.getLine(FIRST_LINE).addElement(OPENER_INDEX + 1, doubleBarline()),
                endingOn(FIRST_LINE)),
            new SectionCase("replacing the opening repeat with a single barline removes the ending",
                ONE_LINE_ANCHOR_INDEX, new String[] {ONE_LINE},
                replaceOpener(),
                endingOn(FIRST_LINE)),
            new SectionCase("deleting the line that holds the opening repeat removes the ending",
                ENDING_LINE_ANCHOR_INDEX, new String[] {":| 3", "|: 3", ENDING_LINE},
                song -> song.removeLine(OPENER_LINE),
                endingOn(SECOND_LINE)),
            new SectionCase("an opening repeat on an earlier line keeps the ending through an edit to its line",
                ENDING_LINE_ANCHOR_INDEX, new String[] {":| |: 3", ENDING_LINE},
                song -> song.getLine(SECOND_LINE).addElement(0, crotchet()),
                null)
        );
    }

    private static Consumer<Song> replaceOpener() {
        return song -> song.getLine(FIRST_LINE).setElement(OPENER_INDEX, singleBarline());
    }

    @Test
    void testUndoRestoresTheEndingAndRedoRemovesItAgain() {
        var fixture = fixtureOf(ONE_LINE_ANCHOR_INDEX, ONE_LINE);
        var song = fixture.song();
        var line = fixture.endingLine();
        var range = fixture.ending().range();
        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> replaceOpener().accept(song));
            var batch = recorder.batches().getFirst();

            assertThat(line.spanRegistry().findEndings())
                .as("the fixture reaches the case only if the edit removes the ending")
                .isEmpty();

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batch);

            assertThat(line.getElement(OPENER_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT);
            assertThat(line.spanRegistry().findEndings()).singleElement()
                .satisfies(restored -> assertThat(restored.range()).isEqualTo(range));

            MutationReplayer.redoBatch(song, NO_FONT_CHANGES, batch);

            assertThat(line.spanRegistry().findEndings()).isEmpty();
        } finally {
            recorder.dispose();
        }
    }
}
