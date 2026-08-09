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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import songscribe.UnitTest;

/**
 * Unit tests for {@link Span}'s four predicate factories as pure functions of two
 * {@link SpanBound}s — every combination of the four bound kinds, for each predicate.
 * No {@link Line} is involved: {@link SpanLookupTest} covers what a real line resolves to,
 * this covers what the predicates do with it.
 *
 * <p>The readings under test: an off-edge bound is unbounded in its direction, so a
 * cross-line half reports true for every element it passes over; {@link SpanBound#ABSENT} is
 * rejected by {@link Span#containing} because an endpoint with no position bounds nothing;
 * {@link Span#overlappingBeyondEndpoint} excludes a span that shares only the one endpoint
 * two back-to-back hairpins may legally meet at; and no bound is ever coerced to index 0 or
 * to a last index, so {@link Span#exactly} answers only for two real positions.
 */
class SpanBoundPredicateTest extends UnitTest {

    // Layout the bounds describe:  0    1(anchor)    2(query)    3(end)    4
    private static final int ANCHOR_INDEX = 1;
    private static final int QUERY_INDEX = 2;
    private static final int END_INDEX = 3;
    private static final int LAST_INDEX = 4;

    /**
     * The four bound kinds, in the row and column order of the expectation tables below.
     * The {@link SpanBound.At} entry differs per side, so each side names its own list.
     */
    private static final List<SpanBound> ANCHOR_CASES = List.of(
        new SpanBound.At(ANCHOR_INDEX), SpanBound.BEFORE_LINE, SpanBound.AFTER_LINE, SpanBound.ABSENT);
    private static final List<SpanBound> END_CASES = List.of(
        new SpanBound.At(END_INDEX), SpanBound.BEFORE_LINE, SpanBound.AFTER_LINE, SpanBound.ABSENT);

    /**
     * Asserts {@code predicate} against all sixteen combinations at once, so a failure names
     * every combination that disagrees rather than only the first.
     */
    private static void assertMatrix(Span.IndexPredicate predicate, String predicateName, boolean[][] expected) {
        var assertions = new ArrayList<Executable>();

        for (var anchorCase = 0; anchorCase < ANCHOR_CASES.size(); anchorCase++) {
            for (var endCase = 0; endCase < END_CASES.size(); endCase++) {
                var anchorBound = ANCHOR_CASES.get(anchorCase);
                var endBound = END_CASES.get(endCase);
                var expectedMatch = expected[anchorCase][endCase];

                assertions.add(() -> assertThat(predicate.test(anchorBound, endBound))
                    .as("%s with anchor %s and end %s", predicateName, anchorBound, endBound)
                    .isEqualTo(expectedMatch));
            }
        }

        assertAll(assertions);
    }

    // -----------------------------------------------------------------------
    // Span.containing(QUERY_INDEX)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContainingEveryBoundCombination {

        // Columns: end At(3), BEFORE_LINE, AFTER_LINE, ABSENT.
        // An anchor matches when it is at or before the query — At(1) and BEFORE_LINE are;
        // an end matches when it is at or after it — At(3) and AFTER_LINE are.
        private static final boolean[][] EXPECTED = {
            {true, false, true, false},     // anchor At(1)
            {true, false, true, false},     // anchor BEFORE_LINE — before every index
            {false, false, false, false},   // anchor AFTER_LINE — past the query already
            {false, false, false, false},   // anchor ABSENT — no position bounds nothing
        };

        @Test
        void testContainingOverEveryBoundCombination() {
            assertMatrix(Span.containing(QUERY_INDEX), "containing(" + QUERY_INDEX + ')', EXPECTED);
        }

        @Test
        void testOffEdgeEndCoversEveryElementFromTheAnchorToTheLineEdge() {
            var anchorBound = new SpanBound.At(ANCHOR_INDEX);
            var assertions = new ArrayList<Executable>();

            for (var index = ANCHOR_INDEX; index <= LAST_INDEX; index++) {
                var elementIndex = index;
                assertions.add(() -> assertThat(
                    Span.containing(elementIndex).test(anchorBound, SpanBound.AFTER_LINE))
                    .as("a half exiting the right edge passes over element %d", elementIndex)
                    .isTrue());
            }

            assertions.add(() -> assertThat(
                Span.containing(ANCHOR_INDEX - 1).test(anchorBound, SpanBound.AFTER_LINE))
                .as("but it still starts at its anchor")
                .isFalse());

            assertAll(assertions);
        }

