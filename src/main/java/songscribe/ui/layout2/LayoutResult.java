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

package songscribe.ui.layout2;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.ui.layout.Bounds;
import songscribe.ui.layout.LineElement;

/**
 * Immutable result of the layout engine containing all positioned elements for rendering.
 * <p>
 * The LayoutResult provides rendering code with final positions for all elements in a line,
 * eliminating the need for any position calculations during rendering. It contains:
 * <ul>
 *   <li>Note columns with their horizontal positions</li>
 *   <li>Line elements (attachments, articulations) with their bounds</li>
 *   <li>Staff geometry (top, bottom, lyric baseline)</li>
 *   <li>Total line height for vertical spacing</li>
 * </ul>
 * <p>
 * This class is immutable after construction. Use {@link Builder} to create instances.
 */
public final class LayoutResult {

    private final @NotNull Map<Note, NoteColumn> noteColumns;
    private final @NotNull Map<LineElement, Bounds> elementBounds;
    private final double lineHeight;
    private final double staffTopY;
    private final double staffBottomY;
    private final double lyricBaselineY;

    /**
     * Creates a layout result with the given data.
     * <p>
     * Use {@link Builder} rather than calling this constructor directly.
     *
     * @param noteColumns      Map of notes to their columns with positions
     * @param elementBounds    Map of line elements to their bounds
     * @param lineHeight       Total height of the line (including staff, elements, and lyrics)
     * @param staffTopY        Y position of the top staff line
     * @param staffBottomY     Y position of the bottom staff line
     * @param lyricBaselineY   Y position of the lyric baseline (0 if no lyrics)
     */
    private LayoutResult(
            @NotNull Map<Note, NoteColumn> noteColumns,
            @NotNull Map<LineElement, Bounds> elementBounds,
            double lineHeight,
            double staffTopY,
            double staffBottomY,
            double lyricBaselineY) {
        this.noteColumns = Map.copyOf(noteColumns);
        this.elementBounds = Map.copyOf(elementBounds);
        this.lineHeight = lineHeight;
        this.staffTopY = staffTopY;
        this.staffBottomY = staffBottomY;
        this.lyricBaselineY = lyricBaselineY;
    }

    // ==========================================================================
    // Note Column Access
    // ==========================================================================

    /**
     * Returns the note column for a specific note.
     *
     * @param note The note to look up
     * @return The note column, or null if the note was not laid out
     */
    public @Nullable NoteColumn getNoteColumn(@NotNull Note note) {
        return noteColumns.get(note);
    }

    /**
     * Returns the X position of a note's column center.
     *
     * @param note The note to look up
     * @return The X position, or 0 if the note was not laid out
     */
    public double getNoteX(@NotNull Note note) {
        var column = noteColumns.get(note);
        return column != null ? column.getX() : 0;
    }

    /**
     * Returns an unmodifiable view of all note columns.
     *
     * @return Map of notes to their columns
     */
    public @NotNull Map<Note, NoteColumn> getNoteColumns() {
        return noteColumns;
    }

    /**
     * Returns whether a note was laid out.
     *
     * @param note The note to check
     * @return true if the note has a column
     */
    public boolean hasNote(@NotNull Note note) {
        return noteColumns.containsKey(note);
    }

    // ==========================================================================
    // Line Element Access
    // ==========================================================================

    /**
     * Returns the bounds for a specific line element.
     *
     * @param element The element to look up
     * @return The bounds, or null if the element was not laid out
     */
    public @Nullable Bounds getElementBounds(@NotNull LineElement element) {
        return elementBounds.get(element);
    }

    /**
     * Returns the position (top-left corner) of a specific line element.
     *
     * @param element The element to look up
     * @return The position, or null if the element was not laid out
     */
    public @Nullable Point2D getElementPosition(@NotNull LineElement element) {
        var bounds = elementBounds.get(element);

        if (bounds == null) {
            return null;
        }

        return new Point2D.Double(bounds.getLeft(), bounds.getTop());
    }

