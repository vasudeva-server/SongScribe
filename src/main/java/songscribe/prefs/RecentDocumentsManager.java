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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


import songscribe.message.MessageCenter;
import songscribe.message.notification.RecentDocumentsDidChangeNotification;

public final class RecentDocumentsManager {

    public static final int MAX_SIZE = 10;

    // Initialized last to ensure all static fields above are ready before the constructor runs.
    private static final RecentDocumentsManager INSTANCE = new RecentDocumentsManager();

    private final List<Path> paths;

    private RecentDocumentsManager() {
        paths = new ArrayList<>();
        loadFromPrefs();
    }

    /**
     * Loads the RECENT_FILES pref into {@code paths}, skipping malformed entries
     * and persisting if any non-existent paths were pruned.
     * <p>
     * Extracted from the constructor so that {@link #reloadForTest()} can
     * exercise the same logic without recreating the singleton.
     */
    private void loadFromPrefs() {
        paths.clear();
        var strings = Prefs.getStringList(PrefsKey.RECENT_FILES);

        for (var str : strings) {
            try {
                paths.add(Path.of(str));
            } catch (Exception e) {
                // Skip entries that cannot be converted to a Path
            }
        }

        // Remove paths that no longer exist on disk
        var sizeBefore = paths.size();
        paths.removeIf(path -> !Files.exists(path));

        if (paths.size() < sizeBefore) {
            persist();
        }
    }

    public static List<Path> getRecents() {
        return List.copyOf(INSTANCE.paths);
    }

    public static void add(Path absolutePath) {
        var normalized = absolutePath.normalize();

        INSTANCE.paths.remove(normalized);
        INSTANCE.paths.addFirst(normalized);

        while (INSTANCE.paths.size() > MAX_SIZE) {
            INSTANCE.paths.removeLast();
        }

        INSTANCE.persist();
        MessageCenter.post(new RecentDocumentsDidChangeNotification());
    }

    public static void remove(Path absolutePath) {
        var normalized = absolutePath.normalize();

        INSTANCE.paths.remove(normalized);
        INSTANCE.persist();
        MessageCenter.post(new RecentDocumentsDidChangeNotification());
    }

    public static void clear() {
        INSTANCE.paths.clear();
        INSTANCE.persist();
        MessageCenter.post(new RecentDocumentsDidChangeNotification());
    }

    /**
     * Clears the in-memory paths list and resets the RECENT_FILES pref entry.
     * Package-private for use in unit tests only — not part of the public API.
     */
    static void resetForTest() {
        INSTANCE.paths.clear();
        Prefs.reset(PrefsKey.RECENT_FILES);
    }

    /**
     * Reloads the in-memory paths list from the current RECENT_FILES pref,
     * running the same startup cleanup (prune non-existent paths) as the
     * constructor. Package-private for use in unit tests only.
     */
    static void reloadForTest() {
        INSTANCE.loadFromPrefs();
    }

    private void persist() {
        var strings = paths.stream().map(Path::toString).toList();
        Prefs.putStringList(PrefsKey.RECENT_FILES, strings);
    }
}
