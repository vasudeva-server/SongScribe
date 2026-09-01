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

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import songscribe.dom.CollisionRegion;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.engraving.EngravingConstants;
import songscribe.font.LocalFonts;
import songscribe.font.TextMeasurement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

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
    public static final Font ENDING_FONT = LocalFonts.load(
        "emmentaler-16.otf", NoteGeometry.MUSIC_FONT_SIZE_SS * LABEL_FONT_SCALE);

    /** Cached visual bounds for the "1." label. */
    private static final Rectangle2D LABEL_1_BOUNDS_SS =
        TextMeasurement.requireVisualBounds("1.", ENDING_FONT);

    /** Cached visual bounds for the "2." label. */
    private static final Rectangle2D LABEL_2_BOUNDS_SS =
        TextMeasurement.requireVisualBounds("2.", ENDING_FONT);

    private EndingBracketGeometry() {
    }

    /**
     * Computes the bracket ranges for an ending and stores them on it.
     * <p>
     * Determines where each visual bracket starts and ends by examining the
     * element types (barlines, repeats) in the line. The ranges are stored on the
     * ending, and both stacking and rendering read them from there rather than
     * deriving positions of their own.
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

        if (!line.hasIndex(start) || !line.hasIndex(end)) {
            List<Ending.BracketRange> noRanges = List.of();
            ending.setBracketRanges(noRanges);
            return noRanges;
        }

        // The ending owns which of its elements is the split. Searching for it again here
        // is what let the two disagree: a scan that includes the anchor takes an anchor
        // that is itself a repeat for the split, which leaves the first bracket unbuilt
        // and the second one anchored at the line's left edge.
        var repeatSplitIndex = ending.getSplitIndex(line);

        var startElement = line.getElement(ending.getOpeningElementIndex(line));
        var endElement = line.getElement(end);

        // The split lies strictly between anchor and end, so both brackets always exist.
        var firstBracket = firstBracketRange(startElement, repeatSplitIndex, line, columnFn);
        var secondBracket = secondBracketRange(
            endElement, end, repeatSplitIndex, line, columnFn);

        var bracketRanges = List.of(firstBracket, secondBracket);
        ending.setBracketRanges(bracketRanges);

        return bracketRanges;
    }

    /**
     * The first bracket: from the element the ending opens on to the split repeat.
     *
     * @param startElement     the element the bracket opens on
     * @param repeatSplitIndex the index of the repeat splitting the two brackets
     * @param line             the line containing the ending
     * @param columnFn         supplies an element's column
     * @return the bracket's range
     */
    private static Ending.BracketRange firstBracketRange(
        StaffElement startElement,
        int repeatSplitIndex,
        Line line,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        var startColumn = columnFn.apply(startElement);
        var startType = startElement.getType();
        double x1;

        // For barlines and repeats, the bar states the anchor its opening arm hangs from.
        // For notes/rests, anchor to the element's left extent.
        if (startType.isBarLine() || startType.isRepeat()) {
            x1 = startColumn.getXSs() + startType.voltaOpeningXOffsetSs();
        }
        else {
            x1 = startColumn.getLeftEdgeXSs() - NoteGeometry.ACCIDENTAL_PADDING_SS;
        }

        var splitElement = line.getElement(repeatSplitIndex);
        var x2 = columnFn.apply(splitElement).getXSs()
            + splitElement.getType().voltaClosingXOffsetSs();

        return new Ending.BracketRange(x1, x2, 1, true);
    }

    /**
     * The second bracket: from the split repeat to the element the ending closes on.
     *
     * @param endElement       the ending's end element
     * @param end              the end element's index
     * @param repeatSplitIndex the index of the repeat splitting the two brackets
     * @param line             the line containing the ending
     * @param columnFn         supplies an element's column
     * @return the bracket's range
     */
    private static Ending.BracketRange secondBracketRange(
        StaffElement endElement,
        int end,
        int repeatSplitIndex,
        Line line,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        var splitElement = line.getElement(repeatSplitIndex);
        var x1 = columnFn.apply(splitElement).getXSs()
            + splitElement.getType().voltaOpeningXOffsetSs();

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

        if (endType.isBarLine() || endType.isRepeat()) {
            x2 += endType.voltaClosingXOffsetSs();
            hasClosingStroke = endType == ElementType.REPEAT_RIGHT
                || endType == ElementType.REPEAT_LEFT_RIGHT
                || endType == ElementType.FINAL_DOUBLE_BARLINE;
        }
        else {
            x2 = endColumn.getRightEdgeXSs() + SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT);
            hasClosingStroke = false;
        }

        return new Ending.BracketRange(x1, x2, 2, hasClosingStroke);
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
        var spanWidthSs = bracket.inkRightXSs() - bracket.x1Ss();
        var bracketThicknessSs = EngravingConstants.VOLTA_BRACKET_SS;
        var halfThicknessSs = bracketThicknessSs / 2;

        // The bracket is stroked along the element's Y, and a stroke is centered on its path,
        // so the bar's ink reaches half a thickness above that Y. Regions describe ink, so
        // they start there: a region starting at the Y itself leaves that half unreserved,
        // and a volta with nothing stacked above it is then clipped by exactly that much.
        var inkTopSs = -halfThicknessSs;
        var regions = new ArrayList<CollisionRegion>(4);

        // Horizontal bar
        regions.add(new CollisionRegion(
            xBaseSs, inkTopSs, spanWidthSs, bracketThicknessSs));

        // The ticks hang from the bar. Their lower ends are the stroked path's caps, which
        // drawPath insets so that the ink stops on the endpoint rather than bulging past it.
        var tickHeightSs = Ending.VOLTA_TICK_HEIGHT_SS + halfThicknessSs;

        // Left tick
        regions.add(new CollisionRegion(
            xBaseSs, inkTopSs, bracketThicknessSs, tickHeightSs));

        // Right tick (only if there is a closing stroke)
        if (bracket.hasClosingStroke()) {
            regions.add(new CollisionRegion(
                xBaseSs + spanWidthSs - bracketThicknessSs, inkTopSs,
                bracketThicknessSs, tickHeightSs));
        }

        // Label (e.g. "1." or "2.")
        var labelBounds = labelBoundsSs(bracket.number());
        var labelWidthSs = labelBounds.getWidth();
        var labelHeightSs = TextMeasurement.inkHeight(labelBounds);
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
