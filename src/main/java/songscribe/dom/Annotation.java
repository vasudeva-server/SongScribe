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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.util.Copyable;

/**
 * A piece of text placed above or below a note — {@code dolce}, {@code cresc.}, {@code Fine}.
 *
 * <p>Annotation text is expected to be non-blank. That is guaranteed by the UI, not by this
 * class: the annotation combo offers only non-blank items, and its {@code Other…} prompt
 * refuses a blank entry. This class does not enforce the expectation — it logs a warning if
 * it is given blank text and stores it anyway. A blank annotation read from a file is dropped
 * by the reader rather than attached, since it has nothing to draw.
 */
public class Annotation implements Copyable<Annotation> {

    private static final Logger LOG = LoggerFactory.getLogger(Annotation.class);

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
     * @param annotation the text to display; expected to be non-blank
     * @log warn if {@code annotation} is blank; the blank text is stored anyway
     */
    public Annotation(String annotation) {
        this(annotation, Component.LEFT_ALIGNMENT);
    }

    /**
     * @param annotation the text to display; expected to be non-blank
     * @param alignment  a {@code Component} horizontal alignment constant
     * @log warn if {@code annotation} is blank; the blank text is stored anyway
     */
    public Annotation(String annotation, float alignment) {
        warnIfBlank(annotation);
        this.annotation = annotation;
        xAlignment = alignment;
    }

    /**
     * @return the text to display, whatever this annotation was given
     */
    public String getAnnotation() {
        return annotation;
    }

    private static void warnIfBlank(String annotation) {
        if (annotation.isBlank()) {
            LOG.warn("Annotation text must not be blank");
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
     *
     * @return an annotation with the same text, alignment, placement and offset, sharing no state
     *         with this one
     */
    @Override
    public Annotation copy() {
        var copy = new Annotation(annotation, xAlignment);
        copy.placement = placement;
        copy.userYOffsetSs = userYOffsetSs;
        return copy;
    }
}
