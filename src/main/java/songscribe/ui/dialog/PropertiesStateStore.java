/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import java.util.prefs.Preferences;

import com.formdev.flatlaf.util.SystemFileChooser;

public class PropertiesStateStore implements SystemFileChooser.StateStore {

    private final Preferences prefs;

    public PropertiesStateStore() {
        prefs = Preferences.userNodeForPackage(PropertiesStateStore.class)
            .node("fileDialog");
    }

    @Override
    public String get(String key, String def) {
        return prefs.get(key, def);
    }

    @Override
    public void put(String key, String value) {
        if (value == null) {
            prefs.remove(key);
        } else {
            prefs.put(key, value);
        }
    }
}
