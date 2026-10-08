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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import static songscribe.dom.SongFactory.lineOfQuavers;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.bar;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link Hairpin#spanning}: which ranges of a line hold a hairpin, per the class invariant on
 * {@link Hairpin}, with the range the hairpin holds and the same-kind hairpins it absorbs for a
 * range it accepts, widening the requested range over every same-kind hairpin in reach until it
 * stops growing; and the reason and cause it gives for each range it refuses, judged over that
 * widened range. And the sweep of an edit that brings a repeat or a non-single bar inside a
 * hairpin: the hairpin is removed and reported, and undo puts it back.
 */
class HairpinTest extends UnitTest {

    private static final Spanning.Refused<Hairpin> ENDPOINT_UNUSABLE = refusal(
        Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Hairpin> TOO_FEW_COLUMNS = refusal(
        Strings.DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_NOTES, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Hairpin> NON_SINGLE_BAR = refusal(
        Strings.DIALOG_REPORT_REASON_HAIRPIN_NON_SINGLE_BAR, Spanning.Cause.RANGE);
    private static final Spanning.Refused<Hairpin> OPPOSITE_OVERLAP = refusal(
        Strings.DIALOG_REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP, Spanning.Cause.OTHER_SPAN);

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

    /** The range of the line that ends on a rest, which holds {@link #BEFORE_THE_REST}. */
    private static final ElementRange ENDING_ON_A_REST = new ElementRange(0, 3);

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

    /** A hairpin flush against {@link #FLUSH_CANDIDATE}. */
    private static final ElementRange FLUSH_NEIGHBOR = new ElementRange(0, 1);

    private static final ElementRange FLUSH_CANDIDATE = new ElementRange(2, 3);

    /**
     * A range that holds a crescendo.
     *
     * @param description the case, as the test's display name
     * @param line        builds the line, with any hairpin already on it
     * @param candidate   the range asked about
     * @param range       the range the crescendo holds, once it has widened over the crescendos in
     *                    reach
     * @param absorbed    the ranges of the crescendos it absorbs, in any order
     */
    private record AcceptedCase(
        String description,
        Supplier<Line> line,
        ElementRange candidate,
        ElementRange range,
        List<ElementRange> absorbed
    ) {

        /** A case where nothing is in reach: the crescendo holds the candidate and absorbs nothing. */
        static AcceptedCase alone(String description, Supplier<Line> line, ElementRange candidate) {
            return new AcceptedCase(description, line, candidate, candidate, List.of());
        }
    }

    /**
     * A range that holds no crescendo.
     *
     * @param description the case, as the test's display name
     * @param line        builds the line, with any hairpin already on it
     * @param candidate   the range asked about
     * @param refusal     the refusal expected
     */
    private record RefusedCase(
        String description,
        Supplier<Line> line,
        ElementRange candidate,
        Spanning.Refused<Hairpin> refusal
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedCases")
    void testSpanningBuildsACrescendoOverTheRangeWidenedOverTheCrescendosInReach(AcceptedCase testCase) {
        var line = testCase.line().get();
        var spansBefore = List.copyOf(line.spanRegistry().getSpans());

        var spanning = Hairpin.spanning(line, Hairpin.Kind.CRESCENDO, testCase.candidate());

        assertThat(line.spanRegistry().getSpans()).as("the hairpin is built, not added")
            .containsExactlyInAnyOrderElementsOf(spansBefore);

        switch (spanning) {
            case Spanning.Spanned<Hairpin> spanned -> {
                assertThat(spanned.span().getKind()).isEqualTo(Hairpin.Kind.CRESCENDO);
                assertThat(spanned.span().range()).isEqualTo(testCase.range());
                assertThat(spanned.absorbed()).extracting(Hairpin::range)
                    .containsExactlyInAnyOrderElementsOf(testCase.absorbed());
            }
            case Spanning.Refused<Hairpin> refused ->
                throw new AssertionError("expected a hairpin, but the range was refused: " + refused);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedCases")
    void testSpanningRefusesARangeWithTheReasonAndCauseTheInvariantGives(RefusedCase testCase) {
        var line = testCase.line().get();
        var spansBefore = List.copyOf(line.spanRegistry().getSpans());

        var spanning = Hairpin.spanning(line, Hairpin.Kind.CRESCENDO, testCase.candidate());

        assertThat(spanning).isEqualTo(testCase.refusal());
        assertThat(line.spanRegistry().getSpans()).containsExactlyInAnyOrderElementsOf(spansBefore);
    }

    static Stream<AcceptedCase> acceptedCases() {
        return Stream.of(
            acceptedWholeLine("two pitched notes hold one", quaver(), quaver()),
            acceptedWholeLine("a grace note on a pitched host may anchor it", graceQuaver(), quaver(), quaver()),
            acceptedWholeLine("a rest inside it is allowed", quaver(), crotchetRest(), quaver()),
            acceptedWholeLine("a single barline inside it is allowed", quaver(), singleBarline(), quaver()),
            AcceptedCase.alone("starting where a diminuendo ends is allowed",
                () -> {
                    var line = lineOfQuavers(BACK_TO_BACK_SECOND.end() + 1);
                    addDiminuendo(line, BACK_TO_BACK_FIRST);
                    return line;
                },
                BACK_TO_BACK_SECOND),
            AcceptedCase.alone("with nothing in reach, it holds the candidate and absorbs nothing",
                () -> lineWithCrescendo(APART, APART_CANDIDATE), APART_CANDIDATE),
            AcceptedCase.alone("a flush diminuendo is not absorbed",
                () -> lineWithDiminuendo(FLUSH_NEIGHBOR, FLUSH_CANDIDATE), FLUSH_CANDIDATE),
            new AcceptedCase("an overlapped crescendo is absorbed, widening the hairpin over it",
                () -> lineWithCrescendo(OVERLAPPED_CRESCENDO, OVERLAPPING_CANDIDATE), OVERLAPPING_CANDIDATE,
                new ElementRange(OVERLAPPED_CRESCENDO.begin(), OVERLAPPING_CANDIDATE.end()),
                List.of(OVERLAPPED_CRESCENDO)),
            new AcceptedCase("a flush crescendo is absorbed, widening the hairpin over it",
                () -> lineWithCrescendo(FLUSH_NEIGHBOR, FLUSH_CANDIDATE), FLUSH_CANDIDATE,
                new ElementRange(FLUSH_NEIGHBOR.begin(), FLUSH_CANDIDATE.end()),
                List.of(FLUSH_NEIGHBOR))
        );
    }

    static Stream<RefusedCase> refusedCases() {
        var byNonSingleBar = Stream.of(ElementType.values())
            .filter(type -> type.isBar() && type != ElementType.SINGLE_BARLINE)
            .map(type -> refusedWholeLine("a " + type + " inside it is refused",
                NON_SINGLE_BAR, quaver(), bar(type), quaver()));

        var fixed = Stream.of(
            refusedWholeLine("a rest cannot anchor it", ENDPOINT_UNUSABLE, crotchetRest(), quaver(), quaver()),
            refusedWholeLine("it cannot end on a rest", ENDPOINT_UNUSABLE, quaver(), quaver(), crotchetRest()),
            refusedWholeLine("one note and the rest after it hold none", ENDPOINT_UNUSABLE, quaver(), crotchetRest()),
            refusedWholeLine("a grace note and its host are one column", TOO_FEW_COLUMNS, graceQuaver(), quaver()),
            new RefusedCase("sharing two elements with a diminuendo is refused",
                () -> {
                    var line = lineOfQuavers(OVERLAPPED.end() + 1);
                    addDiminuendo(line, OVERLAPPED);
                    return line;
                },
                OVERLAPPING, OPPOSITE_OVERLAP),
            new RefusedCase("absorbing a crescendo that brings the range over a diminuendo is refused",
                () -> {
                    var line = lineOfQuavers(ABSORBING.end() + 1);
                    hairpinOver(line, Hairpin.Kind.CRESCENDO, ABSORBED).addTo();
                    addDiminuendo(line, BETWEEN);
                    return line;
                },
                ABSORBING, OPPOSITE_OVERLAP),
            new RefusedCase("absorbing the crescendos a chain of them brings into reach, over a diminuendo, is refused",
                HairpinTest::lineWithUnmergedChain, CHAIN_CANDIDATE, OPPOSITE_OVERLAP),
            new RefusedCase("a fault in the range is named before another hairpin in the way",
                () -> {
                    var line = lineOf(quaver(), quaver(), quaver(), crotchetRest());
                    addDiminuendo(line, BEFORE_THE_REST);
                    return line;
                },
                ENDING_ON_A_REST, ENDPOINT_UNUSABLE)
        );

        return Stream.concat(fixed, byNonSingleBar);
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
        addWithoutTracking(song, () -> hairpinOver(line, Hairpin.Kind.CRESCENDO, EDITED));

        var batches = new BatchRecorder();

        try {
            ReportRecorder.whileRecording(reports -> {
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
                            .isEqualTo(Strings.get(Strings.DIALOG_REPORT_REASON_HAIRPIN_NON_SINGLE_BAR));
                    }));
            });

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batches.batches().getFirst());

            assertThat(line.spanRegistry().findSpans(Hairpin.class)).extracting(Hairpin::range)
                .containsExactly(EDITED);
        } finally {
            batches.dispose();
        }
    }

    /**
     * A line holding two crescendos that touch but stay apart, because a diminuendo covers the
     * element where they meet.
     */
    private static Line lineWithUnmergedChain() {
        var line = lineOfQuavers(CHAIN_LINE_LENGTH);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, CHAIN_FIRST).addTo();
        addDiminuendo(line, CHAIN_DIMINUENDO);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, CHAIN_SECOND).addTo();
        line.deleteRange(line.effectiveRange(CHAIN_DELETED));

