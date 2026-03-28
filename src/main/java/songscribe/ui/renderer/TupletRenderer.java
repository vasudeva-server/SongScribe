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
import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import module java.desktop;



import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.Tuplet;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.util.GraphicUtils;

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

    private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();

    private static final EngravingDefaults ENGRAVING_DEFAULTS = METADATA.getEngravingDefaults();

    private static final BasicStroke LINE_STROKE = new BasicStroke(
        (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.tupletBracketThickness()),
        BasicStroke.CAP_SQUARE,
        BasicStroke.JOIN_MITER
    );

    // Scale factor for tuplet number glyphs (slightly smaller than standard notation size)
    private static final float TUPLET_NUMBER_SCALE = 0.9f;

    // Bravura font scaled down for tuplet numbers
    private static final Font TUPLET_FONT = MUSIC_FONT.deriveFont(
        MUSIC_FONT.getSize2D() * TUPLET_NUMBER_SCALE);

    // Tuplet digit glyphs indexed by value (0–9)
    private static final SMuFLGlyph[] TUPLET_GLYPHS = {
        SMuFLGlyph.TUPLET_0, SMuFLGlyph.TUPLET_1, SMuFLGlyph.TUPLET_2,
        SMuFLGlyph.TUPLET_3, SMuFLGlyph.TUPLET_4, SMuFLGlyph.TUPLET_5,
        SMuFLGlyph.TUPLET_6, SMuFLGlyph.TUPLET_7, SMuFLGlyph.TUPLET_8,
        SMuFLGlyph.TUPLET_9
    };

    // Padding between bracket arm and glyph edge
    private static final double GAP_PADDING_PX = 2.0;

    // Gap from center to left bracket arm end (half advance width + padding)
    private static final double LEFT_GAP_PX;

    // Gap from center to right bracket arm start (accounts for italic overhang)
    private static final double RIGHT_GAP_PX;

    static {
        double advancePx = StaffSpaces.toPixels(
            METADATA.requireAdvanceWidth(SMuFLGlyph.TUPLET_3)
        ) * TUPLET_NUMBER_SCALE;
        var bbox = METADATA.getBBox(SMuFLGlyph.TUPLET_3);
        double rightOverhang = bbox != null
            ? (StaffSpaces.toPixels(bbox.right()) - advancePx) * TUPLET_NUMBER_SCALE
            : 0;

        LEFT_GAP_PX = advancePx / 2.0 + GAP_PADDING_PX;
        RIGHT_GAP_PX = advancePx / 2.0 + Math.max(rightOverhang, 0) + GAP_PADDING_PX;
    }

    // Notehead visual edges relative to the glyph origin
    private static final double NOTEHEAD_LEFT_PX;
    private static final double NOTEHEAD_RIGHT_PX;

    // Down-stem noteheads are shifted left by half the stem width in NoteRenderer
    private static final double DOWN_STEM_NOTEHEAD_SHIFT_SS = LayoutStylesheet.STEM_WIDTH_SS / 2.0;

    static {
        var bbox = METADATA.requireBBox(SMuFLGlyph.NOTEHEAD_BLACK);
        NOTEHEAD_LEFT_PX = StaffSpaces.toPixels(bbox.left());
        NOTEHEAD_RIGHT_PX = StaffSpaces.toPixels(bbox.right());
    }

    // End cap length: ~0.5 staff spaces
    private static final double END_CAP_LENGTH_PX = StaffSpaces.toPixels(0.5);

    // Bracket clearance above beams: half beam thickness (to clear outer edge)
    // plus 1.0 staff space gap between beam and bracket
    private static final double BRACKET_CLEARANCE_PX =
        StaffSpaces.toPixels(ENGRAVING_DEFAULTS.beamThickness() / 2.0 + 1.0);

    // Horizontal offset from stem center so the bracket's inner edge
    // aligns with the stem's outer edge: half stem + half bracket
    private static final double BRACKET_X_OFFSET_PX = Math.ceil(
        StaffSpaces.toPixels(ENGRAVING_DEFAULTS.stemThickness()) / 2.0
            + LINE_STROKE.getLineWidth() / 2.0);

    // Minimum stem length — used as fallback when StemLayout is not available
    private static final double MIN_STEM_SS = LayoutStylesheet.STEM_LENGTH_SS;

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
    public static TupletRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        Tuplet element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var anchorNote = element.getAnchorElement();
        var endNote = element.getEndElement();

        if (anchorNote == null || endNote == null) {
            return;
        }

        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        int startIndex = element.getAnchorElementIndex();
        int endIndex = element.getEndElementIndex();

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
        Tuplet element,
        ElementRenderContext ctx
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
        return element.getVerticalPositionSs();
    }

    /**
     * Renders a tuplet bracket from interval data.
     */
    public void renderTuplet(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        int startIndex,
        int endIndex,
        int grade,
        double verticalAdjustment
    ) {
        double middleLineYSs = ctx.getMiddleLineYSs();
        var layoutResult = ctx.getLayoutResult();

        // Calculate if there's an odd or even number of notes
        boolean odd = ((endIndex - startIndex + 1) % 2) == 1;

        var firstElement = line.getElement(startIndex);
        var lastElement = line.getElement(endIndex);
        boolean isUpper = firstElement.isUpper();

        // Check if the entire tuplet range is beamed
        boolean allBeamed = line.getBeamings().findInterval(startIndex) != null
            && line.getBeamings().findInterval(endIndex) != null;

        // Top staff line: 4 positions above middle line
        double staffTopY = middleLineYSs - ScaleContext.getInstance().toPixels(4 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS);

        // Find the highest extent across ALL notes in the tuplet group
        double highestRefY = staffTopY;

        for (var i = startIndex; i <= endIndex; i++) {
            var element = line.getElement(i);
            double elementYSs = element.getStaffPosition() * LayoutStylesheet.STAFF_POSITION_OFFSET_SS;
            double refY;

            var stemLayout = layoutResult != null ? layoutResult.getStemLayout(element) : null;

            if (isUpper) {
                // Stems up: clear stem tops (which extend to beam for beamed notes)
                if (stemLayout != null) {
                    refY = middleLineYSs + ScaleContext.getInstance().toPixels(stemLayout.topYSs());
                } else {
                    refY = middleLineYSs + ScaleContext.getInstance().toPixels(elementYSs - MIN_STEM_SS);
                }
            } else {
                // Stems down: clear stem bottoms
                if (stemLayout != null) {
                    refY = middleLineYSs + ScaleContext.getInstance().toPixels(stemLayout.bottomYSs());
                } else {
                    refY = middleLineYSs + ScaleContext.getInstance().toPixels(elementYSs + MIN_STEM_SS);
                }
            }

            highestRefY = Math.min(highestRefY, refY);
        }

        // Horizontal bracket: use the highest extent, but never below staff top
        int bracketY = (int) (Math.min(highestRefY, staffTopY)
            - BRACKET_CLEARANCE_PX);

        // Apply vertical adjustment if any
        if (verticalAdjustment != 0) {
            bracketY += (int) verticalAdjustment;
        }

        // X positions and center
        int lx;
        int rx;
        int cx;

        if (isUpper && layoutResult != null) {
            // Stems up: align bracket with stem centers
            double firstStemXSs = stemXSs(firstElement, layoutResult);
            double lastStemXSs = stemXSs(lastElement, layoutResult);
            lx = (int) (firstStemXSs - BRACKET_X_OFFSET_PX);
            rx = (int) (lastStemXSs + BRACKET_X_OFFSET_PX);

            if (odd) {
                var centerNote = line.getElement(((endIndex - startIndex) / 2) + startIndex);
                cx = (int) stemXSs(centerNote, layoutResult);
            } else {
                var cn1 = line.getElement(((endIndex - startIndex) / 2) + startIndex);
                var cn2 = line.getElement(((endIndex - startIndex) / 2) + startIndex + 1);
                double cn1x = stemXSs(cn1, layoutResult);
                double cn2x = stemXSs(cn2, layoutResult);
                cx = (int) ((cn2x - cn1x) / 2 + cn1x);
            }
        } else {
            // Stems down: align bracket with notehead edges
            // (noteheads are shifted left by DOWN_STEM_NOTEHEAD_SHIFT in NoteRenderer)
            double noteheadShift = DOWN_STEM_NOTEHEAD_SHIFT_SS;
            lx = (int) (firstElement.getXPosSs() - noteheadShift + NOTEHEAD_LEFT_PX);
            rx = (int) (lastElement.getXPosSs() - noteheadShift + NOTEHEAD_RIGHT_PX);
            cx = (lx + rx) / 2;
        }

        // Snap positions to device pixels for crisp rendering
        double slx = GraphicUtils.snapXToDevicePixel(g2, lx);
        double sby = GraphicUtils.snapYToDevicePixel(g2, bracketY);
        double srx = GraphicUtils.snapXToDevicePixel(g2, rx);

        // Shift center rightward by half the italic overhang so the number
        // appears visually centered between the bracket endpoints
        double overhangCompensation = (RIGHT_GAP_PX - LEFT_GAP_PX) / 2.0;
        double scx = GraphicUtils.snapXToDevicePixel(g2, cx + overhangCompensation);

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE, FONT)) {
            g2.setColor(ELEMENT_COLOR);
            g2.setStroke(LINE_STROKE);

            if (allBeamed && isUpper) {
                // Beamed + stems up: number only (beam already provides visual grouping)
                drawTupletNumber(g2, grade, scx, sby);
            } else {
                // All other cases: full bracket with end caps pointing down toward notes
                double capDir = END_CAP_LENGTH_PX;

                // Left end cap
                g2.draw(new Line2D.Double(slx, sby, slx, sby + capDir));

                // Left bracket arm (from left end to gap)
                double gapLeftX = GraphicUtils.snapXToDevicePixel(g2, scx - LEFT_GAP_PX);
                g2.draw(new Line2D.Double(slx, sby, gapLeftX, sby));

                // Right bracket arm (from gap to right end)
                double gapRightX = GraphicUtils.snapXToDevicePixel(g2, scx + RIGHT_GAP_PX);
                g2.draw(new Line2D.Double(gapRightX, sby, srx, sby));

                // Right end cap
                g2.draw(new Line2D.Double(srx, sby, srx, sby + capDir));

                // Draw tuplet number
                drawTupletNumber(g2, grade, scx, sby);
            }
        }
    }

    /**
     * Renders tuplets from Line's interval data.
     */
    public void renderTupletsFromLine(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx
    ) {
        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            renderTuplet(g2, line, ctx, interval.getStart(), interval.getEnd(), interval.getGrade(), interval.getVerticalPositionSs());
        }
    }

    /**
     * Computes the stem center X position for a note in staff-space units.
     * <p>
     * Uses the note's layout X position plus the SMuFL stem anchor offset
     * for the note's type and stem direction.
     *
     * @param note         the note whose stem X to compute
     * @param layoutResult the layout result containing note positions
     * @return stem center X in staff spaces
     */
    private double stemXSs(StaffElement note, LayoutResult layoutResult) {
        double noteXSs = layoutResult.getElementXSs(note);
        double offsetSs = stemCenterXOffsetSs(note.getType(), note.isUpper());
        return noteXSs + offsetSs;
    }

    /**
     * Draws the tuplet number centered in the bracket gap using SMuFL glyphs.
     */
    private void drawTupletNumber(
        Graphics2D g2,
        int grade,
        double cx,
        double bracketY
    ) {
        double scale = TUPLET_NUMBER_SCALE;

        if (grade >= 0 && grade <= 9) {
            // Single digit
            var glyph = TUPLET_GLYPHS[grade];
            var advanceWidth = METADATA.getAdvanceWidth(glyph);
            var bbox = METADATA.getBBox(glyph);

            if (advanceWidth == null || bbox == null) {
                return;
            }

            double advancePx = StaffSpaces.toPixels(advanceWidth) * scale;
            double bboxTopPx = StaffSpaces.toPixels(bbox.top()) * scale;
            double bboxHeightPx = StaffSpaces.toPixels(bbox.height()) * scale;

            // Center horizontally on cx
            double x = GraphicUtils.snapXToDevicePixel(g2, cx - advancePx / 2.0);

            // Center vertically on the bracket line, nudged up 1px for optical balance
            double y = GraphicUtils.snapYToDevicePixel(g2, bracketY - bboxTopPx - bboxHeightPx / 2.0) - 1;

            drawTupletGlyph(g2, glyph, x, y);
        } else {
            // Multi-digit: draw tens then units
            int tens = grade / 10;
            int units = grade % 10;
            var tensGlyph = TUPLET_GLYPHS[tens];
            var unitsGlyph = TUPLET_GLYPHS[units];
            var tensAdvance = METADATA.getAdvanceWidth(tensGlyph);
            var unitsAdvance = METADATA.getAdvanceWidth(unitsGlyph);
            var tensBbox = METADATA.getBBox(tensGlyph);

            if (tensAdvance == null || unitsAdvance == null || tensBbox == null) {
                return;
            }

            double tensAdvancePx = StaffSpaces.toPixels(tensAdvance) * scale;
            double unitsAdvancePx = StaffSpaces.toPixels(unitsAdvance) * scale;
            double totalWidth = tensAdvancePx + unitsAdvancePx;
            double bboxTopPx = StaffSpaces.toPixels(tensBbox.top()) * scale;
            double bboxHeightPx = StaffSpaces.toPixels(tensBbox.height()) * scale;

            double tensX = GraphicUtils.snapXToDevicePixel(g2, cx - totalWidth / 2.0);
            // Nudged up 1px for optical balance
            double y = GraphicUtils.snapYToDevicePixel(g2, bracketY - bboxTopPx - bboxHeightPx / 2.0) - 1;

            drawTupletGlyph(g2, tensGlyph, tensX, y);
            drawTupletGlyph(g2, unitsGlyph, tensX + tensAdvancePx, y);
        }
    }

    /**
     * Draws a tuplet glyph at the scaled tuplet font size.
     */
    private void drawTupletGlyph(
        Graphics2D g2,
        SMuFLGlyph glyph,
        double x,
        double y
    ) {
        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(TUPLET_FONT);
            g2.drawString(glyph.asString(), (float) x, (float) y);
        }
    }

}
