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

package songscribe.ui.fontchooser.model;

import module java.desktop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.ui.fontchooser.FontFamilies;

public class FamilyListModel extends AbstractListModel<String> {

    private final FontFamilies fontFamilies = FontFamilies.getInstance();

    private @Nullable List<String> fontFamilyNames = null;

    @Override
    public int getSize() {
        return getInitializedNames().size();
    }

    @Override
    public String getElementAt(int index) {
        return getInitializedNames().get(index);
    }

    @Nullable
    public String findFirst(CharSequence searchString) {
        for (var family : getInitializedNames()) {
            if (family.toLowerCase(Locale.ENGLISH).contains(searchString)) {
                return family;
            }
        }

        return null;
    }

    private List<String> getInitializedNames() {
        initialize();
        return Objects.requireNonNull(fontFamilyNames, "fontFamilyNames not initialized");
    }

    private void initialize() {
        if (fontFamilyNames == null) {
            fontFamilyNames = new ArrayList<>(fontFamilies.size());

            for (var fontFamily : fontFamilies) {
                fontFamilyNames.add(fontFamily.getName());
            }

            fontFamilyNames.sort(Comparator.naturalOrder());
        }
    }
}
