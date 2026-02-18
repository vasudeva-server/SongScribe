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
 * Abstract base class for elements that attach to notes.
 * <p>
 * Attachments include tempo markings, fermatas, annotations, dynamics, and beat changes.
 * Each attachment has:
 * <ul>
 *   <li>A parent note it attaches to</li>
 *   <li>Horizontal alignment relative to the note (LEFT, CENTER, RIGHT)</li>
 *   <li>Vertical placement (ABOVE or BELOW the staff)</li>
 * </ul>
 * <p>
 * Concrete subclasses will be created in Phase 3.
 */
public abstract class Attachment extends LineElement {

    /**
     * Horizontal alignment of the attachment relative to the note.
     */
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    /**
     * Vertical placement of the attachment relative to the staff.
     */
    public enum Placement {
        ABOVE,
        BELOW
    }

    /** The note this attachment belongs to. */
    private @Nullable Note parentNote;

    /** Horizontal alignment relative to the note. */
    private @NotNull Alignment alignment = Alignment.CENTER;

    /** Vertical placement relative to the staff. */
    private @NotNull Placement placement = Placement.ABOVE;

    /**
     * Returns the note this attachment belongs to.
     */
    public @Nullable Note getParentNote() {
        return parentNote;
    }

    /**
     * Sets the note this attachment belongs to.
     */
    public void setParentNote(@Nullable Note parentNote) {
        this.parentNote = parentNote;
    }

    /**
     * Returns the horizontal alignment relative to the note.
     */
    public @NotNull Alignment getAlignment() {
        return alignment;
    }

    /**
     * Sets the horizontal alignment relative to the note.
     */
    public void setAlignment(@NotNull Alignment alignment) {
        this.alignment = alignment;
    }

    /**
     * Returns the vertical placement relative to the staff.
     */
    public @NotNull Placement getPlacement() {
        return placement;
    }

    /**
     * Sets the vertical placement relative to the staff.
     */
    public void setPlacement(@NotNull Placement placement) {
        this.placement = placement;
    }

    /**
     * Returns whether this attachment is placed above the staff.
     */
    public boolean isAbove() {
        return placement == Placement.ABOVE;
    }

    /**
     * Returns whether this attachment is placed below the staff.
     */
    public boolean isBelow() {
        return placement == Placement.BELOW;
    }
}
