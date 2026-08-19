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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.formdev.flatlaf.util.SystemInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.message.MessageCenter;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.util.LengthUnit;

public final class Prefs {

    private static final Logger LOG = LoggerFactory.getLogger(Prefs.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String USER_DEFAULTS_RESOURCE = "/conf/user-defaults.json";
    private static final String SYSTEM_DEFAULTS_RESOURCE = "/conf/system-defaults.json";
    private static final File OLD_PROPS_FILE =
            new File(System.getProperty("user.home"), ".songscribe/props");

    // Initialized last to ensure all static fields above are ready before the constructor runs.
    private static final Prefs INSTANCE = new Prefs();

    private final Path prefsFile;
    private final Map<String, Object> defaults;
    private final Map<String, Object> store;
    private final Set<String> systemDefaultKeys;

    private Prefs() {
        prefsFile = resolvePrefsFile();

        try {
            Files.createDirectories(prefsFile.getParent());
        } catch (IOException e) {
            LOG.warn("Failed to create preferences directory: {}", prefsFile.getParent(), e);
        }

        var userDefaults = loadDefaultsResource(USER_DEFAULTS_RESOURCE);
        var systemDefaults = loadDefaultsResource(SYSTEM_DEFAULTS_RESOURCE);

        if (!Collections.disjoint(userDefaults.keySet(), systemDefaults.keySet())) {
            var overlap = new HashSet<>(userDefaults.keySet());
            overlap.retainAll(systemDefaults.keySet());
            LOG.error("Keys appear in both user-defaults and system-defaults (system wins): {}", overlap);
        }

        defaults = new HashMap<>();
        defaults.putAll(userDefaults);
        defaults.putAll(systemDefaults);
        systemDefaultKeys = Set.copyOf(systemDefaults.keySet());
        store = loadStore();

        // Everything a stored file needs on the way in, and the only place the order of
        // those steps matters. Saved once here rather than by each step, so a startup
        // that changes several things writes the file once.
        if (new PrefsUpgrade(store, defaults, systemDefaultKeys).apply(OLD_PROPS_FILE)) {
            saveQuietly();
        }
    }

    private static Object getOrDefault(PrefsKey key) {
        var value = INSTANCE.store.get(key.key());
        return value != null ? value : INSTANCE.getDefault(key);
    }

    public static String getString(PrefsKey key) {
        return getOrDefault(key).toString();
    }

    public static int getInt(PrefsKey key) {
        return ((Number) getOrDefault(key)).intValue();
    }

    public static long getLong(PrefsKey key) {
        return ((Number) getOrDefault(key)).longValue();
    }

    public static boolean getBoolean(PrefsKey key) {
        return (Boolean) getOrDefault(key);
    }

    public static String getDefaultString(SystemPrefsKey key) {
        return INSTANCE.getDefault(key).toString();
    }

    public static int getDefaultInt(SystemPrefsKey key) {
        return ((Number) INSTANCE.getDefault(key)).intValue();
    }

    public static List<String> getStringList(PrefsKey key) {
        var value = INSTANCE.store.get(key.key());

        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }

        var defaultValue = INSTANCE.defaults.get(key.key());

        if (defaultValue instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }

        return Collections.emptyList();
    }

    public static void putStringList(PrefsKey key, List<String> value) {
        INSTANCE.store.put(key.key(), new ArrayList<>(value));
        INSTANCE.save(key);
    }

    /**
     * Returns the map stored at the given key, or an empty map if absent.
     * <p>
     * <b>Note:</b> Gson deserializes JSON numbers in nested objects as {@code Double},
     * so callers must cast numeric values through {@link Number} (e.g.,
     * {@code ((Number) value).intValue()}) rather than assuming {@code Integer} or {@code Long}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(PrefsKey key) {
        var value = INSTANCE.store.get(key.key());

        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        var defaultValue = INSTANCE.defaults.get(key.key());

        if (defaultValue instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        return Collections.emptyMap();
    }

    public static void putMap(PrefsKey key, Map<String, ?> entries) {
        var current = new HashMap<>(getMap(key));
        current.putAll(entries);
        INSTANCE.store.put(key.key(), current);
        INSTANCE.save(key);
    }

    public static void put(PrefsKey key, String value) {
        INSTANCE.store.put(key.key(), value);
        INSTANCE.save(key);
    }

    public static void put(PrefsKey key, int value) {
        // Store as Long for consistency with JSON round-tripping
        INSTANCE.store.put(key.key(), (long) value);
        INSTANCE.save(key);
    }

    public static void put(PrefsKey key, long value) {
        INSTANCE.store.put(key.key(), value);
        INSTANCE.save(key);
    }

    public static void put(PrefsKey key, boolean value) {
        INSTANCE.store.put(key.key(), value);
        INSTANCE.save(key);
    }


    public static void reset(PrefsKey key) {
        INSTANCE.store.remove(key.key());
        INSTANCE.save(key);
    }

    public static void resetAll() {
        INSTANCE.store.clear();
        INSTANCE.save(PrefsKey.ALL);
    }

    private static Path resolvePrefsFile() {
        // A directory override (set by the test harness) keeps unit/e2e runs from
        // reading or writing the real per-user preferences file. Unset in
        // production, so the OS-specific resolution below is used normally.
        var override = System.getProperty("songscribe.prefsDir");

        if (override != null && !override.isBlank()) {
            return Paths.get(override).resolve("prefs.json");
        }

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

    private Map<String, Object> loadDefaultsResource(String resource) {
        var stream = Prefs.class.getResourceAsStream(resource);

        if (stream == null) {
            LOG.error("Missing defaults resource: {}", resource);
            return new HashMap<>();
        }

        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var result = parseJsonObject(JsonParser.parseReader(reader).getAsJsonObject());
            LOG.info("Default preferences loaded from: {}", resource);
            return result;
        } catch (IOException e) {
            LOG.error("Failed to load defaults from: {}", resource, e);
            return new HashMap<>();
        }
    }

    private Map<String, Object> loadStore() {
        if (!Files.exists(prefsFile)) {
            return new HashMap<>();
        }

        try (var reader = Files.newBufferedReader(prefsFile, StandardCharsets.UTF_8)) {
            var result = parseJsonObject(JsonParser.parseReader(reader).getAsJsonObject());
            LOG.info("Preferences loaded from: {}", prefsFile);
            return result;
        } catch (IOException e) {
            LOG.warn("Failed to load preferences from {}", prefsFile, e);
            return new HashMap<>();
        }
    }

    private Map<String, Object> parseJsonObject(JsonObject json) {
        var result = new HashMap<String, Object>();

        for (var entry : json.entrySet()) {
            var value = JsonValues.toJavaValue(entry.getValue());

            if (value != null) {
                result.put(entry.getKey(), value);
            }
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
            var merged = new HashMap<>(defaults);
            merged.putAll(store);
            merged.keySet().removeAll(systemDefaultKeys);
            Files.writeString(prefsFile, GSON.toJson(merged), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to save preferences to {}", prefsFile, e);
        }
    }

    private Object getDefault(PrefsKey key) {
        return lookupDefault(key.key());
    }

    private Object getDefault(SystemPrefsKey key) {
        return lookupDefault(key.key());
    }

    private Object lookupDefault(String jsonKey) {
        var value = defaults.get(jsonKey);

        if (value == null) {
            throw new IllegalArgumentException("Unknown preference key: " + jsonKey);
        }

        return value;
    }

}
