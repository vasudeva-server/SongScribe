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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import songscribe.UnitTest;

/**
 * Tests for {@link Spring#of}.
 */
class SpringTest extends UnitTest {

    // Rest gap comfortably larger than the strut, so the gap has slack to give up.
    private static final double REST_ABOVE_STRUT_SS = 3.0;
    private static final double STRUT_BELOW_REST_SS = 1.0;

    // A tight beam-internal reduction weight, and a fresh rest for the withRestSs preservation test.
    private static final double TIGHT_WEIGHT = 0.6;
    private static final double NEW_REST_SS = 5.0;

    private static final double DELTA = 1e-9;

    // --- Phase 5: level offset defaulting, preservation, and the withCorrectionSs derivation ---
    private static final double NO_LEVEL_OFFSET_SS = 0.0;
    private static final double LEVEL_OFFSET_SS = 0.75;
    private static final double WIDENING_CORRECTION_SS = 0.4;
    private static final double NARROWING_CORRECTION_SS = -0.3;

    @Test
    void testTwoArgOfDefaultsToNormalWeightAndNotLiftExempt() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS);

        assertThat(spring.weight()).isEqualTo(Spring.NORMAL_WEIGHT);
        assertThat(spring.liftExempt()).isFalse();
    }

    @Test
    void testFourArgOfCarriesWeightAndLiftExempt() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true);

        assertThat(spring.weight()).isEqualTo(TIGHT_WEIGHT);
        assertThat(spring.liftExempt()).isTrue();
    }

    @Test
    void testWithRestSsPreservesWeightAndLiftExempt() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true)
            .withRestSs(NEW_REST_SS);

        assertThat(spring.restSs()).isEqualTo(NEW_REST_SS);
        assertThat(spring.strutSs()).isEqualTo(STRUT_BELOW_REST_SS);
        assertThat(spring.weight()).isEqualTo(TIGHT_WEIGHT);
        assertThat(spring.liftExempt()).isTrue();
    }

    @Test
    void testTwoArgOfDefaultsLevelOffsetToZero() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS);

        assertThat(spring.levelOffsetSs()).isEqualTo(NO_LEVEL_OFFSET_SS);
    }

    @Test
    void testFourArgOfDefaultsLevelOffsetToZero() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true);

        assertThat(spring.levelOffsetSs()).isEqualTo(NO_LEVEL_OFFSET_SS);
    }

    @Test
    void testFiveArgOfSetsLevelOffset() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true, LEVEL_OFFSET_SS);

        assertThat(spring.levelOffsetSs()).isEqualTo(LEVEL_OFFSET_SS);
        assertThat(spring.weight()).isEqualTo(TIGHT_WEIGHT);
        assertThat(spring.liftExempt()).isTrue();
    }

    @Test
    void testWithRestSsPreservesLevelOffset() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true, LEVEL_OFFSET_SS)
            .withRestSs(NEW_REST_SS);

        assertThat(spring.levelOffsetSs()).isEqualTo(LEVEL_OFFSET_SS);
    }

    @Test
    void testWithCorrectionSsAddsCorrectionToRestAndLevelOffset() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, false, LEVEL_OFFSET_SS)
            .withCorrectionSs(WIDENING_CORRECTION_SS);

        assertThat(spring.restSs()).isCloseTo(REST_ABOVE_STRUT_SS + WIDENING_CORRECTION_SS, within(DELTA));
        assertThat(spring.levelOffsetSs()).isCloseTo(LEVEL_OFFSET_SS + WIDENING_CORRECTION_SS, within(DELTA));
    }

    @Test
    void testWithCorrectionSsPreservesStrutWeightAndLiftExempt() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true, LEVEL_OFFSET_SS)
            .withCorrectionSs(WIDENING_CORRECTION_SS);

        assertThat(spring.strutSs()).isEqualTo(STRUT_BELOW_REST_SS);
        assertThat(spring.weight()).isEqualTo(TIGHT_WEIGHT);
        assertThat(spring.liftExempt()).isTrue();
    }

    @Test
    void testWithCorrectionSsSubtractsFromBothChannelsForANegativeCorrection() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, false, LEVEL_OFFSET_SS)
            .withCorrectionSs(NARROWING_CORRECTION_SS);

        assertThat(spring.restSs()).isCloseTo(REST_ABOVE_STRUT_SS + NARROWING_CORRECTION_SS, within(DELTA));
        assertThat(spring.levelOffsetSs()).isCloseTo(LEVEL_OFFSET_SS + NARROWING_CORRECTION_SS, within(DELTA));
    }
}
