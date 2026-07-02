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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.layout.StaffExtents;

class StaffExtentsTest extends UnitTest {

    private static final double LINE_WIDTH_SS = 64.0;

    // Both top and bot arrays default to the middle staff line (Y-down, middle line = 0),
    // the same coordinate system StackingUtils' anchor calculations use.
    private static final double DEFAULT_EXTENT_SS = 0.0;

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CopyTopFrom {

        @Test
        void testCopiedExtentsMatchSource() {
            var source = new StaffExtents(LINE_WIDTH_SS);
            source.ySet(true, 10.0, 5.0, -3.0);
            source.ySet(true, 30.0, 8.0, -5.0);

            var target = new StaffExtents(LINE_WIDTH_SS);
            target.copyTopFrom(source);

            assertThat(target.yGet(true, 10.0, 5.0)).isEqualTo(-3.0);
            assertThat(target.yGet(true, 30.0, 8.0)).isEqualTo(-5.0);
        }

        @Test
        void testCopyDoesNotAffectBotArray() {
            var source = new StaffExtents(LINE_WIDTH_SS);
            source.ySet(true, 10.0, 5.0, -2.0);
            source.ySet(false, 10.0, 5.0, 6.0);

            var target = new StaffExtents(LINE_WIDTH_SS);
            target.copyTopFrom(source);

            assertThat(target.yGet(false, 10.0, 5.0)).isEqualTo(DEFAULT_EXTENT_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InitializationDefaults {

        @Test
        void testBotDefaultsToMiddleLine() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            assertThat(extents.yGet(false, 0.0, LINE_WIDTH_SS)).isEqualTo(DEFAULT_EXTENT_SS);
        }

        @Test
        void testTopDefaultsToMiddleLine() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            assertThat(extents.yGet(true, 0.0, LINE_WIDTH_SS)).isEqualTo(DEFAULT_EXTENT_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OverlappingReservations {

        @Test
        void testAboveOverlapKeepsHigherExtent() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            // First reservation at Y = -2.0
            extents.ySet(true, 10.0, 10.0, -2.0);
            // Overlapping reservation at Y = -4.0 (higher, since Y-down)
            extents.ySet(true, 15.0, 10.0, -4.0);

            // In the overlap region (15-20), the higher extent (-4.0) wins
            assertThat(extents.yGet(true, 15.0, 5.0)).isEqualTo(-4.0);
            // In the non-overlap region (10-15), only the first reservation
            assertThat(extents.yGet(true, 10.0, 4.0)).isEqualTo(-2.0);
        }

        @Test
        void testAboveOverlapIgnoresLowerExtent() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            // First reservation at Y = -4.0
            extents.ySet(true, 10.0, 10.0, -4.0);
            // Overlapping reservation at Y = -1.0 (lower, does not override)
            extents.ySet(true, 10.0, 10.0, -1.0);

            assertThat(extents.yGet(true, 10.0, 10.0)).isEqualTo(-4.0);
        }

        @Test
        void testBelowOverlapKeepsLowerExtent() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            extents.ySet(false, 10.0, 10.0, 5.0);
            extents.ySet(false, 15.0, 10.0, 7.0);

            // In the overlap region, the lower extent (7.0) wins
            assertThat(extents.yGet(false, 15.0, 5.0)).isEqualTo(7.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StepClamping {

        @Test
        void testNegativeXClampedToFirstStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, -5.0, 3.0, -2.0);

            // Should be readable starting from X = 0
            assertThat(extents.yGet(true, 0.0, 3.0)).isEqualTo(-2.0);
        }

        @Test
        void testWidthBeyondLineEndClampedToLastStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 60.0, 10.0, -3.0);

            // Query at the end of the line should see the reservation
            assertThat(extents.yGet(true, 60.0, 10.0)).isEqualTo(-3.0);
        }

        @Test
        void testXAtExactLineWidthClampedToLastStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, LINE_WIDTH_SS, 5.0, -1.5);

