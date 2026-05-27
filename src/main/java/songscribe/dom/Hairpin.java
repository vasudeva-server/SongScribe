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

import songscribe.smufl.Engraving;

/**
 * Base class for hairpin dynamic markings (crescendo and diminuendo).
 * <p>
 * A hairpin is a wedge-shaped marking placed above the staff that indicates
 * a gradual change in volume. The user can adjust the horizontal endpoints
 * and vertical position.
 */
public abstract sealed class Hairpin extends RangeElement
        permits Crescendo, Diminuendo {

    /**
     * Height of the hairpin opening
     */
    public static final double HAIRPIN_OPENING_HEIGHT_SS = 1.25;  // 10px

    /** User-controlled left-endpoint horizontal shift in staff-space units. */
    private double x1ShiftSs;

    /** User-controlled right-endpoint horizontal shift in staff-space units. */
    private double x2ShiftSs;

    /** User-controlled vertical shift in staff-space units. */
    private double yShiftSs;

    /**
     * Creates a hairpin spanning from anchor to end element.
     *
     * @param anchorElement The starting element of the hairpin
     * @param endElement    The ending element of the hairpin
     */
    protected Hairpin(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    public double getX1ShiftSs() {
        return x1ShiftSs;
    }

    public void setX1ShiftSs(double x1ShiftSs) {
        this.x1ShiftSs = x1ShiftSs;
    }

    public double getX2ShiftSs() {
        return x2ShiftSs;
    }

    public void setX2ShiftSs(double x2ShiftSs) {
        this.x2ShiftSs = x2ShiftSs;
    }

    public double getYShiftSs() {
        return yShiftSs;
    }

    public void setYShiftSs(double yShiftSs) {
        this.yShiftSs = yShiftSs;
    }

    /**
     * Returns the height of the hairpin opening in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return HAIRPIN_OPENING_HEIGHT_SS;
    }

    /**
     * Returns the horizontal span width for collision detection.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(HAIRPIN_OPENING_HEIGHT_SS, endXSs - anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS);
    }

    @Override
    public String toIndexString() {
        var base = getAnchorElementIndex() + "," + getEndElementIndex();
        if (x1ShiftSs != 0 || x2ShiftSs != 0 || yShiftSs != 0) {
            return base + "," + x1ShiftSs + "," + x2ShiftSs + "," + yShiftSs + ";";
        }
        return base + ";";
    }
}
