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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import songscribe.UnitTest;
import songscribe.font.SourceSans3Font;

class MyFontUtilsTest extends UnitTest {

    @BeforeAll
    static void installBundledFonts() {
        // Reset the lazy font cache so we can install bundled fonts before the first load.
        MyFontUtils.resetFontCache();
        SourceSans3Font.install();
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
            var font = MyFontUtils.createFont("SourceSans3SongScribe-Bold", 12);
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
            var font = MyFontUtils.createFont("SourceSans3SongScribe-Bold", 12);
            assertThat(MyFontUtils.getFullFontDescription(font))
                .isEqualTo(SourceSans3Font.FAMILY + " Bold 12 pt");
        }
    }

    @Nested
    class CreateFont {

        // Arbitrary size; these tests assert PS-name resolution, not the size.
        private static final int FALLBACK_FONT_SIZE = 14;

        @Test
        void testKnownPsNameReturnsMatchingFont() {
            var font = MyFontUtils.createFont("SourceSans3SongScribe-Bold", 24);
            assertThat(font).isNotNull();
            assertThat(font.getSize()).isEqualTo(24);
            assertThat(font.getPSName()).isEqualTo("SourceSans3SongScribe-Bold");
        }

        @Test
        void testUnknownPsNameThatIsAnInstalledFamilyResolvesToThatFamily() {
            // A hand-edited or older document may store a display family (e.g.
            // "SignPainter") rather than a PostScript name. When the PS-name lookup
            // misses but the name IS an installed family, createFont must resolve to
            // that family — not the Source Sans 3 approximation. Pick any installed
            // non-Source-Sans-3 family that is not also a PS name, so the family
            // branch (not the PS branch) is exercised and, without the fallback, the
            // name would resolve to the Source Sans 3 family instead.
            var psNames = new HashSet<String>();

            for (var font : MyFontUtils.getAllFonts()) {
                psNames.add(font.getPSName());
            }

            String family = null;

            for (var font : MyFontUtils.getAllFonts()) {
                var candidate = font.getFamily();

                if (!candidate.equals(SourceSans3Font.FAMILY) && !psNames.contains(candidate)) {
                    family = candidate;
                    break;
                }
            }

            if (family == null) {
                assumeTrue(false, "no installed non-Source-Sans-3 family available to exercise the fallback");
                return;
            }

            var font = MyFontUtils.createFont(family, 18);
            assertThat(font.getFamily())
                .as("an installed family name resolves to that family, not the Source Sans 3 approximation")
                .isEqualTo(family);
            assertThat(font.getSize()).isEqualTo(18);
        }

        @Test
        void testUnknownPsNameWithNoWeightResolvesToSourceSans3Regular() {
            // The weight-matched fallback maps any unrecognized PS name to the closest
            // Source Sans 3 SongScribe face by weight. When the style token contains no
            // weight/italic keyword, the fallback resolves to Regular.
            var font = MyFontUtils.createFont("NonExistent-BogusFont-12345", 16);
            assertThat(font).isNotNull();
            assertThat(font.getSize()).isEqualTo(16);
            assertThat(font.getPSName()).isEqualTo("SourceSans3SongScribe-Regular");
        }

        @Test
        void testOldSemiboldNameResolvesToSourceSans3SemiBold() {
            // Old documents may reference SourceSansProSongScribe-Semibold, which is no longer
            // registered. The weight-matched fallback must map it to the closest Source Sans 3
            // SongScribe face by weight: Semibold → SourceSans3SongScribe-SemiBold.
            var font = MyFontUtils.createFont("SourceSansProSongScribe-Semibold", 14);
            assertThat(font).isNotNull();
            assertThat(font.getPSName()).isEqualTo("SourceSans3SongScribe-SemiBold");
        }

        @Test
        void testOldItalicSuffixResolvesToSourceSans3Italic() {
            // Old documents may use the "-It" abbreviation for italic, e.g.
            // SourceSansProSongScribe-It. The weight-matched fallback normalizes "It" → "Italic"
            // and maps it to SourceSans3SongScribe-Italic.
            var font = MyFontUtils.createFont("SourceSansProSongScribe-It", 14);
            assertThat(font).isNotNull();
            assertThat(font.getPSName()).isEqualTo("SourceSans3SongScribe-Italic");
        }

        @ParameterizedTest
        @CsvSource({
            // An unregistered name carrying each weight (and combined weight+italic)
            // exercises every branch of the weight-matched fallback.
            "Unregistered-Medium,         SourceSans3SongScribe-Medium",
            "Unregistered-Bold,           SourceSans3SongScribe-Bold",
            "Unregistered-MediumItalic,   SourceSans3SongScribe-MediumItalic",
            "Unregistered-SemiboldItalic, SourceSans3SongScribe-SemiBoldItalic",
            "Unregistered-BoldItalic,     SourceSans3SongScribe-BoldItalic",
        })
        void testUnregisteredWeightResolvesToClosestSourceSans3Face(
            String psName,
            String expectedPsName
        ) {
            var font = MyFontUtils.createFont(psName, FALLBACK_FONT_SIZE);
            assertThat(font).isNotNull();
            assertThat(font.getPSName()).isEqualTo(expectedPsName);
        }
    }
}
