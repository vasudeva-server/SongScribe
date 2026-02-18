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

import java.awt.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Layout information for a note attachment (tempo, fermata, trill, annotation, etc.).
 * <p>
 * Attachments are elements that attach to a specific note and position themselves
 * relative to it using a {@link MarginReference}.
 */
public final class AttachmentLayout {

    /**
     * Types of note attachments.
     */
    public enum Type {
        /** Tempo marking (e.g., "♩= 120") */
        TEMPO,

        /** Beat/time signature change */
        BEAT_CHANGE,

        /** Fermata (pause) marking */
        FERMATA,

        /** Trill marking */
        TRILL,

        /** Text annotation above note */
        ANNOTATION_ABOVE,

        /** Text annotation below note */
        ANNOTATION_BELOW,

        /** Dynamic marking (p, f, ff, etc.) */
        DYNAMIC,

        /** Crescendo hairpin */
        CRESCENDO,

        /** Diminuendo hairpin */
        DIMINUENDO
    }

    private final @NotNull Type type;
    private final int noteIndex;
    private final @NotNull Point position;
    private final @NotNull ElementBounds bounds;
    private final @Nullable Object data;

    /**
     * Creates attachment layout.
     *
     * @param type      Type of attachment
     * @param noteIndex Index of the note this attaches to
     * @param position  Rendered position (X, Y)
     * @param bounds    Element bounds for hit testing
     * @param data      Type-specific data (e.g., tempo value, annotation text)
     */
    public AttachmentLayout(
        @NotNull Type type,
        int noteIndex,
        @NotNull Point position,
        @NotNull ElementBounds bounds,
        @Nullable Object data
    ) {
        this.type = type;
        this.noteIndex = noteIndex;
        this.position = position;
        this.bounds = bounds;
        this.data = data;
    }

    /**
     * Creates attachment layout without additional data.
     */
    public AttachmentLayout(
        @NotNull Type type,
        int noteIndex,
        @NotNull Point position,
        @NotNull ElementBounds bounds
    ) {
        this(type, noteIndex, position, bounds, null);
    }

    /**
     * Returns the attachment type.
     */
    public @NotNull Type getType() {
        return type;
    }

    /**
     * Returns the index of the note this attachment belongs to.
     */
    public int getNoteIndex() {
        return noteIndex;
    }

    /**
     * Returns the rendered position.
     */
    public @NotNull Point getPosition() {
        return position;
    }

    /**
     * Returns the X coordinate.
     */
    public int getX() {
        return position.x;
    }

    /**
     * Returns the Y coordinate.
     */
    public int getY() {
        return position.y;
    }

    /**
     * Returns the element bounds for hit testing.
     */
    public @NotNull ElementBounds getBounds() {
        return bounds;
    }

    /**
     * Returns type-specific data, or null if none.
     */
    public @Nullable Object getData() {
        return data;
    }

    /**
     * Returns data cast to the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getDataAs(Class<T> type) {
        if (data != null && type.isInstance(data)) {
            return (T) data;
        }

        return null;
    }

    /**
     * Returns the vertical order for stacking.
     */
    public @NotNull VerticalOrder getVerticalOrder() {
        return switch (type) {
            case TEMPO -> VerticalOrder.TEMPO;
            case BEAT_CHANGE -> VerticalOrder.BEAT_CHANGE;
            case FERMATA, TRILL -> VerticalOrder.TRILL;
            case ANNOTATION_ABOVE -> VerticalOrder.ANNOTATIONS_ABOVE;
            case ANNOTATION_BELOW, DYNAMIC -> VerticalOrder.DYNAMICS;
            case CRESCENDO, DIMINUENDO -> VerticalOrder.DYNAMICS;
        };
    }

    /**
     * Returns whether this attachment is positioned above the staff.
     */
    public boolean isAboveStaff() {
        return getVerticalOrder().isAboveStaff();
    }

    /**
     * Returns whether the given point is within hit testing bounds.
     */
    public boolean containsPoint(double x, double y) {
        return bounds.containsForHitTest(x, y);
    }

    @Override
    public String toString() {
        return "AttachmentLayout{" +
            "type=" + type +
            ", noteIndex=" + noteIndex +
            ", pos=(" + position.x + "," + position.y + ")" +
            (data != null ? ", data=" + data : "") +
            "}";
    }
}
