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
 * other. Struts (collision floors) are untouched, so the solver can still compress the springs.
 */
public final class LyricLift {

    private static final double NO_LYRIC_REQUIREMENT_SS = 0.0;

    private static final double NO_LIFT_SS = 0.0;

    private LyricLift() {
    }

    /**
     * The lift is uncapped: a lyric-heavy line loosens evenly to whatever its widest syllable
     * needs.
     *
     * @param columns The columns those springs span; {@code springs.size() + 1} entries
     */
    public static List<Spring> applyLyricLift(List<Spring> springs, List<ElementColumn> columns) {

        var requirementsSs = new double[springs.size()];
        var liftSs = NO_LIFT_SS;

        for (var i = 0; i < springs.size(); i++) {
            var spring = springs.get(i);

            // A lift-exempt gap (grace→host, barline→key signature) never lifts: it keeps its fixed
            // rest and imposes no lyric requirement, so it neither spikes nor drives the line-wide
            // even lift.
            if (spring.liftExempt()) {
                requirementsSs[i] = NO_LYRIC_REQUIREMENT_SS;
                continue;
            }

            var beforePrev = i >= 1 ? columns.get(i - 1) : null;
            var afterCurr = i + 2 < columns.size() ? columns.get(i + 2) : null;
            var requirementSs = lyricRequirementSs(columns.get(i), columns.get(i + 1), beforePrev, afterCurr);
            requirementsSs[i] = requirementSs;

            // The largest shortfall on the line becomes the even lift; a tight beam gap contributes
            // only its own small shortfall, so it never inflates the whole line.
            //
            // The rest is measured with the flag's contribution backed out first (see
            // ElementColumn#getFlagExtentSs): the requirement is a lyric-clearance need, measured
            // from the notehead, and the flag has no bearing on it. Leaving the flag in would let an
            // unrelated stem flip change which gap "wins" the largest-shortfall vote and so silently
            // change the lift applied to the whole line.
            var restExcludingFlagSs = spring.restSs() - columns.get(i).getFlagExtentSs();
            var shortfallSs = requirementSs - restExcludingFlagSs;

            liftSs = Math.max(liftSs, shortfallSs);
        }

        var lifted = new ArrayList<Spring>(springs.size());

        for (var i = 0; i < springs.size(); i++) {
            var spring = springs.get(i);

            if (spring.liftExempt()) {
                lifted.add(spring);
                continue;
            }

            var factor = HorizontalSpacingCalculator.restFactorFor(columns.get(i), columns.get(i + 1));
            var evenRestSs = spring.restSs() + factor * liftSs;
            var newRestSs = Math.max(evenRestSs, requirementsSs[i]);

            lifted.add(spring.withRestSs(newRestSs));
        }

        return lifted;
    }

    private static double lyricRequirementSs(
        ElementColumn prev, ElementColumn curr,
        @Nullable ElementColumn beforePrev, @Nullable ElementColumn afterCurr) {

        return HorizontalSpacingCalculator.lyricGapRequirementSs(
            prev, curr, beforePrev, afterCurr, ElementColumn::getMinGapToNextSyllableSs);
    }
}
