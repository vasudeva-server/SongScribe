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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Annotation;
import songscribe.music.Note;

/**
 * Represents a text annotation attachment on a note.
 * <p>
 * Annotations display text above or below a note (e.g., "dolce", "cresc.", etc.).
 * They can be aligned left, center, or right relative to the note.
 */
public class AnnotationAttachment extends Attachment {

    /** Default width estimate for annotations. */
    private static final double DEFAULT_WIDTH = 40.0;

    /** Default height for annotations. */
    private static final double DEFAULT_HEIGHT = 14.0;

    /** The annotation data. */
    private @NotNull Annotation annotation;

    /**
     * Creates an annotation attachment with the specified text.
     *
     * @param text The annotation text
     */
    public AnnotationAttachment(@NotNull String text) {
        this.annotation = new Annotation(text);
        setPlacement(Placement.ABOVE);
        setAlignment(Alignment.LEFT);
    }

    /**
     * Creates an annotation attachment with the specified annotation data.
     *
     * @param annotation The annotation data
     */
    public AnnotationAttachment(@NotNull Annotation annotation) {
        this.annotation = annotation;
        setPlacement(Placement.ABOVE);
        setAlignment(Alignment.LEFT);
    }

    /**
     * Creates an annotation attachment attached to a note.
     *
     * @param parent     The parent note
     * @param annotation The annotation data
     */
    public AnnotationAttachment(@Nullable Note parent, @NotNull Annotation annotation) {
        this.annotation = annotation;
        setParentNote(parent);
        setPlacement(Placement.ABOVE);
        setAlignment(Alignment.LEFT);

        if (parent != null) {
            setParentElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the annotation data.
     */
    public @NotNull Annotation getAnnotation() {
        return annotation;
    }

    /**
     * Sets the annotation data.
     */
    public void setAnnotation(@NotNull Annotation annotation) {
        this.annotation = annotation;
    }

    /**
     * Returns the annotation text.
     */
    public @NotNull String getText() {
        return annotation.getAnnotation();
    }

    /**
     * Sets the annotation text.
     */
    public void setText(@NotNull String text) {
        annotation.setAnnotation(text);
    }

    @Override
    public double getContentWidth() {
        // In a real implementation, this would measure the text width
        return DEFAULT_WIDTH;
    }

    @Override
    public double getContentHeight() {
        return DEFAULT_HEIGHT;
    }
}
