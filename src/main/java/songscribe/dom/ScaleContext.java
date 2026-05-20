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

package songscribe.dom;

import module java.desktop;

import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

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

    private volatile double pixelsPerStaffSpace = DEFAULT_PIXELS_PER_STAFF_SPACE;

    private ScaleContext() {}

    public static double getPixelsPerStaffSpace() {
        return INSTANCE.pixelsPerStaffSpace;
    }

    public static void setPixelsPerStaffSpace(double pxPerSs) {
        if (pxPerSs <= 0) {
            throw new IllegalArgumentException(
                "pixelsPerStaffSpace must be positive: " + pxPerSs
            );
        }

        INSTANCE.pixelsPerStaffSpace = pxPerSs;
    }

    /** Convert a value in staff-space units to pixels. */
    public static double ssToPx(double ss) {
        return INSTANCE.pixelsPerStaffSpace * ss;
    }

    /** Convert a value in staff-space units to pixels, rounded to the nearest integer. */
    public static int ssToRoundedPx(double ss) {
        return (int) Math.round(INSTANCE.pixelsPerStaffSpace * ss);
    }

    /** Convert a value in pixels to staff-space units. */
    public static double pxToSs(double px) {
        return px / INSTANCE.pixelsPerStaffSpace;
    }

    /** Returns {@code font} scaled from pixel units to staff-space units. */
    public static Font scaleFont(Font font) {
        return font.deriveFont((float) pxToSs(font.getSize()));
    }

    /** Returns the width of {@code text} in staff-space units for the given font. */
    public static double textWidthSs(Font font, String text) {
        return pxToSs(new TextLayout(text, font, GraphicUtils.SCREEN_FRC).getAdvance());
    }

    /** Returns the text height (ascent + descent) in staff-space units for the given font. */
    public static double textHeightSs(Font font) {
        var lm = font.getLineMetrics("", GraphicUtils.SCREEN_FRC);
        return pxToSs(lm.getAscent() + lm.getDescent());
    }

    /** Returns the font ascent in staff-space units for the given font. */
    public static double fontAscentSs(Font font) {
        return pxToSs(font.getLineMetrics("", GraphicUtils.SCREEN_FRC).getAscent());
    }

    /**
     * Returns the maximum ascent in staff-space units for the given font — the largest
     * ascent any glyph in the font can produce, suitable for sizing a stable text box that
     * accommodates accents, diacriticals, and tall caps regardless of which characters are
     * currently typed.
     */
    public static double fontMaxAscentSs(Font font) {
        return pxToSs(MyFontUtils.getFontMetrics(font).getMaxAscent());
    }

    /** Returns the font descent in staff-space units for the given font. */
    public static double fontDescentSs(Font font) {
        return pxToSs(font.getLineMetrics("", GraphicUtils.SCREEN_FRC).getDescent());
    }

    /**
     * Returns an {@link AffineTransform} that scales from staff-space
     * coordinates to pixel coordinates. Apply this to a {@code Graphics2D}
     * before rendering in staff-space units.
     */
    public static AffineTransform getScaleTransform() {
        return AffineTransform.getScaleInstance(INSTANCE.pixelsPerStaffSpace, INSTANCE.pixelsPerStaffSpace);
    }
}
