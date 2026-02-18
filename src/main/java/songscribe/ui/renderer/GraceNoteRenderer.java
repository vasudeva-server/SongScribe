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
import java.awt.Stroke;
import java.awt.geom.Line2D;

import org.jetbrains.annotations.NotNull;

import songscribe.music.GraceSemiQuaver;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Renders grace notes (grace quaver and grace semiquaver).
 * <p>
 * Grace notes are drawn at 60% scale with:
 * <ul>
 *   <li>Grace quaver: single note head, stem, flag, and diagonal slash</li>
 *   <li>Grace semiquaver: two note heads connected by beams with diagonal slash</li>
 * </ul>
 */
public class GraceNoteRenderer extends BaseElementRenderer<Note> {

    private static final GraceNoteRenderer INSTANCE = new GraceNoteRenderer();

    public static GraceNoteRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;

    /** Scale factor for grace notes (60% of normal size). */
    private static final double GRACE_NOTE_SCALE = 0.6;

    /** Filled note head glyph (used for grace quaver/semiquaver). */
    private static final String FILLED_NOTE_HEAD = "\uf0cf";

    /** Main upper flag glyph. */
    private static final String MAIN_UPPER_FLAG = "\uf06a";

    /** Main lower flag glyph. */
    private static final String MAIN_LOWER_FLAG = "\uf04a";

    // Stem geometry (from FughettaRenderer)
    private static final double UPPER_CROTCHET_STEM_X = NOTE_FONT_SIZE / 3.6056337d;
    private static final Line2D.Float UPPER_STEM = new Line2D.Float(
        0f,
        -NOTE_FONT_SIZE / 32f,
        0f,
        -NOTE_FONT_SIZE / 1.1429f
    );
    private static final Line2D.Float LOWER_STEM = new Line2D.Float(
        0f,
        NOTE_FONT_SIZE / 60f,
        0f,
        NOTE_FONT_SIZE / 1.1429f
    );

    // Flag positions
    private static final double UPPER_FLAG_X = NOTE_FONT_SIZE / 3.6834533d;
    private static final double UPPER_FLAG_Y = -NOTE_FONT_SIZE / 1.6623377d;
    private static final double LOWER_FLAG_Y = NOTE_FONT_SIZE / 1.6d;

    // Grace note slash geometry (diagonal line through stem)
    private static final Line2D.Float GRACE_NOTE_UPPER_SLASH = new Line2D.Float(
        NOTE_FONT_SIZE / 18.285715f,
        -NOTE_FONT_SIZE / 5.5652175f,
        NOTE_FONT_SIZE / 2.3703704f,
        -NOTE_FONT_SIZE / 2.6666667f
    );
    private static final Line2D.Float GRACE_NOTE_LOWER_SLASH = new Line2D.Float(
        -NOTE_FONT_SIZE / 11.906977f,
        NOTE_FONT_SIZE / 5.5652175f,
        NOTE_FONT_SIZE / 3.5310345f,
        NOTE_FONT_SIZE / 2.6666667f
    );
    private static final BasicStroke GRACE_NOTE_SLASH_STROKE = new BasicStroke(
        0.64f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Strokes
    private static final Stroke STEM_STROKE = new BasicStroke(1.0f);

    // Grace semiquaver strokes (from Renderer.java)
    private static final BasicStroke GRACE_SEMIQUAVER_STEM_STROKE =
        new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    private static final BasicStroke GRACE_SEMIQUAVER_BEAM_STROKE =
        new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
    private static final BasicStroke GRACE_SEMIQUAVER_STRIKE_STROKE =
        new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);

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

