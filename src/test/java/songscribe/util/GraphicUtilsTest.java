/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.GraphicUtils.Unit;

class GraphicUtilsTest extends UnitTest {

    @Nested
    class UnitCreate {

        @Test
        void testCreateWithMetricTrueReturnsCm() {
            assertThat(Unit.create(true)).isEqualTo(Unit.CM);
        }

        @Test
        void testCreateWithMetricFalseReturnsInch() {
            assertThat(Unit.create(false)).isEqualTo(Unit.INCH);
        }
    }

    @Nested
    class UnitFromValue {

        @Test
        void testFromValueReturnsInch() {
            assertThat(Unit.fromValue(Unit.INCH.getValue())).isEqualTo(Unit.INCH);
        }

        @Test
        void testFromValueReturnsCm() {
            assertThat(Unit.fromValue(Unit.CM.getValue())).isEqualTo(Unit.CM);
        }

        @Test
        void testFromValueUndeterminedValueReturnsUndetermined() {
            assertThat(Unit.fromValue(Unit.UNDETERMINED.getValue())).isEqualTo(Unit.UNDETERMINED);
        }

        @Test
        void testFromValueUnknownValueReturnsUndetermined() {
            assertThat(Unit.fromValue(Integer.MAX_VALUE)).isEqualTo(Unit.UNDETERMINED);
        }
    }

    @Nested
    class UnitDescription {

        @Test
        void testInchDescriptionIsInch() {
            assertThat(Unit.INCH.description()).isEqualTo("inch");
        }

        @Test
        void testCmDescriptionIsCm() {
            assertThat(Unit.CM.description()).isEqualTo("cm");
        }

        @Test
        void testUndeterminedDescriptionIsEmpty() {
            assertThat(Unit.UNDETERMINED.description()).isEmpty();
        }
    }

    @Nested
    class UnitIsMetric {

        @Test
        void testCmIsMetric() {
            assertThat(Unit.CM.isMetric()).isTrue();
        }

        @Test
        void testInchIsNotMetric() {
            assertThat(Unit.INCH.isMetric()).isFalse();
        }

        @Test
        void testUndeterminedIsNotMetric() {
            assertThat(Unit.UNDETERMINED.isMetric()).isFalse();
        }
    }

    @Nested
    class ConvertFromPixels {

        @Test
        void testInchBranchConvertsOneInchToOne() {
            // dpi pixels = 1 inch; result should be exactly 1.0
            assertThat(GraphicUtils.convertFromPixels(GraphicUtils.getDpi(), Unit.INCH))
                .isEqualTo(1.0);
        }

        @Test
        void testInchBranchRoundsToTwoDecimalPlaces() {
            // choose a pixel count that produces a non-exact fraction to exercise rounding
            int pixels = GraphicUtils.getDpi() / 2 + 1;
            double expected = Math.round(pixels * 100.0 / GraphicUtils.getDpi()) / 100d;
            assertThat(GraphicUtils.convertFromPixels(pixels, Unit.INCH)).isEqualTo(expected);
        }

        @Test
        void testCmBranchConvertsOneInchInMm() {
            // dpi pixels = 1 inch = CM_PER_INCH*10 mm; result rounded to nearest mm
            double expected = Math.round(GraphicUtils.CM_PER_INCH * 10) / 10d;
            assertThat(GraphicUtils.convertFromPixels(GraphicUtils.getDpi(), Unit.CM))
                .isEqualTo(expected);
        }

        @Test
        void testCmBranchRoundsToNearestMm() {
            // half-inch in pixels; result = CM_PER_INCH*10/2 mm, rounded to nearest mm
            int pixels = GraphicUtils.getDpi() / 2;
            double expected = Math.round(GraphicUtils.CM_PER_INCH * 10 / 2) / 10d;
            assertThat(GraphicUtils.convertFromPixels(pixels, Unit.CM)).isEqualTo(expected);
        }
    }

    @Nested
    class ConvertToPixels {

        @Test
        void testInchBranchConvertsOneInchToDpiPixels() {
            assertThat(GraphicUtils.convertToPixels(1.0, Unit.INCH)).isEqualTo(GraphicUtils.getDpi());
        }

        @Test
        void testCmBranchConvertsOneInchInMmToDpiPixels() {
            // 1 inch = CM_PER_INCH*10 mm; CM branch divides by CM_PER_INCH*10, giving dpi pixels
            double oneInchInMm = GraphicUtils.CM_PER_INCH * 10;
            assertThat(GraphicUtils.convertToPixels(oneInchInMm, Unit.CM)).isEqualTo(GraphicUtils.getDpi());
        }
    }

    @Nested
    class GetTextBlockWidth {

        private static final int IMAGE_SIZE = 100;

        @Test
        void testEmptyStringReturnsZero() {
            var g2 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                assertThat(GraphicUtils.getTextBlockWidth("", g2)).isEqualTo(0d);
            } finally {
                g2.dispose();
            }
        }

        @Test
        void testMultiLineReturnsMaxLineWidth() {
            var g2 = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                var widthLong = GraphicUtils.getTextBlockWidth("aaaaaaaaaa", g2);
                // longer line first
                assertThat(GraphicUtils.getTextBlockWidth("aaaaaaaaaa\na", g2)).isEqualTo(widthLong);
                // longer line second
                assertThat(GraphicUtils.getTextBlockWidth("a\naaaaaaaaaa", g2)).isEqualTo(widthLong);
            } finally {
                g2.dispose();
            }
        }
    }
}
