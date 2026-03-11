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
package songscribe.ui;

import org.jetbrains.annotations.NotNull;

/**
 * Appearance preference for the application theme.
 */
public enum Appearance {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    private final String key;

    Appearance(@NotNull String key) {
        this.key = key;
    }

    @NotNull
    public String key() {
        return key;
    }

    @NotNull
    public static Appearance fromKey(@NotNull String key) {
        for (var value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }

        return SYSTEM;
    }
}
