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
package songscribe.dom;

import java.awt.Component;

/**
 * A piece of text placed above or below a note — {@code dolce}, {@code cresc.}, {@code Fine}.
 *
 * <p><strong>Invariant: the text is never blank.</strong> An annotation with nothing to say has
 * nothing to draw and no reason to exist, so there is no state in which one is carrying empty
 * text — the constructors and {@link #setAnnotation} refuse it, and both readers drop such an
 * annotation rather than building one. Every caller may therefore treat
 * {@link #getAnnotation()} as text worth rendering, and removing an annotation is a removal
 * rather than a blanking.
 */
public class Annotation {

    public enum Placement { ABOVE, BELOW }

    private Placement placement = Placement.ABOVE;
    private String annotation;
    private float xAlignment = Component.LEFT_ALIGNMENT;

    /**
     * User's manual vertical offset from the layout-calculated position.
     * <p>
     * Final Y position = calculated position + userYOffsetSs
     * <p>
     * Default is 0 (no user adjustment). Positive values move down, negative up.
     */
    private double userYOffsetSs = 0;

    /**
     * @param annotation the text to display; must not be blank
     * @throws IllegalArgumentException if {@code annotation} is blank
     */
    public Annotation(String annotation) {
        this(annotation, Component.LEFT_ALIGNMENT);
    }

    /**
     * @param annotation the text to display; must not be blank
     * @param alignment  a {@code Component} horizontal alignment constant
     * @throws IllegalArgumentException if {@code annotation} is blank
     */
    public Annotation(String annotation, float alignment) {
        requireText(annotation);
        this.annotation = annotation;
        xAlignment = alignment;
    }

    /**
     * @return the text to display, never blank
     */
    public String getAnnotation() {
        return annotation;
    }

    /**
     * @param annotation the text to display; must not be blank
     * @throws IllegalArgumentException if {@code annotation} is blank
     */
    public void setAnnotation(String annotation) {
        requireText(annotation);
        this.annotation = annotation;
    }

    private static void requireText(String annotation) {
        if (annotation.isBlank()) {
            throw new IllegalArgumentException("annotation text must not be blank");
        }
    }

    public float getXAlignment() {
        return xAlignment;
    }

    public void setXAlignment(float alignment) {
        xAlignment = alignment;
    }

    public Placement getPlacement() {
        return placement;
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    public double getUserYOffsetSs() {
        return userYOffsetSs;
    }

    public void setUserYOffsetSs(double userYOffsetSs) {
        this.userYOffsetSs = userYOffsetSs;
    }

    /**
     * Returns a deep copy of this annotation, so mutating the copy (e.g. dragging it to a
     * new vertical position) never affects the original.
     */
    public Annotation copy() {
        var copy = new Annotation(annotation, xAlignment);
        copy.placement = placement;
        copy.userYOffsetSs = userYOffsetSs;
        return copy;
    }
}
