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

import java.awt.geom.Rectangle2D;

import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElement;

/**
 * The clickable rectangle of a note head, in layout space: X in line-local staff spaces, Y in
 * staff spaces relative to the staff midline. Production code reaches it through
 * {@link LayoutResult#elementHitRectSs}.
 */
public final class ElementHitGeometry {

    /**
     * A pixel constant is safe in layout geometry only because {@link DocumentScale} does not
     * vary with on-screen zoom.
     */
    private static final int MIN_HIT_SIZE_PX = 8;

    private ElementHitGeometry() {
    }

    /**
     * @param out overwritten with the result
     */
    public static void elementHitRectSs(double elementXSs, StaffElement element, Rectangle2D.Double out) {
        var elementType = element.getType();

        // From the element, not from its type: a mid-line key change's type width is only a
        // floor, and a hit rect built from it would leave every accidental past the first
        // unclickable.
        var naturalWidthSs = element.getGlyphWidthSs();
        var naturalHeightSs = elementType.getFullElementHeightSs();

        // Layout space puts the staff midline at Y = 0, so the element's Y is the seat of its ink
        // alone.
        var elementYSs = NoteGeometry.noteStaffPositionToCoordinateSs(ElementSeat.seatSp(element).value(), 0);
        var topOffsetSs = elementType.getNoteheadTopOffsetSs();
        var minHitSizeSs = DocumentScale.pxToSs(MIN_HIT_SIZE_PX);

        var xExpansionSs = Math.max(0, (minHitSizeSs - naturalWidthSs) / 2);
        var yExpansionSs = Math.max(0, (minHitSizeSs - naturalHeightSs) / 2);

        out.setRect(
            elementXSs - xExpansionSs,
            elementYSs + topOffsetSs - yExpansionSs,
            naturalWidthSs + 2 * xExpansionSs,
            naturalHeightSs + 2 * yExpansionSs
        );
    }
}
