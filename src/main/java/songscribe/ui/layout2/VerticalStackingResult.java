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

package songscribe.ui.layout2;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Note;
import songscribe.ui.layout.LineElement;

/**
 * Results of vertical stacking calculation for a line.
 * <p>
 * Contains:
 * <ul>
 *   <li>Calculated Y positions for all elements above the staff</li>
 *   <li>Maximum height reached above the staff (for line height calculation)</li>
 *   <li>Lyrics baseline position below the staff</li>
 *   <li>Total line height (staff + elements above + lyrics below + inter-line margin)</li>
 * </ul>
 */
public final class VerticalStackingResult {

    /** Map from note to map of element positions (element -> position). */
    private final @NotNull Map<Note, Map<LineElement, Point2D>> elementPositions;

    /** Maximum height reached above the staff top (negative Y value). */
    private final double maxHeightAboveStaff;

    /** Y position of the lyrics baseline (positive Y value below staff). */
    private final double lyricsBaselineY;

    /** Total height of the line including all elements and margins. */
    private final double lineHeight;

    /**
     * Creates a new vertical stacking result.
     *
     * @param elementPositions    Map of element positions for each note
     * @param maxHeightAboveStaff Maximum height above staff (negative Y)
     * @param lyricsBaselineY     Lyrics baseline Y position
     * @param lineHeight          Total line height
     */
    public VerticalStackingResult(
            @NotNull Map<Note, Map<LineElement, Point2D>> elementPositions,
            double maxHeightAboveStaff,
            double lyricsBaselineY,
            double lineHeight) {
        this.elementPositions = elementPositions;
        this.maxHeightAboveStaff = maxHeightAboveStaff;
        this.lyricsBaselineY = lyricsBaselineY;
        this.lineHeight = lineHeight;
    }

    /**
     * Returns the calculated positions for all elements.
     * <p>
     * Map structure: Note → (LineElement → Point2D position)
     *
     * @return Unmodifiable map of element positions
     */
    public @NotNull Map<Note, Map<LineElement, Point2D>> getElementPositions() {
        return Collections.unmodifiableMap(elementPositions);
    }

    /**
     * Returns the maximum height reached above the staff.
     * This is typically a negative Y value (higher elements = more negative).
     *
     * @return Maximum height above staff in pixels
     */
    public double getMaxHeightAboveStaff() {
        return maxHeightAboveStaff;
    }

    /**
     * Returns the Y position of the lyrics baseline.
     * This is the position where lyric text should be drawn.
     *
     * @return Lyrics baseline Y position in pixels
     */
    public double getLyricsBaselineY() {
        return lyricsBaselineY;
    }

    /**
     * Returns the total height of the line.
     * This includes the staff, all elements above, lyrics below, and margins.
     *
     * @return Total line height in pixels
     */
    public double getLineHeight() {
        return lineHeight;
    }

    /**
     * Returns the position of a specific element for a specific note.
     *
     * @param note    The note
     * @param element The element
     * @return The position, or null if not found
     */
    public Point2D getElementPosition(@NotNull Note note, @NotNull LineElement element) {
        var noteElements = elementPositions.get(note);

        if (noteElements == null) {
            return null;
        }

        return noteElements.get(element);
    }
}
