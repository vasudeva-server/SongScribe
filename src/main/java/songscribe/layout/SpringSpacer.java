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

import java.util.List;

/**
 * Solves a chain of {@link Spring}s against an available span by weighted water-filling: it drains
 * every free gap to a common unit level {@code U}, giving gap {@code i} the length
 * {@code weight_i × U} and clamping each up to its strut (collision floor) and down to its natural
 * length, keeping note spacing as uniform as the struts allow.
 * <p>
 * The solver is compress-only: a chain that already fits is left at its natural length, which
 * keeps lines ragged-right and preserves the even lyric lift the builder applied.
 * <p>
 * Water-filling replaces an earlier proportional-to-compliance compression, which drained each gap
 * in proportion to its slack and so pinched hardest exactly the gaps flanked by the narrowest
 * glyphs (the ones with the most slack). Equalising the lengths spreads the deficit evenly instead,
 * matching this engine's non-proportional principle: beyond the hard collision floor, glyph width
 * and rhythmic value do not dictate how far apart two columns sit.
 * <p>
 * Two per-spring properties break that uniformity by design, both folded into the spring by the
 * builder so this class needs no beam/grace knowledge of its own:
 * <ul>
 *   <li>{@code weight} — a tight beam-internal gap carries {@code weight < 1}, so it levels to
 *       {@code weight × U} and stays proportionally tighter than a normal gap at every compression
 *       level. Its strut still clamps the result, so a hard collision floor always wins.</li>
 *   <li>{@code rigid} — a grace→host gap is pinned to its natural length and excluded from the
 *       water-fill entirely, consuming a fixed slice of the span before the free gaps are levelled.</li>
 * </ul>
 * <p>
 * This class is lyric-unaware: syllable requirements are already folded into each spring's
 * rest/strut by the spring builder and the lyric lift.
 */
public final class SpringSpacer {

    private SpringSpacer() {
    }

    /**
     * Solves the spring chain so that the gaps sum to at most {@code availableSpanSs}.
     * <p>
     * Each gap starts at its natural length {@code max(rest, strut)} — {@code max} rather than
     * {@code rest} because a wide-glyph gap can have {@code rest < strut}, in which case the strut
     * wins and the gap starts on its floor. Such a gap simply never gives; it is one whose strut
     * equals its natural length, and the water-fill below leaves it there. A {@code rigid} gap is
     * likewise pinned to its natural length, but explicitly (by flag), and is held out of the
     * water-fill so it consumes a fixed slice of the span.
     *
     * <pre>
     *   natural = SUM max(rest_i, strut_i)
     *
     *          natural &lt;= availableSpanSs                 natural &gt; availableSpanSs
     *                   |                                           |
     *                   v                                           v
     *          +------------------+          floorSum = SUM strut_i (free) + SUM natural_i (rigid)
     *          |     SOLVED       |                            |
     *          | gap = natural    |            floorSum &gt; available   floorSum &lt;= available
     *          | (ragged right,   |                    |                     |
     *          |  O(n), no loop)  |                    v                     v
     *          +------------------+             +-------------+       WEIGHTED WATER-FILL to unit U:
     *                                           | INFEASIBLE  |       rigid gaps pinned at natural;
     *                                           | (struts do  |       SUM clamp(w_i·U, strut_i,
     *                                           |  not fit)   |           natural_i) = budget
     *                                           +-------------+          (budget = available − rigid)
     *                                                                        |
     *                                                                        v
     *                                           length_i = clamp(w_i·U, strut_i, natural_i)
     *                                             - w_i·U &lt; strut_i  : freeze on the strut (floor)
     *                                             - w_i·U &gt; natural_i: cap at natural (no stretch)
     *                                             - otherwise        : the weighted level w_i·U
     *
     *   U is found by levelling the still-free gaps, clamping the single most-violated one, and
     *   re-levelling the rest — so the loop is bounded by springs.size() passes. Exceeding that
     *   bound is a solver bug, not a layout condition, and throws rather than mis-spacing.
     * </pre>
     *
     * @param springs         the gaps between adjacent column origins, in left-to-right order
     * @param availableSpanSs the maximum allowed sum of gap lengths (delta-X Ss)
     * @return a solved result carrying {@code springs.size()} gap lengths, or
     *         {@link SpringSolveResult#infeasible()} when the chain overflows even fully
     *         compressed
     */
    public static SpringSolveResult solve(List<Spring> springs, double availableSpanSs) {
        var gapCount = springs.size();
        var lengthsSs = new double[gapCount];
        var naturalSpanSs = 0.0;

        for (var i = 0; i < gapCount; i++) {
            lengthsSs[i] = springs.get(i).naturalLengthSs();
            naturalSpanSs += lengthsSs[i];
        }

        // Fast path: the chain fits, so leave it at rest. Lines are ragged right — the solver
        // never stretches.
        if (naturalSpanSs <= availableSpanSs) {
            return SpringSolveResult.solved(lengthsSs);
        }

        return compress(springs, lengthsSs, availableSpanSs);
    }

