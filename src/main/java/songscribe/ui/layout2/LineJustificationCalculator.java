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

package songscribe.ui.layout2;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles line justification by compressing spacing when the line exceeds the staff right margin.
 * <p>
 * Key principles from Gould/Ross:
 * <ul>
 *   <li>Compression is allowed (with limits) to fit notes within the margin</li>
 *   <li>Expansion for justification is NOT done (preserves non-proportional spacing)</li>
 *   <li>If compression would violate minimum gaps, the note is rejected</li>
 * </ul>
 * <p>
 * Algorithm:
 * <ol>
 *   <li>Check if rightmost column exceeds staff right margin</li>
 *   <li>If line exceeds margin:
 *     <ul>
 *       <li>Calculate required compression ratio</li>
 *       <li>Verify all gaps remain above minimums after compression</li>
 *       <li>Apply compression uniformly if possible, or reject if not</li>
 *     </ul>
 *   </li>
 *   <li>If line is under margin: no adjustment (uneven spacing is correct)</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var calculator = new LineJustificationCalculator();
 * var result = calculator.justifyLine(columns, staffRightMargin);
 *
 * if (!result.isSuccess()) {
 *     // Alert user: result.getErrorMessage()
 * }
 * // columns now have adjusted X positions
 * }</pre>
 */
public class LineJustificationCalculator {

    /**
     * Result of line justification attempt.
     */
    public static class JustificationResult {
        private final boolean success;
        private final String errorMessage;
        private final boolean compressionApplied;

        private JustificationResult(boolean success, String errorMessage, boolean compressionApplied) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.compressionApplied = compressionApplied;
        }

        /**
         * Creates a successful result with no compression needed.
         */
        public static JustificationResult success() {
            return new JustificationResult(true, null, false);
        }

        /**
         * Creates a successful result with compression applied.
         */
        public static JustificationResult successWithCompression() {
            return new JustificationResult(true, null, true);
        }

