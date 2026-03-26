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

package songscribe.ui.dialog.fontchooser;

import songscribe.util.MyFontUtils;

public final class FontFamiliesFactory {

    private FontFamiliesFactory() {}

    public static FontFamilies create() {
        var fontFamilies = new FontFamilies();
        var allFonts = MyFontUtils.getAllFonts();

        for (var font : allFonts) {
            if (!font.getFamily().startsWith(".")) {
                fontFamilies.add(font);
            }
        }

        return fontFamilies;
    }
}
