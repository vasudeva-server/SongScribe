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

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The lyric lift pass: widens the rest gaps of a line's springs so adjacent syllables clear each
 * other, preferring an even line-wide lift over a local spike.
 *
 * <p>This is a rest-only transform — struts (collision floors) are untouched, and the resulting
 * springs are still free to compress back down in the solver.
 */
public final class LyricLift {

    /** A gap whose flanking columns do not both bear a syllable imposes no lyric requirement. */
    private static final double NO_LYRIC_REQUIREMENT_SS = 0.0;

    /** No shortfall, so no lift: rests pass through unchanged. */
    private static final double NO_LIFT_SS = 0.0;

    private LyricLift() {
    }

    /**
     * Lifts the rest gaps of {@code springs} so syllables clear their neighbours.
     *
     * <p>Each gap's lyric requirement is the delta-X needed between the two column origins for their
     * syllables not to touch: half of each syllable plus the minimum inter-syllable gap. The line
     * takes an even lift equal to the largest absolute shortfall of any gap
     * ({@code requirement − base rest}). The lift is added to each gap in proportion to that gap's
     * own reducing factor ({@link HorizontalSpacingCalculator#restFactorFor}), so beam-internal and
     * grace gaps stay proportionally tight rather than being levelled up. A gap whose requirement
     * still exceeds its proportional share — a tight gap carrying the binding syllable — takes a
     * local spike to its own requirement.
     *
     * <p>The lift is uncapped: a lyric-heavy line loosens evenly to whatever its widest syllable
     * needs, giving uniform note spacing rather than gap-by-gap spikes. As more notes are added the
     * line naturally overflows and {@link SpringSpacer#solve} compresses it back toward the struts.
     *
     * <pre>
     *   req_i  = prevHalfSyllable + minGapToNextSyllable + currHalfSyllable   (both bear a syllable)
     *   base_i = springs.get(i).restSs()                     <- factor_i × lineRest (+ glyph extent)
     *   lift   = max over gaps of (req_i − base_i), floored at 0
     *   rest_i = max(base_i + factor_i × lift, req_i)         <- local spike only where the share falls short
     *
     *   Worked example - defaultRestLength 2.5, a wide syllable requiring 4.0 on its two gaps:
     *
     *     lift = 4.0 − 2.5 = 1.5
     *     normal gaps      2.5 + 1.0 × 1.5 = 4.0   (whole line lifts evenly to the widest requirement)
     *     beamed-16th gaps 1.5 + 0.6 × 1.5 = 2.4   — proportionally tight, for free
     *
     *       N1  4.0  N2  4.0  N3  4.0  N4  4.0  N5
     *       (uniform note spacing — no local spike on the normal gaps)
     *
     *   Shortening or removing the wide syllable relaxes the line back down on the next layout.
     * </pre>
     *
     * @param springs One spring per adjacent column pair, as built by
     *                {@link HorizontalSpacingCalculator#buildSprings}
     * @param columns The columns those springs span; {@code springs.size() + 1} entries
     * @return New springs with lifted rests, in gap order; struts unchanged
     */
    public static List<Spring> applyLyricLift(List<Spring> springs, List<ElementColumn> columns) {

        var requirementsSs = new double[springs.size()];
        var liftSs = NO_LIFT_SS;

        for (var i = 0; i < springs.size(); i++) {
            var spring = springs.get(i);

            // A lift-exempt gap (grace→host) never lifts: it keeps its default rest and imposes no lyric
            // requirement, so it neither spikes nor drives the line-wide even lift.
            if (spring.liftExempt()) {
                requirementsSs[i] = NO_LYRIC_REQUIREMENT_SS;
                continue;
            }

            var beforePrev = i >= 1 ? columns.get(i - 1) : null;
            var afterCurr = i + 2 < columns.size() ? columns.get(i + 2) : null;
            var requirementSs = lyricRequirementSs(columns.get(i), columns.get(i + 1), beforePrev, afterCurr);
            requirementsSs[i] = requirementSs;

            // The absolute shortfall: how far this gap's ideal rest falls short of its requirement.
            // The largest shortfall on the line becomes the even lift; a tight beam gap contributes
            // only its own small shortfall, so it never inflates the whole line.
            //
            // The rest is measured with the flag's contribution backed out first (see
            // ElementColumn#getFlagExtentSs): the requirement is a lyric-clearance need, measured
            // from the notehead, and the flag has no bearing on it. Leaving the flag in would let an
            // unrelated stem flip change which gap "wins" the largest-shortfall vote and so silently
            // change the lift applied to the whole line (refs #629).
            var restExcludingFlagSs = spring.restSs() - columns.get(i).getFlagExtentSs();
            var shortfallSs = requirementSs - restExcludingFlagSs;

            liftSs = Math.max(liftSs, shortfallSs);
        }

        var lifted = new ArrayList<Spring>(springs.size());

        for (var i = 0; i < springs.size(); i++) {
            var spring = springs.get(i);

            // Lift-exempt gaps pass through untouched — no share of the lift, no requirement spike.
            if (spring.liftExempt()) {
                lifted.add(spring);
                continue;
            }

            var factor = HorizontalSpacingCalculator.restFactorFor(columns.get(i), columns.get(i + 1));
            // The gap's proportional share of the even lift, plus a local spike wherever the capped
            // lift still falls short of the requirement.
            var evenRestSs = spring.restSs() + factor * liftSs;
            var newRestSs = Math.max(evenRestSs, requirementsSs[i]);

            lifted.add(spring.withRestSs(newRestSs));
        }

        return lifted;
    }

    /**
     * Returns the delta-X (Ss) this gap needs for its syllables to clear their neighbour, or
     * {@link #NO_LYRIC_REQUIREMENT_SS} when neither flanking column's lyric reaches into the gap.
     *
     * <p>Each side contributes the part of its syllable that overhangs toward the gap, measured from
     * the notehead (accidentals and augmentation dots are excluded), so a single syllable reserves
     * its footprint against an as-yet-unlyriced neighbour rather than waiting for both sides to bear
     * a syllable. For a grace's host ({@code beforePrev} is a grace), the grace lyric's right
     * overhang past the grace→host union is carried into the host→next gap, since the grace and its
     * host behave as one unioned column for lyric layout.
     */
    private static double lyricRequirementSs(
        ElementColumn prev, ElementColumn curr,
        @Nullable ElementColumn beforePrev, @Nullable ElementColumn afterCurr) {

        return HorizontalSpacingCalculator.lyricGapRequirementSs(
            prev, curr, beforePrev, afterCurr, ElementColumn::getMinGapToNextSyllableSs);
    }
}
