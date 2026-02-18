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

package songscribe.ui.selection;

import java.awt.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Pair;
import songscribe.data.Interval;
import songscribe.data.IntervalSet;
import songscribe.music.Composition;
import songscribe.music.Note;

/**
 * Manages selection state for the Score.
 * <p>
 * SelectionManager holds all selection-related state and provides query methods
 * to determine what actions can be performed on the current selection.
 */
public final class SelectionManager {

    // Supplier for getting the current composition
    private final Supplier<Composition> compositionSupplier;

    // The index of the selected staff line. When notes are selected, this will be -1
    // and selectedNotesLine will be the index of the staff line where the notes are selected.
    private int selectedLine = -1;

    // The index of the staff line on which notes are selected. When a staff line is selected,
    // selectedNotesLine will be -1 and selectedLine will be the index of the staff line.
    private int selectedNotesLine = -1;

    // The index (within the line) of the first and last (inclusive) selected note.
    // If a single note is selected, selectionBegin and selectionEnd will be the same.
    private int selectionBegin = 0;
    private int selectionEnd = 0;

    // True if the user is in select mode (shift held down)
    private boolean inSelectMode = false;

    // True if the user is dragging the mouse to select notes
    private boolean startedDrag = false;

    // The rectangle defined by the point where the drag started and the current mouse position
    private final Rectangle dragRectangle = new Rectangle();

    // The point where the drag started
    private final Point dragStart = new Point();

    // When a selection is made, we determine whether a tie can be added to or removed from
    // the selection. The result of that calculation is stored in this context.
    @Nullable
    private TieContext tieContext = null;

    /**
     * Creates a SelectionManager with the given composition supplier.
     *
     * @param compositionSupplier Supplier for getting the current composition
     */
    public SelectionManager(@NotNull Supplier<Composition> compositionSupplier) {
        this.compositionSupplier = compositionSupplier;
    }

    // -------------------------------------------------------------------------
    // State accessors
    // -------------------------------------------------------------------------

    public int getSelectedLine() {
        return selectedLine;
    }

    public void setSelectedLine(int selectedLine) {
        this.selectedLine = selectedLine;
    }

    public int getSelectedNotesLine() {
        return selectedNotesLine;
    }

    public void setSelectedNotesLine(int selectedNotesLine) {
        this.selectedNotesLine = selectedNotesLine;
    }

    public int getSelectionBegin() {
        return selectionBegin;
    }

