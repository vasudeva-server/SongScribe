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
 * The tie rule on {@link NoteElement}: when the outermost modification bracket closes, a tie
 * stays only while its anchor and the element its walk reaches are notes at one staff position
 * sounding one alteration. It judges the lines the edit touched and the line before each, and its
 * removals are undone with the edit that caused them.
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
     *                    {@link Strings#REPORT_TIES_REMOVED}, or {@code null} when the edit
     *                    reports no tie
     */
    private record EditCase(
        String description, Consumer<Line> edit, @Nullable Integer endIndex, @Nullable String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("editCases")
    void testATieStaysOnlyWhileItsNotesShareAPitch(EditCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(line));

            var first = (NoteElement) line.getElement(ANCHOR);
            var expectedEnd = testCase.endIndex();

            assertTieReport(reports, testCase.reasonKey());

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
     * Asserts that the reports hold one tie removal under {@code reasonKey}, or none when it is
     * {@code null}.
     */
    private static void assertTieReport(ReportRecorder reports, @Nullable String reasonKey) {
        var tieEntries = reports.reports().stream()
            .flatMap(List::stream)
            .filter(change -> change.headingKey().equals(Strings.REPORT_TIES_REMOVED))
            .toList();

        if (reasonKey == null) {
            assertThat(tieEntries).as("the edit reports no tie").isEmpty();
            return;
        }

        assertThat(tieEntries).singleElement()
            .extracting(ReportedChange::description)
            .isEqualTo(Strings.get(reasonKey));
    }

    static Stream<EditCase> editCases() {
        return Stream.of(
            new EditCase("a sharp on the anchor removes it",
                line -> choose(line, ANCHOR, Accidental.SHARP), null, Strings.REPORT_REASON_TIE_NOT_HELD),
            new EditCase("a sharp on the end removes it",
                line -> choose(line, END, Accidental.SHARP), null, Strings.REPORT_REASON_TIE_NOT_HELD),
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
            new EditCase("a grace note inserted between them removes it",
                line -> line.addElement(END, grace(TIED)), null, Strings.REPORT_REASON_TIE_NO_FOLLOWING_NOTE),
            new EditCase("a note at another pitch inserted between them removes it",
                line -> line.addElement(END, note(OTHER)), null, Strings.REPORT_REASON_TIE_NOT_HELD),
            new EditCase("converting the anchor to a grace note leaves nothing tied and reports nothing",
                line -> line.setElement(ANCHOR,
                    StaffElement.convertedFrom(ElementType.GRACE_QUAVER, line.getElement(ANCHOR))),
                null, null),
            new EditCase("replacing the end with a rest removes it, with no note following",
                line -> line.setElement(END, crotchetRest()),
                null, Strings.REPORT_REASON_TIE_NO_FOLLOWING_NOTE),
            new EditCase("replacing the end with a note at another pitch removes it, as not held",
                line -> line.setElement(END, note(OTHER)), null, Strings.REPORT_REASON_TIE_NOT_HELD),
            new EditCase("deleting the end note unties the anchor and reports nothing",
                line -> line.removeRange(ElementRange.single(END)), null, null)
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
                    .extracting(ReportedChange::headingKey, ReportedChange::description)
                    .containsExactly(tuple(Strings.REPORT_BEAMS_REMOVED,
                        Strings.get(Strings.REPORT_REASON_BEAM_TIED_NOTES))));
        });
    }

    @Test
    void testOneUndoRestoresTheEditAndTheTieItRemoved() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);
        var end = (NoteElement) line.getElement(END);
        var soundedBefore = end.soundingAccidental();
        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> choose(line, END, Accidental.SHARP));

            assertThat(anchor.isTiedToNext()).as("the fixture reaches the case only if the edit removes the tie").isFalse();

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, recorder.batches().getFirst(), ChangeReport.EMPTY);

            assertThat(end.soundingAccidental()).isEqualTo(soundedBefore);
            assertThat(anchor.isTiedTo(end)).isTrue();
        } finally {
            recorder.dispose();
        }
    }

    /**
     * An edit on one line, and whether the tie from the first line's last note into the next
     * line survives it.
     *
     * @param description the edit, as the test's display name
     * @param lines       the song's lines in compact notation; the first line ends in {@code ~}, so
     *                    its last note is tied to the second line's first
     * @param edit        makes the edit, inside a modification bracket
     * @param kept        whether the first line's last note is still tied afterwards
     * @param reasonKey   the {@link Strings} key of the reason the rule reports the removal under
     *                    {@link Strings#REPORT_TIES_REMOVED}, or {@code null} when the edit
     *                    reports no tie
     */
    private record CrossLineCase(
        String description, String[] lines, Consumer<Song> edit, boolean kept, @Nullable String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossLineCases")
    void testAnEditOnTheNextLineIsJudgedForTheTieCrossingIntoIt(CrossLineCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());
        var firstLine = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) firstLine.getElement(firstLine.elementCount() - 1);

        assertThat(anchor.isTiedTo((NoteElement) song.getLine(SECOND_LINE).getElement(ANCHOR)))
            .as("the fixture reaches the case only if the tie crosses into the second line")
            .isTrue();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(song));

            assertThat(anchor.isTiedToNext()).isEqualTo(testCase.kept());
            assertTieReport(reports, testCase.reasonKey());
        });
    }

    static Stream<CrossLineCase> crossLineCases() {
        return Stream.of(
            new CrossLineCase("moving the next line's first note to another pitch removes it",
                new String[] {TIED + " ~", TIED + " " + OTHER},
                song -> move(song.getLine(SECOND_LINE), ANCHOR, OTHER), false,
                Strings.REPORT_REASON_TIE_NOT_HELD),
            new CrossLineCase("deleting the end note that is the first note of the next line unties it silently",
                new String[] {TIED + " ~", TIED + " " + OTHER, "" + TIED},
                song -> song.getLine(SECOND_LINE).removeRange(ElementRange.single(ANCHOR)), false, null),
            new CrossLineCase("deleting the line it ends in, bringing a note at another pitch, unties it silently",
                new String[] {TIED + " ~", "" + TIED, OTHER + " " + TIED},
                song -> song.removeLine(SECOND_LINE), false, null),
            new CrossLineCase("deleting the line it ends in, bringing a note at its pitch, unties it silently",
                new String[] {TIED + " ~", "" + TIED, TIED + " " + OTHER},
                song -> song.removeLine(SECOND_LINE), false, null)
        );
    }

    private static NoteElement grace(int staffPosition) {
        var grace = graceQuaver();
        grace.setStaffPosition(new StaffPosition(staffPosition));

        return grace;
    }
}
