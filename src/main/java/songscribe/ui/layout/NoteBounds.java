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

import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the hierarchical bounding boxes for a note.
 * <p>
 * Notes have multiple reference levels that different attachments use:
 * <ul>
 *   <li><b>noteHeadBounds</b>: Just the note head glyph (for ledger lines)</li>
 *   <li><b>noteWithStemBounds</b>: Note head + stem extent (for beams)</li>
 *   <li><b>noteWithArticulationsBounds</b>: Full note including articulations (for ties)</li>
 * </ul>
 * <p>
 * The stem direction affects where articulations are placed and which side
 * ties default to.
 */
public final class NoteBounds {

    private final @NotNull Rectangle2D noteHeadBounds;
    private final @NotNull Rectangle2D noteWithStemBounds;
    private final @NotNull Rectangle2D noteWithArticulationsBounds;
    private final boolean stemUp;

    /**
     * Creates note bounds with all hierarchy levels.
     *
     * @param noteHeadBounds              Just the note head glyph
     * @param noteWithStemBounds          Note head + stem
     * @param noteWithArticulationsBounds Full note including articulations
     * @param stemUp                      True if stem points up
     */
    public NoteBounds(
        @NotNull Rectangle2D noteHeadBounds,
        @NotNull Rectangle2D noteWithStemBounds,
        @NotNull Rectangle2D noteWithArticulationsBounds,
        boolean stemUp
    ) {
        this.noteHeadBounds = noteHeadBounds;
        this.noteWithStemBounds = noteWithStemBounds;
        this.noteWithArticulationsBounds = noteWithArticulationsBounds;
        this.stemUp = stemUp;
    }

    /**
     * Creates note bounds for a note without stem or articulations (e.g., whole note).
     */
    public static NoteBounds headOnly(@NotNull Rectangle2D noteHeadBounds, boolean stemUp) {
        return new NoteBounds(noteHeadBounds, noteHeadBounds, noteHeadBounds, stemUp);
    }

    /**
     * Creates note bounds for a note with stem but no articulations.
     */
    public static NoteBounds withStem(
        @NotNull Rectangle2D noteHeadBounds,
        @NotNull Rectangle2D noteWithStemBounds,
        boolean stemUp
    ) {
        return new NoteBounds(noteHeadBounds, noteWithStemBounds, noteWithStemBounds, stemUp);
    }

    /**
     * Returns bounds for just the note head.
     */
    public @NotNull Rectangle2D getNoteHeadBounds() {
        return noteHeadBounds;
    }

    /**
     * Returns bounds for note head + stem.
     */
    public @NotNull Rectangle2D getNoteWithStemBounds() {
        return noteWithStemBounds;
    }

    /**
     * Returns bounds for full note including articulations.
     */
    public @NotNull Rectangle2D getNoteWithArticulationsBounds() {
        return noteWithArticulationsBounds;
    }

    /**
     * Returns whether the stem points up.
     */
    public boolean isStemUp() {
        return stemUp;
    }

    /**
     * Returns the bounds on the stem side of the note.
     * <p>
     * For stem up: returns upper half of note bounds.
     * For stem down: returns lower half of note bounds.
     */
    public @NotNull Rectangle2D getStemSideBounds() {
        if (stemUp) {
            return new Rectangle2D.Double(
                noteWithArticulationsBounds.getX(),
                noteWithArticulationsBounds.getY(),
                noteWithArticulationsBounds.getWidth(),
                noteHeadBounds.getCenterY() - noteWithArticulationsBounds.getY()
            );
        } else {
            var headCenterY = noteHeadBounds.getCenterY();
            return new Rectangle2D.Double(
                noteWithArticulationsBounds.getX(),
                headCenterY,
                noteWithArticulationsBounds.getWidth(),
                noteWithArticulationsBounds.getMaxY() - headCenterY
            );
        }
    }

    /**
     * Returns the bounds on the opposite side from the stem.
     * <p>
     * For stem up: returns lower half (articulation side).
     * For stem down: returns upper half (articulation side).
     */
    public @NotNull Rectangle2D getOppositeFromStemBounds() {
        if (stemUp) {
            var headCenterY = noteHeadBounds.getCenterY();
            return new Rectangle2D.Double(
                noteWithArticulationsBounds.getX(),
                headCenterY,
                noteWithArticulationsBounds.getWidth(),
                noteWithArticulationsBounds.getMaxY() - headCenterY
            );
        } else {
            return new Rectangle2D.Double(
                noteWithArticulationsBounds.getX(),
                noteWithArticulationsBounds.getY(),
                noteWithArticulationsBounds.getWidth(),
                noteHeadBounds.getCenterY() - noteWithArticulationsBounds.getY()
            );
        }
    }

    /**
     * Returns the top Y coordinate of the full note bounds.
     */
    public double getTop() {
        return noteWithArticulationsBounds.getY();
    }

    /**
     * Returns the bottom Y coordinate of the full note bounds.
     */
    public double getBottom() {
        return noteWithArticulationsBounds.getMaxY();
    }

    /**
     * Returns the horizontal center of the note head.
     */
    public double getCenterX() {
        return noteHeadBounds.getCenterX();
    }

    /**
     * Returns the vertical center of the note head.
     */
    public double getCenterY() {
        return noteHeadBounds.getCenterY();
    }

    /**
     * Returns the Y coordinate for attaching elements above the note.
     * This is the top of the full bounds (including stem and articulations).
     */
    public double getAttachmentTopY() {
        return noteWithArticulationsBounds.getY();
    }

    /**
     * Returns the Y coordinate for attaching elements below the note.
     * This is the bottom of the full bounds (including stem and articulations).
     */
    public double getAttachmentBottomY() {
        return noteWithArticulationsBounds.getMaxY();
    }

    /**
     * Returns a new NoteBounds translated by the given offset.
     */
    public NoteBounds translate(double dx, double dy) {
        return new NoteBounds(
            translateRect(noteHeadBounds, dx, dy),
            translateRect(noteWithStemBounds, dx, dy),
            translateRect(noteWithArticulationsBounds, dx, dy),
            stemUp
        );
    }

    private static Rectangle2D translateRect(Rectangle2D rect, double dx, double dy) {
        return new Rectangle2D.Double(
            rect.getX() + dx,
            rect.getY() + dy,
            rect.getWidth(),
            rect.getHeight()
        );
    }

    @Override
    public String toString() {
        return "NoteBounds{" +
            "head=" + rectToString(noteHeadBounds) +
            ", withStem=" + rectToString(noteWithStemBounds) +
            ", withArticulations=" + rectToString(noteWithArticulationsBounds) +
            ", stemUp=" + stemUp +
            "}";
    }

    private static String rectToString(Rectangle2D r) {
        return String.format("[%.1f,%.1f,%.1f,%.1f]",
            r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
