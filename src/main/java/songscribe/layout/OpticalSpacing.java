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
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;

/**
 * A horizontal-spacing correction pass that ports three of LilyPond's optical-spacing corrections
 * ({@code lily/note-spacing.cc}, {@code lily/staff-spacing.cc}): small additive nudges to the ideal
 * (uncompressed) horizontal gap between adjacent note columns, compensating for optical illusions
 * caused by stem-direction geometry.
 *
 * <p>Corrections are folded in via {@link Spring#withCorrectionSs}, which shifts a spring's
 * {@code restSs} (the uncompressed ideal) and {@code levelOffsetSs} (so the correction survives
 * compression as a relative offset to the whitespace levelled by {@link SpringSpacer}) by the same
 * amount. {@code strutSs} (the hard collision floor) is never touched — these are perceptual
 * nudges, never collision-safety changes, matching LilyPond's own discipline.
 *
 * <p><b>Collision safety and the one-sided-narrowing property.</b> The solver never uses raw
 * {@code restSs} as a placed gap: it goes through {@link Spring#naturalLengthSs} (which floors
 * {@code restSs} at {@code strutSs}) and uses {@code strutSs} directly as the compression floor.
 * Two consequences follow:
 *
 * <ul>
 *   <li>A <b>negative</b> (narrowing) correction that would push {@code restSs} below {@code strutSs}
 *       is safe — the gap simply pins at {@code strutSs}; it never breaches the collision floor.</li>
 *   <li>Because of that same clamp, a narrowing correction has <b>no visible effect</b> on a gap that
 *       is already at (or below) its strut floor: widening always applies, but narrowing is only
 *       observable where the gap has slack above the collision floor. This is expected behavior, not
 *       a bug — it is not "fixed" by inflating the constants.</li>
 * </ul>
 *
 * <p><b>Grace notes are out of scope.</b> LilyPond routes grace columns out of note-spacing entirely
 * — a separate {@code Grace_spacing_engraver} builds a {@code GraceSpacing} spanner and grace columns
 * are floated as loose columns ({@code lily/grace-spacing-engraver.cc},
 * {@code lily/spacing-loose-columns.cc}), so {@code note-spacing.cc}/{@code staff-spacing.cc} never
 * see a grace column. SongScribe has no separate grace pass, so every correction below explicitly
 * early-returns zero when either column is a grace note ({@link ElementColumn#isGraceNote}),
 * reproducing LilyPond's structural exclusion with a guard.
 *
 * <p>A "knee" (a beam that changes stem direction mid-group) is also explicitly out of scope —
 * LilyPond has a separate {@code knee_correction} for it that this port does not implement.
 *
 * <pre>
 *   Screen-down Ss axis (negative = higher on the staff):
 *
 *     -Ss --- stem tip (UP stem)
 *           |
 *           |       prev (UP)        curr (DOWN)
 *     top --|       +---+             +---+   &lt;- getAbsoluteTopYSs  (smaller / higher)
 *           |  o====|   |       o=====|   |
 *       0 --+---- staff middle line ---------
 *           |       |   |notehead    |   |
 *     bot --|       +---+             +---+   &lt;- getAbsoluteBottomYSs (larger / lower)
 *           |                          |
 *     +Ss --                          +-- stem tip (DOWN stem)
 *
 *     verticalOverlapSs = min(bottoms) - max(tops)   (&gt; 0 only where the spans intersect)
 *
 *     prev       curr        fires when                                  correction (Ss)
 *     -------------------------------------------------------------------------------------------
 *     stem UP    stem DOWN   overlap&gt;0, not knee, not grace               +ramp * OPPOSITE_STEM_MAX_CORRECTION_SS
 *     stem DOWN  stem UP     overlap&gt;0, not knee, not grace               -ramp * OPPOSITE_STEM_MAX_CORRECTION_SS
 *     stem X     stem X      |deltaPos|&gt;SAME_DIRECTION_THRESHOLD_SS,      +-SAME_DIRECTION_MAX_CORRECTION_SS
 *                            not grace                                    (widen if curr higher)
 *     barline    stem DOWN   overlap(staff span, curr)&gt;0, not grace       +ramp * BARLINE_DOWNSTEM_MAX_CORRECTION_SS
 *     otherwise                                                           0
 *         ramp = min(overlapSs / STEM_OVERLAP_SATURATION_SS, 1.0)
 * </pre>
 */
public final class OpticalSpacing {

    /**
     * Ported from LilyPond's NoteSpacing.stem-spacing-correction default (scm/define-grobs.scm).
     * Package-private (rather than private) so {@code OpticalSpacingTest} can assert against it
     * directly instead of duplicating the literal.
     */
    static final double OPPOSITE_STEM_MAX_CORRECTION_SS = 0.5;

    /** Ported from LilyPond's NoteSpacing.same-direction-correction default (scm/define-grobs.scm). */
    static final double SAME_DIRECTION_MAX_CORRECTION_SS = 0.25;

    /**
     * Vertical overlap (Ss) at which the opposite-stem and downstem-after-barline corrections reach
     * full strength. LilyPond derives an equivalent saturation point from a hardcoded constant
     * applied inconsistently across two different internal unit scales; this is a single unified
     * value used by both corrections here instead.
     */
    static final double STEM_OVERLAP_SATURATION_SS = 3.5;

