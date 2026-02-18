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

import java.awt.*;

import org.jetbrains.annotations.NotNull;

import static songscribe.ui.renderer.GraphicsState.Property.*;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.Trill;

/**
 * Renders trill markings (tr symbol + wavy line for extended trills).
 */
public class TrillRenderer extends BaseElementRenderer<Trill> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Trill glyph from Fughetta
    private static final String TRILL_GLYPH = "\uf0d9";

    // Glissando glyph for wavy line
    private static final String GLISSANDO_GLYPH = "\uf07e";

    // Length of glissando character
    private static final double GLISSANDO_LENGTH = BaseElementRenderer.NOTE_FONT_SIZE / 2.6666667;

    // Crotchet width
    private static final double CROTCHET_WIDTH = BaseElementRenderer.NOTE_FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final TrillRenderer INSTANCE = new TrillRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TrillRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull TrillRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Trill element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorNote();

        if (anchorNote == null) {
            return;
        }

        var endNote = element.getEndNote();
        int trillYPos = getEffectiveTrillYPos(element, ctx);

        renderTrill(g2, ctx, anchorNote, endNote, trillYPos);
    }

    /**
     * Gets the Y position for a trill from layout result.
     */
    private int getEffectiveTrillYPos(
        @NotNull Trill element,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.getBounds(element);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for Trill element");
        }

        return (int) bounds.getTop() - ctx.getMiddleLineY();
    }

    /**
     * Renders a trill at a note.
     */
    public void renderTrill(
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx,
        @NotNull Note startNote,
        Note endNote,
        int trillYPos
    ) {
        int middleLineY = ctx.getMiddleLineY();

        int x = startNote.getXPos();
        int y = middleLineY + trillYPos;

        try (var ignored = GraphicsState.save(g2, FONT, COLOR)) {
            g2.setFont(ctx.getMusicFont());
            g2.setColor(NOTE_COLOR);
            g2.drawString(TRILL_GLYPH, x, y);

            // Draw wavy line extension if there's an end note
            if (endNote != null && endNote != startNote) {
                int endX = (int) Math.round(endNote.getXPos() + CROTCHET_WIDTH);
                drawWavyLine(g2, x + 18, y - 3, endX, y - 3);
            }
        }
    }

    /**
     * Renders trills from a Line, checking for consecutive trill notes.
     */
    public void renderTrillsFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        int trillYPos = line.getTrillYPos();

        for (int noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);

            if (!note.isTrill()) {
                continue;
            }

            // Only render if this is the start of a trill sequence
            if (noteIndex > 0 && line.getNote(noteIndex - 1).isTrill()) {
                continue;
            }

            // Find the end of the trill sequence
            int trillEnd = noteIndex + 1;

            while (trillEnd < line.noteCount() && line.getNote(trillEnd).isTrill()) {
                trillEnd++;
            }

            trillEnd--;

            Note endNote = (trillEnd > noteIndex) ? line.getNote(trillEnd) : null;
            renderTrill(g2, ctx, note, endNote, trillYPos);
        }
    }

    /**
     * Draws a wavy line using glissando characters.
     */
    private void drawWavyLine(
        @NotNull Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2
    ) {
        double length = Math.sqrt(
            Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)
        );

        int segments = Math.max(2, (int) Math.round(length / GLISSANDO_LENGTH));

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(x1, y1 + 2.25);

            double angle = Math.atan2(y2 - y1, x2 - x1);
            g2.rotate(angle);

            double scale = length / GLISSANDO_LENGTH / segments;
            g2.scale(scale, 1d);

            for (int i = 0; i < segments; i++) {
                g2.drawString(GLISSANDO_GLYPH, (int) Math.round(i * GLISSANDO_LENGTH), 0);
            }
        }
    }
}
