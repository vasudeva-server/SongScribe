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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.SongFactory.editableLine;
import static songscribe.dom.SongFactory.lineOfQuavers;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * What a span kind promises about the same-kind spans already on its line when it is added. A
 * kind does one of three things with a span it meets: a beam and a hairpin absorb it, a tuplet
 * and a trill remove it, an ending stands beside it. A kind whose two touching spans are one
 * gesture — a hairpin — treats a span flush against it as one it meets; every other kind leaves
 * that span standing. A hairpin meets only hairpins of its own kind.
 *
 * <p>A deletion that leaves two same-kind hairpins touching joins them by the same reach, unless
 * the joined hairpin would share more than one element with a hairpin of the opposite kind. A
 * deletion that takes every element of a span takes the span with them, unreported, whatever its
 * kind.
 */
class LineSpansTest extends UnitTest {

    /** What a line holds of a kind once a second span of it is added. */
    private enum Outcome {
        /** One span over both extents. */
        MERGED,
        /** Only the span just added. */
        ONLY_THE_NEW,
        /** Both spans, as they were added. */
        BOTH
    }

    /**
     * One span added to a line already holding one of its kind.
     *
     * @param description the case, as the test's display name
     * @param kind        the kind's concrete class
     * @param line        builds the line the two spans go on
     * @param newExisting builds the span already on the line, over an anchor and an end element
     * @param newAdded    builds the span under test, over an anchor and an end element
     * @param existing    the extent of the span already on the line
     * @param added       the extent of the span under test
     * @param outcome     what the line then holds of the kind
     */
    private record DisplacementCase(
        String description,
        Class<? extends Span<?>> kind,
        Supplier<Line> line,
        BiFunction<StaffElement, StaffElement, Span<?>> newExisting,
        BiFunction<StaffElement, StaffElement, Span<?>> newAdded,
        ElementRange existing,
        ElementRange added,
        Outcome outcome
    ) {

        /** A case whose two spans are built the same way. */
        DisplacementCase(
            String description,
            Class<? extends Span<?>> kind,
            Supplier<Line> line,
            BiFunction<StaffElement, StaffElement, Span<?>> newSpan,
            ElementRange existing,
            ElementRange added,
            Outcome outcome
        ) {
            this(description, kind, line, newSpan, newSpan, existing, added, outcome);
        }
    }

    private static final int LINE_LENGTH = 6;

    /** The span already on the line when the one under test is added. */
    private static final ElementRange EXISTING = new ElementRange(0, 2);

    /** A span sharing its first element with {@link #EXISTING}'s last. */
    private static final ElementRange OVERLAPPING = new ElementRange(EXISTING.end(), LINE_LENGTH - 1);

    /**
     * A span sharing its first element with {@link #EXISTING}'s last, and as long as
     * {@link #EXISTING}: the three notes a triplet of quavers needs.
     */
    private static final ElementRange OVERLAPPING_SAME_LENGTH =
        new ElementRange(EXISTING.end(), EXISTING.end() + EXISTING.end() - EXISTING.begin());

    /** A span beginning just past {@link #EXISTING}, within the reach that counts as touching. */
    private static final ElementRange FLUSH =
        new ElementRange(EXISTING.end() + SPAN_ADJACENCY_REACH, LINE_LENGTH - 1);

    /** {@link #OVERLAPPING} less its last element: a span that starts past the line's first element. */
    private static final ElementRange INNER = new ElementRange(OVERLAPPING.begin(), OVERLAPPING.end() - 1);

    /** A span starting inside {@link #INNER} and running past its end. */
    private static final ElementRange PAST_INNER = new ElementRange(INNER.begin() + 1, OVERLAPPING.end());

    private static final int ENDING_LINE_LENGTH = 10;

    /**
     * The ending already on the line. An ending needs a note in each bracket around its split,
     * so it cannot be as short as {@link #EXISTING}.
     */
    private static final ElementRange EXISTING_ENDING = new ElementRange(0, 3);

