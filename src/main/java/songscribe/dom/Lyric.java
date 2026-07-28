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

package songscribe.dom;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Constants;
import songscribe.util.StringUtils;

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
 * @param verse    1-based verse number; a song's verses are the languages its lyrics are written
 *                 in, and {@link Song#getActiveVerse()} selects the one being shown
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

    /**
     * The lowest verse index a lyric can carry. Verse numbering is 1-based — verse 0 does not
     * exist — so this is also the verse a song shows until another one is selected.
     */
    public static final int FIRST_VERSE = 1;

    /**
     * Compound-word boundary marker: a non-breaking hyphen (U+2011) appended to a
     * syllable's text signals that the syllable joins the next via SongScribe's
     * {@code =}/{@code +} operator. This is format-agnostic domain data shared by
     * every serializer (native {@code .mssw} and MusicXML), so it lives here rather
     * than being redeclared in each I/O class.
     */
    public static final String COMPOUND_WORD_MARKER = Constants.NON_BREAKING_HYPHEN;

    /** Pre-#420 compound marker (zero-width space, U+200B); still recognized on load. */
    private static final String LEGACY_COMPOUND_WORD_MARKER = "\u200B";

    /** Compound markers recognized on load, in preference order (current first). */
    private static final List<String> COMPOUND_WORD_MARKERS =
        List.of(COMPOUND_WORD_MARKER, LEGACY_COMPOUND_WORD_MARKER);

    /** Validates structural carrier vs. text-bearing constraints. */
    public Lyric {
        // Apply typographic substitution + short-A (idempotent; Line.java reconstructions
        // re-run harmlessly; carrier lyrics with text="" are unaffected since processText
        // returns "" unchanged).
        text = StringUtils.processText(text, true);

        if (isCarrier(extend)) {
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

            if (compound && !syllabicContinues(syllabic)) {
                throw new IllegalStateException(
                    "compound lyric requires syllabic BEGIN or MIDDLE, got " + syllabic);
            }
        }
    }

    /**
     * Whether {@code syllabic} continues into the following syllable with a hyphen — true for
     * {@link Syllabic#BEGIN} and {@link Syllabic#MIDDLE}, false otherwise (including {@code null}
     * carrier lyrics).
     */
    public static boolean syllabicContinues(@Nullable Syllabic syllabic) {
        return syllabic == Syllabic.BEGIN || syllabic == Syllabic.MIDDLE;
    }

    /**
     * Whether {@code extend} marks a text-less melisma carrier — {@link Extend#STOP} or
     * {@link Extend#CONTINUE}. Carriers have no text of their own and a {@code null} syllabic;
     * every other extend state is a text-bearing syllable.
     */
    public static boolean isCarrier(Extend extend) {
        return extend == Extend.STOP || extend == Extend.CONTINUE;
    }

    /** Whether this lyric is a text-less melisma carrier (see {@link #isCarrier(Extend)}). */
    public boolean isCarrier() {
        return isCarrier(extend);
    }

    /**
     * Removes a recognized trailing compound-word marker (current U+2011 or the legacy U+200B)
     * from {@code text}, reporting whether one was present. Only meaningful for a syllable that
     * continues into the next ({@link #syllabicContinues}) — the marker carries no meaning
     * elsewhere, so callers should guard on that before applying it.
     */
    public static StrippedText stripCompoundMarker(String text) {
        for (var marker : COMPOUND_WORD_MARKERS) {
            if (text.endsWith(marker)) {
                return new StrippedText(text.substring(0, text.length() - marker.length()), true);
            }
        }

        return new StrippedText(text, false);
    }

    /**
     * Result of {@link #stripCompoundMarker}: the syllable text with any recognized trailing
     * compound-word marker removed, and whether one was present.
     */
    public record StrippedText(String text, boolean hadMarker) {}

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
