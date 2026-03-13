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

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FontFamilies implements Iterable<FontFamily>, Serializable {

    private static final FontFamilies INSTANCE = createFontFamilies();

    @NotNull
    private static FontFamilies createFontFamilies() {
        return FontFamiliesFactory.create();
    }

    public static FontFamilies getInstance() {
        return INSTANCE;
    }

    private final Map<String, FontFamily> families = new TreeMap<>();

    public void add(@NotNull Font font) {
        var family = font.getFamily();
        var fontFamily = families.computeIfAbsent(family, FontFamily::new);
        fontFamily.add(font);
    }

    @NotNull
    @Override
    public Iterator<FontFamily> iterator() {
        return families.values().iterator();
    }

    @Nullable
    public FontFamily get(String name) {
        return families.get(name);
    }

    public int size() {
        return families.size();
    }
}
