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

import songscribe.dom.Direction;
import songscribe.engraving.Staff;

/**
 * A horizontal-spacing correction pass that ports three of LilyPond's optical-spacing corrections:
 * small additive nudges to the ideal (uncompressed) gap between adjacent note columns,
 * compensating for stem-direction illusions. The strut (collision floor) is never touched.
 *
 * <p>A narrowing correction has no visible effect on a gap already at its strut; this is expected,
 * not a bug to "fix" by inflating the constants.
 *
 * <p>Grace columns are ordinary columns here: the gap into a grace and the gap out of it to its
 * host are both corrected, though the grace-to-host gap is {@code liftExempt}.
 *
 * <p>A "knee" (a beam that changes stem direction mid-group) is not implemented.
 *
 * <p>See {@code docs/layout-geometry.md}.
 */
public final class OpticalSpacing {

    /** From LilyPond's NoteSpacing.stem-spacing-correction default. */
    private static final double OPPOSITE_STEM_MAX_CORRECTION_SS = 0.5;

    /** From LilyPond's NoteSpacing.same-direction-correction default. */
    static final double SAME_DIRECTION_MAX_CORRECTION_SS = 0.25;

    /**
     * Vertical overlap (Ss) at which the opposite-stem and downstem-after-barline corrections reach
     * full strength; one value for both.
     */
    static final double STEM_OVERLAP_SATURATION_SS = 3.5;

    static final double SAME_DIRECTION_THRESHOLD_SS = 0.5;

    /** From LilyPond's StaffSpacing.stem-spacing-correction default. */
    static final double DOWNSTEM_BARLINE_MAX_CORRECTION_SS = 0.4;

    private OpticalSpacing() {
    }

    /**
     * Every gap is corrected, lift-exempt ones included.
     *
     * @param columns The columns those springs span; {@code springs.size() + 1} entries
     */
    public static List<Spring> applyCorrections(List<Spring> springs, List<ElementColumn> columns) {
        var corrected = new ArrayList<Spring>(springs.size());

        for (var i = 0; i < springs.size(); i++) {
            var spring = springs.get(i);
            var prev = columns.get(i);
            var curr = columns.get(i + 1);
            var correctionSs = oppositeStemCorrectionSs(prev, curr)
                + sameDirectionCorrectionSs(prev, curr)
                + downstemAfterBarlineCorrectionSs(prev, curr);

            corrected.add(correctionSs == 0.0 ? spring : spring.withCorrectionSs(correctionSs));
        }

        return corrected;
    }

    private static double verticalOverlapSs(
        double topASs, double bottomASs, double topBSs, double bottomBSs) {

        return Math.min(bottomASs, bottomBSs) - Math.max(topASs, topBSs);
    }

    static double verticalOverlapSs(ElementColumn columnA, ElementColumn columnB) {
        return verticalOverlapSs(
            columnA.getAbsoluteTopYSs(), columnA.getAbsoluteBottomYSs(),
            columnB.getAbsoluteTopYSs(), columnB.getAbsoluteBottomYSs());
    }

    private static double saturatedMagnitudeSs(double overlapSs, double maxSs) {
        return Math.clamp(overlapSs / STEM_OVERLAP_SATURATION_SS, 0.0, 1.0) * maxSs;
    }

    /** Package-private so the per-gap debug dump can report this term on its own. */
    static double oppositeStemCorrectionSs(ElementColumn prev, ElementColumn curr) {
        var prevDirection = prev.stemDirection();
        var currDirection = curr.stemDirection();

        if (prevDirection == null || currDirection == null) {
            return 0.0;
        }

        if (prevDirection == currDirection) {
            return 0.0;
        }

        // Opposite-direction stems sharing one beam group is a "knee" - out of scope, so no
        // correction is applied for that case.
        if (prev.sharesBeamGroupWith(curr)) {
            return 0.0;
        }

        var overlapSs = verticalOverlapSs(prev, curr);

        if (overlapSs <= 0.0) {
            return 0.0;
        }

        return saturatedMagnitudeSs(overlapSs, OPPOSITE_STEM_MAX_CORRECTION_SS) * prevDirection.sign();
    }

    /** Package-private so the per-gap debug dump can report this term on its own. */
    static double sameDirectionCorrectionSs(ElementColumn prev, ElementColumn curr) {
        var prevDirection = prev.stemDirection();
        var currDirection = curr.stemDirection();

        if (prevDirection == null || currDirection == null) {
            return 0.0;
        }

        if (prevDirection != currDirection) {
            return 0.0;
        }

        var deltaSs = Math.abs(curr.getPositionSs() - prev.getPositionSs());

        if (deltaSs <= SAME_DIRECTION_THRESHOLD_SS) {
            return 0.0;
        }

        // Ss is screen-down here: a larger (more positive) staff position is a LOWER pitch.
        var currIsLower = curr.getPositionSs() > prev.getPositionSs();

        return currIsLower ? -SAME_DIRECTION_MAX_CORRECTION_SS : SAME_DIRECTION_MAX_CORRECTION_SS;
    }

    /**
     * Unconditionally additive: a downstem right after a barline always gets a little more room,
     * never less.
     *
     * <p>Package-private so the per-gap debug dump can report this term on its own.
     */
    static double downstemAfterBarlineCorrectionSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.isBarline() || curr.stemDirection() != Direction.DOWN) {
            return 0.0;
        }

        var overlapSs = verticalOverlapSs(
            -Staff.STAFF_HALF_SS, Staff.STAFF_HALF_SS, curr.getAbsoluteTopYSs(), curr.getAbsoluteBottomYSs());

        if (overlapSs <= 0.0) {
            return 0.0;
        }

        return saturatedMagnitudeSs(overlapSs, DOWNSTEM_BARLINE_MAX_CORRECTION_SS);
    }
}
