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

import module java.desktop;

import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;

/**
 * Renders bar lines and repeat signs using drawn primitives.
 * <p>
 * Per the SMuFL spec, scoring programs should draw barlines using primitives
 * rather than using the barline glyphs. Repeat dots still use the Bravura
 * {@link SMuFLGlyph#REPEAT_DOTS} glyph.
 * <p>
 * Handles:
 * <ul>
 *   <li>Single bar lines</li>
 *   <li>Double bar lines</li>
 *   <li>Final double bar lines (thin + thick)</li>
 *   <li>Left repeat</li>
 *   <li>Right repeat</li>
 *   <li>Left-right repeat</li>
 * </ul>
 */
public final class BarRenderer implements ElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Half the staff height: distance from middle line to top/bottom staff line. */
    private static final double STAFF_HALF_HEIGHT_SS = 2.0;

    /**
     * Y coordinate of the bottom staff line relative to the middle line.
     * Used as the origin for the repeat dots glyph (SMuFL barline glyph convention).
     */
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
    public static BarRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var noteType = element.getType();

        if (!noteType.isBarLine() && !noteType.isRepeat()) {
            return;
        }

        var noteX = resolveBarXSs(g2, element, invariants, frame);

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(noteX, invariants.getMiddleLineYSs());
            renderBarLineOrRepeat(g2, noteType, invariants);
        }
    }

    /**
     * Renders a bar line or repeat sign using drawn primitives.
     */
    private void renderBarLineOrRepeat(
        Graphics2D g2,
        ElementType noteType,
        LineInvariants invariants
    ) {
        var lt = invariants.getLineThickness();
        var thin = lt.thinBarlineSs();
        var thick = lt.thickBarlineSs();
        var sep = lt.barlineSeparationSs();

        var topY = -STAFF_HALF_HEIGHT_SS;
        var bottomY = STAFF_HALF_HEIGHT_SS;

        switch (noteType) {
            case SINGLE_BARLINE -> drawBar(g2, 0, thin, topY, bottomY);

            case DOUBLE_BARLINE -> {
                drawBar(g2, 0, thin, topY, bottomY);
                drawBar(g2, thin + sep, thin, topY, bottomY);
            }

            case FINAL_DOUBLE_BARLINE -> {
                drawBar(g2, 0, thin, topY, bottomY);
                drawBar(g2, thin + sep, thick, topY, bottomY);
            }

            case REPEAT_LEFT -> {
                // thick | sep | thin | sep | dots
                double x = 0;
                drawBar(g2, x, thick, topY, bottomY);
                x += thick + sep;
                drawBar(g2, x, thin, topY, bottomY);
                x += thin + sep;
                drawRepeatDots(g2, x);
            }

            case REPEAT_RIGHT -> drawRightRepeat(g2, 0, thin, thick, topY, bottomY, sep);

            case REPEAT_LEFT_RIGHT -> {
                // dots | sep | thin | sep | thick | sep | thin | sep | dots
                var x = drawRightRepeat(g2, 0, thin, thick, topY, bottomY, sep);
                x += sep;
                drawBar(g2, x, thin, topY, bottomY);
                x += thin + sep;
                drawRepeatDots(g2, x);
            }

            default -> {
                // Not a barline or repeat type
            }
        }
    }

    // ==========================================================================
    // Drawing Helpers
    // ==========================================================================

    // dots | sep | thin | sep | thick; returns x after the thick bar
    static double drawRightRepeat(
        Graphics2D g2,
        double x,
        double thin,
        double thick,
        double topY,
        double bottomY,
        double sep
    ) {
        drawRepeatDots(g2, x);
        x += Engraving.REPEAT_DOTS_ADVANCE_WIDTH_SS + sep;
        drawBar(g2, x, thin, topY, bottomY);
        x += thin + sep;
        drawBar(g2, x, thick, topY, bottomY);
        return x + thick;
    }

    /**
     * Draws a single barline as a filled rectangle.
     *
     * @param g2      Graphics context (translated to middle line)
     * @param leftX   Left edge X coordinate
     * @param width   Barline width in staff spaces
     * @param topY    Top Y coordinate
     * @param bottomY Bottom Y coordinate
     */
    private static void drawBar(
        Graphics2D g2,
        double leftX,
        double width,
        double topY,
        double bottomY
    ) {
        g2.fill(new Rectangle2D.Double(leftX, topY, width, bottomY - topY));
    }

    /**
     * Draws repeat dots using the Bravura {@link SMuFLGlyph#REPEAT_DOTS} glyph.
     * <p>
     * The glyph origin is at the bottom staff line (SMuFL barline convention),
     * producing two dots in the inner staff spaces.
     *
     * @param g2  Graphics context (translated to middle line)
     * @param xSs Left edge X coordinate for the dots in staff spaces
     */
    private static void drawRepeatDots(Graphics2D g2, double xSs) {
        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.drawString(
                SMuFLGlyph.REPEAT_DOTS.asString(),
                (float) xSs,
                (float) BOTTOM_STAFF_LINE_Y_SS);
        }
    }

    // ==========================================================================
    // Coordinate Helpers
    // ==========================================================================

    /**
     * Resolves the X coordinate for a barline/repeat from the layout result,
     * snapped to device pixels for crisp rendering.
     */
    private static double resolveBarXSs(
        Graphics2D g2,
        StaffElement note,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        double noteX;

        if (frame.hasOverrideElementX()) {
            noteX = frame.overrideElementXSs();
        } else {
            noteX = invariants.getLayoutResult().getElementXSs(note);
        }

        return noteX;
    }
}
