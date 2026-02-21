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

import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import java.awt.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.StaffSpaces;

/**
 * Renders glissando (wavy ornament lines) connecting notes.
 * <p>
 * A glissando is a decorative wavy line that connects a note to a target pitch,
 * typically indicating a slide or glide between pitches. The line is drawn using
 * the Bravura font's wiggleGlissando glyph, repeated and scaled to span the distance.
 * <p>
 * Glissando data is stored on the source note via {@link Note#getGlissando()}.
 */
public class GlissandoRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Base length of one glissando segment (SMuFL repeatOffset: 0.96 ss). */
    private static final double GLISSANDO_LENGTH_PX = StaffSpaces.toPixels(0.96);

    /** Minimum number of glissando segments to draw. */
    private static final int MIN_SEGMENTS = 2;

    /** Horizontal offset from note position to glissando start. */
    private static final int GLISSANDO_START_OFFSET_PX = 15;

    /** Horizontal gap before the next note. */
    private static final int GLISSANDO_END_GAP_PX = 3;

    /** Additional offset for semibreve notes. */
    private static final int SEMIBREVE_OFFSET_PX = 3;

    /** Additional offset for grace notes. */
    private static final int GRACE_NOTE_OFFSET_PX = -3;

    /** X offset per dot on the note. */
    private static final int DOT_OFFSET_PX = 6;

    /** Fallback offset when glissando is at end of line. */
    private static final int END_OF_LINE_OFFSET_PX = 45;

    /** Accidental gap factor. */
    private static final float ACCIDENTAL_GAP_SS = 1.6f;

    /** Y translation for glissando glyph rendering. */
    private static final double GLISSANDO_Y_OFFSET_SS = 2.25;

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
    public static int getGlissandoX1PosPx(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition
    ) {
        var line = composition.getLine(lineIndex);
        var note = line.getNote(xIndex);
        return INSTANCE.getGlissandoX1PosPx(note, glissando);
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
    public static int getGlissandoX2PosPx(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition
    ) {
        var line = composition.getLine(lineIndex);
        return INSTANCE.getGlissandoX2PosPx(line, xIndex, glissando);
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

        var x1 = getGlissandoX1PosPx(note, glissando);
        var x2 = getGlissandoX2PosPx(line, noteIndex, glissando);
        var y1 = noteStaffPositionToCoordinateSs(note.getStaffPosition(), ctx.getMiddleLineYSs());
        var y2 = noteStaffPositionToCoordinateSs(glissando.pitch, ctx.getMiddleLineYSs());

        renderGlissandoLine(g2, x1, (int) y1, x2, (int) y2);
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
        var x1 = getGlissandoX1PosPx(note, glissando);
        var x2 = getGlissandoX2PosPx(line, xIndex, glissando);
        var y1 = noteStaffPositionToCoordinateSs(note.getStaffPosition(), ctx.getMiddleLineYSs());
        var y2 = noteStaffPositionToCoordinateSs(glissando.pitch, ctx.getMiddleLineYSs());

        renderGlissandoLine(g2, x1, (int) y1, x2, (int) y2);
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
    private int getGlissandoX1PosPx(
        @NotNull Note note,
        @NotNull Note.Glissando glissando
    ) {
        var x1 = note.getXPosSs() + GLISSANDO_START_OFFSET_PX + (int) glissando.x1Translate;
        var noteType = note.getNoteType();

        if (noteType == NoteType.SEMIBREVE) {
            x1 += SEMIBREVE_OFFSET_PX;
        } else if (noteType.isGraceNote()) {
            x1 += GRACE_NOTE_OFFSET_PX;
        }

        x1 += note.getDotCount() * DOT_OFFSET_PX;
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
    private int getGlissandoX2PosPx(
        @NotNull Line line,
        int noteIndex,
        @NotNull Note.Glissando glissando
    ) {
        float x2 = (float) -glissando.x2Translate;

        if ((noteIndex + 1) < line.noteCount()) {
            var nextNote = line.getNote(noteIndex + 1);
            x2 += nextNote.getXPosSs() - GLISSANDO_END_GAP_PX;

            var accNum = nextNote.getAccidental().ordinal();

            if (accNum > 0) {
                x2 -= NoteRenderer.getAccidentalWidthSs(nextNote);
                x2 -= ACCIDENTAL_GAP_SS;
            }
        } else {
            // At end of line, use fixed offset from current note
            x2 += line.getNote(noteIndex).getXPosSs() + END_OF_LINE_OFFSET_PX;
        }

        return Math.round(x2);
    }

    // ==========================================================================
    // Glyph Rendering
    // ==========================================================================

    /**
     * Renders the actual glissando wavy line between two points.
     *
     * @param g2 Graphics context
     * @param x1 Start X coordinate
     * @param y1 Start Y coordinate
     * @param x2 End X coordinate
     * @param y2 End Y coordinate
     */
    private void renderGlissandoLine(
        @NotNull Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2
    ) {
        // Calculate total length
        var dx = Math.abs(x2 - x1);
        var dy = Math.abs(y2 - y1);
        var length = Math.sqrt((double) dx * dx + (double) dy * dy);

        // Calculate number of segments (minimum 2)
        var segments = Math.max(MIN_SEGMENTS, (int) Math.round(length / GLISSANDO_LENGTH_PX));

        // Save transform and set up for rotated drawing
        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            g2.setFont(BaseElementRenderer.BRAVURA_FONT);

            // Translate to start position
            g2.translate(x1, y1 + GLISSANDO_Y_OFFSET_SS);

            // Rotate to angle of glissando line
            var angle = Math.atan((double) (y2 - y1) / (double) (x2 - x1));
            g2.rotate(angle);

            // Scale horizontally to fit the line
            var scale = length / GLISSANDO_LENGTH_PX / segments;
            g2.scale(scale, 1d);

            // Draw glissando segments
            var glyphStr = SMuFLGlyph.WIGGLE_GLISSANDO.asString();

            for (var i = 0; i < segments; i++) {
                g2.drawString(glyphStr, (float) (i * GLISSANDO_LENGTH_PX), 0f);
            }
        }
    }

    /**
     * Calculates the Y coordinate for a given pitch position.
     *
     * @param staffPosition The note's staff position relative to middle line
     * @param middleLineYSs Y position of middle staff line in staff spaces
     * @return Y coordinate
     */
    private double noteStaffPositionToCoordinateSs(int staffPosition, double middleLineYSs) {
        return middleLineYSs + staffPosition * (BaseElementRenderer.NOTE_FONT_SIZE / 8);
    }
}
