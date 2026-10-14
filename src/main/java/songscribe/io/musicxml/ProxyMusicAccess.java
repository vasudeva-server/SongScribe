/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.io.musicxml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import jakarta.xml.bind.JAXBElement;

import org.audiveris.proxymusic.AccidentalValue;
import org.audiveris.proxymusic.BackwardForward;
import org.audiveris.proxymusic.BarStyle;
import org.audiveris.proxymusic.Barline;
import org.audiveris.proxymusic.Encoding;
import org.audiveris.proxymusic.Key;
import org.audiveris.proxymusic.Lyric;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.Print;
import org.audiveris.proxymusic.RightLeftMiddle;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.StartStopDiscontinue;
import org.audiveris.proxymusic.StemValue;
import org.audiveris.proxymusic.Step;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;

/**
 * The reads of the generated ProxyMusic object graph that are more than a null
 * check: the ones that must reject the document, the ones that convert, and the
 * {@code JAXBElement} list unwrapping.
 *
 * <p>NullAway checks nothing across ProxyMusic's generated classes, so every absent value in the
 * graph is handled by hand.
 *
 * <p><b>Never import {@code org.audiveris.proxymusic.*}</b> — the package declares
 * its own {@code String} and {@code Double}, so a wildcard import makes every
 * {@code String} reference ambiguous. Single-type imports only.
 */
final class ProxyMusicAccess {

    private static final int DEFAULT_LYRIC_VERSE = 1;

    private ProxyMusicAccess() {}

    // -------------------------------------------------------------------------
    // Generic primitives
    // -------------------------------------------------------------------------

    static <T> T require(@Nullable T value, String detail) throws MusicXmlReader.InvalidFormatException {
        if (value == null) {
            throw new MusicXmlReader.InvalidFormatException(detail);
        }

        return value;
    }

    /**
     * {@code null} when the attribute is absent.
     */
    static <T> @Nullable String token(@Nullable T value, Function<? super T, String> toToken) {
        return value == null ? null : toToken.apply(value);
    }

    static boolean isYes(@Nullable YesNo value) {
        return value == YesNo.YES;
    }

