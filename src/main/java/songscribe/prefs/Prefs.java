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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import com.formdev.flatlaf.util.SystemInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.binding.Property;
import songscribe.binding.Subscription;
import songscribe.binding.ViewProperty;
import songscribe.binding.ViewProperty.WriteNotification;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.util.LengthUnit;

/**
 * The user's preferences: the store, the file behind it, and the observable views of it.
 *
 * <p>Two ways to follow a change, and they are not alternatives. A
 * {@code PrefsDidChangeNotification} on the message bus carries the key that changed, for
 * consequences that are not a value. An observable view — {@link #intProperty},
 * {@link #booleanProperty}, {@link #choiceProperty} — is a {@code Property} a control can
 * be bound to two-way, and it notifies whenever its key changes, whoever changed it. That
 * inward direction is what makes a bound control follow a change it did not itself make.
 *
 * <p><b>The views are notified before the message is posted.</b> Code reacting to both
 * sees the bound controls already settled by the time its handler runs.
 *
 * <p>A view is not a resource. There is one per key, kept for the life of the store, and
 * nothing to unregister when a dialog closes; the observations a caller takes on it are
 * the caller's to cancel, which is what {@code Bindings.dispose} does.
 *
 * <p><b>Threading: writing is EDT-only.</b> Every {@code put}, {@code reset} and
 * {@code resetAll} notifies the views, and {@code songscribe.binding} is unsynchronized by
 * design, so a write from a background thread can lose a notification with nothing
 * reporting it. Reads are unrestricted.
 *
 * <p>See {@code .claude/guides/prefs.md} for the defaults files, the key enums, and how to
 * add or remove a preference.
 */
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

    // The observable view of a key, created on first request and kept for the life of
    // the store it views. A key with no view has never been bound; one with a view may
    // currently have no observers, which costs a map entry and nothing else.
    private final Map<PrefsKey, ViewProperty<Object>> properties = new EnumMap<>(PrefsKey.class);

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

    /**
     * Returns the observable, writable view of {@code key} as an int.
     *
     * @param key the preference to view
     * @return the view
     */
    public static Property<Integer> intProperty(PrefsKey key) {
        return new ConvertedView<>(storedView(key), stored -> ((Number) stored).intValue(), Integer::longValue);
    }

    /**
     * Returns the observable, writable view of {@code key} as a boolean.
     *
     * @param key the preference to view
     * @return the view
     */
    public static Property<Boolean> booleanProperty(PrefsKey key) {
        return new ConvertedView<>(storedView(key), stored -> (Boolean) stored, value -> value);
    }

    /**
     * Returns the observable, writable view of {@code key} as a constant of {@code type}.
     *
     * <p>The typed view a control binds to directly: a radio group answers its enum, this
     * answers the same enum, and the two are bound with nothing in between. Reading
     * converts through {@link PrefsValue#storedValue} case-insensitively, and writing
     * stores the chosen constant's stored value.
     *
     * @param <E> the enum the preference holds
     * @param key the preference to view
     * @param type the enum class, needed to enumerate the constants a stored value is
     *     matched against
     * @return the view, whose {@code get} follows {@link #getChoice}
     * @throws IllegalStateException from {@code get}, if the default for {@code key}
     *     names no constant either
     */
    public static <E extends Enum<E> & PrefsValue> Property<E> choiceProperty(PrefsKey key, Class<E> type) {
        return new ConvertedView<>(
            storedView(key),
            stored -> constantFor(key, type, stored.toString()),
            PrefsValue::storedValue
        );
    }

    /**
     * Reads {@code key} as a constant of {@code type}.
     *
     * <p>The typed read beside {@link #getString} and {@link #getInt}, and the one place
     * an enum preference is decoded.
     *
     * <p><b>A stored value naming no constant reads as the default</b> — the one
     * {@code defaults.json} gives for this key — rather than throwing or answering null.
     * A file written by an older version may hold a value this one no longer knows, so an
     * unreadable value is an expected input, and falling back to the default is what
     * every caller wanted anyway.
     *
     * @param <E> the enum the preference holds
     * @param key the preference to read
     * @param type the enum class whose constants the stored value is matched against
     * @return the constant the stored value names, or the one the default names
     * @throws IllegalStateException if the default names no constant either, which is a
     *     broken {@code defaults.json} rather than a bad stored value
     */
    public static <E extends Enum<E> & PrefsValue> E getChoice(PrefsKey key, Class<E> type) {
        return constantFor(key, type, getString(key));
    }

    /**
     * The constant {@code stored} names, or the one {@code key}'s default names.
     *
     * @param <E> the enum the preference holds
     * @param key the preference the value came from, whose default is the fallback
     * @param type the enum class whose constants are matched against
     * @param stored the stored string
     * @return the matching constant, or the one the default names
     * @throws IllegalStateException if the default names no constant either
     */
    private static <E extends Enum<E> & PrefsValue> E constantFor(PrefsKey key, Class<E> type, String stored) {
        var found = findConstant(type, stored);

        if (found != null) {
            return found;
        }

        var fallback = findConstant(type, INSTANCE.getDefault(key).toString());

        if (fallback == null) {
            throw new IllegalStateException(
                "The default for " + key.key() + " names no " + type.getSimpleName() + " constant");
        }

        return fallback;
    }

    /**
     * @param <E> the enum to search
     * @param type the enum class
     * @param stored the stored string to match against each constant's stored value
     * @return the matching constant, or null if no constant is stored as that
     */
    private static <E extends Enum<E> & PrefsValue> @Nullable E findConstant(Class<E> type, String stored) {
        for (var constant : type.getEnumConstants()) {
            if (constant.storedValue().equalsIgnoreCase(stored)) {
                return constant;
            }
        }

        return null;
    }

    /**
     * A view of one preference, typed as whatever encoding a caller asked for.
     *
     * <p>The typed views are layered over the one stored view rather than each being a
     * view in its own right, because the type a caller wants is not a property of the
     * preference: one key can legitimately be read as a string by one caller and as an
     * enum constant by another, and both have to stay live. Only the stored view is kept,
     * so there is exactly one thing to notify per key; a converted view registers nothing
     * of its own and is garbage as soon as the binding that took it is disposed.
     *
     * @param <T> the type this view presents
     * @param stored the view of what the store actually holds for this key
     * @param decode converts the stored value to this view's type
     * @param encode converts a written value back to what the store holds
     */
    private record ConvertedView<T>(
        ViewProperty<Object> stored,
        Function<Object, T> decode,
        Function<T, Object> encode
    ) implements Property<T> {

        @Override
        public T get() {
            return decode.apply(stored.get());
        }

        @Override
        public void set(T value) {
            stored.set(encode.apply(value));
        }

        @Override
        public Subscription observe(Runnable onNotify) {
            return stored.observe(onNotify);
        }
    }

    /**
     * Returns the view of what the store holds for {@code key}, creating it on the first
     * request.
     *
     * <p>One per key, because two views of one key would each hold their own observers and
     * only the one this class notified would report a change.
     *
     * <p>{@link WriteNotification#FROM_SOURCE} because a write through the view reaches
     * {@link #save}, which notifies the view on its way through — the same shape as a
     * Swing control whose own listener fires on a programmatic write.
     *
     * @param key the preference to view
     * @return the view
     */
    private static ViewProperty<Object> storedView(PrefsKey key) {
        return INSTANCE.properties.computeIfAbsent(
            key,
            _ -> new ViewProperty<>(() -> getOrDefault(key), value -> putValue(key, value), WriteNotification.FROM_SOURCE)
        );
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
        putValue(key, value);
    }

    public static void put(PrefsKey key, int value) {
        // Store as Long for consistency with JSON round-tripping
        putValue(key, (long) value);
    }

    public static void put(PrefsKey key, long value) {
        putValue(key, value);
    }

    public static void put(PrefsKey key, boolean value) {
        putValue(key, value);
    }

    /**
     * Stores {@code value} at {@code key} and saves.
     *
     * <p>What every {@code put} overload and every write through a view comes down to;
     * the overloads exist to fix the stored encoding for each Java type, not to store
     * differently.
     *
     * @param key the preference to write
     * @param value the value to store, already in its stored encoding
     * @effects replaces the stored value, writes the file, notifies {@code key}'s view
     *     and posts {@code PrefsDidChangeNotification}
     */
    private static void putValue(PrefsKey key, Object value) {
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
        notifyProperties(changedKey);
        MessageCenter.post(new PrefsDidChangeNotification(changedKey));
    }

    /**
     * Notifies the views of whatever {@code changedKey} covers, which for
     * {@link PrefsKey#ALL} is every view there is.
     *
     * <p>This is the whole of what makes a bound control follow a change it did not
     * make. The store is the writer, so the store is what notifies; a view holds no
     * subscription of its own and there is nothing to unregister when a dialog closes.
     */
    private void notifyProperties(PrefsKey changedKey) {
        if (changedKey == PrefsKey.ALL) {
            for (var property : List.copyOf(properties.values())) {
                property.notifyObservers();
            }

            return;
        }

        var property = properties.get(changedKey);

        if (property != null) {
            property.notifyObservers();
        }
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
