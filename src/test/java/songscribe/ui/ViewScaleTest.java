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

package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.Font;
import java.awt.Point;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.DocPx;
import songscribe.dom.ScaleContext;
import songscribe.dom.Ss;
import songscribe.dom.ViewPx;

/**
 * Unit tests for {@link ViewScale}: factor derivation, the typed conversions across
 * the {@link Ss}/{@link DocPx}/{@link ViewPx} regimes, and {@link #IDENTITY} behavior.
 */
class ViewScaleTest extends UnitTest {

    private static final double DOUBLE_EPSILON = 1e-9;
    private static final double PPS = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

    // -------------------------------------------------------------------------
    // factor()
    // -------------------------------------------------------------------------

    @Test
    void testFactorIsOneAtDefaultZoom() {
        var viewScale = new ViewScale();
        assertThat(viewScale.factor()).isCloseTo(1.0, within(DOUBLE_EPSILON));
    }

    @Test
    void testFactorScalesWithZoomPercent() {
        var viewScale = new ViewScale();

        viewScale.setZoomPercent(200);
        assertThat(viewScale.factor()).isCloseTo(2.0, within(DOUBLE_EPSILON));

        viewScale.setZoomPercent(50);
        assertThat(viewScale.factor()).isCloseTo(0.5, within(DOUBLE_EPSILON));
    }

    @Test
    void testGetZoomPercentIsInverseOfSetZoomPercent() {
        var viewScale = new ViewScale();

        viewScale.setZoomPercent(150);

        assertThat(viewScale.getZoomPercent()).isEqualTo(150);
    }

    // -------------------------------------------------------------------------
    // Ss <-> ViewPx
    // -------------------------------------------------------------------------

    @Nested
    class SsViewPxConversions {

        @Test
        void testToViewPxAtDefaultZoomEqualsDocumentScale() {
            var viewScale = new ViewScale();
            var ss = new Ss(3.0);

            assertThat(viewScale.toViewPx(ss).value()).isCloseTo(PPS * 3.0, within(DOUBLE_EPSILON));
        }

        @Test
        void testToViewPxScalesByZoomFactor() {
            var viewScale = new ViewScale();
            viewScale.setZoomPercent(200);
            var ss = new Ss(3.0);

            assertThat(viewScale.toViewPx(ss).value()).isCloseTo(PPS * 3.0 * 2.0, within(DOUBLE_EPSILON));
        }

        @Test
        void testSsToViewPxRoundTrips() {
            var viewScale = new ViewScale();
            viewScale.setZoomPercent(250);
            var originalSs = new Ss(4.25);

            var roundTripped = viewScale.toSs(viewScale.toViewPx(originalSs));

            assertThat(roundTripped.value()).isCloseTo(originalSs.value(), within(DOUBLE_EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // DocPx <-> ViewPx
    // -------------------------------------------------------------------------

    @Nested
    class DocPxViewPxConversions {

        @Test
        void testToViewPxAtDefaultZoomIsUnchanged() {
            var viewScale = new ViewScale();
            var docPx = new DocPx(120.0);

            assertThat(viewScale.toViewPx(docPx).value()).isCloseTo(120.0, within(DOUBLE_EPSILON));
        }

        @Test
        void testToViewPxScalesByZoomFactor() {
            var viewScale = new ViewScale();
            viewScale.setZoomPercent(400);
            var docPx = new DocPx(120.0);

            assertThat(viewScale.toViewPx(docPx).value()).isCloseTo(480.0, within(DOUBLE_EPSILON));
        }

        @Test
        void testDocPxToViewPxRoundTrips() {
            var viewScale = new ViewScale();
            viewScale.setZoomPercent(300);
            var originalDocPx = new DocPx(87.5);

            var roundTripped = viewScale.toDocPx(viewScale.toViewPx(originalDocPx));

            assertThat(roundTripped.value()).isCloseTo(originalDocPx.value(), within(DOUBLE_EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // zoomedFont
    // -------------------------------------------------------------------------

    @Nested
    class ZoomedFont {

        private static final Font BASE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

        @Test
        void testZoomedFontIsIdenticalInstanceAtDefaultZoom() {
            var viewScale = new ViewScale();

            assertThat(viewScale.zoomedFont(BASE_FONT)).isSameAs(BASE_FONT);
        }

        @Test
        void testZoomedFontScalesSizeAtNonDefaultZoom() {
            var viewScale = new ViewScale();
            viewScale.setZoomPercent(200);

            var zoomed = viewScale.zoomedFont(BASE_FONT);

            assertThat(zoomed.getSize2D()).isCloseTo(24.0f, within(1e-4f));
        }
    }

    // -------------------------------------------------------------------------
    // IDENTITY
    // -------------------------------------------------------------------------

    @Test
    void testIdentityIsAtDefaultZoom() {
        assertThat(ViewScale.IDENTITY.getZoomPercent()).isEqualTo(100);
        assertThat(ViewScale.IDENTITY.factor()).isCloseTo(1.0, within(DOUBLE_EPSILON));
    }

    @Test
    void testIdentityReturnsBaseFontUnchanged() {
        var font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        assertThat(ViewScale.IDENTITY.zoomedFont(font)).isSameAs(font);
    }
}
