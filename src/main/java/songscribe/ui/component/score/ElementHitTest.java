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
import songscribe.ui.layout.LayoutStylesheet;
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

        var sc = ScaleContext.getInstance();
        var pointXSs = sc.fromPixels(point.x);
        var pointYSs = sc.fromPixels(point.y);
        var helper = new Rectangle2D.Double();
        var song = line.getSong();

        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);

            // Skip the song's auto-maintained terminal (shared predicate).
            if (song != null && !song.isInteractable(element, line)) {
                continue;
            }

            buildElementHitRect(lc, element, helper);

            if (helper.contains(pointXSs, pointYSs)) {
                return elementIndex;
            }
        }

        return -1;
    }

    /**
     * Builds the staff-space-coordinate hit rectangle for the given element into {@code out},
     * expanding narrow/short elements to a minimum clickable size.
     */
    public static void buildElementHitRect(
        LineComponent lc,
        StaffElement element,
        Rectangle2D.Double out
    ) {
        buildElementHitRect(lc, element, out, true);
    }

    /**
     * Builds the staff-space-coordinate hit rectangle for the given element into {@code out}.
     *
     * @param expandToMinimum if true, expands narrow/short elements symmetrically
     *                        to {@link #MIN_HIT_SIZE_PX}. Use true for click hit testing,
     *                        false for drag-selection intersection.
     */
    public static void buildElementHitRect(
        LineComponent lc,
        StaffElement element,
        Rectangle2D.Double out,
        boolean expandToMinimum
    ) {
        var elementType = element.getType();
        var naturalWidthSs = elementType.getElementWidthSs();
        var naturalHeightSs = elementType.getFullElementHeightSs();

        // Get element position in staff spaces
        var layoutResult = lc.getLayoutResult();
        var elementXSs = layoutResult != null ? layoutResult.getElementXSs(element) : 0.0;
        var elementYSs = lc.getMiddleLineYSs()
            + LayoutStylesheet.spToSs(element.getStaffPosition());
        var topOffsetSs = elementType.getNoteheadTopOffsetSs();

        if (expandToMinimum) {
            var minHitSizeSs = ScaleContext.getInstance().fromPixels(MIN_HIT_SIZE_PX);

            // Expand symmetrically: distribute extra width/height evenly on both sides
            var xExpansionSs = Math.max(0, (minHitSizeSs - naturalWidthSs) / 2);
            var yExpansionSs = Math.max(0, (minHitSizeSs - naturalHeightSs) / 2);
            out.setRect(
                elementXSs - xExpansionSs,
                elementYSs + topOffsetSs - yExpansionSs,
                naturalWidthSs + 2 * xExpansionSs,
                naturalHeightSs + 2 * yExpansionSs
            );
        } else {
            out.setRect(elementXSs, elementYSs + topOffsetSs, naturalWidthSs, naturalHeightSs);
        }
    }
}