            assertThat(extents.yGet(true, LINE_WIDTH_SS, 5.0)).isEqualTo(-1.5);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class YGetBasicOperations {

        @Test
        void testAboveQueryReturnsHighestExtentAcrossRange() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            // Two reservations at different heights in the same query range
            extents.ySet(true, 10.0, 5.0, -2.0);
            extents.ySet(true, 13.0, 5.0, -5.0);

            // Query spanning both: should return the highest (most negative)
            assertThat(extents.yGet(true, 10.0, 8.0)).isEqualTo(-5.0);
        }

        @Test
        void testBelowQueryReturnsLowestExtentAcrossRange() {
            var extents = new StaffExtents(LINE_WIDTH_SS);

            extents.ySet(false, 10.0, 5.0, 5.0);
            extents.ySet(false, 13.0, 5.0, 8.0);

            assertThat(extents.yGet(false, 10.0, 8.0)).isEqualTo(8.0);
        }

        @Test
        void testQueryOutsideReservationReturnsDefault() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 10.0, 5.0, -3.0);

            // Query a different region — should still be at default
            assertThat(extents.yGet(true, 30.0, 5.0)).isEqualTo(DEFAULT_EXTENT_SS);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class YSetBasicOperations {

        @Test
        void testAboveReservationUpdatesTopArray() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 10.0, 5.0, -2.0);

            assertThat(extents.yGet(true, 10.0, 5.0)).isEqualTo(-2.0);
        }

        @Test
        void testBelowReservationUpdatesBotArray() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(false, 10.0, 5.0, 6.0);

            assertThat(extents.yGet(false, 10.0, 5.0)).isEqualTo(6.0);
        }

        @Test
        void testReservationDoesNotAffectOtherDirection() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 10.0, 5.0, -2.0);

            // Below direction should still be at default
            assertThat(extents.yGet(false, 10.0, 5.0)).isEqualTo(DEFAULT_EXTENT_SS);
        }

        @Test
        void testZeroWidthReservationAffectsSingleStep() {
            var extents = new StaffExtents(LINE_WIDTH_SS);
            extents.ySet(true, 32.0, 0.0, -1.0);

            assertThat(extents.yGet(true, 32.0, 0.0)).isEqualTo(-1.0);
        }
    }

    // -----------------------------------------------------------------------
    // Row 28: spToSs — sp × STAFF_POSITION_OFFSET_SS
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SpToSs {

        @Test
        void testZeroPositionReturnsZero() {
            assertThat(StaffExtents.spToSs(0)).isEqualTo(0.0);
        }

        @Test
        void testPositivePositionScalesByOffset() {
            final int positionSp = 2;
            final double expectedSs = positionSp * StaffExtents.STAFF_POSITION_OFFSET_SS;
            assertThat(StaffExtents.spToSs(positionSp)).isEqualTo(expectedSs);
        }

        @Test
        void testNegativePositionScalesByOffset() {
            final int positionSp = -4;
            final double expectedSs = positionSp * StaffExtents.STAFF_POSITION_OFFSET_SS;
            assertThat(StaffExtents.spToSs(positionSp)).isEqualTo(expectedSs);
        }

        @Test
        void testRoundTripSpToSsToSp() {
            final int originalSp = 6;
            assertThat(StaffExtents.ssToSp(StaffExtents.spToSs(originalSp))).isEqualTo(originalSp);
        }
    }

    // -----------------------------------------------------------------------
    // Row 29: ssToSp — Math.round(ss / STAFF_POSITION_OFFSET_SS); boundary cases
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SsToSp {

        @Test
        void testZeroReturnsZero() {
            assertThat(StaffExtents.ssToSp(0.0)).isEqualTo(0);
        }

        @Test
        void testExactPositiveConversion() {
            // 0.5ss / 0.5 = 1.0sp exactly
            assertThat(StaffExtents.ssToSp(StaffExtents.STAFF_POSITION_OFFSET_SS)).isEqualTo(1);
        }

        @Test
        void testExactNegativeConversion() {
            // -0.5ss / 0.5 = -1.0sp exactly
            final int expectedSp = -1;
            assertThat(StaffExtents.ssToSp(-StaffExtents.STAFF_POSITION_OFFSET_SS)).isEqualTo(expectedSp);
        }

        @Test
        void testPositiveHalfBoundaryRoundsUp() {
            // 0.25ss = exactly 0.5sp; Math.round(0.5) = 1 (half-up)
            final double halfBoundarySs = StaffExtents.STAFF_POSITION_OFFSET_SS / 2;
            assertThat(StaffExtents.ssToSp(halfBoundarySs)).isEqualTo(1);
        }

        @Test
        void testBelowPositiveHalfBoundaryRoundsDown() {
            // 0.125ss = 0.25sp; Math.round(0.25) = 0 (clearly below 0.5)
            final double belowHalfBoundarySs = StaffExtents.STAFF_POSITION_OFFSET_SS / 2 / 2;
            assertThat(StaffExtents.ssToSp(belowHalfBoundarySs)).isEqualTo(0);
        }

        @Test
        void testNegativeHalfBoundaryRoundsTowardPositiveInfinity() {
            // -0.25ss = exactly -0.5sp; Java Math.round(-0.5) = 0 (rounds toward +inf), NOT -1
            final double negHalfBoundarySs = -StaffExtents.STAFF_POSITION_OFFSET_SS / 2;
            assertThat(StaffExtents.ssToSp(negHalfBoundarySs)).isEqualTo(0);
        }

        @Test
        void testPastNegativeHalfBoundaryRoundsToNegative() {
            // -0.375ss = -0.75sp; Math.round(-0.75) = -1
            final double halfBoundarySs = StaffExtents.STAFF_POSITION_OFFSET_SS / 2;
            final double pastNegHalfBoundarySs = -(halfBoundarySs + halfBoundarySs / 2);
            final int expectedSp = -1;
            assertThat(StaffExtents.ssToSp(pastNegHalfBoundarySs)).isEqualTo(expectedSp);
        }
    }

    // -----------------------------------------------------------------------
    // Row 33: derived constants — MIN/MAX_STAFF_POSITION_SP, MIN_ABOVE/BELOW_STAFF_SS
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DerivedConstants {

        // STAFF_LINES_ABOVE/BELOW + 2 ledger lines on each side defines the range
        private static final int EXTRA_LEDGER_LINES = 2;

        @Test
        void testMinStaffPositionSpMatchesFormulaAndConcreteValue() {
            final int expectedSp = -10;
            assertAll(
                () -> assertThat(StaffExtents.MIN_STAFF_POSITION_SP)
                        .isEqualTo(-(StaffExtents.STAFF_LINES_ABOVE + EXTRA_LEDGER_LINES) * 2),
                () -> assertThat(StaffExtents.MIN_STAFF_POSITION_SP).isEqualTo(expectedSp)
            );
        }

        @Test
        void testMaxStaffPositionSpMatchesFormulaAndConcreteValue() {
            final int expectedSp = 12;
            assertAll(
                () -> assertThat(StaffExtents.MAX_STAFF_POSITION_SP)
                        .isEqualTo((StaffExtents.STAFF_LINES_BELOW + EXTRA_LEDGER_LINES) * 2),
                () -> assertThat(StaffExtents.MAX_STAFF_POSITION_SP).isEqualTo(expectedSp)
            );
        }

        @Test
        void testMinAboveStaffSsMatchesFormulaAndConcreteValue() {
            final double expectedSs = 3.0;
            assertAll(
                () -> assertThat(StaffExtents.MIN_ABOVE_STAFF_SS)
                        .isEqualTo(Math.abs(StaffExtents.MIN_STAFF_POSITION_SP)
                                   * StaffExtents.STAFF_POSITION_OFFSET_SS
                                   - StaffExtents.STAFF_HALF_SS),
                () -> assertThat(StaffExtents.MIN_ABOVE_STAFF_SS).isEqualTo(expectedSs)
            );
        }

        @Test
        void testMinBelowStaffSsMatchesFormulaAndConcreteValue() {
            final double expectedSs = 4.0;
            assertAll(
                () -> assertThat(StaffExtents.MIN_BELOW_STAFF_SS)
                        .isEqualTo(StaffExtents.MAX_STAFF_POSITION_SP
                                   * StaffExtents.STAFF_POSITION_OFFSET_SS
                                   - StaffExtents.STAFF_HALF_SS),
                () -> assertThat(StaffExtents.MIN_BELOW_STAFF_SS).isEqualTo(expectedSs)
            );
        }
    }
}
