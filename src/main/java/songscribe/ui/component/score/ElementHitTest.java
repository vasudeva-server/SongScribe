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

import org.jetbrains.annotations.NotNull;

import songscribe.music.StaffElement;
import songscribe.ui.layout2.ScaleContext;

/**
 * Static hit-testing utilities for note heads in a {@link LineComponent}.
 * <p>
 * Extracted from {@link SelectionHandler} so that {@link ElementDragHandler} can
 * share the same logic without duplicating it.
 */
class ElementHitTest {

    private ElementHitTest() {
    }

    /**
     * Iterates all elements in the line and returns the index of the first element
     * whose hit rectangle contains {@code point}, or -1 if none.
     */
    static int hitTestElement(@NotNull LineComponent lc, @NotNull Point point) {
        var line = lc.getLine();
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
    static void buildElementHitRect(
        @NotNull LineComponent lc,
        @NotNull StaffElement element,
        @NotNull Rectangle out
    ) {
        var elementType = element.getType();
        var upper = element.isUpper();
        var sc = ScaleContext.getInstance();

        // Use notehead width (excludes flag extent); apply 4px minimum for narrow elements (AD-10)
        var widthPx = Math.max((int) Math.round(sc.toPixels(elementType.getNoteheadWidthSs())), 4);
        var heightPx = (int) Math.round(sc.toPixels(elementType.getElementHeightSs(upper)));

        // Get element X from LayoutResult (staff-space) and convert to pixels, so the
        // hit rect is in pixel coordinates consistent with the mouse-event point.
        var layoutResult = lc.getLayoutResult();
        var elementXSs = layoutResult != null ? layoutResult.getElementXSs(element) : 0.0;
        var elementXPx = (int) Math.round(sc.toPixels(elementXSs));
        var elementY = lc.staffPositionToYPx(element.getStaffPosition());
        var topOffsetPx = (int) Math.round(sc.toPixels(elementType.getTopYOffsetSs(upper)));
        out.setBounds(elementXPx, elementY + topOffsetPx, widthPx, heightPx);
    }
}
