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
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * What a span kind promises about the same-kind spans already on its line when it is added. A
 * kind does one of three things with a span it meets: a beam and a hairpin absorb it, a tuplet
 * and a trill remove it, a tie and an ending stand beside it. A kind whose two touching spans are
 * one gesture — a hairpin — treats a span flush against it as one it meets; every other kind
 * leaves that span standing.
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
     * @param newSpan     builds a span of the kind over an anchor and an end element
     * @param existing    the span already on the line
     * @param added       the span under test
     * @param outcome     what the line then holds of the kind
     */
    private record DisplacementCase(
        String description,
        Class<? extends Span> kind,
        Supplier<Line> line,
        BiFunction<StaffElement, StaffElement, Span> newSpan,
        ElementRange existing,
        ElementRange added,
        Outcome outcome
    ) {}

    private static final int LINE_LENGTH = 6;

    /** The span already on the line when the one under test is added. */
    private static final ElementRange EXISTING = new ElementRange(0, 2);

    /** A span sharing its first element with {@link #EXISTING}'s last. */
    private static final ElementRange OVERLAPPING = new ElementRange(EXISTING.end(), LINE_LENGTH - 1);

    /** A span beginning just past {@link #EXISTING}, within the reach that counts as touching. */
    private static final ElementRange FLUSH =
        new ElementRange(EXISTING.end() + SPAN_ADJACENCY_REACH, LINE_LENGTH - 1);

    /** A tie joins two neighboring notes, so its fixtures are pairs rather than the wide ranges. */
    private static final ElementRange EXISTING_TIE = new ElementRange(0, 1);

    /** The next tie of a chain: it shares {@link #EXISTING_TIE}'s last note. */
    private static final ElementRange CHAINED_TIE = new ElementRange(EXISTING_TIE.end(), EXISTING_TIE.end() + 1);

    private static final ElementRange FLUSH_TIE = new ElementRange(
        EXISTING_TIE.end() + SPAN_ADJACENCY_REACH, EXISTING_TIE.end() + SPAN_ADJACENCY_REACH + 1);

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
        add(line, testCase, testCase.existing());

        add(line, testCase, testCase.added());

        var expected = switch (testCase.outcome()) {
            case MERGED -> List.of(new ElementRange(testCase.existing().begin(), testCase.added().end()));
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
            new DisplacementCase("a tie stands beside the tie it shares a note with",
                Tie.class, LineSpansTest::lineOfQuavers, LineSpansTest::newTie,
                EXISTING_TIE, CHAINED_TIE, Outcome.BOTH),
            new DisplacementCase("a tie stands beside the tie flush against it",
                Tie.class, LineSpansTest::lineOfQuavers, LineSpansTest::newTie,
                EXISTING_TIE, FLUSH_TIE, Outcome.BOTH),
            new DisplacementCase("a beam absorbs the beam it overlaps",
                Beam.class, LineSpansTest::lineOfQuavers, LineSpansTest::newBeam,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a beam leaves the beam flush against it standing",
                Beam.class, LineSpansTest::lineOfQuavers, LineSpansTest::newBeam,
                EXISTING, FLUSH, Outcome.BOTH),
            new DisplacementCase("a crescendo absorbs the crescendo it overlaps",
                Crescendo.class, LineSpansTest::lineOfQuavers, Crescendo::new,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a crescendo absorbs the crescendo flush against it",
                Crescendo.class, LineSpansTest::lineOfQuavers, Crescendo::new,
                EXISTING, FLUSH, Outcome.MERGED),
            new DisplacementCase("a diminuendo absorbs the diminuendo it overlaps",
                Diminuendo.class, LineSpansTest::lineOfQuavers, Diminuendo::new,
                EXISTING, OVERLAPPING, Outcome.MERGED),
            new DisplacementCase("a diminuendo absorbs the diminuendo flush against it",
                Diminuendo.class, LineSpansTest::lineOfQuavers, Diminuendo::new,
                EXISTING, FLUSH, Outcome.MERGED),
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
        var line = detachedLine();

        for (var i = 0; i < LINE_LENGTH; i++) {
            line.addElement(quaver());
        }

        return line;
    }

    /** A line of quavers with a repeat at each of {@link #ENDING_SPLIT_INDICES}. */
    private static Line lineWithRepeatSplits() {
        var line = detachedLine();

        for (var i = 0; i < LINE_LENGTH; i++) {
            line.addElement(ENDING_SPLIT_INDICES.contains(i) ? repeatRight() : quaver());
        }

        return line;
    }

    private static Span newTie(StaffElement anchor, StaffElement end) {
        return new Tie((NoteElement) anchor, (NoteElement) end);
    }

    private static Span newBeam(StaffElement anchor, StaffElement end) {
        return new Beam((NoteElement) anchor, (NoteElement) end);
    }

    private static Span newTriplet(StaffElement anchor, StaffElement end) {
        var ratio = new TupletRatio(TRIPLET_NORMAL_NOTES, new NotatableValue(ElementType.QUAVER, Dots.NONE));

        return new Tuplet(anchor, end, TRIPLET_GRADE, ratio);
    }

    private static void add(Line line, DisplacementCase testCase, ElementRange range) {
        testCase.newSpan().apply(line.getElement(range.begin()), line.getElement(range.end())).addTo(line);
    }

    /** The extents of every span on {@code line} of the case's kind, in registry order. */
    private static List<ElementRange> rangesOfKind(Line line, DisplacementCase testCase) {
        return line.spanRegistry().getSpans().stream()
            .filter(testCase.kind()::isInstance)
            .map(span -> new ElementRange(span.requireAnchorElementIndex(), span.requireEndElementIndex()))
            .toList();
    }
}
