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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class PrefsTest extends UnitTest {

    private static final String STORED_TITLE_FONT = "MyFont-Bold";
    private static final int STORED_EXPORT_DPI = 300;
    private static final long STORED_WHATS_NEW_VERSION = 20240101L;
    private static final int DEFAULT_EXPORT_DPI = 600;
    private static final String DEFAULT_TITLE_FONT = "SourceSans3-SemiBold";
    private static final boolean STORED_LOOP_PLAYBACK = true;
    private static final String RECENT_FILE_A = "song_a.mssw";
    private static final String RECENT_FILE_B = "song_b.mssw";

    @AfterEach
    void tearDown() {
        Prefs.reset(PrefsKey.DIALOG_GEOMETRY);
        Prefs.reset(PrefsKey.TITLE_FONT);
        Prefs.reset(PrefsKey.EXPORT_DPI);
        Prefs.reset(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION);
        Prefs.reset(PrefsKey.LOOP_PLAYBACK);
        Prefs.reset(PrefsKey.RECENT_FILES);
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

    // --- getOrDefault ---

    @Test
    void testGetOrDefaultReturnsStoredValueWhenPresent() {
        Prefs.put(PrefsKey.TITLE_FONT, STORED_TITLE_FONT);
        assertThat(Prefs.getString(PrefsKey.TITLE_FONT)).isEqualTo(STORED_TITLE_FONT);
    }

    @Test
    void testGetOrDefaultFallsBackToDefaultWhenAbsent() {
        // No put — store has no override; must return the defaults.json value.
        assertThat(Prefs.getString(PrefsKey.TITLE_FONT)).isEqualTo(DEFAULT_TITLE_FONT);
    }

    // --- getDefault ---

    @Test
    void testGetDefaultThrowsForUnknownKey() {
        // PrefsKey.ALL has no entry in defaults.json; any scalar getter must propagate
        // the IllegalArgumentException thrown by getDefault.
        assertThatThrownBy(() -> Prefs.getString(PrefsKey.ALL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- getString ---

    @Test
    void testGetStringRoundTrip() {
        Prefs.put(PrefsKey.TITLE_FONT, STORED_TITLE_FONT);
        assertThat(Prefs.getString(PrefsKey.TITLE_FONT)).isEqualTo(STORED_TITLE_FONT);
    }

    // --- getInt ---

    @Test
    void testGetIntRoundTrip() {
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);
        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(STORED_EXPORT_DPI);
    }

    @Test
    void testGetIntReadsValueStoredAsLong() {
        // put(key, int) stores the value as Long internally (JSON round-trip consistency).
        // getInt must still return the correct int via Number.intValue().
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);
        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(STORED_EXPORT_DPI);
    }

    @Test
    void testGetIntFallsBackToDefault() {
        // No put — must return the defaults.json value cast through Number.intValue().
        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(DEFAULT_EXPORT_DPI);
    }

    // --- getLong ---

    @Test
    void testGetLongRoundTrip() {
        Prefs.put(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION, STORED_WHATS_NEW_VERSION);
        assertThat(Prefs.getLong(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION)).isEqualTo(STORED_WHATS_NEW_VERSION);
    }

    // --- getBoolean ---

    @Test
    void testGetBooleanRoundTrip() {
        Prefs.put(PrefsKey.LOOP_PLAYBACK, STORED_LOOP_PLAYBACK);
        assertThat(Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK)).isEqualTo(STORED_LOOP_PLAYBACK);
    }

    // --- getStringList ---

    @Test
    void testGetStringListRoundTrip() {
        var files = List.of(RECENT_FILE_A, RECENT_FILE_B);
        Prefs.putStringList(PrefsKey.RECENT_FILES, files);
        assertThat(Prefs.getStringList(PrefsKey.RECENT_FILES)).containsExactly(RECENT_FILE_A, RECENT_FILE_B);
    }

    @Test
    void testGetStringListReturnsEmptyListWhenAbsent() {
        // TITLE_FONT has a String default, not a list. Neither the store nor the
        // defaults hold a List value, so getStringList must return an empty list
        // rather than throwing — verifying the graceful-degradation contract that
        // distinguishes collection getters from scalar getters.
        assertThat(Prefs.getStringList(PrefsKey.TITLE_FONT)).isEmpty();
    }
}
