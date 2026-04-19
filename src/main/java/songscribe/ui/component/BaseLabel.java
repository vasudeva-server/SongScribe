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
package songscribe.ui.component;

import module java.desktop;

/**
 * Base JLabel for custom combo box / list cell renderers. Handles
 * selection background painting that FlatLaf cannot apply when a
 * non-standard font is used for the cell content.
 */
public class BaseLabel extends JLabel {

    public BaseLabel(String text, JList<?> list, int index, boolean isSelected) {
        super(text);
        setOpaque(true);

        if (index == -1) {
            setBackground(list.getBackground());
        } else {
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        }

        setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(getForeground());
    }
}