        /**
         * Creates a failure result with an error message.
         */
        public static JustificationResult failure(@NotNull String errorMessage) {
            return new JustificationResult(false, errorMessage, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public @Nullable String getErrorMessage() {
            return errorMessage;
        }

        public boolean wasCompressionApplied() {
            return compressionApplied;
        }
    }

    /**
     * Creates a new LineJustificationCalculator.
     */
    public LineJustificationCalculator() {
    }

    /**
     * Justifies a line by compressing spacing if it exceeds the right margin.
     * <p>
     * This method modifies column X positions in-place if compression is needed.
     *
     * @param columns          List of columns (must have X positions already set)
     * @param staffRightMargin The right margin of the staff in pixels
     * @return JustificationResult indicating success or failure
     */
    public @NotNull JustificationResult justifyLine(
            @NotNull List<NoteColumn> columns,
            double staffRightMargin) {

        if (columns.isEmpty()) {
            return JustificationResult.success();
        }

        // Check if line exceeds margin
        var lastColumn = columns.get(columns.size() - 1);
        double lineWidth = lastColumn.getRightEdgeX();

        if (lineWidth <= staffRightMargin) {
            // Line fits - no adjustment needed (uneven spacing is correct per Gould/Ross)
            return JustificationResult.success();
        }

        // Line exceeds margin - attempt compression
        return compressLine(columns, staffRightMargin);
    }

    // ==========================================================================
    // Compression Algorithm
    // ==========================================================================

    /**
     * Compresses the line to fit within the staff right margin.
     *
     * @param columns          List of columns
     * @param staffRightMargin Right margin in pixels
     * @return JustificationResult indicating success or failure
     */
    private @NotNull JustificationResult compressLine(
            @NotNull List<NoteColumn> columns,
            double staffRightMargin) {

        // Calculate compression ratio
        var firstColumn = columns.get(0);
        var lastColumn = columns.get(columns.size() - 1);

        // The total width includes the span between column centers plus the extents
        // Width = centerSpan + (lastRightExtent - firstLeftExtent)
        // When compressing, only the centerSpan changes, extents stay fixed
        double centerSpan = lastColumn.getX() - firstColumn.getX();
        double extentOffset = lastColumn.getRightExtent() - firstColumn.getLeftExtent();

        double calculatedWidth = centerSpan + extentOffset;
        double targetWidth = staffRightMargin - firstColumn.getLeftEdgeX();

        // ratio = (targetWidth - extentOffset) / centerSpan
        double compressionRatio = (targetWidth - extentOffset) / centerSpan;

        // Validate that compression is possible
        var validation = validateCompression(columns, compressionRatio);

        if (!validation.isValid()) {
            return JustificationResult.failure(validation.getErrorMessage());
        }

        // Apply compression uniformly
        applyCompression(columns, compressionRatio);

        return JustificationResult.successWithCompression();
    }

    /**
     * Represents validation result for compression.
     */
    private static class CompressionValidation {
        private final boolean valid;
        private final String errorMessage;

        private CompressionValidation(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        static CompressionValidation valid() {
            return new CompressionValidation(true, null);
        }

        static CompressionValidation invalid(@NotNull String errorMessage) {
            return new CompressionValidation(false, errorMessage);
        }

        boolean isValid() {
            return valid;
        }

        @Nullable String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validates that compression would not violate minimum gap requirements.
     *
     * @param columns          List of columns
     * @param compressionRatio The compression ratio to apply
     * @return CompressionValidation result
     */
    private @NotNull CompressionValidation validateCompression(
            @NotNull List<NoteColumn> columns,
            double compressionRatio) {

        double minColumnGap = LayoutConstants.px(LayoutConstants.COMPRESSED_MIN_COLUMN_GAP);
        double minSyllableGap = LayoutConstants.px(LayoutConstants.COMPRESSED_MIN_SYLLABLE_GAP);

        var firstColumn = columns.get(0);
        double anchorX = firstColumn.getX();

        for (var i = 1; i < columns.size(); i++) {
            var prevColumn = columns.get(i - 1);
            var currColumn = columns.get(i);

            // Calculate current gap between column edges
            double prevRightEdge = prevColumn.getX() + prevColumn.getRightExtent();
            double currLeftEdge = currColumn.getX() + currColumn.getLeftExtent();
            double currentGap = currLeftEdge - prevRightEdge;

            // Calculate compressed gap
            // When we compress, positions change relative to anchor (first column):
            // Gap' = Gap - (X_i - X_{i-1}) * (1 - ratio)
            // This is because center distances compress but extents stay fixed
            double centerDistance = currColumn.getX() - prevColumn.getX();
            double compressedGap = currentGap - centerDistance * (1.0 - compressionRatio);

            // Check minimum column gap
            if (compressedGap < minColumnGap) {
                return CompressionValidation.invalid(
                        "Note cannot fit on line while maintaining minimum spacing");
            }

            // Check minimum syllable gap if both columns have syllables
            if (prevColumn.hasSyllable() && currColumn.hasSyllable()) {
                double syllableGap = calculateSyllableGap(prevColumn, currColumn, compressionRatio);

                if (syllableGap < minSyllableGap) {
                    return CompressionValidation.invalid(
                            "Note cannot fit on line while maintaining minimum syllable spacing");
                }
            }
        }

        return CompressionValidation.valid();
    }

    /**
     * Calculates the gap between syllables after compression.
     *
     * @param prevColumn       Previous column
     * @param currColumn       Current column
     * @param compressionRatio Compression ratio
     * @return Syllable gap in pixels
     */
    private double calculateSyllableGap(
            @NotNull NoteColumn prevColumn,
            @NotNull NoteColumn currColumn,
            double compressionRatio) {

        // Current distance between column centers
        double centerDistance = currColumn.getX() - prevColumn.getX();

        // Current syllable gap
        double prevHalfWidth = prevColumn.getSyllableWidth() / 2.0;
        double currHalfWidth = currColumn.getSyllableWidth() / 2.0;
        double currentSyllableGap = centerDistance - prevHalfWidth - currHalfWidth;

        // Compressed syllable gap (same formula as column gap)
        // Gap' = Gap - centerDistance * (1 - ratio)
        double compressedSyllableGap = currentSyllableGap - centerDistance * (1.0 - compressionRatio);

        return compressedSyllableGap;
    }

    /**
     * Applies compression uniformly to all column positions.
     * <p>
     * The first column remains fixed; all subsequent columns are compressed
     * toward the first column by the compression ratio.
     *
     * @param columns          List of columns
     * @param compressionRatio The compression ratio to apply
     */
    private void applyCompression(
            @NotNull List<NoteColumn> columns,
            double compressionRatio) {

        if (columns.isEmpty()) {
            return;
        }

        var firstColumn = columns.get(0);
        double anchorX = firstColumn.getX();

        // Compress all positions relative to the first column
        for (var i = 1; i < columns.size(); i++) {
            var column = columns.get(i);
            double currentOffset = column.getX() - anchorX;
            double compressedOffset = currentOffset * compressionRatio;
            column.setX(anchorX + compressedOffset);
        }
    }
}
