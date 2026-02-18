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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.formdev.flatlaf.util.SystemInfo;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Prefs {

    private static final Logger LOGGER = Logger.getLogger(Prefs.class.getName());
    private static final String DEFAULTS_RESOURCE = "/conf/defaults.json";
    private static final File OLD_PROPS_FILE =
            new File(System.getProperty("user.home"), ".songscribe/props");

    // Maps old flat-properties key names to new camelCase key names.
    // Used only during one-time migration from ~/.songscribe/props.
    private static final Map<String, String> MIGRATION_MAP;

    static {
        var map = new HashMap<String, String>();
        map.put("playinsertingnote", "playInsertingNote");
        map.put("withrepeat", "playWithRepeats");
        map.put("defaultprofile", "defaultProfile");
        map.put("strip-short-a", "stripShortA");
        map.put("autosave-after-strip-short-a", "autoSaveAfterStripShortA");
        map.put("tipindex", "tipIndex");
        map.put("tempochange", "tempoChangePercent");
        map.put("dpi", "exportDpi");
        map.put("showtip", "showTips");
        map.put("playcontinuously", "loopPlayback");
        map.put("control", "control");
        map.put("imageexportfilter", "imageExportFilter");
        map.put("durationshortitude", "playbackNoteDuration");
        map.put("colorizenote", "colorizeNote");
        map.put("instrument", "instrument");
        map.put("metric", "metric");
        map.put("firstrun", "firstRun");
        MIGRATION_MAP = Collections.unmodifiableMap(map);
    }

    // Initialized last to ensure all static fields above are ready before the constructor runs.
    private static final Prefs INSTANCE = new Prefs();

    private final Path prefsFile;
    private final Map<String, Object> defaults;
    private final Map<String, Object> store;

    private Prefs() {
        prefsFile = resolvePrefsFile();
        defaults = loadDefaults();
        store = loadStore();
        migrate();
    }

    public static Prefs getInstance() {
        return INSTANCE;
    }

    @NotNull
    public String getString(@NotNull String key) {
        var value = store.get(key);
        return value != null ? value.toString() : (String) getDefault(key);
    }

    public int getInt(@NotNull String key) {
        var value = store.get(key);
        return value != null ? ((Number) value).intValue() : ((Number) getDefault(key)).intValue();
    }

    public long getLong(@NotNull String key) {
        var value = store.get(key);
        return value != null ? ((Number) value).longValue() : ((Number) getDefault(key)).longValue();
    }

    public boolean getBoolean(@NotNull String key) {
        var value = store.get(key);
        return value != null ? (Boolean) value : (Boolean) getDefault(key);
    }

    public void put(@NotNull String key, @NotNull String value) {
        store.put(key, value);
        save();
    }

    public void put(@NotNull String key, int value) {
        // Store as Long for consistency with JSON round-tripping
        store.put(key, (long) value);
        save();
    }

    public void put(@NotNull String key, long value) {
        store.put(key, value);
        save();
    }

    public void put(@NotNull String key, boolean value) {
        store.put(key, value);
        save();
    }

    public void reset(@NotNull String key) {
        store.remove(key);
        save();
    }

    public void resetAll() {
        store.clear();
        save();
    }

    private static Path resolvePrefsFile() {
        Path dir;

        if (SystemInfo.isMacOS) {
            dir = Paths.get(System.getProperty("user.home"), "Library", "Preferences", "SongScribe");
        } else if (SystemInfo.isWindows) {
            dir = Paths.get(System.getenv("APPDATA"), "SongScribe");
        } else {
            dir = Paths.get(System.getProperty("user.home"), ".songscribe");
        }

        return dir.resolve("prefs.json");
    }

    @NotNull
    private Map<String, Object> loadDefaults() {
        var result = new HashMap<String, Object>();
        var stream = Prefs.class.getResourceAsStream(DEFAULTS_RESOURCE);

        if (stream == null) {
            LOGGER.severe("Missing defaults resource: " + DEFAULTS_RESOURCE);
            return result;
        }

        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();

            for (var entry : json.entrySet()) {
                var element = entry.getValue();

                if (element.isJsonPrimitive()) {
                    var primitive = element.getAsJsonPrimitive();

                    if (primitive.isBoolean()) {
                        result.put(entry.getKey(), primitive.getAsBoolean());
                    } else if (primitive.isNumber()) {
                        // Store all integers as Long so both getInt and getLong work
                        result.put(entry.getKey(), primitive.getAsLong());
                    } else {
                        result.put(entry.getKey(), primitive.getAsString());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load defaults", e);
        }

        return result;
    }

    @NotNull
    private Map<String, Object> loadStore() {
        var result = new HashMap<String, Object>();

        if (!Files.exists(prefsFile)) {
            return result;
        }

        try (var reader = Files.newBufferedReader(prefsFile, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();

            for (var entry : json.entrySet()) {
                var element = entry.getValue();

                if (element.isJsonPrimitive()) {
                    var primitive = element.getAsJsonPrimitive();

                    if (primitive.isBoolean()) {
                        result.put(entry.getKey(), primitive.getAsBoolean());
                    } else if (primitive.isNumber()) {
                        result.put(entry.getKey(), primitive.getAsLong());
                    } else {
                        result.put(entry.getKey(), primitive.getAsString());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load preferences from " + prefsFile, e);
        }

        return result;
    }

    private void save() {
        try {
            Files.createDirectories(prefsFile.getParent());
            var json = new JsonObject();

            // Merge defaults with store overrides so the file always contains all keys
            var merged = new HashMap<>(defaults);
            merged.putAll(store);

            for (var entry : merged.entrySet()) {
                var key = entry.getKey();
                var value = entry.getValue();

                if (value instanceof Boolean b) {
                    json.addProperty(key, b);
                } else if (value instanceof Long l) {
                    json.addProperty(key, l);
                } else {
                    json.addProperty(key, value.toString());
                }
            }

            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(prefsFile, gson.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save preferences to " + prefsFile, e);
        }
    }

    @NotNull
    private Object getDefault(@NotNull String key) {
        var value = defaults.get(key);

        if (value == null) {
            throw new IllegalArgumentException("Unknown preference key: " + key);
        }

        return value;
    }

    private void migrate() {
        if (!OLD_PROPS_FILE.exists()) {
            return;
        }

        var oldProps = new Properties();

        try (var reader = Files.newBufferedReader(OLD_PROPS_FILE.toPath())) {
            oldProps.load(reader);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load old props file for migration", e);
            return;
        }

        for (var entry : MIGRATION_MAP.entrySet()) {
            var oldKey = entry.getKey();
            var newKey = entry.getValue();
            var value = oldProps.getProperty(oldKey);

            if (value != null) {
                writeTyped(newKey, value, defaults.get(newKey));
            }
        }

        // Scan for showwhatsnew* keys and record the highest version seen
        String lastSeenVersion = null;

        for (var oldKey : oldProps.stringPropertyNames()) {
            if (oldKey.startsWith("showwhatsnew")) {
                var version = oldKey.substring("showwhatsnew".length());

                if (!version.isEmpty()) {
                    if (lastSeenVersion == null || version.compareTo(lastSeenVersion) > 0) {
                        lastSeenVersion = version;
                    }
                }
            }
        }

        if (lastSeenVersion != null) {
            store.put("lastSeenWhatsNewVersion", lastSeenVersion);
        }

        save();

        if (!OLD_PROPS_FILE.delete()) {
            LOGGER.warning("Failed to delete old props file: " + OLD_PROPS_FILE);
        }
    }

    private void writeTyped(@NotNull String key, @NotNull String value, @Nullable Object defaultValue) {
        if (defaultValue instanceof Boolean) {
            store.put(key, Boolean.parseBoolean(value));
        } else if (defaultValue instanceof Long) {
            try {
                store.put(key, Long.parseLong(value));
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid numeric value for key " + key + ": " + value);
            }
        } else {
            store.put(key, value);
        }
    }
}
