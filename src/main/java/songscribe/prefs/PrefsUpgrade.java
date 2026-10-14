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
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.util.LengthUnit;

/**
 * Brings a freshly loaded preferences store up to date with the current release.
 *
 * <p>It operates only on the store handed to it — no singleton, no file path, no message bus —
 * so {@link Prefs} can apply it during construction.
 *
 * <p>It never writes the preferences file: {@link #apply} reports whether the store changed
 * and the owner persists. It does delete the old flat properties file once read.
 */
final class PrefsUpgrade {

    private static final Logger LOG = LoggerFactory.getLogger(PrefsUpgrade.class);

    // The boolean that PrefsKey.UNITS replaced: true meant centimetres.
    private static final String OBSOLETE_METRIC_KEY = "metric";

    private static final List<String> OBSOLETE_KEYS = List.of(
        "autoSaveAfterStripShortA", "colorizeNote", "defaultProfile", "messagesPanelHeight", OBSOLETE_METRIC_KEY,
        "stripShortA"
    );

    // Maps old flat-properties key names to PrefsKey values, for the one-time
    // migration from ~/.songscribe/props.
    private static final Map<String, PrefsKey> MIGRATION_MAP = Map.ofEntries(
        Map.entry("playinsertingnote", PrefsKey.PLAY_INSERTED_NOTE),
        Map.entry("playInsertingNote", PrefsKey.PLAY_INSERTED_NOTE),
        Map.entry("withrepeat", PrefsKey.PLAY_WITH_REPEATS),
        Map.entry("tipindex", PrefsKey.TIP_INDEX),
        Map.entry("tempochange", PrefsKey.TEMPO_CHANGE_PERCENT),
        Map.entry("dpi", PrefsKey.EXPORT_DPI),
        Map.entry("showtip", PrefsKey.SHOW_TIPS),
        Map.entry("playcontinuously", PrefsKey.LOOP_PLAYBACK),
        Map.entry("imageexportfilter", PrefsKey.IMAGE_EXPORT_FILTER),
        Map.entry("durationshortitude", PrefsKey.PLAYBACK_NOTE_DURATION),
        Map.entry("instrument", PrefsKey.INSTRUMENT),
        Map.entry("firstrun", PrefsKey.FIRST_RUN)
    );

    private static final String WHATS_NEW_PREFIX = "showwhatsnew";

    private final Map<String, Object> store;
    private final Map<String, Object> defaults;
    private final Set<String> systemDefaultKeys;

    /** @param store the loaded store, which this upgrade mutates in place */
    PrefsUpgrade(Map<String, Object> store, Map<String, Object> defaults, Set<String> systemDefaultKeys) {
        this.store = store;
        this.defaults = defaults;
        this.systemDefaultKeys = systemDefaultKeys;
    }

    /** {@code metric} must be carried over before obsolete keys are dropped; it is one of them. */
    boolean apply(File oldPropsFile) {
        // Before removeKeys drops it: metric is itself an obsolete key.
        var carried = carryMetricOverToUnits();
        var obsoleteDropped = removeKeys(OBSOLETE_KEYS);
        var systemDropped = removeKeys(systemDefaultKeys);
        var imported = importOldProps(oldPropsFile);

        return carried || obsoleteDropped || systemDropped || imported;
    }

    /** A stored {@code units} always wins, so this cannot overwrite a choice made with the new key. */
    private boolean carryMetricOverToUnits() {
        if (store.containsKey(PrefsKey.UNITS.key()) || !Boolean.TRUE.equals(store.get(OBSOLETE_METRIC_KEY))) {
            return false;
        }

        store.put(PrefsKey.UNITS.key(), LengthUnit.CENTIMETERS.name());

        return true;
    }

    private boolean removeKeys(Iterable<String> keys) {
        var removed = false;

        for (var key : keys) {
            if (store.remove(key) != null) {
                removed = true;
            }
        }

        return removed;
    }

    private boolean importOldProps(File oldPropsFile) {
        if (!oldPropsFile.exists()) {
            return false;
        }

        var oldProps = new Properties();

        try (var reader = Files.newBufferedReader(oldPropsFile.toPath())) {
            oldProps.load(reader);
        } catch (IOException e) {
            LOG.warn("Failed to load old props file for migration", e);

            return false;
        }

        for (var entry : MIGRATION_MAP.entrySet()) {
            var newKey = entry.getValue();
            var value = oldProps.getProperty(entry.getKey());

            if (value != null) {
                writeTyped(newKey.key(), value, defaults.get(newKey.key()));
            }
        }

        var lastSeenVersion = highestWhatsNewVersion(oldProps);

        if (lastSeenVersion != null) {
            store.put(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION.key(), lastSeenVersion);
        }

        if (!oldPropsFile.delete()) {
            LOG.warn("Failed to delete old props file: {}", oldPropsFile);
        }

        return true;
    }

    /** @return the highest version seen, or {@code null} when no such key is present */
    private static @Nullable String highestWhatsNewVersion(Properties oldProps) {
        String highest = null;

        for (var oldKey : oldProps.stringPropertyNames()) {
            if (!oldKey.startsWith(WHATS_NEW_PREFIX)) {
                continue;
            }

            var version = oldKey.substring(WHATS_NEW_PREFIX.length());

            if (!version.isEmpty() && (highest == null || version.compareTo(highest) > 0)) {
                highest = version;
            }
        }

        return highest;
    }

    /**
     * @param defaultValue {@code null} when the key has none, in which case the text is stored
     *     as text
     */
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