    /**
     * Weighted water-fill: finds the common unit level {@code U} that makes the gaps sum to
     * {@code availableSpanSs}, giving free gap {@code i} the length {@code weight_i × U}, clamped up
     * to its strut (collision floor) and down to its natural length (compress-only, so no gap is
     * stretched past where it started). Rigid gaps are pinned to their natural length and taken out
     * of the fill up front. {@code lengthsSs} enters at natural length and is mutated in place.
     */
    private static SpringSolveResult compress(
        List<Spring> springs,
        double[] lengthsSs,
        double availableSpanSs) {

        var gapCount = springs.size();
        // Natural length per gap, captured before lengthsSs is mutated by clamps below.
        var naturalSs = lengthsSs.clone();
        var clamped = new boolean[gapCount];
        var floorSumSs = 0.0;
        var rigidTotalSs = 0.0;
        var freeWeight = 0.0;
        var freeCount = 0;

        // Rigid gaps (grace→host) never move: pin each to its natural length and hold it out of the
        // fill. Every other gap can give down to its strut and contributes its weight to the level.
        for (var i = 0; i < gapCount; i++) {
            var spring = springs.get(i);

            if (spring.rigid()) {
                clamped[i] = true;               // lengthsSs[i] already holds the natural length
                rigidTotalSs += naturalSs[i];
                floorSumSs += naturalSs[i];
            } else {
                floorSumSs += spring.strutSs();
                freeWeight += spring.weight();
                freeCount++;
            }
        }

        // Every free gap on its strut, every rigid gap on its default, and the chain still
        // overflows: no fit exists.
        if (availableSpanSs < floorSumSs) {
            return SpringSolveResult.infeasible();
        }

        // Defensive: unreachable under the current invariants. An all-rigid chain has
        // floorSumSs == naturalSpanSs, and compress() is only entered when naturalSpanSs exceeds
        // availableSpanSs, so the infeasibility check above always returns first. Kept because it
        // is the only thing standing between a broken invariant and a 0/0 unit level below.
        if (freeCount == 0) {
            return SpringSolveResult.solved(lengthsSs);
        }

        var budgetSs = availableSpanSs - rigidTotalSs;

        // Each pass levels the still-free gaps to weight_i × U, then clamps the single gap that most
        // violates a bound. Clamping the worst violator is always correct — it sits on that bound in
        // the final fit — and permanently removes one gap, so the loop is bounded by freeCount passes.
        for (var pass = 0; pass < gapCount; pass++) {
            var unitLevelSs = budgetSs / freeWeight;

            var worstGap = -1;
            var worstViolationSs = 0.0;
            var worstAtStrut = false;

            for (var i = 0; i < gapCount; i++) {
                if (clamped[i]) {
                    continue;
                }

                // The gap's weighted share of the level. Below its strut floor -> it holds more than
                // its share (clamp up to the strut). Above its natural length -> compress-only
                // forbids stretching (clamp down to natural).
                var spring = springs.get(i);
                var targetSs = spring.weight() * unitLevelSs;
                var belowStrutSs = spring.strutSs() - targetSs;
                var aboveNaturalSs = targetSs - naturalSs[i];

                if (belowStrutSs > worstViolationSs) {
                    worstViolationSs = belowStrutSs;
                    worstGap = i;
                    worstAtStrut = true;
                }

                if (aboveNaturalSs > worstViolationSs) {
                    worstViolationSs = aboveNaturalSs;
                    worstGap = i;
                    worstAtStrut = false;
                }
            }

            // No free gap violates a bound: each rests at its weighted share of the common level.
            if (worstGap < 0) {
                for (var i = 0; i < gapCount; i++) {
                    if (!clamped[i]) {
                        lengthsSs[i] = springs.get(i).weight() * unitLevelSs;
                    }
                }

                return SpringSolveResult.solved(lengthsSs);
            }

            // A strut clamp pins the gap on its floor; a natural clamp leaves it at its natural length.
            var worstSpring = springs.get(worstGap);
            lengthsSs[worstGap] = worstAtStrut ? worstSpring.strutSs() : naturalSs[worstGap];

            budgetSs -= lengthsSs[worstGap];
            freeWeight -= worstSpring.weight();
            clamped[worstGap] = true;
            freeCount--;
        }

        // Unreachable when feasible: availableSpanSs >= floorSumSs guarantees the last free gap can
        // hold the remaining budget without breaking its floor, so a pass always settles above. A
        // throw keeps a solver bug from silently mis-spacing the line.
        throw new IllegalStateException(
            "SpringSpacer.solve did not settle within " + gapCount + " passes");
    }
}
