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
 * Represents a diminuendo (gradually getting softer) hairpin marking.
 * <p>
 * Diminuendo hairpins open to the left (> shape) and are typically
 * placed below the staff. The user can adjust the horizontal endpoints
 * and vertical position.
 */
public class Diminuendo extends RangeElement {

    private @Nullable Note endNote;
    private int x1Shift = 0;
    private int x2Shift = 0;
    private int yShift = 0;

    /**
     * Creates a diminuendo spanning from anchor to end note.
     *
     * @param anchorNote The starting note of the diminuendo
     * @param endNote    The ending note of the diminuendo
     */
    public Diminuendo(@NotNull Note anchorNote, @NotNull Note endNote) {
        setAnchorNote(anchorNote);
        this.endNote = endNote;
    }

    @Override
    public @Nullable Note getEndNote() {
        return endNote;
    }

    /**
     * Sets the end note of this diminuendo.
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
        // Dynamics are below the staff
        return false;
    }

    /**
     * Returns the horizontal adjustment for the start point.
     */
    public int getX1Shift() {
        return x1Shift;
    }

    /**
     * Sets the horizontal adjustment for the start point.
     */
    public void setX1Shift(int x1Shift) {
        this.x1Shift = x1Shift;
    }

    /**
     * Returns the horizontal adjustment for the end point.
     */
    public int getX2Shift() {
        return x2Shift;
    }

    /**
     * Sets the horizontal adjustment for the end point.
     */
    public void setX2Shift(int x2Shift) {
        this.x2Shift = x2Shift;
    }

    /**
     * Returns the vertical adjustment.
     */
    public int getYShift() {
        return yShift;
    }

    /**
     * Sets the vertical adjustment.
     */
    public void setYShift(int yShift) {
        this.yShift = yShift;
    }

    @Override
    public double getContentWidth() {
        var anchor = getAnchorNote();

        if (anchor == null || endNote == null) {
            return 0;
        }

        return Math.abs(endNote.getX() - anchor.getX()) + endNote.getContentWidth() + x1Shift + x2Shift;
    }

    @Override
    public double getContentHeight() {
        // Height of the hairpin opening
        return 8.0;
    }
}
