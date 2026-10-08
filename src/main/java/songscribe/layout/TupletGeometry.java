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

import songscribe.dom.Tuplet;
import songscribe.dom.TupletGrade;
import songscribe.engraving.StemMetrics;
import songscribe.engraving.TupletMetrics;
import songscribe.font.LocalFonts;
import songscribe.font.TextMeasurement;

/**
 * Layout geometry for {@link Tuplet} brackets: the number font, measured ink dimensions, and the
 * bracket's X and Y extents.
 * <p>
 * {@code Tuplet} is a DOM type and may not depend on {@code songscribe.layout} or hold a font, so
 * everything about a tuplet that needs {@link LocalFonts} or {@link TextMeasurement} lives here
 * instead.
 */
public final class TupletGeometry {

    /** Italic serif font for tuplet numbers (font size in staff-spaces). */
    private static final float TUPLET_FONT_SIZE_SS = 1.65f;

    /** Italic serif font for tuplet numbers. */
    public static final Font TUPLET_FONT = LocalFonts.load("C059-Italic.otf", TUPLET_FONT_SIZE_SS);

    /** Measured ink height of a tuplet number in staff-spaces (representative digit "3"). */
    public static final double TUPLET_NUMBER_INK_HEIGHT_SS = measureNumberInkHeightSs();

    /**
     * Floor on the width {@link #getSpanWidthSs} reports, in staff spaces. A collision region of
     * zero width reserves nothing and lets the layer above sit on top of the bracket, so a span
     * whose two elements have not been separated yet still claims one staff space.
     */
    public static final double MIN_SPAN_WIDTH_SS = 1.0;

    private TupletGeometry() {}

    /**
     * How a tuplet is drawn, which decides the box it reserves: a bracket with the number in a gap
     * of its line, or the number alone over a beam that already shows the group.
     */
    public enum Style {
        /** A bracket line with arms, the number centered in a gap of the line. */
        BRACKETED,

        /** The number alone, with no bracket. */
        NUMBER_ONLY;

        /**
         * Returns how {@code tuplet} is drawn.
         *
         * @param tuplet the tuplet
         * @return {@link #NUMBER_ONLY} when {@link Tuplet#isNumberOnly()}, otherwise
         *     {@link #BRACKETED}
         */
        public static Style of(Tuplet tuplet) {
            return tuplet.isNumberOnly() ? NUMBER_ONLY : BRACKETED;
        }

        /**
         * Returns the vertical height the tuplet reserves: from the number's ink top down to the
         * arm bottom when bracketed, the number's ink height when number-only.
         *
         * @return the reserved height, in staff spaces
         */
        public double heightSs() {
            return switch (this) {
                case BRACKETED -> bracketLineOffsetSs() + TupletMetrics.BRACKET_ARM_HEIGHT_SS;
                case NUMBER_ONLY -> TUPLET_NUMBER_INK_HEIGHT_SS;
            };
        }

        /**
         * Returns how far above the worst note tip the reserved box's bottom sits, which lands the
         * bracket line {@link TupletMetrics#BRACKET_PADDING_SS} above that tip either way.
         * <p>
         * A bracketed box's bottom is the arm bottom, {@link TupletMetrics#BRACKET_ARM_HEIGHT_SS}
         * below the line. A number-only tuplet has no line, but LilyPond still positions the number
         * from it: with no knee, {@code Tuplet_number::calc_y_offset} returns the bracket's
         * {@code positions} midpoint, so the number's ink <em>center</em> lands on the line. Its
         * box bottom is the ink bottom, half an ink height — {@link #bracketLineOffsetSs()} — below
         * that center.
         *
         * @return the bottom margin, in staff spaces
         */
        public double marginSs() {
            return switch (this) {
                case BRACKETED -> TupletMetrics.BRACKET_PADDING_SS - TupletMetrics.BRACKET_ARM_HEIGHT_SS;
                case NUMBER_ONLY -> TupletMetrics.BRACKET_PADDING_SS - bracketLineOffsetSs();
            };
        }
    }

    private static double measureNumberInkHeightSs() {
        var inkHeightSs = TextMeasurement.inkHeight(
            TextMeasurement.requireVisualBounds("3", TUPLET_FONT));

        if (inkHeightSs <= 0) {
            throw new IllegalStateException(
                "Tuplet number font produced zero ink height; the font may have failed to load");
        }

        return inkHeightSs;
    }

    /**
     * The measured ink width of {@code grade} drawn as a tuplet number, in staff spaces.
     * <p>
     * Measured by {@link TextMeasurement} rather than in the paint-time font render
     * context, so it is available before anything is drawn — the number-only tuplet's hit region
     * is the number's ink and nothing else, and hit regions are built at layout time.
     *
     * @param grade the tuplet's grade, whose number is drawn
     * @return the number's ink width, in staff spaces
     */
    public static double numberInkWidthSs(TupletGrade grade) {
        return TextMeasurement.requireVisualBounds(String.valueOf(grade.number()), TUPLET_FONT).getWidth();
    }

