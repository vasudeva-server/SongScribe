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

package songscribe.music;

/**
 * A per-note lyric syllable.
 *
 * @param verse    1-based verse number (only verse 1 is populated until multi-verse support is added)
 * @param relation boundary type that follows this syllable
 * @param text     syllable text (may be empty when {@code extend} is {@link Extend#STOP} or
 *                 {@link Extend#CONTINUE} — those carriers mark melisma boundaries on notes
 *                 that have no text of their own)
 * @param extend   melisma extender state for this lyric (MusicXML-aligned)
 */
public record Lyric(int verse, StaffElement.SyllableRelation relation, String text, Extend extend) {

    /**
     * Melisma extender state for a {@link Lyric}, aligned with MusicXML {@code <extend>} types.
     * <ul>
     *   <li>{@link #NONE} — no extender on this syllable.</li>
     *   <li>{@link #START} — this syllable has text and begins a melisma; the extender runs from
     *       the end of the syllable onward.</li>
     *   <li>{@link #STOP} — this lyric carries no text; it marks the final note under a melisma
     *       and the extender ends at this note's right edge.</li>
     *   <li>{@link #CONTINUE} — this lyric carries no text; it marks a note that continues a
     *       melisma across a line break (or otherwise carries the extender without ending it).</li>
     * </ul>
     */
    public enum Extend { NONE, START, STOP, CONTINUE }
}
