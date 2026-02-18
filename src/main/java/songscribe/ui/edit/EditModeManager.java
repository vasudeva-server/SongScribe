/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.edit;

import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;

/**
 * Manages edit mode state for the Score.
 * <p>
 * EditModeManager holds the state related to edit note preview, including:
 * - The current edit note (the note that will be inserted)
 * - Whether the edit note is currently visible
 * - The position of the edit note on the score
 */
public final class EditModeManager {

    // Whether the edit note is visible (based on mouse position in mouse mode)
    private boolean editNoteIsVisible = false;

    // The current edit note that will be inserted
    @Nullable
    private Note editNote = null;

    // Temporary position calculated during mouse movement
    private final NotePosition newEditNotePoint = new NotePosition();

    // The current position of the edit note
    private final NotePosition editNotePoint = new NotePosition();

    // -------------------------------------------------------------------------
    // Edit note accessors
    // -------------------------------------------------------------------------

    /**
     * Returns whether the edit note is currently visible.
     */
    public boolean isEditNoteVisible() {
        return editNoteIsVisible;
    }

    /**
     * Sets whether the edit note is visible.
     *
     * @param visible true to show the edit note, false to hide it
     */
    public void setEditNoteVisible(boolean visible) {
        this.editNoteIsVisible = visible;
    }

    /**
     * Returns the current edit note, or null if no edit note is set.
     */
    @Nullable
    public Note getEditNote() {
        return editNote;
    }

    /**
     * Sets the current edit note.
     *
     * @param editNote The note to set as the edit note, or null to clear it
     */
    public void setEditNote(@Nullable Note editNote) {
        this.editNote = editNote;
    }

    /**
     * Returns whether an edit note is currently set.
     */
    public boolean hasEditNote() {
        return editNote != null;
    }

    // -------------------------------------------------------------------------
    // Position accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the temporary position used during mouse movement calculations.
     */
    public NotePosition getNewEditNotePoint() {
        return newEditNotePoint;
    }

    /**
     * Returns the current edit note position.
     */
    public NotePosition getEditNotePoint() {
        return editNotePoint;
    }

    /**
     * Copies the new edit note point values to the current edit note point.
     */
    public void applyNewEditNotePoint() {
        editNotePoint.copyFrom(newEditNotePoint);
    }
}