    /** {@code null} when absent. */
    static @Nullable Double decimal(@Nullable BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    static int integer(@Nullable BigInteger value, int fallback) {
        return value == null ? fallback : value.intValue();
    }

    static @Nullable String elementName(JAXBElement<?> element) {
        var name = element.getName();
        return name == null ? null : name.getLocalPart();
    }

    /**
     * Members of another name or an unexpected type are skipped, not thrown on: these lists are
     * open-ended in the schema.
     */
    static <T> List<T> jaxbValues(List<JAXBElement<?>> elements, String name, Class<T> type) {
        var values = new ArrayList<T>();

        for (var element : elements) {
            var value = element.getValue();

            if (name.equals(elementName(element)) && type.isInstance(value)) {
                values.add(type.cast(value));
            }
        }

        return values;
    }

    /** {@code null} when there is none. */
    static <T> @Nullable T firstJaxbValue(List<JAXBElement<?>> elements, String name, Class<T> type) {
        var values = jaxbValues(elements, name, type);
        return values.isEmpty() ? null : values.getFirst();
    }

    // -------------------------------------------------------------------------
    // Score / header
    // -------------------------------------------------------------------------

    /**
     * {@code null} for a document with no part, which the reader maps to a song with no lines.
     */
    static ScorePartwise.@Nullable Part firstPart(ScorePartwise score) {
        var parts = score.getPart();
        return parts.isEmpty() ? null : parts.getFirst();
    }

    /**
     * {@code null} when the document carries no {@code <identification>} or no
     * {@code <encoding>} within it.
     */
    static @Nullable Encoding encoding(ScorePartwise score) {
        var identification = score.getIdentification();
        return identification == null ? null : identification.getEncoding();
    }

    /**
     * {@code null} when it is absent. Present-but-blank is returned as-is: the provenance gate
     * distinguishes missing from blank.
     */
    static @Nullable String software(ScorePartwise score) {
        var encoding = encoding(score);

        if (encoding == null) {
            return null;
        }

        return firstJaxbValue(encoding.getEncodingDateOrEncoderOrSoftware(), MusicXmlTags.SOFTWARE, String.class);
    }

    // -------------------------------------------------------------------------
    // Measure children
    // -------------------------------------------------------------------------

    /**
     * {@code null} when it states none. Absence takes no fallback: {@code 0} is C major, a key
     * the document never stated.
     */
    static @Nullable Integer keyFifths(Key key) {
        var fifths = key.getFifths();
        return fifths == null ? null : fifths.intValue();
    }

    static boolean startsNewSystem(Print print) {
        return isYes(print.getNewSystem());
    }

    /** {@code null} when the barline omits it. */
    static @Nullable String barStyleToken(Barline barline) {
        var barStyle = barline.getBarStyle();
        return barStyle == null ? null : token(barStyle.getValue(), BarStyle::value);
    }

    /** {@code null} when there is no repeat. */
    static @Nullable String repeatDirectionToken(Barline barline) {
        var repeat = barline.getRepeat();
        return repeat == null ? null : token(repeat.getDirection(), BackwardForward::value);
    }

    /** {@code null} when the attribute is absent. */
    static @Nullable String barlineLocationToken(Barline barline) {
        return token(barline.getLocation(), RightLeftMiddle::value);
    }

    /**
     * {@code null} when it carries none.
     */
    static @Nullable EndingMarker endingMarker(Barline barline) {
        var ending = barline.getEnding();

        if (ending == null) {
            return null;
        }

        return new EndingMarker(
            ending.getNumber(), token(ending.getType(), StartStopDiscontinue::value), ending.getRelativeY());
    }

    /**
     * {@code number} and {@code type} may be null for malformed input; the consumers
     * compare with null-safe {@code equals}.
     */
    record EndingMarker(@Nullable String number, @Nullable String type, @Nullable BigDecimal relativeY) {}

    // -------------------------------------------------------------------------
    // Note
    // -------------------------------------------------------------------------

    /** {@code null} when the note omits the element. */
    static @Nullable String noteTypeToken(Note note) {
        var noteType = note.getType();
        return noteType == null ? null : noteType.getValue();
    }

    static String requireNoteTypeToken(Note note) throws MusicXmlReader.InvalidFormatException {
        return require(noteTypeToken(note), "<note> is missing its <type> element");
    }

    static boolean isRest(Note note) {
        return note.getRest() != null;
    }

    static boolean isGrace(Note note) {
        return note.getGrace() != null;
    }

    /**
     * {@code null} when the note carries no usable {@code <pitch>} — a rest, or a
     * {@code <pitch>} whose {@code <step>} is absent or empty. {@code <alter>} is not exposed:
     * pitch is recovered from step and octave.
     */
    static @Nullable PitchValue pitch(Note note) {
        var pitch = note.getPitch();

        if (pitch == null) {
            return null;
        }

        var step = token(pitch.getStep(), Step::value);

        if (step == null || step.isEmpty()) {
            return null;
        }

        return new PitchValue(step.charAt(0), pitch.getOctave());
    }

    record PitchValue(char step, int octave) {}

    /** {@code null} when the note carries none. */
    static @Nullable String accidentalToken(Note note) {
        var accidental = note.getAccidental();
        return accidental == null ? null : token(accidental.getValue(), AccidentalValue::value);
    }

    static boolean hasStem(Note note) {
        return note.getStem() != null;
    }

    /** Meaningful only together with {@link #hasStem(Note)}. */
    static boolean stemIsUp(Note note) {
        var stem = note.getStem();
        return stem != null && stem.getValue() == StemValue.UP;
    }

    /** Lenient, matching {@code StaffElementIO}. */
    static int lyricVerse(Lyric lyric) {
        var number = lyric.getNumber();

        if (number == null) {
            return DEFAULT_LYRIC_VERSE;
        }

        try {
            return Integer.parseInt(number.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_LYRIC_VERSE;
        }
    }
}
