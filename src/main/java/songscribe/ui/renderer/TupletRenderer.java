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
import java.awt.geom.*;

import org.jetbrains.annotations.NotNull;

import songscribe.data.TupletIntervalData;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.Tuplet;

import static songscribe.ui.renderer.GraphicsState.Property.*;

/**
 * Renders tuplet brackets with numbers.
 * <p>
 * Tuplets are rendered as curved brackets with a number (e.g., "3" for triplets)
 * in the middle of the bracket.
 */
public class TupletRenderer extends BaseElementRenderer<Tuplet> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final BasicStroke LINE_STROKE = new BasicStroke(
        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );

    // Crotchet width (from FughettaRenderer)
    private static final double CROTCHET_WIDTH = BaseElementRenderer.NOTE_FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final TupletRenderer INSTANCE = new TupletRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private TupletRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull TupletRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Tuplet element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorNote();
        var endNote = element.getEndNote();

        if (anchorNote == null || endNote == null) {
            return;
        }

        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        int startIndex = element.getAnchorNoteIndex();
        int endIndex = element.getEndNoteIndex();

        if (startIndex < 0 || endIndex < 0) {
            return;
        }

        int verticalPos = getEffectiveTupletVerticalPos(element, ctx);
        renderTuplet(g2, line, ctx, startIndex, endIndex, element.getGrade(), verticalPos);
    }

    /**
     * Gets the vertical position for a tuplet from layout result.
     */
    private int getEffectiveTupletVerticalPos(
        @NotNull Tuplet element,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.getBounds(element);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for Tuplet element");
        }

        // The layout result provides absolute Y, but renderTuplet uses a vertical adjustment
        // TODO: Refactor renderTuplet to use absolute Y from layout result
        return element.getVerticalPosition();
    }

    /**
     * Renders a tuplet bracket from interval data.
     */
    public void renderTuplet(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int startIndex,
        int endIndex,
        int grade,
        int verticalAdjustment
    ) {
        int middleLineY = ctx.getMiddleLineY();

        // Calculate if there's an odd or even number of notes
        boolean odd = ((endIndex - startIndex + 1) % 2) == 1;

        var firstNote = line.getNote(startIndex);
        int upper = firstNote.isUpper() ? -1 : 0;

        // Calculate left bracket position
        int lx = firstNote.getXPos() + (int) CROTCHET_WIDTH;
        int ly = (middleLineY + (int) (firstNote.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET) -
            Note.HOT_SPOT.y) + (upper * (int) firstNote.properties.lengthening);
        ly -= 5;

        // Calculate center position
        int cx;

        if (odd) {
            var centerNote = line.getNote(((endIndex - startIndex) / 2) + startIndex);
            cx = centerNote.getXPos() + (int) CROTCHET_WIDTH;
        } else {
            var cn1 = line.getNote(((endIndex - startIndex) / 2) + startIndex);
            var cn2 = line.getNote(((endIndex - startIndex) / 2) + startIndex + 1);
            cx = ((cn2.getXPos() - cn1.getXPos()) / 2) + cn1.getXPos() + (int) CROTCHET_WIDTH;
        }

        // Calculate right bracket position
        var lastNote = line.getNote(endIndex);
        int rx = lastNote.getXPos() + (int) CROTCHET_WIDTH;
        int ry = (middleLineY + (int) (lastNote.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET) -
            Note.HOT_SPOT.y) + (upper * (int) lastNote.properties.lengthening);
        ry -= 5;

        // Adjust for lower stem notes
        if (!firstNote.isUpper()) {
            lx -= (int) CROTCHET_WIDTH / 2;
            ly += Note.HOT_SPOT.y - 3;
            cx -= (int) CROTCHET_WIDTH / 2;
            rx -= (int) CROTCHET_WIDTH / 2;
            ry += Note.HOT_SPOT.y - 3;
        }

        // Apply vertical adjustment if any
        if (verticalAdjustment != 0) {
            ly += verticalAdjustment;
            ry += verticalAdjustment;
        }

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE, FONT)) {
            g2.setColor(NOTE_COLOR);
            g2.setStroke(LINE_STROKE);

            // Calculate slope for bracket
            var tc = new TupletCalc(lx, ly, rx, ry);

            // Draw left curved bracket segment
            g2.draw(new QuadCurve2D.Float(
                lx, ly,
                ((float) (cx - lx) / 4) + lx, tc.getRate(((cx - lx) / 4) + lx) - 10,
                cx - 7, tc.getRate(cx - 7) - 8
            ));

            // Draw right curved bracket segment
            g2.draw(new QuadCurve2D.Float(
                cx + 7, tc.getRate(cx + 7) - 8,
                (((float) (rx - cx) * 3) / 4) + cx, tc.getRate((((rx - cx) * 3) / 4) + cx) - 10,
                rx, ry
            ));

            // Draw tuplet number
            g2.setFont(BaseElementRenderer.TUPLET_FONT);
            g2.drawString(Integer.toString(grade), cx - 3, tc.getRate(cx - 3) - 5);
        }
    }

    /**
     * Renders tuplets from Line's interval data.
     */
    public void renderTupletsFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            int grade = TupletIntervalData.getGrade(interval);
            int verticalPos = TupletIntervalData.isVerticalAdjusted(interval)
                ? TupletIntervalData.getVerticalPosition(interval)
                : 0;

            renderTuplet(g2, line, ctx, interval.getStart(), interval.getEnd(), grade, verticalPos);
        }
    }

    /**
     * Helper class to calculate the slope of the tuplet bracket.
     */
    private static class TupletCalc {

        private final float m;
        private final float lx;
        private final float ly;

        TupletCalc(int lx, int ly, int rx, int ry) {
            this.lx = lx;
            this.ly = ly;
            this.m = (float) (ry - ly) / (rx - lx);
        }

        float getRate(float x) {
            return m * (x - lx) + ly;
        }
    }
}