        @Test
        void testOffEdgeAnchorCoversEveryElementFromTheLineEdgeToTheEnd() {
            var endBound = new SpanBound.At(END_INDEX);
            var assertions = new ArrayList<Executable>();

            for (var index = 0; index <= END_INDEX; index++) {
                var elementIndex = index;
                assertions.add(() -> assertThat(
                    Span.containing(elementIndex).test(SpanBound.BEFORE_LINE, endBound))
                    .as("a half entering the left edge passes over element %d", elementIndex)
                    .isTrue());
            }

            assertions.add(() -> assertThat(
                Span.containing(END_INDEX + 1).test(SpanBound.BEFORE_LINE, endBound))
                .as("but it still stops at its end")
                .isFalse());

            assertAll(assertions);
        }
    }

    // -----------------------------------------------------------------------
    // Span.overlapping(QUERY_INDEX, QUERY_INDEX)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OverlappingEveryBoundCombination {

        // Same reading as containing, with one deliberate difference: an ABSENT anchor does
        // not reject, so the trill sweeps in Line.addTrill and Line.removeTrillsOverlapping
        // still find a half-detached span and can clean it up.
        private static final boolean[][] EXPECTED = {
            {true, false, true, false},     // anchor At(1)
            {true, false, true, false},     // anchor BEFORE_LINE
            {false, false, false, false},   // anchor AFTER_LINE
            {true, false, true, false},     // anchor ABSENT — not rejected on the anchor side
        };

        @Test
        void testOverlappingOverEveryBoundCombination() {
            assertMatrix(
                Span.overlapping(QUERY_INDEX, QUERY_INDEX),
                "overlapping(" + QUERY_INDEX + ", " + QUERY_INDEX + ')',
                EXPECTED);
        }

        @Test
        void testAbsentAnchorIsFoundWhereContainingRejectsIt() {
            var endBound = new SpanBound.At(END_INDEX);

            assertAll(
                () -> assertThat(Span.overlapping(0, END_INDEX).test(SpanBound.ABSENT, endBound))
                    .as("a half-detached span must stay findable by the removal sweeps")
                    .isTrue(),
                () -> assertThat(Span.containing(END_INDEX).test(SpanBound.ABSENT, endBound))
                    .as("while containment still rejects it")
                    .isFalse()
            );
        }
    }

    // -----------------------------------------------------------------------
    // Span.overlappingBeyondEndpoint(ANCHOR_INDEX, END_INDEX)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OverlappingBeyondEndpointEveryBoundCombination {

        // Same overlap reading as `overlapping` — neither of the endpoint-sharing cases this
        // predicate excludes arises among these four bound kinds at ANCHOR_INDEX/END_INDEX, so
        // the matrix matches OverlappingEveryBoundCombination's; the named cases below pin the
        // excluded cases directly.
        private static final boolean[][] EXPECTED = {
            {true, false, true, false},     // anchor At(1)
            {true, false, true, false},     // anchor BEFORE_LINE
            {false, false, false, false},   // anchor AFTER_LINE
            {true, false, true, false},     // anchor ABSENT — not rejected, see below
        };

        @Test
        void testOverlappingBeyondEndpointOverEveryBoundCombination() {
            assertMatrix(
                Span.overlappingBeyondEndpoint(ANCHOR_INDEX, END_INDEX),
                "overlappingBeyondEndpoint(" + ANCHOR_INDEX + ", " + END_INDEX + ')',
                EXPECTED);
        }

        @Test
        void testSpanWhoseEndIsExactlyAtBeginIsNotMatched() {
            assertThat(Span.overlappingBeyondEndpoint(ANCHOR_INDEX, END_INDEX)
                .test(SpanBound.BEFORE_LINE, new SpanBound.At(ANCHOR_INDEX)))
                .as("a span whose end sits exactly at the other's begin only shares the "
                    + "endpoint two back-to-back hairpins may legally have")
                .isFalse();
        }

        @Test
        void testSpanWhoseAnchorIsExactlyAtEndIsNotMatched() {
            assertThat(Span.overlappingBeyondEndpoint(ANCHOR_INDEX, END_INDEX)
                .test(new SpanBound.At(END_INDEX), SpanBound.AFTER_LINE))
                .as("the mirror case: a span whose anchor sits exactly at the other's end "
                    + "only shares an endpoint")
                .isFalse();
        }

        @Test
        void testSpanOverlappingByTwoOrMoreElementsIsMatched() {
            assertThat(Span.overlappingBeyondEndpoint(ANCHOR_INDEX, END_INDEX)
                .test(SpanBound.BEFORE_LINE, new SpanBound.At(QUERY_INDEX)))
                .as("more than one shared element is a genuine collision, not a legal "
                    + "back-to-back meeting")
                .isTrue();
        }

        @Test
        void testAbsentAnchorIsMatched() {
            // SpanBound.isAt is false for every Unpositioned value, so a half-detached span
            // can never be shown to share only an endpoint and is treated as a collision.
            // This is the conservative answer and is deliberate — contrast with
            // OverlappingEveryBoundCombination.testAbsentAnchorIsFoundWhereContainingRejectsIt,
            // which exists because `overlapping` must keep finding such spans for the removal
            // sweeps.
            assertThat(Span.overlappingBeyondEndpoint(ANCHOR_INDEX, END_INDEX)
                .test(SpanBound.ABSENT, new SpanBound.At(END_INDEX)))
                .as("a half-detached span cannot be shown to share only an endpoint")
                .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Span.exactly(ANCHOR_INDEX, END_INDEX)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ExactlyEveryBoundCombination {

        // Only two real positions can equal two queried ones.
        private static final boolean[][] EXPECTED = {
            {true, false, false, false},    // anchor At(1)
            {false, false, false, false},   // anchor BEFORE_LINE
            {false, false, false, false},   // anchor AFTER_LINE
            {false, false, false, false},   // anchor ABSENT
        };

        @Test
        void testExactlyOverEveryBoundCombination() {
            assertMatrix(
                Span.exactly(ANCHOR_INDEX, END_INDEX),
                "exactly(" + ANCHOR_INDEX + ", " + END_INDEX + ')',
                EXPECTED);
        }

        @Test
        void testOffEdgeBoundIsNeverCoercedToALineEdgeIndex() {
            assertAll(
                () -> assertThat(Span.exactly(ANCHOR_INDEX, LAST_INDEX)
                    .test(new SpanBound.At(ANCHOR_INDEX), SpanBound.AFTER_LINE))
                    .as("an end off the right edge is not the last index")
                    .isFalse(),
                () -> assertThat(Span.exactly(0, END_INDEX)
                    .test(SpanBound.BEFORE_LINE, new SpanBound.At(END_INDEX)))
                    .as("an anchor off the left edge is not index 0")
                    .isFalse()
            );
        }
    }
}
