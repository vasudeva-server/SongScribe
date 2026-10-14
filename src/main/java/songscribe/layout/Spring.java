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
 * The horizontal spacing between two adjacent columns, as a delta-X (Ss) between their origins.
 *
 * <ul>
 *   <li>{@code restSs} — the ideal gap when there is no compression pressure.</li>
 *   <li>{@code strutSs} — the hard collision floor; it wins over every other term.</li>
 *   <li>{@code weight} — the solver's reduction factor: under compression the gap levels to
 *       {@code weight × U} for a line-wide unit level {@code U}.</li>
 *   <li>{@code liftExempt} — takes no share of the lyric lift and imposes no lyric requirement
 *       ({@link LyricLift#applyLyricLift}); says nothing about compression.</li>
 *   <li>{@code levelOffsetSs} — the part of the gap excluded from whitespace levelling: the
 *       previous column's glyph ink plus any optical-spacing correction.</li>
 * </ul>
 */
public record Spring(
    double restSs, double strutSs, double weight, boolean liftExempt, double levelOffsetSs) {

    public static final double NORMAL_WEIGHT = 1.0;

    public static Spring of(double restSs, double strutSs) {
        return of(restSs, strutSs, NORMAL_WEIGHT, false);
    }

    public static Spring of(double restSs, double strutSs, double weight, boolean liftExempt) {
        return of(restSs, strutSs, weight, liftExempt, 0.0);
    }

    public static Spring of(double restSs, double strutSs, double weight, boolean liftExempt, double levelOffsetSs) {
        return new Spring(restSs, strutSs, weight, liftExempt, levelOffsetSs);
    }

    public double naturalLengthSs() {
        return Math.max(restSs, strutSs);
    }

    public Spring withRestSs(double newRestSs) {
        return of(newRestSs, strutSs, weight, liftExempt, levelOffsetSs);
    }

    /**
     * The correction survives compression as an offset; the strut is untouched.
     */
    public Spring withCorrectionSs(double correctionSs) {
        return of(restSs + correctionSs, strutSs, weight, liftExempt, levelOffsetSs + correctionSs);
    }
}
