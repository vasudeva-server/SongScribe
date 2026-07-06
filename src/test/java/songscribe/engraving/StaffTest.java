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

package songscribe.engraving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class StaffTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 28: spToSs — sp × STAFF_POSITION_OFFSET_SS
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SpToSs {

        @Test
        void testZeroPositionReturnsZero() {
            assertThat(Staff.spToSs(0)).isEqualTo(0.0);
        }

        @Test
        void testPositivePositionScalesByOffset() {
            final int positionSp = 2;
            final double expectedSs = positionSp * Staff.STAFF_POSITION_OFFSET_SS;
            assertThat(Staff.spToSs(positionSp)).isEqualTo(expectedSs);
        }

        @Test
        void testNegativePositionScalesByOffset() {
            final int positionSp = -4;
            final double expectedSs = positionSp * Staff.STAFF_POSITION_OFFSET_SS;
            assertThat(Staff.spToSs(positionSp)).isEqualTo(expectedSs);
        }

        @Test
        void testRoundTripSpToSsToSp() {
            final int originalSp = 6;
            assertThat(Staff.ssToSp(Staff.spToSs(originalSp))).isEqualTo(originalSp);
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
            assertThat(Staff.ssToSp(0.0)).isEqualTo(0);
        }

        @Test
        void testExactPositiveConversion() {
            // 0.5ss / 0.5 = 1.0sp exactly
            assertThat(Staff.ssToSp(Staff.STAFF_POSITION_OFFSET_SS)).isEqualTo(1);
        }

        @Test
        void testExactNegativeConversion() {
            // -0.5ss / 0.5 = -1.0sp exactly
            final int expectedSp = -1;
            assertThat(Staff.ssToSp(-Staff.STAFF_POSITION_OFFSET_SS)).isEqualTo(expectedSp);
        }

        @Test
        void testPositiveHalfBoundaryRoundsUp() {
            // 0.25ss = exactly 0.5sp; Math.round(0.5) = 1 (half-up)
            final double halfBoundarySs = Staff.STAFF_POSITION_OFFSET_SS / 2;
            assertThat(Staff.ssToSp(halfBoundarySs)).isEqualTo(1);
        }

        @Test
        void testBelowPositiveHalfBoundaryRoundsDown() {
            // 0.125ss = 0.25sp; Math.round(0.25) = 0 (clearly below 0.5)
            final double belowHalfBoundarySs = Staff.STAFF_POSITION_OFFSET_SS / 2 / 2;
            assertThat(Staff.ssToSp(belowHalfBoundarySs)).isEqualTo(0);
        }

        @Test
        void testNegativeHalfBoundaryRoundsTowardPositiveInfinity() {
            // -0.25ss = exactly -0.5sp; Java Math.round(-0.5) = 0 (rounds toward +inf), NOT -1
            final double negHalfBoundarySs = -Staff.STAFF_POSITION_OFFSET_SS / 2;
            assertThat(Staff.ssToSp(negHalfBoundarySs)).isEqualTo(0);
        }

        @Test
        void testPastNegativeHalfBoundaryRoundsToNegative() {
            // -0.375ss = -0.75sp; Math.round(-0.75) = -1
            final double halfBoundarySs = Staff.STAFF_POSITION_OFFSET_SS / 2;
            final double pastNegHalfBoundarySs = -(halfBoundarySs + halfBoundarySs / 2);
            final int expectedSp = -1;
            assertThat(Staff.ssToSp(pastNegHalfBoundarySs)).isEqualTo(expectedSp);
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
                () -> assertThat(Staff.MIN_STAFF_POSITION_SP)
                        .isEqualTo(-(Staff.STAFF_LINES_ABOVE + EXTRA_LEDGER_LINES) * 2),
                () -> assertThat(Staff.MIN_STAFF_POSITION_SP).isEqualTo(expectedSp)
            );
        }

        @Test
        void testMaxStaffPositionSpMatchesFormulaAndConcreteValue() {
            final int expectedSp = 12;
            assertAll(
                () -> assertThat(Staff.MAX_STAFF_POSITION_SP)
                        .isEqualTo((Staff.STAFF_LINES_BELOW + EXTRA_LEDGER_LINES) * 2),
                () -> assertThat(Staff.MAX_STAFF_POSITION_SP).isEqualTo(expectedSp)
            );
        }

        @Test
        void testMinAboveStaffSsMatchesFormulaAndConcreteValue() {
            final double expectedSs = 3.0;
            assertAll(
                () -> assertThat(Staff.MIN_ABOVE_STAFF_SS)
                        .isEqualTo(Math.abs(Staff.MIN_STAFF_POSITION_SP)
                                   * Staff.STAFF_POSITION_OFFSET_SS
                                   - Staff.STAFF_HALF_SS),
                () -> assertThat(Staff.MIN_ABOVE_STAFF_SS).isEqualTo(expectedSs)
            );
        }

        @Test
        void testMinBelowStaffSsMatchesFormulaAndConcreteValue() {
            final double expectedSs = 4.0;
            assertAll(
                () -> assertThat(Staff.MIN_BELOW_STAFF_SS)
                        .isEqualTo(Staff.MAX_STAFF_POSITION_SP
                                   * Staff.STAFF_POSITION_OFFSET_SS
                                   - Staff.STAFF_HALF_SS),
                () -> assertThat(Staff.MIN_BELOW_STAFF_SS).isEqualTo(expectedSs)
            );
        }
    }
}
