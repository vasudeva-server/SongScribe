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

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;

/**
 * Interface for font-specific bounds calculations.
 * <p>
 * This abstracts the font-dependent glyph bounds calculation from the generic
 * bounds calculation logic. Renderers that use specific music fonts implement
 * this interface to provide glyph-level bounds.
 * <p>
 * Used by {@link BoundsCalculator} to compute complete element bounds.
 */
public interface FontBoundsProvider {

    /**
     * Returns the bounding box for the note head and stem using font glyphs.
     * <p>
     * The returned bounds should be in absolute coordinates (page-relative),
     * not relative to the note position.
     *
     * @param g2        Graphics context for font metrics
     * @param note      The note to measure
     * @param lineIndex The staff line index (0-based)
     * @return Rectangle2D in absolute coordinates
     */
    @NotNull
    Rectangle2D getNoteHeadStemBounds(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    );

    /**
     * Returns the width of a crotchet (quarter note) head in pixels.
     * <p>
     * Used for positioning articulations relative to the note head.
     *
     * @return Crotchet head width in pixels
     */
    double getCrotchetWidth();

    /**
     * Returns the half-width of a note for tie positioning.
     * <p>
     * Different note types may have different widths.
     *
     * @param note The note to measure
     * @return Half-width of the note in pixels
     */
    double getHalfNoteWidthForTie(@NotNull Note note);
}
