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
     * Stem thickness in staff spaces, as the font declares it.
     *
     * <p>The strokes this program draws itself — barlines, brackets, hairpins — follow
     * LilyPond's weights, built up from {@link EngravingConstants#LILYPOND_BASE_THICKNESS_SS}.
     * A stem cannot: it has to register against glyphs the font supplies, starting at a
     * notehead's stem anchor and carrying a flag cut for its edge, so the font's own stem
     * width is the one those glyphs are drawn around.
     *
     * @return the stem thickness in staff spaces
     */
    public static double thicknessSs() {
        return SMuFLMetadata.stemThicknessSs();
    }

    /**
     * Half the stem thickness — the distance from a stem's center line to either edge. Beam and
     * stem-anchor geometry work outward from a stem's center, so they need the half width rather
     * than the full one.
     *
     * @return half the stem thickness in staff spaces
     */
    public static double halfWidthSs() {
        return thicknessSs() / 2.0;
    }

    /**
     * Stem length in staff-space units, from LilyPond's {@code Stem.details.lengths}, first
     * entry ({@code scm/define-grobs.scm:3453}, commented there "3.5 (or 3 measured from note
     * head) is standard length").
     */
    public static final double STEM_LENGTH_SS = 3.5;

    /** Stem length for grace notes in staff-space units — a SongScribe decision, not a port. */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

    private StemMetrics() {}
}
