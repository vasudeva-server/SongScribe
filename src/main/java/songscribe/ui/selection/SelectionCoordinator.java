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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;

/**
 * Lightweight score-level coordinator that tracks which line (if any) has
 * the active selection, and handles cross-line queries.
 * <p>
 * Replaces SelectionManager as the score-level selection object.
 */
public final class SelectionCoordinator {

    private final Supplier<Composition> compositionSupplier;

    /** Registry of per-line selection states, keyed by line index. */
    private final Map<Integer, LineSelectionState> lineStates = new HashMap<>();

    /** Which line currently has the active selection, or -1 if none. */
    private int activeLineIndex = -1;

    /** Whether the user is in select mode (shift held down or select mode active). */
    private boolean inSelectMode = false;

    public SelectionCoordinator(@NotNull Supplier<Composition> compositionSupplier) {
        this.compositionSupplier = compositionSupplier;
    }

    // -------------------------------------------------------------------------
    // Line state registry
    // -------------------------------------------------------------------------

    /**
     * Registers a LineSelectionState for the given line index.
     * Called by LineComponent when it is set up.
     */
    public void registerLineState(int lineIndex, @NotNull LineSelectionState state) {
        lineStates.put(lineIndex, state);
    }

    /**
     * Removes a LineSelectionState registration.
     */
    public void unregisterLineState(int lineIndex) {
        lineStates.remove(lineIndex);
    }

    /**
     * Clears all registered line states.
     */
    public void clearLineStates() {
        lineStates.clear();
        activeLineIndex = -1;
    }

    /**
     * Returns the LineSelectionState for the given line index, or null if not registered.
     */
    @Nullable
    public LineSelectionState getLineState(int lineIndex) {
        return lineStates.get(lineIndex);
    }

    // -------------------------------------------------------------------------
    // Active line management
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the line that currently has the active selection,
     * or -1 if no line has an active selection.
     */
    public int getActiveLineIndex() {
        return activeLineIndex;
    }

    /**
     * Returns the active LineSelectionState, or null if no line is active.
     */
    @Nullable
    public LineSelectionState getActiveSelection() {
        if (activeLineIndex == -1) {
            return null;
        }

        return lineStates.get(activeLineIndex);
    }

    /**
     * Activates the given line for selection. Clears the previous line's selection.
     */
    public void activateLine(int lineIndex) {
        if (activeLineIndex != -1 && activeLineIndex != lineIndex) {
            var previousState = lineStates.get(activeLineIndex);

            if (previousState != null) {
                previousState.clearSelection();
            }
        }

        activeLineIndex = lineIndex;
    }

    /**
     * Clears the active line's selection and resets activeLineIndex to -1.
     */
    public void clearSelection() {
        if (activeLineIndex != -1) {
            var state = lineStates.get(activeLineIndex);

            if (state != null) {
                state.clearSelection();
            }
        }

        activeLineIndex = -1;
    }

    // -------------------------------------------------------------------------
    // Select mode
    // -------------------------------------------------------------------------

    public boolean isInSelectMode() {
        return inSelectMode;
    }

    public void setInSelectMode(boolean inSelectMode) {
        this.inSelectMode = inSelectMode;
    }

    // -------------------------------------------------------------------------
    // Cross-line queries (needed for rendering and Score API)
    // -------------------------------------------------------------------------

    /**
     * Returns whether the note at the given index on the given line is selected.
     * Delegates to the correct LineSelectionState.
     */
    public boolean isNoteSelected(int noteIndex, int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.hasNoteSelection() && state.isNoteSelected(noteIndex);
    }

    /**
     * Returns whether the staff line itself is selected (for deletion).
     */
    public boolean isLineSelected(int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.isLineSelected();
    }

    // -------------------------------------------------------------------------
    // Cross-line query methods
    // -------------------------------------------------------------------------

    /**
     * Returns whether a line can be deleted (a line is selected and there's more than one line).
     */
    public boolean canDeleteLine() {
        if (activeLineIndex == -1) {
            return false;
        }

        var state = lineStates.get(activeLineIndex);

        if (state == null || !state.isLineSelected()) {
            return false;
        }

        return compositionSupplier.get().lineCount() > 1;
    }

    /**
     * Returns whether tempo can be changed.
     */
    public boolean canChangeTempo() {
        var state = getActiveSelection();

        if (state == null) {
            return false;
        }

        var selectedNote = state.getSingleSelectedNote();

        return selectedNote != null;
    }

    // -------------------------------------------------------------------------
    // Convenience accessors that delegate to active LineSelectionState
    // -------------------------------------------------------------------------

    /**
     * Returns the number of notes in the active selection.
     */
    public int getSelectionSize() {
        var state = getActiveSelection();
        return (state != null) ? state.getSelectionSize() : 0;
    }

    /**
     * Returns the current selection, or null if nothing is selected.
     */
    @Nullable
    public NoteSelection getSelection() {
        var state = getActiveSelection();
        return (state != null) ? state.getSelection() : null;
    }

    /**
     * Returns the single selected note if exactly one note is selected,
     * or null otherwise.
     */
    @Nullable
    public songscribe.music.Note getSingleSelectedNote() {
        var state = getActiveSelection();
        return (state != null) ? state.getSingleSelectedNote() : null;
    }

    /**
     * Returns the index of the line that has a line selection (for deletion),
     * or -1 if no line is selected.
     */
    public int getSelectedLine() {
        if (activeLineIndex != -1) {
            var state = lineStates.get(activeLineIndex);

            if (state != null && state.isLineSelected()) {
                return activeLineIndex;
            }
        }

        return -1;
    }
}
