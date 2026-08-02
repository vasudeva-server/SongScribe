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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for same-type hairpin merge semantics in {@link Line#addCrescendo}
 * and {@link Line#addDiminuendo}.
 *
 * <p>These tests verify that two adjacent or overlapping hairpins of the same type
 * merge into a single wider hairpin. They complement the non-merging e2e smoke in
 * {@code DynamicsMarkingTest}.
 *
 * <p>All element setup uses {@link Song#withoutMutationTracking}. Hairpin additions
 * under test also use suspended tracking when only structural state (span, count) is
 * asserted rather than mutation emissions.
 */
class LineHairpinMergeTest extends UnitTest {

    // Indices into the 5-element line built in setUp()
    private static final int IDX_0 = 0;
    private static final int IDX_1 = 1;
    private static final int IDX_2 = 2;
    private static final int IDX_3 = 3;
    private static final int IDX_4 = 4;

    // Number of note elements placed in the line fixture
    private static final int NOTE_COUNT = 5;

    private Song song;
    private Line line;

    /** Adds {@value #NOTE_COUNT} quarter notes to {@code line} via suspended tracking. */
    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            }
        });
    }

    // -----------------------------------------------------------------------
    // addCrescendo — same-type merge (row 38)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddCrescendoSameTypeMerge {

        /**
         * Adding crescendo [0,2] then [2,4] — they share endpoint 2 — must merge into a
         * single crescendo [0,4]. The count must be 1 and the span must be exact.
         */
        @Test
        void testAdjacentCrescendosWithSharedEndpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_2))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("adjacent crescendos must merge into exactly one crescendo")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("merged crescendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("merged crescendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        /**
         * Adding crescendo [0,1] then [1,2] — they share endpoint 1 — must merge into a
         * single crescendo [0,2].
         */
        @Test
        void testAdjacentCrescendosWithSharedNearEndpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_1), line.getElement(IDX_2))));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("adjacent crescendos must merge into exactly one crescendo")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("merged crescendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("merged crescendo end must be 2")
                    .isEqualTo(IDX_2)
            );
        }

        /**
         * A crescendo added adjacent to an existing diminuendo must NOT merge with it:
         * different types must remain separate.
         */
        @Test
        void testCrescendoDoesNotMergeWithAdjacentDiminuendo() {
            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_0), line.getElement(IDX_2))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var crescendos = line.findSpans(Crescendo.class);
            var diminuendos = line.findSpans(Diminuendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("crescendo must remain separate — different type from diminuendo")
                    .hasSize(1),
                () -> assertThat(diminuendos)
                    .as("diminuendo must remain separate — different type from crescendo")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("crescendo anchor must be 2, not absorbed by diminuendo")
                    .isEqualTo(IDX_2),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("crescendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }
    }

    // -----------------------------------------------------------------------
    // addCrescendo — genuine interior overlap, no shared endpoint
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddCrescendoPartialOverlapMerge {

        /**
         * Adding crescendo [0,3] then [2,4] — the two overlap only in the interior
         * (element 2), sharing no endpoint — must still merge into a single crescendo
         * [0,4], with the absorbed [0,3] crescendo removed. This is the shape a
         * re-anchored pasted hairpin produces when it lands inside an existing one.
         */
        @Test
        void testInteriorOverlapWithNoSharedEndpointMergesIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_3))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("interior overlap must merge into exactly one crescendo; the absorbed span must be removed")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("merged crescendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("merged crescendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }
    }

    // -----------------------------------------------------------------------
    // addDiminuendo — same-type merge (row 38)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddDiminuendoSameTypeMerge {

        /**
         * Adding diminuendo [0,2] then [2,4] — they share endpoint 2 — must merge into a
         * single diminuendo [0,4]. The count must be 1 and the span must be exact.
         */
        @Test
        void testAdjacentDiminuendosWithSharedEndpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_0), line.getElement(IDX_2))));

            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var diminuendos = line.findSpans(Diminuendo.class);

            assertAll(
                () -> assertThat(diminuendos)
                    .as("adjacent diminuendos must merge into exactly one diminuendo")
                    .hasSize(1),
                () -> assertThat(diminuendos.getFirst().getAnchorElementIndex())
                    .as("merged diminuendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(diminuendos.getFirst().getEndElementIndex())
                    .as("merged diminuendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        /**
         * Adding diminuendo [0,1] then [1,2] — they share endpoint 1 — must merge into a
         * single diminuendo [0,2].
         */
        @Test
        void testAdjacentDiminuendosWithSharedNearEndpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_1), line.getElement(IDX_2))));

            var diminuendos = line.findSpans(Diminuendo.class);

            assertAll(
                () -> assertThat(diminuendos)
                    .as("adjacent diminuendos must merge into exactly one diminuendo")
                    .hasSize(1),
                () -> assertThat(diminuendos.getFirst().getAnchorElementIndex())
                    .as("merged diminuendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(diminuendos.getFirst().getEndElementIndex())
                    .as("merged diminuendo end must be 2")
                    .isEqualTo(IDX_2)
            );
        }

        /**
         * A diminuendo added adjacent to an existing crescendo must NOT merge with it:
         * different types must remain separate.
         */
        @Test
        void testDiminuendoDoesNotMergeWithAdjacentCrescendo() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_2))));

            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var crescendos = line.findSpans(Crescendo.class);
            var diminuendos = line.findSpans(Diminuendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("crescendo must remain separate — different type from diminuendo")
                    .hasSize(1),
                () -> assertThat(diminuendos)
                    .as("diminuendo must remain separate — different type from crescendo")
                    .hasSize(1),
                () -> assertThat(diminuendos.getFirst().getAnchorElementIndex())
                    .as("diminuendo anchor must be 2, not absorbed by crescendo")
                    .isEqualTo(IDX_2),
                () -> assertThat(diminuendos.getFirst().getEndElementIndex())
                    .as("diminuendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }
    }

    // -----------------------------------------------------------------------
    // Abutting hairpins — no shared endpoint, merely touching
    // -----------------------------------------------------------------------

    /**
     * The tests above merge hairpins that share an endpoint, so their ranges overlap.
     * These merge hairpins that only <em>touch</em>: one ends on the element directly
     * before the other begins, with no element in common. One uninterrupted dynamic
     * gesture is one hairpin however the user happened to draw it, so the two merge —
     * which is also what makes a pasted hairpin landing flush against an existing one
     * continue it instead of restarting it.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AbuttingHairpinMerge {

        @Test
        void testCrescendoAddedDirectlyAfterAnotherMergesIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("a crescendo starting where another ended must merge into one")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("merged crescendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("merged crescendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        @Test
        void testCrescendoAddedDirectlyBeforeAnotherMergesIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("a crescendo ending where another began must merge into one")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("merged crescendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("merged crescendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        @Test
        void testDiminuendoAddedDirectlyAfterAnotherMergesIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            var diminuendos = line.findSpans(Diminuendo.class);

            assertAll(
                () -> assertThat(diminuendos)
                    .as("a diminuendo starting where another ended must merge into one")
                    .hasSize(1),
                () -> assertThat(diminuendos.getFirst().getAnchorElementIndex())
                    .as("merged diminuendo anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(diminuendos.getFirst().getEndElementIndex())
                    .as("merged diminuendo end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        @Test
        void testAbuttingHairpinsOfDifferentTypesStayApart() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addDiminuendo(new Diminuendo(line.getElement(IDX_2), line.getElement(IDX_4))));

            assertAll(
                () -> assertThat(line.findSpans(Crescendo.class))
                    .as("crescendo must survive alongside the abutting diminuendo")
                    .hasSize(1),
                () -> assertThat(line.findSpans(Diminuendo.class))
                    .as("a crescendo swelling straight into a diminuendo is two hairpins")
                    .hasSize(1)
            );
        }

        /**
         * A hairpin separated from a same-type one by even a single plain note is a
         * second gesture, not a continuation, so the two must not merge across the gap.
         */
        @Test
        void testHairpinsSeparatedByAGapStayApart() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_3), line.getElement(IDX_4))));

            assertThat(line.findSpans(Crescendo.class))
                .as("a plain note between two crescendos keeps them separate")
                .hasSize(2);
        }

        /**
         * A single-note crescendo [2,2] inserted between an existing crescendo ending
         * directly before it ([0,1]) and one starting directly after it ([3,4]) abuts
         * both at once. {@code mergeOverlappingSpans} runs two independent expansion
         * checks — one for the anchor side, one for the end side — and this is the case
         * where a single {@code addCrescendo} call must satisfy both in the same pass,
         * absorbing the earlier and the later hairpin together into one [0,4] span.
         */
        @Test
        void testCrescendoAbuttingBothANeighborBeforeAndAfterAbsorbsBothAtOnce() {
            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_3), line.getElement(IDX_4))));

            song.withoutMutationTracking(() ->
                line.addCrescendo(new Crescendo(line.getElement(IDX_2), line.getElement(IDX_2))));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("the middle crescendo must absorb both neighbors into one span")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElementIndex())
                    .as("merged crescendo anchor must reach back to the earlier neighbor")
                    .isEqualTo(IDX_0),
                () -> assertThat(crescendos.getFirst().getEndElementIndex())
                    .as("merged crescendo end must reach forward to the later neighbor")
                    .isEqualTo(IDX_4)
            );
        }
    }
}
