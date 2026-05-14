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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.util.GraphicUtils;

class PageModelTest extends UnitTest {

    private final PageModel model = PageModel.getInstance();

    @AfterEach
    void resetPageSize() {
        Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "letter");
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
            assertThat(model.getSize()).isEqualTo(PageModel.Size.LETTER);
        }

        @Test
        void returnsA4WhenPrefSet() {
            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "a4");
            assertThat(model.getSize()).isEqualTo(PageModel.Size.A4);
        }

        @Test
        void caseInsensitive() {
            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "A4");
            assertThat(model.getSize()).isEqualTo(PageModel.Size.A4);
        }

        @Test
        void unknownValueDefaultsToLetter() {
            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "tabloid");
            assertThat(model.getSize()).isEqualTo(PageModel.Size.LETTER);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PageDimensionsPx {

        @Test
        void letterPageWidthPx() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(8.5);
            assertThat(model.getPageWidthPx()).isEqualTo(expected);
        }

        @Test
        void letterPageHeightPx() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(11.0);
            assertThat(model.getPageHeightPx()).isEqualTo(expected);
        }

        @Test
        void a4PageWidthPx() {
            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "a4");
            var expected = GraphicUtils.Unit.INCH.convertToPixels(8.27);
            assertThat(model.getPageWidthPx()).isEqualTo(expected);
        }

        @Test
        void a4PageHeightPx() {
            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "a4");
            var expected = GraphicUtils.Unit.INCH.convertToPixels(11.69);
            assertThat(model.getPageHeightPx()).isEqualTo(expected);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Margins {

        @Test
        void topMarginIsHalfInch() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(0.5);
            assertThat(model.getTopMarginPx()).isEqualTo(expected);
        }

        @Test
        void bottomMarginIsHalfInch() {
            var expected = GraphicUtils.Unit.INCH.convertToPixels(0.5);
            assertThat(model.getBottomMarginPx()).isEqualTo(expected);
        }

        @Test
        void horizontalMarginCentersContent() {
            var pageWidthPx = model.getPageWidthPx();
            var lineWidthPx = 400;
            var expected = (pageWidthPx - lineWidthPx) / 2;
            assertThat(model.getHorizontalMarginPx(lineWidthPx)).isEqualTo(expected);
        }

        @Test
        void horizontalMarginReturnsZeroWhenLineWidthExceedsPage() {
            var lineWidthPx = model.getPageWidthPx() + 100;
            assertThat(model.getHorizontalMarginPx(lineWidthPx)).isEqualTo(0);
        }

        @Test
        void horizontalMarginReturnsZeroWhenLineWidthEqualsPage() {
            var lineWidthPx = model.getPageWidthPx();
            assertThat(model.getHorizontalMarginPx(lineWidthPx)).isEqualTo(0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContentArea {

        @Test
        void contentAreaWidthAccountsForDefaultMargins() {
            var defaultMarginPx = GraphicUtils.Unit.INCH.convertToPixels(
                PageModel.DEFAULT_HORIZONTAL_MARGIN_INCHES
            );
            var expected = model.getPageWidthPx() - 2 * defaultMarginPx;
            assertThat(model.getContentAreaWidthPx()).isEqualTo(expected);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineWidthConstants {

        @Test
        void maxLineWidthInches() {
            assertThat(model.getMaxLineWidthInches()).isEqualTo(7.77);
        }

        @Test
        void minLineWidthInches() {
            assertThat(model.getMinLineWidthInches()).isEqualTo(5.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DefaultLineWidth {

        @Test
        void defaultLineWidthSsMatchesContentArea() {
            var expected = ScaleContext.getInstance().pxToSs(model.getContentAreaWidthPx());
            assertThat(model.getDefaultLineWidthSs()).isCloseTo(expected, within(0.001));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PageSizeChange {

        @Test
        void pageSizeChangesWhenPrefChanges() {
            assertThat(model.getSize()).isEqualTo(PageModel.Size.LETTER);

            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "a4");
            assertThat(model.getSize()).isEqualTo(PageModel.Size.A4);

            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "letter");
            assertThat(model.getSize()).isEqualTo(PageModel.Size.LETTER);
        }

        @Test
        void pageWidthChangesWithSize() {
            var letterWidth = model.getPageWidthPx();

            Prefs.getInstance().put(PrefsKey.PAGE_SIZE, "a4");
            var a4Width = model.getPageWidthPx();

            assertThat(a4Width).isLessThan(letterWidth);
        }
    }
}
