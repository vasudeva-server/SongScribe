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
    public static final float TUPLET_FONT_SIZE_SS = 1.65f;

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
     */
    public static double numberInkWidthSs(TupletGrade grade) {
        return TextMeasurement.requireVisualBounds(String.valueOf(grade.number()), TUPLET_FONT).getWidth();
    }

    /**
     * Reserved vertical height for a bracketed tuplet (bracket line + arm). A number-only
     * tuplet (see {@link Tuplet#isNumberOnly()}) reserves {@link #numberOnlyHeightSs()}
     * instead, so a caller laying out a tuplet branches on {@code isNumberOnly} to choose
     * between the two; neither height is meaningful without that choice, which is why a
     * tuplet has no context-free height query.
     */
    public static double bracketedHeightSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0 + TupletMetrics.BRACKET_ARM_HEIGHT_SS;
    }

    /**
     * Reserved vertical height for a number-only tuplet (beamed, stems up).
     */
    @SuppressWarnings("SameReturnValue")
    public static double numberOnlyHeightSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS;
    }

    /**
     * Offset from the box top to the bracket line center in staff-spaces.
     */
    public static double bracketLineOffsetSs() {
        return TUPLET_NUMBER_INK_HEIGHT_SS / 2.0;
    }

    /**
     * X of the visual bracket's left edge — where the left arm is drawn — given the anchor
     * column's reference X and the anchor note's head width. The arm sits on the anchor note's
     * stem side (insetting a full notehead width for a down-stem, only the stem width for an
     * up-stem) and extends {@link TupletMetrics#ARM_EXTENSION_SS} outward past it. An anchor with
     * no upward stem — a down-stem, a whole note, a rest — insets the full head width, putting
     * the arm outside the head's left edge.
     * <p>
     * Shared by the renderer ({@code TupletRenderer.renderTupletsFromLine}) and the stacker's
     * sloped clearance ({@code StructuralStacker.computeTupletClearanceLeftYSs}) so the reserved
     * arm position and the drawn arm position stay in lockstep — mirroring LilyPond's use of the
     * bracket's visual X-bounds {@code x0}/{@code x1} for both slope and placement
     * (tuplet-bracket.cc).
     *
     * @param tuplet          the tuplet whose bracket edge is computed
     * @param anchorXSs       X of the anchor column's reference point
     * @param noteheadWidthSs the anchor note's own head width in staff spaces
     */
    public static double bracketLeftEdgeXSs(Tuplet tuplet, double anchorXSs, double noteheadWidthSs) {
        var insetSs = tuplet.anchorStemUp() ? StemMetrics.thicknessSs() : noteheadWidthSs;

        return anchorXSs + noteheadWidthSs - insetSs - TupletMetrics.ARM_EXTENSION_SS;
    }

    /**
     * X of the visual bracket's right edge — where the right arm is drawn — given the end
     * column's reference X. The arm clears the end notehead and extends
     * {@link TupletMetrics#ARM_EXTENSION_SS} outward past it. See {@link #bracketLeftEdgeXSs} for
     * why this is shared with the renderer.
     *
     * @param endXSs          X of the end column's reference point
     * @param noteheadWidthSs the end note's own head width in staff spaces
     */
    public static double bracketRightEdgeXSs(double endXSs, double noteheadWidthSs) {
        return endXSs + noteheadWidthSs + TupletMetrics.ARM_EXTENSION_SS;
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
