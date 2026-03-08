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

import java.util.Collections;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.Note;

/**
 * Spacing calculations for note insertion operations using layout2 algorithms.
 * <p>
 * This class provides simplified spacing calculations for interactive editing operations
 * (adding notes, inserting notes) without requiring full layout recalculation. It creates
 * lightweight NoteColumns internally to leverage the standard spacing algorithms from
 * {@link HorizontalSpacingCalculator}.
 * <ul>
 *   <li>Append note to end of line: {@link #calculateAppendPositionSs(Line, Note)}</li>
 *   <li>Insert note in middle: {@link #calculateInsertionShiftSs(Line, Note, int)}</li>
 * </ul>
 */
public class InsertionSpacingCalculator {

    /**
     * Result of an insertion spacing calculation, providing both the X position for the
     * inserted note and the shift amount for subsequent notes.
     *
     * @param insertedNoteXSs X position where the inserted note should be placed
     * @param shiftForSubsequentNotesSs Amount to shift all notes after the insertion point (always >= 0)
     */
    public record InsertionResult(double insertedNoteXSs, double shiftForSubsequentNotesSs) {}

    private InsertionSpacingCalculator() {
        // Prevent instantiation - utility class with static methods only
    }

    /**
     * Calculates the X position for appending a note to the end of a line.
     * <p>
     * This method creates lightweight NoteColumns for the last existing note and the
     * note to append, then uses the standard spacing algorithm to determine where the
     * new note should be placed.
     *
     * @param line         The line to append to
     * @param noteToAppend The note being appended
     * @return X position in pixels where the note should be placed
     */
    public static double calculateAppendPositionSs(@NotNull Line line, @NotNull Note noteToAppend) {
        var noteCount = line.noteCount();

        if (noteCount == 0) {
            // First note on the line - use standard first note positioning
            return ScaleContext.getInstance().toPixels(LayoutConstants.calculateFirstNoteXSs(line.getKeyAccidentalCount()));
        }

        // Get the last note and create a column for it
        var lastNote = line.getNote(noteCount - 1);
        var lastColumn = createLightweightColumn(lastNote);

        // Use the last note's actual X position
        lastColumn.setXSs(lastNote.getXPosSs());

        // Create column for note to append
        var appendColumn = createLightweightColumn(noteToAppend);

        // Calculate where the new note should go
        return HorizontalSpacingCalculator.calculateNextColumnXSs(lastColumn, appendColumn);
    }

    /**
     * Calculates the X position for {@code nextNote} when placed after {@code currentNote},
     * using the current X position of {@code currentNote}.
     * <p>
     * This is useful for paste operations where notes have already been positioned and
     * you need to determine where the next note in sequence should go.
     *
     * @param currentNote A note with its X position already set
     * @param nextNote    The note to be placed after currentNote
     * @return X position where nextNote should be placed
     */
    public static double calculateNextNoteXSs(@NotNull Note currentNote, @NotNull Note nextNote) {
        var currentColumn = createLightweightColumn(currentNote);
        currentColumn.setXSs(currentNote.getXPosSs());
        var nextColumn = createLightweightColumn(nextNote);
        return HorizontalSpacingCalculator.calculateNextColumnXSs(currentColumn, nextColumn);
    }

