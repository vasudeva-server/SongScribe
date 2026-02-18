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

import songscribe.music.ArticulationType;
import songscribe.music.Note;

/**
 * Represents an articulation marking on a note.
 * <p>
 * Articulations modify how a note is played (staccato, accent, etc.).
 * They are drawn outward from the note head:
 * <ul>
 *   <li>For downward stems: articulations go below the head</li>
 *   <li>For upward stems: articulations go above the head</li>
 * </ul>
 */
public class Articulation extends LineElement {

    /** Default size for articulation symbols in pixels. */
    private static final double DEFAULT_SIZE = 8.0;

    /** The note this articulation belongs to. */
    private @Nullable Note parentNote;

    /** The type of articulation (STACCATO, ACCENT, etc.). */
    private @NotNull ArticulationType type;

    /**
     * Creates a new articulation of the specified type.
     *
     * @param type The articulation type
     */
    public Articulation(@NotNull ArticulationType type) {
        this.type = type;
    }

    /**
     * Creates a new articulation attached to a note.
     *
     * @param parent The parent note
     * @param type   The articulation type
     */
    public Articulation(@Nullable Note parent, @NotNull ArticulationType type) {
        this.parentNote = parent;
        this.type = type;

        if (parent != null) {
            setParentElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the note this articulation belongs to.
     */
    public @Nullable Note getParentNote() {
        return parentNote;
    }

    /**
     * Sets the note this articulation belongs to.
     */
    public void setParentNote(@Nullable Note parentNote) {
        this.parentNote = parentNote;
    }

    /**
     * Returns the articulation type.
     */
    public @NotNull ArticulationType getType() {
        return type;
    }

    /**
     * Sets the articulation type.
     */
    public void setType(@NotNull ArticulationType type) {
        this.type = type;
    }

    /**
     * Returns whether this is a staccato articulation.
     */
    public boolean isStaccato() {
        return type == ArticulationType.STACCATO;
    }

    /**
     * Returns whether this is an accent articulation.
     */
    public boolean isAccent() {
        return type == ArticulationType.ACCENT;
    }

    @Override
    public double getContentWidth() {
        return DEFAULT_SIZE;
    }

    @Override
    public double getContentHeight() {
        return DEFAULT_SIZE;
    }
}
