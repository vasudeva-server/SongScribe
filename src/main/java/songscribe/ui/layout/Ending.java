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
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 */
public class Ending extends RangeElement {

    /**
     * The type of ending (first or second).
     */
    public enum Type {
        FIRST,
        SECOND
    }

    private @Nullable Note endNote;
    private @NotNull Type type = Type.FIRST;
    private int yPositionSs = 0;

    /**
     * Creates an ending bracket.
     *
     * @param anchorNote The first note of the ending
     * @param endNote    The last note of the ending
     * @param type       Whether this is a first or second ending
     */
    public Ending(@NotNull Note anchorNote, @NotNull Note endNote, @NotNull Type type) {
        setAnchorNote(anchorNote);
        this.endNote = endNote;
        this.type = type;
    }

    @Override
    public @Nullable Note getEndNote() {
        return endNote;
    }

    /**
     * Sets the end note of this ending.
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

        int startIndex = getAnchorNoteIndex();
        int endIndex = getEndNoteIndex();

        if (startIndex < 0 || endIndex < 0) {
            return 0;
        }

        return endIndex - startIndex + 1;
    }

    @Override
    public boolean isAbove() {
        // Endings are always above the staff
        return true;
    }

    /**
     * Returns the ending type (first or second).
     */
    public @NotNull Type getType() {
        return type;
    }

    /**
     * Sets the ending type.
     */
    public void setType(@NotNull Type type) {
        this.type = type;
    }

    /**
     * Returns the user-adjustable Y offset for this ending bracket.
     */
    public int getYPositionSs() {
        return yPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this ending bracket.
     */
    public void setYPositionSs(int yPositionSs) {
        this.yPositionSs = yPositionSs;
    }

    /**
     * Returns the label text for this ending ("1." or "2.").
     */
    public @NotNull String getLabel() {
        return type == Type.FIRST ? "1." : "2.";
    }

    @Override
    public double getContentWidth() {
        var anchor = getAnchorNote();

        if (anchor == null || endNote == null) {
            return 0;
        }

        return Math.abs(endNote.getX() - anchor.getX()) + endNote.getContentWidth();
    }

    @Override
    public double getContentHeight() {
        // Height of ending bracket with label
        return 15.0;
    }
}
