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
 * and the horizontal span its wavy line occupies.
 */
public final class TrillGeometry {

    private TrillGeometry() {}

    /**
     * The left edge of the drawn "tr" glyph, in staff-space units.
     * <p>
     * The glyph is centered on the notehead rather than on the note column, so it starts a little
     * left of the column. Written once here and used by everything that has to agree on where the
     * glyph lands: the stacker's reservation, the renderer's ink, and the hit region.
     *
     * @param columnXSs         X of the anchor note's column
     * @param noteheadCenterXSs the notehead's center, measured from the column's left edge
     */
    public static double glyphLeftXSs(double columnXSs, double noteheadCenterXSs) {
        return columnXSs + noteheadCenterXSs - Trill.TRILL_GLYPH_WIDTH_SS / 2.0;
    }

    /**
     * Returns the full horizontal span the trill's wavy line occupies, in staff spaces: from the
     * anchor note's X to one glyph width past the end note's, since the line is drawn in whole
     * glyphs and the last one starts at the end note rather than finishing there.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return the span width in staff spaces, never less than one {@link Trill#TRILL_GLYPH_WIDTH_SS}
     *     however close the two elements sit
     */
    public static double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(Trill.TRILL_GLYPH_WIDTH_SS, endXSs - anchorXSs + Trill.TRILL_GLYPH_WIDTH_SS);
    }
}
