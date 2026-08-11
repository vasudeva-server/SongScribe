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
 * <p>This package is {@code @NullMarked} and the build runs NullAway as an error,
 * but ProxyMusic's generated classes carry no JSpecify annotations, so NullAway
 * checks nothing across them: {@code note.getPitch().getStep()} on a
 * {@code <rest>} compiles clean and throws {@link NullPointerException} at load
 * time. Every absent value in the graph is therefore handled by hand, and the
 * three kinds of handling that are worth having in one place live here:
 *
 * <ul>
 *   <li><b>Required values.</b> An absence that makes the document unreadable is
 *       rejected by {@link #require} or a {@code requireXxx} accessor, so the
 *       failure is a {@link MusicXmlReader.UnsupportedFormatException} carrying a
 *       specific {@link MusicXmlReader.UnsupportedFormatException#detail() detail}
 *       rather than an NPE. {@code MusicXmlReaderLenienceTest} is the
 *       specification for which values those are.</li>
 *   <li><b>Optional scalars that need converting.</b> A generated enum, a
 *       {@code BigDecimal}/{@code BigInteger}, a {@code yes|no}, or a
 *       {@code tenths} measurement, each turned into what the model wants and
 *       {@code null}-or-fallback when absent — {@link #token}, {@link #isYes},
 *       {@link #decimal}, {@link #integer}, {@link #tenthsToSs}.</li>
 *   <li><b>{@code JAXBElement} lists.</b> The heterogeneous
 *       {@code List<JAXBElement<?>>} properties such as
 *       {@code Encoding.getEncodingDateOrEncoderOrSoftware()}, unwrapped by
 *       element name and type through {@link #jaxbValues} and
 *       {@link #firstJaxbValue}.</li>
 * </ul>
 *
 * <p>An ordinary null-checked read of a directly-typed child stays at its use
 * site — {@code score.getMovementTitle()} in {@code HeaderMapper},
 * {@code note.getTimeModification()} in {@code NoteMapper}. Routing those through
 * here would add a delegating method per getter and check nothing that the
 * caller's own {@code null} test does not.
 *
 * <p>Because the checking is by hand, a missed one is possible.
 * {@code SongFileLoader.load} catches {@link RuntimeException} as a backstop, so
 * the consequence is a logged stack trace and "this file cannot be read" rather
 * than the application going down. That backstop is defence in depth, not a
 * substitute for the check.
 *
 * <p>The generated enums carry no common supertype, so their XML tokens are read
 * through {@link #token(Object, Function)} with the enum's own {@code value()}
 * method reference rather than one overload per enum.
 *
 * <p><b>Never import {@code org.audiveris.proxymusic.*}</b> — the package declares
 * its own {@code String} and {@code Double}, so a wildcard import makes every
 * {@code String} reference ambiguous. Single-type imports only.
 */
final class ProxyMusicAccess {

    /** Verse number for a {@code <lyric>} that omits its {@code number} attribute. */
    static final int DEFAULT_LYRIC_VERSE = 1;

    private ProxyMusicAccess() {}

    // -------------------------------------------------------------------------
    // Generic primitives
    // -------------------------------------------------------------------------

    /**
     * Returns {@code value}, or rejects the document when it is absent.
     *
     * @throws MusicXmlReader.UnsupportedFormatException if {@code value} is null
     */
    static <T> T require(@Nullable T value, String detail) throws MusicXmlReader.UnsupportedFormatException {
        if (value == null) {
            throw new MusicXmlReader.UnsupportedFormatException(detail);
        }

        return value;
    }

    /**
     * Reads a generated enum's XML token, or {@code null} when the attribute is
     * absent. Every generated enum exposes {@code value()} but they share no
     * supertype, so the accessor is passed in:
     * {@code token(barline.getLocation(), RightLeftMiddle::value)}.
     */
    static <T> @Nullable String token(@Nullable T value, Function<? super T, String> toToken) {
        return value == null ? null : toToken.apply(value);
    }

    /** True when a {@code yes|no} attribute is present and says {@code yes}. */
    static boolean isYes(@Nullable YesNo value) {
        return value == YesNo.YES;
    }

    /** An optional {@code xs:decimal} as a {@code double}, or {@code null} when absent. */
    static @Nullable Double decimal(@Nullable BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /** An optional {@code xs:integer} as an {@code int}, or {@code fallback} when absent. */
    static int integer(@Nullable BigInteger value, int fallback) {
        return value == null ? fallback : value.intValue();
    }

    /**
     * An optional {@code tenths}-valued attribute converted to SongScribe
     * staff-spaces, or 0 when the attribute is absent.
     */
    static int tenthsToSs(@Nullable BigDecimal tenths) {
        return tenths == null ? 0 : MusicXmlUnits.tenthsToSs(tenths.doubleValue());
    }

    /** The local element name of one member of a heterogeneous {@code JAXBElement} list. */
    static @Nullable String elementName(JAXBElement<?> element) {
        var name = element.getName();
        return name == null ? null : name.getLocalPart();
    }

    /**
     * Every value in {@code elements} published under the element name {@code name}
     * whose type is {@code type}, in document order. Members of another name or an
     * unexpected type are skipped rather than throwing: these lists are open-ended
     * in the schema, and a foreign file may carry constructs this reader ignores.
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

    /** The first value matching {@link #jaxbValues}, or {@code null} when there is none. */
    static <T> @Nullable T firstJaxbValue(List<JAXBElement<?>> elements, String name, Class<T> type) {
        var values = jaxbValues(elements, name, type);
        return values.isEmpty() ? null : values.getFirst();
    }

    // -------------------------------------------------------------------------
    // Score / header
    // -------------------------------------------------------------------------

    /**
     * The first {@code <part>}, or {@code null} for a document with no part at all
     * (a legitimate, if empty, MusicXML document — the reader maps it to a song
     * with no lines rather than failing).
     */
    static ScorePartwise.@Nullable Part firstPart(ScorePartwise score) {
        var parts = score.getPart();
        return parts.isEmpty() ? null : parts.getFirst();
    }

    /**
     * The {@code <encoding>} block, or {@code null} when the document carries no
     * {@code <identification>} or no {@code <encoding>} within it.
     */
    static @Nullable Encoding encoding(ScorePartwise score) {
        var identification = score.getIdentification();
        return identification == null ? null : identification.getEncoding();
    }

    /**
     * The document's {@code <software>} provenance tag, or {@code null} when it is
     * absent. Present-but-blank is returned as-is: the provenance gate
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
     * A {@code <key>}'s {@code <fifths>}, or {@code null} when it states none.
     *
     * <p>Absence cannot take a fallback the way {@link #integer(BigInteger, int)} allows:
     * {@code 0} is C major, a real key, so substituting it for a missing element would give
     * the line a key signature the document never stated.
     */
    static @Nullable Integer keyFifths(Key key) {
        var fifths = key.getFifths();
        return fifths == null ? null : fifths.intValue();
    }

    /** True when this {@code <print>} opens a new system (and therefore a new {@code Line}). */
    static boolean startsNewSystem(Print print) {
        return isYes(print.getNewSystem());
    }

    /** The {@code <bar-style>} token, or {@code null} when the barline omits it. */
    static @Nullable String barStyleToken(Barline barline) {
        var barStyle = barline.getBarStyle();
        return barStyle == null ? null : token(barStyle.getValue(), BarStyle::value);
    }

    /** The {@code <repeat>} direction token, or {@code null} when there is no repeat. */
    static @Nullable String repeatDirectionToken(Barline barline) {
        var repeat = barline.getRepeat();
        return repeat == null ? null : token(repeat.getDirection(), BackwardForward::value);
    }

    /** The barline {@code location} token, or {@code null} when the attribute is absent. */
    static @Nullable String barlineLocationToken(Barline barline) {
        return token(barline.getLocation(), RightLeftMiddle::value);
    }

    /**
     * The barline's {@code <ending>} volta marker, or {@code null} when it carries
     * none. The schema permits at most one per barline, so this is a single value
     * where the SAX reader accumulated a list.
     */
    static @Nullable EndingMarker endingMarker(Barline barline) {
        var ending = barline.getEnding();

        if (ending == null) {
            return null;
        }

        return new EndingMarker(ending.getNumber(), token(ending.getType(), StartStopDiscontinue::value));
    }

    /**
     * One {@code <ending number type>} read off a {@code <barline>}. Both fields may
     * be null for malformed input; the consumers compare with null-safe
     * {@code equals}.
     */
    record EndingMarker(@Nullable String number, @Nullable String type) {}

    // -------------------------------------------------------------------------
    // Note
    // -------------------------------------------------------------------------

    /** The {@code <type>} token, or {@code null} when the note omits the element. */
    static @Nullable String noteTypeToken(Note note) {
        var noteType = note.getType();
        return noteType == null ? null : noteType.getValue();
    }

    /**
     * The {@code <type>} token of a note that must have one.
     *
     * @throws MusicXmlReader.UnsupportedFormatException if the note omits {@code <type>}
     */
    static String requireNoteTypeToken(Note note) throws MusicXmlReader.UnsupportedFormatException {
        return require(noteTypeToken(note), "<note> is missing its <type> element");
    }

    /** True for a {@code <rest>}. */
    static boolean isRest(Note note) {
        return note.getRest() != null;
    }

    /** True for a {@code <grace>} note. */
    static boolean isGrace(Note note) {
        return note.getGrace() != null;
    }

    /**
     * The note's pitch, or {@code null} when it carries no usable {@code <pitch>} — a rest,
     * or a {@code <pitch>} whose {@code <step>} is absent or empty.
     *
     * <p>Step and octave come back together rather than from an accessor each: they are read
     * from the same {@code <pitch>} element, the caller needs both or neither, and two
     * accessors fetched it twice. {@code <alter>} is deliberately not exposed — pitch is
     * recovered from step and octave.
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

    /** A {@code <pitch>} reduced to the two values the model spells a note from. */
    record PitchValue(char step, int octave) {}

    /** The {@code <accidental>} token, or {@code null} when the note carries none. */
    static @Nullable String accidentalToken(Note note) {
        var accidental = note.getAccidental();
        return accidental == null ? null : token(accidental.getValue(), AccidentalValue::value);
    }

    /**
     * True when the note's {@code <accidental>} is drawn in parentheses — the writer
     * emits either {@code cautionary} or {@code parentheses}.
     */
    static boolean accidentalIsParenthesized(Note note) {
        var accidental = note.getAccidental();
        return accidental != null && (isYes(accidental.getCautionary()) || isYes(accidental.getParentheses()));
    }

    /** True when the note carries a {@code <stem>} — a manual direction override. */
    static boolean hasStem(Note note) {
        return note.getStem() != null;
    }

    /**
     * True when the note's {@code <stem>} says {@code up}. Meaningful only together
     * with {@link #hasStem(Note)}.
     */
    static boolean stemIsUp(Note note) {
        var stem = note.getStem();
        return stem != null && stem.getValue() == StemValue.UP;
    }

    /**
     * The verse number of a {@code <lyric>}: its {@code number} attribute, or
     * {@link #DEFAULT_LYRIC_VERSE} when absent or not a number (lenient, matching
     * {@code StaffElementIO}).
     */
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
