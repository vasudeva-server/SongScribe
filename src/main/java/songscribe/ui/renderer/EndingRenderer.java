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

import java.awt.*;
import java.awt.geom.*;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.Ending;
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
        (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.repeatEndingLineThickness()),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Bar line positioning constants (from FughettaRenderer)
    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;
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
    public static @NotNull EndingRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull LineElement element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
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
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex,
        @NotNull ElementRenderContext ctx
    ) {
        for (var iter = line.getFirstSecondEndings().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();

            int start = interval.getStart();
            int end = interval.getEnd();

            // Find repeat right position within the interval
            int repeatRightPos = IntStream.rangeClosed(start, end)
                .filter(i -> line.getNote(i).getNoteType() == NoteType.REPEAT_RIGHT)
                .findFirst()
                .orElse(-1);

            double repeatX = 0d;
            var startNote = line.getNote(start);

            // Adjust start if previous note is a bar line
            if (start > 0) {
                var previousNote = line.getNote(start - 1);

                if (previousNote.getNoteType() == NoteType.SINGLE_BARLINE) {
                    --start;
                    startNote = previousNote;
                }
            }

            var endNote = line.getNote(end);

            // Render first ending (before repeat or entire interval if no repeat)
            if ((start < repeatRightPos) || (repeatRightPos == -1)) {
                double x2;

                if (repeatRightPos != -1) {
                    // Right edge aligns with thin line of repeat
                    repeatX = line.getNote(repeatRightPos).getXPosSs() + REPEAT_RIGHT_THICK_X_PX;
                    x2 = repeatX - REPEAT_THICK_THIN_DIFF_PX;
                } else {
                    // Go halfway to next note
                    double nextX = line.getNote(end + 1).getXPosSs();
                    x2 = endNote.getXPosSs();
                    x2 += (nextX - x2) / 2d;
                }

                double x1 = startNote.getXPosSs();

                // Align with bar line if starting on one
                if (startNote.getNoteType() == NoteType.SINGLE_BARLINE) {
                    x1 += BAR_LINE_X1_PX;
                } else if (start > 0) {
                    // Otherwise go halfway to previous note's right edge
                    var previousNote = line.getNote(start - 1);
                    double previousX = previousNote.getXPosSs() + previousNote.getRealUpNoteRect().width;
                    x1 -= (x1 - previousX) / 2d;
                }

                drawEnding(g2, line, lineIndex, ctx, x1, x2, 1, startNote, endNote);
            }

            // Render second ending (after repeat)
            if ((repeatRightPos != -1) && (end > repeatRightPos)) {
                double x2 = endNote.getXPosSs();
                var type = endNote.getNoteType();

                // Extend to next bar line if present
                if (type != NoteType.SINGLE_BARLINE &&
                    type != NoteType.DOUBLE_BARLINE &&
                    (end + 1) < line.noteCount()) {

                    var nextNote = line.getNote(end + 1);
                    var nextType = nextNote.getNoteType();

                    if (nextType == NoteType.SINGLE_BARLINE ||
                        nextType == NoteType.DOUBLE_BARLINE) {
                        ++end;
                        type = nextType;
                        x2 = nextNote.getXPosSs();
                    }
                }

                // Align right edge with bar line
                if (type == NoteType.SINGLE_BARLINE || type == NoteType.DOUBLE_BARLINE) {
                    x2 += BAR_LINE_X1_PX;

                    if (type == NoteType.DOUBLE_BARLINE) {
                        x2 -= BAR_LINE_SPACE_PX;
                    }
                } else {
                    // Go halfway to next note or a note width beyond
                    var nextNote = line.getNote(end + 1);
                    x2 += nextNote.getRealUpNoteRect().width;

                    if (end < line.noteCount()) {
                        x2 += (nextNote.getXPosSs() - x2) / 2d;
                    } else {
                        x2 += nextNote.getRealUpNoteRect().width;
                    }
                }

                var repeatNote = line.getNote(repeatRightPos);
                drawEnding(g2, line, lineIndex, ctx, repeatX + REPEAT_THICK_THIN_DIFF_PX, x2, 2, repeatNote, endNote);
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
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex,
        @NotNull ElementRenderContext ctx,
        double x1,
        double x2,
        int number,
        @NotNull Note startNote,
        @NotNull Note endNote
    ) {
        int y = getEffectiveEndingYPosPx(ctx, startNote, endNote);
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
            g2.setColor(NOTE_COLOR);
            g2.draw(bracket);

            // Draw ending number
            g2.setFont(BaseElementRenderer.ENDING_FONT);
            g2.drawString(Integer.toString(number), (float) x1 + 4, y - 3);
        }
    }

    /**
     * Gets the Y position for an ending bracket from layout result.
     */
    private int getEffectiveEndingYPosPx(
        @NotNull ElementRenderContext ctx,
        @NotNull Note startNote,
        @NotNull Note endNote
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.findRangeElementBounds(startNote, endNote, Ending.class);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for Ending element");
        }

        return (int) bounds.getTop();
    }
}
