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
 * Unit tests for {@link Line} beam management: add/merge/remove and query methods.
 *
 * <p>All setup uses {@link Song#withoutMutationTracking} so element and beam
 * installation produce no notification and require no open bracket.
 */
class LineBeamTest extends UnitTest {

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

    /** Adds {@value #NOTE_COUNT} eighth notes to {@code line} via suspended tracking. */
    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.QUAVER));
            }
        });
    }

    // -----------------------------------------------------------------------
    // addBeaming — adjacent merge (row 25)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddBeamingAdjacentMerge {

        /**
         * Adding [0,2] then [2,4] — they share endpoint 2 — must merge into a single
         * beam [0,4]. The beam count must be 1 (not 2) and the span must be exact.
         */
        @Test
        void testAdjacentBeamsWithSharedEndpointMergeIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_0), line.getElement(IDX_2))));

            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_2), line.getElement(IDX_4))));

            var beams = line.findBeamsOverlapping(IDX_0, IDX_4);

            assertAll(
                () -> assertThat(beams)
                    .as("adjacent beams must merge into exactly one beam")
                    .hasSize(1),
                () -> assertThat(beams.getFirst().getAnchorElementIndex())
                    .as("merged beam anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(beams.getFirst().getEndElementIndex())
                    .as("merged beam end must be 4")
                    .isEqualTo(IDX_4)
            );
        }

        /**
         * Beams merge only where they overlap. Two beam groups written back to back —
         * [0,1] then [2,4], sharing no element — are two deliberate groupings, and
         * unlike hairpins they must stay that way.
         */
        @Test
        void testAbuttingBeamsWithNoSharedEndpointStayApart() {
            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_0), line.getElement(IDX_1))));

            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_2), line.getElement(IDX_4))));

            assertThat(line.findBeamsOverlapping(IDX_0, IDX_4))
                .as("beam groups written back to back must remain two groups")
                .hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // addBeaming — subsumed beam removed (row 26)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddBeamingSubsumedRemoved {

        /**
         * Adding [1,3] then [0,4] — the wider span fully covers [1,3] — must produce a
         * single beam [0,4]. The narrow beam must not survive.
         */
        @Test
        void testNarrowBeamIsRemovedWhenWiderSpanCoversIt() {
            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_1), line.getElement(IDX_3))));

            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_0), line.getElement(IDX_4))));

            var allBeams = line.findBeamsOverlapping(IDX_0, IDX_4);

            assertAll(
                () -> assertThat(allBeams)
                    .as("subsumed beam must be removed; only the wide beam remains")
                    .hasSize(1),
                () -> assertThat(allBeams.getFirst().getAnchorElementIndex())
                    .as("surviving beam anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(allBeams.getFirst().getEndElementIndex())
                    .as("surviving beam end must be 4")
                    .isEqualTo(IDX_4)
            );
        }
    }

    // -----------------------------------------------------------------------
    // addBeaming — genuine interior overlap, no shared endpoint
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddBeamingPartialOverlapMerge {

        /**
         * Adding [0,3] then [2,4] — the two overlap only in the interior (element 2),
         * sharing no endpoint — must still merge into a single beam [0,4], with the
         * absorbed [0,3] beam removed. This is the shape a re-anchored pasted beam
         * produces when it lands inside an existing one.
         */
        @Test
        void testInteriorOverlapWithNoSharedEndpointMergesIntoOneSpan() {
            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_0), line.getElement(IDX_3))));

            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_2), line.getElement(IDX_4))));

            var beams = line.findBeamsOverlapping(IDX_0, IDX_4);

            assertAll(
                () -> assertThat(beams)
                    .as("interior overlap must merge into exactly one beam; the absorbed span must be removed")
                    .hasSize(1),
                () -> assertThat(beams.getFirst().getAnchorElementIndex())
                    .as("merged beam anchor must be 0")
                    .isEqualTo(IDX_0),
                () -> assertThat(beams.getFirst().getEndElementIndex())
                    .as("merged beam end must be 4")
                    .isEqualTo(IDX_4)
            );
        }
    }

    // -----------------------------------------------------------------------
    // mergeOverlappingSpans — absorption predicate branch coverage
    // -----------------------------------------------------------------------

    /**
     * {@code mergeOverlappingSpans}'s two absorption predicates are conjunctions:
     * <ul>
     *   <li>anchor side: {@code candidateAnchor <= anchorIdx && anchorIdx <= candidateEnd + reach}</li>
     *   <li>end side: {@code candidateAnchor - reach <= endIdx && endIdx <= candidateEnd}</li>
     * </ul>
     * Beams never absorb an adjacent span ({@code reach == 0}). Each test below sets up a
     * candidate that satisfies exactly one half of one conjunction, so the other half alone
     * must keep it from being absorbed — proving the {@code &&} cannot be relaxed to
     * {@code ||} without a failure.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MergePredicateBranchCoverage {

        /**
         * Anchor side, first half only false: the candidate [3,4] starts after the new
         * beam's anchor (3), so {@code candidateAnchor <= anchorIdx} fails even though
         * {@code anchorIdx <= candidateEnd + reach} (2 <= 4) holds.
         */
        @Test
        void testCandidateStartingAfterNewAnchorFailsFirstHalfAndIsNotAbsorbed() {
            var candidate = new Beam(line.getElement(IDX_3), line.getElement(IDX_4));
            var added = new Beam(line.getElement(IDX_2), line.getElement(IDX_2));

            song.withoutMutationTracking(() -> line.addBeaming(candidate));
            song.withoutMutationTracking(() -> line.addBeaming(added));

            assertAll(
                () -> assertThat(line.findBeamsOverlapping(IDX_0, IDX_4))
                    .as("candidate starting after the new beam's anchor must not be absorbed")
                    .hasSize(2),
                () -> assertThat(added.getAnchorElementIndex())
                    .as("new beam's anchor must stay 2, not shrink toward the candidate's anchor")
                    .isEqualTo(IDX_2)
            );
        }

        /**
         * Anchor side, second half only false: the candidate [0,1] starts at or before
         * the new beam's anchor (3), so {@code candidateAnchor <= anchorIdx} (0 <= 3)
         * holds, but {@code anchorIdx <= candidateEnd + reach} (3 <= 1) fails.
         */
        @Test
        void testCandidateEndingBeforeNewAnchorFailsSecondHalfAndIsNotAbsorbed() {
            var candidate = new Beam(line.getElement(IDX_0), line.getElement(IDX_1));
            var added = new Beam(line.getElement(IDX_3), line.getElement(IDX_3));

            song.withoutMutationTracking(() -> line.addBeaming(candidate));
            song.withoutMutationTracking(() -> line.addBeaming(added));

            assertAll(
                () -> assertThat(line.findBeamsOverlapping(IDX_0, IDX_4))
                    .as("candidate ending before the new beam's anchor must not be absorbed")
                    .hasSize(2),
                () -> assertThat(added.getAnchorElementIndex())
                    .as("new beam's anchor must stay 3, not widen toward the candidate's anchor")
                    .isEqualTo(IDX_3)
            );
        }

        /**
         * End side, first half only false: the candidate [3,4] starts too far past the
         * new beam's end (2) for {@code candidateAnchor - reach <= endIdx} (3 <= 2) to
         * hold, even though {@code endIdx <= candidateEnd} (2 <= 4) does.
         */
        @Test
        void testCandidateStartingAfterNewEndFailsFirstHalfAndIsNotAbsorbed() {
            var candidate = new Beam(line.getElement(IDX_3), line.getElement(IDX_4));
            var added = new Beam(line.getElement(IDX_1), line.getElement(IDX_2));

            song.withoutMutationTracking(() -> line.addBeaming(candidate));
            song.withoutMutationTracking(() -> line.addBeaming(added));

            assertAll(
                () -> assertThat(line.findBeamsOverlapping(IDX_0, IDX_4))
                    .as("candidate starting after the new beam's end must not be absorbed")
                    .hasSize(2),
                () -> assertThat(added.getEndElementIndex())
                    .as("new beam's end must stay 2, not widen toward the candidate's end")
                    .isEqualTo(IDX_2)
            );
        }

        /**
         * End side, second half only false: the candidate [0,1] starts at or before the
         * new beam's end (4) for {@code candidateAnchor - reach <= endIdx} (0 <= 4) to
         * hold, but {@code endIdx <= candidateEnd} (4 <= 1) fails.
         */
        @Test
        void testCandidateEndingBeforeNewEndFailsSecondHalfAndIsNotAbsorbed() {
            var candidate = new Beam(line.getElement(IDX_0), line.getElement(IDX_1));
            var added = new Beam(line.getElement(IDX_3), line.getElement(IDX_4));

            song.withoutMutationTracking(() -> line.addBeaming(candidate));
            song.withoutMutationTracking(() -> line.addBeaming(added));

            assertAll(
                () -> assertThat(line.findBeamsOverlapping(IDX_0, IDX_4))
                    .as("candidate ending before the new beam's end must not be absorbed")
                    .hasSize(2),
                () -> assertThat(added.getEndElementIndex())
                    .as("new beam's end must stay 4, not shrink toward the candidate's end")
                    .isEqualTo(IDX_4)
            );
        }
    }

    // -----------------------------------------------------------------------
    // removeBeaming — absent beam is no-op (row 27)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RemoveBeamingAbsentIsNoOp {

        /**
         * Removing a {@link Beam} that was never added must leave the line's beam
         * collection unchanged and must not throw.
         */
        @Test
        void testRemoveBeamNotOnLineIsNoOp() {
            // Install one beam so the collection is non-empty.
            song.withoutMutationTracking(() ->
                line.addBeaming(new Beam(line.getElement(IDX_0), line.getElement(IDX_2))));

            // A beam instance that was never added to the line.
            var absentBeam = new Beam(line.getElement(IDX_1), line.getElement(IDX_3));

            song.withoutMutationTracking(() -> line.removeBeaming(absentBeam));

            // The original beam must still be present, and only it.
            var beams = line.findBeamsOverlapping(IDX_0, IDX_4);
            assertAll(
                () -> assertThat(beams)
                    .as("collection unchanged after no-op remove: still one beam")
                    .hasSize(1),
                () -> assertThat(beams.getFirst().getAnchorElementIndex())
                    .as("surviving beam anchor unchanged")
                    .isEqualTo(IDX_0),
                () -> assertThat(beams.getFirst().getEndElementIndex())
                    .as("surviving beam end unchanged")
                    .isEqualTo(IDX_2)
            );
        }
    }

    // -----------------------------------------------------------------------
    // findBeamsOverlapping (row 30)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindBeamsOverlapping {

        // Two non-overlapping beams: [0,1] and [3,4]
        private Beam beamLeft;
        private Beam beamRight;

        @BeforeEach
        void addBeams() {
            beamLeft = new Beam(line.getElement(IDX_0), line.getElement(IDX_1));
            beamRight = new Beam(line.getElement(IDX_3), line.getElement(IDX_4));
            var left = beamLeft;
            var right = beamRight;
            song.withoutMutationTracking(() -> {
                line.addBeaming(left);
                line.addBeaming(right);
            });
        }

        @Test
        void testQuerySpanningBothBeamsReturnsBoth() {
            var result = line.findBeamsOverlapping(IDX_0, IDX_4);

            assertThat(result)
                .as("query [0,4] must return both beams")
                .hasSize(2);
        }

        @Test
        void testQueryTouchingOnlyLeftBeamReturnsLeftOnly() {
            var result = line.findBeamsOverlapping(IDX_0, IDX_1);

            assertThat(result)
                .as("query [0,1] must return only the left beam")
                .containsExactly(beamLeft);
        }

        @Test
        void testQueryTouchingOnlyRightBeamReturnsRightOnly() {
            var result = line.findBeamsOverlapping(IDX_3, IDX_4);

            assertThat(result)
                .as("query [3,4] must return only the right beam")
                .containsExactly(beamRight);
        }

        @Test
        void testQueryInGapBetweenBeamsReturnsEmpty() {
            // Index 2 sits in the gap between [0,1] and [3,4].
            var result = line.findBeamsOverlapping(IDX_2, IDX_2);

            assertThat(result)
                .as("query [2,2] in the gap must return no beams")
                .isEmpty();
        }

        @Test
        void testQueryOutsideAllBeamsReturnsEmpty() {
            // Build a fresh line with a single beam [1,2] and query entirely outside it.
            var song2 = new Song();
            var line2 = song2.getLine(0);
            song2.withoutMutationTracking(() -> {
                for (var i = 0; i < NOTE_COUNT; i++) {
                    line2.addElement(new StaffElement(ElementType.QUAVER));
                }
                line2.addBeaming(new Beam(line2.getElement(IDX_1), line2.getElement(IDX_2)));
            });

            // Query [3,4] — entirely to the right of [1,2].
            var result = line2.findBeamsOverlapping(IDX_3, IDX_4);

            assertThat(result)
                .as("query [3,4] outside beam [1,2] must return empty list")
                .isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // sameBeamAt
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SameBeamAt {

        // Two separate beams: [0,1] and [3,4]; index 2 is unbeamed.
        @BeforeEach
        void addBeams() {
            song.withoutMutationTracking(() -> {
                line.addBeaming(new Beam(line.getElement(IDX_0), line.getElement(IDX_1)));
                line.addBeaming(new Beam(line.getElement(IDX_3), line.getElement(IDX_4)));
            });
        }

        @Test
        void testBothIndicesInsideOneBeamReturnsTrue() {
            assertThat(line.sameBeamAt(IDX_0, IDX_1))
                .as("both indices are covered by the same beam [0,1]")
                .isTrue();
        }

        @Test
        void testIndicesInDifferentBeamsReturnsFalse() {
            assertThat(line.sameBeamAt(IDX_0, IDX_4))
                .as("index 0 is in beam [0,1] and index 4 is in a different beam [3,4]")
                .isFalse();
        }

        @Test
        void testOneIndexBeamedAndOtherNotReturnsFalse() {
            assertThat(line.sameBeamAt(IDX_0, IDX_2))
                .as("index 0 is beamed but index 2 is not part of any beam")
                .isFalse();
        }

        @Test
        void testNeitherIndexBeamedReturnsFalse() {
            var song2 = new Song();
            var line2 = song2.getLine(0);
            song2.withoutMutationTracking(() -> {
                for (var i = 0; i < NOTE_COUNT; i++) {
                    line2.addElement(new StaffElement(ElementType.QUAVER));
                }
            });

            assertThat(line2.sameBeamAt(IDX_0, IDX_1))
                .as("neither index is part of any beam")
                .isFalse();
        }

        @Test
        void testNegativeIndexReturnsFalseRatherThanThrow() {
            assertThat(line.sameBeamAt(-1, IDX_0))
                .as("a negative index must not match any beam and must not throw")
                .isFalse();
        }

        @Test
        void testIndexPastElementCountReturnsFalseRatherThanThrow() {
            assertThat(line.sameBeamAt(line.elementCount(), IDX_0))
                .as("an index past the last element must not match any beam and must not throw")
                .isFalse();
        }

        @Test
        void testBothIndicesOnEmptyLineReturnsFalse() {
            var emptySong = new Song();
            var emptyLine = emptySong.getLine(0);

            assertThat(emptyLine.sameBeamAt(IDX_0, IDX_1))
                .as("an empty line has no beams to match")
                .isFalse();
        }
    }
}
