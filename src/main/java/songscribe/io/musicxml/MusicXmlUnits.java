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

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.io.DocumentValidation;

final class MusicXmlUnits {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlUnits.class);

    // DIVISIONS is defined in NoteTypeMapping (which owns the tick math).
    // DIVISIONS = 480 ensures that the smallest representable note fraction
    // — a double-dotted 32nd — produces an exact integer tick count:
    //   (480 / 8) × 7/4  =  60 × 7/4  =  105 ticks  (exact)
    static final int DIVISIONS = NoteTypeMapping.DIVISIONS;

    // Measure numbering starts at 1 (MusicXML spec requires positive integers).
    static final int FIRST_MEASURE_NUMBER = 1;

    // Each diatonic step = ½ staff space = 5 tenths.  Used to compute the
    // grace-note stem-tip Y: staffPosition × -5 gives tenths above the middle
    // staff line (positive = up in MusicXML; positions increase downward in
    // SongScribe, so the sign is negated).
    static final int TENTHS_PER_STAFF_POSITION = 5;

    // Standard upward stem extension above a grace notehead: 3.5 staff spaces
    // = 35 tenths. Added to the note's tenths-from-middle-line to give the
    // stem-tip default-y.
    static final int GRACE_STEM_EXTENSION_TENTHS = 35;

    // <staff-distance> is write-forward: SongScribe's single-staff model has no
    // inter-staff spacing, so a zero distance is emitted and ignored on read.
    static final String STAFF_DISTANCE_TENTHS = "0";

    /**
     * Empty-string sentinel stored in beam-level values for levels where the
     * note has no {@code <beam>} element. A level entry is empty when the note
     * is not short enough for that secondary beam level.
     */
    static final String NO_BEAM_AT_LEVEL = "";

    private MusicXmlUnits() {}

    /**
     * Formats a MusicXML tenths value as a two-decimal-place string.
     * MusicXML tenths are decimal numbers; two decimal places are sufficient
     * precision for all position values. {@link Locale#ROOT} forces a period
     * decimal separator so the output stays valid {@code xs:decimal} regardless
     * of the JVM default locale.
     */
    static String formatTenths(double tenths) {
        return String.format(Locale.ROOT, "%.2f", tenths);
    }

    /**
     * Converts a staff-space measure to MusicXML tenths — the inverse of the
     * reader's {@code tenthsToSs}. All position values share this single
     * conversion so the scattered {@code × TENTHS_PER_STAFF_SPACE} arithmetic
     * has one source of truth.
     */
    static double ssToTenths(double ss) {
        return ss * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
    }

    /**
     * Converts a staff-space measure to a formatted MusicXML tenths string —
     * the {@link #formatTenths(double)} of {@link #ssToTenths(double)}. This is
     * the form every position attribute emits, so it lives here as the single
     * source of truth for the convert-then-format composition.
     */
    static String formatSsAsTenths(double ss) {
        return formatTenths(ssToTenths(ss));
    }

    /**
     * Converts a MusicXML {@code relative-y}/{@code relative-x} value in tenths to
     * SongScribe staff-spaces (tenths ÷ 10), rounded to the nearest integer.
     */
    static int tenthsToSs(double tenths) {
        return (int) Math.round(tenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE);
    }

    /**
     * Reads an optional {@code tenths}-valued attribute and converts it to
     * SongScribe staff-spaces, returning 0 when the attribute is absent.
     */
    static int optionalTenthsAttrToSs(Attributes attributes, String attrName) throws SAXException {
        var raw = attributes.getValue(attrName);

        if (raw == null) {
            return 0;
        }

        return tenthsToSs(parseDoubleOrThrow(attrName, raw));
    }

    /**
     * Parses {@code raw} (trimmed) as an integer, throwing a {@link SAXException}
     * if it is not a valid integer. Delegates to the shared
     * {@link DocumentValidation#parseIntOrThrow}, supplying this class's logger so
     * the {@code .mssw} and MusicXML readers report corrupt values one way. The
     * trim tolerates the surrounding whitespace SAX character data can carry.
     */
    static int parseIntOrThrow(String tag, String raw) throws SAXException {
        return DocumentValidation.parseIntOrThrow(LOG, tag, raw.trim());
    }

    /**
     * Parses {@code raw} (trimmed) as a double, throwing a {@link SAXException} if
     * it is not a valid number. Used for positional attributes (tenths), which
     * MusicXML permits to be fractional. Delegates to the shared
     * {@link DocumentValidation#parseDoubleOrThrow}, supplying this class's logger.
     */
    static double parseDoubleOrThrow(String attr, String raw) throws SAXException {
        return DocumentValidation.parseDoubleOrThrow(LOG, attr, raw.trim());
    }
}
