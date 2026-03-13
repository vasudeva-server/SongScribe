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
package songscribe.music;

import module java.desktop;

import songscribe.ui.component.Score;

public class Annotation {

    public static final int ABOVE = (int) (-4 * Score.STAFF_POSITION_OFFSET_PX);
    private int yPosPx = ABOVE;
    public static final int BELOW = (int) (8 * Score.STAFF_POSITION_OFFSET_PX);
    private String annotation;
    private float xAlignment = Component.LEFT_ALIGNMENT;

    /**
     * User's manual vertical offset from the layout-calculated position.
     * <p>
     * Final Y position = calculated position + userYOffset
     * <p>
     * Default is 0 (no user adjustment). Positive values move down, negative up.
     */
    private double userYOffset = 0;

    public Annotation(String annotation) {
        this.annotation = annotation;
    }

    public Annotation(String annotation, float alignment) {
        this.annotation = annotation;
        xAlignment = alignment;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public float getXAlignment() {
        return xAlignment;
    }

    public void setXAlignment(float alignment) {
        xAlignment = alignment;
    }

    public int getYPosPx() {
        return yPosPx;
    }

    public void setYPosPx(int yPosPx) {
        this.yPosPx = yPosPx;
    }

    public double getUserYOffset() {
        return userYOffset;
    }

    public void setUserYOffset(double userYOffset) {
        this.userYOffset = userYOffset;
    }
}
