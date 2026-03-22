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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.formdev.flatlaf.util.SystemInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.notification.PrefsDidChangeNotification;

public final class Prefs {

    private static final Logger LOG = LoggerFactory.getLogger(Prefs.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULTS_RESOURCE = "/conf/defaults.json";
    private static final File OLD_PROPS_FILE =
            new File(System.getProperty("user.home"), ".songscribe/props");

    // Maps old flat-properties key names to PrefsKey values.
    // Used only during one-time migration from ~/.songscribe/props.
    private static final Map<String, PrefsKey> MIGRATION_MAP;

    static {
        var map = new HashMap<String, PrefsKey>();
        map.put("playinsertingnote", PrefsKey.PLAY_INSERTED_NOTE);
        map.put("playInsertingNote", PrefsKey.PLAY_INSERTED_NOTE);
        map.put("withrepeat", PrefsKey.PLAY_WITH_REPEATS);
        map.put("strip-short-a", PrefsKey.STRIP_SHORT_A);
        map.put("autosave-after-strip-short-a", PrefsKey.AUTO_SAVE_AFTER_STRIP_SHORT_A);
        map.put("tipindex", PrefsKey.TIP_INDEX);
        map.put("tempochange", PrefsKey.TEMPO_CHANGE_PERCENT);
        map.put("dpi", PrefsKey.EXPORT_DPI);
        map.put("showtip", PrefsKey.SHOW_TIPS);
        map.put("playcontinuously", PrefsKey.LOOP_PLAYBACK);
        map.put("control", PrefsKey.CONTROL);
        map.put("imageexportfilter", PrefsKey.IMAGE_EXPORT_FILTER);
        map.put("durationshortitude", PrefsKey.PLAYBACK_NOTE_DURATION);
        map.put("colorizenote", PrefsKey.COLORIZE_NOTE);
        map.put("instrument", PrefsKey.INSTRUMENT);
        map.put("metric", PrefsKey.METRIC);
        map.put("firstrun", PrefsKey.FIRST_RUN);
        MIGRATION_MAP = Collections.unmodifiableMap(map);
    }

    private static final List<String> OBSOLETE_KEYS = List.of("defaultProfile");

    // Initialized last to ensure all static fields above are ready before the constructor runs.
    private static final Prefs INSTANCE = new Prefs();

    private final Path prefsFile;
    private final Map<String, Object> defaults;
    private final Map<String, Object> store;

    private Prefs() {
        prefsFile = resolvePrefsFile();

        try {
            Files.createDirectories(prefsFile.getParent());
        } catch (IOException e) {
            LOG.warn("Failed to create preferences directory: {}", prefsFile.getParent(), e);
        }

        defaults = loadDefaults();
        store = loadStore();
        removeObsoleteKeys();
        migrate();
    }

    public static Prefs getInstance() {
        return INSTANCE;
    }

    public String getString(PrefsKey key) {
        var value = store.get(key.key());
        return value != null ? value.toString() : (String) getDefault(key);
    }

    public int getInt(PrefsKey key) {
        var value = store.get(key.key());
        return value != null ? ((Number) value).intValue() : ((Number) getDefault(key)).intValue();
    }

    public long getLong(PrefsKey key) {
        var value = store.get(key.key());
        return value != null ? ((Number) value).longValue() : ((Number) getDefault(key)).longValue();
    }

    public boolean getBoolean(PrefsKey key) {
        var value = store.get(key.key());
        return value != null ? (Boolean) value : (Boolean) getDefault(key);
    }

    public List<String> getStringList(PrefsKey key) {
        var value = store.get(key.key());

        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }

        return Collections.emptyList();
    }

    public void putStringList(PrefsKey key, List<String> value) {
        store.put(key.key(), new ArrayList<>(value));
        save(key);
    }

    public void put(PrefsKey key, String value) {
        store.put(key.key(), value);
        save(key);
    }

    public void put(PrefsKey key, int value) {
        // Store as Long for consistency with JSON round-tripping
        store.put(key.key(), (long) value);
        save(key);
    }

    public void put(PrefsKey key, long value) {
        store.put(key.key(), value);
        save(key);
    }

    public void put(PrefsKey key, boolean value) {
        store.put(key.key(), value);
        save(key);
    }


    public void reset(PrefsKey key) {
        store.remove(key.key());
        save(key);
    }

    public void resetAll() {
        store.clear();
        save(PrefsKey.ALL);
    }

    private void removeObsoleteKeys() {
        var removed = false;

        for (var key : OBSOLETE_KEYS) {
            if (store.remove(key) != null) {
                removed = true;
            }
        }

        if (removed) {
            saveQuietly();
        }
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

    private Map<String, Object> loadDefaults() {
        var result = new HashMap<String, Object>();
        var stream = Prefs.class.getResourceAsStream(DEFAULTS_RESOURCE);

        if (stream == null) {
            LOG.error("Missing defaults resource: {}", DEFAULTS_RESOURCE);
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

            LOG.info("Default preferences loaded");
        } catch (IOException e) {
            LOG.error("Failed to load defaults", e);
        }

        return result;
    }

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
                } else if (element.isJsonArray()) {
                    var list = new ArrayList<String>();

                    for (var item : element.getAsJsonArray()) {
                        list.add(item.getAsString());
                    }

                    result.put(entry.getKey(), list);
                }
            }

            LOG.info("Preferences loaded from: {}", prefsFile);
        } catch (IOException e) {
            LOG.warn("Failed to load preferences from {}", prefsFile, e);
        }

        return result;
    }

    private void save(PrefsKey changedKey) {
        writeToFile();
        MessageCenter.post(new PrefsDidChangeNotification(changedKey));
    }

    // Writes without posting a notification — used during construction
    // before any subscribers exist.
    private void saveQuietly() {
        writeToFile();
    }

    private void writeToFile() {
        try {
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
                } else if (value instanceof List<?> list) {
                    var array = new JsonArray();
                    list.forEach(item -> array.add(item.toString()));
                    json.add(key, array);
                } else {
                    json.addProperty(key, value.toString());
                }
            }

            Files.writeString(prefsFile, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to save preferences to {}", prefsFile, e);
        }
    }

    private Object getDefault(PrefsKey key) {
        var value = defaults.get(key.key());

        if (value == null) {
            throw new IllegalArgumentException("Unknown preference key: " + key.key());
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
            LOG.warn("Failed to load old props file for migration", e);
            return;
        }

        for (var entry : MIGRATION_MAP.entrySet()) {
            var oldKey = entry.getKey();
            var newKey = entry.getValue();
            var value = oldProps.getProperty(oldKey);

            if (value != null) {
                writeTyped(newKey.key(), value, defaults.get(newKey.key()));
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
            store.put(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION.key(), lastSeenVersion);
        }

        saveQuietly();

        if (!OLD_PROPS_FILE.delete()) {
            LOG.warn("Failed to delete old props file: {}", OLD_PROPS_FILE);
        }
    }

    private void writeTyped(String key, String value, @Nullable Object defaultValue) {
        if (defaultValue instanceof Boolean) {
            store.put(key, Boolean.parseBoolean(value));
        } else if (defaultValue instanceof Long) {
            try {
                store.put(key, Long.parseLong(value));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid numeric value for key {}: {}", key, value);
            }
        } else {
            store.put(key, value);
        }
    }
}
