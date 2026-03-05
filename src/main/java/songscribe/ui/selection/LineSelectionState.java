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

import java.util.stream.IntStream;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Pair;

import songscribe.data.Interval;
import songscribe.data.TupletInterval;
import songscribe.data.IntervalSet;
import songscribe.music.Line;
import songscribe.music.Note;

/**
 * Per-line selection state and query methods.
 * <p>
 * Each LineComponent owns a LineSelectionState that tracks which notes (if any)
 * are selected on that line, and whether the line itself is selected for deletion.
 */
public final class LineSelectionState {

    private final Line line;

    private int selectionBegin = -1;
    private int selectionEnd = -1;
    private int selectionAnchor = -1;
    private boolean lineSelected = false;
    private int selectedGlissandoNoteIndex = -1;

    @Nullable
    private TieContext tieContext = null;

    public LineSelectionState(@NotNull Line line) {
        this.line = line;
    }

    // -------------------------------------------------------------------------
    // State accessors
    // -------------------------------------------------------------------------

    public int getSelectionBegin() {
        return selectionBegin;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    public boolean isLineSelected() {
        return lineSelected;
    }

    public void setLineSelected(boolean lineSelected) {
        this.lineSelected = lineSelected;

        if (lineSelected) {
            selectedGlissandoNoteIndex = -1;
        }
    }

    /**
     * Returns whether a glissando is selected on this line.
     */
    public boolean hasGlissandoSelection() {
        return selectedGlissandoNoteIndex != -1;
    }

    /**
     * Returns the note index of the selected glissando, or -1 if none.
     */
    public int getSelectedGlissandoNoteIndex() {
        return selectedGlissandoNoteIndex;
    }

    /**
     * Selects the glissando owned by the note at the given index,
     * clearing any note or line selection.
     */
    public void selectGlissando(int noteIndex) {
        selectedGlissandoNoteIndex = noteIndex;
        selectionBegin = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
        lineSelected = false;
    }

    /**
     * Returns whether the glissando at the given note index is selected.
     */
    public boolean isGlissandoSelected(int noteIndex) {
        return selectedGlissandoNoteIndex == noteIndex;
    }

    @Nullable
    public TieContext getTieContext() {
        return tieContext;
    }

    public void setTieContext(@Nullable TieContext tieContext) {
        this.tieContext = tieContext;
    }

    @NotNull
    public Line getLine() {
        return line;
    }

    // -------------------------------------------------------------------------
    // Selection state methods
    // -------------------------------------------------------------------------

    /**
     * Clears the selection state on this line.
     */
    public void clearSelection() {
        selectionBegin = -1;
        selectionEnd = -1;
        selectionAnchor = -1;
        lineSelected = false;
        selectedGlissandoNoteIndex = -1;
    }

    /**
     * Selects all notes on this line.
     */
    public void selectAll() {
        if (line.noteCount() == 0) {
            return;
        }

        selectionBegin = 0;
        selectionEnd = line.noteCount() - 1;
        selectionAnchor = 0;
        selectedGlissandoNoteIndex = -1;
    }

    /**
     * Returns whether any notes are selected on this line.
     */
    public boolean hasNoteSelection() {
        return selectionBegin != -1;
    }

    /**
     * Returns whether the note at the given index is selected.
     */
    public boolean isNoteSelected(int noteIndex) {
        return (selectionBegin <= noteIndex) && (noteIndex <= selectionEnd);
    }

    /**
     * Returns the number of notes in the current selection.
     */
    public int getSelectionSize() {
        if (selectionBegin == -1) {
            return 0;
        }

        return (selectionEnd - selectionBegin) + 1;
    }

    /**
     * Returns the current selection, or null if nothing is selected.
     */
    @Nullable
    public NoteSelection getSelection() {
        if (lineSelected) {
            return new NoteSelection(line, 0, line.noteCount() - 1);
        }

        if (selectionBegin != -1) {
            return new NoteSelection(line, selectionBegin, selectionEnd);
        }

        return null;
    }

    /**
     * Returns the single selected note if exactly one note is selected,
     * or null otherwise.
     */
    @Nullable
    public Note getSingleSelectedNote() {
        if ((selectionBegin != -1) && (selectionBegin == selectionEnd)) {
            return line.getNote(selectionBegin);
        }

        return null;
    }

    /**
     * Sets the selection from a single click on a note.
     */
    public void setSelectionFromClick(int noteIndex) {
        selectionBegin = noteIndex;
        selectionEnd = noteIndex;
        selectionAnchor = noteIndex;
        selectedGlissandoNoteIndex = -1;
    }

    /**
     * Returns the selection anchor index, or -1 if no anchor is set.
     */
    public int getSelectionAnchor() {
        return selectionAnchor;
    }

    /**
     * Sets the selection anchor independently (used by drag selection).
     */
    public void setSelectionAnchor(int noteIndex) {
        selectionAnchor = noteIndex;
    }

    /**
     * Extends the selection from the anchor to the given note index.
     * The anchor stays unchanged.
     */
    public void extendSelectionTo(int noteIndex) {
        if (selectionAnchor == -1) {
            return;
        }

        selectionBegin = Math.min(selectionAnchor, noteIndex);
        selectionEnd = Math.max(selectionAnchor, noteIndex);
        selectedGlissandoNoteIndex = -1;
    }

    /**
     * Extends the selection to include the given note index (for drag selection).
     * If no selection exists yet, starts the selection at that index.
     */
    public void extendSelection(int noteIndex) {
        if (selectionBegin == -1) {
            selectionBegin = noteIndex;
        }

        selectionEnd = noteIndex;
        selectedGlissandoNoteIndex = -1;
    }

    /**
     * Resets selection begin/end to -1 without touching lineSelected.
     * Used before recalculating selection from drag.
     */
    public void resetNoteSelection() {
        selectionBegin = -1;
        selectionEnd = -1;
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

        return IntStream.rangeClosed(selectionBegin, selectionEnd).allMatch(
            i -> line.getNote(i).getNoteType().isBeamable()
        );
    }

    /**
     * Returns whether the current selection can toggle a tie.
     * Also sets the tieContext field.
     */
    public boolean canToggleTie() {
        if (getSelectionSize() != 2) {
            tieContext = new TieContext(false, null);
            return false;
        }

        var ties = line.getTies();
        Interval firstTieInterval = null;
        Integer firstPitch = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            var note = line.getNote(i);

            if (!note.getNoteType().isRealNote()) {
                tieContext = new TieContext(false, null);
                return false;
            }

            if (firstPitch == null) {
                firstPitch = note.getStaffPosition();
            } else if (note.getStaffPosition() != firstPitch) {
                tieContext = new TieContext(false, null);
                return false;
            }

            if (i == selectionBegin) {
                firstTieInterval = ties.findInterval(i);
            } else {
                //noinspection ObjectEquality
                if (ties.findInterval(i) != firstTieInterval) {
                    tieContext = new TieContext(false, null);
                    return false;
                }
            }
        }

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

        var tuplets = line.getTuplets();
        TupletInterval firstInterval = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            if (!line.getNote(i).getNoteType().isRealNote()) {
                return new Pair<>(false, false);
            }

            var currentInterval = tuplets.findInterval(i);

            if (i == selectionBegin) {
                firstInterval = currentInterval;
            } else if (currentInterval != firstInterval) {
                return new Pair<>(false, false);
            }
        }

        return new Pair<>(true, firstInterval != null);
    }

    /**
     * Returns whether the current selection can toggle trill.
     */
    public boolean canToggleTrill() {
        if (selectionBegin == -1) {
            return false;
        }

        return line
            .getNotes(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(note -> note.getNoteType().isRealNote());
    }

    /**
     * Returns whether the current selection can toggle lyrics under rests.
     */
    public boolean canToggleLyricsUnderRests() {
        var note = getSingleSelectedNote();
        return (note != null) && note.getNoteType().isRest();
    }

    /**
     * Returns whether the stem direction can be flipped.
     */
    public boolean canFlipStemDirection() {
        if (getSelectionSize() == 0) {
            return false;
        }

        return line
            .getNotes(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(note -> !note.getNoteType().isRest());
    }

    /**
     * Returns whether the selection should connect (add) or disconnect (remove) an interval.
     */
    public boolean shouldConnectSelection(@NotNull IntervalSet intervals) {
        var beginInterval = intervals.findInterval(selectionBegin);
        var endInterval = intervals.findInterval(selectionEnd);

        //noinspection ObjectEquality
        return (beginInterval == null) || (beginInterval != endInterval);
    }
}
