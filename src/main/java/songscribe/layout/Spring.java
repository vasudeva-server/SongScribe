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

package songscribe.layout;

/**
 * The horizontal spacing between two adjacent columns, expressed as a delta-X (Ss) between
 * their origins.
 *
 * <ul>
 *   <li>{@code restSs} — the ideal gap, used when there is no compression pressure.</li>
 *   <li>{@code strutSs} — the hard collision floor; the gap can never compress below this
 *       value, no matter how much compression pressure is applied.</li>
 *   <li>{@code weight} — the solver's reduction factor for this gap: under compression the gap
 *       levels to {@code weight × U} for a line-wide unit level {@code U}, so a tight beam-internal
 *       gap ({@code weight < 1}) stays proportionally tighter than a normal gap ({@code weight == 1})
 *       at every compression level, not just at rest. The strut still clamps the result, so a hard
 *       collision floor always wins over the reduction.</li>
 *   <li>{@code liftExempt} — a gap that takes no share of the lyric lift and imposes no lyric
 *       requirement of its own ({@link LyricLift#applyLyricLift}). Used for grace-host pairs, whose
 *       ideal is a fixed distance that must not grow with the line's syllables. It says nothing
 *       about compression: an exempt gap water-fills and gives down to its strut like any other.</li>
 *   <li>{@code levelOffsetSs} — the part of this gap the solver excludes from whitespace levelling:
 *       the previous column's glyph ink at the start of the gap, plus any optical-spacing
 *       correction. Under compression the gap levels to {@code levelOffset + weight × U}, so gaps
 *       with thin left glyphs (barlines) compress like their neighbours in visual-whitespace terms,
 *       and optical corrections survive compression as relative offsets instead of being levelled
 *       away.</li>
 * </ul>
 */
public record Spring(
    double restSs, double strutSs, double weight, boolean liftExempt, double levelOffsetSs) {

    /** A gap with no reduction: it levels to the full common unit under compression. */
    public static final double NORMAL_WEIGHT = 1.0;

    /** Creates a normal {@link Spring} ({@link #NORMAL_WEIGHT}, not lift-exempt, no level offset). */
    public static Spring of(double restSs, double strutSs) {
        return of(restSs, strutSs, NORMAL_WEIGHT, false);
    }

    /**
     * Creates a {@link Spring} with an explicit solver {@code weight} and {@code liftExempt} flag
     * and no level offset.
     */
    public static Spring of(double restSs, double strutSs, double weight, boolean liftExempt) {
        return of(restSs, strutSs, weight, liftExempt, 0.0);
    }

    /**
     * Creates a {@link Spring} with an explicit solver {@code weight}, {@code liftExempt} flag, and
     * {@code levelOffsetSs}.
     */
    public static Spring of(double restSs, double strutSs, double weight, boolean liftExempt, double levelOffsetSs) {
        return new Spring(restSs, strutSs, weight, liftExempt, levelOffsetSs);
    }

    /**
     * The uncompressed delta-X this gap wants: its rest, floored by its strut so a gap whose glyphs
     * would already collide at rest length starts pushed apart rather than overlapping.
     */
    public double naturalLengthSs() {
        return Math.max(restSs, strutSs);
    }

    /**
     * Returns a copy of this spring with a new rest gap, keeping the strut, weight, lift-exempt flag
     * and level offset. The strut is a hard collision floor and so is unaffected by rest adjustments
     * (e.g. the lyric lift pass).
     */
    public Spring withRestSs(double newRestSs) {
        return of(newRestSs, strutSs, weight, liftExempt, levelOffsetSs);
    }

    /**
     * Returns a copy of this spring with an optical-spacing correction folded in: the correction is
     * added to both the rest (so the uncompressed ideal reflects it) and the level offset (so it
     * survives compression as a relative offset to the levelled whitespace rather than being
     * levelled away). The strut is untouched — corrections are perceptual nudges, never
     * collision-safety changes.
     */
    public Spring withCorrectionSs(double correctionSs) {
        return of(restSs + correctionSs, strutSs, weight, liftExempt, levelOffsetSs + correctionSs);
    }
}
