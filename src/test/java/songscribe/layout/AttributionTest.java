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

import java.awt.Font;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Attribution;
import songscribe.dom.ScaleContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AttributionTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @Test
    void testCtorSetsAttributionMarginBottomSs() {
        var attribution = new Attribution("Composer");

        assertThat(attribution.getMarginBottomSs())
            .isCloseTo(Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS, within(EPSILON));
    }

    @Test
    void testComputeContentWidthSsUsesStringWidth() {
        var font = new Font("Dialog", Font.PLAIN, 12);

        var attribution = new Attribution("Composer");
        var expected = ScaleContext.textWidthSs(font, "Composer");

        assertThat(attribution.computeContentWidthSs(font)).isCloseTo(expected, within(EPSILON));
    }

    @Test
    void testComputeContentHeightSsUsesFontMetrics() {
        var font = new Font("Dialog", Font.PLAIN, 12);

        var attribution = new Attribution("Composer");
        var expected = ScaleContext.textHeightSs(font);

        assertThat(attribution.computeContentHeightSs(font)).isCloseTo(expected, within(EPSILON));
    }

    @Test
    void testGetContentDimensionsThrowUnsupportedOperationException() {
        var attribution = new Attribution("Composer");

        assertThatThrownBy(attribution::getContentWidthSs)
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(attribution::getContentHeightSs)
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(attribution::getContentWidthPx)
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(attribution::getContentHeightPx)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testIsRightAlignedDefaultTrueAndRoundTrip() {
        var attribution = new Attribution("Composer");

        assertThat(attribution.isRightAligned()).isTrue();

        attribution.setRightAligned(false);
        assertThat(attribution.isRightAligned()).isFalse();
    }
}
