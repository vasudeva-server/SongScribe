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

import java.awt.geom.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.data.Interval;
import songscribe.music.Note;
import songscribe.ui.layout.Bounds;
import songscribe.ui.layout.LineElement;

/**
 * Immutable result of the layout engine containing all positioned elements for rendering.
 * <p>
 * All positions and dimensions are in staff-space units.
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

    /**
     * Offset for positioning an insertion note before the first note in the line (ss).
     */
    private static final double INSERTION_BEFORE_FIRST_OFFSET_SS = 1.875;  // 15px

    private final @NotNull Map<Note, NoteColumn> noteColumns;
    private final @NotNull Map<LineElement, Bounds> elementBounds;
    private final @NotNull Map<Interval, BeamLayout> beamLayouts;
    private final @NotNull Map<Note, StemLayout> stemLayouts;
    private final @NotNull Map<Interval, TieLayout> tieLayouts;
    private final double lineHeightSs;
    private final double staffTopYSs;
    private final double staffBottomYSs;
    private final double lyricBaselineYSs;

    /**
     * Creates a layout result with the given data.
     * <p>
     * Use {@link Builder} rather than calling this constructor directly.
     *
     * @param noteColumns      Map of notes to their columns with positions
     * @param elementBounds    Map of line elements to their bounds
     * @param beamLayouts      Map of beam intervals to their computed beam geometry
     * @param stemLayouts      Map of unbeamed notes to their computed stem geometry
     * @param tieLayouts       Map of tie intervals to their computed tie geometry
     * @param lineHeightSs       Total height of the line in staff spaces (including staff, elements, and lyrics)
     * @param staffTopYSs        Y position of the top staff line in staff spaces
     * @param staffBottomYSs     Y position of the bottom staff line in staff spaces
     * @param lyricBaselineYSs   Y position of the lyric baseline in staff spaces (0 if no lyrics)
     */
    private LayoutResult(
        @NotNull Map<Note, NoteColumn> noteColumns,
        @NotNull Map<LineElement, Bounds> elementBounds,
        @NotNull Map<Interval, BeamLayout> beamLayouts,
        @NotNull Map<Note, StemLayout> stemLayouts,
        @NotNull Map<Interval, TieLayout> tieLayouts,
        double lineHeightSs,
        double staffTopYSs,
        double staffBottomYSs,
        double lyricBaselineYSs) {
        this.noteColumns = Map.copyOf(noteColumns);
        this.elementBounds = Map.copyOf(elementBounds);
        this.beamLayouts = Map.copyOf(beamLayouts);
        this.stemLayouts = Map.copyOf(stemLayouts);
        this.tieLayouts = Map.copyOf(tieLayouts);
        this.lineHeightSs = lineHeightSs;
        this.staffTopYSs = staffTopYSs;
        this.staffBottomYSs = staffBottomYSs;
        this.lyricBaselineYSs = lyricBaselineYSs;
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
     * Returns the X position of a note's note head left edge (glyph origin).
     *
     * @param note The note to look up
     * @return The X position, or 0 if the note was not laid out
     */
    public double getNoteXSs(@NotNull Note note) {
        var column = noteColumns.get(note);
        return column != null ? column.getXSs() : 0;
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
    // Beam + Stem Layout Access
    // ==========================================================================

    /**
     * Returns the beam geometry for a beam interval.
     *
     * @param interval The beam interval to look up
     * @return The beam layout, or null if not computed
     */
    public @Nullable BeamLayout getBeamLayout(@NotNull Interval interval) {
        return beamLayouts.get(interval);
    }

    /**
     * Returns the stem geometry for a note.
     * <p>
     * Checks beamed stem layouts first (notes inside a beam group), then falls back
     * to the standalone stem layouts for unbeamed notes.
     *
     * @param note The note to look up
     * @return The stem layout, or null if not computed
     */
    public @Nullable StemLayout getStemLayout(@NotNull Note note) {
        for (var beamLayout : beamLayouts.values()) {
            var stemLayout = beamLayout.stems().get(note);

            if (stemLayout != null) {
                return stemLayout;
            }
        }

        return stemLayouts.get(note);
    }

    // ==========================================================================
    // Tie Layout Access
    // ==========================================================================

    /**
     * Returns the tie geometry for a tie interval, if it was computed during layout.
     * <p>
     * Returns an empty Optional when the interval was not laid out (e.g., degenerate
     * tie spanning notes that could not be positioned). Callers should skip rendering
     * if the result is empty.
     *
     * @param interval The tie interval to look up
     * @return An Optional containing the tie layout, or empty if not computed
     */
    public @NotNull Optional<TieLayout> getTieLayout(@NotNull Interval interval) {
        return Optional.ofNullable(tieLayouts.get(interval));
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
     * Returns the total height of this line (staff + elements + lyrics), in staff spaces.
     */
    public double getLineHeightSs() {
        return lineHeightSs;
    }

    /**
     * Returns the Y position of the top staff line, in staff spaces.
     */
    public double getStaffTopYSs() {
        return staffTopYSs;
    }

    /**
     * Returns the Y position of the bottom staff line, in staff spaces.
     */
    public double getStaffBottomYSs() {
        return staffBottomYSs;
    }

    /**
     * Returns the Y position of the lyric baseline, in staff spaces.
     *
     * @return Lyric baseline Y in staff spaces, or 0 if no lyrics on this line
     */
    public double getLyricBaselineYSs() {
        return lyricBaselineYSs;
    }

    /**
     * Returns whether this line has lyrics.
     */
    public boolean hasLyrics() {
        return lyricBaselineYSs > 0;
    }

    /**
     * Returns the width of this line (rightmost X position).
     * <p>
     * Calculates the rightmost edge of all note columns in the line.
     *
     * @return Line width in staff-space units, or 0 if no columns
     */
    public double getLineWidthSs() {
        double maxX = 0;

        for (var column : noteColumns.values()) {
            double rightEdge = column.getRightEdgeXSs();

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
     * Returns the index of the note whose head contains {@code mouseXSs}, or {@code -1} if none.
     * <p>
     * Only the horizontal (X) dimension is checked; Y position is ignored.
     * The hit zone is the actual note head body: {@code [xSs, xSs + rightExtentSs]}.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the notes
     * @return Note index, or {@code -1} if mouseXSs is not within any note head's horizontal bounds
     */
    public int findNoteAtXSs(double mouseXSs, @NotNull songscribe.music.Line line) {
        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);
            var column = noteColumns.get(note);

            if (column == null) {
                continue;
            }

            var noteX = column.getXSs();

            if (mouseXSs >= noteX && mouseXSs <= noteX + column.getRightExtentSs()) {
                return i;
            }
        }

        return -1;
    }

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
     * @param mouseX Mouse X coordinate in staff-space units
     * @param line   The line containing the notes
     * @return Insertion index (0 to noteCount inclusive)
     */
    public int findInsertionIndex(double mouseXSs, @NotNull songscribe.music.Line line) {
        int noteCount = line.noteCount();

        if (noteCount == 0) {
            return 0;
        }

        // Check each note to see if mouse is within its note head bounds
        int noteAtX = findNoteAtXSs(mouseXSs, line);

        if (noteAtX >= 0) {
            return noteAtX;
        }

        // Mouse is not over any note head - find insertion slot between notes

        // Check if before first note
        var firstNote = line.getNote(0);
        var firstColumn = noteColumns.get(firstNote);

        if (firstColumn == null) {
            return 0;
        }

        if (mouseXSs < firstColumn.getXSs()) {
            return 0;
        }

        // Check if after last note
        var lastNote = line.getNote(noteCount - 1);
        var lastColumn = noteColumns.get(lastNote);

        if (lastColumn == null) {
            return noteCount;
        }

        if (mouseXSs > lastColumn.getRightEdgeXSs()) {
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

            var currentRight = currentColumn.getRightEdgeXSs();
            var nextLeft = nextColumn.getXSs();

            // Check if mouseXSs is in the gap between note heads
            if (mouseXSs > currentRight && mouseXSs < nextLeft) {
                return i + 1;
            }
        }

        // Fallback: return position after last note
        return noteCount;
    }

    /**
     * Calculates the X position for rendering an insertion note at a given index.
     * <p>
     * If the mouse is within the horizontal bounds of a note head, snaps to that note's position.
     * Otherwise, positions between notes or after the last note as appropriate.
     *
     * @param insertionIndex The insertion index (0 to noteCount inclusive)
     * @param mouseX         Mouse X coordinate in staff-space units (used to detect if over a note head)
     * @param insertionNote  The note to be inserted (used to calculate extents for after-last-note positioning)
     * @param line           The line containing the notes
     * @return X position in staff-space units for rendering the insertion note
     */
    public double calculateInsertionXSs(
        int insertionIndex,
        double mouseXSs,
        @NotNull Note insertionNote,
        @NotNull songscribe.music.Line line) {

        int noteCount = line.noteCount();

        // Empty line - use first note position (clef + key signature + offset)
        if (noteCount == 0) {
            return LayoutConstants.calculateFirstNoteXSs(line.getKeyAccidentalCount());
        }

        // Check if mouse is over any note head - if so, snap to that note's position
        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var column = noteColumns.get(note);

            if (column == null) {
                continue;
            }

            var noteX = column.getXSs();

            if (mouseXSs >= noteX && mouseXSs <= noteX + column.getRightExtentSs()) {
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
                return LayoutConstants.FIRST_NOTE_OFFSET_SS;
            }

            return firstColumn.getXSs() - INSERTION_BEFORE_FIRST_OFFSET_SS;
        }

        // After last note - use same spacing logic as layout engine
        if (insertionIndex >= noteCount) {
            var lastNote = line.getNote(noteCount - 1);
            var lastColumn = noteColumns.get(lastNote);

            if (lastColumn == null) {
                return LayoutConstants.FIRST_NOTE_OFFSET_SS;
            }

            // Build a temporary column for the insertion note to calculate proper spacing
            var insertionColumn = new NoteColumn(
                insertionNote,
                java.util.Collections.emptyList(),
                NoteColumnBuilder.calculateLeftExtentSs(insertionNote),
                NoteColumnBuilder.calculateRightExtentSs(insertionNote, false, true),
                0,
                0,
                null,
                0,
                false
            );

            // Use the same spacing calculation as HorizontalSpacingCalculator
            return HorizontalSpacingCalculator.calculateNextColumnXSs(lastColumn, insertionColumn);
        }

        // Between notes - use midpoint
        var prevNote = line.getNote(insertionIndex - 1);
        var currNote = line.getNote(insertionIndex);

        var prevColumn = noteColumns.get(prevNote);
        var currColumn = noteColumns.get(currNote);

        if (prevColumn == null || currColumn == null) {
            return LayoutConstants.FIRST_NOTE_OFFSET_SS;
        }

        return (prevColumn.getXSs() + currColumn.getXSs()) / 2.0;
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
        private final Map<Interval, BeamLayout> beamLayouts;
        private final Map<Note, StemLayout> stemLayouts;
        private final Map<Interval, TieLayout> tieLayouts;
        private double lineHeightSs = 0;
        private double staffTopYSs = 0;
        private double staffBottomYSs = 0;
        private double lyricBaselineYSs = 0;

        public Builder() {
            this.noteColumns = new HashMap<>();
            this.elementBounds = new HashMap<>();
            this.beamLayouts = new HashMap<>();
            this.stemLayouts = new HashMap<>();
            this.tieLayouts = new HashMap<>();
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
         * @param lineHeightSs Height in staff-space units
         * @return This builder for chaining
         */
        public Builder setLineHeightSs(double lineHeightSs) {
            this.lineHeightSs = lineHeightSs;
            return this;
        }

        /**
         * Sets the staff geometry.
         *
         * @param staffTopYSs    Y position of top staff line in staff spaces
         * @param staffBottomYSs Y position of bottom staff line in staff spaces
         * @return This builder for chaining
         */
        public Builder setStaffGeometrySs(double staffTopYSs, double staffBottomYSs) {
            this.staffTopYSs = staffTopYSs;
            this.staffBottomYSs = staffBottomYSs;
            return this;
        }

        /**
         * Sets the lyric baseline Y position.
         *
         * @param lyricBaselineYSs Y position in staff spaces (0 if no lyrics)
         * @return This builder for chaining
         */
        public Builder setLyricBaselineYSs(double lyricBaselineYSs) {
            this.lyricBaselineYSs = lyricBaselineYSs;
            return this;
        }

        /**
         * Adds computed beam geometry for a beam interval.
         *
         * @param interval   The beam interval
         * @param beamLayout The computed beam geometry
         * @return This builder for chaining
         */
        public Builder putBeamLayout(@NotNull Interval interval, @NotNull BeamLayout beamLayout) {
            beamLayouts.put(interval, beamLayout);
            return this;
        }

        /**
         * Adds computed stem geometry for an unbeamed note.
         *
         * @param note       The note
         * @param stemLayout The computed stem geometry
         * @return This builder for chaining
         */
        public Builder putStemLayout(@NotNull Note note, @NotNull StemLayout stemLayout) {
            stemLayouts.put(note, stemLayout);
            return this;
        }

        /**
         * Adds computed tie geometry for a tie interval.
         *
         * @param interval  The tie interval
         * @param tieLayout The computed tie geometry
         * @return This builder for chaining
         */
        public Builder putTieLayout(@NotNull Interval interval, @NotNull TieLayout tieLayout) {
            tieLayouts.put(interval, tieLayout);
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
                beamLayouts,
                stemLayouts,
                tieLayouts,
                lineHeightSs,
                staffTopYSs,
                staffBottomYSs,
                lyricBaselineYSs
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
            lineHeightSs,
            staffTopYSs,
            staffBottomYSs,
            lyricBaselineYSs
        );
    }

    // ==========================================================================
    // Layout Records
    // ==========================================================================

    /**
     * Immutable stem geometry for a single note, computed during layout.
     * <p>
     * All values are in staff-space units.
     *
     * @param topYSs       Y position of the top of the stem
     * @param bottomYSs    Y position of the bottom of the stem
     * @param lengtheningSs Extra stem extension beyond the minimum required to reach the beam (≥ 0)
     * @param stubRight  For partial-beam notes: true if the stub extends to the right, false to the left.
     *                   Meaningless for full-beam and unbeamed notes.
     */
    public record StemLayout(
        double topYSs,
        double bottomYSs,
        double lengtheningSs,
        boolean stubRight) {}

    /**
     * Immutable beam geometry for a beam group, computed during layout.
     * <p>
     * All values are in staff-space units unless noted.
     *
     * @param slope      Beam slope in staff-space units per staff-space unit (dimensionless)
     * @param startYSs     Beam Y position at the first note's X coordinate
     * @param stemsUp    True if stems point upward (beam below noteheads)
     * @param thickeningSs Extra beam thickness from the {@code 1/cos(angle)} raster correction (ss);
     *                   added symmetrically to the nominal {@code BEAM_DEPTH}
     * @param stems      Stem geometry keyed by note, for every note in this beam group
     */
    public record BeamLayout(
        double slope,
        double startYSs,
        boolean stemsUp,
        double thickeningSs,
        @NotNull Map<Note, StemLayout> stems) {}

    /**
     * Immutable tie geometry, computed during layout.
     * <p>
     * All values are in staff-space units. The outer and inner curves form a filled
     * lens shape when rendered as a closed path: draw the outer cubic Bezier from
     * start to end, then draw the inner cubic Bezier in reverse (end back to start),
     * then close and fill.
     *
     * @param startXSs    Tie start X position
     * @param startYSs    Tie start Y position
     * @param endXSs      Tie end X position
     * @param endYSs      Tie end Y position
     * @param cp1XSs      Outer curve control point 1 X
     * @param cp1YSs      Outer curve control point 1 Y
     * @param cp2XSs      Outer curve control point 2 X
     * @param cp2YSs      Outer curve control point 2 Y
     * @param innerCp1XSs Inner curve control point 1 X
     * @param innerCp1YSs Inner curve control point 1 Y
     * @param innerCp2XSs Inner curve control point 2 X
     * @param innerCp2YSs Inner curve control point 2 Y
     */
    public record TieLayout(
        double startXSs, double startYSs,
        double endXSs, double endYSs,
        double cp1XSs, double cp1YSs,
        double cp2XSs, double cp2YSs,
        double innerCp1XSs, double innerCp1YSs,
        double innerCp2XSs, double innerCp2YSs) {}
}
