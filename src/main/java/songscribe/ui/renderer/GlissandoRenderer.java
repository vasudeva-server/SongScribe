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

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Composition;
import songscribe.music.GraceSemiQuaver;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;

/**
 * Renders glissando (wavy ornament lines) connecting notes.
 * <p>
 * A glissando is a decorative wavy line that connects a note to a target pitch,
 * typically indicating a slide or glide between pitches. The line is drawn using
 * the Fughetta font's glissando glyph, repeated and scaled to span the distance.
 * <p>
 * Glissando data is stored on the source note via {@link Note#getGlissando()}.
 */
public class GlissandoRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Glissando glyph from Fughetta font. */
    private static final String GLISSANDO_GLYPH = "\uf07e";

    /** Base length of one glissando segment. */
    private static final double GLISSANDO_LENGTH = BaseElementRenderer.NOTE_FONT_SIZE / 2.6666667;

    /** Minimum number of glissando segments to draw. */
    private static final int MIN_SEGMENTS = 2;

    /** Horizontal offset from note position to glissando start. */
    private static final int GLISSANDO_START_OFFSET = 15;

    /** Horizontal gap before the next note. */
    private static final int GLISSANDO_END_GAP = 3;

    /** Additional offset for semibreve notes. */
    private static final int SEMIBREVE_OFFSET = 3;

    /** Additional offset for grace notes. */
    private static final int GRACE_NOTE_OFFSET = -3;

    /** X offset per dot on the note. */
    private static final int DOT_OFFSET = 6;

    /** Fallback offset when glissando is at end of line. */
    private static final int END_OF_LINE_OFFSET = 45;

    /** Accidental gap factor. */
    private static final float ACCIDENTAL_GAP = 1.6f;

    /** Y translation for glissando glyph rendering. */
    private static final double GLISSANDO_Y_OFFSET = 2.25;

    // ==========================================================================
    // Singleton
    // ==========================================================================

    private static final GlissandoRenderer INSTANCE = new GlissandoRenderer();

    private GlissandoRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull GlissandoRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Public Position Methods (for HorizontalAdjustment)
    // ==========================================================================

    /**
     * Calculates the starting X position for a glissando (public static version).
     * <p>
     * This method is used by HorizontalAdjustment to position UI handles.
     *
     * @param xIndex      Index of the source note in the line
     * @param glissando   The glissando data
     * @param lineIndex   Index of the line in the composition
     * @param composition The composition containing the line
     * @return X coordinate for glissando start
     */
    public static int getGlissandoX1Pos(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition
    ) {
        var line = composition.getLine(lineIndex);
        var note = line.getNote(xIndex);
        return INSTANCE.getGlissandoX1Pos(note, glissando);
    }

    /**
     * Calculates the ending X position for a glissando (public static version).
     * <p>
     * This method is used by HorizontalAdjustment to position UI handles.
     *
     * @param xIndex      Index of the source note in the line
     * @param glissando   The glissando data
     * @param lineIndex   Index of the line in the composition
     * @param composition The composition containing the line
     * @return X coordinate for glissando end
     */
    public static int getGlissandoX2Pos(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition
    ) {
        var line = composition.getLine(lineIndex);
        return INSTANCE.getGlissandoX2Pos(line, xIndex, glissando);
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders glissandos for all notes in a line.
     *
     * @param g2   Graphics context
     * @param line The line containing notes
     * @param ctx  Render context
     */
    public void renderGlissandosFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            //noinspection ObjectEquality
            if (note.getGlissando() != Note.NO_GLISSANDO) {
                renderGlissando(g2, line, note, i, ctx);
            }
        }
    }

    /**
     * Renders a glissando for a specific note.
     * <p>
     * This is the public entry point for rendering a single glissando,
     * used by both line-level rendering and edit mode preview.
     *
     * @param g2        Graphics context
     * @param line      The line containing the note
     * @param note      The note with the glissando
     * @param noteIndex Index of the note in the line
     * @param ctx       Render context
     */
    public void renderGlissando(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull Note note,
        int noteIndex,
        @NotNull ElementRenderContext ctx
    ) {
        var glissando = note.getGlissando();

        //noinspection ObjectEquality
        if (glissando == Note.NO_GLISSANDO) {
            return;
        }

        var x1 = getGlissandoX1Pos(note, glissando);
        var x2 = getGlissandoX2Pos(line, noteIndex, glissando);
        var y1 = noteYPosToCoordinate(note.getYPos(), ctx.getMiddleLineY());
        var y2 = noteYPosToCoordinate(glissando.pitch, ctx.getMiddleLineY());

        renderGlissandoLine(g2, x1, y1, x2, y2, ctx);
    }

    /**
     * Renders a glissando for edit mode preview.
     * <p>
     * This method is called when the user is placing a glissando note
     * and needs to see a preview of the glissando line.
     *
     * @param g2        Graphics context
     * @param xIndex    Index of the note to attach glissando to
     * @param glissando The glissando data
     * @param line      The line containing the note
     * @param ctx       Render context
     */
    public void renderEditGlissando(
        @NotNull Graphics2D g2,
        int xIndex,
        @NotNull Note.Glissando glissando,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        if (xIndex < 0 || xIndex >= line.noteCount()) {
            return;
        }

        var note = line.getNote(xIndex);
        var x1 = getGlissandoX1Pos(note, glissando);
        var x2 = getGlissandoX2Pos(line, xIndex, glissando);
        var y1 = noteYPosToCoordinate(note.getYPos(), ctx.getMiddleLineY());
        var y2 = noteYPosToCoordinate(glissando.pitch, ctx.getMiddleLineY());

        renderGlissandoLine(g2, x1, y1, x2, y2, ctx);
    }

    // ==========================================================================
    // Position Calculation
    // ==========================================================================

    /**
     * Calculates the starting X position for a glissando.
     *
     * @param note      The source note
     * @param glissando The glissando data
     * @return X coordinate for glissando start
     */
    private int getGlissandoX1Pos(
        @NotNull Note note,
        @NotNull Note.Glissando glissando
    ) {
        var x1 = note.getXPos() + GLISSANDO_START_OFFSET + glissando.x1Translate;
        var noteType = note.getNoteType();

        if (noteType == NoteType.SEMIBREVE) {
            x1 += SEMIBREVE_OFFSET;
        } else if (noteType.isGraceNote()) {
            x1 += GRACE_NOTE_OFFSET;

            if (noteType == NoteType.GRACE_SEMIQUAVER) {
                x1 += ((GraceSemiQuaver) note).getX2DiffPos();
            }
        }

        x1 += note.getDotCount() * DOT_OFFSET;
        return x1;
    }

    /**
     * Calculates the ending X position for a glissando.
     *
     * @param line      The line containing the notes
     * @param noteIndex Index of the source note
     * @param glissando The glissando data
     * @return X coordinate for glissando end
     */
    private int getGlissandoX2Pos(
        @NotNull Line line,
        int noteIndex,
        @NotNull Note.Glissando glissando
    ) {
        float x2 = -glissando.x2Translate;

        if ((noteIndex + 1) < line.noteCount()) {
            var nextNote = line.getNote(noteIndex + 1);
            x2 += nextNote.getXPos() - GLISSANDO_END_GAP;

            var accNum = nextNote.getAccidental().ordinal();

            if (accNum > 0) {
                x2 -= NoteRenderer.getAccidentalWidth(nextNote);
                x2 -= ACCIDENTAL_GAP;
            }
        } else {
            // At end of line, use fixed offset from current note
            x2 += line.getNote(noteIndex).getXPos() + END_OF_LINE_OFFSET;
        }

        return Math.round(x2);
    }

    // ==========================================================================
    // Glyph Rendering
    // ==========================================================================

    /**
     * Renders the actual glissando wavy line between two points.
     *
     * @param g2  Graphics context
     * @param x1  Start X coordinate
     * @param y1  Start Y coordinate
     * @param x2  End X coordinate
     * @param y2  End Y coordinate
     * @param ctx Render context
     */
    private void renderGlissandoLine(
        @NotNull Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2,
        @NotNull ElementRenderContext ctx
    ) {
        // Calculate total length
        var dx = Math.abs(x2 - x1);
        var dy = Math.abs(y2 - y1);
        var length = Math.sqrt((double) dx * dx + (double) dy * dy);

        // Calculate number of segments (minimum 2)
        var segments = Math.max(MIN_SEGMENTS, (int) Math.round(length / GLISSANDO_LENGTH));

        // Save transform and set up for rotated drawing
        var savedTransform = g2.getTransform();
        g2.setFont(ctx.getFughettaFont());

        // Translate to start position
        g2.translate(x1, y1 + GLISSANDO_Y_OFFSET);

        // Rotate to angle of glissando line
        var angle = Math.atan((double) (y2 - y1) / (double) (x2 - x1));
        g2.rotate(angle);

        // Scale horizontally to fit the line
        var scale = length / GLISSANDO_LENGTH / segments;
        g2.scale(scale, 1d);

        // Draw glissando segments
        for (var i = 0; i < segments; i++) {
            g2.drawString(GLISSANDO_GLYPH, (int) Math.round(i * GLISSANDO_LENGTH), 0);
        }

        // Restore transform
        g2.setTransform(savedTransform);
    }

    /**
     * Calculates the Y coordinate for a given pitch position.
     *
     * @param yPos        The note's y-position relative to middle line
     * @param middleLineY Y position of middle staff line
     * @return Y coordinate
     */
    private int noteYPosToCoordinate(int yPos, int middleLineY) {
        return middleLineY + (int) (yPos * (BaseElementRenderer.NOTE_FONT_SIZE / 8));
    }
}