        if (noteType == NoteType.GRACE_QUAVER) {
            renderGraceQuaver(g2, element, ctx.getMiddleLineY());
        } else if (noteType == NoteType.GRACE_SEMIQUAVER) {
            renderGraceSemiQuaver(g2, element, ctx.getMiddleLineY());
        }
    }

    /**
     * Renders a grace note with simple method signature for backward compatibility.
     *
     * @param g2          Graphics context
     * @param note        The grace note to render
     * @param middleLineY Y position of middle staff line
     */
    public void render(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int middleLineY
    ) {
        var noteType = note.getNoteType();

        if (noteType == NoteType.GRACE_QUAVER) {
            renderGraceQuaver(g2, note, middleLineY);
        } else if (noteType == NoteType.GRACE_SEMIQUAVER) {
            renderGraceSemiQuaver(g2, note, middleLineY);
        }
    }

    /**
     * Renders a grace quaver (single grace note with slash through stem).
     */
    private void renderGraceQuaver(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int middleLineY
    ) {
        var origTransform = g2.getTransform();
        var noteX = note.getXPos();
        var noteY = noteYPosToCoordinate(note.getYPos(), middleLineY);

        g2.translate(noteX, noteY);
        var translatedTransform = g2.getTransform();

        // Scale for grace note
        g2.scale(GRACE_NOTE_SCALE, GRACE_NOTE_SCALE);
        g2.setFont(FUGHETTA);
        g2.setColor(NOTE_COLOR);

        // Draw note head
        g2.drawString(FILLED_NOTE_HEAD, 0, 0);

        // Draw stem and flag
        g2.setStroke(STEM_STROKE);

        if (note.isUpper()) {
            // Upper stem
            g2.translate(UPPER_CROTCHET_STEM_X, 0);
            g2.draw(UPPER_STEM);
            g2.translate(-UPPER_CROTCHET_STEM_X, 0);

            // Upper flag
            g2.drawString(MAIN_UPPER_FLAG, (float) UPPER_FLAG_X, (float) UPPER_FLAG_Y);

            // Reset scale and draw slash
            g2.setTransform(translatedTransform);
            g2.setStroke(GRACE_NOTE_SLASH_STROKE);
            g2.draw(GRACE_NOTE_UPPER_SLASH);
        } else {
            // Lower stem
            g2.draw(LOWER_STEM);

            // Lower flag
            g2.drawString(MAIN_LOWER_FLAG, 0f, (float) LOWER_FLAG_Y);

            // Reset scale and draw slash
            g2.setTransform(translatedTransform);
            g2.setStroke(GRACE_NOTE_SLASH_STROKE);
            g2.draw(GRACE_NOTE_LOWER_SLASH);
        }

        // Render ledger lines and accidentals
        g2.setTransform(translatedTransform);
        renderLedgerLines(g2, note);
        renderAccidental(g2, note);

        g2.setTransform(origTransform);
    }

    /**
     * Renders a grace semiquaver (two grace notes beamed together with slash).
     */
    private void renderGraceSemiQuaver(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int middleLineY
    ) {
        var graceSemiQuaver = (GraceSemiQuaver) note;
        var origTransform = g2.getTransform();
        var noteX = note.getXPos();
        var noteY = noteYPosToCoordinate(note.getYPos(), middleLineY);

        g2.translate(noteX, noteY);
        var translatedTransform = g2.getTransform();

        // Scale and draw first note head at offset position
        g2.scale(GRACE_NOTE_SCALE, GRACE_NOTE_SCALE);
        g2.setFont(FUGHETTA);
        g2.setColor(NOTE_COLOR);

        // First note head (at y0Pos offset)
        var y0Offset = (graceSemiQuaver.getY0Pos() - graceSemiQuaver.getYPos()) *
            LayoutStylesheet.NOTE_Y_OFFSET / GRACE_NOTE_SCALE;
        g2.drawString(FILLED_NOTE_HEAD, 0, (float) y0Offset);

        // Second note head (at x2DiffPos offset)
        var x2Offset = graceSemiQuaver.getX2DiffPos() / GRACE_NOTE_SCALE;
        g2.drawString(FILLED_NOTE_HEAD, (float) x2Offset, 0);

        // Reset transform and draw beams
        g2.setTransform(origTransform);
        g2.translate(note.isUpper() ? -2.7 : -1.7, 0);
        drawGraceSemiQuaverBeam(g2, note, middleLineY);

        g2.setTransform(origTransform);
    }

    /**
     * Draws the stems, beams, and slash for a grace semiquaver.
     * Adapted from Renderer.drawGraceSemiQuaverBeam().
     */
    private void drawGraceSemiQuaverBeam(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int middleLineY
    ) {
        var graceSemiQuaver = (GraceSemiQuaver) note;
        var dir = note.isUpper() ? -1 : 1;
        var x1 = note.getXPos() + (note.isUpper() ? 8 : 2);
        var x2 = x1 + graceSemiQuaver.getX2DiffPos();
        var y1Pos = graceSemiQuaver.getY0Pos();
        var y2Pos = note.getYPos();

        // Draw stems
        g2.setStroke(GRACE_SEMIQUAVER_STEM_STROKE);

        var yHead1 = noteYPosToCoordinate(y1Pos, middleLineY);
        var lengthening1 = Math.max((dir * (y2Pos - y1Pos)) - 2, 0);
        var yUpper1 = noteYPosToCoordinate(y1Pos + (dir * (4 + lengthening1)), middleLineY);
        g2.drawLine(x1, yHead1, x1, yUpper1 + dir);

        var lengthening2 = Math.max((dir * (y1Pos - y2Pos)) - 2, 0);
        var yUpper2 = noteYPosToCoordinate(y2Pos + (dir * (4 + lengthening2)), middleLineY);
        var yHead2 = noteYPosToCoordinate(y2Pos, middleLineY);
        g2.drawLine(x2, yHead2, x2, yUpper2 + dir);

        // Draw beams
        g2.setStroke(GRACE_SEMIQUAVER_BEAM_STROKE);
        var oldClip = g2.getClip();
        g2.setClip(x1, 0, x2 - x1, Integer.MAX_VALUE);
        g2.drawLine(x1, yUpper1, x2, yUpper2);
        yUpper1 -= dir * 3;
        yUpper2 -= dir * 3;
        g2.drawLine(x1, yUpper1, x2, yUpper2);
        g2.setClip(oldClip);

        // Draw grace strike (diagonal slash)
        g2.setStroke(GRACE_SEMIQUAVER_STRIKE_STROKE);
        yUpper1 += (int) ((-3 * dir * LayoutStylesheet.NOTE_Y_OFFSET) + (dir * 5));
        yUpper2 += (int) ((dir * LayoutStylesheet.NOTE_Y_OFFSET) + (dir * 4));
        g2.drawLine(x1 - 5, yUpper1, x2 - 3, yUpper2);

        // Draw ledger lines
        g2.setStroke(LEDGER_LINE_STROKE);
        drawStaveLongitude(g2, y1Pos, middleLineY, x1 - 8, x1 + 3);
        drawStaveLongitude(g2, y2Pos, middleLineY, x2 - 8, x2 + 3);
    }

    /**
     * Draws ledger lines for notes above or below the staff.
     * Adapted from Renderer.drawStaveLongitude().
     */
    private void drawStaveLongitude(
        @NotNull Graphics2D g2,
        int yPos,
        int middleLineY,
        int x1Pos,
        int x2Pos
    ) {
        if (Math.abs(yPos) > 5) {
            var i = yPos;

            if ((yPos % 2) != 0) {
                i += (yPos > 0) ? -1 : 1;
            }

            for (var step = (yPos > 0) ? -2 : 2; Math.abs(i) > 5; i += step) {
                var y = noteYPosToCoordinate(i, middleLineY);
                g2.drawLine(x1Pos, y, x2Pos, y);
            }
        }
    }

    // ==========================================================================
    // Ledger Lines (for grace quaver)
    // ==========================================================================

    /**
     * Renders ledger lines for notes outside the staff.
     */
    private void renderLedgerLines(@NotNull Graphics2D g2, @NotNull Note note) {
        var yPos = note.getYPos();

        if (Math.abs(yPos) <= 5) {
            return;
        }

        g2.setStroke(LEDGER_LINE_STROKE);
        g2.setColor(NOTE_COLOR);

        var ledgerWidth = NOTE_FONT_SIZE * GRACE_NOTE_SCALE / 1.5;
        var startY = (yPos > 0) ? 6 : -6;
        var step = (yPos > 0) ? 2 : -2;

        for (var i = startY; (yPos > 0) ? i <= yPos : i >= yPos; i += step) {
            var ledgerY = i * LayoutStylesheet.NOTE_Y_OFFSET;
            drawLedgerLine(g2, 0, ledgerY, ledgerWidth);
        }
    }

    // ==========================================================================
    // Accidentals
    // ==========================================================================

    /**
     * Renders accidental for the grace note.
     */
    private void renderAccidental(
        @NotNull Graphics2D g2,
        @NotNull Note note
    ) {
        var accidental = note.getAccidental();

        if (accidental == null || accidental.ordinal() == 0) {
            return;
        }

        // Use grace-sized font for accidentals
        g2.setFont(FUGHETTA_GRACE);
        g2.setColor(NOTE_COLOR);

        var accidentalStr = getAccidentalString(accidental.ordinal());
        var xOffset = -NOTE_FONT_SIZE * GRACE_NOTE_SCALE / 2.5f;

        if (note.isAccidentalInParentheses()) {
            accidentalStr = BEGIN_PARENTHESIS + accidentalStr + END_PARENTHESIS;
            xOffset -= NOTE_FONT_SIZE * GRACE_NOTE_SCALE / 4f;
        }

        g2.drawString(accidentalStr, (float) xOffset, 0f);
    }

    /**
     * Returns the Fughetta glyph string for an accidental.
     */
    private String getAccidentalString(int accidentalOrdinal) {
        return switch (accidentalOrdinal) {
            case 1 -> NATURAL;
            case 2 -> FLAT;
            case 3 -> SHARP;
            case 4 -> DOUBLE_NATURAL;
            case 5 -> DOUBLE_FLAT;
            case 6 -> DOUBLE_SHARP;
            case 7 -> NATURAL_FLAT;
            case 8 -> NATURAL_SHARP;
            default -> "";
        };
    }
}
