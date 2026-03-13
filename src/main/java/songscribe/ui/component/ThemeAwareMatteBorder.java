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

import org.jetbrains.annotations.NotNull;

/**
 * A matte border that lazily reads its color from a UIManager key on each paint,
 * so it automatically adapts when the LAF changes at runtime.
 */
public class ThemeAwareMatteBorder extends AbstractBorder {

    private static final Color DEFAULT_COLOR = Color.GRAY;

    private final int top;
    private final int left;
    private final int bottom;
    private final int right;
    private final String colorKey;

    public ThemeAwareMatteBorder(int top, int left, int bottom, int right, @NotNull String colorKey) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.colorKey = colorKey;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        var color = UIManager.getColor(colorKey);

        if (color == null) {
            color = DEFAULT_COLOR;
        }

        g.setColor(color);

        if (top > 0) {
            g.fillRect(x, y, width, top);
        }

        if (left > 0) {
            g.fillRect(x, y, left, height);
        }

        if (bottom > 0) {
            g.fillRect(x, y + height - bottom, width, bottom);
        }

        if (right > 0) {
            g.fillRect(x + width - right, y, right, height);
        }
    }

    @Override
    @NotNull
    public Insets getBorderInsets(Component c) {
        return new Insets(top, left, bottom, right);
    }

    @Override
    @NotNull
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(top, left, bottom, right);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return true;
    }
}
