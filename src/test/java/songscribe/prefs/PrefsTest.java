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

package songscribe.prefs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class PrefsTest extends UnitTest {

    @Test
    void testDefaultProfileKeyIsObsolete() {
        // defaultProfile was removed from defaults.json and added to OBSOLETE_KEYS.
        // Accessing it should throw because it exists in neither defaults nor store.
        assertThatThrownBy(() -> Prefs.getInstance().getString("defaultProfile"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("defaultProfile");
    }

    @Test
    void testFontDefaultsExist() {
        var prefs = Prefs.getInstance();

        assertThat(prefs.getString("titleFont")).isEqualTo("LatoPlus-Bold");
        assertThat(prefs.getInt("titleFontSize")).isEqualTo(30);
        assertThat(prefs.getString("lyricsFont")).isEqualTo("LatoPlus-Regular");
        assertThat(prefs.getInt("lyricsFontSize")).isEqualTo(17);
        assertThat(prefs.getString("attributionFont")).isEqualTo("LatoPlus-Regular");
        assertThat(prefs.getInt("attributionFontSize")).isEqualTo(15);
        assertThat(prefs.getString("annotationFont")).isEqualTo("LatoPlus-Regular");
        assertThat(prefs.getInt("annotationFontSize")).isEqualTo(15);
    }
}
