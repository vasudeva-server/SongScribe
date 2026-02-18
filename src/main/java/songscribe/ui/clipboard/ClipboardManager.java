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

package songscribe.ui.clipboard;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import songscribe.data.IntervalSet;
import songscribe.music.Note;

/**
 * Manages clipboard state for copy/paste operations.
 * <p>
 * ClipboardManager stores copied notes and their associated interval sets
 * (ties, beaming) for paste operations.
 */
public final class ClipboardManager {

    // Copied notes waiting to be pasted
    private final ArrayList<Note> pasteboard = new ArrayList<>();

    // Associated interval sets (ties, etc.) for the copied notes
    private IntervalSet[] intervalSetsCopyBuffer = null;

    // -------------------------------------------------------------------------
    // Pasteboard accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the number of notes in the pasteboard.
     */
    public int getSize() {
        return pasteboard.size();
    }

    /**
     * Returns whether the pasteboard is empty.
     */
    public boolean isEmpty() {
        return pasteboard.isEmpty();
    }

    /**
     * Returns the note at the specified index.
     *
     * @param index The index of the note to retrieve
     * @return The note at the specified index
     */
    public Note getNote(int index) {
        return pasteboard.get(index);
    }

    /**
     * Returns the first note in the pasteboard.
     *
     * @return The first note
     */
    public Note getFirstNote() {
        return pasteboard.getFirst();
    }

    /**
     * Returns the last note in the pasteboard.
     *
     * @return The last note
     */
    public Note getLastNote() {
        return pasteboard.getLast();
    }

    // -------------------------------------------------------------------------
    // Interval buffer accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the interval sets copy buffer.
     *
     * @return The interval sets array, or null if none
     */
    @Nullable
    public IntervalSet[] getIntervalsCopyBuffer() {
        return intervalSetsCopyBuffer;
    }

    // -------------------------------------------------------------------------
    // Pasteboard mutators
    // -------------------------------------------------------------------------

    /**
     * Clears the pasteboard and prepares for a new copy operation.
     */
    public void clear() {
        pasteboard.clear();
        intervalSetsCopyBuffer = null;
    }

    /**
     * Adds a note to the pasteboard.
     *
     * @param note The note to add
     */
    public void addNote(Note note) {
        pasteboard.add(note);
    }

    /**
     * Sets the interval sets copy buffer.
     *
     * @param intervals The interval sets array
     */
    public void setIntervalsCopyBuffer(IntervalSet[] intervals) {
        this.intervalSetsCopyBuffer = intervals;
    }
}
