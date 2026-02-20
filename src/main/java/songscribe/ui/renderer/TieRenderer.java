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
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import java.awt.*;
import java.awt.geom.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.Tie;

/**
 * Renders tie arcs between two notes of the same pitch.
 * <p>
 * Ties are Bézier curves that connect notes. The tie direction (above or below)
 * depends on stem direction.
 */
public class TieRenderer extends BaseElementRenderer<Tie> {

    // ==========================================================================
    // Constants from Renderer
    // ==========================================================================

    private static final EngravingDefaults ENGRAVING_DEFAULTS =
        SMuFLMetadata.getInstance().getEngravingDefaults();

    private static final BasicStroke LINE_STROKE = new BasicStroke(
        (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.tieMidpointThickness()),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Note Y offset (from LayoutStylesheet)
    private static final double NOTE_Y_OFFSET_PX = LayoutStylesheet.toPixelsDouble(LayoutStylesheet.NOTE_Y_OFFSET);

    // Singleton instance
    private static final TieRenderer INSTANCE = new TieRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TieRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull TieRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Tie element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorNote();
        var endNote = element.getEndNote();

        if (anchorNote == null || endNote == null) {
            return;
        }

        renderTie(g2, anchorNote, endNote, ctx);
    }

    /**
     * Renders a tie between two notes.
     *
     * @param g2        Graphics context
     * @param startNote The starting note
     * @param endNote   The ending note
     * @param ctx       Render context
     */
    public void renderTie(
        @NotNull Graphics2D g2,
        @NotNull Note startNote,
        @NotNull Note endNote,
        @NotNull ElementRenderContext ctx
    ) {
        double middleLineYSs = ctx.getMiddleLineYSs();

        // Determine tie direction (above = true if stem is up)
        boolean tieUpper = startNote.isUpper();

        // Calculate start position
        double halfNoteWidth = getHalfNoteWidthForTiePx(startNote);
        double xPos = startNote.getXPos() + halfNoteWidth + 2;

        // Calculate gap (distance between tie start and end)
        double endHalfWidth = getHalfNoteWidthForTiePx(endNote);
        double gap = (endNote.getXPos() + endHalfWidth) - xPos - 3;

        // Handle case where stem directions differ
        if (startNote.isUpper() != endNote.isUpper() && startNote.isUpper()) {
            tieUpper = false;
            xPos += 7;
            gap -= 5;
        }

        // Calculate Y position
        double yPos = middleLineYSs + (startNote.getStaffPosition() * NOTE_Y_OFFSET_PX) +
            (tieUpper ? (NOTE_Y_OFFSET_PX + 2) : (-NOTE_Y_OFFSET_PX - 2));

        // Draw the tie curve
        try (var ignored = GraphicsState.save(g2, COLOR, STROKE)) {
            g2.setStroke(LINE_STROKE);
            g2.setColor(NOTE_COLOR);

            GeneralPath tie = new GeneralPath(Path2D.WIND_NON_ZERO, 2);
            tie.moveTo(xPos, yPos);

            // First curve (outer)
            tie.quadTo(
                xPos + (gap / 2),
                yPos + (tieUpper ? 6 : -6),
                xPos + gap,
                yPos
            );

            // Second curve (inner, creating the filled shape)
            tie.quadTo(
                xPos + (gap / 2),
                yPos + (tieUpper ? 8 : -8),
                xPos,
                yPos
            );

            tie.closePath();
            g2.draw(tie);
            g2.fill(tie);
        }
    }

    /**
     * Returns half the width of a note for tie positioning.
     * Whole notes and half notes are wider than filled notes.
     */
    private double getHalfNoteWidthForTiePx(@NotNull Note note) {
        var noteType = note.getNoteType();

        if (noteType == NoteType.SEMIBREVE || noteType == NoteType.MINIM) {
            return note.getRealUpNoteRect().width / 2.0;
        }

        // Default crotchet width (from FughettaRenderer)
        double crotchetWidth = BaseElementRenderer.NOTE_FONT_SIZE / 3.6056337d;
        return crotchetWidth / 2.0;
    }
}
