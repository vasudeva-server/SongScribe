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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** That a stack of beams grows by one center-to-center step per additional beam. */
class BeamMetricsTest extends UnitTest {

    /** Staff spaces within which two computed lengths count as the same. */
    private static final double TOLERANCE_SS = 1.0e-9;

    /**
     * One beam stack and the vertical extent it occupies.
     *
     * @param description the case, as the test's display name
     * @param beamCount   how many beams stand in the stack
     * @param expectedSs  the outer-edge-to-outer-edge extent, in staff spaces
     */
    private record BeamStackCase(String description, int beamCount, double expectedSs) {}

    /**
     * The three stacks this program can draw: a quaver's single beam, a semiquaver's two, and a
     * demisemiquaver's three. Each row states its own sum rather than the formula, so a change to
     * how the stack grows shows up as a row that no longer holds.
     */
    static Stream<BeamStackCase> beamStackCases() {
        return Stream.of(
            new BeamStackCase("one beam is its own thickness",
                1, BeamMetrics.BEAM_THICKNESS_SS),
            new BeamStackCase("two beams add one center-to-center step",
                2, BeamMetrics.BEAM_THICKNESS_SS + BeamMetrics.BEAM_TRANSLATION_SS),
            new BeamStackCase("three beams add two center-to-center steps",
                3, BeamMetrics.BEAM_THICKNESS_SS
                    + BeamMetrics.BEAM_TRANSLATION_SS
                    + BeamMetrics.BEAM_TRANSLATION_SS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("beamStackCases")
    void testABeamStackGrowsByOneCenterToCenterStepPerBeam(BeamStackCase testCase) {
        assertThat(BeamMetrics.beamStackHeightSs(testCase.beamCount()))
            .isCloseTo(testCase.expectedSs(), within(TOLERANCE_SS));
    }

}
