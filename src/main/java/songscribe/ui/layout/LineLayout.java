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

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

/**
 * Layout information for a single staff line.
 * <p>
 * Contains positions and bounds for all elements within the line:
 * notes, attachments (tempo, fermata, etc.), range elements (ties, etc.),
 * and lyrics.
 *
 * @deprecated Replaced by new ElementRenderer system (Phase 6). Scheduled for removal in Phase 8.
 */
@Deprecated
public final class LineLayout {

    private final int lineIndex;
    private final @NotNull ElementBounds lineBounds;
    private final int middleLineY;
    private final @NotNull List<NoteLayout> notes;
    private final @NotNull List<AttachmentLayout> attachments;
    private final @NotNull List<RangeLayout> rangeElements;
    private final @NotNull LyricsLayout lyrics;

    /**
     * Creates line layout with all elements.
     *
     * @param lineIndex     Index of this line in the composition
     * @param lineBounds    Full extent of the line (staff + all content)
     * @param middleLineY   Y coordinate of the middle (B) staff line
     * @param notes         Note layouts
     * @param attachments   Attachment layouts (tempo, fermata, etc.)
     * @param rangeElements Range element layouts (ties, etc.)
     * @param lyrics        Lyrics layout for this line
     */
    public LineLayout(
        int lineIndex,
        @NotNull ElementBounds lineBounds,
        int middleLineY,
        @NotNull List<NoteLayout> notes,
        @NotNull List<AttachmentLayout> attachments,
        @NotNull List<RangeLayout> rangeElements,
        @NotNull LyricsLayout lyrics
    ) {
        this.lineIndex = lineIndex;
        this.lineBounds = lineBounds;
        this.middleLineY = middleLineY;
        this.notes = List.copyOf(notes);
        this.attachments = List.copyOf(attachments);
        this.rangeElements = List.copyOf(rangeElements);
        this.lyrics = lyrics;
    }

    /**
     * Creates an empty line layout.
     */
    public static LineLayout empty(int lineIndex, @NotNull ElementBounds lineBounds, int middleLineY) {
        return new LineLayout(
            lineIndex,
            lineBounds,
            middleLineY,
            List.of(),
            List.of(),
            List.of(),
            LyricsLayout.empty()
        );
    }

    /**
     * Returns the line index within the composition.
     */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Returns the full line bounds (staff + all content).
     */
    public @NotNull ElementBounds getLineBounds() {
        return lineBounds;
    }

    /**
     * Returns the Y coordinate of the middle (B) staff line.
     */
    public int getMiddleLineY() {
        return middleLineY;
    }

    /**
     * Returns the list of note layouts.
     */
    public @NotNull List<NoteLayout> getNotes() {
        return notes;
    }

    /**
     * Returns the note layout at the given index, or empty if out of bounds.
     */
    public Optional<NoteLayout> getNote(int noteIndex) {
        if (noteIndex >= 0 && noteIndex < notes.size()) {
            return Optional.of(notes.get(noteIndex));
        }

        return Optional.empty();
    }

    /**
     * Returns the number of notes.
     */
    public int getNoteCount() {
        return notes.size();
    }

    /**
     * Returns the list of attachment layouts.
     */
    public @NotNull List<AttachmentLayout> getAttachments() {
        return attachments;
    }

    /**
     * Returns attachments of the specified type.
     */
    public @NotNull List<AttachmentLayout> getAttachments(@NotNull AttachmentLayout.Type type) {
        return attachments.stream()
            .filter(a -> a.getType() == type)
            .toList();
    }

    /**
     * Returns the list of range element layouts.
     */
    public @NotNull List<RangeLayout> getRangeElements() {
        return rangeElements;
    }

    /**
     * Returns range elements of the specified type.
     */
    public @NotNull List<RangeLayout> getRangeElements(@NotNull RangeLayout.Type type) {
        return rangeElements.stream()
            .filter(r -> r.getType() == type)
            .toList();
    }

    /**
     * Returns the lyrics layout for this line.
     */
    public @NotNull LyricsLayout getLyrics() {
        return lyrics;
    }

    /**
     * Returns the Y coordinate of the top staff line.
     */
    public int getTopStaffLineY() {
        return middleLineY - (2 * 8); // 2 staff lines above middle, 8px per line
    }

    /**
     * Returns the Y coordinate of the bottom staff line.
     */
    public int getBottomStaffLineY() {
        return middleLineY + (2 * 8); // 2 staff lines below middle, 8px per line
    }

    /**
     * Returns all elements above staff sorted by vertical order.
     */
    public @NotNull List<Object> getAboveStaffElements() {
        var above = new java.util.ArrayList<>();

        for (var attachment : attachments) {
            if (attachment.isAboveStaff()) {
                above.add(attachment);
            }
        }

        for (var range : rangeElements) {
            if (range.isAbove()) {
                above.add(range);
            }
        }

        return above;
    }

    /**
     * Returns whether the given point is within any note's hit testing bounds.
     */
    public Optional<NoteLayout> findNoteAt(double x, double y) {
        return notes.stream()
            .filter(n -> n.containsPoint(x, y))
            .findFirst();
    }

    /**
     * Returns whether the given point is within any attachment's hit testing bounds.
     */
    public Optional<AttachmentLayout> findAttachmentAt(double x, double y) {
        return attachments.stream()
            .filter(a -> a.containsPoint(x, y))
            .findFirst();
    }

    @Override
    public String toString() {
        return "LineLayout{" +
            "index=" + lineIndex +
            ", middleY=" + middleLineY +
            ", notes=" + notes.size() +
            ", attachments=" + attachments.size() +
            ", ranges=" + rangeElements.size() +
            "}";
    }
}
