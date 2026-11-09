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
import songscribe.engraving.StaffPosition;
import songscribe.undo.MutationReplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static songscribe.dom.BatchReplay.NO_FONT_CHANGES;
import static songscribe.dom.LineEdits.choose;
import static songscribe.dom.LineEdits.move;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * {@link TieRule#apply}: when the outermost modification bracket closes, each tie on a line the
 * edit touched, or on the line before one, stays only while {@link Tie#judge} finds it to hold.
 * Each tie removed is reported with its reason and its lines, and no beam stays over both notes of
 * a tie that stays.
 */
class TieRuleTest extends UnitTest {

    /** The staff position every tied note starts at. */
    private static final int TIED = 3;

    /** Another staff position, at a different pitch. */
    private static final int OTHER = 4;

    private static final int ANCHOR = 0;
    private static final int END = 1;
    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;

    /** The place of a removal on the one line of {@link #TIED_PAIR}. */
    private static final LineRange ON_FIRST_LINE = new LineRange(FIRST_LINE, FIRST_LINE);

    /** The place of a removal of the tie that crosses from the first line into the second. */
    private static final LineRange ACROSS_THE_LINE_BREAK = new LineRange(FIRST_LINE, SECOND_LINE);

    /** A line with a tie from its first note to its second, and a note after them. */
    private static final String TIED_PAIR = TIED + " ~ " + TIED + " " + OTHER;

    /** A line of two quavers, which a beam can join. */
    private static final String QUAVER_PAIR = "Q Q";

    /**
     * One edit to the line {@link #TIED_PAIR} builds, and what it leaves of the tie.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     * @param endIndex    the index the tie from the first element ends on afterwards, or
     *                    {@code null} when the first element is no longer tied
     * @param reasonKey   the {@link Strings} key of the reason the rule reports the removal under
     *                    {@link ChangeHeading#TIES_REMOVED}, or {@code null} when the edit
     *                    reports no tie
     */
    private record EditCase(
        String description, Consumer<Line> edit, @Nullable Integer endIndex, @Nullable String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("editCases")
    void testAnEditKeepsATieOnTheLineItEditsOnlyWhileTheTieHolds(EditCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(line));

            var first = (NoteElement) line.getElement(ANCHOR);
            var expectedEnd = testCase.endIndex();

            assertTieReport(reports, testCase.reasonKey(), ON_FIRST_LINE);

            if (expectedEnd == null) {
                assertThat(first.isTiedToNext()).as("the tie is removed, not only broken").isFalse();
                return;
            }

            var tie = first.tie();

            assertThat(tie).isNotNull();
            assertThat(tie.end()).isSameAs(line.getElement(expectedEnd));
        });
    }

    /**
     * Asserts that the reports hold one tie removal under {@code reasonKey}, placed on
     * {@code where}, or none when {@code reasonKey} is {@code null}.
     */
    private static void assertTieReport(ReportRecorder reports, @Nullable String reasonKey, @Nullable LineRange where) {
        var tieEntries = reports.reports().stream()
            .flatMap(List::stream)
            .filter(change -> change.heading().equals(ChangeHeading.TIES_REMOVED))
            .toList();

        if (reasonKey == null) {
            assertThat(tieEntries).as("the edit reports no tie").isEmpty();
            return;
        }

        assertThat(tieEntries).singleElement()
            .extracting(ReportedChange::description, ReportedChange::where)
            .containsExactly(Strings.get(reasonKey), where);
    }

    static Stream<EditCase> editCases() {
        return Stream.of(
            new EditCase("moving one note to another pitch removes it",
                line -> move(line, END, OTHER), null, Strings.REPORT_REASON_TIE_NOT_HELD),
            new EditCase("moving both notes to one new pitch keeps it",
                line -> {
                    move(line, ANCHOR, OTHER);
                    move(line, END, OTHER);
                },
                END, null),
            new EditCase("a same-pitch note inserted between them keeps it, ending on that note",
                line -> line.addElement(END, note(TIED)), END, null),
            new EditCase("a note at another pitch inserted between them removes it",
                line -> line.addElement(END, note(OTHER)), null, Strings.REPORT_REASON_TIE_NOT_HELD),
            new EditCase("replacing the end with a rest removes it, with no note following",
                line -> line.setElement(END, crotchetRest()),
                null, Strings.REPORT_REASON_TIE_NO_FOLLOWING_NOTE)
        );
    }

    @Test
    void testATiedNoteInsertedInsideABeamKeepsItsTieAndTheBeamGoes() {
        var song = compactSong(Key.NO_ACCIDENTALS, QUAVER_PAIR);
        var line = song.getLine(FIRST_LINE);
        var pitch = ((NoteElement) line.getElement(END)).getStaffPosition().value();
        var tied = quaver(pitch, Stem.AUTO);

        addWithoutTracking(song, () -> beamOver(line, new ElementRange(ANCHOR, END)));

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> {
                line.addElement(END, tied);
                line.addTie(tied);
            });

            assertThat(tied.isTiedTo((NoteElement) line.getElement(END + 1)))
                .as("the tie stays on its notes").isTrue();
            assertThat(line.spanRegistry().findBeamAt(END)).as("no beam holds both tied notes").isNull();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries)
                    .extracting(ReportedChange::heading, ReportedChange::description)
                    .containsExactly(tuple(
                        ChangeHeading.BEAMS_REMOVED,
                        Strings.get(Strings.REPORT_REASON_BEAM_TIED_NOTES))));
        });
    }

    /**
     * An edit that touches only the line after the first line, and breaks the tie from the first
     * line's last note into the line after it.
     *
     * @param description the edit, as the test's display name
     * @param lines       the song's lines in compact notation; the first line ends in {@code ~}, so
     *                    its last note is tied to the second line's first
     * @param edit        makes the edit, inside a modification bracket
     * @param reasonKey   the {@link Strings} key of the reason the rule reports the removal under
     *                    {@link ChangeHeading#TIES_REMOVED}
     */
    private record CrossLineCase(String description, String[] lines, Consumer<Song> edit, String reasonKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossLineCases")
    void testAnEditOnTheLineAfterAnAnchorRemovesTheTieItBreaks(CrossLineCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());
        var firstLine = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) firstLine.getElement(firstLine.elementCount() - 1);

        assertThat(anchor.isTiedTo((NoteElement) song.getLine(SECOND_LINE).getElement(ANCHOR)))
            .as("the fixture reaches the case only if the tie crosses into the second line")
            .isTrue();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(song));

            assertThat(anchor.isTiedToNext()).isFalse();
            assertTieReport(reports, testCase.reasonKey(), ACROSS_THE_LINE_BREAK);
        });
    }

    static Stream<CrossLineCase> crossLineCases() {
        return Stream.of(
            new CrossLineCase("moving the next line's first note to another pitch removes it",
                new String[] {TIED + " ~", TIED + " " + OTHER},
                song -> move(song.getLine(SECOND_LINE), ANCHOR, OTHER), Strings.REPORT_REASON_TIE_NOT_HELD),
            new CrossLineCase("inserting a line after it, whose first note is at another pitch, removes it",
                new String[] {TIED + " ~", "" + TIED},
                song -> {
                    var inserted = new Line(song);
                    song.withoutMutationTracking(() -> inserted.addElement(note(OTHER)));
                    song.addLine(SECOND_LINE, inserted);
                },
                Strings.REPORT_REASON_TIE_NOT_HELD)
        );
    }
}
