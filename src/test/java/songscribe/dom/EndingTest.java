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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.editableLine;
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
 * ill formed removes it, and every other edit keeps it, with {@link Ending#spanning} still
 * accepting the kept ending's range afterward.
 */
class EndingTest extends UnitTest {

    /** What {@link Ending#spanning} answers: an ending when {@code refusal} is null, else that refusal. */
    private record Verdict(Spanning.@Nullable Refused<Ending> refusal) {

        static Verdict refused(String reasonKey, Spanning.Cause cause) {
            return new Verdict(new Spanning.Refused<>(reasonKey, cause));
        }
    }

    private static final Verdict ACCEPTED = new Verdict(null);
    private static final Verdict NOT_VALID =
        Verdict.refused(Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID, Spanning.Cause.RANGE);
    private static final Verdict OVERLAPS_ENDING =
        Verdict.refused(Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID, Spanning.Cause.OTHER_SPAN);
    private static final Verdict NO_OPEN_SECTION =
        Verdict.refused(Strings.DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION, Spanning.Cause.RANGE);
    private static final Verdict SPLIT_NOT_RIGHT_REPEAT =
        Verdict.refused(Strings.DIALOG_REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT, Spanning.Cause.RANGE);
    private static final Verdict BAR_AFTER_END =
        Verdict.refused(Strings.DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END, Spanning.Cause.RANGE);

    /** A line already holding zero or more well-formed, attached endings. */
    private record Fixture(Line line, List<Ending> endings) {}

    /** Asserts what {@link Ending#spanning} answers for {@code span} of {@code line}. */
    private static void assertSpanning(Line line, ElementRange span, Verdict verdict) {
        var spanning = Ending.spanning(line, span);
        var refusal = verdict.refusal();

        if (refusal == null) {
            assertThat(spanning).isInstanceOf(Spanning.Spanned.class);
        } else {
            assertThat(spanning).isEqualTo(refusal);
        }
    }

    /** Builds {@code range} of {@code line} into an ending, asserts it is accepted, and attaches
     * it to {@code line}. */
    private static Ending attachEnding(Line line, ElementRange range) {
        var ending = endingOver(line, range);
        ending.addTo(line);
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

    /**
     * A freshly built line, and what {@link Ending#spanning} answers for the candidate range
     * {@code arrange} builds.
     *
     * @param description the case, as the test's display name
     * @param newLine     builds an empty line of a real song, with any earlier lines the case
     *                    needs
     * @param arrange     builds the line's elements, including any ending already on it, and
     *                    returns the candidate range to test
     * @param verdict     what {@link Ending#spanning} answers for the candidate range
     */
    private record SpanningCase(
        String description, Supplier<Line> newLine, Function<Line, ElementRange> arrange, Verdict verdict
    ) {

        /** A case on a line with no line before it. */
        SpanningCase(String description, Function<Line, ElementRange> arrange, Verdict verdict) {
            this(description, () -> editableLine(), arrange, verdict);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("spanningCases")
    void testSpanningAcceptsAnEndingOrNamesTheReasonItRefusesTheRange(SpanningCase testCase) {
        var line = testCase.newLine().get();
        var span = testCase.arrange().apply(line);

        assertSpanning(line, span, testCase.verdict());
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

    /** Adapts a fixture that populates a whole line into a {@link SpanningCase#arrange} whose
     * candidate range is that whole line. */
    private static Function<Line, ElementRange> wholeLine(Consumer<Line> populate) {
        return line -> {
            populate.accept(line);
            return new ElementRange(0, line.elementCount() - 1);
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

    /** The empty last line of a song whose earlier line is {@code earlierLine}, in compact notation. */
    private static Supplier<Line> lineAfter(String earlierLine) {
        return () -> editableLine(earlierLine);
    }

    static Stream<SpanningCase> spanningCases() {
        return Stream.of(
            new SpanningCase("a note anchor at the start of the line is accepted",
                wholeLine(durationAnchorFixture(StaffElementFactory::crotchet)), ACCEPTED),
            new SpanningCase("a rest anchor is accepted",
                wholeLine(durationAnchorFixture(StaffElementFactory::crotchetRest)), ACCEPTED),
            new SpanningCase("a SINGLE_BARLINE anchor is accepted",
                wholeLine(barAnchorFixture(StaffElementFactory::singleBarline)), ACCEPTED),
            new SpanningCase("a REPEAT_LEFT anchor is refused",
                wholeLine(barAnchorFixture(StaffElementFactory::repeatLeft)), NOT_VALID),
            new SpanningCase("a DOUBLE_BARLINE anchor is refused",
                wholeLine(barAnchorFixture(StaffElementFactory::doubleBarline)), NOT_VALID),
            new SpanningCase("a REPEAT_LEFT_RIGHT anchor is refused",
                wholeLine(barAnchorFixture(StaffElementFactory::repeatLeftRight)), NOT_VALID),
            new SpanningCase("a FINAL_DOUBLE_BARLINE anchor is refused",
                wholeLine(barAnchorFixture(StaffElementFactory::finalDoubleBarline)), NOT_VALID),

            new SpanningCase("no bar strictly inside closes a repeat, so there is no split",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                NOT_VALID),

            new SpanningCase("a bar that does not close a repeat sits in the first bracket",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(singleBarline());
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                }),
                NOT_VALID),

            new SpanningCase("a bar that does not close a repeat sits in the second bracket",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                    line.addElement(singleBarline());
                    line.addElement(crotchet());
                }),
                NOT_VALID),

            new SpanningCase("a bracket that holds only a grace note holds no content",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(graceQuaver());
                    line.addElement(doubleBarline());
                }),
                NOT_VALID),

            new SpanningCase("a note end is the second bracket's content",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                }),
                ACCEPTED),

            new SpanningCase("a REPEAT_LEFT_RIGHT split is refused with its own reason",
                wholeLine(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatLeftRight());
                    line.addElement(crotchet());
                    line.addElement(singleBarline());
                }),
                SPLIT_NOT_RIGHT_REPEAT),

            new SpanningCase("a bar directly after the end bar is refused",
                endingFollowedBy(singleBarline(), singleBarline()),
                BAR_AFTER_END),

            new SpanningCase("a bar after the end bar, past a grace note, is refused",
                endingFollowedBy(singleBarline(), graceQuaver(), singleBarline()),
                BAR_AFTER_END),

            new SpanningCase("a bar after the bar that follows an end note is refused",
                endingFollowedBy(crotchet(), singleBarline(), singleBarline()),
                BAR_AFTER_END),

            new SpanningCase("an end bar that is the last element of its line is accepted",
                endingFollowedBy(singleBarline()),
                ACCEPTED),

            new SpanningCase("an end note followed by a bar that is the last element of its line is accepted",
                endingFollowedBy(crotchet(), singleBarline()),
                ACCEPTED),

            new SpanningCase("a split that a double barline stands behind has no open section",
                line -> {
                    line.addElement(doubleBarline());
                    line.addElement(crotchet());
                    return addEnding(line, crotchet(), repeatRight(), crotchet());
                },
                NO_OPEN_SECTION),

            new SpanningCase("a split behind a closing repeat on an earlier line has no open section",
                lineAfter(":| 3"),
                line -> addEnding(line, crotchet(), repeatRight(), crotchet()),
                NO_OPEN_SECTION),

            new SpanningCase("a split behind an opening repeat on an earlier line is accepted",
                lineAfter(":| |: 3"),
                line -> addEnding(line, crotchet(), repeatRight(), crotchet()),
                ACCEPTED),

            new SpanningCase("a candidate whose split crosses the earlier ending's split has no open section",
                line -> {
                    attachEnding(line, addEnding(line, crotchet(), repeatRight(), crotchet()));
                    line.addElement(crotchet());
                    return addEnding(line, crotchet(), repeatRight(), crotchet());
                },
                NO_OPEN_SECTION),

            new SpanningCase("a repeat opening between two endings gives the later one an open section",
                line -> {
                    attachEnding(line, addEnding(line, crotchet(), repeatRight(), crotchet()));
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    return addEnding(line, crotchet(), repeatRight(), crotchet());
                },
                ACCEPTED),

            new SpanningCase("a candidate overlapping an ending already on the line is refused",
                line -> {
                    var existingRange = addEnding(line, crotchet(), repeatRight(), crotchet());
                    attachEnding(line, existingRange);
                    return existingRange;
                },
                OVERLAPS_ENDING)
        );
    }

    /**
     * Elements in front of a note or rest anchor, and what {@link Ending#spanning} answers for an
     * ending from that anchor to the end of the line.
     *
     * @param description the case, as the test's display name
     * @param inFront     the elements in front of the anchor, in line order
     * @param anchor      the note or rest the candidate ending is anchored on
     * @param verdict     what {@link Ending#spanning} answers for the candidate ending
     */
    private record InFrontCase(
        String description, List<Supplier<StaffElement>> inFront, Supplier<StaffElement> anchor,
        Verdict verdict
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("inFrontCases")
    void testSpanningChecksWhatStandsInFrontOfTheAnchor(InFrontCase testCase) {
        var line = editableLine();

        for (var element : testCase.inFront()) {
            line.addElement(element.get());
        }

        var anchorIndex = line.elementCount();
        durationAnchorFixture(testCase.anchor()).accept(line);

        assertSpanning(line, new ElementRange(anchorIndex, line.elementCount() - 1), testCase.verdict());
    }

    static Stream<InFrontCase> inFrontCases() {
        return Stream.of(
            new InFrontCase("a note anchor after a SINGLE_BARLINE is accepted",
                List.of(StaffElementFactory::singleBarline), StaffElementFactory::crotchet,
                ACCEPTED),
            new InFrontCase("a note anchor after a note is accepted",
                List.of(StaffElementFactory::crotchet), StaffElementFactory::crotchet,
                ACCEPTED),
            new InFrontCase("a note anchor after a DOUBLE_BARLINE is refused",
                List.of(StaffElementFactory::doubleBarline), StaffElementFactory::crotchet,
                NOT_VALID),
            new InFrontCase("a note anchor after a REPEAT_LEFT is refused",
                List.of(StaffElementFactory::repeatLeft), StaffElementFactory::crotchet,
                NOT_VALID),
            new InFrontCase("a note anchor after a REPEAT_RIGHT is refused",
                List.of(StaffElementFactory::repeatRight), StaffElementFactory::crotchet,
                NOT_VALID),
            new InFrontCase("a note anchor after a REPEAT_LEFT_RIGHT is refused",
                List.of(StaffElementFactory::repeatLeftRight), StaffElementFactory::crotchet,
                NOT_VALID),
            new InFrontCase("a rest anchor after a REPEAT_LEFT_RIGHT is refused",
                List.of(StaffElementFactory::repeatLeftRight), StaffElementFactory::crotchetRest,
                NOT_VALID),
            new InFrontCase("a grace note between a DOUBLE_BARLINE and a note anchor does not hide the bar",
                List.of(StaffElementFactory::doubleBarline, StaffElementFactory::graceQuaver),
                StaffElementFactory::crotchet,
                NOT_VALID)
        );
    }

    /** What an ending may end on, besides a note or a rest: the bars that end a section. */
    private static final Set<ElementType> ENDING_TERMINAL_BARS = EnumSet.of(
        ElementType.SINGLE_BARLINE,
        ElementType.DOUBLE_BARLINE,
        ElementType.FINAL_DOUBLE_BARLINE,
        ElementType.REPEAT_LEFT);

    /**
     * An element an ending ends on, and whether {@link Ending#spanning} accepts it.
     *
     * @param description the case, as the test's display name
     * @param end         builds the last element of the candidate range
     * @param verdict     what {@link Ending#spanning} answers for the range
     */
    private record EndTypeCase(String description, Supplier<StaffElement> end, Verdict verdict) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("endTypeCases")
    void testSpanningAcceptsAnEndOnlyOnANoteARestOrAnEndingTerminal(EndTypeCase testCase) {
        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(repeatRight());
            line.addElement(crotchet());
            line.addElement(testCase.end().get());
        });
        var line = song.getLine(0);

        assertSpanning(line, new ElementRange(0, line.elementCount() - 1), testCase.verdict());
    }

    static Stream<EndTypeCase> endTypeCases() {
        var bars = Stream.of(ElementType.values())
            .filter(ElementType::isBar)
            .map(type -> new EndTypeCase("a " + type + " end",
                () -> bar(type), ENDING_TERMINAL_BARS.contains(type) ? ACCEPTED : NOT_VALID));

        return Stream.concat(bars, Stream.of(
            new EndTypeCase("a note end", StaffElementFactory::crotchet, ACCEPTED),
            new EndTypeCase("a rest end", StaffElementFactory::crotchetRest, ACCEPTED),
            new EndTypeCase("a grace note end", StaffElementFactory::graceQuaver, NOT_VALID)));
    }

    // ===== The sweep =====

    /**
     * A fixture, an edit to apply to it, and whether each of the fixture's endings survives.
     *
     * @param description  the case, as the test's display name
     * @param buildFixture builds a fresh line with one or more well-formed endings attached
     * @param edit         the edit to apply to the fixture's line
     * @param kept         whether each of {@code buildFixture}'s endings, in the same order, is
     *                     still attached after {@code edit} lands
     */
    private record SweepCase(
        String description, Supplier<Fixture> buildFixture, Consumer<Line> edit, List<Boolean> kept
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("sweepCases")
    void testAnEditKeepsOrRemovesTheEndingAsTheInvariantRequires(SweepCase testCase) {
        var fixture = testCase.buildFixture().get();
        var line = fixture.line();

        testCase.edit().accept(line);

        for (var i = 0; i < fixture.endings().size(); i++) {
            var ending = fixture.endings().get(i);
            var shouldBeKept = testCase.kept().get(i);

            assertThat(line.spanRegistry().getSpans().contains(ending))
                .as("ending %d is kept", i)
                .isEqualTo(shouldBeKept);

            if (shouldBeKept) {
                // spanning refuses a range that an ending on the line already covers, so the
                // kept ending comes off while the others stay, and goes back afterwards
                var range = ending.range();

                ending.removeFrom(line);
                assertSpanning(line, range, ACCEPTED);
                ending.addTo(line);
            }
        }
    }

    /** The fixture line {@code A X :| Y ||}, with the ending over all five elements. */
    private static final int NOTE_A_INDEX = 0;
    private static final int NOTE_X_INDEX = 1;
    private static final int NOTE_Y_INDEX = 3;
    private static final int END_INDEX = 4;

    private static Fixture noteAnchorFixture() {
        var line = editableLine();

        line.addElement(crotchet());     // A, the anchor
        line.addElement(crotchet());     // X
        line.addElement(repeatRight());  // the split
        line.addElement(crotchet());     // Y
        line.addElement(doubleBarline()); // the end

        return new Fixture(line, List.of(attachEnding(line, new ElementRange(NOTE_A_INDEX, END_INDEX))));
    }

    /** The bar-anchor fixture {@code <anchor> X :| Y Z}, with the ending over all five. */
    private static Fixture barAnchoredEnding(StaffElement anchor) {
        var line = editableLine();

        line.addElement(anchor);
        line.addElement(crotchet());
        line.addElement(repeatRight());
        line.addElement(crotchet());
        line.addElement(crotchet());

        return new Fixture(line, List.of(attachEnding(line, new ElementRange(NOTE_A_INDEX, END_INDEX))));
    }

    /** The note-anchor fixture whose first bracket holds only the anchor: {@code A :| X Y}. */
    private static final int SOLE_CONTENT_END_INDEX = 3;

    private static Fixture soleContentAnchorFixture() {
        var line = editableLine();

        line.addElement(crotchet());     // A, the anchor and the first bracket's only content
        line.addElement(repeatRight());  // the split, immediately after the anchor
        line.addElement(crotchet());
        line.addElement(crotchet());     // the end

        return new Fixture(
            line, List.of(attachEnding(line, new ElementRange(NOTE_A_INDEX, SOLE_CONTENT_END_INDEX))));
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
        var line = editableLine();

        for (var element : inFront) {
            line.addElement(element);
        }

        var anchorIndex = line.elementCount();

        line.addElement(anchor);
        line.addElement(crotchet());       // X
        line.addElement(repeatRight());    // the split
        line.addElement(crotchet());       // Y
        line.addElement(doubleBarline());  // the end

        return new Fixture(
            line, List.of(attachEnding(line, new ElementRange(anchorIndex, line.elementCount() - 1))));
    }

    static Stream<SweepCase> sweepCases() {
        return Stream.of(
            new SweepCase("deleting a note that is not the bracket's only content keeps the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(NOTE_X_INDEX)),
                List.of(true)),

            new SweepCase("deleting the second bracket's only content removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.removeRange(ElementRange.single(NOTE_Y_INDEX)),
                List.of(false)),

            new SweepCase("inserting a bar at the end's index removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(END_INDEX, singleBarline()),
                List.of(false)),

            new SweepCase("inserting a bar at the anchor's index keeps the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(NOTE_A_INDEX, singleBarline()),
                List.of(true)),

            new SweepCase("inserting a note inside a bracket keeps the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(NOTE_X_INDEX, crotchet()),
                List.of(true)),

            new SweepCase("replacing a bracket's content note with a bar removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.setElement(NOTE_X_INDEX, singleBarline()),
                List.of(false)),

            new SweepCase("replacing a SINGLE_BARLINE anchor with a DOUBLE_BARLINE removes the ending",
                () -> barAnchoredEnding(singleBarline()),
                line -> line.setElement(NOTE_A_INDEX, doubleBarline()),
                List.of(false)),

            new SweepCase("replacing the anchor that is the first bracket's only content with a bar removes the ending",
                EndingTest::soleContentAnchorFixture,
                line -> line.setElement(NOTE_A_INDEX, singleBarline()),
                List.of(false)),

            new SweepCase("inserting a REPEAT_RIGHT in front of a note anchor removes the ending",
                EndingTest::noteAnchorFixture,
                line -> line.addElement(NOTE_A_INDEX, repeatRight()),
                List.of(false)),

            new SweepCase("replacing the note in front of a note anchor with a DOUBLE_BARLINE removes the ending",
                () -> frontedFixture(crotchet(), crotchet()),
                line -> line.setElement(FRONTED_ANCHOR_INDEX - 1, doubleBarline()),
                List.of(false)),

            new SweepCase("deleting the only note between a REPEAT_LEFT and a note anchor removes the ending",
                () -> frontedFixture(crotchet(), repeatLeft(), crotchet()),
                line -> line.removeRange(ElementRange.single(TWICE_FRONTED_ANCHOR_INDEX - 1)),
                List.of(false)),

            new SweepCase("replacing a SINGLE_BARLINE anchor after a REPEAT_LEFT_RIGHT with a note removes the ending",
                () -> frontedFixture(singleBarline(), repeatLeftRight()),
                line -> line.setElement(FRONTED_ANCHOR_INDEX, crotchet()),
                List.of(false))
        );
    }
}
