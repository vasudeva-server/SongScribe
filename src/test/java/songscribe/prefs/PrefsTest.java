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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.UnitTest;

class PrefsTest extends UnitTest {

    private static final String STORED_TITLE_FONT = "MyFont-Bold";
    private static final String STORED_APPEARANCE = "dark";
    private static final String DEFAULT_APPEARANCE = "system";
    private static final int STORED_EXPORT_DPI = 300;
    private static final long STORED_WHATS_NEW_VERSION = 20240101L;
    private static final int DEFAULT_EXPORT_DPI = 600;
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
        Prefs.reset(PrefsKey.APPEARANCE);
        Prefs.reset(PrefsKey.EXPORT_DPI);
        Prefs.reset(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION);
        Prefs.reset(PrefsKey.LOOP_PLAYBACK);
        Prefs.reset(PrefsKey.RECENT_FILES);
        // Defensive cleanup for raw keys seeded by tests that bypass the public API.
        // The happy paths remove them, but a test that fails early would otherwise
        // leak obsolete keys or system-default (font) keys into subsequent tests.
        Prefs.removeObsoleteKeysForTest();
        Prefs.removeSystemDefaultKeysFromStoreForTest();
    }

    @Test
    void testAllKeysExistInDefaults() throws IOException {
        // Each enum maps to exactly one defaults file: every PrefsKey (except ALL) must
        // appear in user-defaults.json, every SystemPrefsKey in system-defaults.json, and
        // the two files' key sets must be disjoint.
        var userDefaultsStream = Prefs.class.getResourceAsStream("/conf/user-defaults.json");
        var systemDefaultsStream = Prefs.class.getResourceAsStream("/conf/system-defaults.json");
        assertThat(userDefaultsStream).as("user-defaults.json resource must exist").isNotNull();
        assertThat(systemDefaultsStream).as("system-defaults.json resource must exist").isNotNull();

        Set<String> userKeys;
        Set<String> systemKeys;

        try (var reader = new InputStreamReader(userDefaultsStream, StandardCharsets.UTF_8)) {
            userKeys = JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }

        try (var reader = new InputStreamReader(systemDefaultsStream, StandardCharsets.UTF_8)) {
            systemKeys = JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }

        for (var key : PrefsKey.values()) {
            if (key == PrefsKey.ALL) {
                continue;
            }

            assertThat(userKeys)
                .as("'%s' missing from user-defaults.json", key.key())
                .contains(key.key());
        }

        for (var key : SystemPrefsKey.values()) {
            assertThat(systemKeys)
                .as("'%s' missing from system-defaults.json", key.key())
                .contains(key.key());
        }

        assertThat(userKeys)
            .as("Keys must not appear in both user-defaults.json and system-defaults.json")
            .doesNotContainAnyElementsOf(systemKeys);
    }

    @Test
    void testSaveWritesGlobalKeyAndOmitsFontKey() throws IOException {
        // The merged defaults always include the system-default (font) keys, so writeToFile()
        // must strip them before writing prefs.json — even when the store never held them.
        // Putting a global key triggers the write; a font key must be absent and the global
        // key present. Asserting both sides proves the filter is surgical: an over-eager
        // implementation that drops global keys too would still fail the positive assertion.
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);

        var prefsDirOverride = System.getProperty("songscribe.prefsDir");
        assertThat(prefsDirOverride)
            .as("songscribe.prefsDir system property must be set during tests")
            .isNotNull();

        var prefsPath = Path.of(prefsDirOverride, "prefs.json");
        var contents = Files.readString(prefsPath, StandardCharsets.UTF_8);
        var written = JsonParser.parseString(contents).getAsJsonObject();

        assertThat(written.keySet())
            .as("font key '%s' must be absent from written prefs.json", SystemPrefsKey.TITLE_FONT.key())
            .doesNotContain(SystemPrefsKey.TITLE_FONT.key());
        assertThat(written.keySet())
            .as("global key '%s' must be present in written prefs.json", PrefsKey.EXPORT_DPI.key())
            .contains(PrefsKey.EXPORT_DPI.key());
        assertThat(written.get(PrefsKey.EXPORT_DPI.key()).getAsLong())
            .as("global key '%s' must have the stored value", PrefsKey.EXPORT_DPI.key())
            .isEqualTo(STORED_EXPORT_DPI);
    }

    @Test
    void testRemoveSystemDefaultKeysFromStoreRemovesFontKeys() {
        // Seed a system-default (font) key directly into the raw store (the public API cannot
        // write it) and one live global key. removeSystemDefaultKeysFromStoreForTest() must
        // strip the font key without disturbing the global key — it is surgical, not a
        // wholesale clear, so the global key must retain its exact stored value.
        Prefs.putRawStored(SystemPrefsKey.TITLE_FONT.key(), STORED_TITLE_FONT);
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);

        Prefs.removeSystemDefaultKeysFromStoreForTest();

        assertThat(Prefs.getRawStored(SystemPrefsKey.TITLE_FONT.key()))
            .as("font key must be absent from store after removeSystemDefaultKeysFromStore")
            .isNull();
        assertThat(Prefs.getRawStored(PrefsKey.EXPORT_DPI))
            .as("global key must survive removeSystemDefaultKeysFromStore with its value intact")
            .isEqualTo((long) STORED_EXPORT_DPI);
    }

    // --- getMap ---

    @Test
    void testGetMapStoreValueTakesPrecedenceOverDefault() {
        // Path (a): store value present → returns that map with correct content.
        // DIALOG_GEOMETRY has default {} in user-defaults.json; the stored value must win.
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
        // Path (b): key absent from store but has a default in user-defaults.json.
        // DIALOG_GEOMETRY defaults to {} (empty map). Assert the default is returned
        // exactly — an empty map — confirming the fallback path is exercised.
        var result = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        assertThat(result).isEmpty();
    }

    @Test
    void testGetMapReturnsEmptyMapWhenAbsentAndNoMapDefault() {
        // Path (c): key absent from store AND has no map-typed default.
        // APPEARANCE has a String default, so getMap must degrade to an empty map.
        var result = Prefs.getMap(PrefsKey.APPEARANCE);
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
    private void assertMapCoordinates(@Nullable Map<String, Object> map, int expectedX, int expectedY) {
        assertThat(map).isNotNull();

        var xRaw = map.get("x");
        var yRaw = map.get("y");
        assertThat(xRaw).isNotNull();
        assertThat(yRaw).isNotNull();

        assertThat(((Number) xRaw).intValue()).isEqualTo(expectedX);
        assertThat(((Number) yRaw).intValue()).isEqualTo(expectedY);
    }

    // --- getOrDefault ---

    @Test
    void testGetOrDefaultReturnsStoredValueWhenPresent() {
        Prefs.put(PrefsKey.APPEARANCE, STORED_APPEARANCE);
        assertThat(Prefs.getString(PrefsKey.APPEARANCE)).isEqualTo(STORED_APPEARANCE);
    }

    @Test
    void testGetOrDefaultFallsBackToDefaultWhenAbsent() {
        // No put — store has no override; must return the user-defaults.json value.
        assertThat(Prefs.getString(PrefsKey.APPEARANCE)).isEqualTo(DEFAULT_APPEARANCE);
    }

    // --- getDefault ---

    @Test
    void testGetDefaultThrowsForUnknownKey() {
        // PrefsKey.ALL has no entry in either defaults file; any scalar getter must propagate
        // the IllegalArgumentException thrown by getDefault.
        assertThatThrownBy(() -> Prefs.getString(PrefsKey.ALL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- getString ---

    @Test
    void testGetStringRoundTrip() {
        Prefs.put(PrefsKey.APPEARANCE, STORED_APPEARANCE);
        assertThat(Prefs.getString(PrefsKey.APPEARANCE)).isEqualTo(STORED_APPEARANCE);
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
        // No put — must return the user-defaults.json value cast through Number.intValue().
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
        // APPEARANCE has a String default, not a list. Neither the store nor the
        // defaults hold a List value, so getStringList must return an empty list
        // rather than throwing — verifying the graceful-degradation contract that
        // distinguishes collection getters from scalar getters.
        assertThat(Prefs.getStringList(PrefsKey.APPEARANCE)).isEmpty();
    }

    @Test
    void testGetStringListWithListDefaultReturnsEmptyListWhenStoreAbsent() {
        // RECENT_FILES has a list-typed default (empty array []) in user-defaults.json.
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
        Prefs.put(PrefsKey.APPEARANCE, STORED_APPEARANCE);
        assertThat(Prefs.getString(PrefsKey.APPEARANCE)).isEqualTo(STORED_APPEARANCE);
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
        // returns the user-defaults.json value — confirming the override is removed, not
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
        // that both keys revert to their defaults-file values. resetAll clears the
        // entire store, so no individual reset is needed afterward — @AfterEach
        // resets are harmless no-ops when the store is already empty.
        Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);
        Prefs.put(PrefsKey.APPEARANCE, STORED_APPEARANCE);

        Prefs.resetAll();

        assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(DEFAULT_EXPORT_DPI);
        assertThat(Prefs.getString(PrefsKey.APPEARANCE)).isEqualTo(DEFAULT_APPEARANCE);
    }

    // --- removeObsoleteKeys ---

    @Test
    void testRemoveObsoleteKeysStripsObsoleteKeysFromStore() {
        // Seed the two known obsolete keys directly into the raw store
        // (they have no PrefsKey enum constant, so no public API can write them).
        // Also seed a live key to confirm it survives the call untouched.
        Prefs.putRawStored(OBSOLETE_KEY_COLORIZE_NOTE, OBSOLETE_KEY_VALUE);
        Prefs.putRawStored(OBSOLETE_KEY_DEFAULT_PROFILE, OBSOLETE_KEY_VALUE);
        Prefs.put(PrefsKey.APPEARANCE, STORED_APPEARANCE);

        Prefs.removeObsoleteKeysForTest();

        // Both obsolete keys must be absent from the store after removal.
        assertThat(Prefs.getRawStored(OBSOLETE_KEY_COLORIZE_NOTE)).isNull();
        assertThat(Prefs.getRawStored(OBSOLETE_KEY_DEFAULT_PROFILE)).isNull();

        // The non-obsolete live key must be intact — removeObsoleteKeys is surgical.
        assertThat(Prefs.getRawStored(PrefsKey.APPEARANCE)).isEqualTo(STORED_APPEARANCE);
    }

    // --- writeTyped ---

    @Nested
    class WriteTyped {

        @AfterEach
        void tearDown() {
            Prefs.reset(PrefsKey.FIRST_RUN);
            Prefs.reset(PrefsKey.EXPORT_DPI);
            Prefs.reset(PrefsKey.APPEARANCE);
        }

        @Test
        void testBooleanDefaultParsesBooleanString() {
            // When the default value is Boolean, the string "true" must be stored
            // as Boolean.TRUE — not as a String — so that getBoolean() can cast without error.
            Prefs.writeTypedForTest(PrefsKey.FIRST_RUN.key(), "true", Boolean.FALSE);
            assertThat(Prefs.getRawStored(PrefsKey.FIRST_RUN)).isInstanceOf(Boolean.class).isEqualTo(Boolean.TRUE);
        }

        @Test
        void testLongDefaultParsesNumericString() {
            // When the default value is Long, the string must be parsed to Long
            // so that getInt()/getLong() work via Number coercion.
            Prefs.writeTypedForTest(PrefsKey.EXPORT_DPI.key(), "150", 600L);
            assertThat(Prefs.getRawStored(PrefsKey.EXPORT_DPI)).isInstanceOf(Long.class).isEqualTo(150L);
        }

        @Test
        void testLongDefaultInvalidNumericStringIsIgnored() {
            // An invalid numeric string must be silently skipped (warning logged, no store entry
            // written) — migration must not corrupt prefs with unparseable values.
            // Seed a known value first so we can confirm it is unchanged.
            Prefs.put(PrefsKey.EXPORT_DPI, STORED_EXPORT_DPI);
            Prefs.writeTypedForTest(PrefsKey.EXPORT_DPI.key(), "not-a-number", 600L);
            // The store value must remain the previously put value, not be overwritten with garbage.
            assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(STORED_EXPORT_DPI);
        }

        @Test
        void testOtherDefaultStoresStringAsIs() {
            // When the default is a String (or any non-Boolean/non-Long type),
            // the value is stored verbatim as a String.
            Prefs.writeTypedForTest(PrefsKey.APPEARANCE.key(), "MyFont-Regular", "default");
            assertThat(Prefs.getRawStored(PrefsKey.APPEARANCE)).isInstanceOf(String.class).isEqualTo("MyFont-Regular");
        }
    }

    // --- migrate ---

    @Nested
    class Migrate {

        private static final String OLD_KEY_DPI = "dpi";
        private static final String OLD_KEY_FIRSTRUN = "firstrun";
        private static final String OLD_KEY_LOOP = "playcontinuously";
        private static final String OLD_DPI_VALUE = "300";
        private static final String WHATS_NEW_VERSION_1 = "1.0";
        private static final String WHATS_NEW_VERSION_2 = "2.0";
        private static final String WHATS_NEW_PREFIX = "showwhatsnew";

        @AfterEach
        void tearDown() {
            Prefs.reset(PrefsKey.EXPORT_DPI);
            Prefs.reset(PrefsKey.FIRST_RUN);
            Prefs.reset(PrefsKey.LOOP_PLAYBACK);
            Prefs.reset(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION);
        }

        @Test
        void testMigrateIsNoOpWhenOldFileAbsent(@TempDir File tempDir) {
            // Passing a non-existent file must leave the store completely unchanged.
            var storeSnapshot = Prefs.getInt(PrefsKey.EXPORT_DPI);
            var noSuchFile = new File(tempDir, "no-such-props");

            Prefs.migrateForTest(noSuchFile);

            assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(storeSnapshot);
        }

        @Test
        void testMigrateMapsKnownKeysIntoStore(@TempDir File tempDir) throws IOException {
            // Old properties file with a "dpi" entry → must be stored under
            // PrefsKey.EXPORT_DPI as Long (because the default is numeric).
            var oldFile = writePropsFile(tempDir, OLD_KEY_DPI + '=' + OLD_DPI_VALUE);

            Prefs.migrateForTest(oldFile);

            assertThat(Prefs.getInt(PrefsKey.EXPORT_DPI)).isEqualTo(Integer.parseInt(OLD_DPI_VALUE));
        }

        @Test
        void testMigrateBooleanKeyStoredAsBoolean(@TempDir File tempDir) throws IOException {
            // "firstrun=true" must be stored as Boolean (not String) because
            // the default for FIRST_RUN is a boolean.
            var oldFile = writePropsFile(tempDir, OLD_KEY_FIRSTRUN + "=true");

            Prefs.migrateForTest(oldFile);

            assertThat(Prefs.getRawStored(PrefsKey.FIRST_RUN)).isInstanceOf(Boolean.class);
            assertThat(Prefs.getBoolean(PrefsKey.FIRST_RUN)).isTrue();
        }

        @Test
        void testMigrateRecordsHighestShowWhatsnewVersion(@TempDir File tempDir) throws IOException {
            // Multiple showwhatsnew* keys: migration must record the lexicographically
            // highest version suffix in LAST_SEEN_WHATS_NEW_VERSION.
            var oldFile = writePropsFile(
                tempDir,
                WHATS_NEW_PREFIX + WHATS_NEW_VERSION_1 + "=true",
                WHATS_NEW_PREFIX + WHATS_NEW_VERSION_2 + "=true"
            );

            Prefs.migrateForTest(oldFile);

            assertThat(Prefs.getString(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION))
                .isEqualTo(WHATS_NEW_VERSION_2);
        }

        @Test
        void testMigrateDeletesOldFileAfterSuccess(@TempDir File tempDir) throws IOException {
            // The old props file must be deleted after a successful migration
            // so that the migration does not run again on the next launch.
            var oldFile = writePropsFile(tempDir, OLD_KEY_LOOP + "=true");

            Prefs.migrateForTest(oldFile);

            assertThat(oldFile).doesNotExist();
        }

        private File writePropsFile(File dir, String... lines) throws IOException {
            var file = new File(dir, "props");

            try (var writer = new PrintWriter(Files.newBufferedWriter(file.toPath()))) {
                for (var line : lines) {
                    writer.println(line);
                }
            }

            return file;
        }
    }

}
