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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Font;
import java.awt.font.TextLayout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

class ScaleContextTest extends UnitTest {

    // Pixels-per-staff-space value used for all conversion tests; chosen to be
    // distinct from the default so a forgotten reset would cause a different test
    // to fail rather than silently pass.
    private static final double TEST_PPS = 10.0;

    // Staff-space inputs for ssToPx / pxToSs round-trip tests.
    private static final double SS_SMALL = 3.0;
    private static final double SS_LARGE = 7.5;

    // Expected pixel products for the above inputs at TEST_PPS.
    private static final double EXPECTED_PX_SMALL = TEST_PPS * SS_SMALL;   // 30.0
    private static final double EXPECTED_PX_LARGE = TEST_PPS * SS_LARGE;   // 75.0

    // Staff-space values whose products with TEST_PPS land just below 0.5 and
    // just above 0.5, exercising both rounding directions of ssToRoundedPx.
    //   TEST_PPS * SS_ROUND_DOWN = 10 * 0.04 = 0.4  → rounds to 0
    //   TEST_PPS * SS_ROUND_UP   = 10 * 0.06 = 0.6  → rounds to 1
    private static final double SS_ROUND_DOWN = 0.04;
    private static final double SS_ROUND_UP   = 0.06;
    private static final int    EXPECTED_ROUNDED_DOWN = 0;
    private static final int    EXPECTED_ROUNDED_UP   = 1;

    // Tolerance for double comparisons (exact arithmetic is expected, but
    // floating-point representation warrants a tiny epsilon).
    private static final double DOUBLE_EPSILON = 1e-9;

    // Negative pps value used to verify the guard in setPixelsPerStaffSpace.
    private static final double NEGATIVE_PPS = -5.0;

    // Zoom percentages used for setZoomPercent / getZoomPercent tests.
    private static final int ZOOM_PERCENT_DEFAULT = 100;
    private static final int ZOOM_PERCENT_HALF = 50;
    private static final int ZOOM_PERCENT_DOUBLE = 200;
    private static final int ZOOM_PERCENT_NEGATIVE = -10;

