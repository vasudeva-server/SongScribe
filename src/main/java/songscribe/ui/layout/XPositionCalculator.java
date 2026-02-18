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

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;

/**
 * Calculates horizontal positions for notes based on duration and spacing.
 * <p>
 * Note positioning follows these rules:
 * <ul>
 *   <li>First note starts at {@link LayoutStylesheet#FIRST_NOTE_X}</li>
 *   <li>Subsequent notes are spaced based on the previous note's duration</li>
 *   <li>User can adjust spacing via {@link Line#getNoteDistChangeRatio()}</li>
 *   <li>Individual notes can have manual offsets via {@link Note#getXOffset()}</li>
 * </ul>
 */
public final class XPositionCalculator {

    private XPositionCalculator() {
        // Prevent instantiation
    }

    /**
     * Calculates the X position for a note based on its position in the line.
     *
     * @param noteIndex     Index of the note in the line (0-based)
     * @param note          The note to position
     * @param previousNote  The previous note (null if this is the first note)
     * @param distanceRatio User-adjustable spacing multiplier from Line
     * @return X position in pixels
     */
    public static double calculateNoteX(
        int noteIndex,
        @NotNull Note note,
        @Nullable Note previousNote,
        float distanceRatio
    ) {
        if (noteIndex == 0 || previousNote == null) {
            return LayoutStylesheet.px(LayoutStylesheet.FIRST_NOTE_X) + note.getXOffset();
        }

        // Base spacing from previous note depends on previous note's duration
        double baseSpacing = getBaseSpacingForNoteType(previousNote.getNoteType());

        // Apply user adjustment ratio
        double adjustedSpacing = baseSpacing * distanceRatio;

        // Get previous note's X position
        double previousX = previousNote.getX();

        // Apply note's manual X offset
        double manualOffset = note.getXOffset();

        return previousX + adjustedSpacing + manualOffset;
    }

    /**
     * Returns the base horizontal spacing after a note of the given type.
     * <p>
     * Longer notes have more space after them; shorter notes have less.
     *
     * @param noteType The type of note
     * @return Spacing in pixels
     */
    public static double getBaseSpacingForNoteType(@NotNull NoteType noteType) {
        return switch (noteType) {
            case SEMIBREVE, SEMIBREVE_REST ->
                LayoutStylesheet.px(LayoutStylesheet.SEMIBREVE_MARGIN_RIGHT);

            case MINIM, MINIM_REST ->
                LayoutStylesheet.px(LayoutStylesheet.MINIM_MARGIN_RIGHT);

            case CROTCHET, CROTCHET_REST ->
                LayoutStylesheet.px(LayoutStylesheet.CROTCHET_MARGIN_RIGHT);

            case QUAVER, QUAVER_REST, GRACE_QUAVER ->
                LayoutStylesheet.px(LayoutStylesheet.QUAVER_MARGIN_RIGHT);

            case SEMIQUAVER, SEMIQUAVER_REST, GRACE_SEMIQUAVER, DEMI_SEMIQUAVER, DEMI_SEMIQUAVER_REST ->
                LayoutStylesheet.px(LayoutStylesheet.SEMIQUAVER_MARGIN_RIGHT);

            case SINGLE_BARLINE, DOUBLE_BARLINE, FINAL_DOUBLE_BARLINE,
                 REPEAT_LEFT, REPEAT_RIGHT, REPEAT_LEFT_RIGHT ->
                LayoutStylesheet.px(LayoutStylesheet.BARLINE_MARGIN_RIGHT);

            case BREATH_MARK ->
                LayoutStylesheet.px(LayoutStylesheet.BREATH_MARK_MARGIN_RIGHT);

            default ->
                LayoutStylesheet.px(LayoutStylesheet.CROTCHET_MARGIN_RIGHT);
        };
    }

    /**
     * Positions all notes in a line horizontally.
     * <p>
     * Updates each note's position based on duration-based spacing.
     *
     * @param line The line containing notes to position
     */
    public static void positionLineNotes(@NotNull Line line) {
        var notes = line.getNotes();

        if (notes.isEmpty()) {
            return;
        }

        Note previousNote = null;
        float distanceRatio = line.getNoteDistChangeRatio();

        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            double x = calculateNoteX(i, note, previousNote, distanceRatio);

            // Update the note's position (preserve Y)
            note.setPosition(x, note.getY());
            previousNote = note;
        }
    }

    /**
     * Calculates the total width needed for a line's notes.
     * <p>
     * Returns the X position of the last note plus its width and margin.
     *
     * @param line The line to measure
     * @return Total width in pixels, or FIRST_NOTE_X if line is empty
     */
    public static double calculateLineWidth(@NotNull Line line) {
        var notes = line.getNotes();

        if (notes.isEmpty()) {
            return LayoutStylesheet.px(LayoutStylesheet.FIRST_NOTE_X);
        }

        // Position notes first to ensure X values are calculated
        positionLineNotes(line);

        var lastNote = notes.get(notes.size() - 1);
        var lastNoteBounds = lastNote.getMarginBounds();

        // Return the right edge of the last note's margin bounds
        return lastNoteBounds.getMaxX() + LayoutStylesheet.px(1.0);
    }
}
