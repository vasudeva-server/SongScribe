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
 * Layout geometry for {@link Trill} markings. The stacker reserves exactly the ink extent, so the
 * decoration layout is the trill's ink.
 */
public final class TrillGeometry {

    private TrillGeometry() {}

    public record InkExtent(double leftXSs, double rightXSs) {

        public double widthSs() {
            return rightXSs - leftXSs;
        }
    }

    /**
     * @param noteheadCenterXSs measured from the column's left edge
     */
    public static double glyphLeftXSs(double columnXSs, double noteheadCenterXSs) {
        return columnXSs + noteheadCenterXSs - Trill.TRILL_GLYPH_WIDTH_SS / 2.0;
    }

    public static boolean isSingleNote(Trill trill) {
        return trill.getAnchorElement() == trill.getEndElement();
    }

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