        assertThat(line.spanRegistry().findSpans(Hairpin.class)).extracting(Hairpin::range)
            .as("the arrangement leaves both crescendos apart")
            .contains(CHAIN_FIRST, CHAIN_SECOND_LEFT);

        return line;
    }

    /** A line of quavers long enough for {@code candidate}, holding a crescendo over {@code existing}. */
    private static Line lineWithCrescendo(ElementRange existing, ElementRange candidate) {
        var line = lineOfQuavers(candidate.end() + 1);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, existing).addTo();
        return line;
    }

    /** A line of quavers long enough for {@code candidate}, holding a diminuendo over {@code existing}. */
    private static Line lineWithDiminuendo(ElementRange existing, ElementRange candidate) {
        var line = lineOfQuavers(candidate.end() + 1);
        addDiminuendo(line, existing);
        return line;
    }

    private static void addDiminuendo(Line line, ElementRange range) {
        hairpinOver(line, Hairpin.Kind.DIMINUENDO, range).addTo();
    }

    private static Spanning.Refused<Hairpin> refusal(String reasonKey, Spanning.Cause cause) {
        return new Spanning.Refused<>(reasonKey, cause, Strings.DIALOG_REPORT_HAIRPINS_REMOVED);
    }

    /** A case whose line holds {@code elements} and whose candidate is the whole line. */
    private static RefusedCase refusedWholeLine(
        String description, Spanning.Refused<Hairpin> refusal, StaffElement... elements
    ) {
        return new RefusedCase(description, () -> lineOf(elements), rangeOfAll(elements), refusal);
    }

    /** A case whose line holds {@code elements}, a crescendo over all of which holds exactly those. */
    private static AcceptedCase acceptedWholeLine(String description, StaffElement... elements) {
        return AcceptedCase.alone(description, () -> lineOf(elements), rangeOfAll(elements));
    }

    private static ElementRange rangeOfAll(StaffElement... elements) {
        return new ElementRange(0, elements.length - 1);
    }

}
