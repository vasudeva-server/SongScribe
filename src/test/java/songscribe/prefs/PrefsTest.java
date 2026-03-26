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

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class PrefsTest extends UnitTest {

    @AfterEach
    void tearDown() {
        Prefs.getInstance().reset(PrefsKey.DIALOG_GEOMETRY);
    }

    @Test
    void testFontDefaultsExist() {
        var prefs = Prefs.getInstance();

        assertThat(prefs.getString(PrefsKey.TITLE_FONT)).isEqualTo("LatoPlus-Bold");
        assertThat(prefs.getInt(PrefsKey.TITLE_FONT_SIZE)).isEqualTo(30);
        assertThat(prefs.getString(PrefsKey.LYRICS_FONT)).isEqualTo("LatoPlus-Regular");
        assertThat(prefs.getInt(PrefsKey.LYRICS_FONT_SIZE)).isEqualTo(17);
        assertThat(prefs.getString(PrefsKey.ATTRIBUTION_FONT)).isEqualTo("LatoPlus-Regular");
        assertThat(prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE)).isEqualTo(15);
        assertThat(prefs.getString(PrefsKey.ANNOTATION_FONT)).isEqualTo("LatoPlus-Regular");
        assertThat(prefs.getInt(PrefsKey.ANNOTATION_FONT_SIZE)).isEqualTo(15);
    }

    @Test
    void testGetMapReturnsEmptyMapForMissingKey() {
        var map = Prefs.getInstance().getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(map).isEmpty();
    }

    @Test
    void testPutMapAndGetMapRoundTrip() {
        var prefs = Prefs.getInstance();
        var entries = Map.of("TestDialog", Map.of("x", 100, "y", 200));
        prefs.putMap(PrefsKey.DIALOG_GEOMETRY, entries);

        var result = prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).containsKey("TestDialog");
    }

    @Test
    void testPutMapMergesEntries() {
        var prefs = Prefs.getInstance();
        prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of("Dialog1", Map.of("x", 10, "y", 20)));
        prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of("Dialog2", Map.of("x", 30, "y", 40)));

        var result = prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).containsKey("Dialog1");
        assertThat(result).containsKey("Dialog2");
    }

    @Test
    void testGetMapOnNonMapValueReturnsEmptyMap() {
        var map = Prefs.getInstance().getMap(PrefsKey.TITLE_FONT);
        assertThat(map).isEmpty();
    }
}
