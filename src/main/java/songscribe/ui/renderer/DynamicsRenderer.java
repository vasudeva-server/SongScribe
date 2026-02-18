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

import java.awt.BasicStroke;
import java.awt.Graphics2D;

import org.jetbrains.annotations.NotNull;

import songscribe.data.DynamicsIntervalData;
import songscribe.data.Interval;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;

/**
 * Renders crescendo and diminuendo hairpins.
 * <p>
 * Crescendo: opens from left to right (gets louder)
 * Diminuendo: opens from right to left (gets softer)
 */
public class DynamicsRenderer extends BaseElementRenderer<LineElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final BasicStroke LINE_STROKE = new BasicStroke(
        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );

    // Crotchet width (from FughettaRenderer)
    private static final double CROTCHET_WIDTH = BaseElementRenderer.NOTE_FONT_SIZE / 3.6056337d;

    // Singleton instance
    private static final DynamicsRenderer INSTANCE = new DynamicsRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private DynamicsRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull DynamicsRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull LineElement element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        if (element instanceof Crescendo crescendo) {
            renderCrescendo(crescendo, g2, ctx);
        } else if (element instanceof Diminuendo diminuendo) {
            renderDiminuendo(diminuendo, g2, ctx);
        }
    }

    private void renderCrescendo(
        @NotNull Crescendo element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorNote();
        var endNote = element.getEndNote();

        if (anchorNote == null || endNote == null) {
            return;
        }

        int yShift = getEffectiveDynamicsYShift(element, ctx);
        renderHairpin(g2, ctx, anchorNote, endNote, true, 0, 0, yShift);
    }

    private void renderDiminuendo(
        @NotNull Diminuendo element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorNote();
        var endNote = element.getEndNote();

        if (anchorNote == null || endNote == null) {
            return;
        }

        int yShift = getEffectiveDynamicsYShift(element, ctx);
        renderHairpin(g2, ctx, anchorNote, endNote, false, 0, 0, yShift);
    }

    /**
     * Gets the Y shift for a dynamics element from layout result.
     */
    private int getEffectiveDynamicsYShift(
        @NotNull LineElement element,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult == null) {
            throw new IllegalStateException("Layout result must be available for rendering");
        }

        var bounds = layoutResult.getBounds(element);

        if (bounds == null) {
            throw new IllegalStateException("No bounds found for dynamics element");
        }

        // Calculate shift from default position
        int middleLineY = ctx.getMiddleLineY();
        int defaultY = middleLineY + (int) (6 * LayoutStylesheet.NOTE_Y_OFFSET);
        return (int) bounds.getTop() - defaultY;
    }

    /**
     * Renders a hairpin (crescendo or diminuendo).
     *
     * @param g2           Graphics context
     * @param ctx          Render context
     * @param startNote    The starting note
     * @param endNote      The ending note
     * @param isCrescendo  True for crescendo, false for diminuendo
     * @param x1Shift      Horizontal shift for start
     * @param x2Shift      Horizontal shift for end
     * @param yShift       Vertical shift
     */
    public void renderHairpin(
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx,
        @NotNull Note startNote,
        @NotNull Note endNote,
        boolean isCrescendo,
        int x1Shift,
        int x2Shift,
        int yShift
    ) {
        int middleLineY = ctx.getMiddleLineY();

        int x1 = startNote.getXPos() + x1Shift;
        int x2 = endNote.getXPos() + (int) CROTCHET_WIDTH + x2Shift;

        // Calculate Y positions (below the staff, around y position 5-7)
        int yTop = middleLineY + (int) ((isCrescendo ? 5 : 6) * LayoutStylesheet.NOTE_Y_OFFSET) + yShift;
        int yBottom = middleLineY + (int) ((isCrescendo ? 7 : 6) * LayoutStylesheet.NOTE_Y_OFFSET) + yShift;
        int yMiddle = middleLineY + (int) (6 * LayoutStylesheet.NOTE_Y_OFFSET) + yShift;

        g2.setColor(NOTE_COLOR);
        g2.setStroke(LINE_STROKE);

        if (isCrescendo) {
            // Crescendo: point on left, open on right
            g2.drawLine(x1, yMiddle, x2, yTop);
            g2.drawLine(x1, yMiddle, x2, yBottom);
        } else {
            // Diminuendo: open on left, point on right
            g2.drawLine(x1, yTop, x2, yMiddle);
            g2.drawLine(x1, yBottom, x2, yMiddle);
        }
    }

    /**
     * Renders crescendos from Line's interval data.
     */
    public void renderCrescendosFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        renderDynamicsFromInterval(g2, line, ctx, line.getCrescendos(), true);
    }

    /**
     * Renders diminuendos from Line's interval data.
     */
    public void renderDiminuendosFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        renderDynamicsFromInterval(g2, line, ctx, line.getDiminuendos(), false);
    }

    private void renderDynamicsFromInterval(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        @NotNull songscribe.data.IntervalSet dynamics,
        boolean isCrescendo
    ) {
        for (var iter = dynamics.listIterator(); iter.hasNext(); ) {
            Interval interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());

            int x1Shift = DynamicsIntervalData.getX1Shift(interval);
            int x2Shift = DynamicsIntervalData.getX2Shift(interval);
            int yShift = DynamicsIntervalData.getYShift(interval);

            renderHairpin(g2, ctx, startNote, endNote, isCrescendo, x1Shift, x2Shift, yShift);
        }
    }
}
