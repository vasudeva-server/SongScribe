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
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * What a span kind promises about the same-kind spans already on its line when it is added. A
 * kind does one of three things with a span it meets: a beam and a hairpin absorb it, a tuplet
 * and a trill remove it, an ending stands beside it. A kind whose two touching spans are one
 * gesture — a hairpin — treats a span flush against it as one it meets; every other kind leaves
 * that span standing. A hairpin meets only hairpins of its own kind.
 *
 * <p>A deletion that leaves two same-kind hairpins touching joins them by the same reach. Every
 * edit a line makes asks its spans first, an insertion and an in-place modification as well as a
 * deletion, which is what removes a tuplet the edit leaves rhythmically invalid.
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
        Class<? extends Span> kind,
        Supplier<Line> line,
        BiFunction<StaffElement, StaffElement, Span> newExisting,
        BiFunction<StaffElement, StaffElement, Span> newAdded,
        ElementRange existing,
        ElementRange added,
        Outcome outcome
    ) {

        /** A case whose two spans are built the same way. */
        DisplacementCase(
            String description,
            Class<? extends Span> kind,
            Supplier<Line> line,
            BiFunction<StaffElement, StaffElement, Span> newSpan,
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

    /** A span beginning just past {@link #EXISTING}, within the reach that counts as touching. */
    private static final ElementRange FLUSH =
        new ElementRange(EXISTING.end() + SPAN_ADJACENCY_REACH, LINE_LENGTH - 1);

    /** {@link #OVERLAPPING} less its last element: a span that starts past the line's first element. */
    private static final ElementRange INNER = new ElementRange(OVERLAPPING.begin(), OVERLAPPING.end() - 1);

    /** A span starting inside {@link #INNER} and running past its end. */
    private static final ElementRange PAST_INNER = new ElementRange(INNER.begin() + 1, OVERLAPPING.end());

    /**
     * The indices of the repeats that give {@link #EXISTING}, {@link #OVERLAPPING} and
     * {@link #FLUSH} each the split an ending holds strictly inside it.
     */
    private static final List<Integer> ENDING_SPLIT_INDICES = List.of(1, 4);

    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;

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

        assertThat(rangesOfKind(line, testCase)).isEqualTo(expected);
    }

    @Test
    void testCasesCoverEveryConcreteSpanKind() {
        assertThat(displacementCases().<Class<?>>map(DisplacementCase::kind).distinct())
            .containsExactlyInAnyOrderElementsOf(leavesOf(Span.class));
    }

    static Stream<DisplacementCase> displacementCases() {
        return Stream.of(
            new DisplacementCase("a beam absorbs the beam it overlaps",
                Beam.class, LineSpansTest::lineOfQuavers, LineSpansTest::newBeam,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a beam absorbs the beam its end falls inside",
                Beam.class, LineSpansTest::lineOfQuavers, LineSpansTest::newBeam,
                OVERLAPPING, EXISTING, Outcome.MERGED),
            new DisplacementCase("a beam absorbs the beam its start falls inside",
                Beam.class, LineSpansTest::lineOfQuavers, LineSpansTest::newBeam,
                INNER, PAST_INNER, Outcome.MERGED),
            new DisplacementCase("a beam leaves the beam flush against it standing",
                Beam.class, LineSpansTest::lineOfQuavers, LineSpansTest::newBeam,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("a crescendo absorbs the crescendo it overlaps",
                Hairpin.class, LineSpansTest::lineOfQuavers, LineSpansTest::newCrescendo,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a crescendo absorbs the crescendo flush against its start",
                Hairpin.class, LineSpansTest::lineOfQuavers, LineSpansTest::newCrescendo,
                EXISTING, FLUSH, Outcome.MERGED),
            new DisplacementCase("a crescendo absorbs the crescendo flush against its end",
                Hairpin.class, LineSpansTest::lineOfQuavers, LineSpansTest::newCrescendo,
                FLUSH, EXISTING, Outcome.MERGED),
            new DisplacementCase("a diminuendo absorbs the diminuendo it overlaps",
                Hairpin.class, LineSpansTest::lineOfQuavers, LineSpansTest::newDiminuendo,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a diminuendo absorbs the diminuendo flush against its start",
                Hairpin.class, LineSpansTest::lineOfQuavers, LineSpansTest::newDiminuendo,
                EXISTING, FLUSH, Outcome.MERGED),
            new DisplacementCase("a crescendo stands beside the diminuendo it overlaps",
                Hairpin.class, LineSpansTest::lineOfQuavers,
                LineSpansTest::newDiminuendo, LineSpansTest::newCrescendo,
                EXISTING, OVERLAPPING, Outcome.BOTH),
            new DisplacementCase("a tuplet removes the tuplet it overlaps",
                Tuplet.class, LineSpansTest::lineOfQuavers, LineSpansTest::newTriplet,
                EXISTING, OVERLAPPING, Outcome.ONLY_THE_NEW),
            new DisplacementCase("a tuplet leaves the tuplet flush against it standing",
                Tuplet.class, LineSpansTest::lineOfQuavers, LineSpansTest::newTriplet,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("a trill removes the trill it overlaps",
                Trill.class, LineSpansTest::lineOfQuavers, Trill::new,
                EXISTING, OVERLAPPING, Outcome.ONLY_THE_NEW),
            new DisplacementCase("a trill leaves the trill flush against it standing",
                Trill.class, LineSpansTest::lineOfQuavers, Trill::new,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("an ending stands beside the ending it overlaps",
                Ending.class, LineSpansTest::lineWithRepeatSplits, Ending::new,
                EXISTING, OVERLAPPING, Outcome.BOTH),
            new DisplacementCase("an ending stands beside the ending flush against it",
                Ending.class, LineSpansTest::lineWithRepeatSplits, Ending::new,
                EXISTING, FLUSH, Outcome.BOTH)
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

    private static final double FIRST_HAIRPIN_X1_SHIFT_SS = 0.5;

    /**
     * The element at {@link #DELETED_INDEX} deleted from a line of crescendos.
     *
     * @param description the case, as the test's display name
     * @param crescendos  the crescendos before the deletion, in the order they are added
     * @param expected    the crescendos the line holds afterwards, in any order
     */
    private record HairpinDeletionCase(String description, List<ElementRange> crescendos, List<ElementRange> expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("hairpinDeletionCases")
    void testADeletionJoinsTheSameKindHairpinsItLeavesTouching(HairpinDeletionCase testCase) {
        var line = lineOfQuavers(HAIRPIN_LINE_LENGTH);
        testCase.crescendos().forEach(range -> crescendoOver(line, range).addTo(line));

        deleteElement(line, DELETED_INDEX);

        assertThat(rangesOf(line, Hairpin.class)).containsExactlyInAnyOrderElementsOf(testCase.expected());
    }

    static Stream<HairpinDeletionCase> hairpinDeletionCases() {
        return Stream.of(
            new HairpinDeletionCase("two crescendos left touching become one over both",
                List.of(EXISTING, ONE_ELEMENT_ON), List.of(JOINED)),
            new HairpinDeletionCase("crescendos added in the reverse of their order on the line join from the earlier start",
                List.of(ONE_ELEMENT_ON, EXISTING), List.of(JOINED)),
            new HairpinDeletionCase("one surviving element between them keeps them apart",
                List.of(EXISTING, TWO_ELEMENTS_ON),
                List.of(EXISTING, TWO_ELEMENTS_ON.shiftedBy(ONE_PLACE_BACK))),
            new HairpinDeletionCase("each run left touching becomes one, apart from the next run",
                List.of(EXISTING, ONE_ELEMENT_ON, FAR),
                List.of(JOINED, FAR.shiftedBy(ONE_PLACE_BACK))),
            new HairpinDeletionCase("a crescendo that loses its first note starts on the note after it",
                List.of(FROM_DELETED),
                List.of(new ElementRange(DELETED_INDEX, FROM_DELETED.end() + ONE_PLACE_BACK)))
        );
    }

    @Test
    void testTheJoinedHairpinCarriesTheFirstOnesOffsets() {
        var line = lineOfQuavers(HAIRPIN_LINE_LENGTH);
        var first = crescendoOver(line, EXISTING);
        first.setX1ShiftSs(FIRST_HAIRPIN_X1_SHIFT_SS);
        first.addTo(line);
        crescendoOver(line, ONE_ELEMENT_ON).addTo(line);

        deleteElement(line, DELETED_INDEX);

        assertThat(line.spanRegistry().getSpans()).singleElement()
            .isInstanceOfSatisfying(Hairpin.class,
                joined -> assertThat(joined.getX1ShiftSs()).isEqualTo(FIRST_HAIRPIN_X1_SHIFT_SS));
    }

    @Test
    void testAHairpinWhoseEndpointsBothSurviveADeletionIsLeftAsItIs() {
        var line = lineOfQuavers(HAIRPIN_LINE_LENGTH);
        var hairpin = crescendoOver(line, EXISTING);
        hairpin.addTo(line);

        deleteElement(line, DELETED_INDEX);

        assertThat(line.spanRegistry().getSpans())
            .as("the same hairpin, not a copy of it")
            .containsExactly(hairpin);
    }

    // ------------------------------------------------------------------------
    // Every edit asks the spans first
    // ------------------------------------------------------------------------

    /** An element strictly inside the triplet over {@link #EXISTING}. */
    private static final int TRIPLET_MEMBER = EXISTING.begin() + 1;

    /**
     * One edit that leaves the triplet over {@link #EXISTING} rhythmically invalid.
     *
     * @param description the edit, as the test's display name
     * @param edit        performs it on the line
     */
    private record UnbalancingEdit(String description, Consumer<Line> edit) {}

    static Stream<UnbalancingEdit> unbalancingEdits() {
        return Stream.of(
            new UnbalancingEdit("an insertion inside its bracket",
                line -> line.addElement(TRIPLET_MEMBER, quaver())),
            new UnbalancingEdit("a dot added to a member",
                line -> line.modifyElement(TRIPLET_MEMBER, EnumSet.of(ElementField.DOT_COUNT),
                    () -> ((NoteElement) line.getElement(TRIPLET_MEMBER)).setDots(Dots.ONE))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unbalancingEdits")
    void testAnEditThatUnbalancesATupletRemovesIt(UnbalancingEdit testCase) {
        var line = lineOfQuavers();
        newTriplet(line.getElement(EXISTING.begin()), line.getElement(EXISTING.end())).addTo(line);

        testCase.edit().accept(line);

        assertThat(rangesOf(line, Tuplet.class)).isEmpty();
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

    private static Line lineOfQuavers() {
        return lineOfQuavers(LINE_LENGTH);
    }

    /** A line of quavers with a repeat at each of {@link #ENDING_SPLIT_INDICES}. */
    private static Line lineWithRepeatSplits() {
        var line = detachedLine();

        for (var i = 0; i < LINE_LENGTH; i++) {
            line.addElement(ENDING_SPLIT_INDICES.contains(i) ? repeatRight() : quaver());
        }

        return line;
    }

    private static Span newCrescendo(StaffElement anchor, StaffElement end) {
        return new Hairpin(Hairpin.Kind.CRESCENDO, anchor, end);
    }

    private static Span newDiminuendo(StaffElement anchor, StaffElement end) {
        return new Hairpin(Hairpin.Kind.DIMINUENDO, anchor, end);
    }

    private static Span newBeam(StaffElement anchor, StaffElement end) {
        return new Beam((NoteElement) anchor, (NoteElement) end);
    }

    private static Span newTriplet(StaffElement anchor, StaffElement end) {
        var ratio = new TupletRatio(TRIPLET_NORMAL_NOTES, new NotatableValue(NoteValue.EIGHTH, Dots.NONE));

        return new Tuplet(anchor, end, TRIPLET_GRADE, ratio);
    }

    private static void add(Line line, BiFunction<StaffElement, StaffElement, Span> newSpan, ElementRange range) {
        newSpan.apply(line.getElement(range.begin()), line.getElement(range.end())).addTo(line);
    }

    /** The extents of every span on {@code line} of the case's kind, in registry order. */
    private static List<ElementRange> rangesOfKind(Line line, DisplacementCase testCase) {
        return rangesOf(line, testCase.kind());
    }

    /** The extents of every span on {@code line} of {@code kind}, in registry order. */
    private static List<ElementRange> rangesOf(Line line, Class<? extends Span> kind) {
        return line.spanRegistry().getSpans().stream()
            .filter(kind::isInstance)
            .map(span -> new ElementRange(span.requireAnchorElementIndex(), span.requireEndElementIndex()))
            .toList();
    }
}
