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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import module java.desktop;


import songscribe.music.DynamicsInterval;
import songscribe.music.IntervalSet;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.ScaleContext;

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

    private static final EngravingDefaults ENGRAVING_DEFAULTS =
        SMuFLMetadata.getInstance().getEngravingDefaults();

    private static final BasicStroke LINE_STROKE = new BasicStroke(
        (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.hairpinThickness()),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Crotchet width (from FughettaRenderer)
    private static final double CROTCHET_WIDTH_PX = BaseElementRenderer.FONT_SIZE / 3.6056337d;

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
    public static DynamicsRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        LineElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        if (element instanceof Crescendo crescendo) {
            renderCrescendo(crescendo, g2, ctx);
        } else if (element instanceof Diminuendo diminuendo) {
            renderDiminuendo(diminuendo, g2, ctx);
        }
    }

    private void renderCrescendo(
        Crescendo element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorElement();
        var endNote = element.getEndElement();

        if (anchorNote == null || endNote == null) {
            return;
        }

        int yShift = getEffectiveDynamicsYShiftPx(element, ctx);
        renderHairpin(g2, ctx, anchorNote, endNote, true, 0, 0, yShift);
    }

    private void renderDiminuendo(
        Diminuendo element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorElement();
        var endNote = element.getEndElement();

        if (anchorNote == null || endNote == null) {
            return;
        }

        int yShift = getEffectiveDynamicsYShiftPx(element, ctx);
        renderHairpin(g2, ctx, anchorNote, endNote, false, 0, 0, yShift);
    }

    /**
     * Gets the Y shift for a dynamics element from layout result.
     */
    private int getEffectiveDynamicsYShiftPx(
        LineElement element,
        ElementRenderContext ctx
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
        double middleLineYSs = ctx.getMiddleLineYSs();
        double defaultY = middleLineYSs - ScaleContext.getInstance().toRoundedPixels(6 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
        return (int) (bounds.getTop() - defaultY);
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
        Graphics2D g2,
        ElementRenderContext ctx,
        StaffElement startNote,
        StaffElement endNote,
        boolean isCrescendo,
        double x1Shift,
        double x2Shift,
        double yShift
    ) {
        double middleLineYSs = ctx.getMiddleLineYSs();

        int x1 = (int) (startNote.getXPosSs() + x1Shift);
        int x2 = (int) (endNote.getXPosSs() + CROTCHET_WIDTH_PX + x2Shift);

        // Y positions above the staff: center 1 staff space above top staff line
        // (top staff line = middleLineYSs - 4*NOTE_Y_OFFSET; 1 space = 2*NOTE_Y_OFFSET)
        int yTop = (int) (middleLineYSs - ScaleContext.getInstance().toPixels(7 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS) + yShift);
        int yBottom = (int) (middleLineYSs - ScaleContext.getInstance().toPixels(5 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS) + yShift);
        int yMiddle = (int) (middleLineYSs - ScaleContext.getInstance().toPixels(6 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS) + yShift);

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE)) {
            g2.setColor(ELEMENT_COLOR);
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
    }

    /**
     * Renders crescendos from Line's interval data.
     */
    public void renderCrescendosFromLine(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx
    ) {
        renderDynamicsFromInterval(g2, line, ctx, line.getCrescendos(), true);
    }

    /**
     * Renders diminuendos from Line's interval data.
     */
    public void renderDiminuendosFromLine(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx
    ) {
        renderDynamicsFromInterval(g2, line, ctx, line.getDiminuendos(), false);
    }

    private void renderDynamicsFromInterval(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        IntervalSet<DynamicsInterval> dynamics,
        boolean isCrescendo
    ) {
        for (var iter = dynamics.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getElement(interval.getStart());
            var endNote = line.getElement(interval.getEnd());

            renderHairpin(g2, ctx, startNote, endNote, isCrescendo,
                interval.getX1ShiftSs(), interval.getX2ShiftSs(), interval.getYShiftSs());
        }
    }
}
