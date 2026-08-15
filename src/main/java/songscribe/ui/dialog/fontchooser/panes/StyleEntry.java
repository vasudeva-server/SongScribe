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

package songscribe.ui.dialog.fontchooser.panes;

import java.awt.Font;

import songscribe.util.MyFontUtils;

public final class StyleEntry {

    private final Font font;
    private final String name;

    public StyleEntry(Font font) {
        this.font = font;
        name = MyFontUtils.getStyleDescription(font);
    }

    public Font getFont() {
        return font;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        //noinspection ConstantValue -- overridden method may be called with null obj
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        // For comparison purposes, we only care about the PS font name
        var other = (StyleEntry) obj;
        return font.getPSName().equals(other.font.getPSName());
    }

    @Override
    public int hashCode() {
        // Must be consistent with equals, which compares by PS name.
        return font.getPSName().hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
