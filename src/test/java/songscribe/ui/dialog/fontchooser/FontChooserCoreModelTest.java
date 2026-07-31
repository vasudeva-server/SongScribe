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

package songscribe.ui.dialog.fontchooser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import songscribe.UnitTest;
import songscribe.util.MyFontUtils;

class FontChooserCoreModelTest extends UnitTest {

    // ---------------------------------------------------------------------------
    // FontNameComparator
    // ---------------------------------------------------------------------------

    @Nested
    class FontNameComparatorTests {

        private final FontNameComparator comparator = new FontNameComparator();

        @Test
        void testLowerCaseNameSortsAfterUpperCaseName() {
            // Lowercase letters have higher codepoints than uppercase in ASCII,
            // so "arial" sorts AFTER "Arial".
            var arialLower = new Font("arial", Font.PLAIN, 12);
            var arialUpper = new Font("Arial", Font.PLAIN, 12);
            assertThat(comparator.compare(arialLower, arialUpper)).isGreaterThan(0);
        }

        @Test
        void testIdenticalNamesCompareAsZero() {
            var font1 = new Font("Helvetica", Font.PLAIN, 12);
            var font2 = new Font("Helvetica", Font.BOLD, 14);
            assertThat(comparator.compare(font1, font2)).isEqualTo(0);
        }

        @Test
        void testAlphabeticalOrderingByName() {
            var apple = new Font("Apple", Font.PLAIN, 12);
            var zebra = new Font("Zebra", Font.PLAIN, 12);
            assertThat(comparator.compare(apple, zebra)).isLessThan(0);
            assertThat(comparator.compare(zebra, apple)).isGreaterThan(0);
        }
    }

    // ---------------------------------------------------------------------------
    // FontFamily
    // ---------------------------------------------------------------------------

    @Nested
    class FontFamilyTests {

        @Test
        void testAddAccumulatesFontsOrderedByFontNameComparator() {
            // Add fonts in reverse alphabetical order; TreeSet should sort them ascending.
            var family = new FontFamily("TestFamily");
            var zebra = new Font("Zebra Bold", Font.BOLD, 12);
            var apple = new Font("Apple Regular", Font.PLAIN, 12);
            var middle = new Font("Middle Italic", Font.ITALIC, 12);

            family.add(zebra);
            family.add(apple);
            family.add(middle);

            var styles = family.getStyles();
            assertThat(styles).hasSize(3);
            assertThat(styles).containsExactly(apple, middle, zebra);
        }

        @Test
        void testAddDuplicateNameIsIgnored() {
            // TreeSet deduplicates by comparator equality — same name = same position.
            var family = new FontFamily("TestFamily");
            var font1 = new Font("Same Name", Font.PLAIN, 12);
            var font2 = new Font("Same Name", Font.BOLD, 14);

            family.add(font1);
            family.add(font2);

            // FontNameComparator returns 0 for identical names, so only one is kept.
            assertThat(family.getStyles()).hasSize(1);
        }
    }

    // ---------------------------------------------------------------------------
    // FontFamilies
    // ---------------------------------------------------------------------------

    @Nested
    class FontFamiliesTests {

        @Test
        @SuppressWarnings("NullAway")
        void testAddGroupsFontsByFamily() {
            // Two fonts sharing one family + one font from another family → two groups.
            var families = new FontFamilies();
            var font1 = Mockito.mock(Font.class);
            var font2 = Mockito.mock(Font.class);
            var font3 = Mockito.mock(Font.class);

            when(font1.getFamily()).thenReturn("Lato");
            when(font1.getName()).thenReturn("Lato Regular");
            when(font2.getFamily()).thenReturn("Lato");
            when(font2.getName()).thenReturn("Lato Bold");
            when(font3.getFamily()).thenReturn("Arial");
            when(font3.getName()).thenReturn("Arial Regular");

            families.add(font1);
            families.add(font2);
            families.add(font3);

            assertThat(families.size()).isEqualTo(2);

            var latoFamily = families.get("Lato");
            assertThat(latoFamily).isNotNull();

            if (latoFamily == null) {
                return; // unreachable — satisfies NullAway after the assertThat above
            }

            assertThat(latoFamily.getStyles()).hasSize(2);

            var arialFamily = families.get("Arial");
            assertThat(arialFamily).isNotNull();

            if (arialFamily == null) {
                return; // unreachable — satisfies NullAway after the assertThat above
            }

            assertThat(arialFamily.getStyles()).hasSize(1);
        }

        @Test
        @SuppressWarnings("NullAway")
        void testGetReturnsCorrectFamilyForKnownName() {
            var families = new FontFamilies();
            var font = Mockito.mock(Font.class);
            when(font.getFamily()).thenReturn("Courier");
            when(font.getName()).thenReturn("Courier Regular");
            families.add(font);

            var result = families.get("Courier");
            assertThat(result).isNotNull();

            if (result == null) {
                return; // unreachable — satisfies NullAway after the assertThat above
            }

            assertThat(result.getName()).isEqualTo("Courier");
        }

        @Test
        void testGetReturnsNullForUnknownFamily() {
            var families = new FontFamilies();
            assertThat(families.get("NonExistentFamily")).isNull();
        }
    }

    // ---------------------------------------------------------------------------
    // FontFamiliesFactory
    // ---------------------------------------------------------------------------

    @Nested
    class FontFamiliesFactoryTests {

        @Test
        void testCreateExcludesDotPrefixedFamilies() {
            var dotFont = Mockito.mock(Font.class);
            var normalFont = Mockito.mock(Font.class);

            when(dotFont.getFamily()).thenReturn(".HiddenMacFont");
            when(dotFont.getName()).thenReturn(".HiddenMacFont Regular");
            when(normalFont.getFamily()).thenReturn("Arial");
            when(normalFont.getName()).thenReturn("Arial Regular");

            try (var fontUtils = mockStatic(MyFontUtils.class)) {
                fontUtils.when(MyFontUtils::getAllFonts).thenReturn(List.of(dotFont, normalFont));

                var result = FontFamiliesFactory.create();

                assertThat(result.get(".HiddenMacFont")).isNull();
                assertThat(result.get("Arial")).isNotNull();
            }
        }

        @Test
        @SuppressWarnings("NullAway")
        void testCreateGroupsRemainingFontsByFamily() {
            var font1 = Mockito.mock(Font.class);
            var font2 = Mockito.mock(Font.class);
            var font3 = Mockito.mock(Font.class);

            when(font1.getFamily()).thenReturn("Lato");
            when(font1.getName()).thenReturn("Lato Regular");
            when(font2.getFamily()).thenReturn("Lato");
            when(font2.getName()).thenReturn("Lato Bold");
            when(font3.getFamily()).thenReturn("Helvetica");
            when(font3.getName()).thenReturn("Helvetica Regular");

            try (var fontUtils = mockStatic(MyFontUtils.class)) {
                fontUtils.when(MyFontUtils::getAllFonts).thenReturn(List.of(font1, font2, font3));

                var result = FontFamiliesFactory.create();

                assertThat(result.size()).isEqualTo(2);

                var latoFamily = result.get("Lato");
                assertThat(latoFamily).isNotNull();

                if (latoFamily == null) {
                    return; // unreachable — satisfies NullAway after the assertThat above
                }

                assertThat(latoFamily.getStyles()).hasSize(2);

                var helveticaFamily = result.get("Helvetica");
                assertThat(helveticaFamily).isNotNull();

                if (helveticaFamily == null) {
                    return; // unreachable — satisfies NullAway after the assertThat above
                }

                assertThat(helveticaFamily.getStyles()).hasSize(1);
            }
        }
    }

}
