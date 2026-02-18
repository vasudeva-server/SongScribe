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

import static songscribe.ui.renderer.GraphicsState.Property.*;

import java.awt.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.BeatChange;
import songscribe.music.Note;
import songscribe.ui.layout.BeatChangeAttachment;

/**
 * Renders beat change indicators (note = note format).
 * <p>
 * Beat changes show equivalence between two note values, e.g., "♩ = ♪."
 * indicating that the quarter note of the old tempo equals the dotted
 * eighth note of the new tempo.
 */
public class BeatChangeRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;
    private static final double TEMPO_CHANGE_ZOOM_X = BaseElementRenderer.TEMPO_CHANGE_ZOOM_X;
    private static final double TEMPO_CHANGE_ZOOM_Y = BaseElementRenderer.TEMPO_CHANGE_ZOOM_Y;
    private static final double CROTCHET_WIDTH = NOTE_FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final BeatChangeRenderer INSTANCE = new BeatChangeRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private BeatChangeRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull BeatChangeRenderer getInstance() {
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
        var beatChange = element.getBeatChange();

        if (beatChange == null) {
            return;
        }

        renderBeatChange(g2, beatChange, element, ctx);
    }

    /**
     * Renders a beat change for a note if it has one.
     *
     * @param g2   Graphics context
     * @param note The note
     * @param ctx  Render context
     */
    public void renderBeatChange(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Renders the beat change indicator.
     */
    private void renderBeatChange(
        @NotNull Graphics2D g2,
        @NotNull BeatChange beatChange,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        var composition = ctx.getComposition();
        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        int xPos = note.getXPos();
        int yPos = getEffectiveBeatChangeYPos(note, ctx);

        drawBeatChange(g2, beatChange, xPos, yPos, composition);
    }

    /**
     * Gets the Y position for beat change from layout result.
     */
    private int getEffectiveBeatChangeYPos(
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.findAttachmentBounds(note, BeatChangeAttachment.class);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for BeatChangeAttachment on note");
        }

        return (int) bounds.getTop();
    }

    /**
     * Draws a beat change at a specific position.
     *
     * @param g2         Graphics context
     * @param beatChange The beat change data
     * @param xPos       X position
     * @param yPos       Y position
     * @param composition The composition (for font access)
     */
    public void drawBeatChange(
        @NotNull Graphics2D g2,
        @NotNull BeatChange beatChange,
        int xPos,
        int yPos,
        @NotNull songscribe.music.Composition composition
    ) {
        // Draw first note
        drawTempoChangeNote(g2, beatChange.getFirstNote(), xPos, yPos);

        // Draw equals sign
        g2.setFont(composition.getAttributionFont());
        g2.setColor(NOTE_COLOR);
        int eqXPos = xPos + (int) CROTCHET_WIDTH + 7;
        g2.drawString("=", (float) eqXPos, yPos);

        // Draw second note
        drawTempoChangeNote(g2, beatChange.getSecondNote(), (int) Math.round(eqXPos + 12), yPos);
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
        try (var ignored = GraphicsState.save(g2, FONT, TRANSFORM)) {
            g2.setFont(BaseElementRenderer.MUSIC_FONT);

            g2.translate(x, y - ((NOTE_FONT_SIZE * TEMPO_CHANGE_ZOOM_Y) / 8.0));
            g2.scale(TEMPO_CHANGE_ZOOM_X, TEMPO_CHANGE_ZOOM_Y);

            // Draw note using simple rendering
            paintSimpleTempoNote(g2, tempoNote);
        }
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

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(NOTE_COLOR);
            g2.drawString(headChar, 0f, 0f);

            // Draw stem if needed (tempo notes have stems up)
            if (noteType.isNoteWithStem()) {
                float stemX = (float) (NOTE_FONT_SIZE / 3.6056337d);
                float stemY1 = -NOTE_FONT_SIZE / 32f;
                float stemY2 = -NOTE_FONT_SIZE / 1.1429f + 2;

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
    }
}
