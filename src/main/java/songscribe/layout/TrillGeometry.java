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

import songscribe.dom.Trill;

/**
 * Layout geometry for {@link Trill} markings: where the "tr" glyph lands relative to a column,
 * and the horizontal extent of the trill's ink.
 * <p>
 * The stacker reserves exactly the ink extent, so the trill's decoration layout is its ink: the
 * renderer draws within those bounds and the hit region is those bounds, and neither works out an
 * edge of its own.
 */
public final class TrillGeometry {

    private TrillGeometry() {}

    /**
     * The horizontal extent of a trill's ink, in the line's layout space.
     *
     * @param leftXSs  the "tr" glyph's left edge
     * @param rightXSs the ink's right edge
     */
    public record InkExtent(double leftXSs, double rightXSs) {

        /**
         * Returns the extent's width.
         *
         * @return {@link #rightXSs} minus {@link #leftXSs}, in staff spaces
         */
        public double widthSs() {
            return rightXSs - leftXSs;
        }
    }

    /**
     * The left edge of the drawn "tr" glyph, in staff-space units.
     * <p>
     * The glyph is centered on the notehead rather than on the note column, so it starts a little
     * left of the column.
     *
     * @param columnXSs         X of the anchor note's column
     * @param noteheadCenterXSs the notehead's center, measured from the column's left edge
     * @return the glyph's left edge, in staff spaces
     */
    public static double glyphLeftXSs(double columnXSs, double noteheadCenterXSs) {
        return columnXSs + noteheadCenterXSs - Trill.TRILL_GLYPH_WIDTH_SS / 2.0;
    }

    /**
     * Returns whether a trill sits on one note, which draws the "tr" glyph and no wavy line.
     *
     * @param trill the trill
     * @return {@code true} when the trill's anchor is its end
     */
    public static boolean isSingleNote(Trill trill) {
        return trill.getAnchorElement() == trill.getEndElement();
    }

    /**
     * Returns the horizontal extent of a trill's ink: from the "tr" glyph's left edge
     * ({@link #glyphLeftXSs}) to the glyph's right edge for a single-note trill
     * ({@link #isSingleNote}), or to the end note's right edge — where the wavy line stops — for a
     * trill over a range.
     *
     * @param trill   the trill
     * @param columns the trill's anchor and end elements with their columns
     * @return the trill's ink extent
     */
    public static InkExtent inkExtentSs(Trill trill, ElementColumn.SpanColumns columns) {
        var leftXSs = glyphLeftXSs(
            columns.anchorColumn().getXSs(), NoteGeometry.getNoteheadCenterXSs(columns.anchor()));

        if (isSingleNote(trill)) {
            return new InkExtent(leftXSs, leftXSs + trill.intrinsicWidthSs());
        }

        return new InkExtent(
            leftXSs, columns.endColumn().getXSs() + columns.end().getType().getElementWidthSs());
    }
}
