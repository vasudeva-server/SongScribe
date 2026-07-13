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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.layout.PageModel;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.GraphicUtils;

class PageModelTest extends UnitTest {

    private static final double LETTER_WIDTH_INCHES = 8.5;
    private static final double HORIZONTAL_MARGIN_INCHES = 0.5;
    private static final double SS_TOLERANCE = 0.001;

    /**
     * LETTER content-area width in px, derived from the page-size spec rather than
     * from the accessors under test, so the oracle stays independent of them while
     * remaining exact at any DPI (it converts each spec dimension exactly as
     * production does). The page is 8.5" wide with a 0.5" margin on each side.
     */
    private static int letterContentWidthPx() {
        var pageWidthPx = GraphicUtils.Unit.INCH.convertToPixels(LETTER_WIDTH_INCHES);
        var marginPx = GraphicUtils.Unit.INCH.convertToPixels(HORIZONTAL_MARGIN_INCHES);
        return pageWidthPx - 2 * marginPx;
    }

    @AfterEach
    void resetPageSize() {
        Prefs.put(PrefsKey.PAGE_SIZE, "letter");
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SizeEnum {

        @Test
        void letterDimensions() {
            assertThat(PageModel.Size.LETTER.widthInches()).isEqualTo(8.5);
            assertThat(PageModel.Size.LETTER.heightInches()).isEqualTo(11.0);
        }

        @Test
        void a4Dimensions() {
            assertThat(PageModel.Size.A4.widthInches()).isEqualTo(8.27);
            assertThat(PageModel.Size.A4.heightInches()).isEqualTo(11.69);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PageSizeFromPrefs {

        @Test
        void defaultIsLetter() {
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.LETTER);
        }

        @Test
        void returnsA4WhenPrefSet() {
            Prefs.put(PrefsKey.PAGE_SIZE, "a4");
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.A4);
        }

        @Test
        void caseInsensitive() {
            Prefs.put(PrefsKey.PAGE_SIZE, "A4");
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.A4);
        }

        @Test
        void unknownValueDefaultsToLetter() {
            Prefs.put(PrefsKey.PAGE_SIZE, "tabloid");
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.LETTER);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PageDimensionsPx {

        @Test
        void letterPageWidthPx() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(8.5);
            assertThat(PageModel.getPageWidthPx().value()).isEqualTo((double) expected);
        }

        @Test
        void letterPageHeightPx() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(11.0);
            assertThat(PageModel.getPageHeightPx().value()).isEqualTo((double) expected);
        }

        @Test
        void a4PageWidthPx() {
            Prefs.put(PrefsKey.PAGE_SIZE, "a4");
            var expected = GraphicUtils.Unit.INCH.convertToPixels(8.27);
            assertThat(PageModel.getPageWidthPx().value()).isEqualTo((double) expected);
        }

        @Test
        void a4PageHeightPx() {
            Prefs.put(PrefsKey.PAGE_SIZE, "a4");
            var expected = GraphicUtils.Unit.INCH.convertToPixels(11.69);
            assertThat(PageModel.getPageHeightPx().value()).isEqualTo((double) expected);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Margins {

        @Test
        void topMarginIsHalfInch() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(0.5);
            assertThat(PageModel.getTopMarginPx().value()).isEqualTo((double) expected);
        }

        @Test
        void bottomMarginIsHalfInch() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(0.5);
            assertThat(PageModel.getBottomMarginPx().value()).isEqualTo((double) expected);
        }

        @Test
        void horizontalMarginCentersContent() {
            var pageWidthPx = PageModel.getPageWidthPx().value();
            var lineWidthPx = 400;
            var expected = (pageWidthPx - lineWidthPx) / 2;
            assertThat(PageModel.getHorizontalMarginPx(lineWidthPx).value()).isEqualTo(expected);
        }

        @Test
        void horizontalMarginReturnsZeroWhenLineWidthExceedsPage() {
            var lineWidthPx = PageModel.getPageWidthPx().roundedPx() + 100;
            assertThat(PageModel.getHorizontalMarginPx(lineWidthPx).value()).isEqualTo(0.0);
        }

        @Test
        void horizontalMarginReturnsZeroWhenLineWidthEqualsPage() {
            var lineWidthPx = PageModel.getPageWidthPx().roundedPx();
            assertThat(PageModel.getHorizontalMarginPx(lineWidthPx).value()).isEqualTo(0.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContentArea {

        @Test
        void contentAreaWidthAccountsForDefaultMargins() {
            assertThat(PageModel.getContentAreaWidthPx()).isEqualTo(letterContentWidthPx());
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineWidthConstants {

        @Test
        void maxLineWidthInches() {
            assertThat(PageModel.getMaxLineWidthInches()).isEqualTo(7.77);
        }

        @Test
        void minLineWidthInches() {
            assertThat(PageModel.getMinLineWidthInches()).isEqualTo(5.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DefaultLineWidth {

        @Test
        void defaultLineWidthSsMatchesContentArea() {
            // Expected px derived independently of getContentAreaWidthPx(), so this no
            // longer reduces to the tautology pxToSs(x) == pxToSs(x).
            var expectedSs = ScaleContext.pxToSs(letterContentWidthPx());
            assertThat(PageModel.getDefaultLineWidthSs()).isCloseTo(expectedSs, within(SS_TOLERANCE));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PageSizeChange {

        @Test
        void pageSizeChangesWhenPrefChanges() {
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.LETTER);

            Prefs.put(PrefsKey.PAGE_SIZE, "a4");
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.A4);

            Prefs.put(PrefsKey.PAGE_SIZE, "letter");
            assertThat(PageModel.getSize()).isEqualTo(PageModel.Size.LETTER);
        }

        @Test
        void pageWidthChangesWithSize() {
            var letterWidth = PageModel.getPageWidthPx().value();

            Prefs.put(PrefsKey.PAGE_SIZE, "a4");
            var a4Width = PageModel.getPageWidthPx().value();

            assertThat(a4Width).isLessThan(letterWidth);
        }
    }
}
