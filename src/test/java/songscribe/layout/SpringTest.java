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

    // Rest gap comfortably larger than the strut, so compliance should be their difference.
    private static final double REST_ABOVE_STRUT_SS = 3.0;
    private static final double STRUT_BELOW_REST_SS = 1.0;
    private static final double EXPECTED_COMPLIANCE_SS = REST_ABOVE_STRUT_SS - STRUT_BELOW_REST_SS;

    // Rest and strut equal: no slack to give up.
    private static final double REST_EQUALS_STRUT_SS = 2.0;

    // Rest smaller than strut: compliance must still floor at zero, not go negative.
    private static final double REST_BELOW_STRUT_SS = 1.0;
    private static final double STRUT_ABOVE_REST_SS = 2.5;

    private static final double NO_COMPLIANCE_SS = 0.0;

    // A tight beam-internal reduction weight, and a fresh rest for the withRestSs preservation test.
    private static final double TIGHT_WEIGHT = 0.6;
    private static final double NEW_REST_SS = 5.0;

    private static final double DELTA = 1e-9;

    @Test
    void testOfComputesComplianceAsRestMinusStrutWhenRestExceedsStrut() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS);

        assertThat(spring.complianceSs()).isCloseTo(EXPECTED_COMPLIANCE_SS, within(DELTA));
    }

    @Test
    void testOfComputesZeroComplianceWhenRestEqualsStrut() {
        var spring = Spring.of(REST_EQUALS_STRUT_SS, REST_EQUALS_STRUT_SS);

        assertThat(spring.complianceSs()).isCloseTo(NO_COMPLIANCE_SS, within(DELTA));
    }

    @Test
    void testOfFloorsComplianceAtZeroWhenRestIsBelowStrut() {
        var spring = Spring.of(REST_BELOW_STRUT_SS, STRUT_ABOVE_REST_SS);

        assertThat(spring.complianceSs()).isCloseTo(NO_COMPLIANCE_SS, within(DELTA));
    }

    @Test
    void testTwoArgOfDefaultsToNormalWeightAndNotRigid() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS);

        assertThat(spring.weight()).isEqualTo(Spring.NORMAL_WEIGHT);
        assertThat(spring.rigid()).isFalse();
    }

    @Test
    void testFourArgOfCarriesWeightAndRigid() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true);

        assertThat(spring.weight()).isEqualTo(TIGHT_WEIGHT);
        assertThat(spring.rigid()).isTrue();
        assertThat(spring.complianceSs()).isCloseTo(EXPECTED_COMPLIANCE_SS, within(DELTA));
    }

    @Test
    void testWithRestSsPreservesWeightAndRigidAndRecomputesCompliance() {
        var spring = Spring.of(REST_ABOVE_STRUT_SS, STRUT_BELOW_REST_SS, TIGHT_WEIGHT, true)
            .withRestSs(NEW_REST_SS);

        assertThat(spring.restSs()).isEqualTo(NEW_REST_SS);
        assertThat(spring.weight()).isEqualTo(TIGHT_WEIGHT);
        assertThat(spring.rigid()).isTrue();
        assertThat(spring.complianceSs()).isCloseTo(NEW_REST_SS - STRUT_BELOW_REST_SS, within(DELTA));
    }
}