    /**
     * Returns an unmodifiable view of all element bounds.
     *
     * @return Map of line elements to their bounds
     */
    public @NotNull Map<LineElement, Bounds> getElementBounds() {
        return elementBounds;
    }

    /**
     * Returns whether a line element was laid out.
     *
     * @param element The element to check
     * @return true if the element has bounds
     */
    public boolean hasElement(@NotNull LineElement element) {
        return elementBounds.containsKey(element);
    }

    // ==========================================================================
    // Compatibility Methods (for renderers expecting LineElementLayoutResult interface)
    // ==========================================================================

    /**
     * Returns the bounds for a specific element.
     * <p>
     * This method provides compatibility with code expecting LineElementLayoutResult.
     * Accepts Object for flexibility but element should be a LineElement.
     *
     * @param element The element to look up
     * @return The bounds, or null if the element was not laid out
     */
    public @Nullable Bounds getBounds(@NotNull Object element) {
        if (element instanceof LineElement) {
            return elementBounds.get((LineElement) element);
        }

        return null;
    }

    /**
     * Finds bounds for an attachment with the given parent note and type.
     * <p>
     * Used by renderers that need to look up layout results for attachments
     * but don't have direct access to the attachment object created during layout.
     *
     * @param parentNote     The note the attachment is attached to
     * @param attachmentType The type of attachment to find
     * @return The bounds if found, null otherwise
     */
    public @Nullable Bounds findAttachmentBounds(
            @NotNull Note parentNote,
            @NotNull Class<? extends songscribe.ui.layout.Attachment> attachmentType) {

        for (var entry : elementBounds.entrySet()) {
            var element = entry.getKey();

            if (attachmentType.isInstance(element)) {
                var attachment = (songscribe.ui.layout.Attachment) element;

                if (attachment.getParentNote() == parentNote) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Finds the attachment object with the given parent note and type.
     * <p>
     * Used by renderers that need access to the attachment object created during layout.
     *
     * @param parentNote     The note the attachment is attached to
     * @param attachmentType The type of attachment to find
     * @param <A>            The attachment type
     * @return The attachment if found, null otherwise
     */
    @SuppressWarnings("unchecked")
    public @Nullable <A extends songscribe.ui.layout.Attachment> A findAttachment(
            @NotNull Note parentNote,
            @NotNull Class<A> attachmentType) {

        for (var element : elementBounds.keySet()) {
            if (attachmentType.isInstance(element)) {
                var attachment = (songscribe.ui.layout.Attachment) element;

                if (attachment.getParentNote() == parentNote) {
                    return (A) attachment;
                }
            }
        }

        return null;
    }

    /**
     * Finds bounds for a range element with the given anchor and end notes.
     * <p>
     * Used by renderers that need to look up layout results for range elements
     * but don't have direct access to the range element object created during layout.
     *
     * @param anchorNote       The anchor (start) note of the range
     * @param endNote          The end note of the range
     * @param rangeElementType The type of range element to find
     * @return The bounds if found, null otherwise
     */
    public @Nullable Bounds findRangeElementBounds(
            @NotNull Note anchorNote,
            @NotNull Note endNote,
            @NotNull Class<? extends songscribe.ui.layout.RangeElement> rangeElementType) {

        for (var entry : elementBounds.entrySet()) {
            var element = entry.getKey();

            if (rangeElementType.isInstance(element)) {
                var rangeElement = (songscribe.ui.layout.RangeElement) element;

                if (rangeElement.getAnchorNote() == anchorNote &&
                    rangeElement.getEndNote() == endNote) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Returns whether the result contains bounds for the given element.
     * <p>
     * This method provides compatibility with code expecting LineElementLayoutResult.
     *
     * @param element The element to check
     * @return true if bounds exist for this element
     */
    public boolean contains(@NotNull Object element) {
        if (element instanceof LineElement) {
            return elementBounds.containsKey((LineElement) element);
        }

        return false;
    }

    // ==========================================================================
    // Staff Geometry
    // ==========================================================================

    /**
     * Returns the total height of this line (staff + elements + lyrics).
     */
    public double getLineHeight() {
        return lineHeight;
    }

    /**
     * Returns the Y position of the top staff line.
     */
    public double getStaffTopY() {
        return staffTopY;
    }

    /**
     * Returns the Y position of the bottom staff line.
     */
    public double getStaffBottomY() {
        return staffBottomY;
    }

    /**
     * Returns the Y position of the lyric baseline.
     *
     * @return Lyric baseline Y, or 0 if no lyrics on this line
     */
    public double getLyricBaselineY() {
        return lyricBaselineY;
    }

    /**
     * Returns whether this line has lyrics.
     */
    public boolean hasLyrics() {
        return lyricBaselineY > 0;
    }

    /**
     * Returns the width of this line (rightmost X position).
     * <p>
     * Calculates the rightmost edge of all note columns in the line.
     *
     * @return Line width in pixels, or 0 if no columns
     */
    public double getLineWidth() {
        double maxX = 0;

        for (var column : noteColumns.values()) {
            double rightEdge = column.getRightEdgeX();

            if (rightEdge > maxX) {
                maxX = rightEdge;
            }
        }

        return maxX;
    }

    // ==========================================================================
    // Statistics
    // ==========================================================================

    /**
     * Returns the number of note columns in this result.
     */
    public int getNoteColumnCount() {
        return noteColumns.size();
    }

    /**
     * Returns the number of line elements in this result.
     */
    public int getElementCount() {
        return elementBounds.size();
    }

    // ==========================================================================
    // Builder
    // ==========================================================================

    /**
     * Builder for creating LayoutResult instances incrementally.
     */
    public static class Builder {

        private final Map<Note, NoteColumn> noteColumns;
        private final Map<LineElement, Bounds> elementBounds;
        private double lineHeight = 0;
        private double staffTopY = 0;
        private double staffBottomY = 0;
        private double lyricBaselineY = 0;

        public Builder() {
            this.noteColumns = new HashMap<>();
            this.elementBounds = new HashMap<>();
        }

        /**
         * Adds a note column to the result.
         *
         * @param note   The note
         * @param column The note's column with position
         * @return This builder for chaining
         */
        public Builder putNoteColumn(@NotNull Note note, @NotNull NoteColumn column) {
            noteColumns.put(note, column);
            return this;
        }

        /**
         * Adds a line element with its bounds to the result.
         *
         * @param element The element
         * @param bounds  The element's bounds
         * @return This builder for chaining
         */
        public Builder putElementBounds(@NotNull LineElement element, @NotNull Bounds bounds) {
            elementBounds.put(element, bounds);
            return this;
        }

        /**
         * Sets the total line height.
         *
         * @param lineHeight Height in pixels
         * @return This builder for chaining
         */
        public Builder setLineHeight(double lineHeight) {
            this.lineHeight = lineHeight;
            return this;
        }

        /**
         * Sets the staff geometry.
         *
         * @param staffTopY    Y position of top staff line
         * @param staffBottomY Y position of bottom staff line
         * @return This builder for chaining
         */
        public Builder setStaffGeometry(double staffTopY, double staffBottomY) {
            this.staffTopY = staffTopY;
            this.staffBottomY = staffBottomY;
            return this;
        }

        /**
         * Sets the lyric baseline Y position.
         *
         * @param lyricBaselineY Y position (0 if no lyrics)
         * @return This builder for chaining
         */
        public Builder setLyricBaselineY(double lyricBaselineY) {
            this.lyricBaselineY = lyricBaselineY;
            return this;
        }

        /**
         * Builds the immutable result.
         *
         * @return The layout result
         */
        public LayoutResult build() {
            return new LayoutResult(
                noteColumns,
                elementBounds,
                lineHeight,
                staffTopY,
                staffBottomY,
                lyricBaselineY
            );
        }
    }

    /**
     * Creates a new builder for LayoutResult.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format(
            "LayoutResult{columns=%d, elements=%d, height=%.1f, staff=[%.1f, %.1f], lyrics=%.1f}",
            noteColumns.size(),
            elementBounds.size(),
            lineHeight,
            staffTopY,
            staffBottomY,
            lyricBaselineY
        );
    }
}
