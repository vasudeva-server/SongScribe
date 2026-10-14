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
import songscribe.dom.BarElement;
import songscribe.engraving.BarStroke;
import songscribe.engraving.Staff;
import songscribe.smufl.SMuFLGlyph;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

public final class BarRenderer implements ElementRenderer<BarElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Singleton instance
    private static final BarRenderer INSTANCE = new BarRenderer();

    private BarRenderer() {
    }

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
        BarElement element,
        Graphics2D g2
    ) {
        var noteX = frame.resolveElementXSs(element, invariants);

        try (var _ = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(noteX, invariants.getMiddleLineYSs());
            drawStrokes(g2, element.bar(), 0, 0);
        }
    }

    // ==========================================================================
    // Drawing Helpers
    // ==========================================================================

    /**
     * The one definition of a single barline, so one drawn from something other than an element
     * (a {@code CautionaryKeySignature}'s) cannot differ in thickness or height.
     *
     * @param g2 Graphics context, in staff-space units
     */
    public static void drawSingleBarLine(Graphics2D g2, double xSs, double middleLineYSs) {
        drawStrokes(g2, BarAppearance.SINGLE_BARLINE, xSs, middleLineYSs);
    }

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
     * @param g2 Graphics context (translated to middle line)
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
     * @param g2   Graphics context (translated to middle line)
     * @param ySs  Y coordinate of the bottom staff line, the glyph's origin
     */
    private static void drawRepeatDots(Graphics2D g2, double xSs, double ySs) {
        try (var _ = GraphicsState.save(g2, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.drawString(SMuFLGlyph.REPEAT_DOTS.asString(), (float) xSs, (float) ySs);
        }
    }

}
