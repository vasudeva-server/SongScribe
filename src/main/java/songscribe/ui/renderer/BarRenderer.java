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

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Renders bar lines and repeat signs.
 * <p>
 * Handles:
 * <ul>
 *   <li>Single bar lines</li>
 *   <li>Double bar lines</li>
 *   <li>Final double bar lines (with thick line)</li>
 *   <li>Left repeat</li>
 *   <li>Right repeat</li>
 *   <li>Left-right repeat</li>
 * </ul>
 */
public class BarRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants from Renderer
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;

    // Bar line strokes
    private static final BasicStroke HEAVY_LINE_STROKE = new BasicStroke(
        4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );
    private static final BasicStroke THIN_LINE_STROKE = new BasicStroke(
        1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );
    private static final BasicStroke REPEAT_HEAVY_STROKE = new BasicStroke(
        4.167f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL
    );
    private static final BasicStroke REPEAT_THIN_STROKE = new BasicStroke(
        1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL
    );
    private static final BasicStroke LINE_STROKE = new BasicStroke(
        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );

    // Spacing
    private static final double BAR_LINE_SPACE = 4.167d;
    private static final double REPEAT_THIN_CIRCLE_DIFF = NOTE_FONT_SIZE / 6.918919d;
    private static final double REPEAT_THICK_THIN_DIFF = NOTE_FONT_SIZE / 6.095238d;

    // Repeat dots
    private static final Ellipse2D.Float REPEAT_CIRCLE_1 = new Ellipse2D.Float(
        0f,
        (NOTE_FONT_SIZE / 2f) - (NOTE_FONT_SIZE / 2.3703704f),
        NOTE_FONT_SIZE / 8f,
        NOTE_FONT_SIZE / 8f
    );
    private static final Ellipse2D.Float REPEAT_CIRCLE_2 = new Ellipse2D.Float(
        0f,
        (NOTE_FONT_SIZE / 2f) - (NOTE_FONT_SIZE / 1.4545455f),
        NOTE_FONT_SIZE / 8f,
        NOTE_FONT_SIZE / 8f
    );

    // Lines
    private static final Line2D.Float VERTICAL_LINE = new Line2D.Float(
        0, -NOTE_FONT_SIZE / 2f, 0, NOTE_FONT_SIZE / 2f
    );
    private static final Line2D.Float BAR_LINE = new Line2D.Float(
        Note.NORMAL_IMAGE_WIDTH, -NOTE_FONT_SIZE / 2f,
        Note.NORMAL_IMAGE_WIDTH, NOTE_FONT_SIZE / 2f
    );

    // Repeat positions
    private static final double REPEAT_LEFT_THICK_X = 4.167d / 2d;
    private static final double REPEAT_RIGHT_THICK_X = Note.NORMAL_IMAGE_WIDTH - REPEAT_LEFT_THICK_X;
    private static final double REPEAT_LEFT_RIGHT_THICK_X = Note.NORMAL_IMAGE_WIDTH / 2d;

    // Singleton instance
    private static final BarRenderer INSTANCE = new BarRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private BarRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull BarRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Note element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var noteType = element.getNoteType();

        if (!noteType.isBarLine() && !noteType.isRepeat()) {
            return;
        }

        var transform = g2.getTransform();

        // Get position - bar lines are centered vertically on the staff
        int noteX = element.getXPos();
        int noteY = ctx.getMiddleLineY();

        g2.translate(noteX, noteY);
        g2.setColor(NOTE_COLOR);

        renderBarLineOrRepeat(g2, noteType);

        g2.setTransform(transform);
    }

    /**
     * Renders a bar line or repeat sign (static helper for delegation).
     */
    public static void renderBarLineOrRepeat(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
        switch (noteType) {
            case SINGLE_BARLINE, DOUBLE_BARLINE, FINAL_DOUBLE_BARLINE -> drawBarLine(g2, noteType);
            case REPEAT_LEFT -> drawRepeat(g2, REPEAT_LEFT_THICK_X, 1d, true);
            case REPEAT_RIGHT -> drawRepeat(g2, REPEAT_RIGHT_THICK_X, -1d, true);
            case REPEAT_LEFT_RIGHT -> {
                drawRepeat(g2, REPEAT_LEFT_RIGHT_THICK_X, 1d, true);
                drawRepeat(g2, REPEAT_LEFT_RIGHT_THICK_X, -1d, false);
            }
            default -> {
                // Not a bar line type
            }
        }
    }

    /**
     * Draws a bar line (single, double, or final double).
     */
    private static void drawBarLine(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
        var transform = g2.getTransform();

        if (noteType == NoteType.DOUBLE_BARLINE) {
            g2.setStroke(THIN_LINE_STROKE);
            g2.draw(BAR_LINE);
            g2.translate(-BAR_LINE_SPACE - THIN_LINE_STROKE.getLineWidth(), 0);
        } else if (noteType == NoteType.FINAL_DOUBLE_BARLINE) {
            g2.setStroke(HEAVY_LINE_STROKE);
            g2.translate(-HEAVY_LINE_STROKE.getLineWidth() / 2f, 0);
            g2.draw(BAR_LINE);
            g2.translate(
                -BAR_LINE_SPACE -
                    (HEAVY_LINE_STROKE.getLineWidth() / 2f) -
                    (THIN_LINE_STROKE.getLineWidth() / 2f),
                0
            );
        }

        g2.setStroke(THIN_LINE_STROKE);
        g2.draw(BAR_LINE);

        g2.setTransform(transform);
    }

    /**
     * Draws a repeat sign.
     *
     * @param g2         Graphics context
     * @param thickStart X position of the thick line
     * @param direction  1 for left repeat, -1 for right repeat
     * @param drawThick  Whether to draw the thick line
     */
    private static void drawRepeat(
        @NotNull Graphics2D g2,
        double thickStart,
        double direction,
        boolean drawThick
    ) {
        var transform = g2.getTransform();
        g2.translate(thickStart, 0);

        // Align repeat bottom with staff line
        double offset = LINE_STROKE.getLineWidth() / 2d;
        var line = new Line2D.Double(
            VERTICAL_LINE.x1, VERTICAL_LINE.y1,
            VERTICAL_LINE.x2, VERTICAL_LINE.y2 + offset
        );

        if (drawThick) {
            g2.setStroke(REPEAT_HEAVY_STROKE);
            g2.draw(line);
        }

        g2.setStroke(REPEAT_THIN_STROKE);
        g2.translate(REPEAT_THICK_THIN_DIFF * direction, 0);
        g2.draw(line);

        g2.translate(
            (REPEAT_THIN_CIRCLE_DIFF * direction) - (REPEAT_CIRCLE_1.width / 2),
            0
        );
        g2.fill(REPEAT_CIRCLE_1);
        g2.fill(REPEAT_CIRCLE_2);

        g2.setTransform(transform);
    }
}
