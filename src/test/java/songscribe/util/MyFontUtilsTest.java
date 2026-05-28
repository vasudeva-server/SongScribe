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

package songscribe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Font;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class MyFontUtilsTest extends UnitTest {

    @BeforeAll
    static void installBundledFonts() {
        // Reset the lazy font cache so we can install bundled fonts before the first load.
        MyFontUtils.resetFontCache();
        MyFontUtils.installLocalFont("LatoPlus-Regular.otf");
        MyFontUtils.installLocalFont("LatoPlus-Bold.otf");
        MyFontUtils.installLocalFont("LatoPlus-Italic.otf");
        MyFontUtils.installLocalFont("LatoPlus-BoldItalic.otf");
    }

    @Nested
    class ParsePSName {

        @Test
        void testHyphenSeparatorSplitsOnLastHyphen() {
            var result = MyFontUtils.parsePSName("Lato-Bold");
            assertThat(result.family()).isEqualTo("Lato");
            assertThat(result.style()).isEqualTo("Bold");
        }

        @Test
        void testHyphenInFamilyNameUsesLastPartAsStyle() {
            var result = MyFontUtils.parsePSName("Source-Sans-Pro-Bold");
            assertThat(result.family()).isEqualTo("Source-Sans-Pro");
            assertThat(result.style()).isEqualTo("Bold");
        }

        @Test
        void testUnderscoreSeparatorSplitsOnFirstUnderscore() {
            var result = MyFontUtils.parsePSName("LatoPlus_BoldItalic");
            assertThat(result.family()).isEqualTo("LatoPlus");
            assertThat(result.style()).isEqualTo("BoldItalic");
        }

        @Test
        void testUnderscoreStyleWithHyphensStripsHyphens() {
            var result = MyFontUtils.parsePSName("LatoPlus_Semi-Bold");
            assertThat(result.family()).isEqualTo("LatoPlus");
            assertThat(result.style()).isEqualTo("SemiBold");
        }

        @Test
        void testNoSeparatorReturnsEmptyStyle() {
            var result = MyFontUtils.parsePSName("Damascus");
            assertThat(result.family()).isEqualTo("Damascus");
            assertThat(result.style()).isEmpty();
        }
    }

    @Nested
    class ParseStyle {

        @Test
        void testOsfNormalization() {
            assertThat(MyFontUtils.parseStyle("OsF")).isEqualTo("Oldstyle Figures");
        }

        @Test
        void testCompoundStyleLowercasesSecondWord() {
            assertThat(MyFontUtils.parseStyle("SemiBold")).isEqualTo("Semibold");
        }

        @Test
        void testCamelCaseIsSplitIntoWords() {
            assertThat(MyFontUtils.parseStyle("BoldItalic")).isEqualTo("Bold Italic");
        }

        @Test
        void testAbbreviationScExpandsToSmallCaps() {
            assertThat(MyFontUtils.parseStyle("SC")).isEqualTo("Small Caps");
        }

        @Test
        void testAbbreviationCondExpandsToCondensed() {
            assertThat(MyFontUtils.parseStyle("Cond")).isEqualTo("Condensed");
        }
    }

    @Nested
    class GetStyleDescription {

        @Test
        void testBundledBoldFontReturnsBold() {
            var font = MyFontUtils.createFont("LatoPlus-Bold", 12);
            assertThat(MyFontUtils.getStyleDescription(font)).isEqualTo("Bold");
        }

        @Test
        void testDamascusStyleExtractsStyleFromPsName() {
            // PS name has no separator → psStyle="" but psFamily starts with fontFamily,
            // and the suffix matches STYLE_PATTERN.
            var font = mock(Font.class);
            when(font.getPSName()).thenReturn("DamascusBold");
            when(font.getFamily()).thenReturn("Damascus");
            assertThat(MyFontUtils.getStyleDescription(font)).isEqualTo("Bold");
        }

        @Test
        void testPsNameEqualsJavaFamilyReturnsRegular() {
            var font = mock(Font.class);
            when(font.getPSName()).thenReturn("SomeFont");
            when(font.getFamily()).thenReturn("SomeFont");
            assertThat(MyFontUtils.getStyleDescription(font)).isEqualTo("Regular");
        }
    }

    @Nested
    class GetFullFontDescription {

        @Test
        void testFormatsAsFamilyStyleSizePt() {
            var font = MyFontUtils.createFont("LatoPlus-Bold", 12);
            assertThat(MyFontUtils.getFullFontDescription(font))
                .isEqualTo(font.getFamily() + " Bold 12 pt");
        }
    }

    @Nested
    class CreateFont {

        @Test
        void testKnownPsNameReturnsMatchingFont() {
            var font = MyFontUtils.createFont("LatoPlus-Bold", 24);
            assertThat(font).isNotNull();
            assertThat(font.getSize()).isEqualTo(24);
            assertThat(font.getPSName()).isEqualTo("LatoPlus-Bold");
        }

        @Test
        void testUnknownPsNameReturnsFallbackFont() {
            var font = MyFontUtils.createFont("NonExistent-BogusFont-12345", 16);
            assertThat(font).isNotNull();
            assertThat(font.getSize()).isEqualTo(16);
            var fallback = MyFontUtils.getUIFont("Label.font");
            assertThat(font.getPSName()).isEqualTo(fallback.getPSName());
        }
    }
}
