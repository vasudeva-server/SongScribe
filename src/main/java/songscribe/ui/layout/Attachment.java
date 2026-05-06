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

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;

/**
 * Abstract base class for elements that attach to staff elements.
 * <p>
 * Attachments include tempo markings, fermatas, annotations, dynamics, and beat changes.
 * Each attachment has:
 * <ul>
 *   <li>An owner element it attaches to</li>
 *   <li>Horizontal alignment relative to the element (LEFT, CENTER, RIGHT)</li>
 * </ul>
 * <p>
 * Vertical placement is determined by the layout calculator, not stored as a property.
 */
public abstract class Attachment extends LineElement {

    /**
     * Horizontal alignment of the attachment relative to the owner element.
     */
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    /** The staff element this attachment belongs to. */
    private @Nullable StaffElement ownerElement;

    /** Horizontal alignment relative to the owner element. */
    private Alignment alignment = Alignment.CENTER;

    /**
     * Returns the staff element this attachment belongs to.
     */
    public @Nullable StaffElement getOwnerElement() {
        return ownerElement;
    }

    /**
     * Sets the staff element this attachment belongs to.
     */
    public void setOwnerElement(@Nullable StaffElement ownerElement) {
        this.ownerElement = ownerElement;
    }

    /**
     * Returns the horizontal alignment relative to the note.
     */
    public Alignment getAlignment() {
        return alignment;
    }

    /**
     * Sets the horizontal alignment relative to the note.
     */
    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
    }

    /**
     * Returns a deep copy of this attachment, re-owned by {@code newOwner}.
     */
    public abstract Attachment copy(StaffElement newOwner);
}
