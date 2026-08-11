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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.GraphicUtils.Unit;

class GraphicUtilsTest extends UnitTest {

    // A script family (issue #573) whose ink, at a large enough size, overshoots the
    // font's nominal ascent. Not installed on every platform, so the test using it skips
    // via assumeTrue when unavailable.
    private static final String OVERSHOOT_FONT_FAMILY = "SignPainter";
    private static final int OVERSHOOT_FONT_SIZE = 48;
    private static final String OVERSHOOT_TEXT = "Satya";

    private static boolean isFontFamilyAvailable(String family) {
        return Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ).contains(family);
    }

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
            var pixels = GraphicUtils.getDpi() / 2 + 1;
            var expected = Math.round(pixels * 100.0 / GraphicUtils.getDpi()) / 100d;
            assertThat(GraphicUtils.convertFromPixels(pixels, Unit.INCH)).isEqualTo(expected);
        }

        @Test
        void testCmBranchConvertsOneInchInMm() {
            // dpi pixels = 1 inch = CM_PER_INCH*10 mm; result rounded to nearest mm
            var expected = Math.round(GraphicUtils.CM_PER_INCH * 10) / 10d;
            assertThat(GraphicUtils.convertFromPixels(GraphicUtils.getDpi(), Unit.CM))
                .isEqualTo(expected);
        }

        @Test
        void testCmBranchRoundsToNearestMm() {
            // half-inch in pixels; result = CM_PER_INCH*10/2 mm, rounded to nearest mm
            var pixels = GraphicUtils.getDpi() / 2;
            var expected = Math.round(GraphicUtils.CM_PER_INCH * 10 / 2) / 10d;
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
            var oneInchInMm = GraphicUtils.CM_PER_INCH * 10;
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
        private static Rectangle2D capturePlacedBounds(Consumer<? super Graphics2D> draw) {
            var image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
            var g2 = spy(image.createGraphics());
            var placed = new Shape[]{null};

            doAnswer(answerVoid((Shape local) -> placed[0] = g2.getTransform().createTransformedShape(local))).when(g2).fill(any(Shape.class));

            try {
                draw.accept(g2);
            } finally {
                g2.dispose();
            }

            assertThat(placed[0]).as("drawRoundedLine must fill exactly one shape").isNotNull();

            return placed[0].getBounds2D();
        }

        @Test
        void testHorizontalLineSpansExactlyEndpoints() {
            var bounds = capturePlacedBounds(g2 ->
                GraphicUtils.drawRoundedLine(g2, X1, Y, X2, Y, THICKNESS_SS));

            assertThat(bounds.getMinX()).as("left end at x1").isCloseTo(X1, within(TOLERANCE));
            assertThat(bounds.getMaxX()).as("right end at x2").isCloseTo(X2, within(TOLERANCE));
            assertThat(bounds.getCenterY()).as("centered on y").isCloseTo(Y, within(TOLERANCE));
            assertThat(bounds.getHeight()).as("height equals thickness").isCloseTo(THICKNESS_SS, within(TOLERANCE));
        }

        @Test
        void testAngledLineCenteredOnMidpoint() {
            // A line from the origin to (ANGLED_END, ANGLED_END): the placed shape must rotate to the
            // 45° angle, so its bounds center lands on the line midpoint (verifying rotation + placement
            // for non-axis-aligned lines, the glissando case).
            var bounds = capturePlacedBounds(g2 ->
                GraphicUtils.drawRoundedLine(g2, 0, 0, ANGLED_END, ANGLED_END, THICKNESS_SS));

            assertThat(bounds.getCenterX()).isCloseTo(ANGLED_END / 2, within(TOLERANCE));
            assertThat(bounds.getCenterY()).isCloseTo(ANGLED_END / 2, within(TOLERANCE));
            // A 45° line's bounding box is square; a non-rotated fill would be wide and flat instead.
            assertThat(bounds.getWidth()).isCloseTo(bounds.getHeight(), within(TOLERANCE));
        }
    }

    @Nested
    class DrawPath {

        private static final double TOLERANCE = 1e-6;
        private static final int IMAGE_SIZE = 100;
        private static final double THICKNESS_SS = 2.0;

        // A horizontal segment, comfortably inside the image bounds.
        private static final double X1 = 10.0;
        private static final double X2 = 40.0;
        private static final double Y = 25.0;

        // Downward drop of the vertical arm for the L-shaped path.
        private static final double CORNER_DROP = 15.0;

        // Vertical component of the diagonal segment; with X2 - X1 it makes a non-axis-aligned line.
        private static final double DIAGONAL_RISE = 20.0;

        // PathIterator.currentSegment fills up to six coordinates (a cubic's three control points).
        private static final int PATH_SEGMENT_COORDS = 6;

        /**
         * Captures the bounds of the stroked outline {@code drawPath} produces, by recording the
         * path passed to {@code g2.draw} and stroking it with the stroke that was set. {@code drawPath}
         * draws in device space (no transform), so these bounds are the on-screen geometry.
         */
        private static Rectangle2D captureStrokedBounds(Consumer<? super Graphics2D> draw) {
            var image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
            var g2 = spy(image.createGraphics());
            var stroked = new Shape[]{null};

            doAnswer(answerVoid((Shape path) -> stroked[0] = g2.getStroke().createStrokedShape(path))).when(g2).draw(any(Shape.class));

            try {
                draw.accept(g2);
            } finally {
                g2.dispose();
            }

            verify(g2, times(1)).draw(any(Shape.class));

            return stroked[0].getBounds2D();
        }

        @Test
        void testStrokedPathSpansExactlyEndpoints() {
            // Two-point horizontal path: the round caps must bring the stroked extent back to the
            // given coordinates, despite each endpoint being pulled inward by half the thickness.
            var bounds = captureStrokedBounds(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{
                    new Point2D.Double(X1, Y),
                    new Point2D.Double(X2, Y)
                }, THICKNESS_SS));

            assertThat(bounds.getMinX()).as("left end at x1").isCloseTo(X1, within(TOLERANCE));
            assertThat(bounds.getMaxX()).as("right end at x2").isCloseTo(X2, within(TOLERANCE));
            assertThat(bounds.getCenterY()).as("centered on y").isCloseTo(Y, within(TOLERANCE));
            assertThat(bounds.getHeight()).as("height equals thickness").isCloseTo(THICKNESS_SS, within(TOLERANCE));
        }

        @Test
        void testInteriorCornerNotInset() {
            // An L-shaped path: only the two free ends are pulled inward; the interior corner is a
            // join and stays put. So the rounded join still reaches half a thickness past the corner
            // in x, while the vertical free end caps back to exactly the corner drop in y.
            var bounds = captureStrokedBounds(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{
                    new Point2D.Double(X1, Y),
                    new Point2D.Double(X2, Y),
                    new Point2D.Double(X2, Y + CORNER_DROP)
                }, THICKNESS_SS));

            assertThat(bounds.getMaxX())
                .as("rounded join reaches half a thickness past the corner")
                .isCloseTo(X2 + THICKNESS_SS / 2, within(TOLERANCE));
            assertThat(bounds.getMaxY())
                .as("vertical free end at corner drop")
                .isCloseTo(Y + CORNER_DROP, within(TOLERANCE));
        }

        @Test
        void testDiagonalSegmentInsetAlongItsOwnDirection() {
            // A diagonal segment is the only case that exercises capInset's full 2-D normalization:
            // both dx and dy are nonzero, so an inset that divided by a single axis instead of the
            // true segment length would land off the line. Axis-aligned paths cannot catch that.
            var start = new Point2D.Double(X1, Y);
            var end = new Point2D.Double(X2, Y + DIAGONAL_RISE);
            var halfWidthSs = THICKNESS_SS / 2.0;
            var lengthSs = Math.hypot(end.x - start.x, end.y - start.y);
            var unitXSs = (end.x - start.x) / lengthSs;
            var unitYSs = (end.y - start.y) / lengthSs;

            var points = capturePathPoints(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{start, end}, THICKNESS_SS));

            assertThat(points).as("two-point path has two vertices").hasSize(2);
            assertThat(points.get(0).getX())
                .as("start inset along the segment direction in x")
                .isCloseTo(start.x + unitXSs * halfWidthSs, within(TOLERANCE));
            assertThat(points.get(0).getY())
                .as("start inset along the segment direction in y")
                .isCloseTo(start.y + unitYSs * halfWidthSs, within(TOLERANCE));
            assertThat(points.get(1).getX())
                .as("end inset back along the segment direction in x")
                .isCloseTo(end.x - unitXSs * halfWidthSs, within(TOLERANCE));
            assertThat(points.get(1).getY())
                .as("end inset back along the segment direction in y")
                .isCloseTo(end.y - unitYSs * halfWidthSs, within(TOLERANCE));
        }

        @Test
        void testCoincidentEndSegmentLeavesEndpointUninsetWithoutNaN() {
            // A zero-length first/last segment has no direction to inset along. capInset must return
            // the endpoint unchanged rather than dividing by zero and poisoning the path with NaN.
            var points = capturePathPoints(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{
                    new Point2D.Double(X1, Y),
                    new Point2D.Double(X1, Y),
                    new Point2D.Double(X2, Y)
                }, THICKNESS_SS));

            assertThat(points).as("no path vertex is NaN").allSatisfy(point -> {
                assertThat(point.getX()).isNotNaN();
                assertThat(point.getY()).isNotNaN();
            });
            assertThat(points.getFirst())
                .as("zero-length first segment leaves the start uninset")
                .isEqualTo(new Point2D.Double(X1, Y));
        }

        @Test
        void testClosingStrokeBracketFreeEndsCapToArmBottom() {
            // The 4-point U of a closing-stroke ending bracket: both vertical free ends are inset
            // upward and their round caps must land exactly on the arm bottom, while the two top
            // corners are joins that bulge half a thickness outward.
            var halfWidthSs = THICKNESS_SS / 2.0;

            var bounds = captureStrokedBounds(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{
                    new Point2D.Double(X1, Y + CORNER_DROP),
                    new Point2D.Double(X1, Y),
                    new Point2D.Double(X2, Y),
                    new Point2D.Double(X2, Y + CORNER_DROP)
                }, THICKNESS_SS));

            assertThat(bounds.getMaxY())
                .as("both free ends cap to exactly the arm bottom")
                .isCloseTo(Y + CORNER_DROP, within(TOLERANCE));
            assertThat(bounds.getMinY())
                .as("top corners join half a thickness above the bracket line")
                .isCloseTo(Y - halfWidthSs, within(TOLERANCE));
            assertThat(bounds.getMinX())
                .as("left arm spans half a thickness left of x1")
                .isCloseTo(X1 - halfWidthSs, within(TOLERANCE));
            assertThat(bounds.getMaxX())
                .as("right arm spans half a thickness right of x2")
                .isCloseTo(X2 + halfWidthSs, within(TOLERANCE));
        }

        @Test
        void testFewerThanTwoPointsDrawsNothing() {
            // The contract requires at least two points; degenerate input must be a silent no-op
            // rather than throwing an ArrayIndexOutOfBoundsException.
            assertThat(countDraws(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{}, THICKNESS_SS)))
                .as("empty path draws nothing")
                .isZero();

            assertThat(countDraws(g2 ->
                GraphicUtils.drawPath(g2, new Point2D[]{new Point2D.Double(X1, Y)}, THICKNESS_SS)))
                .as("single-point path draws nothing")
                .isZero();
        }

        /**
         * Captures the vertices of the {@link java.awt.geom.Path2D} {@code drawPath} builds, before
         * it is stroked, so a test can assert the exact inset coordinates {@code capInset} produced.
         */
        private static List<Point2D> capturePathPoints(Consumer<? super Graphics2D> draw) {
            var image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
            var g2 = spy(image.createGraphics());
            var captured = new Shape[]{null};

            doAnswer(answerVoid((Shape shape) -> captured[0] = shape)).when(g2).draw(any(Shape.class));

            try {
                draw.accept(g2);
            } finally {
                g2.dispose();
            }

            verify(g2, times(1)).draw(any(Shape.class));

            var points = new ArrayList<Point2D>();
            var coords = new double[PATH_SEGMENT_COORDS];

            for (var iterator = captured[0].getPathIterator(null); !iterator.isDone(); iterator.next()) {
                iterator.currentSegment(coords);
                points.add(new Point2D.Double(coords[0], coords[1]));
            }

            return points;
        }

        /** Returns how many times {@code drawPath} invoked {@code g2.draw}. */
        private static int countDraws(Consumer<? super Graphics2D> draw) {
            var image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
            var g2 = spy(image.createGraphics());
            var drawCount = new int[]{0};

            doAnswer(answerVoid((Shape shape) -> drawCount[0]++)).when(g2).draw(any(Shape.class));

            try {
                draw.accept(g2);
            } finally {
                g2.dispose();
            }

            return drawCount[0];
        }
    }

    /**
     * The real-font counterpart to the hand-built-bounds arithmetic in
     * {@code TitleFootnotesLyricsComponentTest.InkPadding}: an installed face whose glyphs
     * genuinely rise above their own nominal ascent must be padded for. Without a real
     * overshooting font somewhere in the suite, nothing distinguishes a correct
     * {@link GraphicUtils#extraInkAbove} from one that concludes no font ever overshoots.
     * <p>
     * The descent side of the same property is covered end to end by
     * {@code TitleFootnotesLyricsComponentTest.testPreferredSizeHeightCoversDescenderOvershoot}.
     */
    @Test
    void testExtraInkAbovePadsARealFontThatOvershootsItsAscent() {
        assumeTrue(
            isFontFamilyAvailable(OVERSHOOT_FONT_FAMILY),
            OVERSHOOT_FONT_FAMILY + " is not installed on this system"
        );

        var font = new Font(OVERSHOOT_FONT_FAMILY, Font.PLAIN, OVERSHOOT_FONT_SIZE);
        var ascent = GraphicUtils.fontMetrics(font).getAscent();
        var bounds = GraphicUtils.visualBounds(OVERSHOOT_TEXT, font);

        assertThat(bounds).as("ink bounds for '" + OVERSHOOT_TEXT + "' must not be null").isNotNull();

        // Precondition: confirm this font/size actually overshoots the nominal ascent
        // here, otherwise the assertion below would hold trivially.
        assumeTrue(
            GraphicUtils.inkHeight(bounds) > ascent,
            OVERSHOOT_TEXT + " in " + OVERSHOOT_FONT_FAMILY + " does not overshoot the ascent here"
        );

        assertThat(GraphicUtils.extraInkAbove(bounds, ascent))
            .as("ink rising above the nominal ascent must be padded for")
            .isPositive();
    }
}
