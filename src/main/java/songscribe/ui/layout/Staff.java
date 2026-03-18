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

package songscribe.ui.layout;

import songscribe.ui.layout.ScaleContext;

/**
 * Represents the 5 horizontal staff lines within a Line.
 * <p>
 * The Staff element contains only the 5 staff lines themselves. Clef and KeySignature
 * are positioned absolutely as siblings and do not contribute to Staff's bounds.
 * <p>
 * Staff has margin to accommodate attribution text, which may trigger vertical
 * movement if there's a collision.
 */
public class Staff extends LineElement {

    /** Width of the staff lines (typically the full line width minus margins). */
    private double width;

    /**
     * Creates a staff with the specified width.
     *
     * @param width Width of the staff lines in pixels
     */
    public Staff(double width) {
        this.width = width;
    }

    /**
     * Returns the width of the staff.
     */
    public double getWidth() {
        return width;
    }

    /**
     * Sets the width of the staff.
     */
    public void setWidth(double width) {
        this.width = width;
    }

    @Override
    public double getContentWidth() {
        return width;
    }

    @Override
    public double getContentHeight() {
        return ScaleContext.getInstance().toPixels(LayoutStylesheet.STAFF_HEIGHT_SS);
    }

    /**
     * Returns the Y offset between staff lines.
     */
    public int getLineSpacing() {
        return ScaleContext.getInstance().toRoundedPixels(1.0);
    }

    /**
     * Returns the number of staff lines (always 5).
     */
    public int getLineCount() {
        return LayoutStylesheet.STAFF_LINE_COUNT;
    }

    /**
     * Returns the Y coordinate of the top staff line relative to staff position.
     */
    public double getTopLineY() {
        return 0;
    }

    /**
     * Returns the Y coordinate of the bottom staff line relative to staff position.
     */
    public double getBottomLineY() {
        return ScaleContext.getInstance().toPixels((LayoutStylesheet.STAFF_LINE_COUNT - 1) * 1.0);
    }

    /**
     * Returns the Y coordinate of the middle staff line (B line) relative to staff position.
     */
    public double getMiddleLineY() {
        return ScaleContext.getInstance().toPixels(2.0);
    }

    /**
     * Returns the Y coordinate of a specific staff line (0 = top, 4 = bottom).
     *
     * @param lineIndex Staff line index (0-4)
     * @return Y coordinate relative to staff position
     */
    public double getLineY(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= LayoutStylesheet.STAFF_LINE_COUNT) {
            throw new IndexOutOfBoundsException("Staff line index must be 0-4, got: " + lineIndex);
        }

        return ScaleContext.getInstance().toPixels(lineIndex);
    }
}
