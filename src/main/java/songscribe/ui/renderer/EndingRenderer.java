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
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LineElement;

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
        (float) ENGRAVING_DEFAULTS.repeatEndingLineThickness(),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Bar line positioning constants (in staff-space units, derived from font size)
    private static final float NOTE_FONT_SIZE = BaseElementRenderer.FONT_SIZE;
    private static final double BAR_LINE_X1_SS = NOTE_FONT_SIZE / 11.636364d;
    private static final double BAR_LINE_SPACE_SS = NOTE_FONT_SIZE / 5.8181818d;
    private static final double REPEAT_RIGHT_THICK_X_SS = NOTE_FONT_SIZE / 11.636364d;
    private static final double REPEAT_THICK_THIN_DIFF_SS = NOTE_FONT_SIZE / 11.636364d * 2;

    /** Notehead width for computing right edges in staff-space units. */
    private static final double NOTE_HEAD_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();

    // SMuFL Private Use Area glyphs for volta bracket labels
    private static final char VOLTA_DIGIT_ZERO = 0xF5C8;
    private static final char VOLTA_PERIOD = 0xF477;

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
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            return;
        }

        for (var ending : line.findEndings()) {
            int start = ending.getAnchorElementIndex();
            int end = ending.getEndElementIndex();

            // Skip endings with invalid indices (e.g. anchor/end note was deleted)
            if (start < 0 || end < 0 || start >= line.elementCount() || end >= line.elementCount()) {
                continue;
            }

            System.out.println("[EndingRenderer] start=" + start + " end=" + end
                + " elementCount=" + line.elementCount());

            // Find repeat right position within the interval
            int repeatRightPos = IntStream.rangeClosed(start, end)
                .filter(i -> line.getElement(i).getType() == ElementType.REPEAT_RIGHT)
                .findFirst()
                .orElse(-1);

            System.out.println("[EndingRenderer] repeatRightPos=" + repeatRightPos);

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
                    repeatX = layoutResult.getElementXSs(line.getElement(repeatRightPos)) + REPEAT_RIGHT_THICK_X_SS;
                    x2 = repeatX - REPEAT_THICK_THIN_DIFF_SS;
                } else {
                    x2 = layoutResult.getElementXSs(endNote);

                    if ((end + 1) < line.elementCount()) {
                        double nextX = layoutResult.getElementXSs(line.getElement(end + 1));
                        x2 += (nextX - x2) / 2d;
                    } else {
                        x2 += NOTE_HEAD_WIDTH_SS;
                    }
                }

                double x1 = layoutResult.getElementXSs(startNote);

                // Align with bar line's left edge, or go halfway to previous note
                if (startNote.getType() == ElementType.SINGLE_BARLINE) {
                    // x1 is already the barline's layout X — no offset needed
                } else if (start > 0) {
                    // Otherwise go halfway to previous note's right edge
                    var previousNote = line.getElement(start - 1);
                    double previousX = layoutResult.getElementXSs(previousNote) + NOTE_HEAD_WIDTH_SS;
                    x1 -= (x1 - previousX) / 2d;
                }

                drawEnding(g2, line, lineIndex, ctx, ending, x1, x2, 1, startNote, endNote);
            }

            // Render second ending (after repeat)
            if ((repeatRightPos != -1) && (end > repeatRightPos)) {
                double x2 = layoutResult.getElementXSs(endNote);
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
                        x2 = layoutResult.getElementXSs(nextElement);
                    }
                }

                // Align right edge with bar line
                if (type == ElementType.SINGLE_BARLINE || type == ElementType.DOUBLE_BARLINE) {
                    x2 += BAR_LINE_X1_SS;

                    if (type == ElementType.DOUBLE_BARLINE) {
                        x2 -= BAR_LINE_SPACE_SS;
                    }
                } else {
                    // Go halfway to next element, or a notehead width beyond if at end of line
                    if ((end + 1) < line.elementCount()) {
                        var nextElement = line.getElement(end + 1);
                        x2 += (layoutResult.getElementXSs(nextElement) - x2) / 2d;
                    } else {
                        x2 += NOTE_HEAD_WIDTH_SS;
                    }
                }

                var repeatNote = line.getElement(repeatRightPos);
                drawEnding(g2, line, lineIndex, ctx, ending, repeatX + REPEAT_THICK_THIN_DIFF_SS, x2, 2, repeatNote, endNote);
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
        Ending ending,
        double x1,
        double x2,
        int number,
        StaffElement startNote,
        StaffElement endNote
    ) {
        double yTopSs = getEffectiveEndingYSs(ctx, ending);
        double yBottomSs = yTopSs + ending.getContentHeightSs();

        System.out.println("[drawEnding] number=" + number
            + " x1=" + String.format("%.2f", x1)
            + " x2=" + String.format("%.2f", x2)
            + " yTopSs=" + String.format("%.2f", yTopSs)
            + " yBottomSs=" + String.format("%.2f", yBottomSs)
            + " middleLineYSs=" + String.format("%.2f", ctx.getMiddleLineYSs())
            + " contentHeightSs=" + String.format("%.2f", ending.getContentHeightSs()));

        // Build bracket path: left leg up, horizontal top, optional right leg down
        var bracket = new Path2D.Double();
        bracket.moveTo(x1, yBottomSs);      // bottom-left (closest to staff)
        bracket.lineTo(x1, yTopSs);          // top-left
        bracket.lineTo(x2, yTopSs);          // top-right

        // First ending has right leg, second ending is open on the right
        if (number == 1) {
            bracket.lineTo(x2, yBottomSs);  // bottom-right
        }

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE, FONT)) {
            g2.setStroke(STEM_STROKE);
            g2.setColor(ELEMENT_COLOR);
            g2.draw(bracket);

            // Draw ending label (e.g. "1." or "2.") using Bravura volta glyphs
            g2.setFont(BaseElementRenderer.ENDING_FONT);
            g2.drawString(endingLabel(number), (float) (x1 + 0.5), (float) (yBottomSs - 0.375));
        }
    }

    /**
     * Returns the Bravura volta label string for an ending number.
     * <p>
     * Uses SMuFL Private Use Area glyphs: {@link #VOLTA_DIGIT_ZERO} = 0, each
     * successive digit is +2 (1 = U+F5CA, 2 = U+F5CC, etc.), followed by
     * {@link #VOLTA_PERIOD}.
     */
    private static String endingLabel(int number) {
        char digitGlyph = (char) (VOLTA_DIGIT_ZERO + number * 2);
        return new String(new char[]{digitGlyph, VOLTA_PERIOD});
    }

    /**
     * Returns the top Y coordinate for an ending bracket in component staff-space units.
     */
    private double getEffectiveEndingYSs(ElementRenderContext ctx, Ending ending) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var decorationLayout = layoutResult.getDecorationLayout(ending);

        if (decorationLayout != null) {
            System.out.println("[getEffectiveEndingYSs] decorationLayout.ySs=" + String.format("%.2f", decorationLayout.ySs())
                + " decorationLayout.xSs=" + String.format("%.2f", decorationLayout.xSs())
                + " decorationLayout.widthSs=" + String.format("%.2f", decorationLayout.widthSs())
                + " decorationLayout.heightSs=" + String.format("%.2f", decorationLayout.heightSs()));
            return layoutYToComponentYSs(decorationLayout.ySs(), ctx);
        }

        throw new IllegalStateException("No layout found for Ending element");
    }
}
