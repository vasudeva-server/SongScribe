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
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout2.LayoutConstants;
import songscribe.ui.layout2.LayoutResult;

/**
 * Renders glissando (wavy ornament lines) connecting notes.
 * <p>
 * A glissando is a decorative wavy line that connects a note to a target pitch,
 * typically indicating a slide or glide between pitches. The line is drawn using
 * the Bravura font's wiggleGlissando glyph, tiled at natural size to span the distance.
 * <p>
 * Glissando data is stored on the source note via {@link Note#getGlissando()}.
 */
public class GlissandoRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Minimum number of glissando segments to draw. */
    private static final int MIN_SEGMENTS = 2;

    /**
     * Minimum gap between a glissando endpoint and the adjacent notehead edge, in staff spaces.
     * Applied after the source notehead's right edge (x1) and before the target notehead's
     * left edge (x2). The notehead edges are derived from bravura_metadata.json bounding boxes.
     */
    private static final double MIN_NOTEHEAD_GAP_SS = 0.375;  // 3px

    /** X offset per dot on the note. */
    private static final double DOT_OFFSET_SS = 0.75;  // 6px

    /** Fallback offset when glissando is at end of line. */
    private static final double END_OF_LINE_OFFSET_SS = 5.625;  // 45px

    /** Y offset for end-of-line glissando preview, in staff positions (4sp = 2ss). */
    private static final int DEFAULT_SLIDE_OUT_Y_OFFSET_SP = 4;

    /** Accidental gap factor. */
    private static final float ACCIDENTAL_GAP_SS = 1.6f;

    /**
     * Y offset applied to the glyph baseline to vertically center the wave on the note position.
     * wiggleTrillFaster: bBoxSW.y=0.380, bBoxNE.y=0.848 → center = 0.614 ss above baseline.
     * Applied in the rotated coordinate system so it stays perpendicular to the line at any angle.
     */
    private static final double GLISSANDO_Y_OFFSET_SS = 0.614;

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
     * @param xIndex       Index of the source note in the line
     * @param glissando    The glissando data
     * @param lineIndex    Index of the line in the composition
     * @param composition  The composition containing the line
     * @param layoutResult Layout result for resolving note positions (may be null)
     * @return X coordinate for glissando start in staff-space units
     */
    public static double getGlissandoX1Ss(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition,
        @Nullable LayoutResult layoutResult
    ) {
        var line = composition.getLine(lineIndex);
        var note = line.getNote(xIndex);
        return INSTANCE.getGlissandoX1Ss(note, glissando, layoutResult);
    }

    /**
     * Calculates the ending X position for a glissando (public static version).
     * <p>
     * This method is used by HorizontalAdjustment to position UI handles.
     *
     * @param xIndex       Index of the source note in the line
     * @param glissando    The glissando data
     * @param lineIndex    Index of the line in the composition
     * @param composition  The composition containing the line
     * @param layoutResult Layout result for resolving note positions (may be null)
     * @return X coordinate for glissando end in staff-space units
     */
    public static double getGlissandoX2Ss(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition,
        @Nullable LayoutResult layoutResult
    ) {
        var line = composition.getLine(lineIndex);
        return INSTANCE.getGlissandoX2Ss(line, xIndex, glissando, layoutResult);
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

        var layoutResult = ctx.getLayoutResult();
        var x1 = getGlissandoX1Ss(note, glissando, layoutResult);
        var x2 = getGlissandoX2Ss(line, noteIndex, glissando, layoutResult);
        var y1 = noteStaffPositionToCoordinateSs(note.getStaffPosition(), ctx.getMiddleLineYSs());

        // Use the live next note's staff position when available so the glissando
        // endpoint tracks the note during pitch-drag. Fall back to glissando.pitch
        // at end of line where there is no next note.
        var nextNote = ((noteIndex + 1) < line.noteCount()) ? line.getNote(noteIndex + 1) : null;
        var targetPitch = (nextNote != null) ? nextNote.getStaffPosition() : glissando.pitch;
        var y2 = noteStaffPositionToCoordinateSs(targetPitch, ctx.getMiddleLineYSs());

        // Adjust endpoints to follow the line direction (angle-dependent gap).
        if (nextNote != null) {
            var x1CenterSs = resolveNoteXSs(note, layoutResult);
            var x2CenterSs = resolveNoteXSs(nextNote, layoutResult);
            var adjusted = adjustEndpointsForAngle(x1CenterSs, x2CenterSs, y1, y2, x1, x2);
            y1 = adjusted[0];
            y2 = adjusted[1];
        }

        renderGlissandoLine(g2, x1, y1, x2, y2);
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
        var layoutResult = ctx.getLayoutResult();
        var x1 = getGlissandoX1Ss(note, glissando, layoutResult);
        var x2 = getGlissandoX2Ss(line, xIndex, glissando, layoutResult);
        var y1 = noteStaffPositionToCoordinateSs(note.getStaffPosition(), ctx.getMiddleLineYSs());
        var y2 = noteStaffPositionToCoordinateSs(glissando.pitch, ctx.getMiddleLineYSs());

        // Adjust endpoints to follow the line direction (angle-dependent gap).
        if ((xIndex + 1) < line.noteCount()) {
            var nextNote = line.getNote(xIndex + 1);
            var x1CenterSs = resolveNoteXSs(note, layoutResult);
            var x2CenterSs = resolveNoteXSs(nextNote, layoutResult);
            var adjusted = adjustEndpointsForAngle(x1CenterSs, x2CenterSs, y1, y2, x1, x2);
            y1 = adjusted[0];
            y2 = adjusted[1];
        }

        renderGlissandoLine(g2, x1, y1, x2, y2);
    }

    /**
     * Returns the default Y offset (in staff positions) used for end-of-line glissando previews.
     */
    public static int getDefaultSlideOutYOffsetSp() {
        return DEFAULT_SLIDE_OUT_Y_OFFSET_SP;
    }

    /**
     * Renders a preview glissando line connecting the source note to a target pitch.
     * <p>
     * Used when the glissando tool is selected and the mouse hovers between notes
     * (or past the last note). No note head is shown — only the wavy line preview.
     *
     * @param g2            Graphics context (staff-space coordinate system)
     * @param sourceIndex   Index of the source note in the line
     * @param targetPitchSp Target pitch in staff positions
     * @param line          The line containing the notes
     * @param ctx           Render context
     */
    public void renderPreviewGlissando(
        @NotNull Graphics2D g2,
        int sourceIndex,
        int targetPitchSp,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        if (sourceIndex < 0 || sourceIndex >= line.noteCount()) {
            return;
        }

        var note = line.getNote(sourceIndex);
        var sentinel = new Note.Glissando(targetPitchSp);
        var layoutResult = ctx.getLayoutResult();
        var x1 = getGlissandoX1Ss(note, sentinel, layoutResult);

        double x2;
        var noteCount = line.noteCount();

        if (sourceIndex + 1 < noteCount) {
            x2 = getGlissandoX2Ss(line, sourceIndex, sentinel, layoutResult);
        } else {
            x2 = resolveNoteXSs(note, layoutResult) + LayoutConstants.DEFAULT_COLUMN_GAP_SS;
        }

        var middleLineYSs = ctx.getMiddleLineYSs();
        var y1 = noteStaffPositionToCoordinateSs(note.getStaffPosition(), middleLineYSs);
        var y2 = noteStaffPositionToCoordinateSs(targetPitchSp, middleLineYSs);

        if (sourceIndex + 1 < noteCount) {
            var nextNote = line.getNote(sourceIndex + 1);
            var x1CenterSs = resolveNoteXSs(note, layoutResult);
            var x2CenterSs = resolveNoteXSs(nextNote, layoutResult);
            var adjusted = adjustEndpointsForAngle(x1CenterSs, x2CenterSs, y1, y2, x1, x2);
            y1 = adjusted[0];
            y2 = adjusted[1];
        }

        renderGlissandoLine(g2, x1, y1, x2, y2);
    }

    // ==========================================================================
    // Position Calculation
    // ==========================================================================

    /**
     * Calculates the starting X position for a glissando.
     * <p>
     * The start is placed MIN_NOTEHEAD_GAP_SS past the source notehead's right edge,
     * where the right edge is read from bravura_metadata.json bounding boxes.
     *
     * @param note         The source note
     * @param glissando    The glissando data
     * @param layoutResult Layout result for resolving note positions (may be null)
     * @return X coordinate for glissando start in staff-space units
     */
    private double getGlissandoX1Ss(
        @NotNull Note note,
        @NotNull Note.Glissando glissando,
        @Nullable LayoutResult layoutResult
    ) {
        var noteXSs = resolveNoteXSs(note, layoutResult);
        var x1 = noteXSs + getNoteheadRightEdgeSs(note) + MIN_NOTEHEAD_GAP_SS;
        x1 += note.getDotCount() * DOT_OFFSET_SS;
        x1 += glissando.x1Translate;
        return x1;
    }

    /**
     * Returns the right edge of the notehead bounding box in staff spaces, relative to note X.
     * <p>
     * For regular notes the value is read from bravura_metadata.json via SMuFLMetadata.
     * For grace notes the pre-composed acciaccatura glyph bbox is scaled by GRACE_NOTE_SCALE.
     *
     * @param note The note whose notehead right edge is needed
     * @return Right edge of the notehead in staff-space units (relative to note X)
     */
    private static double getNoteheadRightEdgeSs(@NotNull Note note) {
        var metadata = SMuFLMetadata.getInstance();
        var noteType = note.getNoteType();

        // Regular notes: look up the notehead glyph directly
        var glyph = noteType.getSMuFLNoteheadGlyph();

        if (glyph != null) {
            var bbox = metadata.getBBox(glyph);

            if (bbox != null) {
                return bbox.right();
            }
        }

        // Grace notes: pre-composed glyph rendered at GRACE_NOTE_SCALE
        if (noteType.isGraceNote()) {
            var graceGlyph = note.isUpper()
                ? SMuFLGlyph.GRACE_NOTE_ACCIACCATURA_STEM_UP
                : SMuFLGlyph.GRACE_NOTE_ACCIACCATURA_STEM_DOWN;
            var bbox = metadata.getBBox(graceGlyph);

            if (bbox != null) {
                return bbox.right() * GraceNoteRenderer.GRACE_NOTE_SCALE;
            }
        }

        // Fallback: use a safe default (noteheadBlack right edge)
        return 1.18;
    }

    /**
     * Calculates the ending X position for a glissando.
     *
     * @param line         The line containing the notes
     * @param noteIndex    Index of the source note
     * @param glissando    The glissando data
     * @param layoutResult Layout result for resolving note positions (may be null)
     * @return X coordinate for glissando end in staff-space units
     */
    private double getGlissandoX2Ss(
        @NotNull Line line,
        int noteIndex,
        @NotNull Note.Glissando glissando,
        @Nullable LayoutResult layoutResult
    ) {
        var x2 = -glissando.x2Translate;

        if ((noteIndex + 1) < line.noteCount()) {
            var nextNote = line.getNote(noteIndex + 1);
            x2 += resolveNoteXSs(nextNote, layoutResult) - MIN_NOTEHEAD_GAP_SS;

            var accNum = nextNote.getAccidental().ordinal();

            if (accNum > 0) {
                x2 -= NoteRenderer.getAccidentalWidthSs(nextNote);
                x2 -= ACCIDENTAL_GAP_SS;
            }
        } else {
            // At end of line, use fixed offset from current note
            var currentNote = line.getNote(noteIndex);
            x2 += resolveNoteXSs(currentNote, layoutResult) + END_OF_LINE_OFFSET_SS;
        }

        return x2;
    }

    /**
     * Resolves the actual X position for a note, using layout data when available.
     *
     * @param note         The note to resolve
     * @param layoutResult Layout result (may be null for fallback)
     * @return The note's X position in staff-space units
     */
    private static double resolveNoteXSs(
        @NotNull Note note,
        @Nullable LayoutResult layoutResult
    ) {
        return (layoutResult != null) ? layoutResult.getNoteXSs(note) : note.getXPosSs();
    }

    // ==========================================================================
    // Glyph Rendering
    // ==========================================================================

    /**
     * Adjusts glissando endpoint y-coordinates to follow the line direction.
     * <p>
     * Without this, a fixed x-gap from the notehead causes the wavy line to appear
     * detached at steep angles. By shifting y proportionally to the slope (MuseScore-style),
     * the endpoints move along the line direction so the visual gap scales as 1/cos(angle).
     *
     * @param x1CenterSs Notehead center X of the source note, in staff spaces
     * @param x2CenterSs Notehead center X of the target note, in staff spaces
     * @param y1         Raw y of source note, in staff spaces
     * @param y2         Raw y of target note, in staff spaces
     * @param x1         Gap-adjusted start X, in staff spaces
     * @param x2         Gap-adjusted end X, in staff spaces
     * @return {adjustedY1, adjustedY2}
     */
    private static double[] adjustEndpointsForAngle(
        double x1CenterSs, double x2CenterSs,
        double y1, double y2,
        double x1, double x2
    ) {
        var rawDx = x2CenterSs - x1CenterSs;

        if (rawDx > 0) {
            var slope = (y2 - y1) / rawDx;
            y1 += slope * (x1 - x1CenterSs);
            y2 += slope * (x2 - x2CenterSs);
        }

        return new double[]{y1, y2};
    }

    /**
     * Renders the actual glissando wavy line between two points.
     *
     * @param g2 Graphics context (staff-space coordinate system)
     * @param x1 Start X coordinate in staff spaces
     * @param y1 Start Y coordinate in staff spaces
     * @param x2 End X coordinate in staff spaces
     * @param y2 End Y coordinate in staff spaces
     */
    private void renderGlissandoLine(
        @NotNull Graphics2D g2,
        double x1,
        double y1,
        double x2,
        double y2
    ) {
        var dx = x2 - x1;
        var dy = y2 - y1;
        var length = Math.sqrt(dx * dx + dy * dy);

        var advance = Objects.requireNonNull(
            SMuFLMetadata.getInstance().getAdvanceWidth(SMuFLGlyph.WIGGLE_TRILL_FASTER));

        // Truncate (not ceil) so segments never overshoot the endpoint gap.
        // Any leftover space is split equally on both sides, centering the pattern.
        var n = Math.max(MIN_SEGMENTS, (int) (length / advance));
        var xOffset = (length - n * advance) * 0.5;

        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            g2.setFont(BaseElementRenderer.BRAVURA_FONT);

            // Translate to (x1, y1), then rotate so the line is horizontal.
            // Applying the Y centering offset after rotation keeps it perpendicular
            // to the line at any angle (rather than purely vertical in screen coords).
            g2.translate(x1, y1);
            g2.rotate(Math.atan2(dy, dx));

            var glyphStr = SMuFLGlyph.WIGGLE_TRILL_FASTER.asString();

            for (var i = 0; i < n; i++) {
                g2.drawString(glyphStr, (float) (xOffset + i * advance), (float) GLISSANDO_Y_OFFSET_SS);
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
        // Staff positions are in half-staff-space increments, so multiply by 0.5 ss
        return middleLineYSs + staffPosition * 0.5;
    }
}
