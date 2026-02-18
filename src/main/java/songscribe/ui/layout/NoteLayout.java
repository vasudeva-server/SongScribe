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

import java.awt.*;

import org.jetbrains.annotations.NotNull;

/**
 * Layout information for a single note.
 * <p>
 * Contains the note's final position (after applying user offset) and its
 * hierarchical bounding boxes for different attachment levels.
 *
 * @deprecated Replaced by new ElementRenderer system (Phase 6). Scheduled for removal in Phase 8.
 */
@Deprecated
public final class NoteLayout {

    private final int noteIndex;
    private final @NotNull Point position;
    private final @NotNull NoteBounds bounds;
    private final @NotNull ElementBounds elementBounds;

    /**
     * Creates note layout with position and bounds.
     *
     * @param noteIndex     Index of the note within its line
     * @param position      Final X, Y position (includes user offset)
     * @param bounds        Hierarchical note bounds (head, stem, articulations)
     * @param elementBounds Full element bounds with padding/margin for hit testing
     */
    public NoteLayout(
        int noteIndex,
        @NotNull Point position,
        @NotNull NoteBounds bounds,
        @NotNull ElementBounds elementBounds
    ) {
        this.noteIndex = noteIndex;
        this.position = position;
        this.bounds = bounds;
        this.elementBounds = elementBounds;
    }

    /**
     * Returns the note's index within its line.
     */
    public int getNoteIndex() {
        return noteIndex;
    }

    /**
     * Returns the final position (X, Y) of the note.
     * This is the position used for rendering, including any user offset.
     */
    public @NotNull Point getPosition() {
        return position;
    }

    /**
     * Returns the X coordinate of the note position.
     */
    public int getX() {
        return position.x;
    }

    /**
     * Returns the Y coordinate of the note position.
     */
    public int getY() {
        return position.y;
    }

    /**
     * Returns the hierarchical note bounds.
     */
    public @NotNull NoteBounds getBounds() {
        return bounds;
    }

    /**
     * Returns the element bounds for hit testing and layout spacing.
     */
    public @NotNull ElementBounds getElementBounds() {
        return elementBounds;
    }

    /**
     * Returns whether the note's stem points up.
     */
    public boolean isStemUp() {
        return bounds.isStemUp();
    }

    /**
     * Returns the center X of the note head.
     */
    public double getCenterX() {
        return bounds.getCenterX();
    }

    /**
     * Returns the center Y of the note head.
     */
    public double getCenterY() {
        return bounds.getCenterY();
    }

    /**
     * Returns the top Y coordinate (including articulations).
     */
    public double getTop() {
        return bounds.getTop();
    }

    /**
     * Returns the bottom Y coordinate (including articulations).
     */
    public double getBottom() {
        return bounds.getBottom();
    }

    /**
     * Returns whether the given point is within hit testing bounds.
     */
    public boolean containsPoint(double x, double y) {
        return elementBounds.containsForHitTest(x, y);
    }

    @Override
    public String toString() {
        return "NoteLayout{" +
            "index=" + noteIndex +
            ", pos=(" + position.x + "," + position.y + ")" +
            ", stemUp=" + bounds.isStemUp() +
            "}";
    }
}
