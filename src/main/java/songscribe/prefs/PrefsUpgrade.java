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
 * <p>Everything a stored preferences file needs on the way in, in one place: a setting
 * whose spelling changed, keys that no longer mean anything, keys the application now
 * decides for itself, and values still sitting in the flat properties file SongScribe
 * used before JSON.
 *
 * <p>It operates on a store handed to it and reads nothing else — no singleton, no file
 * path of its own, no message bus. That is what lets {@link Prefs} apply it to its own
 * store during construction while anything else applies it to a store it built, and it
 * is why {@code Prefs} needs no openings in its own surface for this logic to be
 * reachable.
 *
 * <p><b>It never writes the preferences file.</b> {@link #apply} reports whether the
 * store changed and leaves persisting to the owner, which knows where the file is and
 * what belongs in it. It does delete the old flat properties file once it has read it,
 * that file being nothing the owner has an interest in.
 */
final class PrefsUpgrade {

    private static final Logger LOG = LoggerFactory.getLogger(PrefsUpgrade.class);

    // The boolean that PrefsKey.UNITS replaced: true meant centimetres.
    private static final String OBSOLETE_METRIC_KEY = "metric";

    private static final List<String> OBSOLETE_KEYS = List.of(
        "autoSaveAfterStripShortA", "colorizeNote", "defaultProfile", OBSOLETE_METRIC_KEY, "stripShortA"
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

    /**
     * @param store the loaded store, which this upgrade mutates in place
     * @param defaults the merged defaults, read to decide what type a migrated flat
     *     properties value should be stored as
     * @param systemDefaultKeys the keys the application decides for itself, which a
     *     user's file has no business carrying
     */
    PrefsUpgrade(Map<String, Object> store, Map<String, Object> defaults, Set<String> systemDefaultKeys) {
        this.store = store;
        this.defaults = defaults;
        this.systemDefaultKeys = systemDefaultKeys;
    }

    /**
     * Applies every upgrade step to the store, in the order they depend on.
     *
     * <p>The order is part of the promise, not an implementation detail: the
     * {@code metric} setting is carried over <i>before</i> obsolete keys are dropped,
     * because {@code metric} is one of the keys being dropped and reading it afterwards
     * would find nothing. Everything else is independent.
     *
     * @param oldPropsFile the flat properties file SongScribe used before JSON; ignored
     *     when it does not exist, which is the case for every launch after the first
     * @return {@code true} when the store now differs from what was loaded, so the owner
     *     knows whether the file needs rewriting
     * @effects mutates the store, and deletes {@code oldPropsFile} once its contents
     *     have been read across. Never writes the preferences file itself.
     * @log warn when the old properties file cannot be read, or cannot be deleted after
     *     being read, or holds a numeric value that does not parse — in each case the
     *     upgrade carries on with what it has.
     */
    boolean apply(File oldPropsFile) {
        // Before removeKeys drops it: metric is itself an obsolete key.
        var carried = carryMetricOverToUnits();
        var obsoleteDropped = removeKeys(OBSOLETE_KEYS);
        var systemDropped = removeKeys(systemDefaultKeys);
        var imported = importOldProps(oldPropsFile);

        return carried || obsoleteDropped || systemDropped || imported;
    }

    /**
     * Carries a stored {@code metric} choice over to {@link PrefsKey#UNITS}.
     *
     * <p>The two keys differ in spelling and in type — a boolean became an enum name —
     * so {@link #MIGRATION_MAP}, which maps names to names, cannot carry this one.
     * Without it a user who had chosen centimetres would find inches after upgrading,
     * with the old value already erased.
     *
     * <p>A stored {@code units} always wins, so this cannot overwrite a choice made with
     * the new key; and {@code metric} absent or false leaves nothing stored, which is the
     * {@code units} default.
     *
     * @return whether the store changed
     */
    private boolean carryMetricOverToUnits() {
        if (store.containsKey(PrefsKey.UNITS.key()) || !Boolean.TRUE.equals(store.get(OBSOLETE_METRIC_KEY))) {
            return false;
        }

        store.put(PrefsKey.UNITS.key(), LengthUnit.CENTIMETERS.name());

        return true;
    }

    /**
     * Drops {@code keys} from the store.
     *
     * @param keys the keys to drop; ones the store does not hold are skipped
     * @return whether any key was actually removed
     */
    private boolean removeKeys(Iterable<String> keys) {
        var removed = false;

        for (var key : keys) {
            if (store.remove(key) != null) {
                removed = true;
            }
        }

        return removed;
    }

    /**
     * Reads the pre-JSON flat properties file into the store, then deletes it.
     *
     * <p>Each recognized old key is written under its {@link PrefsKey} name, typed to
     * match that key's default. The {@code showwhatsnew*} keys are handled separately:
     * there was one per version seen, and what replaced them is a single key holding the
     * highest, so they are scanned rather than mapped.
     *
     * @param oldPropsFile the file to read
     * @return whether anything was read across
     */
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

    /**
     * Returns the highest version named by a {@code showwhatsnew<version>} key.
     *
     * @param oldProps the old flat properties
     * @return the highest version seen, or {@code null} when no such key is present
     */
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
     * Stores {@code value} under {@code key}, typed to match {@code defaultValue}.
     *
     * <p>The old file held everything as text; the store holds booleans and numbers as
     * themselves, so the default is what says which this key is.
     *
     * @param key the store key to write
     * @param value the text from the old file
     * @param defaultValue the key's default, or {@code null} when it has none, in which
     *     case the text is stored as text
     * @log warn when {@code value} does not parse as a number for a numeric key, which
     *     leaves the key unwritten and so falling back to its default
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
