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

package songscribe.ui.fontchooser;

import module java.desktop;

import java.util.Collection;
import java.util.TreeSet;

public class FontFamily {

    private final String name;

    private final Collection<Font> styles = new TreeSet<>(
        new FontNameComparator()
    );

    public FontFamily(String name) {
        this.name = name;
    }

    public boolean add(Font font) {
        return styles.add(font);
    }

    public String getName() {
        return name;
    }

    public Collection<Font> getStyles() {
        return styles;
    }
}
