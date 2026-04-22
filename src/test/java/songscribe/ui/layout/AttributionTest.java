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

import java.awt.FontMetrics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.error.RuntimeError;
import songscribe.music.Composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AttributionTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @Test
    void testComputeContentWidthSsUsesStringWidth() {
        var fm = mock(FontMetrics.class);
        when(fm.stringWidth("Composer")).thenReturn(48);

        var attribution = new Attribution("Composer");
        double expected = ScaleContext.getInstance().fromPixels(48);

        assertThat(attribution.computeContentWidthSs(fm)).isCloseTo(expected, within(EPSILON));
    }

    @Test
    void testTextHeightSsUsesAscentPlusDescent() {
        var fm = mock(FontMetrics.class);
        when(fm.getAscent()).thenReturn(12);
        when(fm.getDescent()).thenReturn(4);

        double expected = ScaleContext.getInstance().fromPixels(16);

        assertThat(ScaleContext.getInstance().textHeightSs(fm)).isCloseTo(expected, within(EPSILON));
    }

    @Nested
    class ParentLineResolution {

        @Test
        void testGetContentWidthSsReadsCompositionFontMetrics() {
            var comp = new Composition();
            var line = comp.getLine(0);
            var attribution = new Attribution("Composer");
            attribution.setParentLine(line);

            var expected = attribution.computeContentWidthSs(comp.getAttributionFontMetrics());

            assertThat(attribution.getContentWidthSs()).isCloseTo(expected, within(EPSILON));
        }

        @Test
        void testGetContentHeightSsReadsCompositionFontMetrics() {
            var comp = new Composition();
            var line = comp.getLine(0);
            var attribution = new Attribution("Composer");
            attribution.setParentLine(line);

            var expected = ScaleContext.getInstance().textHeightSs(comp.getAttributionFontMetrics());

            assertThat(attribution.getContentHeightSs()).isCloseTo(expected, within(EPSILON));
        }

        @Test
        void testGetContentWidthSsThrowsWhenParentLineNull() {
            try (MockedStatic<RuntimeError> mockRuntimeError = mockStatic(RuntimeError.class)) {
                mockRuntimeError.when(() -> RuntimeError.exit(anyString()))
                    .thenReturn(new RuntimeException("null parentLine"));

                var attribution = new Attribution("Composer");

                assertThatThrownBy(attribution::getContentWidthSs)
                    .isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        void testGetContentHeightSsThrowsWhenParentLineNull() {
            try (MockedStatic<RuntimeError> mockRuntimeError = mockStatic(RuntimeError.class)) {
                mockRuntimeError.when(() -> RuntimeError.exit(anyString()))
                    .thenReturn(new RuntimeException("null parentLine"));

                var attribution = new Attribution("Composer");

                assertThatThrownBy(attribution::getContentHeightSs)
                    .isInstanceOf(RuntimeException.class);
            }
        }
    }
}
