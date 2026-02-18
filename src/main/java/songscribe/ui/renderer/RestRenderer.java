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
import java.awt.geom.Ellipse2D;
import java.util.EnumMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.layout.LayoutStylesheet;

import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

/**
 * Renders rest glyphs (whole, half, quarter, eighth, sixteenth, thirty-second rests).
 * <p>
 * Rest glyphs are rendered at the note's position using the Fughetta font.
 * Rests don't have stems, flags, or accidentals.
 */
public class RestRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Rest Glyphs (from FughettaRenderer)
    // ==========================================================================

    private static final EnumMap<NoteType, String> REST_GLYPHS = new EnumMap<>(NoteType.class);

    static {
        REST_GLYPHS.put(NoteType.SEMIBREVE_REST, "\uf0ee");
        REST_GLYPHS.put(NoteType.MINIM_REST, "\uf0ee");
        REST_GLYPHS.put(NoteType.CROTCHET_REST, "\uf0ce");
        REST_GLYPHS.put(NoteType.QUAVER_REST, "\uf0e4");
        REST_GLYPHS.put(NoteType.SEMIQUAVER_REST, "\uf0c5");
        REST_GLYPHS.put(NoteType.DEMI_SEMIQUAVER_REST, "\uf0a8");
    }

    // Y position adjustment for whole and half rests (relative to middle line)
    private static final int SEMIBREVE_REST_Y_OFFSET = -2;  // Above the middle line
    private static final int MINIM_REST_Y_OFFSET = 0;       // On the middle line

    // Dot dimensions
    private static final double DOT_WIDTH = NOTE_FONT_SIZE / 9.142858d;
    private static final Ellipse2D.Double[] NOTE_DOTS = new Ellipse2D.Double[]{
        new Ellipse2D.Double(13.1d, -DOT_WIDTH / 2, DOT_WIDTH, DOT_WIDTH),
        new Ellipse2D.Double(15.878d + DOT_WIDTH, -DOT_WIDTH / 2, DOT_WIDTH, DOT_WIDTH),
    };

    // Singleton instance
    private static final RestRenderer INSTANCE = new RestRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private RestRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull RestRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the Fughetta glyph character for a rest type.
     *
     * @param noteType The rest note type
     * @return The glyph string, or null if not a rest type
     */
    @Nullable
    public static String getRestGlyph(@NotNull NoteType noteType) {
        return REST_GLYPHS.get(noteType);
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
        var noteType = element.getNoteType();

        if (!noteType.isRest()) {
            return;
        }

        // Get rest position
        int noteX = element.getXPos();
        int noteY = calculateRestY(element, ctx.getMiddleLineY());

        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            g2.translate(noteX, noteY);
            g2.setFont(ctx.getMusicFont());
            // Note: Don't set color here - respect the color set by the caller
            // (e.g., blue for edit notes, black for composition notes)

            // Draw rest glyph
            String glyph = REST_GLYPHS.get(noteType);

            if (glyph != null) {
                g2.drawString(glyph, 0f, 0f);
            }

            // Draw dots
            renderDots(g2, element, noteType);
        }
    }

    /**
     * Calculates the Y position for a rest.
     * Whole rests hang from the second line, half rests sit on the middle line.
     * Other rests are centered vertically on the staff.
     */
    private int calculateRestY(@NotNull Note note, int middleLineY) {
        var noteType = note.getNoteType();

        if (noteType == NoteType.SEMIBREVE_REST) {
            // Whole rest hangs below the 4th line (second from top)
            return middleLineY + (int) (SEMIBREVE_REST_Y_OFFSET * LayoutStylesheet.NOTE_Y_OFFSET);
        }

        if (noteType == NoteType.MINIM_REST) {
            // Half rest sits on the middle line
            return middleLineY + (int) (MINIM_REST_Y_OFFSET * LayoutStylesheet.NOTE_Y_OFFSET);
        }

        // Other rests use standard note Y positioning
        return middleLineY + (int) (note.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET);
    }

    /**
     * Renders dots for dotted rests.
     */
    private void renderDots(@NotNull Graphics2D g2, @NotNull Note note, @NotNull NoteType noteType) {
        if (note.getDotCount() == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            // Position dots to the right of the rest glyph
            // Different rest types have different widths, so adjust accordingly
            switch (noteType) {
                case SEMIBREVE_REST:
                case MINIM_REST:
                    g2.translate(8, 0);
                    break;

                case CROTCHET_REST:
                    g2.translate(6, 0);
                    break;

                case QUAVER_REST:
                case SEMIQUAVER_REST:
                case DEMI_SEMIQUAVER_REST:
                    g2.translate(7, 0);
                    break;

                default:
                    g2.translate(8, 0);
                    break;
            }

            // Draw the dots
            for (int i = 0; i < note.getDotCount(); i++) {
                g2.fill(NOTE_DOTS[i]);
            }
        }
    }
}
