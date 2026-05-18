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

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.GraphicUtils;

/**
 * Singleton that encapsulates all page geometry: physical page dimensions,
 * margins, and content area calculations. Reads the active page size from
 * the {@link PrefsKey#PAGE_SIZE} preference.
 */
@SuppressWarnings("SameReturnValue")
public final class PageModel {

    /** Vertical margin (top and bottom) in inches. */
    private static final double VERTICAL_MARGIN_INCHES = 0.5;

    /** Default horizontal margin per side in inches. */
    static final double DEFAULT_HORIZONTAL_MARGIN_INCHES = 0.5;

    /** Minimum horizontal margin per side in inches. */
    private static final double MIN_HORIZONTAL_MARGIN_INCHES = 0.25;

    /** Maximum line width in inches, derived from A4 width minus minimum horizontal margins. */
    public static final double MAX_LINE_WIDTH_INCHES = 7.77;

    /** Minimum line width in inches. */
    public static final double MIN_LINE_WIDTH_INCHES = 5.0;

    /** Physical page sizes. */
    public enum Size {
        LETTER(8.5, 11.0),
        A4(8.27, 11.69);

        private final double widthInches;
        private final double heightInches;

        Size(double widthInches, double heightInches) {
            this.widthInches = widthInches;
            this.heightInches = heightInches;
        }

        public double widthInches() {
            return widthInches;
        }

        public double heightInches() {
            return heightInches;
        }
    }

    private PageModel() {}

    /** Returns the active page size from preferences. */
    public static Size getSize() {
        var value = Prefs.getString(PrefsKey.PAGE_SIZE);
        return "a4".equalsIgnoreCase(value) ? Size.A4 : Size.LETTER;
    }

    /** Full page width in pixels. */
    public static int getPageWidthPx() {
        return inchesToPx(getSize().widthInches());
    }

    /** Full page height in pixels. */
    public static int getPageHeightPx() {
        return inchesToPx(getSize().heightInches());
    }

    /** Top margin in pixels (fixed 0.5"). */
    public static int getTopMarginPx() {
        return inchesToPx(VERTICAL_MARGIN_INCHES);
    }

    /** Bottom margin in pixels (fixed 0.5"). */
    public static int getBottomMarginPx() {
        return inchesToPx(VERTICAL_MARGIN_INCHES);
    }

    /**
     * Horizontal margin per side in pixels, computed to center the content
     * within the page. Returns 0 if the line width equals or exceeds the page width.
     */
    public static int getHorizontalMarginPx(int lineWidthPx) {
        var pageWidthPx = getPageWidthPx();
        return Math.max(0, (pageWidthPx - lineWidthPx) / 2);
    }

    /** Content area width in pixels (page width minus default horizontal margins on each side). */
    public static int getContentAreaWidthPx() {
        return getPageWidthPx() - 2 * inchesToPx(DEFAULT_HORIZONTAL_MARGIN_INCHES);
    }

    /** Maximum line width in inches (constant, derived from A4 constraint). */
    public static double getMaxLineWidthInches() {
        return MAX_LINE_WIDTH_INCHES;
    }

    /** Minimum line width in inches. */
    public static double getMinLineWidthInches() {
        return MIN_LINE_WIDTH_INCHES;
    }

    /** Default line width in staff spaces, based on the content area width. */
    public static double getDefaultLineWidthSs() {
        return ScaleContext.getInstance().pxToSs(getContentAreaWidthPx());
    }

    private static int inchesToPx(double inches) {
        return GraphicUtils.Unit.INCH.convertToPixels(inches);
    }
}
