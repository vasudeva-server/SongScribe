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
 * Conversion between staff spaces and pixels.
 * One staff space equals the distance between two adjacent staff lines.
 */
public final class StaffSpaces {

    /** Pixels per staff space at the current (default) scale. */
    public static final double PIXELS_PER_STAFF_SPACE = 8.0;

    private StaffSpaces() {}

    /** Converts a value in staff spaces to pixels. */
    public static double toPixels(double staffSpaces) {
        return staffSpaces * PIXELS_PER_STAFF_SPACE;
    }

    /** Converts a value in pixels to staff spaces. */
    public static double fromPixels(double pixels) {
        return pixels / PIXELS_PER_STAFF_SPACE;
    }
}
