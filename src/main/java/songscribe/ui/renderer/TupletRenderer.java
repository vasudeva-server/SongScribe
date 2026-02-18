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
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.Tuplet;
import songscribe.util.GraphicUtils;

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
    private static final Font TUPLET_FONT = BRAVURA_FONT.deriveFont(
        BRAVURA_FONT.getSize2D() * TUPLET_NUMBER_SCALE);

    // Tuplet digit glyphs indexed by value (0–9)
    private static final SMuFLGlyph[] TUPLET_GLYPHS = {
        SMuFLGlyph.TUPLET_0, SMuFLGlyph.TUPLET_1, SMuFLGlyph.TUPLET_2,
        SMuFLGlyph.TUPLET_3, SMuFLGlyph.TUPLET_4, SMuFLGlyph.TUPLET_5,
        SMuFLGlyph.TUPLET_6, SMuFLGlyph.TUPLET_7, SMuFLGlyph.TUPLET_8,
        SMuFLGlyph.TUPLET_9
    };

    // Padding between bracket arm and glyph edge
    private static final double GAP_PADDING = 2.0;

    // Gap from center to left bracket arm end (half advance width + padding)
    private static final double LEFT_GAP;

    // Gap from center to right bracket arm start (accounts for italic overhang)
    private static final double RIGHT_GAP;

    static {
        double advancePx = StaffSpaces.toPixels(METADATA.getAdvanceWidth(SMuFLGlyph.TUPLET_3))
            * TUPLET_NUMBER_SCALE;
        var bbox = METADATA.getBBox(SMuFLGlyph.TUPLET_3);
        double rightOverhang = bbox != null
            ? (StaffSpaces.toPixels(bbox.right()) - advancePx) * TUPLET_NUMBER_SCALE
            : 0;

        LEFT_GAP = advancePx / 2.0 + GAP_PADDING;
        RIGHT_GAP = advancePx / 2.0 + Math.max(rightOverhang, 0) + GAP_PADDING;
    }

    // Notehead visual edges relative to the glyph origin
    private static final double NOTEHEAD_LEFT;
    private static final double NOTEHEAD_RIGHT;

    // Down-stem noteheads are shifted left by half the stem width in NoteRenderer
    private static final double DOWN_STEM_NOTEHEAD_SHIFT = NoteRenderer.STEM_WIDTH / 2.0;

    static {
        var bbox = METADATA.getBBox(SMuFLGlyph.NOTEHEAD_BLACK);
        assert bbox != null;
        NOTEHEAD_LEFT = StaffSpaces.toPixels(bbox.left());
        NOTEHEAD_RIGHT = StaffSpaces.toPixels(bbox.right());
    }

    // End cap length: ~0.5 staff spaces
    private static final double END_CAP_LENGTH = StaffSpaces.toPixels(0.5);

    // Bracket clearance above beams: half beam thickness (to clear outer edge)
    // plus 1.0 staff space gap between beam and bracket
    private static final double BRACKET_CLEARANCE =
        StaffSpaces.toPixels(ENGRAVING_DEFAULTS.beamThickness() / 2.0 + 1.0);

    // Horizontal offset from stem center so the bracket's inner edge
    // aligns with the stem's outer edge: half stem + half bracket
    private static final double BRACKET_X_OFFSET = Math.ceil(
        StaffSpaces.toPixels(ENGRAVING_DEFAULTS.stemThickness()) / 2.0
            + LINE_STROKE.getLineWidth() / 2.0);

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
        var lastNote = line.getNote(endIndex);
        boolean isUpper = firstNote.isUpper();

        // Check if the entire tuplet range is beamed
        boolean allBeamed = line.getBeamings().findInterval(startIndex) != null
            && line.getBeamings().findInterval(endIndex) != null;

        // Top staff line: 4 positions above middle line
        double staffTopY = middleLineY - 4 * LayoutStylesheet.NOTE_Y_OFFSET;

        // Find the highest extent across ALL notes in the tuplet group
        double highestRefY = staffTopY;

        for (var i = startIndex; i <= endIndex; i++) {
            var note = line.getNote(i);
            double noteY = middleLineY + note.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET;
            double refY;

            if (isUpper) {
                // Stems up: clear stem tops (which extend to beam for beamed notes)
                refY = noteY + note.properties.stem.y2;
            } else {
                // Stems down: clear notehead tops (approx 1 staff space above note center)
                refY = noteY - LayoutStylesheet.NOTE_Y_OFFSET;
            }

            highestRefY = Math.min(highestRefY, refY);
        }

        // Horizontal bracket: use the highest extent, but never below staff top
        int bracketY = (int) (Math.min(highestRefY, staffTopY)
            - BRACKET_CLEARANCE);

        // Apply vertical adjustment if any
        if (verticalAdjustment != 0) {
            bracketY += verticalAdjustment;
        }

        // X positions and center
        int lx;
        int rx;
        int cx;

        if (isUpper) {
            // Stems up: align bracket with stems
            lx = (int) (firstNote.getXPos() + firstNote.properties.stem.x1 - BRACKET_X_OFFSET);
            rx = (int) (lastNote.getXPos() + lastNote.properties.stem.x1 + BRACKET_X_OFFSET);

            if (odd) {
                var centerNote = line.getNote(((endIndex - startIndex) / 2) + startIndex);
                cx = (int) (centerNote.getXPos() + centerNote.properties.stem.x1);
            } else {
                var cn1 = line.getNote(((endIndex - startIndex) / 2) + startIndex);
                var cn2 = line.getNote(((endIndex - startIndex) / 2) + startIndex + 1);
                int cn1x = (int) (cn1.getXPos() + cn1.properties.stem.x1);
                int cn2x = (int) (cn2.getXPos() + cn2.properties.stem.x1);
                cx = (cn2x - cn1x) / 2 + cn1x;
            }
        } else {
            // Stems down: align bracket with notehead edges
            // (noteheads are shifted left by DOWN_STEM_NOTEHEAD_SHIFT in NoteRenderer)
            double noteheadShift = DOWN_STEM_NOTEHEAD_SHIFT;
            lx = (int) (firstNote.getXPos() - noteheadShift + NOTEHEAD_LEFT);
            rx = (int) (lastNote.getXPos() - noteheadShift + NOTEHEAD_RIGHT);
            cx = (lx + rx) / 2;
        }

        // Snap positions to device pixels for crisp rendering
        double slx = GraphicUtils.snapXToDevicePixel(g2, lx);
        double sby = GraphicUtils.snapYToDevicePixel(g2, bracketY);
        double srx = GraphicUtils.snapXToDevicePixel(g2, rx);

        // Shift center rightward by half the italic overhang so the number
        // appears visually centered between the bracket endpoints
        double overhangCompensation = (RIGHT_GAP - LEFT_GAP) / 2.0;
        double scx = GraphicUtils.snapXToDevicePixel(g2, cx + overhangCompensation);

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE, FONT)) {
            g2.setColor(NOTE_COLOR);
            g2.setStroke(LINE_STROKE);

            if (allBeamed && isUpper) {
                // Beamed + stems up: number only (beam already provides visual grouping)
                drawTupletNumber(g2, grade, scx, sby);
            } else {
                // All other cases: full bracket with end caps pointing down toward notes
                double capDir = END_CAP_LENGTH;

                // Left end cap
                g2.draw(new Line2D.Double(slx, sby, slx, sby + capDir));

                // Left bracket arm (from left end to gap)
                double gapLeftX = GraphicUtils.snapXToDevicePixel(g2, scx - LEFT_GAP);
                g2.draw(new Line2D.Double(slx, sby, gapLeftX, sby));

                // Right bracket arm (from gap to right end)
                double gapRightX = GraphicUtils.snapXToDevicePixel(g2, scx + RIGHT_GAP);
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
     * Draws the tuplet number centered in the bracket gap using SMuFL glyphs.
     */
    private void drawTupletNumber(
        @NotNull Graphics2D g2,
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
        @NotNull Graphics2D g2,
        @NotNull SMuFLGlyph glyph,
        double x,
        double y
    ) {
        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(TUPLET_FONT);
            g2.drawString(glyph.asString(), (float) x, (float) y);
        }
    }

}
