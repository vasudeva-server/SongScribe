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

package songscribe.ui.renderer;

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import module java.desktop;

import java.util.stream.IntStream;


import songscribe.music.ElementType;
import songscribe.music.EndingInterval;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.ScaleContext;

/**
 * Renders first and second ending brackets.
 * <p>
 * Endings are bracket lines that indicate which measures to play during
 * different iterations of a repeat. First ending is played the first time,
 * second ending is played on the repeat.
 */
public class EndingRenderer extends BaseElementRenderer<LineElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final EngravingDefaults ENGRAVING_DEFAULTS =
        SMuFLMetadata.getInstance().getEngravingDefaults();

    private static final BasicStroke STEM_STROKE = new BasicStroke(
        (float) ScaleContext.getInstance().toPixels(ENGRAVING_DEFAULTS.repeatEndingLineThickness()),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Bar line positioning constants (from FughettaRenderer)
    private static final float NOTE_FONT_SIZE = BaseElementRenderer.FONT_SIZE;
    private static final double BAR_LINE_X1_PX = NOTE_FONT_SIZE / 11.636364d;
    private static final double BAR_LINE_SPACE_PX = NOTE_FONT_SIZE / 5.8181818d;
    private static final double REPEAT_RIGHT_THICK_X_PX = NOTE_FONT_SIZE / 11.636364d;
    private static final double REPEAT_THICK_THIN_DIFF_PX = NOTE_FONT_SIZE / 11.636364d * 2;

    // Singleton instance
    private static final EndingRenderer INSTANCE = new EndingRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private EndingRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static EndingRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        LineElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        // EndingRenderer is called directly with Line, not through element interface
        // This method is a placeholder for the interface requirement
    }

    /**
     * Renders all first/second endings for a line.
     *
     * @param g2        Graphics context
     * @param line      The line
     * @param lineIndex Line index
     * @param ctx       Render context
     */
    public void renderEndings(
        Graphics2D g2,
        Line line,
        int lineIndex,
        ElementRenderContext ctx
    ) {
        for (var iter = line.getFirstSecondEndings().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();

            int start = interval.getStart();
            int end = interval.getEnd();

            // Find repeat right position within the interval
            int repeatRightPos = IntStream.rangeClosed(start, end)
                .filter(i -> line.getElement(i).getType() == ElementType.REPEAT_RIGHT)
                .findFirst()
                .orElse(-1);

            double repeatX = 0d;
            var startNote = line.getElement(start);

            // Adjust start if previous note is a bar line
            if (start > 0) {
                var previousNote = line.getElement(start - 1);

                if (previousNote.getType() == ElementType.SINGLE_BARLINE) {
                    --start;
                    startNote = previousNote;
                }
            }

            var endNote = line.getElement(end);

            // Render first ending (before repeat or entire interval if no repeat)
            if ((start < repeatRightPos) || (repeatRightPos == -1)) {
                double x2;

                if (repeatRightPos != -1) {
                    // Right edge aligns with thin line of repeat
                    repeatX = line.getElement(repeatRightPos).getXPosSs() + REPEAT_RIGHT_THICK_X_PX;
                    x2 = repeatX - REPEAT_THICK_THIN_DIFF_PX;
                } else {
                    // Go halfway to next note
                    double nextX = line.getElement(end + 1).getXPosSs();
                    x2 = endNote.getXPosSs();
                    x2 += (nextX - x2) / 2d;
                }

                double x1 = startNote.getXPosSs();

                // Align with bar line if starting on one
                if (startNote.getType() == ElementType.SINGLE_BARLINE) {
                    x1 += BAR_LINE_X1_PX;
                } else if (start > 0) {
                    // Otherwise go halfway to previous note's right edge
                    var previousNote = line.getElement(start - 1);
                    double previousX = previousNote.getXPosSs() + previousNote.getContentWidthPx();
                    x1 -= (x1 - previousX) / 2d;
                }

                drawEnding(g2, line, lineIndex, ctx, interval, x1, x2, 1, startNote, endNote);
            }

            // Render second ending (after repeat)
            if ((repeatRightPos != -1) && (end > repeatRightPos)) {
                double x2 = endNote.getXPosSs();
                var type = endNote.getType();

                // Extend to next bar line if present
                if (type != ElementType.SINGLE_BARLINE &&
                    type != ElementType.DOUBLE_BARLINE &&
                    (end + 1) < line.elementCount()) {

                    var nextElement = line.getElement(end + 1);
                    var nextType = nextElement.getType();

                    if (nextType == ElementType.SINGLE_BARLINE ||
                        nextType == ElementType.DOUBLE_BARLINE) {
                        ++end;
                        type = nextType;
                        x2 = nextElement.getXPosSs();
                    }
                }

                // Align right edge with bar line
                if (type == ElementType.SINGLE_BARLINE || type == ElementType.DOUBLE_BARLINE) {
                    x2 += BAR_LINE_X1_PX;

                    if (type == ElementType.DOUBLE_BARLINE) {
                        x2 -= BAR_LINE_SPACE_PX;
                    }
                } else {
                    // Go halfway to next element or an element width beyond
                    var nextElement = line.getElement(end + 1);
                    x2 += nextElement.getContentWidthPx();

                    if (end < line.elementCount()) {
                        x2 += (nextElement.getXPosSs() - x2) / 2d;
                    } else {
                        x2 += nextElement.getContentWidthPx();
                    }
                }

                var repeatNote = line.getElement(repeatRightPos);
                drawEnding(g2, line, lineIndex, ctx, interval, repeatX + REPEAT_THICK_THIN_DIFF_PX, x2, 2, repeatNote, endNote);
            }
        }
    }

    /**
     * Draws a single ending bracket.
     *
     * @param g2        Graphics context
     * @param line      The line
     * @param lineIndex Line index
     * @param ctx       Render context
     * @param x1        Left X coordinate
     * @param x2        Right X coordinate
     * @param number    Ending number (1 or 2)
     * @param startNote The first note of the ending
     * @param endNote   The last note of the ending
     */
    private void drawEnding(
        Graphics2D g2,
        Line line,
        int lineIndex,
        ElementRenderContext ctx,
        EndingInterval interval,
        double x1,
        double x2,
        int number,
        StaffElement startNote,
        StaffElement endNote
    ) {
        int y = getEffectiveEndingYPosPx(ctx, interval, startNote);
        int fontHeight = BaseElementRenderer.ENDING_FONT.getSize() + 2;

        // Build bracket path
        var bracket = new Path2D.Double();
        bracket.moveTo(x1, y);
        bracket.lineTo(x1, y - fontHeight);
        bracket.lineTo(x2, y - fontHeight);

        // First ending has right leg, second ending doesn't
        if (number == 1) {
            bracket.lineTo(x2, y);
        }

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE, FONT)) {
            g2.setStroke(STEM_STROKE);
            g2.setColor(ELEMENT_COLOR);
            g2.draw(bracket);

            // Draw ending number
            g2.setFont(BaseElementRenderer.ENDING_FONT);
            g2.drawString(Integer.toString(number), (float) x1 + 4, y - 3);
        }
    }

    /**
     * Gets the Y position for an ending bracket from layout result.
     * <p>
     * Tries SpanLayout (keyed by EndingInterval) first, then falls back to
     * DecorationLayout (for Ending range elements), then to legacy bounds.
     */
    private int getEffectiveEndingYPosPx(
        ElementRenderContext ctx,
        EndingInterval interval,
        StaffElement startNote
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        // Try SpanLayout keyed by the legacy interval
        var spanLayout = layoutResult.getSpanLayout(interval);

        if (spanLayout != null) {
            return (int) layoutYToComponentYSs(spanLayout.ySs(), ctx);
        }

        // Try DecorationLayout for Ending range elements
        var decorationLayout = layoutResult.findRangeElementDecorationLayout(
            startNote, Ending.class);

        if (decorationLayout != null) {
            return (int) layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        }

        throw new IllegalStateException("No layout found for Ending element");
    }
}
