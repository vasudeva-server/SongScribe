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

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.music.Tempo;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Renders tempo change indicators (note = number format).
 * <p>
 * Tempo markings show the beat note and tempo in BPM, e.g., "♩ = 120".
 * They appear above the staff at the specified note position.
 */
public class TempoRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;
    private static final double TEMPO_CHANGE_ZOOM_X = BaseElementRenderer.TEMPO_CHANGE_ZOOM_X;
    private static final double TEMPO_CHANGE_ZOOM_Y = BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y;
    private static final double CROTCHET_WIDTH = NOTE_FONT_SIZE / 3.6056337d;

    // Stem and flag positions for tempo note bounds calculation
    private static final double UPPER_CROTCHET_STEM_X = NOTE_FONT_SIZE / 3.6056337d;
    private static final double UPPER_MINIM_STEM_X = NOTE_FONT_SIZE / 3.1411042d;
    private static final double TEMPO_STEM_SHORTENING = 2;
    private static final Line2D.Float UPPER_STEM = new Line2D.Float(
        0f,
        -NOTE_FONT_SIZE / 32f,
        0f,
        -NOTE_FONT_SIZE / 1.1429f
    );
    private static final double UPPER_FLAG_X = NOTE_FONT_SIZE / 3.6834533d;
    private static final double UPPER_FLAG_Y = -NOTE_FONT_SIZE / 1.6623377d;

    // Singleton instance
    private static final TempoRenderer INSTANCE = new TempoRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TempoRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull TempoRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Tempo Note Bounds Calculation
    // ==========================================================================

    /**
     * Calculate the bounding box for a tempo note in the local coordinate
     * system (before scaling and translation). This includes the note head,
     * stem, flags, and dots.
     * <p>
     * Migrated from FughettaRenderer.getTempoNoteBounds().
     *
     * @param frc  the font render context
     * @param font the Fughetta font
     * @param note the tempo note
     * @return the bounding rectangle, or null if the note type has no glyph
     */
    @Nullable
    public static Rectangle2D getTempoNoteBounds(
        FontRenderContext frc,
        Font font,
        Note note
    ) {
        var noteType = note.getNoteType();
        var noteHeadChar = NoteRenderer.getNoteHeadChar(noteType);

        if (noteHeadChar == null) {
            return null;
        }

        // Get note head bounds
        var noteGv = font.createGlyphVector(frc, noteHeadChar);
        var bounds = new Rectangle2D.Double();
        bounds.setRect(noteGv.getVisualBounds());

        // Add stem bounds if note has stem
        if (noteType.isNoteWithStem()) {
            var stemX = (noteType == NoteType.MINIM)
                ? UPPER_MINIM_STEM_X
                : UPPER_CROTCHET_STEM_X;
            var stemY1 = UPPER_STEM.getY1();
            var stemY2 = UPPER_STEM.getY2() + TEMPO_STEM_SHORTENING;

            // Account for stem stroke width (1px) and cap
            // Use min/max to handle both upper and lower stems
            var stemTop = Math.min(stemY1, stemY2);
            var stemBottom = Math.max(stemY1, stemY2);
            var stemBounds = new Rectangle2D.Double(
                stemX - 0.5,
                stemTop,
                1.0,
                stemBottom - stemTop
            );

            bounds.add(stemBounds);
        }

        // Add flag bounds if note is beamable (quaver, semiquaver, etc.)
        if (noteType.isBeamable()) {
            // Approximate flag bounds - flags extend upward from the stem
            var flagBounds = new Rectangle2D.Double(
                UPPER_FLAG_X,
                UPPER_FLAG_Y + TEMPO_STEM_SHORTENING,
                5.0, // approximate flag width
                10.0 // approximate flag height
            );

            bounds.add(flagBounds);
        }

        // Add dot bounds if note has dots
        if (note.getDotCount() > 0) {
            // Dots are positioned to the right of the note head
            var dotX = bounds.getMaxX() + 2;
            var dotY = 0;
            var dotBounds = new Rectangle2D.Double(
                dotX,
                dotY - 2,
                note.getDotCount() * 4.0,
                4.0
            );

            bounds.add(dotBounds);
        }

        return bounds;
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
        var tempo = element.getTempoChange();

        if (tempo == null) {
            return;
        }

        renderTempoChange(g2, tempo, element, ctx);
    }

    /**
     * Renders a tempo change for a note if it has one.
     *
     * @param g2   Graphics context
     * @param note The note
     * @param ctx  Render context
     */
    public void renderTempo(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Renders the initial tempo marking (used for first note of first line).
     * <p>
     * This method is for rendering the composition's default tempo, which is
     * stored separately from note tempo changes.
     *
     * @param g2    Graphics context
     * @param note  The note at which to position the tempo
     * @param tempo The tempo to render
     * @param ctx   Render context
     */
    public void renderInitialTempo(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull Tempo tempo,
        @NotNull ElementRenderContext ctx
    ) {
        renderTempoChange(g2, tempo, note, ctx);
    }

    /**
     * Renders the tempo change indicator.
     */
    private void renderTempoChange(
        @NotNull Graphics2D g2,
        @NotNull Tempo tempo,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        var composition = ctx.getComposition();
        var line = ctx.getCurrentLine();
        int lineIndex = ctx.getLineIndex();

        if (line == null) {
            return;
        }

        int middleLineY = ctx.getMiddleLineY();

        // Calculate Y position (above the staff)
        int yPos = middleLineY + getEffectiveTempoChangeYPos(g2, line, lineIndex);

        // Build tempo text
        var tempoBuilder = new StringBuilder(25);
        var tempoTypeNote = tempo.getTempoType().getNote();

        double xPos = note.getXPos();

        if (tempo.shouldShowTempo()) {
            drawTempoChangeNote(g2, tempoTypeNote, (int) xPos, yPos);
            tempoBuilder.append("= ");
            tempoBuilder.append(tempo.getVisibleTempo());
            tempoBuilder.append(' ');
        }

        tempoBuilder.append(tempo.getTempoDescription());

        g2.setFont(composition.getAttributionFont());
        g2.setColor(NOTE_COLOR);

        if (tempo.shouldShowTempo()) {
            int extraOffset = 0;

            if (tempoTypeNote.getDotCount() == 1 ||
                tempoTypeNote.getNoteType() == NoteType.QUAVER) {
                extraOffset = 6;
            }

            xPos += CROTCHET_WIDTH + 5 + extraOffset;
        }

        var tempoText = tempoBuilder.toString();
        g2.drawString(tempoText, (float) xPos, (float) yPos);
    }

    /**
     * Draws a tempo note (scaled smaller than regular notes).
     */
    private void drawTempoChangeNote(
        @NotNull Graphics2D g2,
        @NotNull Note tempoNote,
        int x,
        int y
    ) {
        g2.setFont(BaseElementRenderer.FUGHETTA);
        var transform = g2.getTransform();

        g2.translate(x, y - ((NOTE_FONT_SIZE * TEMPO_CHANGE_ZOOM_Y) / 8.0));
        g2.scale(TEMPO_CHANGE_ZOOM_X, TEMPO_CHANGE_ZOOM_Y);

        // Draw note using NoteRenderer's simple rendering
        paintSimpleTempoNote(g2, tempoNote);

        g2.setTransform(transform);
    }

    /**
     * Paints a simple note for tempo display (no accidentals, ledger lines, etc.).
     */
    private void paintSimpleTempoNote(@NotNull Graphics2D g2, @NotNull Note note) {
        var noteType = note.getNoteType();
        String headChar = NoteRenderer.getNoteHeadChar(noteType);

        if (headChar == null) {
            return;
        }

        g2.setColor(NOTE_COLOR);
        g2.drawString(headChar, 0f, 0f);

        // Draw stem if needed (tempo notes have stems up)
        if (noteType.isNoteWithStem()) {
            // Simple stem rendering for tempo notes
            float stemX = (float) (NOTE_FONT_SIZE / 3.6056337d);
            float stemY1 = -NOTE_FONT_SIZE / 32f;
            float stemY2 = -NOTE_FONT_SIZE / 1.1429f + 2; // Shortened stem

            g2.drawLine((int) stemX, (int) stemY1, (int) stemX, (int) stemY2);

            // Draw flags for 8th notes and smaller
            if (noteType.isBeamable()) {
                float flagX = (float) (NOTE_FONT_SIZE / 3.6834533d);
                float flagY = (float) (-NOTE_FONT_SIZE / 1.6623377f + 2);
                g2.drawString("\uf06a", flagX, flagY); // Main upper flag
            }
        }

        // Draw dots
        if (note.getDotCount() > 0) {
            double dotWidth = NOTE_FONT_SIZE / 9.142858d;
            g2.fillOval((int) 13.1d, (int) (-dotWidth / 2), (int) dotWidth, (int) dotWidth);
        }
    }

    /**
     * Gets the effective Y position for tempo change, accounting for line settings.
     */
    private int getEffectiveTempoChangeYPos(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        // Position above the staff
        return (int) (-7 * LayoutStylesheet.NOTE_Y_OFFSET) + line.getTempoChangeYPos();
    }
}