    // Font used for rows 47–49: a plain, headless-safe font with a fixed size.
    private static final int TEST_FONT_SIZE_PT = 12;
    private static final Font TEST_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, TEST_FONT_SIZE_PT);

    // Fixed text string for the two text-based font-metric wrappers.
    private static final String TEST_TEXT = "Hello";

    @BeforeEach
    void setUp() {
        // Establish a known pps before each test so tests are independent of
        // whatever the JVM-global singleton currently holds.
        ScaleContext.setPixelsPerStaffSpace(TEST_PPS);
    }

    @AfterEach
    void tearDown() {
        // Restore the production default so no test pollutes later ones.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    // -------------------------------------------------------------------------
    // Row 43 — ssToPx(ss) = pps × ss
    // -------------------------------------------------------------------------

    @Test
    void testSsToPxReturnsProductOfPpsAndSs() {
        assertThat(ScaleContext.ssToPx(SS_SMALL))
            .isCloseTo(EXPECTED_PX_SMALL, within(DOUBLE_EPSILON));
        assertThat(ScaleContext.ssToPx(SS_LARGE))
            .isCloseTo(EXPECTED_PX_LARGE, within(DOUBLE_EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 44 — ssToRoundedPx(ss) rounds to nearest int (both directions)
    // -------------------------------------------------------------------------

    @Test
    void testSsToRoundedPxRoundsDown() {
        // Product is 0.4, which Math.round maps to 0.
        assertThat(ScaleContext.ssToRoundedPx(SS_ROUND_DOWN)).isEqualTo(EXPECTED_ROUNDED_DOWN);
    }

    @Test
    void testSsToRoundedPxRoundsUp() {
        // Product is 0.6, which Math.round maps to 1.
        assertThat(ScaleContext.ssToRoundedPx(SS_ROUND_UP)).isEqualTo(EXPECTED_ROUNDED_UP);
    }

    // -------------------------------------------------------------------------
    // Row 45 — pxToSs(px) = px / pps, inverse of ssToPx
    // -------------------------------------------------------------------------

    @Test
    void testPxToSsReturnsPxDividedByPps() {
        assertThat(ScaleContext.pxToSs(EXPECTED_PX_SMALL))
            .isCloseTo(SS_SMALL, within(DOUBLE_EPSILON));
        assertThat(ScaleContext.pxToSs(EXPECTED_PX_LARGE))
            .isCloseTo(SS_LARGE, within(DOUBLE_EPSILON));
    }

    @Test
    void testPxToSsIsInverseOfSsToPxForRoundValue() {
        double originalSs = SS_SMALL;
        double roundTripped = ScaleContext.pxToSs(ScaleContext.ssToPx(originalSs));
        assertThat(roundTripped).isCloseTo(originalSs, within(DOUBLE_EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 46 — setPixelsPerStaffSpace throws IllegalArgumentException for ≤ 0
    // -------------------------------------------------------------------------

    @Test
    void testSetPixelsPerStaffSpaceThrowsForZero() {
        assertThatThrownBy(() -> ScaleContext.setPixelsPerStaffSpace(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetPixelsPerStaffSpaceThrowsForNegativeValue() {
        assertThatThrownBy(() -> ScaleContext.setPixelsPerStaffSpace(NEGATIVE_PPS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // setZoomPercent / getZoomPercent — 100% maps to the default pps
    // -------------------------------------------------------------------------

    @Test
    void testSetZoomPercentAtDefaultSetsDefaultPps() {
        ScaleContext.setZoomPercent(ZOOM_PERCENT_DEFAULT);

        assertThat(ScaleContext.getPixelsPerStaffSpace())
            .isCloseTo(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE, within(DOUBLE_EPSILON));
    }

    @Test
    void testSetZoomPercentScalesPpsProportionally() {
        ScaleContext.setZoomPercent(ZOOM_PERCENT_HALF);
        assertThat(ScaleContext.getPixelsPerStaffSpace())
            .isCloseTo(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE * ZOOM_PERCENT_HALF / ZOOM_PERCENT_DEFAULT, within(DOUBLE_EPSILON));

        ScaleContext.setZoomPercent(ZOOM_PERCENT_DOUBLE);
        assertThat(ScaleContext.getPixelsPerStaffSpace())
            .isCloseTo(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE * ZOOM_PERCENT_DOUBLE / ZOOM_PERCENT_DEFAULT, within(DOUBLE_EPSILON));
    }

    @Test
    void testGetZoomPercentIsInverseOfSetZoomPercent() {
        ScaleContext.setZoomPercent(ZOOM_PERCENT_HALF);
        assertThat(ScaleContext.getZoomPercent()).isEqualTo(ZOOM_PERCENT_HALF);

        ScaleContext.setZoomPercent(ZOOM_PERCENT_DOUBLE);
        assertThat(ScaleContext.getZoomPercent()).isEqualTo(ZOOM_PERCENT_DOUBLE);
    }

    @Test
    void testSetZoomPercentThrowsForZero() {
        assertThatThrownBy(() -> ScaleContext.setZoomPercent(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetZoomPercentThrowsForNegativeValue() {
        assertThatThrownBy(() -> ScaleContext.setZoomPercent(ZOOM_PERCENT_NEGATIVE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Row 47 — getScaleTransform() returns AffineTransform scaled by pps
    // -------------------------------------------------------------------------

    @Test
    void testGetScaleTransformHasScaleEqualToPps() {
        var transform = ScaleContext.getScaleTransform();
        assertThat(transform.getScaleX()).isCloseTo(TEST_PPS, within(DOUBLE_EPSILON));
        assertThat(transform.getScaleY()).isCloseTo(TEST_PPS, within(DOUBLE_EPSILON));
        assertThat(transform.getShearX()).isCloseTo(0.0, within(DOUBLE_EPSILON));
        assertThat(transform.getShearY()).isCloseTo(0.0, within(DOUBLE_EPSILON));
        assertThat(transform.getTranslateX()).isCloseTo(0.0, within(DOUBLE_EPSILON));
        assertThat(transform.getTranslateY()).isCloseTo(0.0, within(DOUBLE_EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 48 — scaleFont(font) converts font size from px to staff-space units
    // -------------------------------------------------------------------------

    @Test
    void testScaleFontConvertsPointSizeThroughPxToSs() {
        // The production method returns font.deriveFont((float) pxToSs(font.getSize())).
        float expectedSize = (float) ScaleContext.pxToSs(TEST_FONT.getSize());
        Font scaledFont = ScaleContext.scaleFont(TEST_FONT);
        assertThat(scaledFont.getSize2D()).isCloseTo(expectedSize, within((float) DOUBLE_EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 49 — font-metric wrappers each return pxToSs(<raw pixel metric>)
    // -------------------------------------------------------------------------

    @Test
    void testFontAscentSsEqualsPxToSsOfLineMetricAscent() {
        double rawAscentPx = TEST_FONT.getLineMetrics("", GraphicUtils.SCREEN_FRC).getAscent();
        double expectedSs = ScaleContext.pxToSs(rawAscentPx);
        assertThat(ScaleContext.fontAscentSs(TEST_FONT)).isCloseTo(expectedSs, within(DOUBLE_EPSILON));
    }

    @Test
    void testFontDescentSsEqualsPxToSsOfLineMetricDescent() {
        double rawDescentPx = TEST_FONT.getLineMetrics("", GraphicUtils.SCREEN_FRC).getDescent();
        double expectedSs = ScaleContext.pxToSs(rawDescentPx);
        assertThat(ScaleContext.fontDescentSs(TEST_FONT)).isCloseTo(expectedSs, within(DOUBLE_EPSILON));
    }

    @Test
    void testFontMaxAscentSsEqualsPxToSsOfFontMetricsMaxAscent() {
        double rawMaxAscentPx = MyFontUtils.getFontMetrics(TEST_FONT).getMaxAscent();
        double expectedSs = ScaleContext.pxToSs(rawMaxAscentPx);
        assertThat(ScaleContext.fontMaxAscentSs(TEST_FONT)).isCloseTo(expectedSs, within(DOUBLE_EPSILON));
    }

    @Test
    void testTextHeightSsEqualsPxToSsOfAscentPlusDescent() {
        var lm = TEST_FONT.getLineMetrics("", GraphicUtils.SCREEN_FRC);
        double rawHeightPx = lm.getAscent() + lm.getDescent();
        double expectedSs = ScaleContext.pxToSs(rawHeightPx);
        assertThat(ScaleContext.textHeightSs(TEST_FONT)).isCloseTo(expectedSs, within(DOUBLE_EPSILON));
    }

    @Test
    void testTextWidthSsEqualsPxToSsOfTextLayoutAdvance() {
        double rawAdvancePx = new TextLayout(TEST_TEXT, TEST_FONT, GraphicUtils.SCREEN_FRC).getAdvance();
        double expectedSs = ScaleContext.pxToSs(rawAdvancePx);
        assertThat(ScaleContext.textWidthSs(TEST_FONT, TEST_TEXT)).isCloseTo(expectedSs, within(DOUBLE_EPSILON));
    }
}
