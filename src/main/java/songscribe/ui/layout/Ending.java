/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.layout;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;
import songscribe.ui.renderer.BaseElementRenderer;
import songscribe.ui.renderer.LineThickness;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 */
public class Ending extends RangeElement {

    /** Scale of volta label font relative to standard music font size. */
    private static final float LABEL_FONT_SCALE = 0.6f;

    /** Font for volta bracket labels. */
    public static final Font ENDING_FONT = MyFontUtils.getLocalFont(
        "emmentaler-16.otf", BaseElementRenderer.FONT_SIZE * LABEL_FONT_SCALE);

    /** Horizontal inset of label from left arm of bracket. */
    public static final double LABEL_X_INSET_SS = 0.9;

    /** Vertical gap between the bracket's horizontal line and the top of the label. */
    public static final double LABEL_Y_OFFSET_SS = 0.5;

    /** Cached visual bounds for the "1." label. */
    private static final Rectangle2D LABEL_1_BOUNDS_SS =
        ENDING_FONT.createGlyphVector(GraphicUtils.LAYOUT_FRC, "1.").getVisualBounds();

    /** Cached visual bounds for the "2." label. */
    private static final Rectangle2D LABEL_2_BOUNDS_SS =
        ENDING_FONT.createGlyphVector(GraphicUtils.LAYOUT_FRC, "2.").getVisualBounds();

    /**
     * The type of ending (first or second).
     */
    public enum Type {
        FIRST,
        SECOND
    }

    /**
     * The x-range and properties of a single visual bracket (first or second ending).
     *
     * @param x1Ss             left edge X in staff spaces
     * @param x2Ss             right edge X in staff spaces
     * @param number           bracket number (1 or 2)
     * @param hasClosingStroke whether the bracket has a right vertical tick
     */
    public record BracketRange(
        double x1Ss, double x2Ss, int number, boolean hasClosingStroke
    ) {
        /** Returns the width of this bracket in staff spaces. */
        public double widthSs() {
            return x2Ss - x1Ss;
        }

        /** Returns the label text for this bracket (e.g. "1." or "2."). */
        public String label() {
            return number + ".";
        }
    }

    private Type type = Type.FIRST;
    private int yPositionSs = 0;
    private int repeatSplitIndex = -1;
    private List<BracketRange> bracketRanges = List.of();

    /**
     * Creates an ending bracket.
     *
     * @param anchorElement The first element of the ending
     * @param endElement    The last element of the ending
     * @param type       Whether this is a first or second ending
     */
    public Ending(StaffElement anchorElement, StaffElement endElement, Type type) {
        super(anchorElement, endElement);
        this.type = type;
    }

