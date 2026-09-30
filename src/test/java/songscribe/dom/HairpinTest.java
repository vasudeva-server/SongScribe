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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.BatchReplay.undo;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.bar;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link Hairpin#spanning}: which ranges of a line hold a hairpin, per the class invariant on
 * {@link Hairpin}, and the reason and cause it gives for each range it refuses, judged over the
 * range the hairpin would converge to. And {@link Hairpin#converge}: the range a new hairpin
 * widens to over the same-kind hairpins in reach, repeated until it stops growing. And the sweep
 * of an edit that brings a repeat or a non-single bar inside a hairpin: the hairpin is removed and
 * reported, and undo puts it back.
 */
class HairpinTest extends UnitTest {

    private static final Spanning.Refused<Hairpin> ENDPOINT_UNUSABLE =
        new Spanning.Refused<>(Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Hairpin> TOO_FEW_COLUMNS =
        new Spanning.Refused<>(Strings.DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_COLUMNS, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Hairpin> STRUCTURAL_BOUNDARY =
        new Spanning.Refused<>(Strings.DIALOG_REPORT_REASON_HAIRPIN_STRUCTURAL_BOUNDARY, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Hairpin> OPPOSITE_OVERLAP =
        new Spanning.Refused<>(Strings.DIALOG_REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP, Spanning.Cause.OTHER_SPAN);

    /** A diminuendo that a crescendo over {@link #OVERLAPPING} shares two elements with. */
    private static final ElementRange OVERLAPPED = new ElementRange(1, 3);

    private static final ElementRange OVERLAPPING = new ElementRange(0, 2);

    /** A diminuendo that a crescendo over {@link #BACK_TO_BACK_SECOND} starts where it ends. */
    private static final ElementRange BACK_TO_BACK_FIRST = new ElementRange(0, 2);

    private static final ElementRange BACK_TO_BACK_SECOND = new ElementRange(2, 4);

    /** A crescendo that a new crescendo over {@link #ABSORBING} absorbs. */
    private static final ElementRange ABSORBED = new ElementRange(0, 3);

    /** A diminuendo back to back with both {@link #ABSORBED} and {@link #ABSORBING}. */
    private static final ElementRange BETWEEN = new ElementRange(3, 4);

    private static final ElementRange ABSORBING = new ElementRange(4, 6);

    /** A diminuendo inside the range of a crescendo that ends on a rest. */
    private static final ElementRange BEFORE_THE_REST = new ElementRange(1, 2);

    /**
     * What {@link Hairpin#spanning} answers for a candidate range.
     *
     * @param description the case, as the test's display name
     * @param line        builds the line, with any hairpin already on it
     * @param candidate   the range asked about, over the line {@code line} builds
     * @param refusal     the refusal expected, or null when a hairpin is expected
     */
    private record SpanningCase(
        String description,
        Supplier<Line> line,
        Function<Line, ElementRange> candidate,
        Spanning.@Nullable Refused<Hairpin> refusal
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("spanningCases")
    void testSpanningBuildsAHairpinOverTheCandidateOrNamesTheReasonAndCauseItRefuses(SpanningCase testCase) {
        var line = testCase.line().get();
        var candidate = testCase.candidate().apply(line);
        var spansBefore = List.copyOf(line.spanRegistry().getSpans());

        var spanning = Hairpin.spanning(line, Hairpin.Kind.CRESCENDO, candidate);

        assertThat(line.spanRegistry().getSpans()).as("the hairpin is built, not added")
            .containsExactlyInAnyOrderElementsOf(spansBefore);

        var refusal = testCase.refusal();

        if (refusal != null) {
            assertThat(spanning).isEqualTo(refusal);
            return;
        }

        assertThat(spanning).isInstanceOfSatisfying(Spanning.Spanned.class, spanned ->
            assertThat(spanned.span()).isInstanceOfSatisfying(Hairpin.class, hairpin -> {
                assertThat(hairpin.getKind()).isEqualTo(Hairpin.Kind.CRESCENDO);
                assertThat(hairpin.range()).as("the candidate, not the converged range").isEqualTo(candidate);
                assertThat(hairpin.getX1ShiftSs()).isZero();
                assertThat(hairpin.getX2ShiftSs()).isZero();
            }));
    }

    static Stream<SpanningCase> spanningCases() {
        var byStructuralBar = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && type != ElementType.SINGLE_BARLINE)
            .map(type -> wholeLine("a " + type + " inside it is refused",
                STRUCTURAL_BOUNDARY, quaver(), bar(type), quaver()));

        var fixed = Stream.of(
            wholeLine("two pitched notes hold one", null, quaver(), quaver()),
            wholeLine("a grace note on a pitched host may anchor it", null, graceQuaver(), quaver(), quaver()),
            wholeLine("a rest inside it is allowed", null, quaver(), crotchetRest(), quaver()),
            wholeLine("a single barline inside it is allowed", null, quaver(), singleBarline(), quaver()),
            wholeLine("a rest cannot anchor it", ENDPOINT_UNUSABLE, crotchetRest(), quaver(), quaver()),
            wholeLine("it cannot end on a rest", ENDPOINT_UNUSABLE, quaver(), quaver(), crotchetRest()),
            wholeLine("one note and the rest after it hold none", ENDPOINT_UNUSABLE, quaver(), crotchetRest()),
            wholeLine("a grace note and its host are one column", TOO_FEW_COLUMNS, graceQuaver(), quaver()),
            new SpanningCase("sharing two elements with a diminuendo is refused",
                () -> lineOfQuavers(OVERLAPPED.end() + 1),
                line -> {
                    addDiminuendo(line, OVERLAPPED);
                    return OVERLAPPING;
                },
                OPPOSITE_OVERLAP),
            new SpanningCase("starting where a diminuendo ends is allowed",
                () -> lineOfQuavers(BACK_TO_BACK_SECOND.end() + 1),
                line -> {
                    addDiminuendo(line, BACK_TO_BACK_FIRST);
                    return BACK_TO_BACK_SECOND;
                },
                null),
            new SpanningCase("absorbing a crescendo that brings the range over a diminuendo is refused",
                () -> lineOfQuavers(ABSORBING.end() + 1),
                line -> {
                    hairpinOver(line, Hairpin.Kind.CRESCENDO, ABSORBED).addTo(line);
                    addDiminuendo(line, BETWEEN);
                    return ABSORBING;
                },
                OPPOSITE_OVERLAP),
            new SpanningCase("a fault in the range is named before another hairpin in the way",
                () -> lineOf(quaver(), quaver(), quaver(), crotchetRest()),
                line -> {
                    addDiminuendo(line, BEFORE_THE_REST);
                    return wholeRangeOf(line);
                },
                ENDPOINT_UNUSABLE)
        );

        return Stream.concat(fixed, byStructuralBar);
    }

    /** The widening {@link #CHAIN_CANDIDATE} reaches: the whole of the chain it absorbs. */
    private static final ElementRange CHAIN_CONVERGED = new ElementRange(0, 7);

    /** A new crescendo in reach of the later of two unmerged crescendos only. */
    private static final ElementRange CHAIN_CANDIDATE = new ElementRange(6, 7);

    // The chain is built the only way two touching crescendos can stay apart: a deletion that
    // leaves them touching across a diminuendo.

    private static final ElementRange CHAIN_FIRST = new ElementRange(0, 2);
    private static final ElementRange CHAIN_DIMINUENDO = new ElementRange(2, 5);
    private static final ElementRange CHAIN_SECOND = new ElementRange(5, 7);
    private static final ElementRange CHAIN_DELETED = new ElementRange(3, 4);
    private static final int CHAIN_LINE_LENGTH = 10;

    /** {@link #CHAIN_SECOND} once {@link #CHAIN_DELETED} is gone. */
    private static final ElementRange CHAIN_SECOND_LEFT = new ElementRange(3, 5);

    /** A crescendo one surviving element short of {@link #APART_CANDIDATE}'s reach. */
    private static final ElementRange APART = new ElementRange(0, 1);

    private static final ElementRange APART_CANDIDATE = new ElementRange(3, 4);

    /** A crescendo that {@link #OVERLAPPING_CANDIDATE} overlaps by one element. */
    private static final ElementRange OVERLAPPED_CRESCENDO = new ElementRange(0, 2);

    private static final ElementRange OVERLAPPING_CANDIDATE = new ElementRange(2, 4);

    /** A diminuendo flush against {@link #FLUSH_CANDIDATE}, which a crescendo never absorbs. */
    private static final ElementRange FLUSH_DIMINUENDO = new ElementRange(0, 1);

    private static final ElementRange FLUSH_CANDIDATE = new ElementRange(2, 3);

    /**
     * What {@link Hairpin#converge} answers for a new crescendo.
     *
     * @param description the case, as the test's display name
     * @param line        builds the line, with the hairpins already on it
     * @param candidate   the range the new crescendo would cover
     * @param range       the converged range expected
     * @param absorbed    the ranges of the crescendos expected in the absorbed list, in any order
     */
    private record ConvergeCase(
        String description,
        Supplier<Line> line,
        ElementRange candidate,
        ElementRange range,
        List<ElementRange> absorbed
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("convergeCases")
    void testConvergeWidensOverEverySameKindHairpinInReachUntilTheRangeStopsGrowing(ConvergeCase testCase) {
        var line = testCase.line().get();

        var convergence = Hairpin.converge(line, Hairpin.Kind.CRESCENDO, testCase.candidate());

        assertThat(convergence.range()).isEqualTo(testCase.range());
        assertThat(convergence.absorbed()).extracting(Hairpin::range)
            .containsExactlyInAnyOrderElementsOf(testCase.absorbed());
    }

    static Stream<ConvergeCase> convergeCases() {
        return Stream.of(
            new ConvergeCase("with nothing in reach, the range is the candidate and nothing is absorbed",
                () -> lineWithCrescendo(APART, APART_CANDIDATE), APART_CANDIDATE, APART_CANDIDATE, List.of()),
            new ConvergeCase("an overlapped crescendo is absorbed",
                () -> lineWithCrescendo(OVERLAPPED_CRESCENDO, OVERLAPPING_CANDIDATE), OVERLAPPING_CANDIDATE,
                new ElementRange(OVERLAPPED_CRESCENDO.begin(), OVERLAPPING_CANDIDATE.end()),
                List.of(OVERLAPPED_CRESCENDO)),
            new ConvergeCase("a flush diminuendo is not absorbed",
                () -> {
                    var line = lineOfQuavers(FLUSH_CANDIDATE.end() + 1);
                    addDiminuendo(line, FLUSH_DIMINUENDO);
                    return line;
                },
                FLUSH_CANDIDATE, FLUSH_CANDIDATE, List.of()),
            new ConvergeCase("a crescendo brought into reach by an absorbed one is absorbed too",
                HairpinTest::lineWithUnmergedChain, CHAIN_CANDIDATE, CHAIN_CONVERGED,
                List.of(CHAIN_FIRST, CHAIN_SECOND_LEFT))
        );
    }

    /** The hairpin the bar edits land in. */
    private static final ElementRange EDITED = new ElementRange(0, 2);

    /** The note strictly inside {@link #EDITED} that a bar edit lands on. */
    private static final int EDITED_INTERIOR = EDITED.begin() + 1;

    private static final int EDITED_LINE_LENGTH = 6;

    private static final int PITCH = 3;

    /**
     * A line of {@link #EDITED_LINE_LENGTH} notes in compact notation, whose song gives each note
     * the accidental state an edit inside a modification bracket reads.
     */
    private static final String LINE_OF_NOTES =
        String.join(" ", Collections.nCopies(EDITED_LINE_LENGTH, String.valueOf(PITCH)));

    /** How an edit brings a bar inside a hairpin. */
    private enum BarEdit {
        /** The bar is inserted in front of {@link #EDITED_INTERIOR}. */
        INSERTION,
        /** The bar replaces the note at {@link #EDITED_INTERIOR}. */
        REPLACEMENT
    }

    /**
     * Every bar other than a single barline that an edit can put inside a hairpin, with each edit.
     * A final double barline is not one: it may only end the song, so nothing follows it.
     */
    static Stream<Arguments> structuralBarEdits() {
        return Stream.of(ElementType.values())
            .filter(type -> type.isBar()
                && type != ElementType.SINGLE_BARLINE
                && type != ElementType.FINAL_DOUBLE_BARLINE)
            .flatMap(type -> Stream.of(BarEdit.values()).map(edit -> Arguments.of(type, edit)));
    }

    @ParameterizedTest(name = "{1} of {0}")
    @MethodSource("structuralBarEdits")
    void testAnEditThatBringsAStructuralBarInsideAHairpinRemovesItReportedAndUndoRestoresIt(
        ElementType barType, BarEdit edit
    ) {
        var song = compactSong(Key.NO_ACCIDENTALS, LINE_OF_NOTES);
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> crescendoOver(line, EDITED).addTo(line));

        var batches = new BatchRecorder();
        var reports = new ReportRecorder();

        try {
            song.withModification(() -> {
                switch (edit) {
                    case INSERTION -> line.addElement(EDITED_INTERIOR, bar(barType));
                    case REPLACEMENT -> line.setElement(EDITED_INTERIOR, bar(barType));
                }
            });

            assertThat(line.spanRegistry().findSpans(Hairpin.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.DIALOG_REPORT_HAIRPINS_REMOVED);
                    assertThat(entry.description())
                        .isEqualTo(Strings.get(Strings.DIALOG_REPORT_REASON_HAIRPIN_STRUCTURAL_BOUNDARY));
                }));

            undo(song, batches.batches().getFirst());

            assertThat(line.spanRegistry().findSpans(Hairpin.class)).extracting(Hairpin::range)
                .containsExactly(EDITED);
        } finally {
            batches.dispose();
            reports.dispose();
        }
    }

    /**
     * A line holding two crescendos that touch but stay apart, because a diminuendo covers the
     * element where they meet.
     */
    private static Line lineWithUnmergedChain() {
        var line = lineOfQuavers(CHAIN_LINE_LENGTH);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, CHAIN_FIRST).addTo(line);
        addDiminuendo(line, CHAIN_DIMINUENDO);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, CHAIN_SECOND).addTo(line);
        line.deleteRange(line.effectiveRange(CHAIN_DELETED));

        assertThat(line.spanRegistry().findSpans(Hairpin.class)).extracting(Hairpin::range)
            .as("the arrangement leaves both crescendos apart")
            .contains(CHAIN_FIRST, CHAIN_SECOND_LEFT);

        return line;
    }

    /** A line of quavers long enough for {@code candidate}, holding a crescendo over {@code existing}. */
    private static Line lineWithCrescendo(ElementRange existing, ElementRange candidate) {
        var line = lineOfQuavers(candidate.end() + 1);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, existing).addTo(line);
        return line;
    }

    private static void addDiminuendo(Line line, ElementRange range) {
        hairpinOver(line, Hairpin.Kind.DIMINUENDO, range).addTo(line);
    }

    /** A case whose line holds {@code elements} and whose candidate is the whole line. */
    private static SpanningCase wholeLine(
        String description, Spanning.@Nullable Refused<Hairpin> refusal, StaffElement... elements
    ) {
        return new SpanningCase(description, () -> lineOf(elements), HairpinTest::wholeRangeOf, refusal);
    }

    private static Line lineOf(StaffElement... elements) {
        var line = detachedLine();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    private static ElementRange wholeRangeOf(Line line) {
        return new ElementRange(0, line.elementCount() - 1);
    }
}
