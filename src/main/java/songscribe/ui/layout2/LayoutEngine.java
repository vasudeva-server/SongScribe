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

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.Bounds;
import songscribe.ui.layout.LineElement;

/**
 * Orchestrates the complete layout pipeline for a staff line.
 * <p>
 * The LayoutEngine coordinates all layout calculators to produce a final {@link LayoutResult}
 * containing positioned elements ready for rendering. The pipeline executes in this order:
 * <ol>
 *   <li>{@link NoteColumnBuilder} - Creates note columns from the line's notes</li>
 *   <li>{@link HorizontalSpacingCalculator} - Positions columns horizontally (lyric-driven)</li>
 *   <li>{@link VerticalStackingCalculator} - Positions elements vertically (layer-by-layer)</li>
 *   <li>{@link LineJustificationCalculator} - Compresses spacing if line exceeds margin</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var engine = new LayoutEngine(g2, lyricsFont, staffRightMargin);
 * LayoutResult result = engine.layout(line);
 *
 * if (result == null) {
 *     // Layout failed (line justification error)
 *     String error = engine.getLastError();
 *     // Display error to user
 * } else {
 *     // Use result for rendering
 * }
 * }</pre>
 */
public class LayoutEngine {

    // Standard staff height (5 lines * 4 spaces = 32 pixels based on 8px line spacing)
    private static final double STAFF_HEIGHT = 32.0;

    private final Graphics2D g2;
    private final Font lyricsFont;
    private final double staffRightMargin;

    // Calculators
    private final NoteColumnBuilder columnBuilder;
    private final HorizontalSpacingCalculator horizontalCalculator;
    private final VerticalStackingCalculator verticalCalculator;
    private final LineJustificationCalculator justificationCalculator;

    // Error tracking
    private String lastError;

    /**
     * Creates a new LayoutEngine.
     *
     * @param g2               Graphics context for text measurement
     * @param lyricsFont       Font to use for lyrics (for measuring syllable widths)
     * @param staffRightMargin Right margin of the staff in pixels
     */
    public LayoutEngine(
            @NotNull Graphics2D g2,
            @NotNull Font lyricsFont,
            double staffRightMargin) {
        this.g2 = g2;
        this.lyricsFont = lyricsFont;
        this.staffRightMargin = staffRightMargin;

        // Initialize calculators
        this.columnBuilder = new NoteColumnBuilder(g2, lyricsFont);
        this.horizontalCalculator = new HorizontalSpacingCalculator();
        this.verticalCalculator = new VerticalStackingCalculator();
        this.justificationCalculator = new LineJustificationCalculator();

        this.lastError = null;
    }

    /**
     * Executes the complete layout pipeline for a line.
     * <p>
     * This is the main entry point for layout. It orchestrates all calculators
     * and produces a final LayoutResult ready for rendering.
     *
     * @param line The line to lay out
     * @return LayoutResult with all positioned elements, or null if layout fails
     */
    public @Nullable LayoutResult layout(@NotNull Line line) {
        lastError = null;

        // Step 1: Build note columns
        List<NoteColumn> columns = columnBuilder.buildColumns(line);

        if (columns.isEmpty()) {
            // Empty line - return empty result
            return LayoutResult.builder()
                .setLineHeight(STAFF_HEIGHT)
                .setStaffGeometry(0, STAFF_HEIGHT)
                .setLyricBaselineY(0)
                .build();
        }

        // Step 2: Calculate horizontal positions
        horizontalCalculator.calculatePositions(columns, line);

        // Step 3: Apply line justification (compression if needed)
        var justificationResult = justificationCalculator.justifyLine(columns, staffRightMargin);

        if (!justificationResult.isSuccess()) {
            // Line cannot fit within margin while maintaining minimum spacing
            lastError = justificationResult.getErrorMessage();
            return null;
        }

        // Step 4: Calculate vertical positions
        var verticalResult = verticalCalculator.calculateVerticalPositions(columns, line, g2);

        // Step 5: Build final LayoutResult
        return buildLayoutResult(columns, verticalResult, line);
    }

    /**
     * Returns the last error message from a failed layout attempt.
     *
     * @return Error message, or null if no error
     */
    public @Nullable String getLastError() {
        return lastError;
    }

    /**
     * Builds the final LayoutResult from calculated positions.
     */
    private LayoutResult buildLayoutResult(
            @NotNull List<NoteColumn> columns,
            @NotNull VerticalStackingResult verticalResult,
            @NotNull Line line) {

        var builder = LayoutResult.builder();

        // Add note columns
        for (var column : columns) {
            builder.putNoteColumn(column.getNote(), column);
        }

        // Add element bounds from vertical stacking result
        var elementPositions = verticalResult.getElementPositions();

        for (var noteEntry : elementPositions.entrySet()) {
            var note = noteEntry.getKey();
            var elementMap = noteEntry.getValue();

            for (var elementEntry : elementMap.entrySet()) {
                var element = elementEntry.getKey();
                var position = elementEntry.getValue();

                // Create bounds for this element
                // For now, use simple rectangular bounds based on element content size
                var contentBounds = new Rectangle2D.Double(
                    position.getX(),
                    position.getY(),
                    element.getContentWidth(),
                    element.getContentHeight()
                );

                var bounds = element.getBounds();

                // Update bounds position to match calculated position
                bounds = new Bounds(
                    contentBounds,
                    new Rectangle2D.Double(
                        position.getX() - element.getMarginLeft(),
                        position.getY() - element.getMarginTop(),
                        element.getContentWidth() + element.getMarginLeft() + element.getMarginRight(),
                        element.getContentHeight() + element.getMarginTop() + element.getMarginBottom()
                    )
                );

                builder.putElementBounds(element, bounds);
            }
        }

        // Set staff geometry
        double staffTopY = 0;
        double staffBottomY = STAFF_HEIGHT;

        builder.setStaffGeometry(staffTopY, staffBottomY);

        // Set line height and lyrics baseline from vertical result
        builder.setLineHeight(verticalResult.getLineHeight());
        builder.setLyricBaselineY(verticalResult.getLyricsBaselineY());

        return builder.build();
    }

    /**
     * Returns the graphics context used by this engine.
     */
    public @NotNull Graphics2D getGraphics() {
        return g2;
    }

    /**
     * Returns the lyrics font used by this engine.
     */
    public @NotNull Font getLyricsFont() {
        return lyricsFont;
    }

    /**
     * Returns the staff right margin used by this engine.
     */
    public double getStaffRightMargin() {
        return staffRightMargin;
    }
}
