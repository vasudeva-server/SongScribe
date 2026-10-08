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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import net.engio.mbassy.listener.Handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementLocationRange;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.NoteValue;
import songscribe.dom.ReportedChange;
import songscribe.dom.Span;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.StaffElementRun.InsertionTarget;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletValidator;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.ChangesWereReportedNotification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static songscribe.dom.SongFactory.DOTTED_BEAT_TEMPO;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The two rules of {@link PasteSpanReconciliation}, as the paste runs them.
 *
 * <p>The straddle rule ({@link PasteSpanReconciliation#reconcile}): a destination span that the
 * paste lands inside of is removed or kept by its kind, and the fragment's spans of the kinds
 * that decides are dropped. It is asserted on the reconciliation itself, which is pure.
 *
 * <p>The target rule ({@link PasteSpanReconciliation#settle}): a pasted ending or hairpin is
 * judged by its kind's own rule against the line as the paste leaves it, and so is a tuplet or a
 * beam. An accepted span is added as the one its rule built, carrying the pasted span's user
 * state, and a refused one is not added and is reported with the rule's reason. A trill has no
 * rule, and is added as the fragment's own. These run through {@link FragmentPaste#paste}, inside
 * the modification bracket a paste runs in, so what is asserted is what the song holds and what
 * the paste reports.
 *
 * <p>The tables of both rules are pinned to the kinds {@link Span} permits, so a new kind fails a
 * test until a row says what each rule does with it.
 */
class PasteSpanReconciliationTest extends UnitTest {

    private static final double USER_X_OFFSET_SS = 0.25;
    private static final double USER_Y_OFFSET_SS = -1.5;

    private static final String EMPTY_LINE = "";

    /** The notation of a line that holds the shape an ending covers whole. */
    private static final String ENDING_LINE = "6 :| 6 |";

    private static final String QUAVER_TOKEN = "Q";
    private static final String THREE_QUAVERS = "Q Q Q";
    private static final String FOUR_QUAVERS = "Q Q Q Q";

    /** The index a fragment lands at inside a destination span that covers the whole line. */
    private static final int STRADDLE_INDEX = 2;

    private static final int FIRST_LINE = 0;

    // ---- The straddle rule ----

    /**
     * A destination span that a paste lands inside of, and a span of the fragment pasted into it.
     *
     * @param description     the case, as the test's display name
     * @param destinationKind the kind of the destination's span
     * @param notation        the compact notation of the line both spans cover in full
     * @param destinationSpan builds the destination's span over the whole of a line
     * @param fragmentSpan    builds the fragment's span over the whole of a line
     */
    private record StraddleCase(
        String description,
        Class<?> destinationKind,
        String notation,
        Function<Line, Span<?>> destinationSpan,
        Function<Line, Span<?>> fragmentSpan
    ) {}

    /** What reconciling a {@link StraddleCase} gave, with the two spans it was about. */
    private record Straddled(Span<?> destinationSpan, Span<?> fragmentSpan, PasteSpanReconciliation reconciliation) {}

    private static Straddled reconcileStraddle(StraddleCase testCase) {
        var destination = lineOfNotation(testCase.notation());
        var destinationSpan = testCase.destinationSpan().apply(destination);
        addWithoutTracking(destination.getSong(), () -> destinationSpan);

        var source = lineOfNotation(testCase.notation());
        addWithoutTracking(source.getSong(), () -> testCase.fragmentSpan().apply(source));

        var pasted = Fragment.capture(source, wholeRangeOf(source)).instantiate();
        var fragmentSpan = pasted.spans().getFirst();

        var reconciliation = PasteSpanReconciliation.reconcile(
            destination, new InsertionTarget.AtIndex(STRADDLE_INDEX), pasted.spans());

        return new Straddled(destinationSpan, fragmentSpan, reconciliation);
    }

    private static PasteSpanReconciliation.LostSpan straddledAway(Span<?> span) {
        return new PasteSpanReconciliation.LostSpan(span, Strings.DIALOG_REPORT_REASON_PASTE_STRADDLED);
    }

    private static PasteSpanReconciliation.LostSpan droppedFromFragment(Span<?> span) {
        return new PasteSpanReconciliation.LostSpan(span, Strings.DIALOG_REPORT_REASON_PASTE_FRAGMENT_DROPPED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("destinationAndFragmentGoCases")
    void testAStraddledSpanIsRemovedAndTheFragmentsSpansOfItsKindAreDropped(StraddleCase testCase) {
        var straddled = reconcileStraddle(testCase);
        var reconciliation = straddled.reconciliation();

        assertThat(reconciliation.targetSpansToRemove()).containsExactly(straddledAway(straddled.destinationSpan()));
        assertThat(reconciliation.fragmentSpansKept()).isEmpty();
        assertThat(reconciliation.fragmentSpansDropped()).containsExactly(droppedFromFragment(straddled.fragmentSpan()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("destinationGoesFragmentStaysCases")
    void testAStraddledSpanIsRemovedAndTheFragmentsSpanIsKept(StraddleCase testCase) {
        var straddled = reconcileStraddle(testCase);
        var reconciliation = straddled.reconciliation();

        assertThat(reconciliation.targetSpansToRemove()).containsExactly(straddledAway(straddled.destinationSpan()));
        assertThat(reconciliation.fragmentSpansKept()).containsExactly(straddled.fragmentSpan());
        assertThat(reconciliation.fragmentSpansDropped()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("destinationStaysFragmentGoesCases")
    void testAStraddledSpanIsKeptAndTheFragmentsSpanIsDropped(StraddleCase testCase) {
        var straddled = reconcileStraddle(testCase);
        var reconciliation = straddled.reconciliation();

        assertThat(reconciliation.targetSpansToRemove()).isEmpty();
        assertThat(reconciliation.fragmentSpansKept()).isEmpty();
        assertThat(reconciliation.fragmentSpansDropped()).containsExactly(droppedFromFragment(straddled.fragmentSpan()));
    }

    @Test
    void testTheStraddleTablesCoverEveryKindOfSpan() {
        var kinds = Stream.of(
                destinationAndFragmentGoCases(), destinationGoesFragmentStaysCases(), destinationStaysFragmentGoesCases())
            .flatMap(cases -> cases)
            .map(StraddleCase::destinationKind)
            .distinct()
            .toList();

        assertThat(kinds).containsExactlyInAnyOrder(Span.class.getPermittedSubclasses());
    }

    /** Tuplets and beams are rhythmic groupings: a wrecked one and a pasted one both go. */
    static Stream<StraddleCase> destinationAndFragmentGoCases() {
        return Stream.of(
            new StraddleCase("a straddled tuplet is removed and the fragment's tuplets are dropped",
                Tuplet.class, THREE_QUAVERS, PasteSpanReconciliationTest::tuplet, PasteSpanReconciliationTest::tuplet),
            new StraddleCase("a straddled beam is removed and the fragment's beams are dropped",
                Beam.class, FOUR_QUAVERS, line -> beamOver(line, wholeRangeOf(line)),
                line -> beamOver(line, wholeRangeOf(line)))
        );
    }

    static Stream<StraddleCase> destinationGoesFragmentStaysCases() {
        var oppositeHairpins = Stream.of(Hairpin.Kind.values())
            .flatMap(destinationKind -> Stream.of(Hairpin.Kind.values())
                .filter(fragmentKind -> fragmentKind != destinationKind)
                .map(fragmentKind -> new StraddleCase(
                    "a straddled " + destinationKind + " is removed for a fragment " + fragmentKind,
                    Hairpin.class, FOUR_QUAVERS, line -> hairpin(line, destinationKind),
                    line -> hairpin(line, fragmentKind))));

        return Stream.concat(
            Stream.of(
                new StraddleCase("a straddled trill is removed and the fragment's trill is kept",
                    Trill.class, FOUR_QUAVERS, line -> trillOver(line, wholeRangeOf(line)),
                    line -> trillOver(line, wholeRangeOf(line))),
                new StraddleCase("a straddled tuplet does not drop the fragment's beam",
                    Tuplet.class, THREE_QUAVERS, PasteSpanReconciliationTest::tuplet,
                    line -> beamOver(line, wholeRangeOf(line)))
            ),
            oppositeHairpins);
    }

    static Stream<StraddleCase> destinationStaysFragmentGoesCases() {
        var sameHairpins = Stream.of(Hairpin.Kind.values())
            .map(kind -> new StraddleCase(
                "a straddled " + kind + " is kept and the fragment's " + kind + " is dropped",
                Hairpin.class, FOUR_QUAVERS, line -> hairpin(line, kind), line -> hairpin(line, kind)));

        return Stream.concat(
            Stream.of(new StraddleCase("a straddled ending is kept and the fragment's endings are dropped",
                Ending.class, ENDING_LINE, line -> endingOver(line, wholeRangeOf(line)),
                line -> endingOver(line, wholeRangeOf(line)))),
            sameHairpins);
    }

    // ---- The target rule ----

    /**
     * A span the target rule accepts, copied from a source line and pasted onto an empty line.
     *
     * @param description the case, as the test's display name
     * @param kind        the kind of the span
     * @param notation    the compact notation of the source line, which the span covers in full
     * @param span        builds the span over the whole of a line
     */
    private record AcceptedCase(
        String description,
        Class<?> kind,
        String notation,
        Function<Line, Span<?>> span
    ) {}

    /** What a paste of one span left in the song, with the fragment span it pasted. */
    private record Pasted(Span<?> fragmentSpan, List<Span<?>> spansOnLine, List<ReportedChange> reports) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedCases")
    void testAnAcceptedSpanIsAddedCarryingItsUserOffsetsAndNothingIsReported(AcceptedCase testCase) {
        var pasted = pasteOntoEmptyLine(testCase);

        assertThat(pasted.reports()).isEmpty();
        assertThat(pasted.spansOnLine()).singleElement()
            .isInstanceOf(testCase.kind())
            .satisfies(span -> {
                assertThat(span.getUserXOffsetSs()).isEqualTo(USER_X_OFFSET_SS);
                assertThat(span.getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
            });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factoryBuiltCases")
    void testASpanItsFactoryAcceptsIsAddedAsTheOneTheFactoryBuiltNotTheFragments(AcceptedCase testCase) {
        var pasted = pasteOntoEmptyLine(testCase);

        assertThat(pasted.spansOnLine()).singleElement().isNotSameAs(pasted.fragmentSpan());
    }

    @Test
    void testATrillIsAddedAsTheFragmentsOwnSpan() {
        var pasted = pasteOntoEmptyLine(trillCase());

        assertThat(pasted.spansOnLine()).singleElement().isSameAs(pasted.fragmentSpan());
    }

    @ParameterizedTest
    @EnumSource(Hairpin.Kind.class)
    void testAPastedHairpinKeepsItsDirection(Hairpin.Kind kind) {
        var pasted = pasteOntoEmptyLine(hairpinCase(kind));

        assertThat(pasted.spansOnLine()).singleElement()
            .isInstanceOfSatisfying(Hairpin.class, hairpin -> assertThat(hairpin.getKind()).isEqualTo(kind));
    }

    @Test
    void testTheAcceptedCasesCoverEveryKindOfSpan() {
        assertThat(acceptedCases().map(AcceptedCase::kind).distinct())
            .containsExactlyInAnyOrder(Span.class.getPermittedSubclasses());
    }

    static Stream<AcceptedCase> acceptedCases() {
        return Stream.concat(
            Stream.of(
                new AcceptedCase("an ending onto an empty line", Ending.class, ENDING_LINE,
                    line -> endingOver(line, wholeRangeOf(line))),
                new AcceptedCase("a tuplet of three quavers", Tuplet.class, THREE_QUAVERS,
                    PasteSpanReconciliationTest::tuplet),
                new AcceptedCase("a beam", Beam.class, THREE_QUAVERS, line -> beamOver(line, wholeRangeOf(line))),
                trillCase()),
            Stream.of(Hairpin.Kind.values()).map(PasteSpanReconciliationTest::hairpinCase));
    }

    static Stream<AcceptedCase> factoryBuiltCases() {
        return acceptedCases().filter(testCase -> testCase.kind() != Trill.class);
    }

    private static AcceptedCase trillCase() {
        return new AcceptedCase("a trill", Trill.class, THREE_QUAVERS, line -> trillOver(line, wholeRangeOf(line)));
    }

    private static AcceptedCase hairpinCase(Hairpin.Kind kind) {
        return new AcceptedCase("a " + kind, Hairpin.class, THREE_QUAVERS, line -> hairpin(line, kind));
    }

    /** The paste of one case's span, with the user offsets it carries, onto an empty line. */
    private static Pasted pasteOntoEmptyLine(AcceptedCase testCase) {
        var source = lineOfNotation(testCase.notation());
        var span = testCase.span().apply(source);
        span.setUserXOffsetSs(USER_X_OFFSET_SS);
        span.setUserYOffsetSs(USER_Y_OFFSET_SS);
        addWithoutTracking(source.getSong(), () -> span);

        var destination = lineOfNotation(EMPTY_LINE);

        return paste(Fragment.capture(source, wholeRangeOf(source)).instantiate(), destination, 0);
    }

    /**
     * An ending pasted into a destination that refuses it.
     *
     * @param description the case, as the test's display name
     * @param elements    the elements of the destination line
     * @param insertIndex where on the destination the fragment lands
     * @param refusalKey  the reason the destination refuses the ending
     */
    private record RefusedEndingCase(
        String description,
        Supplier<List<StaffElement>> elements,
        ToIntFunction<Line> insertIndex,
        String refusalKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedEndingCases")
    void testAnEndingTheDestinationRefusesIsNotAddedAndIsReportedWithTheRulesReason(RefusedEndingCase testCase) {
        var source = lineOfNotation(ENDING_LINE);
        addWithoutTracking(source.getSong(), () -> endingOver(source, wholeRangeOf(source)));

        var destination = lineOfNotation(EMPTY_LINE);
        destination.getSong().withoutMutationTracking(
            () -> lineOf(destination, testCase.elements().get().toArray(StaffElement[]::new)));

        var pasted = paste(
            Fragment.capture(source, wholeRangeOf(source)).instantiate(),
            destination,
            testCase.insertIndex().applyAsInt(destination));

        assertThat(pasted.spansOnLine()).isEmpty();
        assertThat(pasted.reports()).singleElement().satisfies(report -> {
            assertThat(report.headingKey()).isEqualTo(Strings.DIALOG_REPORT_ENDINGS_REMOVED);
            assertThat(report.description()).isEqualTo(Strings.get(testCase.refusalKey()));
        });
    }

    static Stream<RefusedEndingCase> refusedEndingCases() {
        return Stream.of(
            new RefusedEndingCase("after a double barline, its anchor is refused",
                () -> List.of(crotchet(), doubleBarline()), Line::elementCount,
                Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),
            new RefusedEndingCase("in front of a barline, its end bar is refused",
                () -> List.of(crotchet(), singleBarline()), line -> line.elementCount() - 1,
                Strings.DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END)
        );
    }

    // A tuplet the destination's beat splits: the source beat is already dotted where the tuplet
    // begins, so a later change to the dotted beat is no barrier there, and in a destination
    // running a crotchet beat it is one.

    private static final int BEAT_LEAD_INDEX = 0;
    private static final ElementRange FIRST_TRIPLET = new ElementRange(1, 3);
    private static final ElementRange SECOND_TRIPLET = new ElementRange(4, 6);

    /**
     * {@code length} quavers of a one-line song, each of {@code beatChangeIndices} carrying a
     * change to the dotted beat.
     */
    private static Line quaversChangingBeatAt(int length, int... beatChangeIndices) {
        var notation = String.join(" ", Collections.nCopies(length, QUAVER_TOKEN));
        var line = lineOfNotation(notation);

        line.getSong().withoutMutationTracking(() -> {
            for (var index : beatChangeIndices) {
                line.getElement(index).addAttachment(new TempoChangeAttachment(DOTTED_BEAT_TEMPO));
            }
        });

        return line;
    }

    /** The triplet over {@code range} of {@code source}, which the source's own beat accepts. */
    private static Tuplet strictlyValidTriplet(Line source, ElementRange range) {
        var tuplet = tripletOver(source, range, NoteValue.EIGHTH);
        addWithoutTracking(source.getSong(), () -> tuplet);

        assertThat(Tuplet.spanningStated(
            Tuplet.Site.at(source, range), tuplet.getGrade(), tuplet.getRatio(), TupletValidator.Strictness.STRICT))
            .as("the source's beat accepts the tuplet, so only the destination can refuse it")
            .isInstanceOf(Spanning.Spanned.class);

        return tuplet;
    }

    /** The range {@code original} lands at when only {@code captured} is pasted, at the line's start. */
    private static ElementRange landedAt(ElementRange original, ElementRange captured) {
        return new ElementRange(original.begin() - captured.begin(), original.end() - captured.begin());
    }

    private static ReportedChange beatBarrierReport(ElementRange landedAt) {
        return new ReportedChange(
            Strings.DIALOG_REPORT_TUPLETS_REMOVED,
            Strings.get(Strings.DIALOG_REPORT_REASON_BEAT_BARRIER),
            ElementLocationRange.onLine(FIRST_LINE, landedAt));
    }

    @Test
    void testATupletTheDestinationsBeatSplitsIsNotAddedAndIsReportedWithTheRulesReason() {
        var source = quaversChangingBeatAt(FIRST_TRIPLET.end() + 1, BEAT_LEAD_INDEX, FIRST_TRIPLET.end());
        strictlyValidTriplet(source, FIRST_TRIPLET);

        var pasted = paste(Fragment.capture(source, FIRST_TRIPLET).instantiate(), lineOfNotation(EMPTY_LINE), 0);

        assertThat(pasted.spansOnLine()).isEmpty();
        assertThat(pasted.reports())
            .containsExactly(beatBarrierReport(landedAt(FIRST_TRIPLET, FIRST_TRIPLET)));
    }

    @Test
    void testEverySpanOfAFragmentIsEitherAddedOrReportedNeverBoth() {
        var source = quaversChangingBeatAt(SECOND_TRIPLET.end() + 1, BEAT_LEAD_INDEX, SECOND_TRIPLET.end());
        strictlyValidTriplet(source, FIRST_TRIPLET);
        strictlyValidTriplet(source, SECOND_TRIPLET);
        var captured = new ElementRange(FIRST_TRIPLET.begin(), SECOND_TRIPLET.end());

        var pasted = paste(Fragment.capture(source, captured).instantiate(), lineOfNotation(EMPTY_LINE), 0);

        assertThat(pasted.spansOnLine()).singleElement().isInstanceOfSatisfying(Tuplet.class, tuplet ->
            assertThat(tuplet.range()).isEqualTo(landedAt(FIRST_TRIPLET, captured)));
        assertThat(pasted.reports()).containsExactly(beatBarrierReport(landedAt(SECOND_TRIPLET, captured)));
    }

    // ---- A span with an endpoint on a removed key-change barline ----

    private static final Key RUNNING_KEY = Key.TWO_SHARPS;

    /** The index of the barline in front of the key change, where the stranded span ends. */
    private static final int BARLINE_INDEX = 3;

    /**
     * A fragment span that ends on the barline in front of a key change the destination's key
     * makes redundant.
     *
     * @param description the case, as the test's display name
     * @param notation    the compact notation of the source line
     * @param span        builds the span over the range up to and including the barline
     * @param headingKey  the heading the span's report entry is under
     */
    private record StrandedCase(String description, String notation, Function<Line, Span<?>> span, String headingKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("strandedCases")
    void testASpanEndingOnARemovedKeyChangeBarlineIsDroppedAndReported(StrandedCase testCase) {
        var source = lineOfNotation(testCase.notation());
        addWithoutTracking(source.getSong(), () -> testCase.span().apply(source));

        var song = compactSong(RUNNING_KEY, EMPTY_LINE, EMPTY_LINE, EMPTY_LINE);
        var destination = song.getLine(FIRST_LINE);

        var pasted = paste(Fragment.capture(source, wholeRangeOf(source)).instantiate(), destination, 0);

        assertThat(pasted.spansOnLine()).isEmpty();
        assertThat(pasted.reports())
            .extracting(ReportedChange::headingKey, ReportedChange::description, ReportedChange::where)
            .containsExactly(tuple(
                testCase.headingKey(),
                Strings.get(Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED),
                ElementLocationRange.onLine(FIRST_LINE, new ElementRange(0, BARLINE_INDEX - 1))));
    }

    static Stream<StrandedCase> strandedCases() {
        var range = new ElementRange(0, BARLINE_INDEX);

        return Stream.of(
            new StrandedCase("an ending", "6 :| 6 | K:" + RUNNING_KEY.name() + " 6",
                line -> endingOver(line, range), Strings.DIALOG_REPORT_ENDINGS_REMOVED),
            new StrandedCase("a tuplet", "Q Q Q | K:" + RUNNING_KEY.name() + " Q",
                line -> tripletOver(line, range, NoteValue.EIGHTH), Strings.DIALOG_REPORT_TUPLETS_REMOVED)
        );
    }

    // ---- Fixtures ----

    private static Line lineOfNotation(String notation) {
        return compactSong(Key.NO_ACCIDENTALS, notation, EMPTY_LINE, EMPTY_LINE).getLine(FIRST_LINE);
    }

    private static Tuplet tuplet(Line line) {
        return tripletOver(line, wholeRangeOf(line), NoteValue.EIGHTH);
    }

    private static Hairpin hairpin(Line line, Hairpin.Kind kind) {
        return hairpinOver(line, kind, wholeRangeOf(line));
    }

    /**
     * Pastes {@code fragment} at {@code insertIndex} of {@code destination}, as the paste does:
     * inside a modification bracket, reduced for the key running there.
     *
     * @return the spans the destination holds afterwards, and what the paste reported
     */
    private static Pasted paste(Fragment fragment, Line destination, int insertIndex) {
        var fragmentSpan = fragment.spans().getFirst();
        var recorder = new ReportRecorder();

        try {
            destination.getSong().withModification(() -> FragmentPaste.paste(
                destination,
                new InsertionTarget.AtIndex(insertIndex),
                fragment.withoutRedundantKeyChanges(destination.keyBefore(insertIndex))));
        } finally {
            recorder.dispose();
        }

        return new Pasted(fragmentSpan, List.copyOf(destination.spanRegistry().getSpans()), recorder.entries());
    }

    /** Takes the entries of every change report the edit delivers on the class's bus. */
    static final class ReportRecorder {
        private final List<ReportedChange> entries = new ArrayList<>();
        private final MessageSubscription subscription = new MessageSubscription(this);

        @Handler
        void changesWereReported(ChangesWereReportedNotification notification) {
            entries.addAll(notification.getReport().changes());
        }

        List<ReportedChange> entries() {
            return entries;
        }

        void dispose() {
            subscription.dispose();
        }
    }
}
