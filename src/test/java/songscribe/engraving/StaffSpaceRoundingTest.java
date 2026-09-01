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

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rounding and range-checking arithmetic {@link StaffPosition} and {@link Staff} share for
 * converting a staff-space measurement to a discrete grid position or half-space count.
 *
 * <p>The two conversions sit together because their contracts require they round alike and
 * differ only in how they handle a value outside the grid: a distance from the middle line
 * resolves to {@code null} when it rounds off {@link StaffPosition#MIN_SP}..{@link
 * StaffPosition#MAX_SP}, while a distance travelled has no grid to fall off.
 */
class StaffSpaceRoundingTest extends UnitTest {

    /** Half way between two adjacent staff positions — where the rounding rule is observable. */
    private static final double HALF_WAY_SS = Staff.HALF_SPACE_SS / 2;

    /** Enough to fall off a tie, and far too little to reach the next position. */
    private static final double A_HAIR_SS = 0.01;

    /**
     * How far past each end of the position grid the half-space round trip runs. Enough half
     * spaces that a count out there is unmistakably outside the grid rather than on its edge.
     */
    private static final int HALF_SPACES_PAST_THE_GRID = 8;

    /**
     * One staff-space measurement and the staff position it resolves to.
     *
     * @param description the case, as the test's display name
     * @param ss          the measurement, in staff spaces from the middle line, Y-down
     * @param expectedSp  the staff position it must resolve to, in half staff spaces
     */
    private record SsToSpCase(String description, double ss, int expectedSp) {}

    /**
     * Every case is exactly on a position, exactly half way between two, or just off half way,
     * because half way is the only input where the rounding rule is observable.
     *
     * <p>The rule is round-half-up, not round-half-away-from-zero: {@code Math.round} adds a half
     * and takes the floor, so a measurement half way between two positions resolves to the lower
     * one on both sides of the middle line — downward on the page below it, and toward it above.
     */
    static Stream<SsToSpCase> ssToSpCases() {
        return Stream.of(
            new SsToSpCase("the middle line is position zero", 0.0, 0),
            new SsToSpCase("a whole staff space below is two positions down", 1.0, 2),
            new SsToSpCase("a whole staff space above is two positions up", -1.0, -2),
            new SsToSpCase("half way below the middle line rounds down the page", 0.25, 1),
            new SsToSpCase("half way above the middle line also rounds down the page", -0.25, 0),
            new SsToSpCase("just short of half way below stays on the middle line", 0.24, 0),
            new SsToSpCase("just past half way above reaches the position above", -0.26, -1),
            new SsToSpCase("the highest valid position",
                StaffPosition.toSs(StaffPosition.MIN_SP), StaffPosition.MIN_SP),
            new SsToSpCase("one position inside the highest valid one",
                StaffPosition.toSs(StaffPosition.MIN_SP + 1), StaffPosition.MIN_SP + 1),
            new SsToSpCase("the lowest valid position",
                StaffPosition.toSs(StaffPosition.MAX_SP), StaffPosition.MAX_SP),
            new SsToSpCase("one position inside the lowest valid one",
                StaffPosition.toSs(StaffPosition.MAX_SP - 1), StaffPosition.MAX_SP - 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ssToSpCases")
    void testStaffSpacesResolveToTheNearestStaffPosition(SsToSpCase testCase) {
        assertThat(StaffPosition.atSs(testCase.ss())).isEqualTo(testCase.expectedSp());
    }

    /**
     * One staff-space measurement and whether it lands on the position grid at all.
     *
     * @param description the case, as the test's display name
     * @param ss          the measurement, in staff spaces from the middle line, Y-down
     * @param onTheGrid   whether it must round onto a valid position rather than off the grid
     */
    private record ContainsSsCase(String description, double ss, boolean onTheGrid) {}

    /**
     * The cases that separate "at the extreme position" from "past it" — the distinction a
     * {@code null} result cannot carry on its own, so each row states which side of the
     * boundary a measurement falls on.
     *
     * <p>The two half-way rows are the ones that matter, and they are deliberately not mirror
     * images: round-half-up means a measurement exactly half a position past the top of the grid
     * rounds back onto it, while one exactly half a position past the bottom rounds off it. The
     * grid is asymmetric at its edges for the same reason the rounding is.
     */
    static Stream<ContainsSsCase> containsSsCases() {
        return Stream.of(
            new ContainsSsCase("the middle line is on the grid", 0.0, true),
            new ContainsSsCase("a whole staff space below is on the grid", 1.0, true),
            new ContainsSsCase("the highest valid position is on the grid",
                StaffPosition.toSs(StaffPosition.MIN_SP), true),
            new ContainsSsCase("the lowest valid position is on the grid",
                StaffPosition.toSs(StaffPosition.MAX_SP), true),
            new ContainsSsCase("half a position above the highest valid one rounds back onto it",
                StaffPosition.toSs(StaffPosition.MIN_SP) - HALF_WAY_SS, true),
            new ContainsSsCase("a hair further above leaves the grid",
                StaffPosition.toSs(StaffPosition.MIN_SP) - HALF_WAY_SS - A_HAIR_SS, false),
            new ContainsSsCase("just short of half a position below the lowest valid one stays on",
                StaffPosition.toSs(StaffPosition.MAX_SP) + HALF_WAY_SS - A_HAIR_SS, true),
            new ContainsSsCase("half a position below the lowest valid one leaves the grid",
                StaffPosition.toSs(StaffPosition.MAX_SP) + HALF_WAY_SS, false),
            new ContainsSsCase("a whole position above the highest valid one is off the grid",
                StaffPosition.toSs(StaffPosition.MIN_SP - 1), false),
            new ContainsSsCase("a whole position below the lowest valid one is off the grid",
                StaffPosition.toSs(StaffPosition.MAX_SP + 1), false));
    }

    /** The rows of {@link #containsSsCases()} that fall outside the grid. */
    static Stream<ContainsSsCase> offTheGridCases() {
        return containsSsCases().filter(testCase -> !testCase.onTheGrid());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("containsSsCases")
    void testStaffSpacesOffTheGridResolveToNoPosition(ContainsSsCase testCase) {
        if (testCase.onTheGrid()) {
            assertThat(StaffPosition.atSs(testCase.ss())).isNotNull();
        } else {
            assertThat(StaffPosition.atSs(testCase.ss())).isNull();
        }
    }

    /**
     * One staff-space distance and the count of half staff spaces it spans.
     *
     * @param description        the case, as the test's display name
     * @param ss                 the distance, in staff spaces, Y-down
     * @param expectedHalfSpaces the count of half staff spaces it must resolve to
     */
    private record SsToHalfSpacesCase(String description, double ss, int expectedHalfSpaces) {}

    /**
     * The same inputs as {@link #ssToSpCases()}, read as a distance travelled rather than as a
     * distance from the middle line. The two tables sit together because the only thing that
     * separates the conversions is which of them has a grid to fall off, and a divergence in how
     * they round is visible here and nowhere else.
     *
     * <p>The rounding rule is the one documented on {@code ssToSpCases}: round-half-up, so a
     * distance exactly half way between two half spaces resolves toward positive on both sides
     * of zero.
     */
    static Stream<SsToHalfSpacesCase> ssToHalfSpacesCases() {
        return Stream.of(
            new SsToHalfSpacesCase("no travel spans no half spaces", 0.0, 0),
            new SsToHalfSpacesCase("a whole staff space down spans two half spaces", 1.0, 2),
            new SsToHalfSpacesCase("a whole staff space up spans two the other way", -1.0, -2),
            new SsToHalfSpacesCase("half way down snaps down the page", 0.25, 1),
            new SsToHalfSpacesCase("half way up also snaps down the page", -0.25, 0),
            new SsToHalfSpacesCase("just short of half way down snaps back", 0.24, 0),
            new SsToHalfSpacesCase("just past half way up reaches the next half space", -0.26, -1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ssToHalfSpacesCases")
    void testADistanceResolvesToTheNearestWholeCountOfHalfSpaces(SsToHalfSpacesCase testCase) {
        assertThat(Staff.ssToHalfSpaces(testCase.ss())).isEqualTo(testCase.expectedHalfSpaces());
    }

    /**
     * Every count of half spaces the round trip runs over: the whole position grid, and a margin
     * past each end of it. The margin is what states the promise a distance conversion makes and
     * a position conversion cannot — a distance is not bounded by the grid, so a count beyond it
     * must come back whole rather than resolving to {@code null} the way {@link
     * StaffPosition#atSs} would.
     */
    static IntStream halfSpaceCounts() {
        return IntStream.rangeClosed(
            StaffPosition.MIN_SP - HALF_SPACES_PAST_THE_GRID,
            StaffPosition.MAX_SP + HALF_SPACES_PAST_THE_GRID);
    }

    @ParameterizedTest(name = "{0} half spaces")
    @MethodSource("halfSpaceCounts")
    void testACountOfHalfSpacesSurvivesTheRoundTripThroughStaffSpaces(int halfSpaces) {
        assertThat(Staff.ssToHalfSpaces(Staff.halfSpacesToSs(halfSpaces))).isEqualTo(halfSpaces);
    }

}
