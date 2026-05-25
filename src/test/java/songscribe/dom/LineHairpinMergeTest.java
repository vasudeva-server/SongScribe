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
 * {@code DynamicsMarkingTest} which must not be touched.
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

            var crescendos = line.findRangeElements(Crescendo.class);

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

            var crescendos = line.findRangeElements(Crescendo.class);

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

            var crescendos = line.findRangeElements(Crescendo.class);
            var diminuendos = line.findRangeElements(Diminuendo.class);

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

            var diminuendos = line.findRangeElements(Diminuendo.class);

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

            var diminuendos = line.findRangeElements(Diminuendo.class);

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

            var crescendos = line.findRangeElements(Crescendo.class);
            var diminuendos = line.findRangeElements(Diminuendo.class);

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
}
