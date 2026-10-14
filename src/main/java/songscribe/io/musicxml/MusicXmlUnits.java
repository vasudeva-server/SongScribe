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

    private MusicXmlUnits() {}

    static double ssToTenths(double ss) {
        return ss * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
    }

    /**
     * The scale is pinned to {@value #POSITION_DECIMAL_PLACES} decimal places so a position
     * written as an attribute reads identically to the same measurement written as text by
     * {@link #formatSsAsTenths(double)}.
     */
    static BigDecimal ssAsTenths(double ss) {
        return BigDecimal.valueOf(ssToTenths(ss)).setScale(POSITION_DECIMAL_PLACES, RoundingMode.HALF_UP);
    }

    static String formatSsAsTenths(double ss) {
        return ssAsTenths(ss).toPlainString();
    }

    /**
     * {@code null} when the shift is zero, which keeps the attribute out of the document.
     */
    static @Nullable BigDecimal shiftTenths(double shiftSs) {
        return shiftSs != 0 ? ssAsTenths(shiftSs) : null;
    }

    static double tenthsToExactSs(double tenths) {
        return tenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE;
    }

    /**
     * @param tenths null when the attribute is absent, which selects 0
     */
    static double tenthsToExactSs(@Nullable BigDecimal tenths) {
        return tenths == null ? 0 : tenthsToExactSs(tenths.doubleValue());
    }

    static int parseIntOrThrow(String tag, String raw) throws SAXException {
        return DocumentValidation.parseIntOrThrow(LOG, tag, raw.trim());
    }

    static double parseDoubleOrThrow(String attr, String raw) throws SAXException {
        return DocumentValidation.parseDoubleOrThrow(LOG, attr, raw.trim());
    }
}
