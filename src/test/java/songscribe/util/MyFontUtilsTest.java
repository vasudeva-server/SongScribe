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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class MyFontUtilsTest extends UnitTest {

    @Test
    void testCreateFontWithKnownPsNameReturnsCorrectSize() {
        var font = MyFontUtils.createFont("LatoPlus-Bold", 24);
        assertThat(font).isNotNull();
        assertThat(font.getSize()).isEqualTo(24);
    }

    @Test
    void testCreateFontWithUnknownPsNameReturnsFallback() {
        var font = MyFontUtils.createFont("NonExistent-BogusFont-12345", 16);
        assertThat(font).isNotNull();
        assertThat(font.getSize()).isEqualTo(16);
        // The PS name should NOT be the bogus name — it should be a fallback
        assertThat(font.getPSName()).isNotEqualTo("NonExistent-BogusFont-12345");
    }
}
