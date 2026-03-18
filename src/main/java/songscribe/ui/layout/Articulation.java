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

import songscribe.music.ArticulationType;
import songscribe.music.StaffElement;

/**
 * Represents an articulation marking on a staff element.
 * <p>
 * Articulations modify how an element is played (staccato, accent, etc.).
 * They are drawn outward from the note head:
 * <ul>
 *   <li>For downward stems: articulations go below the head</li>
 *   <li>For upward stems: articulations go above the head</li>
 * </ul>
 */
public class Articulation extends LineElement {

    /** Default size for articulation symbols in pixels. */
    private static final double DEFAULT_SIZE = 8.0;

    /** The staff element this articulation belongs to. */
    private @Nullable StaffElement ownerElement;

    /** The type of articulation (STACCATO, ACCENT, etc.). */
    private ArticulationType type;

    /**
     * Creates a new articulation of the specified type.
     *
     * @param type The articulation type
     */
    public Articulation(ArticulationType type) {
        this.type = type;
    }

    /**
     * Creates a new articulation attached to a staff element.
     *
     * @param parent The owner element
     * @param type   The articulation type
     */
    public Articulation(@Nullable StaffElement parent, ArticulationType type) {
        this.ownerElement = parent;
        this.type = type;

        if (parent != null) {
            setParentElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the staff element this articulation belongs to.
     */
    public @Nullable StaffElement getOwnerElement() {
        return ownerElement;
    }

    /**
     * Sets the staff element this articulation belongs to.
     */
    public void setOwnerElement(@Nullable StaffElement ownerElement) {
        this.ownerElement = ownerElement;
    }

    /**
     * Returns the articulation type.
     */
    public ArticulationType getType() {
        return type;
    }

    /**
     * Sets the articulation type.
     */
    public void setType(ArticulationType type) {
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