    /** Minimum vertical gap (Ss) between two same-direction notes before the correction applies. */
    static final double SAME_DIRECTION_THRESHOLD_SS = 0.5;

    /** Ported from LilyPond's StaffSpacing.stem-spacing-correction default (scm/define-grobs.scm). */
    static final double DOWNSTEM_BARLINE_MAX_CORRECTION_SS = 0.4;

    private OpticalSpacing() {
    }

    /**
     * Applies the opposite-stem and same-direction optical-spacing corrections to {@code springs}.
     *
     * @param springs One spring per adjacent column pair, as built by
     *                {@link HorizontalSpacingCalculator#buildSprings}
     * @param columns The columns those springs span; {@code springs.size() + 1} entries
     * @return New springs with corrected rests, in gap order; struts unchanged
     */
    public static List<Spring> applyCorrections(List<Spring> springs, List<ElementColumn> columns) {
        var corrected = new ArrayList<Spring>(springs.size());

        for (var i = 0; i < springs.size(); i++) {
            var spring = springs.get(i);

            // A rigid gap (grace->host) packs at a fixed distance and never changes - same
            // convention as LyricLift.applyLyricLift.
            if (spring.rigid()) {
                corrected.add(spring);
                continue;
            }

            var prev = columns.get(i);
            var curr = columns.get(i + 1);
            var correctionSs = oppositeStemCorrectionSs(prev, curr)
                + sameDirectionCorrectionSs(prev, curr)
                + downstemAfterBarlineCorrectionSs(prev, curr);

            corrected.add(correctionSs == 0.0 ? spring : spring.withCorrectionSs(correctionSs));
        }

        return corrected;
    }

    /**
     * Length of the 1-D overlap between two vertical spans [topA, bottomA] and [topB, bottomB] in the
     * screen-down Ss convention (smaller = higher). Non-positive when the spans do not intersect.
     */
    private static double verticalOverlapSs(double topA, double bottomA, double topB, double bottomB) {
        return Math.min(bottomA, bottomB) - Math.max(topA, topB);
    }

    private static double verticalOverlapSs(ElementColumn a, ElementColumn b) {
        return verticalOverlapSs(
            a.getAbsoluteTopYSs(), a.getAbsoluteBottomYSs(),
            b.getAbsoluteTopYSs(), b.getAbsoluteBottomYSs());
    }

    /**
     * Ramps a correction from 0 up to {@code maxSs} as {@code overlapSs} grows from 0 to
     * {@link #STEM_OVERLAP_SATURATION_SS}, saturating at {@code maxSs} beyond that.
     */
    private static double saturatedMagnitudeSs(double overlapSs, double maxSs) {
        return Math.min(overlapSs / STEM_OVERLAP_SATURATION_SS, 1.0) * maxSs;
    }

    /**
     * Widens the gap when {@code prev} stems up and {@code curr} stems down (their noteheads sit
     * close together, so the stems visually crowd the gap), and narrows it in the opposite case,
     * ramped by how much the two columns' vertical spans overlap.
     */
    private static double oppositeStemCorrectionSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.hasStem() || !curr.hasStem()) {
            return 0.0;
        }

        // Grace-note gaps are governed by grace spacing, not these optical corrections - LilyPond
        // routes grace columns out of note-spacing entirely, so exclude them here.
        if (prev.isGraceNote() || curr.isGraceNote()) {
            return 0.0;
        }

        if (prev.getDirection() == curr.getDirection()) {
            return 0.0;
        }

        // Opposite-direction stems sharing one beam group is a "knee" - out of scope, so no
        // correction is applied for that case.
        if (prev.getBeamGroupId() != ElementColumn.NO_BEAM_GROUP
            && prev.getBeamGroupId() == curr.getBeamGroupId()) {
            return 0.0;
        }

        var overlapSs = verticalOverlapSs(prev, curr);

        if (overlapSs <= 0.0) {
            return 0.0;
        }

        return saturatedMagnitudeSs(overlapSs, OPPOSITE_STEM_MAX_CORRECTION_SS) * prev.getDirection().sign();
    }

    /**
     * Widens or narrows the gap between two same-direction-stem notes by a fixed step when their
     * staff positions differ enough to look uneven, based on which of the two sits lower.
     */
    private static double sameDirectionCorrectionSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.hasStem() || !curr.hasStem()) {
            return 0.0;
        }

        if (prev.isGraceNote() || curr.isGraceNote()) {
            return 0.0;
        }

        if (prev.getDirection() != curr.getDirection()) {
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
     * Widens the gap after a barline when {@code curr} stems down, ramped by how much the downstem
     * column's vertical span overlaps the barline's own span (the full staff height, symmetric about
     * the middle line). Unconditionally additive - a downstem right after a barline always gets a
     * little more room, never less.
     */
    private static double downstemAfterBarlineCorrectionSs(ElementColumn prev, ElementColumn curr) {
        if (!prev.isBarline() || !curr.hasStem() || curr.getDirection() != StaffElement.Direction.DOWN) {
            return 0.0;
        }

        // A barline is not a grace note, so only curr can be one; exclude it for the same reason as
        // the other two corrections.
        if (curr.isGraceNote()) {
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
