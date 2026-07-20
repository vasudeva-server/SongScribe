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
 *   <li>{@code complianceSs} — the slack this gap gives up under compression, i.e. how much
 *       {@code restSs} exceeds {@code strutSs}.</li>
 *   <li>{@code weight} — the solver's reduction factor for this gap: under compression the gap
 *       levels to {@code weight × U} for a line-wide unit level {@code U}, so a tight beam-internal
 *       gap ({@code weight < 1}) stays proportionally tighter than a normal gap ({@code weight == 1})
 *       at every compression level, not just at rest. The strut still clamps the result, so a hard
 *       collision floor always wins over the reduction.</li>
 *   <li>{@code rigid} — a gap whose length never changes from its natural (default): it takes no
 *       lyric lift and does not participate in the water-fill, consuming a fixed slice of the span.
 *       Used for grace-host pairs, which pack at a fixed distance regardless of the line's fit.</li>
 * </ul>
 */
public record Spring(double restSs, double strutSs, double complianceSs, double weight, boolean rigid) {

    /** A gap with no reduction: it levels to the full common unit under compression. */
    public static final double NORMAL_WEIGHT = 1.0;

    /**
     * Creates a normal {@link Spring} ({@link #NORMAL_WEIGHT}, not rigid) with {@code complianceSs}
     * derived from {@code restSs} and {@code strutSs}: the amount by which the rest gap exceeds the
     * strut, floored at zero.
     */
    public static Spring of(double restSs, double strutSs) {
        return of(restSs, strutSs, NORMAL_WEIGHT, false);
    }

    /**
     * Creates a {@link Spring} with an explicit solver {@code weight} and {@code rigid} flag.
     * {@code complianceSs} is derived from {@code restSs} and {@code strutSs}.
     */
    public static Spring of(double restSs, double strutSs, double weight, boolean rigid) {
        return new Spring(restSs, strutSs, Math.max(0, restSs - strutSs), weight, rigid);
    }

    /**
     * The uncompressed delta-X this gap wants: its rest, floored by its strut so a gap whose glyphs
     * would already collide at rest length starts pushed apart rather than overlapping.
     */
    public double naturalLengthSs() {
        return Math.max(restSs, strutSs);
    }

    /**
     * Returns a copy of this spring with a new rest gap, keeping the strut, weight and rigid flag and
     * recomputing {@code complianceSs} from the new rest. The strut is a hard collision floor and so
     * is unaffected by rest adjustments (e.g. the lyric lift pass).
     */
    public Spring withRestSs(double newRestSs) {
        return of(newRestSs, strutSs, weight, rigid);
    }
}