    /**
     * The left repeat between {@link #EXISTING_ENDING} and {@link #SEPARATED_ENDING}. It opens a
     * section for the later ending's split to close: without it the walk back from that split
     * meets the earlier ending's split first.
     */
    private static final int SECTION_OPENER_INDEX = EXISTING_ENDING.end() + 1;

    /**
     * An ending on the far side of {@link #SECTION_OPENER_INDEX}. A note stands between the
     * opener and its anchor, because a note anchor cannot follow a repeat directly.
     */
    private static final ElementRange SEPARATED_ENDING =
        new ElementRange(SECTION_OPENER_INDEX + 2, ENDING_LINE_LENGTH - 1);

    /**
     * The indices of the repeats that give {@link #EXISTING_ENDING} and
     * {@link #SEPARATED_ENDING} each the split an ending holds strictly inside it.
     */
    private static final List<Integer> ENDING_SPLIT_INDICES =
        List.of(EXISTING_ENDING.begin() + 1, SEPARATED_ENDING.begin() + 1);

    @ParameterizedTest(name = "{0}")
    @MethodSource("displacementCases")
    void testASpanAbsorbsRemovesOrJoinsTheSpansOfItsKindAsItsKindDeclares(DisplacementCase testCase) {
        var line = testCase.line().get();
        add(line, testCase.newExisting(), testCase.existing());

        add(line, testCase.newAdded(), testCase.added());

        var expected = switch (testCase.outcome()) {
            case MERGED -> List.of(new ElementRange(
                Math.min(testCase.existing().begin(), testCase.added().begin()),
                Math.max(testCase.existing().end(), testCase.added().end())));
            case ONLY_THE_NEW -> List.of(testCase.added());
            case BOTH -> List.of(testCase.existing(), testCase.added());
        };

        assertThat(rangesOfKind(line, testCase)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void testCasesCoverEveryConcreteSpanKind() {
        assertThat(displacementCases().<Class<?>>map(DisplacementCase::kind).distinct())
            .containsExactlyInAnyOrderElementsOf(leavesOf(Span.class));
        assertThat(wholeSpanDeletionCases().<Class<?>>map(WholeSpanDeletionCase::kind).distinct())
            .containsExactlyInAnyOrderElementsOf(leavesOf(Span.class));
    }

    static Stream<DisplacementCase> displacementCases() {
        return Stream.of(
            new DisplacementCase("a beam absorbs the beam it overlaps",
                Beam.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newBeam,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a beam absorbs the beam its end falls inside",
                Beam.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newBeam,
                OVERLAPPING, EXISTING, Outcome.MERGED),
            new DisplacementCase("a beam absorbs the beam its start falls inside",
                Beam.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newBeam,
                INNER, PAST_INNER, Outcome.MERGED),
            new DisplacementCase("a beam leaves the beam flush against it standing",
                Beam.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newBeam,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("a crescendo absorbs the crescendo it overlaps",
                Hairpin.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newCrescendo,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a crescendo absorbs the crescendo flush against its start",
                Hairpin.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newCrescendo,
                EXISTING, FLUSH, Outcome.MERGED),
            new DisplacementCase("a crescendo absorbs the crescendo flush against its end",
                Hairpin.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newCrescendo,
                FLUSH, EXISTING, Outcome.MERGED),
            new DisplacementCase("a diminuendo absorbs the diminuendo it overlaps",
                Hairpin.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newDiminuendo,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a diminuendo absorbs the diminuendo flush against its start",
                Hairpin.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newDiminuendo,
                EXISTING, FLUSH, Outcome.MERGED),
            new DisplacementCase("a tuplet removes the tuplet it overlaps",
                Tuplet.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newTriplet,
                EXISTING, OVERLAPPING_SAME_LENGTH, Outcome.ONLY_THE_NEW),
            new DisplacementCase("a tuplet leaves the tuplet flush against it standing",
                Tuplet.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newTriplet,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("a trill removes the trill it overlaps",
                Trill.class, () -> lineOfQuavers(LINE_LENGTH), Trill::new,
                EXISTING, OVERLAPPING, Outcome.ONLY_THE_NEW),
            new DisplacementCase("a trill leaves the trill flush against it standing",
                Trill.class, () -> lineOfQuavers(LINE_LENGTH), Trill::new,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("an ending stands beside the ending a repeat opening separates it from",
                Ending.class, LineSpansTest::lineWithRepeatSplits, LineSpansTest::newEnding,
                EXISTING_ENDING, SEPARATED_ENDING, Outcome.BOTH)
        );
    }

    // ------------------------------------------------------------------------
    // Hairpins across a deletion
    // ------------------------------------------------------------------------

    /** The element between {@link #EXISTING} and the crescendo after it, which each case deletes. */
    private static final int DELETED_INDEX = EXISTING.end() + 1;

    /** A crescendo the deletion leaves touching {@link #EXISTING}. */
    private static final ElementRange ONE_ELEMENT_ON = new ElementRange(DELETED_INDEX + 1, DELETED_INDEX + 2);

    /** A crescendo the deletion leaves one surviving element away from {@link #EXISTING}. */
    private static final ElementRange TWO_ELEMENTS_ON = new ElementRange(DELETED_INDEX + 2, DELETED_INDEX + 3);

    /** The first element of {@link #FAR}: one past the reach of {@link #ONE_ELEMENT_ON}'s end. */
    private static final int FAR_BEGIN = ONE_ELEMENT_ON.end() + SPAN_ADJACENCY_REACH + 1;

    /** A crescendo the deletion leaves apart from {@link #ONE_ELEMENT_ON}. */
    private static final ElementRange FAR = new ElementRange(FAR_BEGIN, FAR_BEGIN + 1);

    /** A crescendo whose first note is the deleted element. */
    private static final ElementRange FROM_DELETED = new ElementRange(DELETED_INDEX, ONE_ELEMENT_ON.end());

    private static final int HAIRPIN_LINE_LENGTH = FAR.end() + 1;

    private static final int ONE_PLACE_BACK = -1;

    /** {@link #EXISTING} and {@link #ONE_ELEMENT_ON} as one crescendo, once the deletion leaves them touching. */
    private static final ElementRange JOINED =
        new ElementRange(EXISTING.begin(), ONE_ELEMENT_ON.shiftedBy(ONE_PLACE_BACK).end());

    /** The deletion most cases make: the element at {@link #DELETED_INDEX}. */
    private static final ElementRange ONE_DELETED = ElementRange.single(DELETED_INDEX);

    // Two cases put a hairpin of one kind between two of the other, each sharing one endpoint
    // with it, and delete from the line until the outer two touch.

    /** The middle hairpin of the first case: it shares {@link #EXISTING}'s end. */
    private static final ElementRange MIDDLE = new ElementRange(2, 5);

    /** The interior of {@link #MIDDLE}, which the first case deletes. */
    private static final ElementRange MIDDLE_INTERIOR = new ElementRange(3, 4);

    /** The last hairpin of both cases: it shares {@link #MIDDLE}'s end. */
    private static final ElementRange LAST = new ElementRange(5, 7);

    /** {@link #MIDDLE} once its interior is deleted; also the first hairpin of the second case. */
    private static final ElementRange MIDDLE_LEFT = new ElementRange(2, 3);

    /** {@link #LAST} once {@link #MIDDLE_INTERIOR} is deleted; also the middle hairpin of the second case. */
    private static final ElementRange LAST_LEFT = new ElementRange(3, 5);

    /** The element strictly inside {@link #LAST_LEFT}, which the second case deletes. */
    private static final ElementRange LAST_LEFT_INTERIOR = ElementRange.single(4);

    /** The first element of the line, which the first-note deletion row deletes. */
    private static final ElementRange FIRST_ELEMENT = ElementRange.single(EXISTING.begin());

    private static final double FIRST_HAIRPIN_Y_OFFSET_SS = 0.5;

    /** A hairpin of {@code kind} over {@code range}. */
    private record PlacedHairpin(Hairpin.Kind kind, ElementRange range) {}

    private static PlacedHairpin crescendo(ElementRange range) {
        return new PlacedHairpin(Hairpin.Kind.CRESCENDO, range);
    }

    private static PlacedHairpin diminuendo(ElementRange range) {
        return new PlacedHairpin(Hairpin.Kind.DIMINUENDO, range);
    }

    /**
     * A deletion from a line of hairpins.
     *
     * @param description the case, as the test's display name
     * @param hairpins    the hairpins before the first deletion, in the order they are added
     * @param deletions   the elements each deletion takes, in the order they are made
     * @param expected    the hairpins the line holds afterwards, in any order
     */
    private record HairpinDeletionCase(
        String description, List<PlacedHairpin> hairpins, List<ElementRange> deletions, List<PlacedHairpin> expected
    ) {

        /** A case with one deletion. */
        HairpinDeletionCase(
            String description, List<PlacedHairpin> hairpins, ElementRange deleted, List<PlacedHairpin> expected
        ) {
            this(description, hairpins, List.of(deleted), expected);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hairpinDeletionCases")
    void testADeletionJoinsTheSameKindHairpinsItLeavesTouchingUnlessTheJoinCoversAnOppositeOne(
        HairpinDeletionCase testCase
    ) {
        var line = lineOfQuavers(HAIRPIN_LINE_LENGTH);
        testCase.hairpins().forEach(placed -> hairpinOver(line, placed.kind(), placed.range()).addTo());

        testCase.deletions().forEach(deleted -> line.deleteRange(line.effectiveRange(deleted)));

        assertThat(placedHairpins(line)).containsExactlyInAnyOrderElementsOf(testCase.expected());
    }

    static Stream<HairpinDeletionCase> hairpinDeletionCases() {
        return Stream.of(
            new HairpinDeletionCase("two crescendos left touching become one over both",
                List.of(crescendo(EXISTING), crescendo(ONE_ELEMENT_ON)), ONE_DELETED,
                List.of(crescendo(JOINED))),
            new HairpinDeletionCase("crescendos added in the reverse of their order on the line join from the earlier start",
                List.of(crescendo(ONE_ELEMENT_ON), crescendo(EXISTING)), ONE_DELETED,
                List.of(crescendo(JOINED))),
            new HairpinDeletionCase("one surviving element between them keeps them apart",
                List.of(crescendo(EXISTING), crescendo(TWO_ELEMENTS_ON)), ONE_DELETED,
                List.of(crescendo(EXISTING), crescendo(TWO_ELEMENTS_ON.shiftedBy(ONE_PLACE_BACK)))),
            new HairpinDeletionCase("each run left touching becomes one, apart from the next run",
                List.of(crescendo(EXISTING), crescendo(ONE_ELEMENT_ON), crescendo(FAR)), ONE_DELETED,
                List.of(crescendo(JOINED), crescendo(FAR.shiftedBy(ONE_PLACE_BACK)))),
            new HairpinDeletionCase("a crescendo that loses its first note starts on the note after it",
                List.of(crescendo(FROM_DELETED)), ONE_DELETED,
                List.of(crescendo(new ElementRange(DELETED_INDEX, FROM_DELETED.end() + ONE_PLACE_BACK)))),
            new HairpinDeletionCase("two crescendos left touching across a diminuendo stay apart",
                List.of(crescendo(EXISTING), diminuendo(MIDDLE), crescendo(LAST)), MIDDLE_INTERIOR,
                List.of(crescendo(EXISTING), diminuendo(MIDDLE_LEFT), crescendo(LAST_LEFT))),
            new HairpinDeletionCase(
                "deleting the first note of a crescendo that touches another across a diminuendo leaves them apart",
                List.of(crescendo(EXISTING), diminuendo(MIDDLE), crescendo(LAST)),
                List.of(MIDDLE_INTERIOR, FIRST_ELEMENT),
                List.of(
                    crescendo(new ElementRange(EXISTING.begin(), EXISTING.end() + ONE_PLACE_BACK)),
                    diminuendo(MIDDLE_LEFT.shiftedBy(ONE_PLACE_BACK)),
                    crescendo(LAST_LEFT.shiftedBy(ONE_PLACE_BACK)))),
            new HairpinDeletionCase("two diminuendos left touching across a shortened crescendo stay apart",
                List.of(diminuendo(MIDDLE_LEFT), crescendo(LAST_LEFT), diminuendo(LAST)), LAST_LEFT_INTERIOR,
                List.of(
                    diminuendo(MIDDLE_LEFT),
                    crescendo(new ElementRange(LAST_LEFT.begin(), LAST_LEFT.end() + ONE_PLACE_BACK)),
                    diminuendo(LAST.shiftedBy(ONE_PLACE_BACK))))
        );
    }

    /** Every hairpin on {@code line}, as its kind and extent. */
    private static List<PlacedHairpin> placedHairpins(Line line) {
        return line.spanRegistry().findSpans(Hairpin.class).stream()
            .map(hairpin -> new PlacedHairpin(hairpin.getKind(), hairpin.range()))
            .toList();
    }

    @Test
    void testTheJoinedHairpinCarriesTheFirstOnesOffsets() {
        var line = lineOfQuavers(HAIRPIN_LINE_LENGTH);
        var first = hairpinOver(line, Hairpin.Kind.CRESCENDO, EXISTING);
        first.setUserYOffsetSs(FIRST_HAIRPIN_Y_OFFSET_SS);
        first.addTo();
        hairpinOver(line, Hairpin.Kind.CRESCENDO, ONE_ELEMENT_ON).addTo();

        deleteElement(line, DELETED_INDEX);

        assertThat(line.spanRegistry().getSpans()).singleElement()
            .isInstanceOfSatisfying(Hairpin.class,
                joined -> assertThat(joined.getUserYOffsetSs()).isEqualTo(FIRST_HAIRPIN_Y_OFFSET_SS));
    }

    @Test
    void testAHairpinWhoseEndpointsBothSurviveADeletionIsLeftAsItIs() {
        var line = lineOfQuavers(HAIRPIN_LINE_LENGTH);
        var hairpin = hairpinOver(line, Hairpin.Kind.CRESCENDO, EXISTING);
        hairpin.addTo();

        deleteElement(line, DELETED_INDEX);

        assertThat(line.spanRegistry().getSpans())
            .as("the same hairpin, not a copy of it")
            .containsExactly(hairpin);
    }

    // ------------------------------------------------------------------------
    // A deletion that takes a whole span
    // ------------------------------------------------------------------------

    /**
     * One span of a kind, on a line of a real song, whose every element is then deleted.
     *
     * @param description the case, as the test's display name
     * @param kind        the kind's concrete class
     * @param line        builds the line, on a song whose tracking is suspended
     * @param newSpan     builds the span, over an anchor and an end element
     * @param extent      the elements the span covers, all of which the deletion takes
     */
    private record WholeSpanDeletionCase(
        String description,
        Class<? extends Span<?>> kind,
        Supplier<Line> line,
        BiFunction<StaffElement, StaffElement, Span<?>> newSpan,
        ElementRange extent
    ) {}

    static Stream<WholeSpanDeletionCase> wholeSpanDeletionCases() {
        return Stream.of(
            new WholeSpanDeletionCase("a beam",
                Beam.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newBeam, EXISTING),
            new WholeSpanDeletionCase("a crescendo",
                Hairpin.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newCrescendo, EXISTING),
            new WholeSpanDeletionCase("a tuplet",
                Tuplet.class, () -> lineOfQuavers(LINE_LENGTH), LineSpansTest::newTriplet, EXISTING),
            new WholeSpanDeletionCase("a trill",
                Trill.class, () -> lineOfQuavers(LINE_LENGTH), Trill::new, EXISTING),
            new WholeSpanDeletionCase("an ending",
                Ending.class, LineSpansTest::lineWithRepeatSplits, LineSpansTest::newEnding, EXISTING_ENDING)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wholeSpanDeletionCases")
    void testADeletionThatTakesEveryElementOfASpanRemovesItUnreported(WholeSpanDeletionCase testCase) {
        var line = testCase.line().get();
        var song = line.getSong();
        add(line, testCase.newSpan(), testCase.extent());
        song.endSuspendMutationTracking();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> line.deleteRange(line.effectiveRange(testCase.extent())));

            assertThat(rangesOf(line, testCase.kind())).isEmpty();
            assertThat(reports.reports()).as("the user deleted the span, so nothing is reported").isEmpty();
        });
    }

    private static void deleteElement(Line line, int index) {
        line.deleteRange(line.effectiveRange(ElementRange.single(index)));
    }

    /** The classes a sealed hierarchy bottoms out in, reached through every sealed level. */
    private static List<Class<?>> leavesOf(Class<?> sealedRoot) {
        var permitted = sealedRoot.getPermittedSubclasses();

        if (permitted == null) {
            return List.of(sealedRoot);
        }

        var leaves = new ArrayList<Class<?>>();

        for (var subclass : permitted) {
            leaves.addAll(leavesOf(subclass));
        }

        return leaves;
    }

    /**
     * A line of a real song, of quavers with a right repeat at each of
     * {@link #ENDING_SPLIT_INDICES} and a left repeat at {@link #SECTION_OPENER_INDEX}.
     */
    private static Line lineWithRepeatSplits() {
        var line = editableLine();

        for (var i = 0; i < ENDING_LINE_LENGTH; i++) {
            line.addElement(elementAt(i));
        }

        return line;
    }

    private static StaffElement elementAt(int index) {
        if (ENDING_SPLIT_INDICES.contains(index)) {
            return repeatRight();
        }

        if (index == SECTION_OPENER_INDEX) {
            return repeatLeft();
        }

        return quaver();
    }

    private static Span<?> newCrescendo(StaffElement anchor, StaffElement end) {
        return newHairpin(Hairpin.Kind.CRESCENDO, anchor, end);
    }

    private static Span<?> newDiminuendo(StaffElement anchor, StaffElement end) {
        return newHairpin(Hairpin.Kind.DIMINUENDO, anchor, end);
    }

    private static Span<?> newHairpin(Hairpin.Kind kind, StaffElement anchor, StaffElement end) {
        return over(anchor, end, (line, range) -> hairpinOver(line, kind, range));
    }

    private static Span<?> newBeam(StaffElement anchor, StaffElement end) {
        return over(anchor, end, SpanFactory::beamOver);
    }

    private static Span<?> newTriplet(StaffElement anchor, StaffElement end) {
        return over(anchor, end, (line, range) -> tripletOver(line, range, NoteValue.EIGHTH));
    }

    private static Span<?> newEnding(StaffElement anchor, StaffElement end) {
        return over(anchor, end, SpanFactory::endingOver);
    }

    /** The span {@code build} makes over the range from {@code anchor} to {@code end}, on their line. */
    private static Span<?> over(
        StaffElement anchor, StaffElement end, BiFunction<Line, ElementRange, Span<?>> build
    ) {
        var line = anchor.getParentLine();

        assertThat(line).isNotNull();

        return build.apply(line, new ElementRange(line.getElementIndex(anchor), line.getElementIndex(end)));
    }

    private static void add(Line line, BiFunction<StaffElement, StaffElement, Span<?>> newSpan, ElementRange range) {
        newSpan.apply(line.getElement(range.begin()), line.getElement(range.end())).addTo();
    }

    /** The extents of every span on {@code line} of the case's kind, in no defined order. */
    private static List<ElementRange> rangesOfKind(Line line, DisplacementCase testCase) {
        return rangesOf(line, testCase.kind());
    }

    /** The extents of every span on {@code line} of {@code kind}, in no defined order. */
    private static List<ElementRange> rangesOf(Line line, Class<? extends Span<?>> kind) {
        return line.spanRegistry().getSpans().stream()
            .filter(kind::isInstance)
            .map(Span::range)
            .toList();
    }
}
