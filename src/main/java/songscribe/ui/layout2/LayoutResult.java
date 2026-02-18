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
    // Insertion Note Positioning (Edit Mode)
    // ==========================================================================

    /**
     * Finds which insertion slot a mouse X coordinate falls into.
     * <p>
     * Insertion slots are the positions where a note can be inserted or replaced:
     * <ul>
     *   <li>Index 0 to noteCount-1: over an existing note (for replacement)</li>
     *   <li>Index noteCount: after the last note (for appending)</li>
     * </ul>
     * <p>
     * If the mouse is within the horizontal bounds of a note head, returns that note's index
     * to indicate replacement. Otherwise, returns the insertion slot between notes.
     *
     * @param mouseX Mouse X coordinate in pixels
     * @param line   The line containing the notes
     * @return Insertion index (0 to noteCount inclusive)
     */
    public int findInsertionIndex(double mouseX, @NotNull songscribe.music.Line line) {
        int noteCount = line.noteCount();

        if (noteCount == 0) {
            return 0;
        }

        // Note head half-width (from NoteColumnBuilder.HALF_NOTE_HEAD)
        double noteHeadHalfWidth = 9.0;

        // Check each note to see if mouse is within its note head bounds
        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var column = noteColumns.get(note);

            if (column == null) {
                continue;
            }

            var noteX = column.getX();
            var noteLeft = noteX - noteHeadHalfWidth;
            var noteRight = noteX + noteHeadHalfWidth;

            // If mouse is within note head bounds, return this note's index (for replacement)
            if (mouseX >= noteLeft && mouseX <= noteRight) {
                return i;
            }
        }

        // Mouse is not over any note head - find insertion slot between notes

        // Check if before first note
        var firstNote = line.getNote(0);
        var firstColumn = noteColumns.get(firstNote);

        if (firstColumn == null) {
            return 0;
        }

        if (mouseX < firstColumn.getX() - noteHeadHalfWidth) {
            return 0;
        }

        // Check if after last note
        var lastNote = line.getNote(noteCount - 1);
        var lastColumn = noteColumns.get(lastNote);

        if (lastColumn == null) {
            return noteCount;
        }

        if (mouseX > lastColumn.getX() + noteHeadHalfWidth) {
            return noteCount;
        }

        // Find the slot between notes (excluding note head bounds)
        for (var i = 0; i < noteCount - 1; i++) {
            var currentNote = line.getNote(i);
            var nextNote = line.getNote(i + 1);

            var currentColumn = noteColumns.get(currentNote);
            var nextColumn = noteColumns.get(nextNote);

            if (currentColumn == null || nextColumn == null) {
                continue;
            }

            var currentRight = currentColumn.getX() + noteHeadHalfWidth;
            var nextLeft = nextColumn.getX() - noteHeadHalfWidth;

            // Check if mouseX is in the gap between note heads
            if (mouseX > currentRight && mouseX < nextLeft) {
                return i + 1;
            }
        }

        // Fallback: return position after last note
        return noteCount;
    }

    /**
     * Checks whether the mouse X coordinate is directly over an existing note head.
     *
     * @param mouseX Mouse X coordinate in pixels
     * @param line   The line containing the notes
     * @return true if the mouse is within the horizontal bounds of a note head
     */
    public boolean isMouseOverNoteHead(double mouseX, @NotNull songscribe.music.Line line) {
        int noteCount = line.noteCount();

        if (noteCount == 0) {
            return false;
        }

        double noteHeadHalfWidth = 9.0;

        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var column = noteColumns.get(note);

            if (column == null) {
                continue;
            }

            var noteX = column.getX();

            if (mouseX >= noteX - noteHeadHalfWidth && mouseX <= noteX + noteHeadHalfWidth) {
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the X position for rendering an insertion note at a given index.
     * <p>
     * If the mouse is within the horizontal bounds of a note head, snaps to that note's position.
     * Otherwise, positions between notes or after the last note as appropriate.
     *
     * @param insertionIndex The insertion index (0 to noteCount inclusive)
     * @param mouseX         Mouse X coordinate (used to detect if over a note head)
     * @param insertionNote  The note to be inserted (used to calculate extents for after-last-note positioning)
     * @param line           The line containing the notes
     * @return X position in pixels for rendering the insertion note
     */
    public double calculateInsertionX(
            int insertionIndex,
            double mouseX,
            @NotNull Note insertionNote,
            @NotNull songscribe.music.Line line) {

        int noteCount = line.noteCount();

        // Empty line - use first note position (clef + key signature + offset)
        if (noteCount == 0) {
            return LayoutConstants.calculateFirstNoteX(line.getKeyAccidentalCount());
        }

        // Note head half-width
        double noteHeadHalfWidth = 9.0;

        // Check if mouse is over any note head - if so, snap to that note's position
        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var column = noteColumns.get(note);

            if (column == null) {
                continue;
            }

            var noteX = column.getX();
            if (mouseX >= noteX - noteHeadHalfWidth && mouseX <= noteX + noteHeadHalfWidth) {
                // Mouse is over this note head - snap to its position
                return noteX;
            }
        }

        // Mouse is not over a note head - handle insertion

        // Before first note - position to the left
        if (insertionIndex == 0) {
            var firstNote = line.getNote(0);
            var firstColumn = noteColumns.get(firstNote);

            if (firstColumn == null) {
                return LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);
            }

            return firstColumn.getX() - 15;  // FIRST_NOTE_IN_LINE_MOVEMENT offset
        }

        // After last note - use same spacing logic as layout engine
        if (insertionIndex >= noteCount) {
            var lastNote = line.getNote(noteCount - 1);
            var lastColumn = noteColumns.get(lastNote);

            if (lastColumn == null) {
                return LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);
            }

            // Build a temporary column for the insertion note to calculate proper spacing
            var insertionColumn = new NoteColumn(
                    insertionNote,
                    java.util.Collections.emptyList(),
                    NoteColumnBuilder.calculateLeftExtent(insertionNote),
                    NoteColumnBuilder.calculateRightExtent(insertionNote),
                    0,
                    0,
                    null,
                    0,
                    null
            );

            // Use the same spacing calculation as HorizontalSpacingCalculator
            return HorizontalSpacingCalculator.calculateNextColumnX(lastColumn, insertionColumn);
        }

        // Between notes - use midpoint
        var prevNote = line.getNote(insertionIndex - 1);
        var currNote = line.getNote(insertionIndex);

        var prevColumn = noteColumns.get(prevNote);
        var currColumn = noteColumns.get(currNote);

        if (prevColumn == null || currColumn == null) {
            return LayoutConstants.px(LayoutConstants.FIRST_NOTE_OFFSET);
        }

        return (prevColumn.getX() + currColumn.getX()) / 2.0;
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
