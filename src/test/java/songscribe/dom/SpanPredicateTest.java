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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What each {@link Span.IndexPredicate} factory on {@link Span} answers for a span's own
 * anchor-to-end range.
 */
class SpanPredicateTest extends UnitTest {

    /** The range and the element every case asks about. */
    private static final ElementRange RANGE = new ElementRange(2, 4);
    private static final int ELEMENT = 3;

    private static final int BEFORE_RANGE = 0;
    private static final int AFTER_RANGE = 6;

    /**
     * One predicate asked about one span range.
     *
     * @param description the case, as the test's display name
     * @param predicate   the predicate under test
     * @param spanRange   the span's own anchor-to-end range
     * @param expected    what the predicate answers
     */
    private record PredicateCase(
        String description,
        Span.IndexPredicate predicate,
        ElementRange spanRange,
        boolean expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("predicateCases")
    void testEachPredicateAnswersAsItsFactoryStates(PredicateCase testCase) {
        assertThat(testCase.predicate().test(testCase.spanRange()))
            .isEqualTo(testCase.expected());
    }

    static Stream<PredicateCase> predicateCases() {
        var insertion = Span.straddling(new StaffElementRun.InsertionTarget.AtIndex(ELEMENT));
        var replacement = Span.straddling(new StaffElementRun.InsertionTarget.Replacing(
            lineOfQuavers(AFTER_RANGE + 1).effectiveRange(RANGE)));

        return Stream.of(
            new PredicateCase("containing: an element inside the span",
                Span.containing(ELEMENT), RANGE, true),
            new PredicateCase("containing: an element on an endpoint",
                Span.containing(RANGE.begin()), RANGE, true),
            new PredicateCase("containing: an element past the end",
                Span.containing(AFTER_RANGE), RANGE, false),

            new PredicateCase("overlapping: a span sharing one element",
                Span.overlapping(RANGE), new ElementRange(BEFORE_RANGE, RANGE.begin()), true),
            new PredicateCase("overlapping: a span wholly before",
                Span.overlapping(RANGE), new ElementRange(BEFORE_RANGE, BEFORE_RANGE + 1), false),

            new PredicateCase("beyond an endpoint: a span ending on the range's first element",
                Span.overlappingBeyondEndpoint(RANGE), new ElementRange(BEFORE_RANGE, RANGE.begin()), false),
            new PredicateCase("beyond an endpoint: a span anchored on the range's last element",
                Span.overlappingBeyondEndpoint(RANGE), new ElementRange(RANGE.end(), AFTER_RANGE), false),
            new PredicateCase("beyond an endpoint: a span reaching past the range's first element",
                Span.overlappingBeyondEndpoint(RANGE), new ElementRange(BEFORE_RANGE, ELEMENT), true),

            new PredicateCase("strictly containing: an element inside the span",
                Span.strictlyContaining(ELEMENT), RANGE, true),
            new PredicateCase("strictly containing: an element on the anchor",
                Span.strictlyContaining(RANGE.begin()), RANGE, false),
            new PredicateCase("strictly containing: an element on the end",
                Span.strictlyContaining(RANGE.end()), RANGE, false),

            new PredicateCase("exactly: the range itself",
                Span.exactly(RANGE), RANGE, true),
            new PredicateCase("exactly: a span one element longer",
                Span.exactly(RANGE), new ElementRange(RANGE.begin(), RANGE.end() + 1), false),

            new PredicateCase("anchored at: a span anchored there",
                Span.anchoredAt(ELEMENT), new ElementRange(ELEMENT, AFTER_RANGE), true),
            new PredicateCase("anchored at: a span ending there",
                Span.anchoredAt(ELEMENT), new ElementRange(BEFORE_RANGE, ELEMENT), false),

            new PredicateCase("ending at: a span ending there",
                Span.endingAt(ELEMENT), new ElementRange(BEFORE_RANGE, ELEMENT), true),
            new PredicateCase("ending at: a span anchored there",
                Span.endingAt(ELEMENT), new ElementRange(ELEMENT, AFTER_RANGE), false),

            new PredicateCase("straddling an insertion: a span from before it to the element it lands on",
                insertion, new ElementRange(BEFORE_RANGE, ELEMENT), true),
            new PredicateCase("straddling an insertion: a span anchored where it lands",
                insertion, new ElementRange(ELEMENT, AFTER_RANGE), false),
            new PredicateCase("straddling an insertion: a span ending before it",
                insertion, new ElementRange(BEFORE_RANGE, RANGE.begin()), false),

            new PredicateCase("straddling a replacement: a span from before the deleted range to past it",
                replacement, new ElementRange(BEFORE_RANGE, AFTER_RANGE), true),
            new PredicateCase("straddling a replacement: a span ending inside the deleted range",
                replacement, new ElementRange(BEFORE_RANGE, ELEMENT), false),
            new PredicateCase("straddling a replacement: a span wholly inside the deleted range",
                replacement, RANGE, false)
        );
    }
}
