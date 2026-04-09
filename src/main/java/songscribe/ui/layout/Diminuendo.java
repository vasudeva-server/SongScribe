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
import songscribe.smufl.Engraving;

/**
 * Represents a diminuendo (gradually getting softer) hairpin marking.
 * <p>
 * Diminuendo hairpins open to the left (> shape) and are typically
 * placed above the staff. The user can adjust the horizontal endpoints
 * and vertical position.
 */
public class Diminuendo extends RangeElement {

    private int x1Shift = 0;
    private int x2Shift = 0;
    private int yShift = 0;

    /**
     * Creates a diminuendo spanning from anchor to end element.
     *
     * @param anchorElement The starting element of the diminuendo
     * @param endElement    The ending element of the diminuendo
     */
    public Diminuendo(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    /**
     * Returns the horizontal adjustment for the start point.
     */
    public int getX1Shift() {
        return x1Shift;
    }

    /**
     * Sets the horizontal adjustment for the start point.
     */
    public void setX1Shift(int x1Shift) {
        this.x1Shift = x1Shift;
    }

    /**
     * Returns the horizontal adjustment for the end point.
     */
    public int getX2Shift() {
        return x2Shift;
    }

    /**
     * Sets the horizontal adjustment for the end point.
     */
    public void setX2Shift(int x2Shift) {
        this.x2Shift = x2Shift;
    }

    /**
     * Returns the vertical adjustment.
     */
    public int getYShift() {
        return yShift;
    }

    /**
     * Sets the vertical adjustment.
     */
    public void setYShift(int yShift) {
        this.yShift = yShift;
    }

    /**
     * Returns the height of the hairpin opening in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return LayoutStylesheet.HAIRPIN_OPENING_HEIGHT_SS;
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
        return Math.max(getContentHeightSs(), endXSs - anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS);
    }

    @Override
    public double getContentWidthPx() {
        var anchor = getAnchorElement();
        var endElement = getEndElement();

        if (anchor == null || endElement == null) {
            return 0;
        }

        return Math.abs(endElement.getXSs() - anchor.getXSs()) + endElement.getContentWidthPx() + x1Shift + x2Shift;
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }
}
