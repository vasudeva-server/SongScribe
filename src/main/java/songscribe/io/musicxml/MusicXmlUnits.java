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
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.io.DocumentValidation;

final class MusicXmlUnits {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlUnits.class);

    // DIVISIONS is defined in NoteTypeMapping (which owns the tick math and
    // the full derivation). It must survive not just the plain note values
    // but a tuplet's M/N scaling for every ratio N in {2..7}, which is why
    // it is 13440 rather than the smaller value that satisfies the note
    // values alone: (13440 / 8) × 7/4 = 1680 × 7/4 = 2940 ticks (exact),
    // and 2940 stays exact when further scaled by any conventional M/N.
    static final int DIVISIONS = NoteTypeMapping.DIVISIONS;

    // Measure numbering starts at 1 (MusicXML spec requires positive integers).
    static final int FIRST_MEASURE_NUMBER = 1;

    // MusicXML tenths are decimal; two places is enough precision for every position
    // value SongScribe emits, and pinning the scale is what keeps the attribute and
    // text forms of one measurement textually identical.
    static final int POSITION_DECIMAL_PLACES = 2;

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
     * Converts a staff-space measure to MusicXML tenths — the inverse of the
     * reader's {@code tenthsToSs}. All position values share this single
     * conversion so the scattered {@code × TENTHS_PER_STAFF_SPACE} arithmetic
     * has one source of truth.
     */
    static double ssToTenths(double ss) {
        return ss * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
    }

    /**
     * Converts a staff-space measure to the {@code BigDecimal} a tenths-valued attribute
     * takes, pinned to {@value #POSITION_DECIMAL_PLACES} decimal places.
     *
     * <p>The scale is the whole point. JAXB marshals a {@code BigDecimal} through its own
     * {@code toString}, which honours the scale, so pinning it here is what makes a position
     * written as an attribute read identically to the same measurement written as plain text
     * by {@link #formatSsAsTenths(double)} — which is now this value's own text, so the two
     * cannot disagree. Unpinned, {@code BigDecimal.valueOf(12.0)} would print {@code 12.0}
     * against the text path's {@code 12.00}.
     */
    static BigDecimal ssAsTenths(double ss) {
        return BigDecimal.valueOf(ssToTenths(ss)).setScale(POSITION_DECIMAL_PLACES, RoundingMode.HALF_UP);
    }

    /**
     * Converts a staff-space measure to the MusicXML tenths string a position value emits as
     * element text — the {@link #ssAsTenths(double)} of {@code ss}, in plain notation.
     *
     * <p>Derived from {@code ssAsTenths} rather than formatted independently, so "the attribute
     * and the text form of the same measurement agree" is a consequence of the structure rather
     * than a claim two methods have to keep true. A fixed scale of
     * {@value #POSITION_DECIMAL_PLACES} never reaches scientific notation, so
     * {@code toPlainString} and {@code toString} agree; the plain form says so explicitly.
     */
    static String formatSsAsTenths(double ss) {
        return ssAsTenths(ss).toPlainString();
    }

    /**
     * A staff-space position shift as the {@code BigDecimal} a tenths-valued attribute
     * takes, or {@code null} when the shift is zero — JAXB omits a null attribute, which
     * is how a shift stays out of the document unless it carries information.
     */
    static @Nullable BigDecimal shiftTenths(double shiftSs) {
        return shiftSs != 0 ? ssAsTenths(shiftSs) : null;
    }

    /**
     * Converts a MusicXML tenths value to staff-spaces (tenths ÷ 10) without
     * rounding — the exact inverse of {@link #ssToTenths(double)}. Use this for
     * measures the model stores as a fractional double, such as the line width:
     * {@link #tenthsToSs(double)}'s whole-staff-space rounding would silently
     * change a value the writer emitted faithfully.
     */
    static double tenthsToExactSs(double tenths) {
        return tenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE;
    }

    /**
     * Converts a MusicXML {@code relative-y}/{@code relative-x} value in tenths to
     * SongScribe staff-spaces (tenths ÷ 10), rounded to the nearest integer.
     * Position values are whole staff-spaces in the model; for a fractional
     * measure use {@link #tenthsToExactSs(double)}.
     */
    static int tenthsToSs(double tenths) {
        return (int) Math.round(tenthsToExactSs(tenths));
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
