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

package songscribe.ui.fontchooser.listeners;

import module java.desktop;


import songscribe.ui.fontchooser.FontContainer;

/**
 * Created by dheid on 4/1/17.
 * Modified by Aparajita Fishman
 */
public class StyleListSelectionListener implements ListSelectionListener {

    private final FontContainer fontContainer;

    public StyleListSelectionListener(FontContainer fontContainer) {
        this.fontContainer = fontContainer;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            var selectedStyle = fontContainer.getSelectedStyle();
            var newFont = selectedStyle
                .getFont()
                .deriveFont(fontContainer.getSelectedSize());
            fontContainer.setSelectedFont(newFont);
            fontContainer.setPreviewFont(newFont);
        }
    }
}
