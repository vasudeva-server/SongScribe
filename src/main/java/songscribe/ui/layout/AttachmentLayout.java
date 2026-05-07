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

import module java.desktop;

import org.jspecify.annotations.Nullable;

/**
 * Layout information for a note attachment (tempo, fermata, trill, annotation, etc.).
 * <p>
 * Attachments are elements that attach to a specific note and position themselves
 * relative to it using a {@link MarginReference}.
 * <p>
 * Mixed-unit design: {@code positionPx} is the draw-time coordinate (pixels, passed
 * directly to {@code Graphics2D}); {@code bounds} is the hit-test region in staff spaces.
 * Callers must construct {@code ElementBoundsSs} in staff spaces consistently.
 */
public record AttachmentLayout(Type type, int elementIndex, Point positionPx, ElementBoundsSs bounds,
                               @Nullable Object data) {

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

    /**
     * Creates attachment layout.
     *
     * @param type      Type of attachment
     * @param elementIndex Index of the element this attaches to
     * @param positionPx Rendered position (X, Y)
     * @param bounds    Element bounds for hit testing
     * @param data      Type-specific data (e.g., tempo value, annotation text)
     */
    public AttachmentLayout {
    }

    /**
     * Creates attachment layout without additional data.
     */
    public AttachmentLayout(
        Type type,
        int elementIndex,
        Point positionPx,
        ElementBoundsSs bounds
    ) {
        this(type, elementIndex, positionPx, bounds, null);
    }

    /**
     * Returns the attachment type.
     */
    @Override
    public Type type() {
        return type;
    }

    /**
     * Returns the index of the note this attachment belongs to.
     */
    @Override
    public int elementIndex() {
        return elementIndex;
    }

    /**
     * Returns the rendered position.
     */
    @Override
    public Point positionPx() {
        return positionPx;
    }

    /**
     * Returns the X coordinate.
     */
    public int getXPx() {
        return positionPx.x;
    }

    /**
     * Returns the Y coordinate.
     */
    public int getYPx() {
        return positionPx.y;
    }

    /**
     * Returns the element bounds for hit testing.
     */
    @Override
    public ElementBoundsSs bounds() {
        return bounds;
    }

    /**
     * Returns type-specific data, or null if none.
     */
    @Override
    public @Nullable Object data() {
        return data;
    }

    /**
     * Returns data cast to the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getDataAs(Class<T> type) {
        //noinspection PointlessNullCheck
        if (data != null && type.isInstance(data)) {
            return (T) data;
        }

        return null;
    }

    /**
     * Returns the vertical order for stacking.
     */
    public VerticalOrder getVerticalOrder() {
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
    public boolean containsPoint(double xSs, double ySs) {
        return bounds.containsForHitTest(xSs, ySs);
    }

    @Override
    public String toString() {
        return "AttachmentLayout{" +
            "type=" + type +
            ", elementIndex=" + elementIndex +
            ", pos=(" + positionPx.x + ',' + positionPx.y + ')' +
            (data != null ? ", data=" + data : "") +
            '}';
    }
}
