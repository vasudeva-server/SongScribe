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

import java.util.Objects;

/**
 * Represents the position of an edit note on the score.
 * <p>
 * NotePosition tracks where the edit note is located in terms of:
 * - lineIndex: which staff line
 * - xIndex: position within the line's note sequence
 * - y: vertical position on the staff
 * - movement: horizontal offset from the indexed note position
 */
public final class NotePosition {

    private int xIndex = 0;
    private int y = 0;
    private int lineIndex = 0;
    private int movement = 0;

    // -------------------------------------------------------------------------
    // State accessors
    // -------------------------------------------------------------------------

    public int getXIndex() {
        return xIndex;
    }

    public void setXIndex(int xIndex) {
        this.xIndex = xIndex;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    public int getMovement() {
        return movement;
    }

    public void setMovement(int movement) {
        this.movement = movement;
    }

    // -------------------------------------------------------------------------
    // Utility methods
    // -------------------------------------------------------------------------

    /**
     * Copies the values from another NotePosition into this one.
     *
     * @param other The source NotePosition to copy from
     */
    public void copyFrom(NotePosition other) {
        this.xIndex = other.xIndex;
        this.y = other.y;
        this.lineIndex = other.lineIndex;
        this.movement = other.movement;
    }

    @SuppressWarnings("NonFinalFieldReferenceInEquals")
    @Override
    public synchronized boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        return (
            (obj instanceof NotePosition position) &&
            (xIndex == position.xIndex) &&
            (y == position.y) &&
            (lineIndex == position.lineIndex) &&
            (movement == position.movement)
        );
    }

    @SuppressWarnings("NonFinalFieldReferencedInHashCode")
    @Override
    public int hashCode() {
        return Objects.hash(xIndex, y, lineIndex, movement);
    }
}
