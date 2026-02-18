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

package songscribe.ui.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;

/**
 * Represents a trill marking that can span one or more notes.
 * <p>
 * Trills are always displayed above the staff. Even single-note trills
 * are represented as RangeElements where the anchor and end note are the same.
 */
public class Trill extends RangeElement {

    private @Nullable Note endNote;
    private int yPosition = 0;

    /**
     * Creates a trill spanning multiple notes.
     *
     * @param anchorNote The first note of the trill
     * @param endNote    The last note of the trill
     */
    public Trill(@NotNull Note anchorNote, @NotNull Note endNote) {
        setAnchorNote(anchorNote);
        this.endNote = endNote;
    }

    /**
     * Creates a single-note trill.
     *
     * @param note The note with the trill
     */
    public Trill(@NotNull Note note) {
        this(note, note);
    }

    @Override
    public @Nullable Note getEndNote() {
        return endNote;
    }

    /**
     * Sets the end note of this trill.
     */
    public void setEndNote(@Nullable Note endNote) {
        this.endNote = endNote;
    }

    @Override
    public int getNoteCount() {
        var anchor = getAnchorNote();

        if (anchor == null || endNote == null) {
            return 0;
        }

        // Single-note trill
        if (anchor == endNote) {
            return 1;
        }

        int startIndex = getAnchorNoteIndex();
        int endIndex = getEndNoteIndex();

        if (startIndex < 0 || endIndex < 0) {
            return 0;
        }

        return endIndex - startIndex + 1;
    }

    @Override
    public boolean isAbove() {
        // Trills are always above the staff
        return true;
    }

    /**
     * Returns the user-adjustable Y offset for this trill.
     */
    public int getYPosition() {
        return yPosition;
    }

    /**
     * Sets the user-adjustable Y offset for this trill.
     */
    public void setYPosition(int yPosition) {
        this.yPosition = yPosition;
    }

    @Override
    public double getContentWidth() {
        var anchor = getAnchorNote();

        if (anchor == null || endNote == null) {
            return 0;
        }

        if (anchor == endNote) {
            return anchor.getContentWidth();
        }

        return Math.abs(endNote.getX() - anchor.getX()) + endNote.getContentWidth();
    }

    @Override
    public double getContentHeight() {
        // Height of trill symbol ("tr" + wavy line)
        return 12.0;
    }
}