    /**
     * Returns the ending type (first or second).
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the ending type.
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Returns the user-adjustable Y offset for this ending bracket.
     */
    public int getYPositionSs() {
        return yPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this ending bracket.
     */
    public void setYPositionSs(int yPositionSs) {
        this.yPositionSs = yPositionSs;
    }

    /**
     * Returns the label text for this ending ("1." or "2.").
     */
    public String getLabel() {
        return type == Type.FIRST ? "1." : "2.";
    }

    /**
     * Returns the element index of the REPEAT_RIGHT barline that separates the
     * first and second endings, or -1 if no repeat was found.
     */
    public int getRepeatSplitIndex() {
        return repeatSplitIndex;
    }

    /**
     * Returns the bracket ranges computed during layout.
     */
    public List<BracketRange> getBracketRanges() {
        return bracketRanges;
    }

    /**
     * Computes and stores the bracket ranges for this ending.
     * <p>
     * Determines where each visual bracket starts and ends by examining the
     * element types (barlines, repeats) in the line. The logic mirrors
     * {@code EndingRenderer.renderEndings()} to ensure stacking and rendering
     * use identical positions.
     *
     * @param line    the line containing this ending
     * @param elementXSs function that returns the X position of an element in staff spaces
     * @param lt      line thickness metrics
     * @return the computed bracket ranges (also stored on this Ending)
     */
    public List<BracketRange> computeBracketRanges(
        Line line,
        ToDoubleFunction<? super StaffElement> elementXSs,
        LineThickness lt
    ) {
        var start = getAnchorElementIndex();
        var end = getEndElementIndex();

        if (start < 0 || end < 0 || start >= line.elementCount()
            || end >= line.elementCount()) {
            bracketRanges = List.of();
            return bracketRanges;
        }

        // Find the REPEAT_RIGHT that separates first and second endings
        repeatSplitIndex = IntStream.rangeClosed(start, end)
            .filter(i -> line.getElement(i).getType() == ElementType.REPEAT_RIGHT)
            .findFirst()
            .orElse(-1);

        var startElement = line.getElement(start);

        // Adjust start leftward if previous element is a barline
        if (start > 0) {
            var previousElement = line.getElement(start - 1);

            if (previousElement.getType() == ElementType.SINGLE_BARLINE) {
                --start;
                startElement = previousElement;
            }
        }

        var endElement = line.getElement(end);
        var ranges = new ArrayList<BracketRange>(2);
        double repeatX = 0;

        // First bracket (before repeat, or entire span if no repeat)
        if (start < repeatSplitIndex || repeatSplitIndex == -1) {
            double x1 = elementXSs.applyAsDouble(startElement);

            // Align with barline center, or go halfway to previous element
            if (startElement.getType() == ElementType.SINGLE_BARLINE) {
                x1 += lt.thinBarlineSs() / 2;
            }
            else if (start > 0) {
                var prevElement = line.getElement(start - 1);
                double prevX = elementXSs.applyAsDouble(prevElement)
                    + Engraving.NOTE_HEAD_WIDTH_SS;
                x1 -= (x1 - prevX) / 2.0;
            }

            double x2;

            if (repeatSplitIndex != -1) {
                double repeatElementX = elementXSs.applyAsDouble(
                    line.getElement(repeatSplitIndex));
                x2 = repeatElementX
                    + lt.repeatRightThinBarlineCenterXSs();
                repeatX = repeatElementX
                    + lt.repeatRightAfterThickXSs()
                    - lt.voltaBracketSs() / 2;
            }
            else {
                x2 = elementXSs.applyAsDouble(endElement);

                if (end + 1 < line.elementCount()) {
                    double nextX = elementXSs.applyAsDouble(
                        line.getElement(end + 1));
                    x2 += (nextX - x2) / 2.0;
                }
                else {
                    x2 += Engraving.NOTE_HEAD_WIDTH_SS;
                }
            }

            ranges.add(new BracketRange(x1, x2, 1, true));
        }

        // Second bracket (after repeat)
        if (repeatSplitIndex != -1 && end > repeatSplitIndex) {
            double x2 = elementXSs.applyAsDouble(endElement);
            var endType = endElement.getType();

            // Extend to the next barline/repeat if end element is not one
            if (!endType.isBarLine() && !endType.isRepeat()
                && end + 1 < line.elementCount()) {
                var nextElement = line.getElement(end + 1);
                var nextType = nextElement.getType();

                if (nextType.isBarLine() || nextType.isRepeat()) {
                    endType = nextType;
                    x2 = elementXSs.applyAsDouble(nextElement);
                }
            }

            boolean hasClosingStroke;

            switch (endType) {
                case REPEAT_RIGHT, REPEAT_LEFT_RIGHT -> {
                    x2 += lt.repeatRightThinBarlineCenterXSs();
                    hasClosingStroke = true;
                }
                case FINAL_DOUBLE_BARLINE -> {
                    x2 += lt.thinBarlineSs() / 2;
                    hasClosingStroke = true;
                }
                case SINGLE_BARLINE, DOUBLE_BARLINE -> {
                    x2 += lt.thinBarlineSs() / 2;
                    hasClosingStroke = false;
                }
                case REPEAT_LEFT -> {
                    hasClosingStroke = false;
                }
                default -> {
                    if (end + 1 < line.elementCount()) {
                        var nextElement = line.getElement(end + 1);
                        x2 += (elementXSs.applyAsDouble(nextElement) - x2) / 2.0;
                    }
                    else {
                        x2 += Engraving.NOTE_HEAD_WIDTH_SS;
                    }

                    hasClosingStroke = false;
                }
            }

            ranges.add(new BracketRange(repeatX, x2, 2, hasClosingStroke));
        }

        bracketRanges = List.copyOf(ranges);
        return bracketRanges;
    }

    /**
     * Returns the height of the volta bracket in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return LayoutStylesheet.VOLTA_TICK_HEIGHT_SS;
    }

    /**
     * Returns the horizontal span width for collision detection.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(Engraving.NOTE_HEAD_WIDTH_SS, endXSs - anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS);
    }

    /**
     * Returns the cached visual bounds of the ending label text in staff-space units.
     *
     * @param number the bracket number (1 or 2)
     * @return visual bounds rectangle in staff-space units
     */
    static Rectangle2D labelBoundsSs(int number) {
        return number == 1 ? LABEL_1_BOUNDS_SS : LABEL_2_BOUNDS_SS;
    }

    /**
     * Computes collision sub-regions for a single visual bracket.
     * <p>
     * Decomposes the bracket into horizontal bar, vertical ticks, and label
     * so that higher stacking layers can nestle into the open space between them.
     * All xOffset values are relative to the element's anchor X (the first bracket's x1).
     *
     * @param bracket  the bracket range to compute regions for
     * @param xBaseSs  horizontal offset of this bracket's x1 from the element anchor X
     * @return list of collision sub-regions
     */
    public List<CollisionRegion> computeCollisionRegions(
        BracketRange bracket,
        double xBaseSs
    ) {
        double spanWidthSs = bracket.widthSs();
        double bracketThicknessSs = LineThickness.getInstance().voltaBracketSs();
        var regions = new ArrayList<CollisionRegion>(4);

        // Horizontal bar
        regions.add(new CollisionRegion(
            xBaseSs, 0, spanWidthSs, bracketThicknessSs));

        // Left tick
        regions.add(new CollisionRegion(
            xBaseSs, 0, bracketThicknessSs,
            LayoutStylesheet.VOLTA_TICK_HEIGHT_SS));

        // Right tick (only if there is a closing stroke)
        if (bracket.hasClosingStroke()) {
            regions.add(new CollisionRegion(
                xBaseSs + spanWidthSs - bracketThicknessSs, 0,
                bracketThicknessSs,
                LayoutStylesheet.VOLTA_TICK_HEIGHT_SS));
        }

        // Label (e.g. "1." or "2.")
        var labelBounds = labelBoundsSs(bracket.number());
        double labelWidthSs = labelBounds.getWidth();
        double labelHeightSs = -labelBounds.getY();
        regions.add(new CollisionRegion(
            xBaseSs + LABEL_X_INSET_SS, 0,
            labelWidthSs, LABEL_Y_OFFSET_SS + labelHeightSs));

        return regions;
    }

    @Override
    public double getContentWidthPx() {
        var anchor = getAnchorElement();
        var endElement = getEndElement();

        if (anchor == null || endElement == null) {
            return 0;
        }

        return Math.abs(endElement.getXSs() - anchor.getXSs()) + endElement.getContentWidthPx();
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }
}
