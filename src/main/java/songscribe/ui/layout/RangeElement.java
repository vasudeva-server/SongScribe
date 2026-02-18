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

import songscribe.music.Note;

/**
 * Abstract base class for elements that span multiple notes.
 * <p>
 * Range elements include ties, trills, crescendo/diminuendo hairpins,
 * tuplet brackets, and first/second endings.
 * <p>
 * Each range element has:
 * <ul>
 *   <li>An anchor note (first note in the range)</li>
 *   <li>Methods to determine the range extent</li>
 * </ul>
 * <p>
 * Concrete subclasses will be implemented in Phase 4.
 */
public abstract class RangeElement extends LineElement {

    /** The first note in this range. */
    private @Nullable Note anchorNote;

    /**
     * Returns the first note in this range.
     */
    public @Nullable Note getAnchorNote() {
        return anchorNote;
    }

    /**
     * Sets the first note in this range.
     */
    public void setAnchorNote(@Nullable Note anchorNote) {
        this.anchorNote = anchorNote;
    }

    /**
     * Returns the last note in this range.
     * Subclasses must implement based on their specific range definition.
     */
    public abstract @Nullable Note getEndNote();

    /**
     * Returns the number of notes in this range.
     * Returns 0 if the range is not properly defined.
     */
    public abstract int getNoteCount();

    /**
     * Returns whether this range element is above the staff.
     */
    public abstract boolean isAbove();

    /**
     * Returns the index of the anchor note within its line.
     * Returns -1 if the anchor note is not set or not in a line.
     */
    public int getAnchorNoteIndex() {
        if (anchorNote == null || anchorNote.getLine() == null) {
            return -1;
        }

        return anchorNote.getLine().getNoteIndex(anchorNote);
    }

    /**
     * Returns the index of the end note within its line.
     * Returns -1 if the end note is not set or not in a line.
     */
    public int getEndNoteIndex() {
        var endNote = getEndNote();

        if (endNote == null || endNote.getLine() == null) {
            return -1;
        }

        return endNote.getLine().getNoteIndex(endNote);
    }
}