    /**
     * Calculates both the X position for an inserted note and the shift amount for subsequent notes.
     * <p>
     * This method must be called before the note is added to the line, since it examines the
     * line's current state to determine proper spacing.
     *
     * @param line         The line being modified (before insertion)
     * @param insertedNote The note being inserted
     * @param insertIndex  The index where the note will be inserted (0-based)
     * @return An {@link InsertionResult} with the note's X position and the shift for subsequent notes
     */
    public static @NotNull InsertionResult calculateInsertion(
        @NotNull Line line,
        @NotNull Note insertedNote,
        int insertIndex) {

        var noteCount = line.noteCount();

        // Validate index
        if (insertIndex < 0 || insertIndex > noteCount) {
            throw new IllegalArgumentException(
                "insertIndex " + insertIndex + " out of bounds [0, " + noteCount + "]");
        }

        // If inserting at end, no shift needed (use calculateAppendPosition instead)
        if (insertIndex == noteCount) {
            return new InsertionResult(calculateAppendPositionSs(line, insertedNote), 0);
        }

        // Create column for inserted note
        var insertedColumn = createLightweightColumn(insertedNote);

        double insertedNoteXSs;
        double requiredSpaceSs;

        if (insertIndex == 0) {
            // Inserting at beginning - calculate space from line start
            insertedNoteXSs = ScaleContext.getInstance().toPixels(LayoutConstants.calculateFirstNoteXSs(line.getKeyAccidentalCount()));
            var nextNote = line.getNote(0);
            var nextColumn = createLightweightColumn(nextNote);

            // Space needed: firstNoteX → inserted note → existing first note
            insertedColumn.setXSs(insertedNoteXSs);
            double insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                insertedColumn, nextColumn);

            // Shift = (where first note needs to be) - (where it currently is)
            requiredSpaceSs = insertedToNextSs - nextNote.getXPosSs();
        } else {
            // Inserting in middle - calculate space between prev and next
            var prevNote = line.getNote(insertIndex - 1);
            var nextNote = line.getNote(insertIndex);

            var prevColumn = createLightweightColumn(prevNote);
            var nextColumn = createLightweightColumn(nextNote);

            prevColumn.setXSs(prevNote.getXPosSs());

            // Calculate: prev → inserted → next
            insertedNoteXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                prevColumn, insertedColumn);
            insertedColumn.setXSs(insertedNoteXSs);

            double insertedToNextSs = HorizontalSpacingCalculator.calculateNextColumnXSs(
                insertedColumn, nextColumn);

            // Shift = (where next needs to be) - (where it currently is)
            requiredSpaceSs = insertedToNextSs - nextNote.getXPosSs();
        }

        return new InsertionResult(insertedNoteXSs, Math.max(0, requiredSpaceSs));
    }

    /**
     * Calculates the horizontal shift amount needed when inserting a note at a given index.
     * <p>
     * This determines how much existing notes after the insertion point need to shift right
     * to accommodate the inserted note with proper spacing.
     *
     * @param line         The line being modified
     * @param insertedNote The note being inserted
     * @param insertIndex  The index where the note is being inserted (0-based)
     * @return Shift amount in pixels (positive = shift right)
     */
    public static double calculateInsertionShiftSs(
        @NotNull Line line,
        @NotNull Note insertedNote,
        int insertIndex) {

        return calculateInsertion(line, insertedNote, insertIndex).shiftForSubsequentNotesSs();
    }

    /**
     * Creates a lightweight NoteColumn for spacing calculations.
     * <p>
     * This column has accurate geometric extents but no syllable information, since
     * insertion operations typically happen during editing before lyrics are finalized.
     *
     * @param note The note to create a column for
     * @return A NoteColumn with geometric extents but no syllable data
     */
    private static @NotNull NoteColumn createLightweightColumn(@NotNull Note note) {
        // Calculate geometric extents using NoteColumnBuilder's static methods
        double leftExtentSs = NoteColumnBuilder.calculateLeftExtentSs(note);
        double rightExtentSs = NoteColumnBuilder.calculateRightExtentSs(note, false, note.isUpper());

        // For insertion operations, we don't need stem positions or beam group info
        // since we're only calculating horizontal spacing
        double stemTopSs = 0;
        double stemBottomSs = 0;

        // No syllable information for lightweight columns
        String syllable = null;
        double syllableWidthSs = 0;

        return new NoteColumn(
            note,
            Collections.emptyList(),  // No grace notes
            leftExtentSs,
            rightExtentSs,
            stemTopSs,
            stemBottomSs,
            syllable,
            syllableWidthSs,
            false
        );
    }
}
