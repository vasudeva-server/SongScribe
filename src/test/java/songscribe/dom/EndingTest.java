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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SongFactory.editableLine;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.StaffElementFactory.bar;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link Ending#spanning}: which ranges of a line hold an ending, per the class invariant on
 * {@link Ending}, and the reason it gives for each range it refuses — including the section rule,
 * which reads the song across lines, and the rule that no bar follows the end bar. And the sweep
 * every edit runs through {@link Ending#outcomeFor}: an edit that would leave an attached ending
 * ill formed removes it and reports the reason, and every other edit keeps it, with
 * {@link Ending#spanning} still accepting the kept ending's range afterward.
 */
class EndingTest extends UnitTest {

    private static final Spanning.Refused<Ending> NOT_VALID = refusal(
        Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Ending> OVERLAPS_ENDING = refusal(
        Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID, Spanning.Cause.OTHER_SPAN);
    private static final Spanning.Refused<Ending> NO_OPEN_SECTION = refusal(
        Strings.DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Ending> SPLIT_NOT_RIGHT_REPEAT = refusal(
        Strings.DIALOG_REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Ending> BAR_AFTER_END = refusal(
        Strings.DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END, Spanning.Cause.RANGE);

    private static Spanning.Refused<Ending> refusal(String reasonKey, Spanning.Cause cause) {
        return new Spanning.Refused<>(reasonKey, cause, Strings.DIALOG_REPORT_ENDINGS_REMOVED);
    }

    /** What an ending may end on, besides a note or a rest: the bars that end a section. */
    private static final Set<ElementType> ENDING_TERMINAL_BARS = EnumSet.of(
        ElementType.SINGLE_BARLINE,
        ElementType.DOUBLE_BARLINE,
        ElementType.FINAL_DOUBLE_BARLINE,
        ElementType.REPEAT_LEFT);

    /** A line holding one well-formed, attached ending, on the song that line belongs to. */
    private record Fixture(Song song, Line line, Ending ending) {}

    /** Builds {@code range} of {@code line} into an ending, asserts it is accepted, and attaches
     * it to {@code line}. */
    private static Ending attachEnding(Line line, ElementRange range) {
        var ending = endingOver(line, range);
        ending.addTo();
        return ending;
    }

    /**
     * Appends a well-formed ending to {@code line}: {@code anchor}, {@code split}, one note in
     * the second bracket, and {@code end}. Returns the range it occupies. Not attached — the
     * caller decides whether the candidate this describes should be.
     */
    private static ElementRange addEnding(
        Line line, StaffElement anchor, StaffElement split, StaffElement end
    ) {
        var anchorIndex = line.elementCount();
        line.addElement(anchor);
        line.addElement(split);
        line.addElement(crotchet());
        line.addElement(end);
        return new ElementRange(anchorIndex, line.elementCount() - 1);
    }

    // ===== Spanning =====

    /**
     * A freshly built line, and the candidate range {@code arrange} builds on it, which holds an
     * ending.
     *
     * @param description the case, as the test's display name
     * @param newLine     builds an empty line of a real song, with any earlier lines the case
     *                    needs
     * @param arrange     builds the line's elements, including any ending already on it, and
     *                    returns the candidate range to test
     */
    private record AcceptedCase(String description, Supplier<Line> newLine, Function<Line, ElementRange> arrange) {

        /** A case on a line with no line before it. */
        AcceptedCase(String description, Function<Line, ElementRange> arrange) {
            this(description, () -> editableLine(), arrange);
        }
    }

    /**
     * A freshly built line, and the candidate range {@code arrange} builds on it, which holds no
     * ending.
     *
     * @param description the case, as the test's display name
     * @param newLine     builds an empty line of a real song, with any earlier lines the case
     *                    needs
     * @param arrange     builds the line's elements, including any ending already on it, and
     *                    returns the candidate range to test
     * @param refusal     what {@link Ending#spanning} answers for the candidate range
     */
    private record RefusedCase(
        String description,
        Supplier<Line> newLine,
        Function<Line, ElementRange> arrange,
        Spanning.Refused<Ending> refusal
    ) {

        /** A case on a line with no line before it. */
        RefusedCase(String description, Function<Line, ElementRange> arrange, Spanning.Refused<Ending> refusal) {
            this(description, () -> editableLine(), arrange, refusal);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedCases")
    void testSpanningAcceptsAnEndingOverAWellFormedRange(AcceptedCase testCase) {
        var line = testCase.newLine().get();
        var span = testCase.arrange().apply(line);

        assertThat(Ending.spanning(line, span)).isInstanceOf(Spanning.Spanned.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedCases")
    void testSpanningNamesTheReasonAndCauseItRefusesTheRange(RefusedCase testCase) {
        var line = testCase.newLine().get();
        var span = testCase.arrange().apply(line);

        assertThat(Ending.spanning(line, span)).isEqualTo(testCase.refusal());
    }

    /** A fixture anchored on a note or a rest: anchor, a right repeat, content, an end note. */
    private static Consumer<Line> durationAnchorFixture(Supplier<StaffElement> anchor) {
        return line -> {
            line.addElement(anchor.get());
            line.addElement(repeatRight());
            line.addElement(crotchet());
            line.addElement(crotchet());
        };
    }

    /** A fixture anchored on a bar: anchor, content, a right repeat, content, an end note. */
    private static Consumer<Line> barAnchorFixture(Supplier<StaffElement> anchor) {
        return line -> {
            line.addElement(anchor.get());
            line.addElement(crotchet());
            line.addElement(repeatRight());
            line.addElement(crotchet());
            line.addElement(crotchet());
        };
    }

    /** Adapts a fixture that populates a whole line into an {@code arrange} whose candidate range is
     * that whole line. */
    private static Function<Line, ElementRange> wholeLine(Consumer<Line> populate) {
        return line -> {
            populate.accept(line);
            return wholeRangeOf(line);
        };
    }

    /**
     * A line holding an ending's elements — anchor, right repeat, one note, and the end — followed
     * by {@code after}, and the range of the ending alone.
     */
    private static Function<Line, ElementRange> endingFollowedBy(StaffElement end, StaffElement... after) {
        return line -> {
            var range = addEnding(line, crotchet(), repeatRight(), end);

            for (var element : after) {
                line.addElement(element);
            }

            return range;
        };
    }

    /**
     * Elements in front of a note or rest anchor, then a fixture anchored on it; the candidate
     * range runs from the anchor to the end of the line.
     */
    private static Function<Line, ElementRange> anchoredAfter(
        Supplier<StaffElement> anchor, List<Supplier<StaffElement>> inFront
    ) {
        return line -> {
            for (var element : inFront) {
                line.addElement(element.get());
            }

            var anchorIndex = line.elementCount();
            durationAnchorFixture(anchor).accept(line);

            return new ElementRange(anchorIndex, line.elementCount() - 1);
        };
    }

    /** The empty last line of a song whose earlier line is {@code earlierLine}, in compact notation. */
    private static Supplier<Line> lineAfter(String earlierLine) {
        return () -> editableLine(earlierLine);
    }

    /** The first line of a song whose second line is {@code laterLine}, with tracking suspended. */
    private static Supplier<Line> lineBefore(String laterLine) {
        return () -> {
            var song = compactSong(Key.NO_ACCIDENTALS, "", laterLine);
            song.beginSuspendMutationTracking();

            return song.getLine(0);
        };
    }

    /** A line of a real song whose elements are {@code anchor} through {@code end}, in a song of one line. */
    private static Supplier<Line> lineOfEnding(StaffElement end) {
        return () -> buildSong(line -> addEnding(line, crotchet(), repeatRight(), end)).getLine(0);
    }

    private static final Supplier<StaffElement> NOTE = StaffElementFactory::crotchet;

    static Stream<AcceptedCase> acceptedCases() {
        // The only bar an ending anchors on or opens behind; refusedCases holds every other bar.
        var barAnchors = Stream.of(ElementType.SINGLE_BARLINE)
            .map(type -> new AcceptedCase("a " + type + " anchor is accepted",
                wholeLine(barAnchorFixture(() -> bar(type)))));

        var barsInFront = Stream.of(ElementType.SINGLE_BARLINE)
            .map(type -> new AcceptedCase("a note anchor after a " + type + " is accepted",
                anchoredAfter(NOTE, List.of(() -> bar(type)))));

        var terminalEnds = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && ENDING_TERMINAL_BARS.contains(type))
            .map(type -> new AcceptedCase("a " + type + " end is accepted",
                lineOfEnding(bar(type)), EndingTest::wholeRangeOf));

        var fixed = Stream.of(
            new AcceptedCase("a note anchor at the start of the line is accepted",
                wholeLine(durationAnchorFixture(StaffElementFactory::crotchet))),
            new AcceptedCase("a rest anchor is accepted",
                wholeLine(durationAnchorFixture(StaffElementFactory::crotchetRest))),
            new AcceptedCase("a note anchor after a note is accepted",
                anchoredAfter(NOTE, List.of(NOTE))),
            new AcceptedCase("a note end is the second bracket's content",
                lineOfEnding(crotchet()), EndingTest::wholeRangeOf),
            new AcceptedCase("a rest end is the second bracket's content",
                lineOfEnding(StaffElementFactory.crotchetRest()), EndingTest::wholeRangeOf),
            new AcceptedCase("an end bar that is the last element of its line is accepted",
                endingFollowedBy(singleBarline())),
            new AcceptedCase("an end note followed by a bar that is the last element of its line is accepted",
                endingFollowedBy(crotchet(), singleBarline())),
            new AcceptedCase("a bar at the start of the next line is not read",
                lineBefore("|"), endingFollowedBy(singleBarline())),
            new AcceptedCase("a split behind an opening repeat on an earlier line is accepted",
                lineAfter(":| |: 3"),
                line -> addEnding(line, crotchet(), repeatRight(), crotchet())),
            new AcceptedCase("a repeat opening between two endings gives the later one an open section",
                line -> {
                    attachEnding(line, addEnding(line, crotchet(), repeatRight(), crotchet()));
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    return addEnding(line, crotchet(), repeatRight(), crotchet());
                })
        );

        return Stream.of(fixed, barAnchors, barsInFront, terminalEnds).flatMap(stream -> stream);
    }

    static Stream<RefusedCase> refusedCases() {
        var barAnchors = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && type != ElementType.SINGLE_BARLINE)
            .map(type -> new RefusedCase("a " + type + " anchor is refused",
                wholeLine(barAnchorFixture(() -> bar(type))), NOT_VALID));

        var barsInFront = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && type != ElementType.SINGLE_BARLINE)
            .map(type -> new RefusedCase("a note anchor after a " + type + " is refused",
                anchoredAfter(NOTE, List.of(() -> bar(type))), NOT_VALID));

        var interiorBarsInFirstBracket = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && !type.closesRepeat())
            .map(type -> new RefusedCase("a " + type + " inside the first bracket is refused",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(bar(type));
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                }),
                NOT_VALID));

        var interiorBarsInSecondBracket = Stream.of(ElementType.values())
            .filter(ElementType::isBar)
            .map(type -> new RefusedCase("a " + type + " inside the second bracket is refused",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                    line.addElement(bar(type));
                    line.addElement(crotchet());
                }),
                NOT_VALID));

        var nonTerminalEnds = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && !ENDING_TERMINAL_BARS.contains(type))
            .map(type -> new RefusedCase("a " + type + " end is refused",
                lineOfEnding(bar(type)), EndingTest::wholeRangeOf, NOT_VALID));

        var fixed = Stream.of(
            new RefusedCase("a note anchor after a REPEAT_LEFT_RIGHT is refused",
                anchoredAfter(NOTE, List.of(StaffElementFactory::repeatLeftRight)), NOT_VALID),
            new RefusedCase("a rest anchor after a REPEAT_LEFT_RIGHT is refused",
                anchoredAfter(StaffElementFactory::crotchetRest, List.of(StaffElementFactory::repeatLeftRight)),
                NOT_VALID),
            new RefusedCase("a grace note between a DOUBLE_BARLINE and a note anchor does not hide the bar",
                anchoredAfter(NOTE, List.of(StaffElementFactory::doubleBarline, StaffElementFactory::graceQuaver)),
                NOT_VALID),
            new RefusedCase("a grace note end is refused",
                lineOfEnding(graceQuaver()), EndingTest::wholeRangeOf, NOT_VALID),

            new RefusedCase("no bar strictly inside closes a repeat, so there is no split",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                NOT_VALID),

            new RefusedCase("a bracket that holds only a grace note holds no content",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(graceQuaver());
                    line.addElement(doubleBarline());
                }),
                NOT_VALID),

            new RefusedCase("a REPEAT_LEFT_RIGHT split is refused with its own reason",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatLeftRight());
                    line.addElement(crotchet());
                    line.addElement(singleBarline());
                }),
                SPLIT_NOT_RIGHT_REPEAT),

            new RefusedCase("a bar directly after the end bar is refused",
                endingFollowedBy(singleBarline(), singleBarline()),
                BAR_AFTER_END),

            new RefusedCase("a bar after the end bar, past a grace note, is refused",
                endingFollowedBy(singleBarline(), graceQuaver(), singleBarline()),
                BAR_AFTER_END),

            new RefusedCase("a bar after the bar that follows an end note is refused",
                endingFollowedBy(crotchet(), singleBarline(), singleBarline()),
                BAR_AFTER_END),

            new RefusedCase("a split that a double barline stands behind has no open section",
                line -> {
                    line.addElement(doubleBarline());
                    line.addElement(crotchet());
                    return addEnding(line, crotchet(), repeatRight(), crotchet());
                },
                NO_OPEN_SECTION),

            new RefusedCase("a split behind a closing repeat on an earlier line has no open section",
                lineAfter(":| 3"),
                line -> addEnding(line, crotchet(), repeatRight(), crotchet()),
                NO_OPEN_SECTION),

            new RefusedCase("a candidate whose split crosses the earlier ending's split has no open section",
                line -> {
                    attachEnding(line, addEnding(line, crotchet(), repeatRight(), crotchet()));
                    line.addElement(crotchet());
                    return addEnding(line, crotchet(), repeatRight(), crotchet());
                },
                NO_OPEN_SECTION),

            new RefusedCase("a candidate overlapping an ending already on the line is refused",
                line -> {
                    var existingRange = addEnding(line, crotchet(), repeatRight(), crotchet());
                    attachEnding(line, existingRange);
                    return existingRange;
                },
                OVERLAPS_ENDING)
        );

        return Stream.of(
                fixed, barAnchors, barsInFront, interiorBarsInFirstBracket, interiorBarsInSecondBracket,
                nonTerminalEnds)
            .flatMap(stream -> stream);
    }

    // ===== The sweep =====

    /**
     * A fixture and an edit to apply to it that leaves its ending well formed.
     *
     * @param description  the case, as the test's display name
     * @param buildFixture builds a fresh line with one well-formed ending attached
     * @param edit         the edit to apply to the fixture's line, inside a modification bracket
     */
    private record KeepCase(String description, Supplier<Fixture> buildFixture, Consumer<Line> edit) {}

    /**
     * A fixture and an edit to apply to it that leaves its ending ill formed.
     *
     * @param description  the case, as the test's display name
     * @param buildFixture builds a fresh line with one well-formed ending attached
     * @param edit         the edit to apply to the fixture's line, inside a modification bracket
     * @param reasonKey    the {@code Strings} key of the reason the ending is removed for
     */
    private record RemoveCase(
        String description, Supplier<Fixture> buildFixture, Consumer<Line> edit, String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("keepCases")
    void testAnEditThatLeavesTheEndingWellFormedKeepsItUnreportedAndSpanningStillAcceptsItsRange(KeepCase testCase) {
        var fixture = testCase.buildFixture().get();
        var song = fixture.song();
        var line = fixture.line();
        var ending = fixture.ending();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(line));

            assertThat(line.spanRegistry().getSpans()).contains(ending);
            assertThat(reports.reports()).as("a kept ending is not reported").isEmpty();
        });

        // spanning refuses a range that an ending on the line already covers, so the kept ending
        // comes off for the question, and goes back afterwards
        var range = ending.range();

        song.withoutMutationTracking(() -> ending.removeFrom());

        assertThat(Ending.spanning(line, range)).isInstanceOf(Spanning.Spanned.class);

        song.withoutMutationTracking(() -> ending.addTo());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removeCases")
    void testAnEditThatLeavesTheEndingIllFormedRemovesItAndReportsTheReason(RemoveCase testCase) {
        var fixture = testCase.buildFixture().get();
        var song = fixture.song();
        var line = fixture.line();
        var ending = fixture.ending();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(line));

            assertThat(line.spanRegistry().getSpans()).doesNotContain(ending);
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.DIALOG_REPORT_ENDINGS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(testCase.reasonKey()));
                }));
        });
    }

    /** The fixture line {@code A X :| Y ||}, with the ending over all five elements. */
    private static final int NOTE_A_INDEX = 0;
    private static final int NOTE_X_INDEX = 1;
    private static final int SPLIT_INDEX = 2;
    private static final int NOTE_Y_INDEX = 3;
    private static final int END_INDEX = 4;

    /**
     * A real song of two lines, the first holding {@code elements} and the second empty, and an
     * ending attached to {@code endingRange} of the first. Mutation tracking is on, so an edit
     * runs inside {@link Song#withModification}.
     */
    private static Fixture fixtureOf(ElementRange endingRange, StaffElement... elements) {
        var song = buildSong(line -> Arrays.stream(elements).forEach(line::addElement), line -> { });
        var line = song.getLine(0);
        var ending = addWithoutTracking(song, () -> endingOver(line, endingRange));

        return new Fixture(song, line, ending);
    }

    private static Fixture noteAnchorFixture() {
        return fixtureOf(new ElementRange(NOTE_A_INDEX, END_INDEX),
            crotchet(), crotchet(), repeatRight(), crotchet(), doubleBarline());
    }

    /** The bar-anchor fixture {@code <anchor> X :| Y Z}, with the ending over all five. */
    private static Fixture barAnchoredEnding(StaffElement anchor) {
        return fixtureOf(new ElementRange(NOTE_A_INDEX, END_INDEX),
            anchor, crotchet(), repeatRight(), crotchet(), crotchet());
    }

    /** The note-anchor fixture whose first bracket holds only the anchor: {@code A :| X Y}. */
    private static final int SOLE_CONTENT_END_INDEX = 3;

    private static Fixture soleContentAnchorFixture() {
        return fixtureOf(new ElementRange(NOTE_A_INDEX, SOLE_CONTENT_END_INDEX),
            crotchet(), repeatRight(), crotchet(), crotchet());
    }

    /** The anchor's index in a {@link #frontedFixture} with one element in front of it. */
    private static final int FRONTED_ANCHOR_INDEX = 1;

    /** The anchor's index in a {@link #frontedFixture} with two elements in front of it. */
    private static final int TWICE_FRONTED_ANCHOR_INDEX = 2;

    /**
     * The line {@code <inFront...> <anchor> X :| Y ||}, with the ending from the anchor through
     * the end.
     */
    private static Fixture frontedFixture(StaffElement anchor, StaffElement... inFront) {
        var elements = Stream.concat(
                Arrays.stream(inFront),
                Stream.of(anchor, crotchet(), repeatRight(), crotchet(), doubleBarline()))
            .toArray(StaffElement[]::new);

        return fixtureOf(new ElementRange(inFront.length, elements.length - 1), elements);
    }

    static Stream<KeepCase> keepCases() {
        return Stream.of(
            new KeepCase("deleting a note that is not the bracket's only content keeps the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(NOTE_X_INDEX))),

            new KeepCase("inserting a bar at the anchor's index keeps the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(NOTE_A_INDEX, singleBarline())),

            new KeepCase("inserting a note inside a bracket keeps the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(NOTE_X_INDEX, crotchet()))
        );
    }

    static Stream<RemoveCase> removeCases() {
        return Stream.of(
            new RemoveCase("deleting the anchor removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(NOTE_A_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED),

            new RemoveCase("deleting the end removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(END_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED),

            new RemoveCase("deleting the split removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(SPLIT_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("deleting the second bracket's only content removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(NOTE_Y_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("inserting a bar at the end's index removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(END_INDEX, singleBarline()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("replacing a bracket's content note with a bar removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.setElement(NOTE_X_INDEX, singleBarline()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("replacing a SINGLE_BARLINE anchor with a DOUBLE_BARLINE removes the ending",
                () -> barAnchoredEnding(singleBarline()),
                line -> line.setElement(NOTE_A_INDEX, doubleBarline()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("replacing the anchor that is the first bracket's only content with a bar removes the ending",
                EndingTest::soleContentAnchorFixture,
                line -> line.setElement(NOTE_A_INDEX, singleBarline()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("inserting a REPEAT_RIGHT in front of a note anchor removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(NOTE_A_INDEX, repeatRight()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("replacing the note in front of a note anchor with a DOUBLE_BARLINE removes the ending",
                () -> frontedFixture(crotchet(), crotchet()),
                line -> line.setElement(FRONTED_ANCHOR_INDEX - 1, doubleBarline()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("deleting the only note between a REPEAT_LEFT and a note anchor removes the ending",
                () -> frontedFixture(crotchet(), repeatLeft(), crotchet()),
                line -> line.removeRange(ElementRange.single(TWICE_FRONTED_ANCHOR_INDEX - 1)),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),

            new RemoveCase("replacing a SINGLE_BARLINE anchor after a REPEAT_LEFT_RIGHT with a note removes the ending",
                () -> frontedFixture(singleBarline(), repeatLeftRight()),
                line -> line.setElement(FRONTED_ANCHOR_INDEX, crotchet()),
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID)
        );
    }
}
