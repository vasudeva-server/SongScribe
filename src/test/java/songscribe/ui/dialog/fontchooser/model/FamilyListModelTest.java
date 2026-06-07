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

package songscribe.ui.dialog.fontchooser.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.awt.Font;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import songscribe.UnitTest;
import songscribe.ui.dialog.fontchooser.FontFamilies;

class FamilyListModelTest extends UnitTest {

    /** Build a FontFamilies with one font per family name, in the given order. */
    private FontFamilies buildFamilies(String... familyNames) {
        var families = new FontFamilies();

        for (var name : familyNames) {
            var font = Mockito.mock(Font.class);
            when(font.getFamily()).thenReturn(name);
            // FontFamily stores fonts in a TreeSet<Font> sorted by FontNameComparator,
            // which calls Font.getName(). The stub is required to avoid NPE during
            // comparator invocations when more than one font shares a family.
            when(font.getName()).thenReturn(name);
            families.add(font);
        }

        return families;
    }

    @Test
    void testInitializeReturnsFamiliesSortedAscending() {
        // Supply families in reverse alphabetical order; model must sort them.
        var families = buildFamilies("Zebra", "Apple", "Mango");
        var model = new FamilyListModel(families);

        assertThat(model.getSize()).isEqualTo(3);
        assertThat(model.getElementAt(0)).isEqualTo("Apple");
        assertThat(model.getElementAt(1)).isEqualTo("Mango");
        assertThat(model.getElementAt(2)).isEqualTo("Zebra");
    }

    // ---------------------------------------------------------------------------
    // findFirst — contract: caller passes a pre-lowercased search string;
    //             findFirst lowercases stored family names before comparing.
    // ---------------------------------------------------------------------------

    @Test
    void testFindFirstExactMatchReturnsFamily() {
        // Exact lowercase match against the lowercased family name.
        var model = new FamilyListModel(buildFamilies("Arial", "Times New Roman", "Helvetica"));
        assertThat(model.findFirst("arial")).isEqualTo("Arial");
    }

    @Test
    void testFindFirstPrefixMatchReturnsFamily() {
        // Prefix of the lowercased family name matches.
        var model = new FamilyListModel(buildFamilies("Courier New", "Verdana"));
        assertThat(model.findFirst("courier")).isEqualTo("Courier New");
    }

    @Test
    void testFindFirstSubstringMatchReturnsFamily() {
        // Substring of the lowercased family name matches.
        var model = new FamilyListModel(buildFamilies("Times New Roman", "Arial"));
        assertThat(model.findFirst("new")).isEqualTo("Times New Roman");
    }

    @Test
    void testFindFirstNoMatchReturnsNull() {
        var model = new FamilyListModel(buildFamilies("Arial", "Helvetica"));
        assertThat(model.findFirst("wingdings")).isNull();
    }
}
