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

package songscribe.layout;

/**
 * Defines reference points for positioning elements relative to notes or staff.
 * <p>
 * Each element type positions itself relative to one of these reference points.
 * The layout engine calculates actual Y coordinates based on the reference type.
 * <p>
 * Reference types:
 * <ul>
 *   <li>{@link #STAFF_TOP}: Top of top staff line</li>
 *   <li>{@link #STAFF_BOTTOM}: Bottom of bottom staff line</li>
 *   <li>{@link #NOTE_HEAD}: Just the note head glyph</li>
 *   <li>{@link #NOTE_WITH_STEM}: Note head + stem extent</li>
 *   <li>{@link #NOTE_WITH_ARTICULATIONS}: Full note including articulations</li>
 *   <li>{@link #DYNAMIC_ABOVE}: max(STAFF_TOP, NOTE_WITH_ARTICULATIONS.top)</li>
 *   <li>{@link #DYNAMIC_BELOW}: min(STAFF_BOTTOM, NOTE_WITH_ARTICULATIONS.bottom)</li>
 *   <li>{@link #RANGE_MAX_ABOVE}: max(NOTE_WITH_ARTICULATIONS.top) across range</li>
 *   <li>{@link #RANGE_MIN_BELOW}: min(NOTE_WITH_ARTICULATIONS.bottom) across range</li>
 *   <li>{@link #ADJACENT_ELEMENT}: Previous/next element in stack</li>
 * </ul>
 */
public enum MarginReference {

    /**
     * Top of the top staff line.
     * Used by elements that position relative to staff regardless of note content.
     */
    STAFF_TOP,

    /**
     * Bottom of the bottom staff line.
     * Used by elements that position relative to staff regardless of note content.
     */
    STAFF_BOTTOM,

    /**
     * Just the note head glyph.
     * Base reference for articulations and accidentals.
     */
    NOTE_HEAD,

    /**
     * Note head + stem extent.
     * Used for beams and flags.
     */
    NOTE_WITH_STEM,

    /**
     * Full note extent including stacked articulations.
     * Used as attachment point for ties that must clear articulations.
     */
    NOTE_WITH_ARTICULATIONS,

    /**
     * Dynamic reference above: max(STAFF_TOP, NOTE_WITH_ARTICULATIONS.top).
     * Used by tempo, fermata, trill, and above-note annotations.
     * Ensures element clears both staff and any high notes.
     */
    DYNAMIC_ABOVE,

    /**
     * Dynamic reference below: min(STAFF_BOTTOM, NOTE_WITH_ARTICULATIONS.bottom).
     * Used by dynamics (p, f, etc.) and below-staff elements.
     * Ensures element clears both staff and any low notes.
     */
    DYNAMIC_BELOW,

    /**
     * Maximum extent above across all notes in a range.
     * Used by first/second endings and tuplet brackets.
     * Must clear the tallest note in the range.
     */
    RANGE_MAX_ABOVE,

    /**
     * Minimum extent below across all notes in a range.
     * Used by tuplet brackets when placed below.
     * Must clear the lowest note in the range.
     */
    RANGE_MIN_BELOW,

    /**
     * Reference to an adjacent element in a vertical stack.
     * Used when multiple elements stack (e.g., tempo above beat change).
     */
    ADJACENT_ELEMENT
}
