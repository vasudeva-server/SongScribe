/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import java.awt.Font;

import org.jspecify.annotations.Nullable;

import songscribe.font.TextMeasurement;

/**
 * Represents a text annotation attachment on a note.
 * <p>
 * Annotations display text above or below a note (e.g., "dolce", "cresc.", etc.).
 * They can be aligned left, center, or right relative to the note.
 */
public final class AnnotationAttachment extends Attachment {

    /** The annotation data. */
    private Annotation annotation;

    /**
     * Creates an annotation attachment.
     *
     * @param annotation The annotation data
     */
    public AnnotationAttachment(Annotation annotation) {
        this.annotation = annotation;
    }

    @Override
    protected Attachment createCopy() {
        return new AnnotationAttachment(annotation);
    }

    /**
     * @return the annotation data
     */
    public Annotation getAnnotation() {
        return annotation;
    }

    /**
     * @param annotation the annotation data this attachment carries from now on
     */
    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    /**
     * Computes the content width from the actual annotation text.
     *
     * @param font the annotation font
     * @return width in staff-space units
     */
    public double computeContentWidthSs(Font font) {
        return TextMeasurement.textWidthSs(font, annotation.text()).value();
    }

    /**
     * Computes the content height from the annotation font.
     *
     * @param font the annotation font
     * @return height in staff-space units
     */
    public double computeContentHeightSs(Font font) {
        return TextMeasurement.textHeightSs(font).value();
    }
}
