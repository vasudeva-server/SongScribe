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
 * Layout geometry for {@link Tuplet} brackets. {@code Tuplet} is a DOM type and may not depend on
 * {@code songscribe.layout} or hold a font, so everything that needs {@link LocalFonts} or
 * {@link TextMeasurement} lives here.
 */
public final class TupletGeometry {

    private static final float TUPLET_FONT_SIZE_SS = 1.65f;

    public static final Font TUPLET_FONT = LocalFonts.load("C059-Italic.otf", TUPLET_FONT_SIZE_SS);

    public static final double TUPLET_NUMBER_INK_HEIGHT_SS = measureNumberInkHeightSs();

    /**
     * A zero-width collision region reserves nothing, so a span whose elements have not been
     * separated yet still claims one staff space.
     */
    public static final double MIN_SPAN_WIDTH_SS = 1.0;

    private TupletGeometry() {}

    public enum Style {
        BRACKETED,
        NUMBER_ONLY;

        public static Style of(Tuplet tuplet) {
            return tuplet.isNumberOnly() ? NUMBER_ONLY : BRACKETED;
        }

        public double heightSs() {
            return switch (this) {
                case BRACKETED -> bracketLineOffsetSs() + TupletMetrics.BRACKET_ARM_HEIGHT_SS;
                case NUMBER_ONLY -> TUPLET_NUMBER_INK_HEIGHT_SS;
            };
        }

        /**
         * Places the bracket line {@link TupletMetrics#BRACKET_PADDING_SS} above the worst note
         * tip for either style.
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
     * Measured by {@link TextMeasurement} rather than in the paint-time font render context, so
     * it is available when hit regions are built at layout time.
     */
    public static double numberInkWidthSs(TupletGrade grade) {
        return TextMeasurement.requireVisualBounds(String.valueOf(grade.number()), TUPLET_FONT).getWidth();
    }

    public static double bracketLineOffsetSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0;
    }

    /**
     * Shared by the renderer, the hit region and the stacker's sloped clearance
     * ({@code StructuralStacker.computeTupletClearanceLeftYSs}) so the reserved, drawn and
     * clickable arm positions stay in lockstep.
     */
    public static double bracketLeftEdgeXSs(Tuplet tuplet, double anchorXSs, ElementColumn anchorColumn) {
        var noteheadWidthSs = anchorColumn.getNoteheadWidthSs();
        var insetSs = tuplet.anchorStemUp() ? StemMetrics.thicknessSs() : noteheadWidthSs;

        return anchorXSs + noteheadWidthSs - insetSs - TupletMetrics.ARM_EXTENSION_SS;
    }

    /** Shared for the same reason as {@link #bracketLeftEdgeXSs}. */
    public static double bracketRightEdgeXSs(double endXSs, ElementColumn endColumn) {
        return endXSs + endColumn.getNoteheadWidthSs() + TupletMetrics.ARM_EXTENSION_SS;
    }

    /**
     * A laid-out tuplet's bracket frame in layout space. The renderer and the hit region both read
     * the bracket from here.
     */
    public record BracketFrame(double leftEdgeXSs, double rightEdgeXSs, LayoutResult.DecorationLayout.Sloped layout) {

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

        public double centerXSs() {
            return (leftEdgeXSs + rightEdgeXSs) / 2;
        }

        public double boxTopYAtSs(double xSs) {
            return layout.topYAtSs(xSs);
        }

        public double bracketLineYAtSs(double xSs) {
            return boxTopYAtSs(xSs) + bracketLineOffsetSs();
        }
    }

    public static double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(MIN_SPAN_WIDTH_SS, endXSs - anchorXSs);
    }
}
