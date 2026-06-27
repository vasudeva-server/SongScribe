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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.GraphicUtils.CapAdjustment;
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

    @Nested
    class DrawRoundedLine {

        private static final double TOLERANCE = 1e-6;
        private static final int IMAGE_SIZE = 100;
        private static final double THICKNESS_SS = 2.0;

        // A horizontal line, comfortably inside the image bounds.
        private static final double X1 = 10.0;
        private static final double X2 = 40.0;
        private static final double Y = 25.0;

        // A 45° line through the origin, for verifying angled placement.
        private static final double ANGLED_END = 30.0;

        /**
         * Captures the single shape filled by {@code draw}, transformed back into device space.
         * {@code drawRoundedLine} fills an axis-aligned round rect in the line's local frame and
         * places it via the {@code g2} transform, so the captured local shape must be re-transformed
         * by the transform that was active at fill time to recover the on-screen geometry.
         */
        private static Rectangle2D capturePlacedBounds(Consumer<Graphics2D> draw) {
            var image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
            var g2 = spy(image.createGraphics());
            var placed = new Shape[]{null};

            doAnswer(invocation -> {
                Shape local = invocation.getArgument(0);
                placed[0] = g2.getTransform().createTransformedShape(local);
                return null;
            }).when(g2).fill(any(Shape.class));

            try {
                draw.accept(g2);
            } finally {
                g2.dispose();
            }

            assertThat(placed[0]).as("drawRoundedLine must fill exactly one shape").isNotNull();

            return placed[0].getBounds2D();
        }

        @Test
        void testHorizontalLineNoneSpansExactlyEndpoints() {
            var bounds = capturePlacedBounds(g2 ->
                GraphicUtils.drawRoundedLine(g2, X1, Y, X2, Y, THICKNESS_SS));

            assertThat(bounds.getMinX()).as("left end at x1").isCloseTo(X1, within(TOLERANCE));
            assertThat(bounds.getMaxX()).as("right end at x2").isCloseTo(X2, within(TOLERANCE));
            assertThat(bounds.getCenterY()).as("centered on y").isCloseTo(Y, within(TOLERANCE));
            assertThat(bounds.getHeight()).as("height equals thickness").isCloseTo(THICKNESS_SS, within(TOLERANCE));
        }

        @Test
        void testHorizontalLineExtendExtendsEachEndByHalfThickness() {
            var bounds = capturePlacedBounds(g2 ->
                GraphicUtils.drawRoundedLine(g2, X1, Y, X2, Y, THICKNESS_SS, CapAdjustment.EXTEND));

            // EXTEND pushes each end out by half the thickness past the given coordinates.
            assertThat(bounds.getMinX())
                .as("left end extended by half thickness")
                .isCloseTo(X1 - THICKNESS_SS / 2, within(TOLERANCE));
            assertThat(bounds.getMaxX())
                .as("right end extended by half thickness")
                .isCloseTo(X2 + THICKNESS_SS / 2, within(TOLERANCE));
        }

        @Test
        void testAngledLineCenteredOnMidpoint() {
            // A line from the origin to (ANGLED_END, ANGLED_END): the placed shape must rotate to the
            // 45° angle, so its bounds centre lands on the line midpoint (verifying rotation + placement
            // for non-axis-aligned lines, the glissando case).
            var bounds = capturePlacedBounds(g2 ->
                GraphicUtils.drawRoundedLine(g2, 0, 0, ANGLED_END, ANGLED_END, THICKNESS_SS));

            assertThat(bounds.getCenterX()).isCloseTo(ANGLED_END / 2, within(TOLERANCE));
            assertThat(bounds.getCenterY()).isCloseTo(ANGLED_END / 2, within(TOLERANCE));
            // A 45° line's bounding box is square; a non-rotated fill would be wide and flat instead.
            assertThat(bounds.getWidth()).isCloseTo(bounds.getHeight(), within(TOLERANCE));
        }
    }
}
