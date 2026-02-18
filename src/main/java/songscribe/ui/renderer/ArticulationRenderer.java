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
import java.awt.geom.Ellipse2D;

import org.jetbrains.annotations.NotNull;

import songscribe.music.DurationArticulation;
import songscribe.music.ForceArticulation;
import songscribe.music.Note;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Renders articulation markings on notes (staccato, accent).
 * <p>
 * Articulations modify how a note is played:
 * <ul>
 *   <li>Staccato - short, detached (dot above/below note)</li>
 *   <li>Accent - emphasized attack (> symbol)</li>
 * </ul>
 */
public class ArticulationRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Crotchet width for positioning
    private static final double CROTCHET_WIDTH = BaseElementRenderer.NOTE_FONT_SIZE / 3.6056337d;

    // Staccato dot shape
    private static final Ellipse2D.Double STACCATO_ELLIPSE = new Ellipse2D.Double(0, 0, 4, 4);

    // Singleton instance
    private static final ArticulationRenderer INSTANCE = new ArticulationRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private ArticulationRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull ArticulationRenderer getInstance() {
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
        renderAccent(element, g2, ctx);
        renderStaccato(element, g2, ctx);
    }

    /**
     * Renders articulations for a note.
     *
     * @param g2   Graphics context
     * @param note The note
     * @param ctx  Render context
     */
    public void renderArticulation(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Renders an accent marking (> shape).
     */
    private void renderAccent(
        @NotNull Note note,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        if (note.getForceArticulation() != ForceArticulation.ACCENT) {
            return;
        }

        int middleLineY = ctx.getMiddleLineY();
        int xPos = note.getXPos();
        int x2 = xPos + (int) CROTCHET_WIDTH + 2;
        int y = getArticulationY(note, middleLineY);

        g2.setColor(NOTE_COLOR);
        g2.drawLine(xPos, y - 3, x2, y);
        g2.drawLine(xPos, y + 3, x2, y);
    }

    /**
     * Renders a staccato dot.
     */
    private void renderStaccato(
        @NotNull Note note,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        if (note.getDurationArticulation() != DurationArticulation.STACCATO) {
            return;
        }

        int middleLineY = ctx.getMiddleLineY();
        var transform = g2.getTransform();

        // Calculate position - opposite side from stem
        int dir = note.isUpper() ? 1 : -1;
        int durYPos = note.getYPos() + (dir * 2) + (dir * (1 - (note.getYPos() % 2)));
        int durY = middleLineY + (int) (durYPos * LayoutStylesheet.NOTE_Y_OFFSET);

        // Center dot under/over note head
        double halfNoteWidth = getHalfNoteWidthForTie(note);
        int xPos = note.getXPos();

        g2.translate((xPos + halfNoteWidth) - 2, durY - 2);
        g2.setColor(NOTE_COLOR);
        g2.fill(STACCATO_ELLIPSE);

        g2.setTransform(transform);
    }

    /**
     * Calculates the Y position for articulation markings.
     * Articulations are placed on the opposite side from the stem.
     */
    private int getArticulationY(@NotNull Note note, int middleLineY) {
        // Place articulation on opposite side from stem
        int yPos = note.getYPos();

        if (note.isUpper()) {
            // Stem up, articulation below
            int articulationYPos = yPos + 2;

            if ((yPos % 2) == 0) {
                articulationYPos++;
            }

            return middleLineY + (int) (articulationYPos * LayoutStylesheet.NOTE_Y_OFFSET);
        } else {
            // Stem down, articulation above
            int articulationYPos = yPos - 2;

            if ((yPos % 2) == 0) {
                articulationYPos--;
            }

            return middleLineY + (int) (articulationYPos * LayoutStylesheet.NOTE_Y_OFFSET);
        }
    }

    /**
     * Returns half the width of a note for positioning.
     */
    private double getHalfNoteWidthForTie(@NotNull Note note) {
        var noteType = note.getNoteType();

        if (noteType == songscribe.music.NoteType.SEMIBREVE ||
            noteType == songscribe.music.NoteType.MINIM) {
            return note.getRealUpNoteRect().width / 2.0;
        }

        return CROTCHET_WIDTH / 2.0;
    }
}
