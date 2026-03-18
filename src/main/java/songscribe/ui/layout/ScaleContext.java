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

import module java.desktop;

/**
 * Central scale context that defines the mapping between staff-space units
 * and pixel units.
 * <p>
 * In staff-space coordinates, the distance between two adjacent staff lines
 * is exactly 1.0. The {@code pixelsPerStaffSpace} factor converts these
 * abstract units to device pixels.
 * <p>
 * Currently a singleton; this will evolve to per-view instances when zoom
 * support is added.
 */
public final class ScaleContext {

    private static final ScaleContext INSTANCE = new ScaleContext();

    /** Default pixels per staff space, matching the legacy 8px staff line spacing. */
    public static final double DEFAULT_PIXELS_PER_STAFF_SPACE = 8.0;

    private double pixelsPerStaffSpace = DEFAULT_PIXELS_PER_STAFF_SPACE;

    private ScaleContext() {}

    public static ScaleContext getInstance() {
        return INSTANCE;
    }

    public double getPixelsPerStaffSpace() {
        return pixelsPerStaffSpace;
    }

    public void setPixelsPerStaffSpace(double pxPerSs) {
        if (pxPerSs <= 0) {
            throw new IllegalArgumentException(
                "pixelsPerStaffSpace must be positive: " + pxPerSs
            );
        }

        this.pixelsPerStaffSpace = pxPerSs;
    }

    /** Convert a value in staff-space units to pixels. */
    public double toPixels(double ss) {
        return ss * pixelsPerStaffSpace;
    }

    /** Convert a value in staff-space units to pixels, rounded to the nearest integer. */
    public int toRoundedPixels(double ss) {
        return (int) Math.round(ss * pixelsPerStaffSpace);
    }

    /** Convert a value in pixels to staff-space units. */
    public double fromPixels(double px) {
        return px / pixelsPerStaffSpace;
    }

    /**
     * Returns an {@link AffineTransform} that scales from staff-space
     * coordinates to pixel coordinates. Apply this to a {@code Graphics2D}
     * before rendering in staff-space units.
     */
    public AffineTransform getScaleTransform() {
        return AffineTransform.getScaleInstance(pixelsPerStaffSpace, pixelsPerStaffSpace);
    }
}
