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

package songscribe.dom;

import java.awt.Font;

import songscribe.util.GraphicUtils;

/**
 * The fixed authoring scale of a document: the mapping between staff-space units
 * and document pixels that {@link songscribe.ui.ViewScale} folds the current zoom
 * on top of.
 * <p>
 * In staff-space coordinates, the distance between two adjacent staff lines
 * is exactly 1.0. {@link #PIXELS_PER_STAFF_SPACE} converts these abstract units
 * to document pixels, and it is a compile-time constant: one document scale
 * holds for every document and every view.
 * <p>
 * Zoom is per-view; see {@code docs/zoom.md} for how the two scales combine.
 */
public final class DocumentScale {

    /** Pixels per staff space, matching the legacy 8px staff line spacing. */
    public static final double PIXELS_PER_STAFF_SPACE = 8.0;

    private DocumentScale() {}

    /**
     * Converts a value in staff-space units to document pixels.
     *
     * @param ss the distance in staff spaces
     * @return the same distance as an unrounded {@link DocPx}; ask it for
     *         {@link DocPx#positionPx()} or {@link DocPx#sizePx()} to reach a
     *         whole pixel, whichever the value is
     */
    public static DocPx ssToPx(double ss) {
        return new DocPx(PIXELS_PER_STAFF_SPACE * ss);
    }

    /** Convert a value in pixels to staff-space units. */
    public static double pxToSs(double px) {
        return px / PIXELS_PER_STAFF_SPACE;
    }

    /**
     * Convert a physical length in inches to staff-space units.
     * <p>
     * Document pixels are the intermediate step, but deliberately an unrounded one.
     * Rounding a length to a whole pixel on the way in is coarse enough to shift a line width
     * the user never edited onto a neighbouring value — which in turn can push a line that
     * only just fits past the staff margin. Convert through here wherever the exact length
     * matters, and reach a whole pixel only at the end, by asking the resulting {@link DocPx}
     * for the count.
     */
    public static double inchesToSs(double inches) {
        return pxToSs(inches * GraphicUtils.getDpi());
    }

    /** Convert a value in staff-space units to inches. The inverse of {@link #inchesToSs}. */
    public static double ssToInches(double ss) {
        return ssToPx(ss).value() / GraphicUtils.getDpi();
    }

    /**
     * Re-expresses {@code font}'s point size in staff-space units, for a renderer drawing
     * inside the staff-space coordinate transform: text set in the returned font comes out
     * the same visual size as {@code font} would at document scale outside that transform.
     *
     * @param font a font whose size is expressed in document pixels
     * @return the same face and style, sized in staff-space units
     */
    public static Font fontSizedInSs(Font font) {
        return font.deriveFont((float) pxToSs(font.getSize()));
    }
}
