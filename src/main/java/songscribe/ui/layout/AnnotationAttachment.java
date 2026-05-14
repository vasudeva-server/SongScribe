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

package songscribe.ui.layout;

import java.awt.Font;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.music.Annotation;
import songscribe.music.StaffElement;

/**
 * Represents a text annotation attachment on a note.
 * <p>
 * Annotations display text above or below a note (e.g., "dolce", "cresc.", etc.).
 * They can be aligned left, center, or right relative to the note.
 */
public class AnnotationAttachment extends Attachment {

    /** The annotation data. */
    private Annotation annotation;

    /**
     * Creates an annotation attachment with the specified text.
     *
     * @param text The annotation text
     */
    public AnnotationAttachment(String text) {
        annotation = new Annotation(text);
        setAlignment(Alignment.LEFT);
    }

    /**
     * Creates an annotation attachment with the specified annotation data.
     *
     * @param annotation The annotation data
     */
    public AnnotationAttachment(Annotation annotation) {
        this.annotation = annotation;
        setAlignment(Alignment.LEFT);
    }

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

        if (parent != null) {
            setParentLine(parent.getParentLine());
        }
    }

    @Override
    public Attachment copy(StaffElement newOwner) {
        return new AnnotationAttachment(newOwner, annotation);
    }

    /**
     * Returns the annotation data.
     */
    public Annotation getAnnotation() {
        return annotation;
    }

    /**
     * Sets the annotation data.
     */
    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    /**
     * Returns the annotation text.
     */
    public String getText() {
        return annotation.getAnnotation();
    }

    /**
     * Sets the annotation text.
     */
    public void setText(String text) {
        annotation.setAnnotation(text);
    }

    /**
     * Computes the content width from the actual annotation text.
     *
     * @param font the annotation font
     * @return width in staff-space units
     */
    public double computeContentWidthSs(Font font) {
        return ScaleContext.getInstance().textWidthSs(font, annotation.getAnnotation());
    }

    /**
     * Returns the content height in staff-space units, derived from the song's
     * annotation font via {@code parentLine}.
     * <p>
     * {@code parentLine} must be non-null; callers downstream of {@link songscribe.music.Line#addElement}
     * can rely on this. Use {@link ScaleContext#textHeightSs(Font)} directly when
     * the font is already in hand.
     */
    @Override
    public double getContentHeightSs() {
        var parentLine = getParentLine();

        if (parentLine == null) {
            throw RuntimeError.exit(
                "AnnotationAttachment.getContentHeightSs called with null parentLine");
        }

        return ScaleContext.getInstance().textHeightSs(
            parentLine.getSong().getAnnotationFont());
    }

    @Override
    public double getContentWidthPx() {
        // Legacy pixel API — not used for layout (computeContentWidthSs is used instead)
        return 0;
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().ssToPx(getContentHeightSs());
    }
}
