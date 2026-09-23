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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What each {@link Span.IndexPredicate} factory on {@link Span} answers for a span's two endpoint
 * indices, a {@code null} index being an endpoint in no line.
 */
class SpanPredicateTest extends UnitTest {

    /** The range and the element every case asks about. */
    private static final ElementRange RANGE = new ElementRange(2, 4);
    private static final int ELEMENT = 3;

    private static final int BEFORE_RANGE = 0;
    private static final int AFTER_RANGE = 6;

    /**
     * One predicate asked about one pair of endpoint indices.
     *
     * @param description the case, as the test's display name
     * @param predicate   the predicate under test
     * @param anchorIndex the span's anchor index, or {@code null} for an anchor in no line
     * @param endIndex    the span's end index, or {@code null} for an end in no line
     * @param expected    what the predicate answers
     */
    private record PredicateCase(
        String description,
        Span.IndexPredicate predicate,
        @Nullable Integer anchorIndex,
        @Nullable Integer endIndex,
        boolean expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("predicateCases")
    void testEachPredicateAnswersAsItsFactoryStates(PredicateCase testCase) {
        assertThat(testCase.predicate().test(testCase.anchorIndex(), testCase.endIndex()))
            .isEqualTo(testCase.expected());
    }

    static Stream<PredicateCase> predicateCases() {
        return Stream.of(
            new PredicateCase("containing: an element inside the span",
                Span.containing(ELEMENT), RANGE.begin(), RANGE.end(), true),
            new PredicateCase("containing: an element on an endpoint",
                Span.containing(RANGE.begin()), RANGE.begin(), RANGE.end(), true),
            new PredicateCase("containing: an element past the end",
                Span.containing(AFTER_RANGE), RANGE.begin(), RANGE.end(), false),
            new PredicateCase("containing: a span with its anchor in no line",
                Span.containing(ELEMENT), null, RANGE.end(), false),

            new PredicateCase("overlapping: a span sharing one element",
                Span.overlapping(RANGE), BEFORE_RANGE, RANGE.begin(), true),
            new PredicateCase("overlapping: a span wholly before",
                Span.overlapping(RANGE), BEFORE_RANGE, BEFORE_RANGE + 1, false),
            new PredicateCase("overlapping: a span whose anchor is in no line",
                Span.overlapping(RANGE), null, RANGE.begin(), true),
            new PredicateCase("overlapping: a span whose end is in no line",
                Span.overlapping(RANGE), BEFORE_RANGE, null, false),

            new PredicateCase("beyond an endpoint: a span ending on the range's first element",
                Span.overlappingBeyondEndpoint(RANGE), BEFORE_RANGE, RANGE.begin(), false),
            new PredicateCase("beyond an endpoint: a span anchored on the range's last element",
                Span.overlappingBeyondEndpoint(RANGE), RANGE.end(), AFTER_RANGE, false),
            new PredicateCase("beyond an endpoint: a span reaching past the range's first element",
                Span.overlappingBeyondEndpoint(RANGE), BEFORE_RANGE, ELEMENT, true),
            new PredicateCase("beyond an endpoint: a half-detached span counts as a collision",
                Span.overlappingBeyondEndpoint(RANGE), null, ELEMENT, true),

            new PredicateCase("strictly containing: an element inside the span",
                Span.strictlyContaining(ELEMENT), RANGE.begin(), RANGE.end(), true),
            new PredicateCase("strictly containing: an element on the anchor",
                Span.strictlyContaining(RANGE.begin()), RANGE.begin(), RANGE.end(), false),
            new PredicateCase("strictly containing: an element on the end",
                Span.strictlyContaining(RANGE.end()), RANGE.begin(), RANGE.end(), false),

            new PredicateCase("exactly: the range itself",
                Span.exactly(RANGE), RANGE.begin(), RANGE.end(), true),
            new PredicateCase("exactly: a span one element longer",
                Span.exactly(RANGE), RANGE.begin(), RANGE.end() + 1, false),
            new PredicateCase("exactly: a span with its end in no line",
                Span.exactly(RANGE), RANGE.begin(), null, false),

            new PredicateCase("anchored at: a span anchored there",
                Span.anchoredAt(ELEMENT), ELEMENT, AFTER_RANGE, true),
            new PredicateCase("anchored at: a span ending there",
                Span.anchoredAt(ELEMENT), BEFORE_RANGE, ELEMENT, false),
            new PredicateCase("anchored at: a span with its anchor in no line",
                Span.anchoredAt(ELEMENT), null, ELEMENT, false),

            new PredicateCase("ending at: a span ending there",
                Span.endingAt(ELEMENT), BEFORE_RANGE, ELEMENT, true),
            new PredicateCase("ending at: a span anchored there",
                Span.endingAt(ELEMENT), ELEMENT, AFTER_RANGE, false),
            new PredicateCase("ending at: a span with its end in no line",
                Span.endingAt(ELEMENT), ELEMENT, null, false)
        );
    }
}
