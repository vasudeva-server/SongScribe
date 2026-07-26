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
 * {@code levelOffset_i + weight_i × U} and clamping each up to its strut (collision floor) and down
 * to its natural length. The level offset is the gap's non-whitespace component (left-glyph ink
 * plus optical corrections, see {@link Spring#levelOffsetSs}), so the fill levels <em>visual
 * whitespace</em> rather than raw origin-to-origin deltas: thin-left-glyph gaps (barline→note)
 * compress alongside their neighbours instead of being exempted by their small natural length, and
 * optical corrections survive compression as relative offsets instead of being levelled away.
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
 * One per-spring property breaks that uniformity by design, folded into the spring by the builder so
 * this class needs no beam/grace knowledge of its own: {@code weight}. A tight beam-internal gap
 * carries {@code weight < 1}, so it levels to {@code weight × U} and stays proportionally tighter
 * than a normal gap at every compression level. Its strut still clamps the result, so a hard
 * collision floor always wins.
 * <p>
 * Every gap participates in the fill — there is no pinned-and-excluded case. A gap that must not
 * give (or must give only a little) says so through its strut, which the builder raises to the floor
 * it wants; a grace→host gap uses exactly that mechanism to bound how far it can tighten. This keeps
 * "how far may this gap compress?" a single question with a single answer per spring, rather than a
 * flag the solver has to special-case.
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
     * equals its natural length, and the water-fill below leaves it there. That is the only way a
     * gap can be immovable — there is no separate pinning flag.
     *
     * <pre>
     *   natural = SUM max(rest_i, strut_i)
     *
     *          natural &lt;= availableSpanSs                 natural &gt; availableSpanSs
     *                   |                                           |
     *                   v                                           v
     *          +------------------+          floorSum = SUM strut_i
     *          |     SOLVED       |                            |
     *          | gap = natural    |            floorSum &gt; available   floorSum &lt;= available
     *          | (ragged right,   |                    |                     |
     *          |  O(n), no loop)  |                    v                     v
     *          +------------------+             +-------------+       WEIGHTED WATER-FILL to unit U:
     *                                           | INFEASIBLE  |       SUM clamp(w_i·U, strut_i,
     *                                           | (struts do  |           natural_i) = availableSpanSs
     *                                           |  not fit)   |
     *                                           +-------------+
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
     * stretched past where it started). Every gap enters the fill. {@code lengthsSs} enters at
     * natural length and is mutated in place.
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
        var freeWeight = 0.0;
        var freeOffsetSs = 0.0;

        for (var i = 0; i < gapCount; i++) {
            var spring = springs.get(i);
            floorSumSs += spring.strutSs();
            freeWeight += spring.weight();
            freeOffsetSs += spring.levelOffsetSs();
        }

        // Every gap on its strut and the chain still overflows: no fit exists.
        if (availableSpanSs < floorSumSs) {
            return SpringSolveResult.infeasible();
        }

        var budgetSs = availableSpanSs;

        // Each pass levels the still-free gaps to weight_i × U, then clamps the single gap that most
        // violates a bound. Clamping the worst violator is always correct — it sits on that bound in
        // the final fit — and permanently removes one gap, so the loop is bounded by gapCount passes.
        for (var pass = 0; pass < gapCount; pass++) {
            // The still-free gaps' offsets are consumed off the top of the budget; only the
            // remaining whitespace is levelled by weight.
            var unitLevelSs = (budgetSs - freeOffsetSs) / freeWeight;

            var worstGap = -1;
            var worstViolationSs = 0.0;
            var worstAtStrut = false;

            for (var i = 0; i < gapCount; i++) {
                if (clamped[i]) {
                    continue;
                }

                // The gap's offset plus its weighted share of the level. Below its strut floor -> it
                // holds more than its share (clamp up to the strut). Above its natural length ->
                // compress-only forbids stretching (clamp down to natural).
                var spring = springs.get(i);
                var targetSs = spring.levelOffsetSs() + spring.weight() * unitLevelSs;
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

            // No free gap violates a bound: each rests at its offset plus its weighted share of the
            // common level.
            if (worstGap < 0) {
                for (var i = 0; i < gapCount; i++) {
                    if (!clamped[i]) {
                        var spring = springs.get(i);
                        lengthsSs[i] = spring.levelOffsetSs() + spring.weight() * unitLevelSs;
                    }
                }

                return SpringSolveResult.solved(lengthsSs);
            }

            // A strut clamp pins the gap on its floor; a natural clamp leaves it at its natural length.
            var worstSpring = springs.get(worstGap);
            lengthsSs[worstGap] = worstAtStrut ? worstSpring.strutSs() : naturalSs[worstGap];

            budgetSs -= lengthsSs[worstGap];
            freeWeight -= worstSpring.weight();
            freeOffsetSs -= worstSpring.levelOffsetSs();
            clamped[worstGap] = true;
        }

        // Unreachable when feasible: availableSpanSs >= floorSumSs guarantees the last free gap can
        // hold the remaining budget without breaking its floor, so a pass always settles above. A
        // throw keeps a solver bug from silently mis-spacing the line.
        throw new IllegalStateException(
            "SpringSpacer.solve did not settle within " + gapCount + " passes");
    }
}
