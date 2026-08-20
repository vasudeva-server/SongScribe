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
     * Creates an annotation attachment attached to a note.
     *
     * @param parent     The parent note
     * @param annotation The annotation data
     */
    public AnnotationAttachment(@Nullable StaffElement parent, Annotation annotation) {
        this.annotation = annotation;
        setOwnerElement(parent);
        setAlignment(Alignment.LEFT);
        // The line pointer is not set here. StaffElement.addAttachment — which every
        // caller reaches immediately — routes through LineElement.addChild, and that
        // owns it.
    }

    @Override
    public Attachment copy(StaffElement newOwner) {
        return new AnnotationAttachment(newOwner, annotation.copy());
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
        return ScaleContext.textWidthSs(font, annotation.getAnnotation()).value();
    }

    /**
     * Computes the content height from the annotation font.
     *
     * @param font the annotation font
     * @return height in staff-space units
     */
    public double computeContentHeightSs(Font font) {
        return ScaleContext.textHeightSs(font).value();
    }

    @Override
    public double getContentWidthPx() {
        throw new UnsupportedOperationException(
            "AnnotationAttachment width is font-dependent; use computeContentWidthSs(font) instead.");
    }

    @Override
    public double getContentHeightPx() {
        throw new UnsupportedOperationException(
            "AnnotationAttachment height is font-dependent; use computeContentHeightSs(font) instead.");
    }

    @Override
    public double getContentWidthSs() {
        throw new UnsupportedOperationException(
            "AnnotationAttachment width is font-dependent; use computeContentWidthSs(font) instead.");
    }

    @Override
    public double getContentHeightSs() {
        throw new UnsupportedOperationException(
            "AnnotationAttachment height is font-dependent; use computeContentHeightSs(font) instead.");
    }
}
