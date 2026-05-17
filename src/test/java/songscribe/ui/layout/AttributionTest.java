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

package songscribe.ui.layout;

import java.awt.Font;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AttributionTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @Test
    void testComputeContentWidthSsUsesStringWidth() {
        var font = new Font("Dialog", Font.PLAIN, 12);

        var attribution = new Attribution("Composer");
        var expected = ScaleContext.getInstance().textWidthSs(font, "Composer");

        assertThat(attribution.computeContentWidthSs(font)).isCloseTo(expected, within(EPSILON));
    }

    @Test
    void testComputeContentHeightSsUsesFontMetrics() {
        var font = new Font("Dialog", Font.PLAIN, 12);

        var attribution = new Attribution("Composer");
        var expected = ScaleContext.getInstance().textHeightSs(font);

        assertThat(attribution.computeContentHeightSs(font)).isCloseTo(expected, within(EPSILON));
    }
}
