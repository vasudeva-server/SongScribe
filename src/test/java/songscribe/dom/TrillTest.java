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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.songOfQuavers;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The trill invariant, as {@link Trill#spanning} judges a range by it and as the sweep every edit
 * runs through {@link Trill#outcomeFor} keeps it: an edit that leaves an endpoint that is not a
 * pitched note, or a rest or a breath mark inside the trill, removes the trill and reports the
 * reason, and every other edit keeps it.
 */
class TrillTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** The trill every sweep case edits around: an anchor, two interior notes and an end. */
    private static final ElementRange TRILLED = new ElementRange(1, 4);

    private static final int ANCHOR_INDEX = TRILLED.begin();
    private static final int INTERIOR_INDEX = ANCHOR_INDEX + 1;
    private static final int END_INDEX = TRILLED.end();

    /** The note just past the trill's end, where an insertion also lands outside the trill. */
    private static final int OUTSIDE_INDEX = END_INDEX + 1;

    private static final int LINE_LENGTH = OUTSIDE_INDEX + 1;

    /**
     * The elements of a line a trill is asked over, from its first element to its last.
     *
     * @param description the line, as the test's display name
     * @param elements    makes the line's elements, in order
     */
    private record RangeCase(String description, Supplier<List<StaffElement>> elements) {}

    /**
     * A line a trill is refused over, from its first element to its last, and why.
     *
     * @param description the line, as the test's display name
     * @param elements    makes the line's elements, in order
     * @param reasonKey   the {@code Strings} key of the reason refused
     */
    private record RefusedRangeCase(String description, Supplier<List<StaffElement>> elements, String reasonKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedRanges")
    void testARangeWithPitchedNotesAtBothEndsAndNoRestOrBreathMarkHoldsATrill(RangeCase testCase) {
        var line = lineOf(testCase.elements().get().toArray(StaffElement[]::new));
        var range = wholeRangeOf(line);

        var answer = Trill.spanning(line, range);

        assertThat(answer).isInstanceOf(Spanning.Spanned.class);

        var spanned = (Spanning.Spanned<Trill>) answer;

        assertThat(spanned.span().getAnchorElement()).isSameAs(line.getElement(range.begin()));
        assertThat(spanned.span().getEndElement()).isSameAs(line.getElement(range.end()));
        assertThat(spanned.absorbed()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedRanges")
    void testARangeThatBreaksTheInvariantIsRefusedWithTheReason(RefusedRangeCase testCase) {
        var line = lineOf(testCase.elements().get().toArray(StaffElement[]::new));

        assertThat(Trill.spanning(line, wholeRangeOf(line))).isEqualTo(new Spanning.Refused<Trill>(
            testCase.reasonKey(), Spanning.Cause.RANGE, Strings.REPORT_TRILLS_REMOVED));
    }

    static Stream<RangeCase> acceptedRanges() {
        return Stream.of(
            new RangeCase("a single note", () -> List.of(quaver())),
            new RangeCase("notes only", () -> List.of(quaver(), crotchet(), quaver())),
            new RangeCase("a bar line inside", () -> List.of(quaver(), singleBarline(), quaver())),
            new RangeCase("a key change inside", () -> List.of(quaver(), keyChange(Key.ONE_SHARP), quaver())),
            new RangeCase("a grace note inside", () -> List.of(quaver(), graceQuaver(), quaver()))
        );
    }

    static Stream<RefusedRangeCase> refusedRanges() {
        return Stream.of(
            new RefusedRangeCase("a single rest",
                () -> List.of(crotchetRest()), Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RefusedRangeCase("starting on a rest",
                () -> List.of(crotchetRest(), quaver()), Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RefusedRangeCase("starting on a grace note",
                () -> List.of(graceQuaver(), quaver()), Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RefusedRangeCase("ending on a bar line",
                () -> List.of(quaver(), singleBarline()), Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RefusedRangeCase("ending on a key change",
                () -> List.of(quaver(), keyChange(Key.ONE_SHARP)), Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RefusedRangeCase("ending on a rest, with a rest inside, judged by its end first",
                () -> List.of(quaver(), crotchetRest(), crotchetRest()), Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RefusedRangeCase("a rest inside",
                () -> List.of(quaver(), crotchetRest(), quaver()), Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK),
            new RefusedRangeCase("a breath mark inside",
                () -> List.of(quaver(), breathMark(), quaver()), Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK)
        );
    }

    /** A line of quavers, and the trill over {@link #TRILLED}. */
    private record TrilledLine(Song song, Line line, Trill trill) {}

    /**
     * One edit to a trilled line that removes the trill, and why.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     * @param reasonKey   the {@code Strings} key of the reason reported
     */
    private record RemovalCase(String description, Consumer<Line> edit, String reasonKey) {}

    /**
     * One edit to a trilled line that leaves the trill alone.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     */
    private record KeptCase(String description, Consumer<Line> edit) {}

    private static TrilledLine trilledLine() {
        var song = songOfQuavers(LINE_LENGTH);
        var line = song.getLine(FIRST_LINE);
        var trill = addWithoutTracking(song, () -> trillOver(line, TRILLED));

        return new TrilledLine(song, line, trill);
    }

    /**
     * Makes {@code edit} to the trilled line inside a modification bracket, then hands the reports
     * the edit delivered to {@code verdict}.
     */
    private static void whileEditing(TrilledLine trilled, Consumer<Line> edit, Consumer<ReportRecorder> verdict) {
        ReportRecorder.whileRecording(reports -> {
            var line = trilled.line();

            trilled.song().withModification(() -> edit.accept(line));
            verdict.accept(reports);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removalCases")
    void testAnEditThatBreaksTheInvariantRemovesTheTrillAndReportsWhy(RemovalCase testCase) {
        var trilled = trilledLine();

        whileEditing(trilled, testCase.edit(), reports -> {
            assertThat(trilled.line().spanRegistry().findSpans(Trill.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.REPORT_TRILLS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(testCase.reasonKey()));
                }));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keptCases")
    void testAnEditThatLeavesTheInvariantIntactKeepsTheTrill(KeptCase testCase) {
        var trilled = trilledLine();

        whileEditing(trilled, testCase.edit(), reports -> {
            assertThat(trilled.line().spanRegistry().findSpans(Trill.class)).containsExactly(trilled.trill());
            assertThat(reports.reports()).isEmpty();
        });
    }

    static Stream<RemovalCase> removalCases() {
        return Stream.of(
            new RemovalCase("deleting the anchor",
                line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                Strings.REPORT_REASON_ENDPOINT_DELETED),
            new RemovalCase("deleting the end",
                line -> line.removeRange(ElementRange.single(END_INDEX)),
                Strings.REPORT_REASON_ENDPOINT_DELETED),
            new RemovalCase("replacing the anchor with a rest",
                line -> line.setElement(ANCHOR_INDEX, crotchetRest()),
                Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RemovalCase("replacing the end with a grace note",
                line -> line.setElement(END_INDEX, graceQuaver()),
                Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RemovalCase("replacing the end with a bar line",
                line -> line.setElement(END_INDEX, singleBarline()),
                Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new RemovalCase("replacing an interior note with a rest",
                line -> line.setElement(INTERIOR_INDEX, crotchetRest()),
                Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK),
            new RemovalCase("replacing an interior note with a breath mark",
                line -> line.setElement(INTERIOR_INDEX, breathMark()),
                Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK),
            new RemovalCase("inserting a rest inside it",
                line -> line.addElement(INTERIOR_INDEX, crotchetRest()),
                Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK),
            new RemovalCase("inserting a breath mark inside it",
                line -> line.addElement(INTERIOR_INDEX, breathMark()),
                Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK)
        );
    }

    static Stream<KeptCase> keptCases() {
        return Stream.of(
            new KeptCase("replacing the anchor with another pitched note",
                line -> line.setElement(ANCHOR_INDEX, crotchet())),
            new KeptCase("deleting an interior note",
                line -> line.removeRange(ElementRange.single(INTERIOR_INDEX))),
            new KeptCase("replacing a note outside the trill with a rest",
                line -> line.setElement(OUTSIDE_INDEX, crotchetRest())),
            new KeptCase("inserting a quaver inside it",
                line -> line.addElement(INTERIOR_INDEX, quaver())),
            new KeptCase("inserting a grace note inside it",
                line -> line.addElement(INTERIOR_INDEX, graceQuaver())),
            new KeptCase("inserting a bar line inside it",
                line -> line.addElement(INTERIOR_INDEX, singleBarline())),
            new KeptCase("inserting a key change inside it",
                line -> line.addElement(INTERIOR_INDEX, keyChange(Key.ONE_SHARP))),
            new KeptCase("inserting a rest just before the anchor",
                line -> line.addElement(ANCHOR_INDEX, crotchetRest())),
            new KeptCase("inserting a rest just after the end",
                line -> line.addElement(OUTSIDE_INDEX, crotchetRest()))
        );
    }
}
