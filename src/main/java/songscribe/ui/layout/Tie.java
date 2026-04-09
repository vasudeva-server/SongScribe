/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.layout;

import songscribe.music.StaffElement;

/**
 * Represents a tie connecting two elements of the same pitch.
 * <p>
 * Ties connect exactly two elements and are rendered as a curved arc.
 * The placement (above or below) depends on the stem direction of the elements.
 */
public class Tie extends RangeElement {

    /**
     * Creates a new tie between two elements.
     *
     * @param anchorElement The first (starting) element of the tie
     * @param endElement    The second (ending) element of the tie
     */
    public Tie(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    @Override
    public double getContentHeightSs() {
        return 1.0;  // 8px
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }

    @Override
    public boolean isAbove() {
        // Ties go above if stem points down (upper=true), below if stem points up
        var anchor = getAnchorElement();

        return anchor != null && anchor.isUpper();
    }

    @Override
    public double getContentWidthPx() {
        var anchor = getAnchorElement();
        var endElement = getEndElement();

        if (anchor == null || endElement == null) {
            return 0;
        }

        // Width spans from anchor to end element
        return Math.abs(endElement.getXSs() - anchor.getXSs()) + endElement.getContentWidthPx();
    }

    @Override
    public double getContentHeightPx() {
        // Tie curve height - approximate arc height
        return 8.0;
    }
}
