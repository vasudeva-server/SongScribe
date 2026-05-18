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

package songscribe.model;

import org.jspecify.annotations.Nullable;

/**
 * A per-note lyric syllable, representing one of the four MusicXML 4.0 {@code <lyric>} forms:
 * <ol>
 *   <li><b>Syllable</b> ({@code extend=}{@link Extend#NONE}) — a text-bearing syllable with no
 *       melisma. {@code syllabic} indicates the syllable's position within its word.</li>
 *   <li><b>Melisma start</b> ({@code extend=}{@link Extend#START}) — a text-bearing syllable that
 *       begins a melisma; the extender line runs from the syllable onward.</li>
 *   <li><b>Melisma continue</b> ({@code extend=}{@link Extend#CONTINUE}) — a carrier note with no
 *       text that sustains a melisma across a line break. {@code syllabic} is {@code null}.</li>
 *   <li><b>Melisma stop</b> ({@code extend=}{@link Extend#STOP}) — a carrier note with no text
 *       that ends a melisma; the extender line terminates at this note's right edge. {@code
 *       syllabic} is {@code null}.</li>
 * </ol>
 *
 * @param verse    1-based verse number (only verse 1 is populated until multi-verse support is added)
 * @param text     syllable text (may be empty when {@code extend} is {@link Extend#STOP} or
 *                 {@link Extend#CONTINUE} — those carriers mark melisma boundaries on notes
 *                 that have no text of their own)
 * @param extend   melisma extender state for this lyric (MusicXML-aligned)
 * @param syllabic syllabic position within the word ({@code null} only for carrier lyrics with
 *                 {@link Extend#STOP}/{@link Extend#CONTINUE})
 * @param compound {@code true} when this syllable joins the next via a compound-word boundary
 *                 ({@code =}); requires {@code syllabic} to be {@link Syllabic#BEGIN} or
 *                 {@link Syllabic#MIDDLE}
 */
public record Lyric(int verse, String text, Extend extend,
        @Nullable Syllabic syllabic, boolean compound) {

    /** Validates structural carrier vs. text-bearing constraints. */
    public Lyric {
        var isCarrier = extend == Extend.STOP || extend == Extend.CONTINUE;

        if (isCarrier) {
            if (syllabic != null) {
                throw new IllegalStateException(
                    "carrier lyric (extend=" + extend + ") must have null syllabic, got " + syllabic);
            }

            if (compound) {
                throw new IllegalStateException(
                    "carrier lyric (extend=" + extend + ") cannot be compound");
            }
        } else {
            if (syllabic == null) {
                throw new IllegalStateException(
                    "non-carrier lyric requires non-null syllabic");
            }
        }
    }

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

    /**
     * Syllabic position of a lyric syllable within its word, aligned with the MusicXML 4.0
     * {@code <syllabic>} element. Carrier lyrics ({@link Extend#STOP}, {@link Extend#CONTINUE})
     * have no syllabic value — those are represented as {@code null}.
     */
    public enum Syllabic { SINGLE, BEGIN, MIDDLE, END }
}
