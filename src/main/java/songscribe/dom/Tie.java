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

package songscribe.dom;

/**
 * Represents a tie connecting two elements of the same pitch.
 * <p>
 * Ties connect exactly two elements and are rendered as a curved arc.
 * The placement (above or below) depends on the stem direction of the elements.
 */
public class Tie extends RangeElement {

    /**
     * Arc height of a tie curve.
     */
    public static final double TIE_ARC_HEIGHT_SS = 1.0;  // 8px

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
        return TIE_ARC_HEIGHT_SS;
    }

    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(1.0, endXSs - anchorXSs);
    }

    @Override
    public boolean isAbove() {
        // Ties go above if stem points down, below if stem points up
        var anchor = getAnchorElement();

        return anchor != null && anchor.getDirection().isUp();
    }
}
