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

/**
 * Stem thickness and length, in staff spaces.
 */
public final class StemMetrics {
    /** From LilyPond's {@code Stem} grob {@code thickness} ({@code scm/define-grobs.scm:3474}). */
    private static final double STEM_MULTIPLIER = 1.3;

    /** Stem thickness in staff spaces. */
    public static final double THICKNESS_SS = EngravingConstants.LILYPOND_BASE_THICKNESS_SS * STEM_MULTIPLIER;

    /**
     * Half the stem thickness — the distance from a stem's center line to either edge. Beam and
     * stem-anchor geometry work outward from a stem's center, so they need the half width rather
     * than the full one.
     */
    public static final double STEM_HALF_WIDTH_SS = THICKNESS_SS / 2.0;

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
