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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.engraving.SMuFLConstants;

/**
 * Tests for the staff-header geometry {@link HorizontalSpacingCalculator} exposes. Column-to-column
 * spacing lives in {@link HorizontalSpacingCalculatorSpringTest}, which drives the spring engine
 * every rendered position now goes through.
 */
class HorizontalSpacingCalculatorTest extends UnitTest {

    private static final int THREE_KEY_ACCIDENTALS = 3;
    private static final int SEVEN_KEY_ACCIDENTALS = 7;

    // calculateHeaderRightEdgeSs(n) = G_CLEF_WIDTH_SS + n * KEY_ACCIDENTAL_WIDTH_SS
    @Test
    void testCalculateHeaderRightEdgeSsWithZeroAccidentals() {
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(0))
            .isEqualTo(SMuFLConstants.G_CLEF_WIDTH_SS);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithThreeAccidentals() {
        var expected = SMuFLConstants.G_CLEF_WIDTH_SS
            + THREE_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(THREE_KEY_ACCIDENTALS)).isEqualTo(expected);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithSevenAccidentals() {
        var expected = SMuFLConstants.G_CLEF_WIDTH_SS
            + SEVEN_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(SEVEN_KEY_ACCIDENTALS)).isEqualTo(expected);
    }
}
