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

package songscribe.font;

import java.awt.Font;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The naming of a face for a person. Every case here is a pure string function: nothing
 * in this class depends on which faces the machine running the suite has installed, and
 * the {@link FontDescription#style} rows describe a face with a stubbed {@link Font}
 * rather than looking for one that happens to be present.
 */
class FontDescriptionTest extends UnitTest {

    private record ParsePSNameCase(
        String description,
        String psName,
        String expectedFamily,
        String expectedStyle
    ) {}

    private record ParseStyleCase(String description, String style, String expected) {}

    /**
     * @param psName the PostScript name the font file declares
     * @param family the family name AWT reports for the same face, which
     *               {@link FontDescription#style} compares the parsed style against
     */
    private record StyleCase(
        String description,
        String psName,
        String family,
        String expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsePSNameCases")
    void testParsePSNameSplitsFamilyFromStyle(ParsePSNameCase testCase) {
        var parsed = FontDescription.parsePSName(testCase.psName());

        assertThat(parsed.family()).isEqualTo(testCase.expectedFamily());
        assertThat(parsed.style()).isEqualTo(testCase.expectedStyle());
    }

    static Stream<ParsePSNameCase> parsePSNameCases() {
        return Stream.of(
            new ParsePSNameCase(
                "hyphen separates family from style",
                "Lato-Bold", "Lato", "Bold"
            ),
            new ParsePSNameCase(
                "a family that itself contains hyphens keeps all but the last part",
                "Source-Sans-Pro-Bold", "Source-Sans-Pro", "Bold"
            ),
            new ParsePSNameCase(
                "an underscore separates family from style ahead of any hyphen",
                "LatoPlus_BoldItalic", "LatoPlus", "BoldItalic"
            ),
            new ParsePSNameCase(
                "hyphens inside an underscore-separated style are dropped",
                "LatoPlus_Semi-Bold", "LatoPlus", "SemiBold"
            ),
            new ParsePSNameCase(
                "a name with no separator is all family and no style",
                "Damascus", "Damascus", ""
            )
        );
    }

    /*
      One table rather than one method per normalization step: every case feeds a string to
      parseStyle and reads a string back, so what varies between an abbreviation case and a
      compound-weight case is the input and the expected value alone.
    */
    @ParameterizedTest(name = "{0}")
    @MethodSource("parseStyleCases")
    void testParseStyleNormalizesStyleToWholeWords(ParseStyleCase testCase) {
        assertThat(FontDescription.parseStyle(testCase.style())).isEqualTo(testCase.expected());
    }

    static Stream<ParseStyleCase> parseStyleCases() {
        return Stream.of(
            new ParseStyleCase("It expands to Italic", "It", "Italic"),
            new ParseStyleCase("Ita expands to Italic", "Ita", "Italic"),
            new ParseStyleCase("SC expands to Small Caps", "SC", "Small Caps"),
            new ParseStyleCase("Cn expands to Condensed", "Cn", "Condensed"),
            new ParseStyleCase("Cond expands to Condensed", "Cond", "Condensed"),
            new ParseStyleCase("Ext expands to Extended", "Ext", "Extended"),
            new ParseStyleCase("Exp expands to Expanded", "Exp", "Expanded"),
            new ParseStyleCase("DB expands to Demi Bold", "DB", "Demi Bold"),
            new ParseStyleCase("Supp expands to Supplemental", "Supp", "Supplemental"),
            new ParseStyleCase("OsF expands to Oldstyle Figures", "OsF", "Oldstyle Figures"),
            new ParseStyleCase("SemiBold stays one word", "SemiBold", "Semibold"),
            new ParseStyleCase("DemiBold stays one word", "DemiBold", "Demibold"),
            new ParseStyleCase("ExtraLight stays one word", "ExtraLight", "Extralight"),
            new ParseStyleCase("UltraHeavy stays one word", "UltraHeavy", "Ultraheavy"),
            new ParseStyleCase(
                "run-together camel case becomes separate words",
                "BoldItalic", "Bold Italic"
            ),
            new ParseStyleCase(
                "a compound weight stays one word beside an expanded abbreviation",
                "SemiBoldIt", "Semibold Italic"
            ),
            new ParseStyleCase("a face with no style has no words", "", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("styleCases")
    void testStyleNamesTheFaceWithoutRepeatingTheFamily(StyleCase testCase) {
        var font = mock(Font.class);
        when(font.getPSName()).thenReturn(testCase.psName());
        when(font.getFamily()).thenReturn(testCase.family());

        var style = FontDescription.style(font);

        assertThat(style).isEqualTo(testCase.expected());
        assertThat(style).as("a style description is never empty").isNotEmpty();
    }

    static Stream<StyleCase> styleCases() {
        return Stream.of(
            new StyleCase(
                "a family whose own face repeats its weight is Regular",
                "SourceSans3SemiBold-SemiBold", "Source Sans 3 SemiBold", "Regular"
            ),
            new StyleCase(
                "only the repeated weight is dropped from a compound style",
                "SourceSans3SemiBold-SemiBoldItalic", "Source Sans 3 SemiBold", "Italic"
            ),
            new StyleCase(
                "a style the family does not repeat is kept whole",
                "SourceSans3-SemiBoldItalic", "Source Sans 3", "Semibold Italic"
            ),
            new StyleCase(
                "a PostScript name that is the family alone is Regular",
                "SomeFont", "SomeFont", "Regular"
            ),
            new StyleCase(
                "a weight run onto the family with no separator is still the style",
                "DamascusBold", "Damascus", "Bold"
            ),
            new StyleCase(
                "what follows the family is family, not style, when it names no weight",
                "SomeFontSupplement", "SomeFont", "Regular"
            ),
            new StyleCase(
                "a PostScript name unrelated to the family names no style",
                "OtherFont", "SomeFont", "Regular"
            )
        );
    }
}