    /**
     * Offset from the reserved box's top to the bracket line's center: half the number's ink
     * height, since the number straddles the line.
     *
     * @return the offset, in staff spaces
     */
    public static double bracketLineOffsetSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0;
    }

    /**
     * X of the visual bracket's left edge — where the left arm is drawn — given the X the bracket
     * is laid out from and the anchor's column. The arm sits on the anchor note's stem side
     * (insetting a full notehead width for a down-stem, only the stem width for an up-stem) and
     * extends {@link TupletMetrics#ARM_EXTENSION_SS} outward past it. An anchor with no upward
     * stem — a down-stem, a whole note, a rest — insets the full head width, putting the arm
     * outside the head's left edge.
     * <p>
     * Shared by the renderer, the hit region and the stacker's sloped clearance
     * ({@code StructuralStacker.computeTupletClearanceLeftYSs}) so the reserved arm position, the
     * drawn one and the clickable one stay in lockstep — mirroring LilyPond's use of the bracket's
     * visual X-bounds {@code x0}/{@code x1} for both slope and placement (tuplet-bracket.cc).
     *
     * @param tuplet       the tuplet whose bracket edge is computed
     * @param anchorXSs    X of the anchor's reference point, as the bracket is laid out
     * @param anchorColumn the anchor's column, whose notehead width the arm is placed from
     * @return the left edge's X, in staff spaces
     */
    public static double bracketLeftEdgeXSs(Tuplet tuplet, double anchorXSs, ElementColumn anchorColumn) {
        var noteheadWidthSs = anchorColumn.getNoteheadWidthSs();
        var insetSs = tuplet.anchorStemUp() ? StemMetrics.thicknessSs() : noteheadWidthSs;

        return anchorXSs + noteheadWidthSs - insetSs - TupletMetrics.ARM_EXTENSION_SS;
    }

    /**
     * X of the visual bracket's right edge — where the right arm is drawn — given the X the
     * bracket's end is laid out at and the end's column. The arm clears the end notehead and
     * extends {@link TupletMetrics#ARM_EXTENSION_SS} outward past it. See
     * {@link #bracketLeftEdgeXSs} for why this is shared.
     *
     * @param endXSs    X of the end's reference point, as the bracket is laid out
     * @param endColumn the end's column, whose notehead width the arm clears
     * @return the right edge's X, in staff spaces
     */
    public static double bracketRightEdgeXSs(double endXSs, ElementColumn endColumn) {
        return endXSs + endColumn.getNoteheadWidthSs() + TupletMetrics.ARM_EXTENSION_SS;
    }

    /**
     * A laid-out tuplet's bracket frame: its visual edges and its sloped top, in layout space.
     * The renderer and the hit region both read the bracket from here, so the drawn bracket and
     * the clickable one are the same.
     *
     * @param leftEdgeXSs  the visual bracket's left edge ({@link #bracketLeftEdgeXSs})
     * @param rightEdgeXSs the visual bracket's right edge ({@link #bracketRightEdgeXSs})
     * @param layout       the sloped layout the stacking pass placed the tuplet at
     */
    public record BracketFrame(double leftEdgeXSs, double rightEdgeXSs, LayoutResult.DecorationLayout.Sloped layout) {

        /**
         * Returns the frame of {@code tuplet}, laid out at {@code layout} in {@code layoutResult}.
         *
         * @param tuplet       the tuplet
         * @param layout       its sloped layout
         * @param layoutResult the line's layout, which holds the columns of the tuplet's anchor
         *                     and end
         * @return the frame
         * @throws IllegalStateException when {@code layoutResult} holds no column for the anchor
         *     or the end
         */
        public static BracketFrame of(
            Tuplet tuplet, LayoutResult.DecorationLayout.Sloped layout, LayoutResult layoutResult
        ) {
            var anchorXSs = layout.xSs();
            var leftEdgeXSs = bracketLeftEdgeXSs(
                tuplet, anchorXSs, layoutResult.requireElementColumn(tuplet.getAnchorElement()));
            var rightEdgeXSs = bracketRightEdgeXSs(
                anchorXSs + layout.widthSs(), layoutResult.requireElementColumn(tuplet.getEndElement()));

            return new BracketFrame(leftEdgeXSs, rightEdgeXSs, layout);
        }

        /**
         * Returns the X midway between the two edges, where the number is centered.
         *
         * @return the center X, in staff spaces
         */
        public double centerXSs() {
            return (leftEdgeXSs + rightEdgeXSs) / 2;
        }

        /**
         * Returns the Y of the reserved box's sloped top at {@code xSs}.
         *
         * @param xSs an X in layout space
         * @return the box top's Y at {@code xSs}
         */
        public double boxTopYAtSs(double xSs) {
            return layout.topYAtSs(xSs);
        }

        /**
         * Returns the Y of the bracket line's center at {@code xSs}: the box top moved down by
         * {@link #bracketLineOffsetSs}.
         *
         * @param xSs an X in layout space
         * @return the bracket line's Y at {@code xSs}
         */
        public double bracketLineYAtSs(double xSs) {
            return boxTopYAtSs(xSs) + bracketLineOffsetSs();
        }
    }

    /**
     * Returns the horizontal span the tuplet bracket reserves, in staff spaces: the distance
     * between its two elements, floored so that a bracket over elements sharing a column still
     * reserves a region with width to it.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return the span width in staff spaces, never below {@link #MIN_SPAN_WIDTH_SS} and so
     *     always positive
     */
    public static double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(MIN_SPAN_WIDTH_SS, endXSs - anchorXSs);
    }
}
