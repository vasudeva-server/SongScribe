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
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.layout.LineJustificationCalculator.JustificationResult;

class LineJustificationCalculatorTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Column construction helpers
    // -----------------------------------------------------------------------

    /**
     * Builds an ElementColumn with the given position and extents, no syllable.
     *
     * @param xSs           Position of the element head left edge (glyph origin)
     * @param leftExtentSs  Left extent (0 or negative for accidentals)
     * @param rightExtentSs Right extent (positive — element width + dots)
     */
    private static ElementColumn columnAt(double xSs, double leftExtentSs, double rightExtentSs) {
        var col = new ElementColumn(
            crotchet(),
            Collections.emptyList(),
            leftExtentSs,
            rightExtentSs,
            0, 0,
            null, 0,
            false
        );
        col.setXSs(xSs);
        return col;
    }

    /**
     * Builds an ElementColumn with the given position, extents, and syllable.
     */
    private static ElementColumn columnWithSyllableAt(
        double xSs,
        double leftExtentSs,
        double rightExtentSs,
        String syllable,
        double syllableWidthSs) {
        var col = new ElementColumn(
            crotchet(),
            Collections.emptyList(),
            leftExtentSs,
            rightExtentSs,
            0, 0,
            syllable, syllableWidthSs,
            false
        );
        col.setXSs(xSs);
        return col;
    }

    // -----------------------------------------------------------------------
    // Row 43: empty list → success (no exception)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EmptyColumnList {

        @Test
        void testJustifyEmptyListReturnsSuccess() {
            // Row 43: justifyLine on an empty list must return a success result
            // without throwing an exception.
            var calculator = new LineJustificationCalculator();
            var result = calculator.justifyLine(Collections.emptyList(), 10.0);
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Row 44: line already fits → success, no compression applied
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineFits {

        /**
         * Right margin that leaves the line clearly within bounds.
         * <p>
         * The single column has rightEdgeXSs = FITS_COL_X + FITS_COL_RIGHT_EXTENT = 3.0,
         * and this margin is larger, so no compression should trigger.
         */
        private static final double FITS_STAFF_RIGHT_MARGIN_SS = 8.0;

        /** X position of the sole column's glyph origin. */
        private static final double FITS_COL_X = 2.0;
        /** Left extent (no accidental). */
        private static final double FITS_COL_LEFT_EXTENT_SS = 0.0;
        /** Right extent (head width). */
        private static final double FITS_COL_RIGHT_EXTENT_SS = 1.0;

        @Test
        void testLineFitsReturnsSuccessWithNoCompression() {
            // Row 44: when lastColumn.getRightEdgeXSs() <= staffRightMarginSs the
            // result must be success AND wasCompressionApplied() must be false.
            var calculator = new LineJustificationCalculator();
            var col = columnAt(FITS_COL_X, FITS_COL_LEFT_EXTENT_SS, FITS_COL_RIGHT_EXTENT_SS);

            // Verify precondition: line really does fit.
            assertThat(col.getRightEdgeXSs()).isLessThanOrEqualTo(FITS_STAFF_RIGHT_MARGIN_SS);

            var result = calculator.justifyLine(List.of(col), FITS_STAFF_RIGHT_MARGIN_SS);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.wasCompressionApplied()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Rows 45 & 46: compression triggered — ratio and positions are exact
    // -----------------------------------------------------------------------

    /**
     * Shared fixture for rows 45 and 46.
     * <p>
     * Layout (all values in staff spaces):
     * <pre>
     *   firstColumn:  xSs=2.0, leftExtentSs=-0.5, rightExtentSs=1.0
     *     leftEdgeXSs  = 2.0 + (-0.5) = 1.5
     *     rightEdgeXSs = 2.0 + 1.0    = 3.0
     *
     *   secondColumn: xSs=6.0, leftExtentSs=0.0,  rightExtentSs=1.0
     *     rightEdgeXSs = 6.0 + 1.0 = 7.0
     *
     *   staffRightMarginSs = 5.0  →  7.0 > 5.0, triggers compression
     *
     * Compression ratio derivation:
     *   centerSpanSs    = 6.0 - 2.0 = 4.0
     *   extentOffsetSs  = 1.0 - (-0.5) = 1.5
     *   targetWidthSs   = 5.0 - 1.5 = 3.5   (margin - firstCol.leftEdgeXSs)
     *   compressionRatio = (3.5 - 1.5) / 4.0 = 0.5
     *
     * Post-compression positions:
     *   firstColumn stays at xSs = 2.0  (anchor)
     *   secondColumn.newXSs = 2.0 + (6.0 - 2.0) * 0.5 = 4.0
     * </pre>
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CompressionTriggered {

        private static final double FIRST_COL_X = 2.0;
        private static final double FIRST_COL_LEFT_EXTENT_SS = -0.5;
        private static final double FIRST_COL_RIGHT_EXTENT_SS = 1.0;

        private static final double SECOND_COL_X = 6.0;
        private static final double SECOND_COL_LEFT_EXTENT_SS = 0.0;
        private static final double SECOND_COL_RIGHT_EXTENT_SS = 1.0;

        /** Right margin chosen so that the second column's right edge exceeds it. */
        private static final double COMPRESS_STAFF_RIGHT_MARGIN_SS = 5.0;

        // Derived constants — recomputing the production formula exactly.
        private static final double EXPECTED_CENTER_SPAN_SS = SECOND_COL_X - FIRST_COL_X;  // 4.0
        private static final double EXPECTED_EXTENT_OFFSET_SS =
            SECOND_COL_RIGHT_EXTENT_SS - FIRST_COL_LEFT_EXTENT_SS;  // 1.5
        private static final double FIRST_COL_LEFT_EDGE_SS =
            FIRST_COL_X + FIRST_COL_LEFT_EXTENT_SS;  // 1.5
        private static final double EXPECTED_TARGET_WIDTH_SS =
            COMPRESS_STAFF_RIGHT_MARGIN_SS - FIRST_COL_LEFT_EDGE_SS;  // 3.5
        private static final double EXPECTED_COMPRESSION_RATIO =
            (EXPECTED_TARGET_WIDTH_SS - EXPECTED_EXTENT_OFFSET_SS) / EXPECTED_CENTER_SPAN_SS;  // 0.5
        private static final double EXPECTED_SECOND_COL_X_AFTER =
            FIRST_COL_X + (SECOND_COL_X - FIRST_COL_X) * EXPECTED_COMPRESSION_RATIO;  // 4.0

        @Test
        void testCompressionRatioAndResultColumnsPositions() {
            // Row 45: verify the compression ratio (pinned to exact computed value)
            // and resulting column positions after justifyLine modifies them in-place.
            var calculator = new LineJustificationCalculator();
            var firstCol = columnAt(FIRST_COL_X, FIRST_COL_LEFT_EXTENT_SS, FIRST_COL_RIGHT_EXTENT_SS);
            var secondCol = columnAt(SECOND_COL_X, SECOND_COL_LEFT_EXTENT_SS, SECOND_COL_RIGHT_EXTENT_SS);

            // Verify precondition: line actually overflows.
            assertThat(secondCol.getRightEdgeXSs()).isGreaterThan(COMPRESS_STAFF_RIGHT_MARGIN_SS);

            var result = calculator.justifyLine(List.of(firstCol, secondCol), COMPRESS_STAFF_RIGHT_MARGIN_SS);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.wasCompressionApplied()).isTrue();

            // The ratio determines both column positions — verify via positions,
            // which fully encode the ratio (newX = anchor + offset * ratio).
            // First column is the anchor and must not move.
            assertThat(firstCol.getXSs()).isEqualTo(FIRST_COL_X);
            // Second column must land at exactly anchor + offset * ratio.
            assertThat(secondCol.getXSs()).isEqualTo(EXPECTED_SECOND_COL_X_AFTER);
        }

        @Test
        void testApplyCompressionFirstColumnFixedRestScaleByRatio() {
            // Row 46: assert exact compressed positions — first column stays put,
            // subsequent columns scale by ratio relative to the anchor.
            //
            // Three-column fixture (all values in staff spaces), chosen so that
            // the compressed gaps stay well above MIN_COLUMN_GAP_SS:
            //
            //   anchorCol:  xSs=1.0, left=0.0, right=0.5  → leftEdge=1.0, rightEdge=1.5
            //   midCol:     xSs=5.0, left=0.0, right=0.5  → rightEdge=5.5
            //   tailCol:    xSs=9.0, left=0.0, right=0.5  → rightEdge=9.5
            //   staffRightMarginSs = 5.0  →  9.5 > 5.0, triggers compression
            //
            // Ratio derivation (formula uses first and last columns only):
            //   centerSpanSs   = 9.0 − 1.0 = 8.0
            //   extentOffsetSs = 0.5 − 0.0 = 0.5   (lastRight − firstLeft)
            //   targetWidthSs  = 5.0 − 1.0 = 4.0   (margin − firstLeftEdge)
            //   compressionRatio = (4.0 − 0.5) / 8.0 = 3.5 / 8.0 = 0.4375
            //
            // Compressed gap validation (both pairs identical by symmetry):
            //   currentGapSs  = (5.0 − 1.5) = 3.5;  centerDist = 4.0
            //   compressedGap = 3.5 − 4.0 × (1 − 0.4375) = 1.25 > 1.0 ✓
            //
            // Post-compression positions:
            //   midCol.newXSs  = 1.0 + (5.0 − 1.0) × 0.4375 = 2.75
            //   tailCol.newXSs = 1.0 + (9.0 − 1.0) × 0.4375 = 4.5

            // Three-column fixture constants.
            final double anchorX = 1.0;
            final double anchorLeftExtentSs = 0.0;
            final double anchorRightExtentSs = 0.5;

            final double midColX = 5.0;
            final double midColLeftExtentSs = 0.0;
            final double midColRightExtentSs = 0.5;

            final double tailColX = 9.0;
            final double tailColLeftExtentSs = 0.0;
            final double tailColRightExtentSs = 0.5;

            final double threeColMarginSs = 5.0;

            // Derived ratio — mirrors the production formula exactly.
            final double threeColCenterSpan = tailColX - anchorX;  // 8.0
            final double threeColExtentOffset = tailColRightExtentSs - anchorLeftExtentSs;  // 0.5
            final double anchorLeftEdge = anchorX + anchorLeftExtentSs;  // 1.0
            final double threeColTargetWidth = threeColMarginSs - anchorLeftEdge;  // 4.0
            final double threeColRatio =
                (threeColTargetWidth - threeColExtentOffset) / threeColCenterSpan;  // 0.4375

            final double expectedMidXAfter = anchorX + (midColX - anchorX) * threeColRatio;  // 2.75
            final double expectedTailXAfter = anchorX + (tailColX - anchorX) * threeColRatio;  // 4.5

            var calculator = new LineJustificationCalculator();
            var anchorCol = columnAt(anchorX, anchorLeftExtentSs, anchorRightExtentSs);
            var midCol = columnAt(midColX, midColLeftExtentSs, midColRightExtentSs);
            var tailCol = columnAt(tailColX, tailColLeftExtentSs, tailColRightExtentSs);

            var result = calculator.justifyLine(
                List.of(anchorCol, midCol, tailCol), threeColMarginSs);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.wasCompressionApplied()).isTrue();

            // First column (anchor) must not move.
            assertThat(anchorCol.getXSs()).isEqualTo(anchorX);
            // Each subsequent column scales by ratio relative to the anchor.
            assertThat(midCol.getXSs()).isEqualTo(expectedMidXAfter);
            assertThat(tailCol.getXSs()).isEqualTo(expectedTailXAfter);
        }
    }

    // -----------------------------------------------------------------------
    // Row 47: validateCompression rejects when column gap < MIN_COLUMN_GAP_SS
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateCompressionColumnGapViolation {

        @Test
        void testTightColumnsProduceColumnGapFailure() {
            // Row 47: when the post-compression column gap falls below
            // MIN_COLUMN_GAP_SS (1.0 ss), justifyLine must return failure.
            //
            // Fixture (staff spaces):
            //   firstCol:  xSs=1.0, left=0.0, right=0.5  → rightEdge=1.5
            //   secondCol: xSs=3.0, left=0.0, right=1.5  → rightEdge=4.5
            //   margin=3.0  →  4.5 > 3.0, triggers compression
            //
            // compressionRatio = (targetWidth - extentOffset) / centerSpan
            //                  = ((3.0 - 1.0) - 1.5) / (3.0 - 1.0) = 0.5 / 2.0 = 0.25
            //
            // compressedGap = currentGap - centerDist * (1 - ratio)
            //               = 1.5 - 2.0 * (1.0 - 0.25) = 1.5 - 1.5 = 0.0
            //
            // 0.0 < MIN_COLUMN_GAP_SS (1.0) → failure
            final double firstColX = 1.0;
            final double firstColLeftExtentSs = 0.0;
            final double firstColRightExtentSs = 0.5;
            final double secondColX = 3.0;
            final double secondColLeftExtentSs = 0.0;
            final double secondColRightExtentSs = 1.5;
            final double tightMarginSs = 3.0;

            var calculator = new LineJustificationCalculator();
            var firstCol = columnAt(firstColX, firstColLeftExtentSs, firstColRightExtentSs);
            var secondCol = columnAt(secondColX, secondColLeftExtentSs, secondColRightExtentSs);

            // Precondition: line overflows.
            assertThat(secondCol.getRightEdgeXSs()).isGreaterThan(tightMarginSs);

            var result = calculator.justifyLine(List.of(firstCol, secondCol), tightMarginSs);

            assertThat(result.isSuccess()).isFalse();
        }

        // #418 floor change: the compression floor was raised from the old 0.125 ss to
        // MIN_COLUMN_GAP_SS (1.0 ss). A compressed column gap of 0.5 ss now fails, even though it
        // would have passed the old floor — pinning the new, larger floor.
        //
        // Fixture (staff spaces, no syllables so the column-gap check is the gate):
        //   firstCol:  xSs=1.0, left=0.0, right=0.5  → rightEdge=1.5
        //   secondCol: xSs=3.0, left=0.0, right=1.0  → rightEdge=4.0
        //   margin=3.0  →  4.0 > 3.0, triggers compression
        //
        // compressionRatio = ((3.0 - 1.0) - 1.0) / (3.0 - 1.0) = 1.0 / 2.0 = 0.5
        // compressedGap = currentGap - centerDist * (1 - ratio)
        //               = 1.5 - 2.0 * (1.0 - 0.5) = 0.5
        //
        // 0.125 (old floor) < 0.5 < 1.0 (MIN_COLUMN_GAP_SS) → fails only under the new floor
        @Test
        void testCompressedGapBelowNewFloorButAboveOldFloorFails() {
            final double firstColX = 1.0;
            final double firstColLeftExtentSs = 0.0;
            final double firstColRightExtentSs = 0.5;
            final double secondColX = 3.0;
            final double secondColLeftExtentSs = 0.0;
            final double secondColRightExtentSs = 1.0;
            final double marginSs = 3.0;

            var calculator = new LineJustificationCalculator();
            var firstCol = columnAt(firstColX, firstColLeftExtentSs, firstColRightExtentSs);
            var secondCol = columnAt(secondColX, secondColLeftExtentSs, secondColRightExtentSs);

            // Precondition: line overflows.
            assertThat(secondCol.getRightEdgeXSs()).isGreaterThan(marginSs);

            var result = calculator.justifyLine(List.of(firstCol, secondCol), marginSs);

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 48: validateCompression rejects when syllable gap < COMPRESSED_MIN_SYLLABLE_GAP_SS
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateCompressionSyllableGapViolation {

        @Test
        void testWideSyllableColumnsProduceSyllableGapFailure() {
            // Row 48: when the post-compression syllable gap falls below
            // LyricRenderMetrics.COMPRESSED_MIN_SYLLABLE_GAP_SS, justifyLine must fail.
            // The column gap check must pass first (so the syllable check is actually reached).
            //
            // Fixture (staff spaces):
            //   firstCol:  xSs=1.0, left=0.0, right=0.5, syllableWidthSs=2.0
            //   secondCol: xSs=4.0, left=0.0, right=0.5, syllableWidthSs=2.0
            //   margin=3.0  →  4.5 > 3.0, triggers compression
            //
            // compressionRatio = ((3.0 - 1.0) - 0.5) / (4.0 - 1.0) = 1.5 / 3.0 = 0.5
            //
            // compressedColumnGap = 2.5 - 3.0 * (1.0 - 0.5) = 1.0  → not < MIN_COLUMN_GAP_SS (1.0),
            //                       passes at the boundary so the syllable check is reached
            //
            // currentSyllableGap = centerDist - prevHalfWidth - currHalfWidth
            //                    = 3.0 - 1.0 - 1.0 = 1.0
            // compressedSyllableGap = 1.0 - 3.0 * (1.0 - 0.5) = -0.5
            //
            // -0.5 < COMPRESSED_MIN_SYLLABLE_GAP_SS (0.125) → failure
            final double firstColX = 1.0;
            final double firstColLeftExtentSs = 0.0;
            final double firstColRightExtentSs = 0.5;
            final double syllableWidthSs = 2.0;
            final double secondColX = 4.0;
            final double secondColLeftExtentSs = 0.0;
            final double secondColRightExtentSs = 0.5;
            final double syllableMarginSs = 3.0;

            var calculator = new LineJustificationCalculator();
            var firstCol = columnWithSyllableAt(
                firstColX, firstColLeftExtentSs, firstColRightExtentSs, "la", syllableWidthSs);
            var secondCol = columnWithSyllableAt(
                secondColX, secondColLeftExtentSs, secondColRightExtentSs, "la", syllableWidthSs);

            // Precondition: line overflows.
            assertThat(secondCol.getRightEdgeXSs()).isGreaterThan(syllableMarginSs);
            // Precondition: both columns have syllables (syllable check will be reached).
            assertThat(firstCol.hasSyllable()).isTrue();
            assertThat(secondCol.hasSyllable()).isTrue();

            var result = calculator.justifyLine(List.of(firstCol, secondCol), syllableMarginSs);

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 49: JustificationResult factories + getErrorMessage null contract
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ResultFactories {

        @Test
        void testSuccessFactoryHasNoCompressionAndNullErrorMessage() {
            var result = JustificationResult.success();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.wasCompressionApplied()).isFalse();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        void testSuccessWithCompressionFactoryHasCompressionAndNullErrorMessage() {
            var result = JustificationResult.successWithCompression();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.wasCompressionApplied()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        void testFailureFactoryHasNonNullErrorMessage() {
            final var errorMsg = "Note cannot fit on line";
            var result = JustificationResult.failure(errorMsg);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.wasCompressionApplied()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo(errorMsg);
        }
    }
}
