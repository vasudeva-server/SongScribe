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
        var strings = Prefs.getInstance().getStringList(PrefsKey.RECENT_FILES);
        paths = new ArrayList<>();

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

    public static RecentDocumentsManager getInstance() {
        return INSTANCE;
    }

    public List<Path> getRecents() {
        return List.copyOf(paths);
    }

    public void add(Path absolutePath) {
        var normalized = absolutePath.normalize();

        paths.remove(normalized);
        paths.add(0, normalized);

        while (paths.size() > MAX_SIZE) {
            paths.remove(paths.size() - 1);
        }

        persist();
        MessageCenter.post(new RecentDocumentsDidChangeNotification());
    }

    public void remove(Path absolutePath) {
        var normalized = absolutePath.normalize();

        paths.remove(normalized);
        persist();
        MessageCenter.post(new RecentDocumentsDidChangeNotification());
    }

    public void clear() {
        paths.clear();
        persist();
        MessageCenter.post(new RecentDocumentsDidChangeNotification());
    }

    private void persist() {
        var strings = paths.stream().map(Path::toString).toList();
        Prefs.getInstance().putStringList(PrefsKey.RECENT_FILES, strings);
    }
}
