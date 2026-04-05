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

import module java.desktop;

/**
 * Base JLabel for custom combo box / list cell renderers. Handles
 * selection background painting that FlatLaf cannot apply when a
 * non-standard font is used for the cell content.
 */
class BaseLabel extends JLabel {

    private final JList<?> list;
    private final int index;
    private final boolean isSelected;

    BaseLabel(String text, JList<?> list, int index, boolean isSelected) {
        super(text);
        this.list = list;
        this.index = index;
        this.isSelected = isSelected;
        setOpaque(true);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Color color;

        // If index == -1, it's drawing the combo box, otherwise the popup
        if (index == -1) {
            color = list.getBackground();
        } else {
            color = isSelected
                ? list.getSelectionBackground()
                : list.getBackground();
        }

        g.setColor(color);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(
            isSelected
                ? list.getSelectionForeground()
                : list.getForeground()
        );
    }
}
