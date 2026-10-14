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

import songscribe.util.StringUtils;
import songscribe.util.StringUtils.ShortA;

/**
 * A per-note lyric syllable, one of the four MusicXML 4.0 {@code <lyric>} forms named by its
 * {@link Extend}: a syllable ({@code NONE}), a melisma start ({@code START}), and the text-less
 * carriers that sustain ({@code CONTINUE}) or end ({@code STOP}) a melisma, whose
 * {@code syllabic} is {@code null}.
 *
 * <p>A text-bearing lyric bears text, so erasing a syllable removes its {@link Lyric} rather
 * than storing one with blank text.
 */
public record Lyric(Song.Script script, String text, Extend extend,
        @Nullable Syllabic syllabic, boolean compound) {

    /**
     * Compound-word boundary marker: a non-breaking hyphen (U+2011) appended to a syllable's
     * text signals that the syllable joins the next via SongScribe's {@code =}/{@code +}
     * operator. Shared by every serializer.
     */
    public static final String COMPOUND_WORD_MARKER = "\u2011";

    /** Legacy compound marker (zero-width space, U+200B); still recognized on load. */
    private static final String LEGACY_COMPOUND_WORD_MARKER = "\u200B";

    private static final List<String> COMPOUND_WORD_MARKERS =
        List.of(COMPOUND_WORD_MARKER, LEGACY_COMPOUND_WORD_MARKER);

    public Lyric {
        // Apply typographic substitution + short-A (idempotent; Line.java reconstructions
        // re-run harmlessly; carrier lyrics with text="" are unaffected since processText
        // returns "" unchanged).
        text = StringUtils.processText(text, ShortA.STRIP);

        if (isCarrier(extend)) {
            if (!text.isBlank()) {
                throw new IllegalArgumentException(
                    "carrier lyric (extend=" + extend + ") cannot have text; got: \"" + text + '"');
            }

            if (syllabic != null) {
                throw new IllegalArgumentException(
                    "carrier lyric (extend=" + extend + ") must have null syllabic, got " + syllabic);
            }

            if (compound) {
                throw new IllegalArgumentException(
                    "carrier lyric (extend=" + extend + ") cannot be compound");
            }
        } else {
            if (text.isBlank()) {
                throw new IllegalArgumentException(
                    "non-carrier lyric (extend=" + extend + ") requires non-blank text");
            }

            if (syllabic == null) {
                throw new IllegalArgumentException(
                    "non-carrier lyric requires non-null syllabic");
            }

            if (compound && !syllabicContinues(syllabic)) {
                throw new IllegalArgumentException(
                    "compound lyric requires syllabic BEGIN or MIDDLE, got " + syllabic);
            }
        }
    }

    public static Lyric carrier(Song.Script script, Extend extend) {
        return new Lyric(script, "", extend, null, false);
    }

    /**
     * Returns this record when nothing changes.
     */
    public Lyric withExtend(Extend extend) {
        return this.extend == extend ? this : new Lyric(script, text, extend, syllabic, compound);
    }

    public static boolean syllabicContinues(@Nullable Syllabic syllabic) {
        return syllabic == Syllabic.BEGIN || syllabic == Syllabic.MIDDLE;
    }

    public static boolean isCarrier(Extend extend) {
        return extend == Extend.STOP || extend == Extend.CONTINUE;
    }

    public boolean isCarrier() {
        return isCarrier(extend);
    }

    /**
     * Only meaningful for a syllable that continues into the next ({@link #syllabicContinues});
     * callers guard on that before applying it.
     */
    public static StrippedText stripCompoundMarker(String text) {
        for (var marker : COMPOUND_WORD_MARKERS) {
            if (text.endsWith(marker)) {
                return new StrippedText(text.substring(0, text.length() - marker.length()), true);
            }
        }

        return new StrippedText(text, false);
    }

    public record StrippedText(String text, boolean hadMarker) {}

    public enum Extend { NONE, START, STOP, CONTINUE }

    /**
     * Carrier lyrics ({@link Extend#STOP}, {@link Extend#CONTINUE}) have no syllabic value; it is
     * {@code null}.
     */
    public enum Syllabic { SINGLE, BEGIN, MIDDLE, END }
}
