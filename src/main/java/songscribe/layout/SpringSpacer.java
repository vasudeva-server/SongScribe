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

import org.jspecify.annotations.Nullable;

/**
 * Solves a chain of {@link Spring}s against an available span by weighted water-filling,
 * levelling visual whitespace rather than raw origin-to-origin deltas.
 * <p>
 * The solver is compress-only: a chain that already fits is left at its natural length, which
 * keeps lines ragged-right and preserves the even lyric lift the builder applied.
 * <p>
 * Every gap participates in the fill; a gap that must not give says so through its strut. The
 * class is lyric-unaware: syllable requirements are already folded into each spring.
 */
public final class SpringSpacer {

    private SpringSpacer() {
    }

    /**
     * See {@code docs/layout-geometry.md} for the decision diagram.
     *
     * @param springs the gaps between adjacent column origins, in left-to-right order
     * @return {@link SpringSolveResult#infeasible()} when the chain overflows even fully
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

    /** {@code lengthsSs} enters at natural length and is mutated in place. */
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
            var worst = worstViolator(springs, naturalSs, clamped, unitLevelSs);

            // No free gap violates a bound: each rests at its offset plus its weighted share of the
            // common level.
            if (worst == null) {
                for (var i = 0; i < gapCount; i++) {
                    if (!clamped[i]) {
                        var spring = springs.get(i);
                        lengthsSs[i] = spring.levelOffsetSs() + spring.weight() * unitLevelSs;
                    }
                }

                return SpringSolveResult.solved(lengthsSs);
            }

            // A strut clamp pins the gap on its floor; a natural clamp leaves it at its natural length.
            var worstGap = worst.gap();
            var worstSpring = springs.get(worstGap);
            lengthsSs[worstGap] = worst.atStrut() ? worstSpring.strutSs() : naturalSs[worstGap];

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

    private record Clamp(int gap, boolean atStrut) {}

    private static @Nullable Clamp worstViolator(
        List<Spring> springs,
        double[] naturalSs,
        boolean[] clamped,
        double unitLevelSs) {

        Clamp worst = null;
        var worstViolationSs = 0.0;

        for (var i = 0; i < springs.size(); i++) {
            if (clamped[i]) {
                continue;
            }

            var spring = springs.get(i);
            var targetSs = spring.levelOffsetSs() + spring.weight() * unitLevelSs;
            var belowStrutSs = spring.strutSs() - targetSs;
            var aboveNaturalSs = targetSs - naturalSs[i];

            if (belowStrutSs > worstViolationSs) {
                worstViolationSs = belowStrutSs;
                worst = new Clamp(i, true);
            }

            if (aboveNaturalSs > worstViolationSs) {
                worstViolationSs = aboveNaturalSs;
                worst = new Clamp(i, false);
            }
        }

        return worst;
    }
}
