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
import java.awt.geom.Rectangle2D;

import songscribe.dom.BarAppearance;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.engraving.BarStroke;
import songscribe.engraving.Staff;
import songscribe.smufl.SMuFLGlyph;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

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

        var noteX = frame.resolveElementXSs(element, invariants);

        try (var _ = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(noteX, invariants.getMiddleLineYSs());
            renderBarLineOrRepeat(g2, noteType, invariants);
        }
    }

    /**
     * Renders a bar line or repeat sign by drawing its {@link BarAppearance}'s strokes,
     * left to right, starting at the element's own origin.
     */
    private void renderBarLineOrRepeat(
        Graphics2D g2,
        ElementType noteType,
        LineInvariants invariants
    ) {
        if (!(noteType.appearance() instanceof BarAppearance barAppearance)) {
            return;
        }

        drawStrokes(g2, barAppearance, 0, 0);
    }

    // ==========================================================================
    // Drawing Helpers
    // ==========================================================================

    /**
     * Draws a thin single barline spanning the staff, with its left edge at {@code xSs}.
     *
     * <p>This is the one definition of what a single barline looks like, so a barline drawn from
     * something other than an element — the barline a {@code CautionaryKeySignature} draws at the
     * end of a line — cannot come out a different thickness or height from an element's.
     *
     * @param g2            Graphics context, in staff-space units
     * @param xSs           Left edge X coordinate of the barline
     * @param middleLineYSs Y coordinate of the staff's middle line, which the bar is centered on
     */
    public static void drawSingleBarLine(Graphics2D g2, double xSs, double middleLineYSs) {
        drawStrokes(g2, (BarAppearance) ElementType.SINGLE_BARLINE.appearance(), xSs, middleLineYSs);
    }

    /**
     * Draws a barline or repeat sign's strokes left to right, starting at {@code originXSs}
     * and centered vertically on {@code middleLineYSs}.
     *
     * @param g2            Graphics context, in staff-space units
     * @param appearance    the strokes to draw, left to right
     * @param originXSs     X coordinate of the left edge of the first stroke
     * @param middleLineYSs Y coordinate of the staff's middle line, which the strokes are
     *                      centered on
     */
    private static void drawStrokes(
        Graphics2D g2,
        BarAppearance appearance,
        double originXSs,
        double middleLineYSs
    ) {
        var topY = middleLineYSs - Staff.STAFF_HALF_SS;
        var bottomY = middleLineYSs + Staff.STAFF_HALF_SS;
        var x = originXSs;

        for (var stroke : appearance.strokes()) {
            switch (stroke) {
                case THIN, THICK -> drawBar(g2, x, stroke.widthSs(), topY, bottomY);
                case DOTS -> drawRepeatDots(g2, x, bottomY);
            }

            x += stroke.widthSs() + BarStroke.SEPARATION_SS;
        }
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
     * @param g2   Graphics context (translated to middle line)
     * @param xSs  Left edge X coordinate for the dots in staff spaces
     * @param ySs  Y coordinate of the bottom staff line, the glyph's origin
     */
    private static void drawRepeatDots(Graphics2D g2, double xSs, double ySs) {
        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.drawString(SMuFLGlyph.REPEAT_DOTS.asString(), (float) xSs, (float) ySs);
        }
    }

}
