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
 * staff spaces relative to the staff midline.
 * <p>
 * Production code reaches it through {@link LayoutResult#elementHitRectSs}, which places the
 * <em>expanded</em> rect at the element's column, so a thin or short element is still comfortably
 * clickable. The hit registry holds that rect for the element, and the score points at a flagged
 * element with the same one.
 */
public final class ElementHitGeometry {

    /**
     * Smallest clickable extent of an element, in pixels. Elements narrower or shorter than this
     * are expanded symmetrically up to it so they can still be hit.
     * <p>
     * A pixel constant is safe to bake into layout geometry because {@link DocumentScale} is a
     * fixed document scale: it does not vary with on-screen zoom, so this converts to a constant
     * number of staff spaces.
     */
    private static final int MIN_HIT_SIZE_PX = 8;

    private ElementHitGeometry() {
    }

    /**
     * Builds the hit rectangle for {@code element} into {@code out}: the element's note head,
     * expanded symmetrically to at least {@value #MIN_HIT_SIZE_PX} px in each dimension, so a
     * narrow or short element stays clickable and the rectangle stays centered on what is drawn.
     *
     * @param elementXSs the element column's origin X, in line-local staff spaces
     * @param element    the element to measure
     * @param out        the rectangle to write into; reused across a loop to avoid allocating
     *                   one rect per element
     * @effects overwrites {@code out} with the rectangle, in line-local, midline-relative staff
     *     spaces
     */
    public static void elementHitRectSs(double elementXSs, StaffElement element, Rectangle2D.Double out) {
        var elementType = element.getType();

        // From the element, not from its type: a mid-line key change's type width is only a
        // floor, and a hit rect built from it would leave every accidental past the first
        // unclickable.
        var naturalWidthSs = element.getGlyphWidthSs();
        var naturalHeightSs = elementType.getFullElementHeightSs();

        // Layout space puts the staff midline at Y = 0, so the element's Y is the seat of its ink
        // alone — the same quantity the UI hit test read from LineComponent.getMiddleLineYSs().
        var elementYSs = NoteGeometry.noteStaffPositionToCoordinateSs(ElementSeat.seatSp(element).value(), 0);
        var topOffsetSs = elementType.getNoteheadTopOffsetSs();
        var minHitSizeSs = DocumentScale.pxToSs(MIN_HIT_SIZE_PX);

        // Expand symmetrically: distribute the extra width and height evenly on both sides, so
        // the rect stays centered on what is drawn.
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
