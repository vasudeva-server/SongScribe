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

import songscribe.music.Note;
import songscribe.smufl.SMuFLGlyph;

/**
 * Renders grace notes using pre-composed SMuFL glyphs.
 * <p>
 * Grace notes are scaled to 120% of the precomposed glyph size.
 */
public class GraceNoteRenderer extends BaseElementRenderer<Note> {

    private static final GraceNoteRenderer INSTANCE = new GraceNoteRenderer();

    public static GraceNoteRenderer getInstance() {
        return INSTANCE;
    }

    /** Scale factor for grace notes (120% of the precomposed glyph size). */
    public static final double GRACE_NOTE_SCALE = 1.2;

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Note element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        renderGraceQuaver(g2, element, ctx.getMiddleLineY());
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
        renderGraceQuaver(g2, note, middleLineY);
    }

    /**
     * Renders a grace quaver using pre-composed SMuFL glyph.
     */
    private void renderGraceQuaver(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int middleLineY
    ) {
        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = note.getXPos();
            var noteY = noteYPosToCoordinate(note.getYPos(), middleLineY);

            g2.translate(noteX, noteY);
            g2.scale(GRACE_NOTE_SCALE, GRACE_NOTE_SCALE);
            g2.setFont(BRAVURA_FONT);
            // Note: Don't set color here - respect the color set by the caller
            // (e.g., blue for edit notes, black for composition notes)

            // Draw pre-composed grace note glyph
            var glyph = note.isUpper()
                ? SMuFLGlyph.GRACE_NOTE_ACCIACCATURA_STEM_UP
                : SMuFLGlyph.GRACE_NOTE_ACCIACCATURA_STEM_DOWN;
            g2.drawString(glyph.asString(), 0f, 0f);

            // Note: Ledger lines are not rendered for grace notes
        }
    }

}
