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
package songscribe.layout;

import songscribe.dom.DocPx;
import songscribe.dom.ScaleContext;
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
        LETTER(8.5, 11.0, "letter"),
        A4(8.27, 11.69, "a4");

        private final double widthInches;
        private final double heightInches;
        private final String key;

        Size(double widthInches, double heightInches, String key) {
            this.widthInches = widthInches;
            this.heightInches = heightInches;
            this.key = key;
        }

        public double widthInches() {
            return widthInches;
        }

        public double heightInches() {
            return heightInches;
        }

        /** The {@link PrefsKey#PAGE_SIZE} string this size is stored under. */
        public String key() {
            return key;
        }
    }

    private PageModel() {}

    /** Returns the active page size from preferences. */
    public static Size getSize() {
        var value = Prefs.getString(PrefsKey.PAGE_SIZE);

        for (var size : Size.values()) {
            if (size.key.equalsIgnoreCase(value)) {
                return size;
            }
        }

        return Size.LETTER;
    }

    /** Full page width in document pixels (fixed document scale, independent of view zoom). */
    public static DocPx getPageWidthPx() {
        return new DocPx(inchesToPx(getSize().widthInches()));
    }

    /** Full page height in document pixels (fixed document scale, independent of view zoom). */
    public static DocPx getPageHeightPx() {
        return new DocPx(inchesToPx(getSize().heightInches()));
    }

    /** Top margin in document pixels (fixed 0.5"). */
    public static DocPx getTopMarginPx() {
        return new DocPx(inchesToPx(VERTICAL_MARGIN_INCHES));
    }

    /** Bottom margin in document pixels (fixed 0.5"). */
    public static DocPx getBottomMarginPx() {
        return new DocPx(inchesToPx(VERTICAL_MARGIN_INCHES));
    }

    /**
     * Horizontal margin per side in document pixels, computed to center
     * {@code lineWidthPx} (document pixels) within the page. Returns 0 if the
     * line width equals or exceeds the page width.
     */
    public static DocPx getHorizontalMarginPx(int lineWidthPx) {
        return new DocPx(Math.max(0, (getPageWidthPx().value() - lineWidthPx) / 2));
    }

    /** Content area width in pixels (page width minus default horizontal margins on each side). */
    public static int getContentAreaWidthPx() {
        return getPageWidthPx().roundedPx() - 2 * inchesToPx(DEFAULT_HORIZONTAL_MARGIN_INCHES);
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
        return ScaleContext.pxToSs(getContentAreaWidthPx());
    }

    private static int inchesToPx(double inches) {
        return GraphicUtils.Unit.INCH.convertToPixels(inches);
    }
}
