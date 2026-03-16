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

public class MyBorder {

    private int top = 0;
    private int bottom = 0;
    private int left = 0;
    private int right = 0;

    public MyBorder() {}

    public MyBorder(int size) {
        top = size;
        bottom = size;
        left = size;
        right = size;
    }

    public MyBorder(int horizontal, int vertical) {
        top = vertical;
        bottom = vertical;
        left = horizontal;
        right = horizontal;
    }

    public MyBorder(int top, int bottom, int left, int right) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
    }

    /**
     * Creates a border with a default size, then applies per-edge overrides.
     * A value of -1 means "use the default size".
     */
    public static MyBorder withOverrides(
        int defaultSize,
        int top,
        int left,
        int bottom,
        int right
    ) {
        var border = new MyBorder(defaultSize);

        if (top > -1) {
            border.top = top;
        }

        if (left > -1) {
            border.left = left;
        }

        if (bottom > -1) {
            border.bottom = bottom;
        }

        if (right > -1) {
            border.right = right;
        }

        return border;
    }

    public int getTop() {
        return top;
    }

    public void setTop(int top) {
        this.top = top;
    }

    public int getBottom() {
        return bottom;
    }

    public void setBottom(int bottom) {
        this.bottom = bottom;
    }

    public int getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public int getWidth() {
        return left + right;
    }

    public int getHeight() {
        return top + bottom;
    }
}
