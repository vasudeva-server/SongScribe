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

package songscribe.ui.component.score;

import module java.desktop;


import songscribe.music.StaffElement;
import songscribe.ui.layout.ScaleContext;

/**
 * Static hit-testing utilities for note heads in a {@link LineComponent}.
 * <p>
 * Extracted from {@link LineSelectionHandler} so that {@link NoteDragHandler} can
 * share the same logic without duplicating it.
 */
public final class ElementHitTest {

    private static final int MIN_HIT_SIZE_PX = 8;

    private ElementHitTest() {
    }

    /**
     * Iterates all elements in the line and returns the index of the first element
     * whose hit rectangle contains {@code point}, or -1 if none.
     */
    static int hitTestElement(LineComponent lc, Point point) {
        var line = lc.getLine();

        if (line == null) {
            return -1;
        }

        var helper = new Rectangle();

        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);
            buildElementHitRect(lc, element, helper);

            if (helper.contains(point)) {
                return elementIndex;
            }
        }

        return -1;
    }

    /**
     * Builds the pixel-coordinate hit rectangle for the given element into {@code out}.
     */
    public static void buildElementHitRect(
        LineComponent lc,
        StaffElement element,
        Rectangle out
    ) {
        var elementType = element.getType();
        var sc = ScaleContext.getInstance();

        // Use notehead bounds (excludes stem and flag); apply minimum size for narrow elements (AD-10)
        var naturalWidthPx = (int) Math.round(sc.toPixels(elementType.getElementWidthSs()));
        var naturalHeightPx = (int) Math.round(sc.toPixels(elementType.getFullElementHeightSs()));
        var widthPx = Math.max(naturalWidthPx, MIN_HIT_SIZE_PX);
        var heightPx = Math.max(naturalHeightPx, MIN_HIT_SIZE_PX);

        // Get element X from LayoutResult (staff-space) and convert to pixels, so the
        // hit rect is in pixel coordinates consistent with the mouse-event point.
        var layoutResult = lc.getLayoutResult();
        var elementXSs = layoutResult != null ? layoutResult.getElementXSs(element) : 0.0;
        var elementXPx = (int) Math.round(sc.toPixels(elementXSs));
        var elementY = lc.staffPositionToYPx(element.getStaffPosition());
        var topOffsetPx = (int) Math.round(sc.toPixels(elementType.getNoteheadTopOffsetSs()));

        // Center the hit rect on the element when expanded to the minimum size
        var xPx = elementXPx - (widthPx - naturalWidthPx) / 2;
        var yPx = elementY + topOffsetPx - (heightPx - naturalHeightPx) / 2;
        out.setBounds(xPx, yPx, widthPx, heightPx);
    }
}
