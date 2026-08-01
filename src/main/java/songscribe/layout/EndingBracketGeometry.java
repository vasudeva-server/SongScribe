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

package songscribe.layout;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import songscribe.dom.CollisionRegion;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Layout geometry for {@link Ending} volta brackets.
 * <p>
 * {@code Ending} is a DOM type and may not depend on {@code songscribe.layout}; everything
 * about an ending that needs {@link ElementColumn} or {@link NoteGeometry} — the bracket X
 * ranges, the label font and its cached glyph bounds, and the collision decomposition — lives
 * here instead.
 */
public final class EndingBracketGeometry {

    /** Scale of volta label font relative to standard music font size. */
    private static final float LABEL_FONT_SCALE = 0.6f;

    /** Font for volta bracket labels. */
    public static final Font ENDING_FONT = MyFontUtils.getLocalFont(
        "emmentaler-16.otf", NoteGeometry.MUSIC_FONT_SIZE_SS * LABEL_FONT_SCALE);

    /** Cached visual bounds for the "1." label. */
    private static final Rectangle2D LABEL_1_BOUNDS_SS =
        ENDING_FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, "1.").getVisualBounds();

    /** Cached visual bounds for the "2." label. */
    private static final Rectangle2D LABEL_2_BOUNDS_SS =
        ENDING_FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, "2.").getVisualBounds();

    private EndingBracketGeometry() {
    }

    /**
     * Computes the bracket ranges for an ending and stores them on it.
     * <p>
     * Determines where each visual bracket starts and ends by examining the
     * element types (barlines, repeats) in the line. The logic mirrors
     * {@code EndingRenderer.renderEndings()} to ensure stacking and rendering
     * use identical positions.
     *
     * @param ending   the ending to compute ranges for
     * @param line     the line containing the ending
     * @param columnFn function that returns the {@link ElementColumn} of an element,
     *                 from which its X position and horizontal extents are read
     * @return the computed bracket ranges (also stored on the ending)
     */
    public static List<Ending.BracketRange> computeBracketRanges(
        Ending ending,
        Line line,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        var start = ending.getAnchorElementIndex();
        var end = ending.getEndElementIndex();

        if (start < 0 || end < 0 || start >= line.elementCount()
            || end >= line.elementCount()) {
            List<Ending.BracketRange> noRanges = List.of();
            ending.setBracketRanges(noRanges);
            return noRanges;
        }

        // Find the repeat element (REPEAT_RIGHT or REPEAT_LEFT_RIGHT) that separates first and second endings
        var repeatSplitIndex = IntStream.rangeClosed(start, end)
            .filter(i -> {
                var t = line.getElement(i).getType();
                return t == ElementType.REPEAT_RIGHT || t == ElementType.REPEAT_LEFT_RIGHT;
            })
            .findFirst()
            .orElse(-1);

        if (repeatSplitIndex < 0) {
            throw Ending.noSplitElementException(start, end);
        }

        var startElement = line.getElement(start);

        // Adjust start leftward if previous element is a barline or repeat
        if (start > 0) {
            var previousElement = line.getElement(start - 1);
            var prevType = previousElement.getType();

            if (prevType.isBarLine() || prevType.isRepeat()) {
                --start;
                startElement = previousElement;
            }
        }

        var endElement = line.getElement(end);
        var ranges = new ArrayList<Ending.BracketRange>(2);
        var repeatX = 0.0;

        // First bracket (anchor up to the split repeat)
        if (start < repeatSplitIndex) {
            var startColumn = columnFn.apply(startElement);
            var x1 = startColumn.getXSs();
            var startType = startElement.getType();

            // For barlines and repeats, align to the governing thin barline center.
            // For notes/rests, anchor to the element's left extent.
            if (startType.isBarLine() || startType.isRepeat()) {
                x1 += startType.endingAnchorXOffsetSs();
            }
            else {
                x1 = startColumn.getLeftEdgeXSs() - NoteGeometry.ACCIDENTAL_PADDING_SS;
            }

            var repeatElementX = columnFn.apply(
                line.getElement(repeatSplitIndex)).getXSs();
            var x2 = repeatElementX
                + LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS;
            repeatX = repeatElementX
                + LineThickness.REPEAT_RIGHT_AFTER_THICK_X_SS
                - LineThickness.VOLTA_BRACKET_SS / 2;

            ranges.add(new Ending.BracketRange(x1, x2, 1, true));
        }

        // Second bracket (after the split repeat)
        if (end > repeatSplitIndex) {
            var endColumn = columnFn.apply(endElement);
            var x2 = endColumn.getXSs();
            var endType = endElement.getType();

            // Extend to the next barline/repeat if end element is not one
            if (!endType.isBarLine() && !endType.isRepeat()
                && end + 1 < line.elementCount()) {
                var nextElement = line.getElement(end + 1);
                var nextType = nextElement.getType();

                if (nextType.isBarLine() || nextType.isRepeat()) {
                    endType = nextType;
                    x2 = columnFn.apply(nextElement).getXSs();
                }
            }

            boolean hasClosingStroke;

            switch (endType) {
                case REPEAT_RIGHT, REPEAT_LEFT_RIGHT -> {
                    x2 += LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS;
                    hasClosingStroke = true;
                }
                case FINAL_DOUBLE_BARLINE -> {
                    x2 += LineThickness.THIN_BARLINE_SS / 2;
                    hasClosingStroke = true;
                }
                case SINGLE_BARLINE, DOUBLE_BARLINE -> {
                    x2 += LineThickness.THIN_BARLINE_SS / 2;
                    hasClosingStroke = false;
                }
                case REPEAT_LEFT -> hasClosingStroke = false;
                default -> {
                    x2 = endColumn.getRightEdgeXSs() + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
                    hasClosingStroke = false;
                }
            }

            ranges.add(new Ending.BracketRange(repeatX, x2, 2, hasClosingStroke));
        }

        var bracketRanges = List.copyOf(ranges);
        ending.setBracketRanges(bracketRanges);

        return bracketRanges;
    }

    /**
     * Computes collision sub-regions for a single visual bracket.
     * <p>
     * Decomposes the bracket into horizontal bar, vertical ticks, and label
     * so that higher stacking layers can nestle into the open space between them.
     * All xOffset values are relative to the element's anchor X (the first bracket's x1).
     *
     * @param bracket the bracket range to compute regions for
     * @param xBaseSs horizontal offset of this bracket's x1 from the element anchor X
     * @return list of collision sub-regions
     */
    public static List<CollisionRegion> computeCollisionRegions(
        Ending.BracketRange bracket,
        double xBaseSs
    ) {
        var spanWidthSs = bracket.widthSs();
        var bracketThicknessSs = LineThickness.VOLTA_BRACKET_SS;
        var regions = new ArrayList<CollisionRegion>(4);

        // Horizontal bar
        regions.add(new CollisionRegion(
            xBaseSs, 0, spanWidthSs, bracketThicknessSs));

        // Left tick
        regions.add(new CollisionRegion(
            xBaseSs, 0, bracketThicknessSs,
            Ending.VOLTA_TICK_HEIGHT_SS));

        // Right tick (only if there is a closing stroke)
        if (bracket.hasClosingStroke()) {
            regions.add(new CollisionRegion(
                xBaseSs + spanWidthSs - bracketThicknessSs, 0,
                bracketThicknessSs,
                Ending.VOLTA_TICK_HEIGHT_SS));
        }

        // Label (e.g. "1." or "2.")
        var labelBounds = labelBoundsSs(bracket.number());
        var labelWidthSs = labelBounds.getWidth();
        var labelHeightSs = GraphicUtils.inkHeight(labelBounds);
        regions.add(new CollisionRegion(
            xBaseSs + Ending.LABEL_X_INSET_SS, 0,
            labelWidthSs, Ending.LABEL_Y_OFFSET_SS + labelHeightSs));

        return regions;
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
}
