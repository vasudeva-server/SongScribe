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

package songscribe.ui.clipboard;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteValue;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;
import songscribe.dom.StaffElementRun;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Tuplet;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.editableLine;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The two rules of {@link PasteSpanReconciliation}. The straddle rule of
 * {@link PasteSpanReconciliation#reconcile}: a destination span that the paste lands inside of is
 * removed or kept by its kind, and the fragment's spans of the kinds that decides are dropped.
 * The target rule of {@link PasteSpanReconciliation#settleAgainstTarget}: a pasted ending or
 * hairpin is judged by its kind's own rule against the line as the paste leaves it. An accepted
 * one is added as the span that rule built, carrying the pasted span's user state; a refused one
 * is not added and is named with the rule's reason. A pasted tuplet is added as it is, or refused
 * when its placement is broken, and a pasted beam or trill is added as it is.
 *
 * <p>Each case runs the paste's steps in the paste's order: capture, instantiate, reconcile,
 * insert the elements, then settle.
 */
class PasteSpanReconciliationTest extends UnitTest {

    private static final double USER_X_OFFSET_SS = 0.25;
    private static final double USER_Y_OFFSET_SS = -1.5;
    private static final double X1_SHIFT_SS = 0.75;
    private static final double X2_SHIFT_SS = -0.5;

    /** The copied line's crescendo: all three of its notes. */
    private static final ElementRange COPIED_CRESCENDO = new ElementRange(0, 2);

    /**
     * An ending pasted into a destination.
     *
     * @param description the case, as the test's display name
     * @param destination builds the destination line, of a real song
     * @param insertIndex where on the destination the fragment lands
     * @param refusalKey  the reason the destination refuses the ending, or null when it accepts it
     */
    private record EndingPasteCase(
        String description,
        Supplier<Line> destination,
        ToIntFunction<Line> insertIndex,
        @Nullable String refusalKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("endingPasteCases")
    void testAPastedEndingIsAddedAsTheOneItsRuleBuiltOrNamedWithTheRulesReason(EndingPasteCase testCase) {
        var source = lineOf(crotchet(), repeatRight(), crotchet(), singleBarline());
        var copied = endingOver(source, wholeRangeOf(source));
        copied.setUserXOffsetSs(USER_X_OFFSET_SS);
        copied.setUserYOffsetSs(USER_Y_OFFSET_SS);
        copied.addTo(source);

        var destination = testCase.destination().get();
        var pasted = Fragment.capture(source, wholeRangeOf(source)).instantiate();
        var fragmentEnding = pasted.spans().getFirst();

        var settled = paste(pasted, destination, testCase.insertIndex().applyAsInt(destination));

        var refusalKey = testCase.refusalKey();

        if (refusalKey != null) {
            assertThat(settled.fragmentSpans()).isEmpty();
            assertThat(settled.fragmentSpansRejected())
                .containsExactly(new PasteSpanReconciliation.RejectedSpan(fragmentEnding, refusalKey));
            return;
        }

        assertThat(settled.fragmentSpansRejected()).isEmpty();
        assertThat(settled.fragmentSpans()).singleElement()
            .isNotSameAs(fragmentEnding)
            .isInstanceOfSatisfying(Ending.class, ending -> {
                assertThat(ending.getUserXOffsetSs()).isEqualTo(USER_X_OFFSET_SS);
                assertThat(ending.getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
            });
    }

    static Stream<EndingPasteCase> endingPasteCases() {
        return Stream.of(
            new EndingPasteCase("onto an empty line it is accepted",
                PasteSpanReconciliationTest::lineOf, Line::elementCount, null),
            new EndingPasteCase("after a double barline, its anchor is refused",
                () -> lineOf(crotchet(), doubleBarline()), Line::elementCount,
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),
            new EndingPasteCase("in front of a barline, its end bar is refused",
                () -> lineOf(crotchet(), singleBarline()), line -> line.elementCount() - 1,
                Strings.DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END)
        );
    }

    @Test
    void testAPastedHairpinIsAddedAsTheOneItsRuleBuiltCarryingItsShiftsAndOffsets() {
        var source = lineOf(quaver(), quaver(), quaver());
        var copied = hairpinOver(source, Hairpin.Kind.CRESCENDO, COPIED_CRESCENDO);
        copied.setUserXOffsetSs(USER_X_OFFSET_SS);
        copied.setUserYOffsetSs(USER_Y_OFFSET_SS);
        copied.setX1ShiftSs(X1_SHIFT_SS);
        copied.setX2ShiftSs(X2_SHIFT_SS);
        copied.addTo(source);

        var destination = lineOf(quaver());
        var pasted = Fragment.capture(source, COPIED_CRESCENDO).instantiate();
        var fragmentHairpin = pasted.spans().getFirst();

        var settled = paste(pasted, destination, destination.elementCount());

        assertThat(settled.fragmentSpansRejected()).isEmpty();
        assertThat(settled.fragmentSpans()).singleElement()
            .isNotSameAs(fragmentHairpin)
            .isInstanceOfSatisfying(Hairpin.class, hairpin -> {
                assertThat(hairpin.getKind()).isEqualTo(Hairpin.Kind.CRESCENDO);
                assertThat(hairpin.getUserXOffsetSs()).isEqualTo(USER_X_OFFSET_SS);
                assertThat(hairpin.getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
                assertThat(hairpin.getX1ShiftSs()).isEqualTo(X1_SHIFT_SS);
                assertThat(hairpin.getX2ShiftSs()).isEqualTo(X2_SHIFT_SS);
            });
    }

    /** The index a fragment lands at inside a destination span that covers the whole line. */
    private static final int STRADDLE_INDEX = 2;

    private static final int STRADDLE_LINE_LENGTH = 4;

    /**
     * A destination span that a paste lands inside of, and a span of the fragment pasted into it.
     *
     * @param description     the case, as the test's display name
     * @param line            builds a line of the shape both spans cover in full
     * @param destinationSpan builds the destination's span over the whole of a line
     * @param fragmentSpan    builds the fragment's span over the whole of a line
     * @param targetRemoved   whether the straddled destination span is removed
     * @param fragmentKept    whether the fragment's span is still added
     */
    private record StraddleCase(
        String description,
        Supplier<Line> line,
        Function<Line, Span<?>> destinationSpan,
        Function<Line, Span<?>> fragmentSpan,
        boolean targetRemoved,
        boolean fragmentKept
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("straddleCases")
    void testAStraddledSpanAndTheFragmentSpansAreReconciledByKind(StraddleCase testCase) {
        var destination = testCase.line().get();
        var straddled = testCase.destinationSpan().apply(destination);
        straddled.addTo(destination);

        var source = testCase.line().get();
        testCase.fragmentSpan().apply(source).addTo(source);
        var pasted = Fragment.capture(source, wholeRangeOf(source)).instantiate();
        var fragmentSpan = pasted.spans().getFirst();

        var reconciliation = PasteSpanReconciliation.reconcile(
            destination, new StaffElementRun.InsertionTarget.AtIndex(STRADDLE_INDEX), pasted.spans());

        if (testCase.targetRemoved()) {
            assertThat(reconciliation.targetSpansToRemove()).containsExactly(straddled);
        } else {
            assertThat(reconciliation.targetSpansToRemove()).isEmpty();
        }

        if (testCase.fragmentKept()) {
            assertThat(reconciliation.fragmentSpans()).containsExactly(fragmentSpan);
            assertThat(reconciliation.fragmentSpansDropped()).isEmpty();
        } else {
            assertThat(reconciliation.fragmentSpans()).isEmpty();
            assertThat(reconciliation.fragmentSpansDropped()).containsExactly(fragmentSpan);
        }
    }

    static Stream<StraddleCase> straddleCases() {
        return Stream.of(
            new StraddleCase("a straddled tuplet is removed and the fragment's tuplets are dropped",
                PasteSpanReconciliationTest::quavers, PasteSpanReconciliationTest::tuplet,
                PasteSpanReconciliationTest::tuplet, true, false),
            new StraddleCase("a straddled beam is removed and the fragment's beams are dropped",
                PasteSpanReconciliationTest::quavers, line -> beamOver(line, wholeRangeOf(line)),
                line -> beamOver(line, wholeRangeOf(line)), true, false),
            new StraddleCase("a straddled trill is removed and the fragment's trill is kept",
                PasteSpanReconciliationTest::quavers, line -> trillOver(line, wholeRangeOf(line)),
                line -> trillOver(line, wholeRangeOf(line)), true, true),
            new StraddleCase("a straddled tuplet does not drop the fragment's beam",
                PasteSpanReconciliationTest::quavers, PasteSpanReconciliationTest::tuplet,
                line -> beamOver(line, wholeRangeOf(line)), true, true),
            new StraddleCase("a straddled crescendo is removed for a fragment diminuendo",
                PasteSpanReconciliationTest::quavers, line -> hairpin(line, Hairpin.Kind.CRESCENDO),
                line -> hairpin(line, Hairpin.Kind.DIMINUENDO), true, true),
            new StraddleCase("a straddled crescendo is kept and the fragment's crescendo is dropped",
                PasteSpanReconciliationTest::quavers, line -> hairpin(line, Hairpin.Kind.CRESCENDO),
                line -> hairpin(line, Hairpin.Kind.CRESCENDO), false, false),
            new StraddleCase("a straddled ending is kept and the fragment's endings are dropped",
                PasteSpanReconciliationTest::endingLine, line -> endingOver(line, wholeRangeOf(line)),
                line -> endingOver(line, wholeRangeOf(line)), false, false)
        );
    }

    /**
     * A tuplet, or a beam or a trill, pasted onto a line, and whether the destination keeps it.
     *
     * @param description the case, as the test's display name
     * @param elements    builds the elements of the copied line, all covered by the tuplet
     * @param refused     whether the destination refuses the tuplet
     */
    private record TupletPasteCase(String description, Supplier<List<StaffElement>> elements, boolean refused) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("tupletPasteCases")
    void testAPastedTupletIsKeptAsItIsUnlessItsPlacementIsBroken(TupletPasteCase testCase) {
        var source = lineOf(testCase.elements().get().toArray(StaffElement[]::new));
        tuplet(source).addTo(source);

        var destination = lineOf(quaver());
        var pasted = Fragment.capture(source, wholeRangeOf(source)).instantiate();
        var fragmentTuplet = pasted.spans().getFirst();

        var settled = paste(pasted, destination, destination.elementCount());

        if (testCase.refused()) {
            assertThat(settled.fragmentSpans()).isEmpty();
            assertThat(settled.fragmentSpansRejected()).containsExactly(new PasteSpanReconciliation.RejectedSpan(
                fragmentTuplet, Strings.DIALOG_REPORT_REASON_PASTE_TUPLET_REJECTED));
            return;
        }

        assertThat(settled.fragmentSpans()).containsExactly(fragmentTuplet);
        assertThat(settled.fragmentSpansRejected()).isEmpty();
    }

    static Stream<TupletPasteCase> tupletPasteCases() {
        return Stream.of(
            new TupletPasteCase("three quavers are kept",
                () -> List.of(quaver(), quaver(), quaver()), false),
            new TupletPasteCase("a barline inside the tuplet is refused",
                () -> List.of(quaver(), singleBarline(), quaver(), quaver()), true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unjudgedSpans")
    void testAPastedBeamOrTrillIsAddedAsTheFragmentsOwnSpan(String description, Function<Line, Span<?>> kind) {
        var source = quavers();
        kind.apply(source).addTo(source);

        var destination = lineOf(quaver());
        var pasted = Fragment.capture(source, wholeRangeOf(source)).instantiate();
        var fragmentSpan = pasted.spans().getFirst();

        var settled = paste(pasted, destination, destination.elementCount());

        assertThat(settled.fragmentSpans()).containsExactly(fragmentSpan);
        assertThat(settled.fragmentSpansRejected()).isEmpty();
    }

    static Stream<Arguments> unjudgedSpans() {
        return Stream.of(
            Arguments.of("a beam", (Function<Line, Span<?>>) line -> beamOver(line, wholeRangeOf(line))),
            Arguments.of("a trill", (Function<Line, Span<?>>) line -> trillOver(line, wholeRangeOf(line)))
        );
    }

    private static Line quavers() {
        var elements = Stream.generate(StaffElementFactory::quaver)
            .limit(STRADDLE_LINE_LENGTH)
            .toArray(StaffElement[]::new);

        return lineOf(elements);
    }

    /** A line holding the shape an ending covers whole: a note, a repeat, a note and a barline. */
    private static Line endingLine() {
        return lineOf(crotchet(), repeatRight(), crotchet(), singleBarline());
    }

    private static Tuplet tuplet(Line line) {
        return tripletOver(line, wholeRangeOf(line), NoteValue.EIGHTH);
    }

    private static Hairpin hairpin(Line line, Hairpin.Kind kind) {
        return hairpinOver(line, kind, wholeRangeOf(line));
    }

    /**
     * Pastes {@code fragment} at {@code insertIndex} of {@code destination}, up to the point
     * where the paste would add its spans, and returns the settled reconciliation.
     */
    private static PasteSpanReconciliation paste(Fragment fragment, Line destination, int insertIndex) {
        var target = new StaffElementRun.InsertionTarget.AtIndex(insertIndex);
        var reconciliation = PasteSpanReconciliation.reconcile(destination, target, fragment.spans());

        destination.insertRun(insertIndex, fragment.elements());

        return reconciliation.settleAgainstTarget(destination);
    }

    private static Line lineOf(StaffElement... elements) {
        var line = editableLine();

        for (var element : List.of(elements)) {
            line.addElement(element);
        }

        return line;
    }

    private static ElementRange wholeRangeOf(Line line) {
        return new ElementRange(0, line.elementCount() - 1);
    }
}
