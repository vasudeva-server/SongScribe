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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.util.GraphicUtils;

import static songscribe.ui.renderer.GraphicsState.Property.*;

/**
 * Renders bar lines and repeat signs using SMuFL/Bravura glyphs.
 * <p>
 * Handles:
 * <ul>
 *   <li>Single bar lines</li>
 *   <li>Double bar lines</li>
 *   <li>Final double bar lines (with thick line)</li>
 *   <li>Left repeat</li>
 *   <li>Right repeat</li>
 *   <li>Left-right repeat</li>
 * </ul>
 */
public class BarRenderer extends BaseElementRenderer<Note> {

    // SMuFL barline glyphs extend upward from their origin at the bottom staff line.
    // From the middle line, the bottom staff line is 2 staff spaces below.
    private static final double BOTTOM_STAFF_LINE_Y = 2.0 * StaffSpaces.PIXELS_PER_STAFF_SPACE;

    // Singleton instance
    private static final BarRenderer INSTANCE = new BarRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private BarRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull BarRenderer getInstance() {
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
        var noteType = element.getNoteType();

        if (!noteType.isBarLine() && !noteType.isRepeat()) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(element.getXPos(), ctx.getMiddleLineY());
            renderBarLineOrRepeat(g2, noteType);
        }
    }

    /**
     * Renders a bar line or repeat sign (static helper for delegation).
     */
    public static void renderBarLineOrRepeat(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
        var glyph = glyphForNoteType(noteType);

        if (glyph == null) {
            return;
        }

        double x = computeGlyphX(g2, glyph, noteType);

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.drawString(glyph.asString(), (float) x, (float) BOTTOM_STAFF_LINE_Y);
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    /**
     * Maps a note type to its corresponding SMuFL barline/repeat glyph.
     */
    @Nullable
    private static SMuFLGlyph glyphForNoteType(@NotNull NoteType noteType) {
        return switch (noteType) {
            case SINGLE_BARLINE -> SMuFLGlyph.BARLINE_SINGLE;
            case DOUBLE_BARLINE -> SMuFLGlyph.BARLINE_DOUBLE;
            case FINAL_DOUBLE_BARLINE -> SMuFLGlyph.BARLINE_FINAL;
            case REPEAT_LEFT -> SMuFLGlyph.REPEAT_LEFT;
            case REPEAT_RIGHT -> SMuFLGlyph.REPEAT_RIGHT;
            case REPEAT_LEFT_RIGHT -> SMuFLGlyph.REPEAT_RIGHT_LEFT;
            default -> null;
        };
    }

    /**
     * Computes the X position for drawing a barline or repeat glyph.
     * Barlines are right-aligned at {@link Note#NORMAL_IMAGE_WIDTH}.
     * Repeats are centered in the note image space using the glyph advance width.
     */
    private static double computeGlyphX(
        @NotNull Graphics2D g2,
        @NotNull SMuFLGlyph glyph,
        @NotNull NoteType noteType
    ) {
        if (noteType.isBarLine()) {
            return Note.NORMAL_IMAGE_WIDTH;
        }

        // Center repeat glyphs in the note image space
        var advanceSS = SMuFLMetadata.getInstance().getAdvanceWidth(glyph);
        double advancePx = (advanceSS != null) ? StaffSpaces.toPixels(advanceSS) : 0;
        double x = (Note.NORMAL_IMAGE_WIDTH - advancePx) / 2.0;

        return GraphicUtils.snapXToDevicePixel(g2, x);
    }
}
