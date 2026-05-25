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
 * Unit tests for {@link Line} tie management: add/merge and query methods.
 *
 * <p>All element setup uses {@link Song#withoutMutationTracking} so element
 * installation produces no notification and requires no open bracket. Tie
 * additions that exercise the {@code addTie} API itself also use
 * {@code withoutMutationTracking} when only structural state is being
 * asserted, not mutation emissions.
 */
class LineTieTest extends UnitTest {

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
    // addTie — adjacent/overlapping tie merge (row 31)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddTieAdjacentMerge {

        /**
         * Adding tie [0,1] then tie [1,2] — they share endpoint 1 — must merge into a
         * single tie [0,2]. The tie count must be 1 (not 2) and the span must be exact.
         */
        @Test
        void testAdjacentTiesWithSharedEndpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(IDX_1), line.getElement(IDX_2))));

            var ties = line.findTies();

            assertAll(
                () -> assertThat(ties)
                    .as("adjacent ties must merge into exactly one tie")
                    .hasSize(1),
                () -> assertThat(ties.getFirst().getAnchorElementIndex())
                    .as("merged tie anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(ties.getFirst().getEndElementIndex())
                    .as("merged tie end must be 2")
                    .isEqualTo(IDX_2)
            );
        }

        /**
         * Adding tie [0,2] then [2,4] — they share endpoint 2 — must merge into a single
         * tie [0,4]. The tie count must be 1 and the span must be exact.
         */
        @Test
        void testAdjacentTiesWithSharedMidpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(IDX_0), line.getElement(IDX_2))));

            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(IDX_2), line.getElement(IDX_4))));

            var ties = line.findTies();

            assertAll(
                () -> assertThat(ties)
                    .as("adjacent ties sharing midpoint must merge into exactly one tie")
                    .hasSize(1),
                () -> assertThat(ties.getFirst().getAnchorElementIndex())
                    .as("merged tie anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(ties.getFirst().getEndElementIndex())
                    .as("merged tie end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        /**
         * Non-adjacent ties [0,1] and [3,4] must remain as two separate ties.
         */
        @Test
        void testNonAdjacentTiesDoNotMerge() {
            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addTie(new Tie(line.getElement(IDX_3), line.getElement(IDX_4))));

            var ties = line.findTies();

            assertThat(ties)
                .as("non-adjacent ties must remain as two separate ties")
                .hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // findTies() (row 33)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindTies {

        /**
         * An empty line has no ties.
         */
        @Test
        void testFindTiesOnEmptyLineReturnsEmpty() {
            assertThat(line.findTies())
                .as("no ties on a fresh line")
                .isEmpty();
        }

        /**
         * After adding two non-overlapping ties, {@link Line#findTies()} must return
         * exactly those two ties with correct spans.
         */
        @Test
        void testFindTiesReturnsBothTiesWithCorrectSpans() {
            var tieLeft = new Tie(line.getElement(IDX_0), line.getElement(IDX_1));
            var tieRight = new Tie(line.getElement(IDX_3), line.getElement(IDX_4));
            var left = tieLeft;
            var right = tieRight;

            song.withoutMutationTracking(() -> {
                line.addTie(left);
                line.addTie(right);
            });

            var ties = line.findTies();

            assertAll(
                () -> assertThat(ties)
                    .as("findTies must return exactly two ties")
                    .hasSize(2),
                () -> assertThat(ties.get(0).getAnchorElementIndex())
                    .as("first tie anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(ties.get(0).getEndElementIndex())
                    .as("first tie end must be 1")
                    .isEqualTo(IDX_1),
                () -> assertThat(ties.get(1).getAnchorElementIndex())
                    .as("second tie anchor must be 3")
                    .isEqualTo(IDX_3),
                () -> assertThat(ties.get(1).getEndElementIndex())
                    .as("second tie end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        /**
         * {@link Line#findTies()} must not include non-tie range elements.
         */
        @Test
        void testFindTiesExcludesNonTieRangeElements() {
            // Add a beam (a non-tie range element) alongside a tie; findTies must return only the tie.
            var tieOnly = new Tie(line.getElement(IDX_0), line.getElement(IDX_1));
            song.withoutMutationTracking(() -> {
                line.addTie(tieOnly);
                line.addBeaming(new Beam(line.getElement(IDX_2), line.getElement(IDX_4)));
            });

            var ties = line.findTies();

            assertAll(
                () -> assertThat(ties)
                    .as("findTies must return only the one tie, not the beam")
                    .hasSize(1),
                () -> assertThat(ties.getFirst())
                    .as("the returned element must be the tie, not the beam")
                    .isSameAs(tieOnly)
            );
        }
    }
}
