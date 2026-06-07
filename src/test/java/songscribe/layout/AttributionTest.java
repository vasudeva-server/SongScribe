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

import org.junit.jupiter.api.Test;

import java.util.List;

import songscribe.UnitTest;
import songscribe.dom.Attribution;
import songscribe.dom.ScaleContext;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.LayoutResult;
import songscribe.layout.stacking.VerticalStackingCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class AttributionTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @Test
    void testCtorSetsAttributionMarginBottomSs() {
        var attribution = new Attribution();

        assertThat(attribution.getMarginBottomSs())
            .isCloseTo(Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS, within(EPSILON));
    }

    @Test
    void testGetContentPxConvertsFromSs() {
        var attribution = new Attribution();
        var widthSs = 10.0;
        var heightSs = 4.0;

        attribution.setDimensionsSs(widthSs, heightSs);

        assertThat(attribution.getContentWidthPx())
            .isCloseTo(ScaleContext.ssToPx(widthSs), within(EPSILON));
        assertThat(attribution.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(heightSs), within(EPSILON));
    }

    @Test
    void testGetUserXOffsetSsAlwaysReturnsZero() {
        var attribution = new Attribution();

        assertThat(attribution.getUserXOffsetSs()).isZero();
    }

    @Test
    void testUserYOffsetSsRoundTrips() {
        var attribution = new Attribution();
        var offsetSs = -2.5;

        attribution.setUserYOffsetSs(offsetSs);

        assertThat(attribution.getUserYOffsetSs()).isCloseTo(offsetSs, within(EPSILON));
    }

    @Test
    void testStackAttributionSkipsWhenDimensionsAreZero() {
        // A freshly constructed Attribution has zero width and height.
        // stackAttribution guards against this with `if (widthSs <= 0 || heightSs <= 0) return`,
        // so the attribution must not receive a DecorationLayout in the builder.
        var attribution = new Attribution();
        var builder = LayoutResult.builder();

        new VerticalStackingCalculator().calculate(
            List.of(),
            detachedLine(),
            builder,
            100.0,
            mock(DocumentFontsHolder.class),
            attribution);

        assertThat(builder.getDecorationLayout(attribution))
            .describedAs("attribution with zero dimensions must not be stacked")
            .isNull();
    }
}
