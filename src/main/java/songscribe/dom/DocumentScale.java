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

    /** Default pixels per staff space, matching the legacy 8px staff line spacing. */
    public static final double PIXELS_PER_STAFF_SPACE = 8.0;

    private DocumentScale() {}

    /** Convert a value in staff-space units to pixels. */
    public static double ssToPx(double ss) {
        return PIXELS_PER_STAFF_SPACE * ss;
    }

    /**
     * Convert a value in staff-space units to document pixels, rounded to the
     * nearest integer. The value rounds to nearest because it is a position.
     */
    public static int ssToRoundedPx(double ss) {
        return new DocPx(ssToPx(ss)).roundedPx();
    }

    /** Convert a value in pixels to staff-space units. */
    public static double pxToSs(double px) {
        return px / PIXELS_PER_STAFF_SPACE;
    }

    /**
     * Convert a physical length in inches to staff-space units.
     * <p>
     * Document pixels are the intermediate step, but deliberately an unrounded one.
     * {@link GraphicUtils#convertToPixels} rounds to a whole pixel, and that is coarse enough
     * to shift a line width the user never edited onto a neighbouring value — which in turn
     * can push a line that only just fits past the staff margin. Use this pair wherever the
     * exact length matters, and {@code convertToPixels} only where a whole pixel count is
     * genuinely what is wanted.
     * <p>
     * <b>Nothing calls this yet.</b> Page setup is where the user's
     * {@link songscribe.util.LengthUnit} choice is going, and a length the user types in
     * inches or centimetres reaches the layout through here. Do not remove it as dead code.
     */
    public static double inchesToSs(double inches) {
        return pxToSs(inches * GraphicUtils.getDpi());
    }

    /** Convert a value in staff-space units to inches. The inverse of {@link #inchesToSs}. */
    public static double ssToInches(double ss) {
        return ssToPx(ss) / GraphicUtils.getDpi();
    }
}
