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
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;

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
    private static final double BOTTOM_STAFF_LINE_Y_SS = 2.0;

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

        double noteX = resolveBarXSs(g2, element, ctx);

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(noteX, ctx.getMiddleLineYSs());
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

        double x = computeGlyphXSs(g2, glyph, noteType);

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.drawString(glyph.asString(), (float) x, (float) BOTTOM_STAFF_LINE_Y_SS);
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    /**
     * Resolves the X coordinate for a barline/repeat from the layout result,
     * snapped to device pixels for crisp rendering.
     */
    private static double resolveBarXSs(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        double noteX;

        if (ctx.hasOverrideNoteX()) {
            noteX = ctx.getOverrideNoteXSs();
        } else {
            var layoutResult = ctx.getLayoutResult();
            noteX = (layoutResult != null) ? layoutResult.getNoteXSs(note) : note.getXPosSs();
        }

        return GraphicUtils.snapXToDevicePixel(g2, noteX);
    }

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
     * Computes the X offset for drawing a barline or repeat glyph
     * relative to the bar element's layout position.
     * Barlines are drawn at the origin (layout positions them correctly).
     * Repeats are centered using the glyph advance width.
     */
    private static double computeGlyphXSs(
        @NotNull Graphics2D g2,
        @NotNull SMuFLGlyph glyph,
        @NotNull NoteType noteType
    ) {
        if (noteType.isBarLine()) {
            return 0;
        }

        // Center repeat glyphs around the layout position
        var advanceSS = SMuFLMetadata.getInstance().getAdvanceWidth(glyph);
        double advance = (advanceSS != null) ? advanceSS : 0;
        double x = -advance / 2.0;

        return GraphicUtils.snapXToDevicePixel(g2, x);
    }
}
