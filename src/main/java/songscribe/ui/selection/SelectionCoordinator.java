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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Note;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.message.Message;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;

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

    // Lazy-initialized list of all reflectable actions discovered from Actions.
    private List<UIAction.Reflectable> reflectableActions = null;

    // Saved toggle states of reflectable actions before a selection becomes active.
    private final Map<UIAction, Boolean> savedToggleStates = new IdentityHashMap<>();

    // Last reflected selection range, used to skip redundant reflection.
    private NoteSelection lastReflectedSelection = null;

    public SelectionCoordinator(@NotNull Supplier<Composition> compositionSupplier) {
        this.compositionSupplier = compositionSupplier;
        MessageCenter.subscribe(this);
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

    /**
     * Returns whether the glissando owned by the note at the given index
     * on the given line is selected.
     */
    public boolean isGlissandoSelected(int noteIndex, int lineIndex) {
        if (activeLineIndex != lineIndex) {
            return false;
        }

        var state = lineStates.get(lineIndex);
        return (state != null) && state.isGlissandoSelected(noteIndex);
    }

    /**
     * Returns whether any glissando is selected on the active line.
     */
    public boolean hasGlissandoSelection() {
        var state = getActiveSelection();
        return (state != null) && state.hasGlissandoSelection();
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

    /**
     * Returns the notes in the active selection, or an empty list if nothing is selected.
     */
    public List<Note> getSelectedNotes() {
        var selection = getSelection();

        if (selection == null) {
            return List.of();
        }

        var line = selection.line();
        var notes = new ArrayList<Note>(selection.end() - selection.begin() + 1);

        for (var i = selection.begin(); i <= selection.end(); i++) {
            notes.add(line.getNote(i));
        }

        return notes;
    }

    // -------------------------------------------------------------------------
    // Toolbar reflection
    // -------------------------------------------------------------------------

    /**
     * Lazily discovers all static fields in Actions that implement UIAction.Reflectable,
     * including elements of UIAction array fields.
     */
    private List<UIAction.Reflectable> getReflectableActions() {
        if (reflectableActions == null) {
            reflectableActions = new ArrayList<>();

            for (var field : Actions.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    var value = field.get(null);

                    if (value instanceof UIAction[] array) {
                        for (var action : array) {
                            if (action instanceof UIAction.Reflectable reflectable) {
                                reflectableActions.add(reflectable);
                            }
                        }
                    } else if (value instanceof List<?> list) {
                        for (var item : list) {
                            if (item instanceof UIAction.Reflectable reflectable) {
                                reflectableActions.add(reflectable);
                            }
                        }
                    } else if (value instanceof UIAction.Reflectable reflectable) {
                        reflectableActions.add(reflectable);
                    }
                } catch (IllegalAccessException e) {
                    // Non-public fields are skipped
                }
            }
        }

        return reflectableActions;
    }

    /**
     * Reflects the current selection onto all reflectable toolbar actions.
     * Fires at LOW_PRIORITY so it runs after all UIAction handlers have processed
     * the selection-changed message.
     */
    @Handler(priority = Message.LOW_PRIORITY)
    public void reflectSelection(MusicSelectionChangedMessage message) {
        var actions = getReflectableActions();
        var selection = getSelection();

        // Selection cleared — restore saved state
        if (selection == null) {
            lastReflectedSelection = null;

            if (!savedToggleStates.isEmpty()) {
                for (var entry : savedToggleStates.entrySet()) {
                    entry.getKey().setSelected(entry.getValue());
                }

                savedToggleStates.clear();
            }

            return;
        }

        // Skip if the selection range is unchanged
        if (selection.equals(lastReflectedSelection)) {
            return;
        }

        lastReflectedSelection = selection;

        // Selection just became active — save current toggle states
        if (savedToggleStates.isEmpty()) {
            for (var reflectable : actions) {
                var action = (UIAction) reflectable;
                savedToggleStates.put(action, action.isSelected());
            }
        }

        // Reflect selection attributes onto toolbar actions
        var line = selection.line();

        for (var reflectable : actions) {
            var action = (UIAction) reflectable;
            var applicable = false;
            var matched = true;

            for (var i = selection.begin(); i <= selection.end(); i++) {
                var note = line.getNote(i);

                if (!reflectable.appliesTo(note)) {
                    continue;
                }

                applicable = true;

                if (!reflectable.matchesNote(note)) {
                    matched = false;
                    break;
                }
            }

            action.setSelected(applicable && matched);
        }
    }
}
