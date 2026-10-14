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
 * The fixed authoring scale of a document: the mapping between staff-space units and document
 * pixels that {@link songscribe.ui.ViewScale} folds the current zoom on top of. One document
 * scale holds for every document and every view.
 * <p>
 * Zoom is per-view; see {@code docs/zoom.md}.
 */
public final class DocumentScale {

    /** Matches the legacy 8px staff line spacing. */
    public static final double PIXELS_PER_STAFF_SPACE = 8.0;

    private DocumentScale() {}

    /**
     * @return an unrounded {@link DocPx}
     */
    public static DocPx ssToPx(double ss) {
        return new DocPx(PIXELS_PER_STAFF_SPACE * ss);
    }

    public static double pxToSs(double px) {
        return px / PIXELS_PER_STAFF_SPACE;
    }

    /**
     * Deliberately unrounded: rounding to a whole pixel on the way in can shift a line width the
     * user never edited onto a neighbouring value, and push a line that only just fits past the
     * staff margin.
     */
    public static double inchesToSs(double inches) {
        return pxToSs(inches * GraphicUtils.getDpi());
    }

    public static double ssToInches(double ss) {
        return ssToPx(ss).value() / GraphicUtils.getDpi();
    }

    /**
     * For a renderer drawing inside the staff-space coordinate transform: text set in the
     * returned font comes out the size {@code font} has outside that transform.
     *
     * @param font a font whose size is expressed in document pixels
     */
    public static Font fontSizedInSs(Font font) {
        return font.deriveFont((float) pxToSs(font.getSize()));
    }
}
