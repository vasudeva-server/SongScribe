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

package songscribe.engraving;

import songscribe.smufl.SMuFLMetadata;

/**
 * Stem thickness and length, in staff spaces.
 */
public final class StemMetrics {
    /**
     * The font's stem width, not a multiple of {@link EngravingConstants#LILYPOND_BASE_THICKNESS_SS}:
     * the stem must register against the font's notehead and flag glyphs.
     */
    public static double thicknessSs() {
        return SMuFLMetadata.stemThicknessSs();
    }

    public static double halfWidthSs() {
        return thicknessSs() / 2.0;
    }

    /**
     * From the first entry of LilyPond's {@code Stem.details.lengths}.
     */
    public static final double STEM_LENGTH_SS = 3.5;

    /** Stem length for grace notes in staff-space units — a SongScribe decision, not a port. */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

    private StemMetrics() {}

    /** A grace stem always points up. */
    public static double graceStemTopYSs(StaffPosition head) {
        return head.toSs() - GRACE_NOTE_STEM_LENGTH_SS;
    }
}
