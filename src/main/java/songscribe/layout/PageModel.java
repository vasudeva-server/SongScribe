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
import songscribe.dom.DocumentScale;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.prefs.PrefsValue;

/**
 * All page geometry: physical page dimensions, margins, and content area
 * calculations.
 */
public final class PageModel {

    private static final double VERTICAL_MARGIN_INCHES = 0.5;

    static final double DEFAULT_HORIZONTAL_MARGIN_INCHES = 0.5;

    private static final double MIN_HORIZONTAL_MARGIN_INCHES = 0.25;

    /** Derived from A4 width minus minimum horizontal margins. */
    public static final double MAX_LINE_WIDTH_INCHES = 7.77;

    public static final double MIN_LINE_WIDTH_INCHES = 5.0;

    public enum Size implements PrefsValue {
        LETTER(8.5, 11.0, "letter"),
        A4(8.27, 11.69, "a4");

        private final double widthInches;
        private final double heightInches;
        private final String storedValue;

        Size(double widthInches, double heightInches, String storedValue) {
            this.widthInches = widthInches;
            this.heightInches = heightInches;
            this.storedValue = storedValue;
        }

        public double widthInches() {
            return widthInches;
        }

        public double heightInches() {
            return heightInches;
        }

        @Override
        public String storedValue() {
            return storedValue;
        }
    }

    private PageModel() {}

    public static Size getSize() {
        return Prefs.getChoice(PrefsKey.PAGE_SIZE, Size.class);
    }

    public static DocPx getPageWidthPx() {
        return inchesToPx(getSize().widthInches());
    }

    public static DocPx getPageHeightPx() {
        return inchesToPx(getSize().heightInches());
    }

    public static DocPx getTopMarginPx() {
        return inchesToPx(VERTICAL_MARGIN_INCHES);
    }

    public static DocPx getBottomMarginPx() {
        return inchesToPx(VERTICAL_MARGIN_INCHES);
    }

    public static DocPx getHorizontalMarginPx(DocPx lineWidth) {
        return new DocPx(Math.max(0, (getPageWidthPx().value() - lineWidth.value()) / 2));
    }

    public static double getDefaultLineWidthSs() {
        return DocumentScale.inchesToSs(
            getSize().widthInches() - 2 * DEFAULT_HORIZONTAL_MARGIN_INCHES);
    }

    private static DocPx inchesToPx(double inches) {
        return DocumentScale.ssToPx(DocumentScale.inchesToSs(inches));
    }
}
