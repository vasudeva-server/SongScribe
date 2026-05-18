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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class PrefsTest extends UnitTest {

    @AfterEach
    void tearDown() {
        Prefs.reset(PrefsKey.DIALOG_GEOMETRY);
    }

    @Test
    void testAllKeysExistInDefaults() throws IOException {
        var stream = Prefs.class.getResourceAsStream("/conf/defaults.json");
        assertThat(stream).isNotNull();

        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var defaults = JsonParser.parseReader(reader).getAsJsonObject();

            for (var key : PrefsKey.values()) {
                if (key == PrefsKey.ALL) {
                    continue;
                }

                assertThat(defaults.has(key.key()))
                    .as("'%s' missing from defaults.json", key.key())
                    .isTrue();
            }
        }
    }

    @Test
    void testGetMapReturnsEmptyMapForMissingKey() {
        var map = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(map).isEmpty();
    }

    @Test
    void testPutMapAndGetMapRoundTrip() {
        var entries = Map.of("TestDialog", Map.of("x", 100, "y", 200));
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, entries);

        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).containsKey("TestDialog");
    }

    @Test
    void testPutMapMergesEntries() {
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of("Dialog1", Map.of("x", 10, "y", 20)));
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of("Dialog2", Map.of("x", 30, "y", 40)));

        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).containsKey("Dialog1");
        assertThat(result).containsKey("Dialog2");
    }

    @Test
    void testGetMapOnNonMapValueReturnsEmptyMap() {
        var map = Prefs.getMap(PrefsKey.TITLE_FONT);
        assertThat(map).isEmpty();
    }
}
