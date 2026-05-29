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
import org.jspecify.annotations.Nullable;
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
    private static final String RECENT_FILE_C = "song_c.mssw";
    private static final String OBSOLETE_KEY_COLORIZE_NOTE = "colorizeNote";
    private static final String OBSOLETE_KEY_DEFAULT_PROFILE = "defaultProfile";
    private static final String OBSOLETE_KEY_VALUE = "someValue";
    private static final String DIALOG1_NAME = "Dialog1";
    private static final String DIALOG2_NAME = "Dialog2";
    private static final String TEST_DIALOG_NAME = "TestDialog";
    private static final int DIALOG1_X = 10;
    private static final int DIALOG1_Y = 20;
    private static final int DIALOG2_X = 30;
    private static final int DIALOG2_Y = 40;
    private static final int TEST_DIALOG_X = 100;
    private static final int TEST_DIALOG_Y = 200;

    @AfterEach
    void tearDown() {
        Prefs.reset(PrefsKey.DIALOG_GEOMETRY);
        Prefs.reset(PrefsKey.TITLE_FONT);
        Prefs.reset(PrefsKey.EXPORT_DPI);
        Prefs.reset(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION);
        Prefs.reset(PrefsKey.LOOP_PLAYBACK);
        Prefs.reset(PrefsKey.RECENT_FILES);
        // Defensive cleanup for raw keys seeded by testRemoveObsoleteKeysStripsObsoleteKeysFromStore.
        // removeObsoleteKeysForTest() removes them on the happy path, but if the test fails
        // early the raw keys would leak into subsequent tests.
        Prefs.removeObsoleteKeysForTest();
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

    // --- getMap ---

    @Test
    void testGetMapStoreValueTakesPrecedenceOverDefault() {
        // Path (a): store value present → returns that map with correct content.
        // DIALOG_GEOMETRY has default {} in defaults.json; the stored value must win.
        var entries = Map.<String, Object>of(TEST_DIALOG_NAME, Map.of("x", TEST_DIALOG_X, "y", TEST_DIALOG_Y));
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, entries);

        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).containsKey(TEST_DIALOG_NAME);

        @SuppressWarnings("unchecked")
        var nested = (Map<String, Object>) result.get(TEST_DIALOG_NAME);
        assertMapCoordinates(nested, TEST_DIALOG_X, TEST_DIALOG_Y);
    }

    @Test
    void testGetMapReturnsDefaultMapWhenStoreAbsent() {
        // Path (b): key absent from store but has a default in defaults.json.
        // DIALOG_GEOMETRY defaults to {} (empty map). Assert the default is returned
        // exactly — an empty map — confirming the fallback path is exercised.
        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).isEmpty();
    }

    @Test
    void testGetMapReturnsEmptyMapWhenAbsentAndNoMapDefault() {
        // Path (c): key absent from store AND has no map-typed default.
        // TITLE_FONT has a String default, so getMap must degrade to an empty map.
        var result = Prefs.getMap(PrefsKey.TITLE_FONT);
        assertThat(result).isEmpty();
    }

    @Test
    void testPutMapAndGetMapRoundTrip() {
        var entries = Map.<String, Object>of(TEST_DIALOG_NAME, Map.of("x", TEST_DIALOG_X, "y", TEST_DIALOG_Y));
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, entries);

        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).containsKey(TEST_DIALOG_NAME);

        @SuppressWarnings("unchecked")
        var nested = (Map<String, Object>) result.get(TEST_DIALOG_NAME);
        assertMapCoordinates(nested, TEST_DIALOG_X, TEST_DIALOG_Y);
    }

    @Test
    void testPutMapMergesEntries() {
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of(DIALOG1_NAME, Map.of("x", DIALOG1_X, "y", DIALOG1_Y)));
        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of(DIALOG2_NAME, Map.of("x", DIALOG2_X, "y", DIALOG2_Y)));

        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);

        // Both entries must survive the second putMap — it merges, not replaces.
        assertThat(result).containsKey(DIALOG1_NAME);
        assertThat(result).containsKey(DIALOG2_NAME);

        @SuppressWarnings("unchecked")
        var dialog1 = (Map<String, Object>) result.get(DIALOG1_NAME);

        @SuppressWarnings("unchecked")
        var dialog2 = (Map<String, Object>) result.get(DIALOG2_NAME);

        assertMapCoordinates(dialog1, DIALOG1_X, DIALOG1_Y);
        assertMapCoordinates(dialog2, DIALOG2_X, DIALOG2_Y);
    }

    /**
     * Asserts that {@code map} contains numeric "x" and "y" values equal to the given coordinates.
     * Reads values via {@link Number} because Gson deserializes JSON integers as {@code Double}
     * after a round-trip through the prefs file.
     */
    @SuppressWarnings("NullAway")
    private void assertMapCoordinates(@Nullable Map<String, Object> map, int expectedX, int expectedY) {
        assertThat(map).isNotNull();

        if (map == null) {
            return; // unreachable — satisfies NullAway after the assertThat above
        }

        var xRaw = map.get("x");
        var yRaw = map.get("y");
        assertThat(xRaw).isNotNull();
        assertThat(yRaw).isNotNull();

        if (xRaw == null || yRaw == null) {
            return; // unreachable — satisfies NullAway
        }

        assertThat(((Number) xRaw).intValue()).isEqualTo(expectedX);
        assertThat(((Number) yRaw).intValue()).isEqualTo(expectedY);
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

    @Test
    void testGetStringListWithListDefaultReturnsEmptyListWhenStoreAbsent() {
        // RECENT_FILES has a list-typed default (empty array []) in defaults.json.
        // getStringList falls back to the defaults layer when the store has no value,
        // so with no store entry the empty-list default is returned — not a stored list.
        // This documents that getStringList consults defaults (unlike the row's claim
        // that it ignores them), but since the recentFiles default is [] the observable
        // result is still an empty list, giving no false confidence that items came from
        // the store.
        assertThat(Prefs.getStringList(PrefsKey.RECENT_FILES)).isEmpty();
    }

    // --- put(PrefsKey, String) ---

    @Test
    void testPutStringStoresAndRetrieves() {
        // Verifies that put(key, String) stores a value retrievable by getString.
        // (save() also posts PrefsDidChangeNotification synchronously, but verifying
        // the bus side-effect would require subscribing a listener — the stored-value
        // assertion is sufficient to confirm the write path executed.)
        Prefs.put(PrefsKey.TITLE_FONT, STORED_TITLE_FONT);
        assertThat(Prefs.getString(PrefsKey.TITLE_FONT)).isEqualTo(STORED_TITLE_FONT);
    }

    // --- put(PrefsKey, int) ---

    @Test
    void testPutIntStoresAsLong() {
        // put(key, int) must coerce the value to Long for JSON round-trip consistency.
        // Assert both the Long type in the raw store and that getInt reads it back
        // correctly via Number.intValue().
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);

        var rawValue = Prefs.getRawStored(PrefsKey.EXPORT_DPI);
        assertThat(rawValue).isInstanceOf(Long.class);
        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(STORED_EXPORT_DPI);
    }

    // --- putStringList: wholesale replace ---

    @Test
    void testPutStringListReplacesListWholesale() {
        // Unlike putMap (which merges), putStringList replaces the stored list entirely.
        // Put an initial list, then overwrite it with a different list, and assert that
        // getStringList returns exactly the second list — none of the first list's
        // elements survive the second put.
        var firstList = List.of(RECENT_FILE_A, RECENT_FILE_B);
        Prefs.putStringList(PrefsKey.RECENT_FILES, firstList);

        var secondList = List.of(RECENT_FILE_C);
        Prefs.putStringList(PrefsKey.RECENT_FILES, secondList);

        assertThat(Prefs.getStringList(PrefsKey.RECENT_FILES))
            .containsExactly(RECENT_FILE_C);
    }

    // --- reset ---

    @Test
    void testResetRemovesOverrideAndRestoresDefault() {
        // Store a non-default value, then reset the key and verify that the getter
        // returns the defaults.json value — confirming the override is removed, not
        // merely zeroed.
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);
        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(STORED_EXPORT_DPI);

        Prefs.reset(PrefsKey.EXPORT_DPI);
        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(DEFAULT_EXPORT_DPI);
    }

    // --- resetAll ---

    @Test
    void testResetAllClearsAllOverrides() {
        // Seed non-default values on two distinct keys, call resetAll, and verify
        // that both keys revert to their defaults.json values. resetAll clears the
        // entire store, so no individual reset is needed afterward — @AfterEach
        // resets are harmless no-ops when the store is already empty.
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);
        Prefs.put(PrefsKey.TITLE_FONT, STORED_TITLE_FONT);

        Prefs.resetAll();

        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(DEFAULT_EXPORT_DPI);
        assertThat(Prefs.getString(PrefsKey.TITLE_FONT)).isEqualTo(DEFAULT_TITLE_FONT);
    }

    // --- removeObsoleteKeys ---

    @Test
    void testRemoveObsoleteKeysStripsObsoleteKeysFromStore() {
        // Seed the two known obsolete keys directly into the raw store
        // (they have no PrefsKey enum constant, so no public API can write them).
        // Also seed a live key to confirm it survives the call untouched.
        Prefs.putRawStored(OBSOLETE_KEY_COLORIZE_NOTE, OBSOLETE_KEY_VALUE);
        Prefs.putRawStored(OBSOLETE_KEY_DEFAULT_PROFILE, OBSOLETE_KEY_VALUE);
        Prefs.put(PrefsKey.TITLE_FONT, STORED_TITLE_FONT);

        Prefs.removeObsoleteKeysForTest();

        // Both obsolete keys must be absent from the store after removal.
        assertThat(Prefs.getRawStored(OBSOLETE_KEY_COLORIZE_NOTE)).isNull();
        assertThat(Prefs.getRawStored(OBSOLETE_KEY_DEFAULT_PROFILE)).isNull();

        // The non-obsolete live key must be intact — removeObsoleteKeys is surgical.
        assertThat(Prefs.getRawStored(PrefsKey.TITLE_FONT)).isEqualTo(STORED_TITLE_FONT);
    }

}