    public void setSelectionBegin(int selectionBegin) {
        this.selectionBegin = selectionBegin;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    public void setSelectionEnd(int selectionEnd) {
        this.selectionEnd = selectionEnd;
    }

    public boolean isInSelectMode() {
        return inSelectMode;
    }

    public void setInSelectMode(boolean inSelectMode) {
        this.inSelectMode = inSelectMode;
    }

    public boolean isStartedDrag() {
        return startedDrag;
    }

    public void setStartedDrag(boolean startedDrag) {
        this.startedDrag = startedDrag;
    }

    public Rectangle getDragRectangle() {
        return dragRectangle;
    }

    public Point getDragStart() {
        return dragStart;
    }

    @Nullable
    public TieContext getTieContext() {
        return tieContext;
    }

    public void setTieContext(@Nullable TieContext tieContext) {
        this.tieContext = tieContext;
    }

    // -------------------------------------------------------------------------
    // Selection state methods
    // -------------------------------------------------------------------------

    /**
     * Clears the selection state. Does not post messages - the caller is responsible
     * for notifying listeners.
     */
    public void clearSelection() {
        selectedLine = -1;
        selectedNotesLine = -1;
    }

    /**
     * Returns whether the note at the given index is selected.
     *
     * @param noteIndex The note index within the line
     * @param lineIndex The staff line index
     * @return true if the note is selected
     */
    public boolean isNoteSelected(int noteIndex, int lineIndex) {
        return (
            (selectedNotesLine == lineIndex) &&
            (selectionBegin <= noteIndex) &&
            (noteIndex <= selectionEnd)
        );
    }

    /**
     * Returns the number of notes in the current selection.
     */
    public int getSelectionSize() {
        if ((selectedNotesLine == -1) || (selectionBegin == -1)) {
            return 0;
        }

        // If the start and end index are the same, that is a single note
        return (selectionEnd - selectionBegin) + 1;
    }

    /**
     * Returns the current selection, or null if nothing is selected.
     */
    @Nullable
    public NoteSelection getSelection() {
        var composition = compositionSupplier.get();

        if (selectedLine != -1) {
            var line = composition.getLine(selectedLine);
            return new NoteSelection(line, 0, line.noteCount() - 1);
        }

        if (selectedNotesLine != -1) {
            return new NoteSelection(
                composition.getLine(selectedNotesLine),
                selectionBegin,
                selectionEnd
            );
        }

        return null;
    }

    /**
     * Returns the single selected note if exactly one note is selected,
     * or null otherwise.
     */
    @Nullable
    public Note getSingleSelectedNote() {
        if ((selectedNotesLine != -1) && (selectionBegin == selectionEnd)) {
            return compositionSupplier.get()
                .getLine(selectedNotesLine)
                .getNote(selectionBegin);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Query methods for UI state
    // -------------------------------------------------------------------------

    /**
     * Returns whether the current selection can be beamed/unbeamed.
     */
    public boolean canToggleBeaming() {
        if (getSelectionSize() < 2) {
            return false;
        }

        var line = compositionSupplier.get().getLine(selectedNotesLine);

        // The selection is beamable only if all notes are beamable
        return IntStream.rangeClosed(selectionBegin, selectionEnd).allMatch(
            i -> line.getNote(i).getNoteType().isBeamable()
        );
    }

    /**
     * Returns whether the current selection can toggle a tie.
     * This method also sets the tieContext field.
     */
    public boolean canToggleTie() {
        // Ties can only be toggled if:
        //   - All of the notes in the selection are real notes (no grace notes or rests)
        //   - The selection >= 2 notes
        //   - All notes have the same pitch (for ties)
        //
        // Ties can only be added if:
        //   - None of the notes are currently in a tie
        //
        // Ties can only be removed if:
        //   - The selection is part of a single tie

        if (getSelectionSize() < 2) {
            tieContext = new TieContext(false, null);
            return false;
        }

        var line = compositionSupplier.get().getLine(selectedNotesLine);
        var ties = line.getTies();
        Interval firstTieInterval = null;

        // Check that all notes are real notes and have the same pitch (for ties)
        Integer firstPitch = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            var note = line.getNote(i);

            if (!note.getNoteType().isRealNote()) {
                tieContext = new TieContext(false, null);
                return false;
            }

            // For ties, all notes must have the same pitch
            if (firstPitch == null) {
                firstPitch = note.getYPos();
            } else if (note.getYPos() != firstPitch) {
                tieContext = new TieContext(false, null);
                return false;
            }

            if (i == selectionBegin) {
                firstTieInterval = ties.findInterval(i);
            } else {
                // If the current note is part of a different tie than the first note,
                // the selection cannot be toggled.
                //noinspection ObjectEquality
                if (ties.findInterval(i) != firstTieInterval) {
                    tieContext = new TieContext(false, null);
                    return false;
                }
            }
        }

        // If we get to here, a tie can either be added or removed
        tieContext = new TieContext(true, firstTieInterval != null ? ties : null);
        return true;
    }

    /**
     * Returns a Pair where:
     *   - The first Boolean indicates whether the selection can be tupleted/untupleted.
     *   - The second Boolean indicates whether the selection is currently tupleted.
     */
    @NotNull
    @Contract(" -> new")
    @SuppressWarnings("ObjectEquality")
    public Pair<Boolean, Boolean> canToggleTuplet() {
        if (getSelectionSize() < 2) {
            return new Pair<>(false, false);
        }

        // Tuplets can only be toggled if all notes are real notes (no grace notes or rests)
        // and are either in the same tuplet or are not in any tuplet.
        var line = compositionSupplier.get().getLine(selectedNotesLine);
        var tuplets = line.getTuplets();
        Interval firstInterval = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            if (!line.getNote(i).getNoteType().isRealNote()) {
                return new Pair<>(false, false);
            }

            var currentInterval = tuplets.findInterval(i);

            // If this is the first note, remember the current interval. This is what
            // we will compare against.
            // Otherwise, if the current interval does not match the previous one,
            // the selection cannot be tupleted/untupleted.
            if (i == selectionBegin) {
                firstInterval = currentInterval;
            } else if (currentInterval != firstInterval) {
                return new Pair<>(false, false);
            }
        }

        return new Pair<>(true, firstInterval != null);
    }

    /**
     * Returns whether tempo can be changed (first note of first line is selected).
     */
    public boolean canChangeTempo() {
        var selectedNote = getSingleSelectedNote();

        if (selectedNote == null) {
            return false;
        }

        var composition = compositionSupplier.get();
        return composition.getLine(0).getNote(0) != selectedNote;
    }

    /**
     * Returns whether the current selection can toggle trill.
     */
    public boolean canToggleTrill() {
        if (selectedNotesLine == -1) {
            return false;
        }

        // A trill can only be applied if one or more real notes (no grace notes or rests)
        // are in the selection.
        var line = compositionSupplier.get().getLine(selectedNotesLine);
        return line
            .getNotes(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(note -> note.getNoteType().isRealNote());
    }

    /**
     * Returns whether the current selection can toggle lyrics under rests.
     */
    public boolean canToggleLyricsUnderRests() {
        // Only enable if there is exactly one rest selected
        var note = getSingleSelectedNote();
        return (note != null) && note.getNoteType().isRest();
    }

    /**
     * Returns whether the partial beam orientation can be flipped.
     */
    public boolean canFlipPartialBeamOrientation() {
        if (getSelectionSize() != 1) {
            return false;
        }

        var line = compositionSupplier.get().getLine(selectedNotesLine);
        return line.getBeamings().isInsideAnyInterval(selectionBegin);
    }

    /**
     * Returns whether the stem direction can be flipped.
     */
    public boolean canFlipStemDirection() {
        if (getSelectionSize() == 0) {
            return false;
        }

        // There has to be at least one non-rest note in the selection
        var line = compositionSupplier.get().getLine(selectedNotesLine);

        return line
            .getNotes(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(note -> !note.getNoteType().isRest());
    }

    /**
     * Returns whether a line can be deleted (a line is selected and there's more than one line).
     */
    public boolean canDeleteLine() {
        return (selectedLine != -1) && (compositionSupplier.get().lineCount() > 1);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Returns whether the selection should connect (add) or disconnect (remove) an interval.
     */
    public boolean shouldConnectSelection(@NotNull IntervalSet intervals) {
        // Figure out if the start and end are part of the same connection.
        // If so, we will remove the connection, otherwise we will add it.
        var beginInterval = intervals.findInterval(selectionBegin);
        var endInterval = intervals.findInterval(selectionEnd);

        //noinspection ObjectEquality
        return (beginInterval == null) || (beginInterval != endInterval);
    }
}
