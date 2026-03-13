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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Layout information for range elements (ties, beams, tuplets, endings).
 * <p>
 * Range elements span multiple notes and are positioned based on the maximum
 * extent of all notes in the range. Placement (above or below) is determined
 * by scanning ALL notes in the range for stem direction.
 */
public final class RangeLayout {

    /**
     * Types of range elements.
     */
    public enum Type {
        /** Tie connecting same-pitch notes */
        TIE,

        /** Beam connecting note stems */
        BEAM,

        /** Tuplet bracket (e.g., triplet) */
        TUPLET,

        /** First/second ending bracket */
        ENDING,

        /** Crescendo hairpin over range */
        CRESCENDO,

        /** Diminuendo hairpin over range */
        DIMINUENDO
    }

    private final @NotNull Type type;
    private final int startElementIndex;
    private final int endElementIndex;
    private final boolean above;
    private final @NotNull ElementBounds bounds;
    private final @Nullable Path2D path;
    private final @Nullable Object data;

    /**
     * Creates range layout.
     *
     * @param type           Type of range element
     * @param startElementIndex Index of first element in range
     * @param endElementIndex   Index of last element in range
     * @param above             True if placed above elements, false if below
     * @param bounds         Element bounds for hit testing
     * @param path           Rendered curve/line path (may be null for some types)
     * @param data           Type-specific data (e.g., tuplet number, ending number)
     */
    public RangeLayout(
        @NotNull Type type,
        int startElementIndex,
        int endElementIndex,
        boolean above,
        @NotNull ElementBounds bounds,
        @Nullable Path2D path,
        @Nullable Object data
    ) {
        this.type = type;
        this.startElementIndex = startElementIndex;
        this.endElementIndex = endElementIndex;
        this.above = above;
        this.bounds = bounds;
        this.path = path;
        this.data = data;
    }

    /**
     * Creates range layout without path or data.
     */
    public RangeLayout(
        @NotNull Type type,
        int startElementIndex,
        int endElementIndex,
        boolean above,
        @NotNull ElementBounds bounds
    ) {
        this(type, startElementIndex, endElementIndex, above, bounds, null, null);
    }

    /**
     * Returns the range element type.
     */
    public @NotNull Type getType() {
        return type;
    }

    /**
     * Returns the index of the first note in the range.
     */
    public int getStartElementIndex() {
        return startElementIndex;
    }

    /**
     * Returns the index of the last note in the range.
     */
    public int getEndElementIndex() {
        return endElementIndex;
    }

    /**
     * Returns whether this element is placed above the notes.
     */
    public boolean isAbove() {
        return above;
    }

    /**
     * Returns the element bounds.
     */
    public @NotNull ElementBounds getBounds() {
        return bounds;
    }

    /**
     * Returns the rendered path (curve for ties, lines for brackets).
     */
    public @Nullable Path2D getPath() {
        return path;
    }

    /**
     * Returns type-specific data.
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
        if (above) {
            return switch (type) {
                case ENDING -> VerticalOrder.ENDINGS;
                default -> VerticalOrder.RANGE_ELEMENTS_ABOVE;
            };
        } else {
            return VerticalOrder.RANGE_ELEMENTS_BELOW;
        }
    }

    /**
     * Returns the number of notes spanned by this range.
     */
    public int getElementCount() {
        return endElementIndex - startElementIndex + 1;
    }

    /**
     * Returns whether the given element index is within this range.
     */
    public boolean containsElement(int elementIndex) {
        return elementIndex >= startElementIndex && elementIndex <= endElementIndex;
    }

    /**
     * Returns whether the given point is within hit testing bounds.
     */
    public boolean containsPoint(double x, double y) {
        return bounds.containsForHitTest(x, y);
    }

    @Override
    public String toString() {
        return "RangeLayout{" +
            "type=" + type +
            ", range=[" + startElementIndex + ".." + endElementIndex + "]" +
            ", above=" + above +
            (data != null ? ", data=" + data : "") +
            "}";
    }
}
