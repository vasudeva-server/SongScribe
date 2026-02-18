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

package songscribe.smufl;

/**
 * Bounding box for a SMuFL glyph, in staff spaces with Y-down (screen) convention.
 *
 * @param left   left edge X coordinate
 * @param top    top edge Y coordinate (smallest Y value, toward top of screen)
 * @param right  right edge X coordinate
 * @param bottom bottom edge Y coordinate (largest Y value, toward bottom of screen)
 */
public record BBox(double left, double top, double right, double bottom) {

    public double width() {
        return right - left;
    }

    public double height() {
        return bottom - top;
    }

    /**
     * Creates a BBox from SMuFL metadata bBoxSW/bBoxNE values, flipping Y
     * from SMuFL's Y-up convention to screen Y-down convention.
     *
     * @param swX bBoxSW x (left in both conventions)
     * @param swY bBoxSW y (bottom in Y-up)
     * @param neX bBoxNE x (right in both conventions)
     * @param neY bBoxNE y (top in Y-up)
     */
    public static BBox fromSMuFL(double swX, double swY, double neX, double neY) {
        return new BBox(swX, -neY, neX, -swY);
    }
}
