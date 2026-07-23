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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.engraving.LineThickness;

/**
 * Port of LilyPond's beam-positioning pipeline ({@code Beam_scoring_problem}),
 * as a pure-math, layout-state-free computation.
 *
 * <pre>
 *  SIGN CONVENTION
 *  ───────────────
 *  LayoutEngine (SongScribe space)  : ss, Y-DOWN positive, y=0 = middle staff line
 *  BeamScoring  (LilyPond space)    : ss, Y-UP   positive, y=0 = middle staff line
 *
 *  Every Y-up ↔ Y-down conversion happens ONLY at the LayoutEngine ↔ BeamScoring
 *  boundary. Inside this class everything is Y-up.
 *
 *      IN  (per stem):  headYUpSs  = -Staff.spToSs(staffPosition)
 *                       headHalfPos = -staffPosition
 *
 *      OUT (per group): a beam position is (leftY, rightY) — the Y of the CENTER
 *                       of the beam farthest from the noteheads, at the first and
 *                       last stem's X. Converted back by the caller as:
 *
 *                           startYSs = -leftY - dirSign * bt / 2
 *                           slope    = -(rightY - leftY) / xSpan
 *
 *                       (bt = beam thickness in ss; slope is Y-down per X.)
 * </pre>
 *
 * <p>Deliberate simplifications relative to LilyPond: no knees, no cross-staff
 * beams, no collision avoidance (beams score only against staff lines), no
 * tremolos, no broken beams, {@code length-fraction = 1}, and single noteheads
 * (no chords).
 */
final class BeamScoring {

    private static final Logger LOG = LoggerFactory.getLogger(BeamScoring.class);

    // ---------------------------------------------------------------------
    // Grob defaults (scm/define-grobs.scm), indexed by beamCount - 1 with
    // LilyPond's robust_list_ref clamp-to-last behavior.
    // ---------------------------------------------------------------------

    /** {@code beamed-lengths}: nominal beamed stem length per beam count. */
    static final double[] BEAMED_LENGTHS_SS = { 3.26, 3.5, 3.6 };

    /** {@code beamed-minimum-free-lengths}: free stem length from chord to beams. */
    static final double[] BEAMED_MINIMUM_FREE_LENGTHS_SS = { 1.83, 1.5, 1.25 };

    /** {@code beamed-extreme-minimum-free-lengths}: LilyPond defines only two entries. */
    static final double[] BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS = { 2.0, 1.25 };

    /** {@code beamed-stem-shorten}: shortening applied when stems are forced. */
    static final double[] BEAMED_STEM_SHORTEN_SS = { 1.0, 0.5, 0.25 };

    // ---------------------------------------------------------------------
    // Quant scoring parameters (Beam_quant_parameters::fill, beam-quanting.cc).
    // ---------------------------------------------------------------------

    /** Number of staff-spaces of quant candidates searched on each side. */
    static final int REGION_SIZE = 2;

    /** Tolerance for treating two beam positions as equal. */
    static final double BEAM_EPS = 1e-3;

    static final double SECONDARY_BEAM_DEMERIT = 10.0;
    static final double STEM_LENGTH_DEMERIT_FACTOR = 5.0;
    static final double HORIZONTAL_INTER_QUANT_PENALTY = 500.0;
    static final double STEM_LENGTH_LIMIT_PENALTY = 5000.0;
    static final double DAMPING_DIRECTION_PENALTY = 800.0;
    static final double HINT_DIRECTION_PENALTY = 20.0;
    static final double MUSICAL_DIRECTION_FACTOR = 400.0;
    static final double IDEAL_SLOPE_FACTOR = 10.0;
    static final double ROUND_TO_ZERO_SLOPE = 0.02;

    // ---------------------------------------------------------------------
    // Tuning values that LilyPond writes as bare literals inside the scorers.
    // ---------------------------------------------------------------------

    /** Weight applied by {@code shrink_extra_weight} to negative deviations. */
    private static final double SHRINK_EXTRA_WEIGHT_FACTOR = 1.5;

    /** Scales the initial "distance from the ideal offset" tie-breaker demerit. */
    private static final double INITIAL_DEMERIT_SCALE = 1000.0;

    /** Half a staff space of padding between a notehead and the beam's quant range. */
    private static final double QUANT_RANGE_PADDING_SS = 0.5;

    /** Divisor of the staff line thickness giving leniency to borderline gaps. */
    private static final double FORBIDDEN_QUANT_FUDGE_FACTOR = 2.2;

    /** Demerit charged for any staff line inside a beam gap, before the distance term. */
    private static final double FORBIDDEN_QUANT_FIXED_DEMERIT = 0.39;

    /** Offset that turns {@code Math.floor} into LilyPond's {@code round_halfway_up}. */
    private static final double ROUND_HALF_UP_OFFSET = 0.5;

    /** How close to an inter quant a horizontal beam must be to be penalized. */
    private static final double HORIZONTAL_INTER_QUANT_TOLERANCE_SS = 0.01;

    /** Distance between adjacent staff lines, in staff spaces. */
    private static final double STAFF_LINE_STEP_SS = 1.0;

    // ---------------------------------------------------------------------
    // Slope damping (beam-quanting.cc slope_damping / concaveness).
    // ---------------------------------------------------------------------

    static final double SLOPE_DAMPING_COEFFICIENT = 0.6;
    static final double DAMPING = 1.0;

    /** Concaveness value at or above which the beam is forced flat. */
    static final double CONCAVE_FORCE_FLAT = 10000.0;

    /**
     * How far past the stem-length feasibility bound the beam is placed when the
     * unquanted position violates that bound. LilyPond's {@code point_in_interval}
     * (beam-quanting.cc:442) picks a point this far inside a half-open interval
     * rather than sitting exactly on its edge, which keeps the whole quant
     * candidate window in feasible territory instead of straddling the minimum.
     */
    private static final double FEASIBLE_POINT_INSET_SS = 2.0;

    // ---------------------------------------------------------------------
    // Geometry — matches the renderer, NOT LilyPond's raw numbers.
    // ---------------------------------------------------------------------

    /** Half-height of a 5-line staff, in staff spaces. */
    static final double STAFF_RADIUS_SS = 2.0;

    /** Beam thickness in staff spaces (LilyPond's {@code beam_thickness}). */
    static final double BEAM_THICKNESS_SS = LineThickness.BEAM_THICKNESS_SS;

    /** Center-to-center distance between stacked beams, in staff spaces. */
    static final double BEAM_TRANSLATION_SS = LineThickness.BEAM_TRANSLATION_SS;

    /** Staff line thickness in staff spaces (LilyPond's {@code slt}). */
    static final double STAFF_LINE_THICKNESS_SS = LineThickness.STAFF_LINE_SS;

    // ---------------------------------------------------------------------
    // Quant offsets — the single source of truth for straddle/sit/inter/hang.
    // Not compile-time constants: they depend on runtime-loaded font metrics.
    // ---------------------------------------------------------------------

    /** Beam center sits on a staff line. */
    static final double STRADDLE_SS = 0.0;

    /** Beam sits on top of a staff line. */
    static final double SIT_SS = (BEAM_THICKNESS_SS - STAFF_LINE_THICKNESS_SS) / 2.0;

    /** Beam center sits between two staff lines. */
    static final double INTER_SS = 0.5;

    /** Beam hangs below a staff line. */
    static final double HANG_SS = 1.0 - (BEAM_THICKNESS_SS - STAFF_LINE_THICKNESS_SS) / 2.0;

    // ---------------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------------

    /**
     * One stem of the beam group, in scoring space.
     *
     * @param xSs        X in staff spaces, relative to the first stem
     * @param headYUpSs  the notehead's Y-up position in staff spaces
     * @param headHalfPos the notehead's integer Y-up half-space position
     * @param beamCount  number of beams at this stem
     */
    record StemInput(double xSs, double headYUpSs, int headHalfPos, int beamCount) {}

    /**
     * Per-stem beam-side stem-end limits, in scoring space (real Y-up ss).
     *
     * @param idealYUpSs    the preferred stem end
     * @param shortestYUpSs the shortest acceptable stem end
     */
    record StemInfo(double idealYUpSs, double shortestYUpSs) {}

    /**
     * The solved beam position: the Y of the center of the beam farthest from the
     * noteheads, at the first and last stem's X, in scoring space (Y-up ss).
     *
     * @param leftYUpSs  the position at the first stem
     * @param rightYUpSs the position at the last stem
     */
    record BeamPosition(double leftYUpSs, double rightYUpSs) {}

    private final List<StemInput> stems;

    /** +1 for stems up, -1 for stems down. */
    private final int dirSign;

    /** Fraction of the group's stems whose direction is forced (0-1). */
    private final double forcedFraction;

    /** The largest beam count over all stems of the group. */
    private final int maxBeamCount;

    /** X of the last stem, relative to the first (0 for a single-column group). */
    private final double xSpan;

    // ---------------------------------------------------------------------
    // Pipeline state
    // ---------------------------------------------------------------------

    private List<StemInfo> stemInfos = List.of();
    private double unquantedLeftY;
    private double unquantedRightY;
    private double musicalDy;

    BeamScoring(List<StemInput> stems, int dirSign, double forcedFraction) {
        this.stems = List.copyOf(stems);
        this.dirSign = dirSign;
        this.forcedFraction = forcedFraction;

        var count = 1;
        for (var stem : this.stems) {
            count = Math.max(count, stem.beamCount());
        }

        maxBeamCount = count;
        xSpan = this.stems.isEmpty() ? 0.0 : this.stems.get(this.stems.size() - 1).xSs() - this.stems.get(0).xSs();
    }

    // ---------------------------------------------------------------------
    // Stem info (port of Stem::calc_stem_info, lily/stem.cc)
    // ---------------------------------------------------------------------

    /** Computes and caches {@link #stemInfos} for every stem of the group. */
    void computeStemInfos() {
        var infos = new ArrayList<StemInfo>(stems.size());

        for (var stem : stems) {
            infos.add(calcStemInfo(stem));
        }

        stemInfos = List.copyOf(infos);
    }

    /**
     * Port of {@code Stem::calc_stem_info} with {@code staff_space = 1},
     * {@code length-fraction = 1}, no tremolo, no {@code no-stem-extend} and no
     * knee. LilyPond computes in "direction-multiplied" space (as if the stem
     * pointed up) and multiplies by the direction only at the end; this port
     * keeps that structure so the clamps read the same as the original.
     *
     * @param stem the stem to measure
     * @return the stem's ideal and shortest beam-side ends, in real Y-up ss
     */
    StemInfo calcStemInfo(StemInput stem) {
        var beamCount = stem.beamCount();
        var heightOfBeams = BEAM_THICKNESS_SS + (beamCount - 1) * BEAM_TRANSLATION_SS;
        var halfBeamThickness = BEAM_THICKNESS_SS / 2.0;

        // Stems only extend to the center of the beam, hence the half-thickness terms.
        var idealLength = Math.max(
            listRef(BEAMED_LENGTHS_SS, beamCount - 1) - halfBeamThickness,
            listRef(BEAMED_MINIMUM_FREE_LENGTHS_SS, beamCount - 1) + heightOfBeams - halfBeamThickness
        );

        // Direction-multiplied space: positive is "away from the notehead".
        var noteStart = stem.headYUpSs() * dirSign;
        var idealY = noteStart + idealLength;

        // The highest beam of an (UP) beam must never be lower than the middle staff line…
        idealY = Math.max(idealY, 0.0);
        // …and its lowest beam must never be lower than the second staff line.
        idealY = Math.max(idealY, -1.0 - BEAM_THICKNESS_SS + heightOfBeams);

        // Port of Beam::calc_stem_shorten, which uses the group's max beam count.
        idealY -= listRef(BEAMED_STEM_SHORTEN_SS, maxBeamCount - 1) * forcedFraction;

        var minimumLength = listRef(BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS, beamCount - 1)
            + heightOfBeams
            - halfBeamThickness;

        return new StemInfo(idealY * dirSign, (noteStart + minimumLength) * dirSign);
    }

    /** Port of LilyPond's {@code robust_list_ref}: clamps the index to the last entry. */
    private static double listRef(double[] values, int index) {
        return values[Math.min(Math.max(index, 0), values.length - 1)];
    }

    // ---------------------------------------------------------------------
    // Unquanted (ideal) positions — ports of Beam_scoring_problem's
    // least_squares_positions / slope_damping / shift_region_to_valid.
    // ---------------------------------------------------------------------

    /**
     * Runs the ideal-position pipeline, producing {@link #unquantedLeftY},
     * {@link #unquantedRightY} and {@link #musicalDy}. Mirrors the sequence in
     * {@code Beam_scoring_problem::Beam_scoring_problem}.
     *
     * <p>Requires {@link #computeStemInfos()} to have run, and a non-degenerate
     * group ({@link #xSpan} != 0) — degenerate groups short-circuit earlier.
     */
    void computeUnquantedPositions() {
        leastSquaresPositions();
        slopeDamping();
        shiftRegionToValid();
    }

    /**
     * Port of {@code Beam_scoring_problem::least_squares_positions}. Every stem
     * is "normal" here, so the first/last normal stems are simply the first and
     * last, and {@code stem_ypositions_} is uniformly 0 (single staff).
     */
    private void leastSquaresPositions() {
        if (stemInfos.isEmpty()) {
            return;
        }

        var idealLeft = stemInfos.get(0).idealYUpSs();
        var idealRight = stemInfos.get(stemInfos.size() - 1).idealYUpSs();

        if (idealRight - idealLeft == 0.0) {
            var firstHeadY = stems.get(0).headYUpSs();
            var lastHeadY = stems.get(stems.size() - 1).headYUpSs();
            var chordDy = lastHeadY - firstHeadY;

            // Simple two-stem beams whose stems both reach the middle line get an
            // artificial slope, since the ideal positions cannot express one.
            if (idealLeft == 0.0 && stems.size() == 2 && chordDy != 0.0) {
                var half = BEAM_THICKNESS_SS / 2.0;
                var towardHigherHead = chordDy > 0.0;
                unquantedLeftY = towardHigherHead ? -half : half;
                unquantedRightY = towardHigherHead ? half : -half;
            } else {
                unquantedLeftY = idealLeft;
                unquantedRightY = idealRight;
            }

            musicalDy = unquantedRightY - unquantedLeftY;
            return;
        }

        var fit = minimiseLeastSquares();
        var dy = setMinimumDy(fit.slope() * xSpan);

        unquantedLeftY = fit.offset();
        unquantedRightY = fit.offset() + dy;
        musicalDy = dy;
    }

    /** A least-squares line through the stems' ideal points. */
    private record LeastSquaresFit(double slope, double offset) {}

    /**
     * Port of {@code minimise_least_squares} (lily/least-squares.cc) over the
     * stems' {@code (xSs, idealYUpSs)} points.
     *
     * @return the fitted slope and Y-intercept; a degenerate system yields slope
     *         0 and the mean Y (0 when there are no points)
     */
    private LeastSquaresFit minimiseLeastSquares() {
        var sx = 0.0;
        var sy = 0.0;
        var sqx = 0.0;
        var sxy = 0.0;

        for (var i = 0; i < stems.size(); i++) {
            var x = stems.get(i).xSs();
            var y = stemInfos.get(i).idealYUpSs();
            sx += x;
            sy += y;
            sqx += x * x;
            sxy += x * y;
        }

        double count = stems.size();
        var den = count * sqx - sx * sx;

        if (count == 0.0 || den == 0.0) {
            return new LeastSquaresFit(0.0, count != 0.0 ? sy / count : 0.0);
        }

        var slope = (count * sxy - sx * sy) / den;
        return new LeastSquaresFit(slope, (sy - slope * sx) / count);
    }

    /**
     * Port of {@code set_minimum_dy}: a dy smaller than the smallest quant would
     * incur absurd direction-sign penalties, so raise its magnitude to that quant.
     *
     * @param dy the raw dy
     * @return the dy, with its magnitude raised to the smallest quant offset
     */
    private static double setMinimumDy(double dy) {
        if (dy == 0.0) {
            return 0.0;
        }

        var smallestQuant = Math.min(Math.min(SIT_SS, INTER_SS), HANG_SS);
        return Math.signum(dy) * Math.max(Math.abs(dy), smallestQuant);
    }

    /**
     * Port of {@code Beam_scoring_problem::slope_damping}. The control flow of the
     * original is preserved exactly: a concave beam zeroes the damping factor,
     * which then skips the damping formula altogether.
     */
    private void slopeDamping() {
        if (stems.size() <= 1) {
            return;
        }

        var damping = DAMPING;
        var concaveness = calcConcaveness();

        if (concaveness >= CONCAVE_FORCE_FLAT) {
            unquantedLeftY = unquantedRightY;
            musicalDy = 0.0;
            damping = 0.0;
        }

        if (damping != 0.0 && damping + concaveness != 0.0) {
            var dy = unquantedRightY - unquantedLeftY;
            var slope = SLOPE_DAMPING_COEFFICIENT * Math.tanh(dy / xSpan) / (damping + concaveness);
            var dampedDy = setMinimumDy(slope * xSpan);

            unquantedLeftY += (dy - dampedDy) / 2.0;
            unquantedRightY -= (dy - dampedDy) / 2.0;
        }
    }

    /**
     * Port of {@code Beam_scoring_problem::calc_concaveness}. Single noteheads
     * make LilyPond's "close" and "far" head positions identical, so its average
     * of two {@code calc_positions_concaveness} calls collapses to one.
     *
     * @return {@link #CONCAVE_FORCE_FLAT} for a concave beam, else the normalized
     *         concaveness magnitude
     */
    private double calcConcaveness() {
        if (stems.size() <= 2) {
            return 0.0;
        }

        var positions = new int[stems.size()];

        for (var i = 0; i < stems.size(); i++) {
            positions[i] = stems.get(i).headHalfPos();
        }

        if (isConcaveSingleNotes(positions, dirSign)) {
            return CONCAVE_FORCE_FLAT;
        }

        return calcPositionsConcaveness(positions, dirSign);
    }

    /**
     * Port of {@code is_concave_single_notes}. A beam is concave when the middle
     * notes get closer to the beam than the outer notes do.
     *
     * @param positions integer Y-up half-space head positions, in stem order
     * @param beamDir   +1 for stems up, -1 for stems down
     * @return whether the beam is concave
     */
    private static boolean isConcaveSingleNotes(int[] positions, int beamDir) {
        var last = positions.length - 1;
        var coveringDown = Math.min(positions[0], positions[last]);
        var coveringUp = Math.max(positions[0], positions[last]);

        // Notes above and below the interval covered by the 1st and last note.
        var above = false;
        var below = false;

        for (var i = 1; i < last; i++) {
            above = above || positions[i] > coveringUp;
            below = below || positions[i] < coveringDown;
        }

        var concave = above && below;

        // A note as close or closer to the beam than begin and end, but reached in
        // the opposite direction from the overall last-first dy.
        var dy = positions[last] - positions[0];
        var closest = Math.max(beamDir * positions[last], beamDir * positions[0]);

        for (var i = 2; !concave && i < last; i++) {
            var innerDy = positions[i] - positions[i - 1];

            if (Integer.signum(innerDy) != Integer.signum(dy)
                && (beamDir * positions[i] >= closest || beamDir * positions[i - 1] >= closest)) {
                concave = true;
            }
        }

        var allCloser = true;

        for (var i = 1; allCloser && i < last; i++) {
            allCloser = beamDir * positions[i] > closest;
        }

        return concave || allCloser;
    }

    /**
     * Port of {@code calc_positions_concaveness}: the normalized deviation of the
     * inner notes from the line connecting the first and last note.
     *
     * @param positions integer Y-up half-space head positions, in stem order
     * @param beamDir   +1 for stems up, -1 for stems down
     * @return the normalized concaveness magnitude
     */
    private static double calcPositionsConcaveness(int[] positions, int beamDir) {
        var last = positions.length - 1;
        double dy = positions[last] - positions[0];
        var slope = dy / last;
        var concaveness = 0.0;

        for (var i = 1; i < last; i++) {
            var lineY = slope * i + positions[0];
            concaveness += Math.max(beamDir * (positions[i] - lineY), 0.0);
        }

        concaveness /= positions.length;

        // For dy = 0 the slope is 0 anyway, so the scaling hardly matters.
        if (dy != 0.0) {
            concaveness /= Math.abs(dy);
        }

        return concaveness;
    }

    /**
     * Port of {@code Beam_scoring_problem::shift_region_to_valid}, without the
     * collision minefield (beams here never collide with anything but staff
     * lines, and a minefield with no forbidden intervals returns its input). What
     * remains is the stem-length feasibility clamp: shift the beam so that no stem
     * ends up shorter than its allowed minimum, keeping the slope.
     */
    private void shiftRegionToValid() {
        if (stems.isEmpty()) {
            return;
        }

        var beamDy = unquantedRightY - unquantedLeftY;
        var slope = beamDy / xSpan;

        // Intersection over stems of the half-line of feasible left-end Y values;
        // it is bounded on the side opposite the stem direction.
        var feasibleBound = stemInfos.get(0).shortestYUpSs() - slope * stems.get(0).xSs();

        for (var i = 1; i < stems.size(); i++) {
            var leftYBound = stemInfos.get(i).shortestYUpSs() - slope * stems.get(i).xSs();
            feasibleBound = dirSign > 0
                ? Math.max(feasibleBound, leftYBound)
                : Math.min(feasibleBound, leftYBound);
        }

        var violates = dirSign > 0 ? unquantedLeftY < feasibleBound : unquantedLeftY > feasibleBound;

        if (violates) {
            // Not the bound itself: LilyPond's point_in_interval steps
            // FEASIBLE_POINT_INSET_SS into the feasible half-line, away from the
            // noteheads (which is the +dirSign side).
            unquantedLeftY = feasibleBound + dirSign * FEASIBLE_POINT_INSET_SS;
            unquantedRightY = unquantedLeftY + beamDy;
        }
    }

    // ---------------------------------------------------------------------
    // Quant candidates (port of Beam_configuration + generate_quants)
    // ---------------------------------------------------------------------

    /** One candidate beam placement and the demerits accumulated against it. */
    private static final class BeamConfiguration {

        private double leftY;
        private double rightY;
        private double demerits;

        /**
         * Port of {@code Beam_configuration::new_config}. LilyPond truncates the
         * unquanted position toward zero before adding the quant offset; Java's
         * {@code (int)} cast has exactly that behavior.
         *
         * @param unquantedLeft  the unquanted left Y
         * @param unquantedRight the unquanted right Y
         * @param offsetLeft     the left quant offset
         * @param offsetRight    the right quant offset
         * @return the candidate, seeded with a tie-breaker demerit that favors
         *         offsets closest to the unquanted position
         */
        static BeamConfiguration newConfig(
            double unquantedLeft,
            double unquantedRight,
            double offsetLeft,
            double offsetRight
        ) {
            var config = new BeamConfiguration();
            config.leftY = (int) unquantedLeft + offsetLeft;
            config.rightY = (int) unquantedRight + offsetRight;
            config.demerits = (Math.abs(offsetLeft) + Math.abs(offsetRight)) / INITIAL_DEMERIT_SCALE;
            return config;
        }

        double dy() {
            return rightY - leftY;
        }

        /**
         * @param edge {@link BeamScoring#LEFT_EDGE} or {@link BeamScoring#RIGHT_EDGE}
         * @return that edge's Y
         */
        double y(int edge) {
            return edge == LEFT_EDGE ? leftY : rightY;
        }
    }

    /** Index of the left (first stem) edge in the two-edge loops. */
    private static final int LEFT_EDGE = 0;

    /** Index of the right (last stem) edge in the two-edge loops. */
    private static final int RIGHT_EDGE = 1;

    /**
     * Port of the {@code quant_range_} setup in {@code init_instance_variables}:
     * the beam center must clear the edge stem's notehead by the beam stack's
     * height plus half a staff space.
     *
     * @param stem the first or last stem of the group
     * @return the bound on that edge's candidate Y — a lower bound for stems up,
     *         an upper bound for stems down
     */
    private double quantBound(StemInput stem) {
        var widen = QUANT_RANGE_PADDING_SS
            + (stem.beamCount() - 1) * BEAM_TRANSLATION_SS
            + BEAM_THICKNESS_SS / 2.0;
        return stem.headYUpSs() + dirSign * widen;
    }

    /**
     * @param y     a candidate edge Y
     * @param bound the bound from {@link #quantBound(StemInput)}
     * @return whether the candidate lies inside the edge's quant range
     */
    private boolean withinQuantRange(double y, double bound) {
        return dirSign > 0 ? y >= bound : y <= bound;
    }

    /**
     * Port of {@code Beam_scoring_problem::generate_quants}, without the knee and
     * collision region widening and without {@code grid_shift} (which only applies
     * to more than four beams — SongScribe caps out at three).
     *
     * @return every in-range left/right quant combination
     */
    private List<BeamConfiguration> generateQuants() {
        var baseQuants = new double[] { STRADDLE_SS, SIT_SS, INTER_SS, HANG_SS };
        var offsets = new ArrayList<Double>();

        // Asymmetric on purpose: LilyPond's loop runs i < region_size.
        for (var i = -REGION_SIZE; i < REGION_SIZE; i++) {
            for (var quant : baseQuants) {
                offsets.add(i + quant);
            }
        }

        var boundLeft = quantBound(stems.get(0));
        var boundRight = quantBound(stems.get(stems.size() - 1));
        var configs = new ArrayList<BeamConfiguration>();

        for (var offsetLeft : offsets) {
            for (var offsetRight : offsets) {
                var config = BeamConfiguration.newConfig(
                    unquantedLeftY,
                    unquantedRightY,
                    offsetLeft,
                    offsetRight
                );

                if (withinQuantRange(config.leftY, boundLeft) && withinQuantRange(config.rightY, boundRight)) {
                    configs.add(config);
                }
            }
        }

        return configs;
    }

    // ---------------------------------------------------------------------
    // Demerit scorers (ports of Beam_scoring_problem::score_*). LilyPond's
    // score_collisions is dropped: beams here only score against staff lines.
    // ---------------------------------------------------------------------

    /**
     * Port of {@code shrink_extra_weight}: takes the magnitude of {@code x}, but
     * weights it up when it is negative.
     *
     * @param x      the deviation
     * @param factor the weight applied when {@code x} is negative
     * @return the weighted magnitude
     */
    private static double shrinkExtraWeight(double x, double factor) {
        return Math.abs(x) * (x < 0.0 ? factor : 1.0);
    }

    /** Port of LilyPond's {@code my_modf}: the fractional part, always positive. */
    private static double myModf(double x) {
        return x - Math.floor(x);
    }

    /**
     * Runs every applicable scorer against a candidate.
     *
     * @param config the candidate to score
     */
    private void score(BeamConfiguration config) {
        scoreStemLengths(config);
        scoreSlopeDirection(config);
        scoreSlopeMusical(config);
        scoreSlopeIdeal(config);
        scoreHorizontalInterQuants(config);
        scoreForbiddenQuants(config);
    }

    /**
     * Port of {@code score_stem_lengths}, minus the knee branches. The whole group
     * shares one direction, so LilyPond's per-direction score/count split collapses
     * to a single running total. The {@code base_lengths_} term is structurally 0
     * here: it is the French-beaming stem-end correction, and SongScribe has no
     * French beaming (see issue #652, which reintroduces it).
     *
     * @param config the candidate to score
     */
    private void scoreStemLengths(BeamConfiguration config) {
        var total = 0.0;

        for (var i = 0; i < stems.size(); i++) {
            var x = stems.get(i).xSs();
            var currentY = config.rightY * x / xSpan + config.leftY * (xSpan - x) / xSpan;
            var info = stemInfos.get(i);

            total += STEM_LENGTH_LIMIT_PENALTY * Math.max(0.0, dirSign * (info.shortestYUpSs() - currentY));
            total += STEM_LENGTH_DEMERIT_FACTOR
                * shrinkExtraWeight(dirSign * (currentY - info.idealYUpSs()), SHRINK_EXTRA_WEIGHT_FACTOR);
        }

        config.demerits += total / Math.max(stems.size(), 1);
    }

    /**
     * Port of {@code score_slope_direction}: going against the damped slope is
     * harshly penalized, except that a flat beam is a defensible choice when the
     * damped slope was near zero anyway.
     *
     * @param config the candidate to score
     */
    private void scoreSlopeDirection(BeamConfiguration config) {
        var dy = config.dy();
        var dampedDy = unquantedRightY - unquantedLeftY;

        if (Math.signum(dampedDy) == Math.signum(dy)) {
            return;
        }

        if (dy == 0.0 && Math.abs(dampedDy / xSpan) <= ROUND_TO_ZERO_SLOPE) {
            config.demerits += HINT_DIRECTION_PENALTY;
        } else {
            config.demerits += DAMPING_DIRECTION_PENALTY;
        }
    }

    /**
     * Port of {@code score_slope_musical}: penalizes a slope steeper than the one
     * the music itself suggests.
     *
     * @param config the candidate to score
     */
    private void scoreSlopeMusical(BeamConfiguration config) {
        config.demerits += MUSICAL_DIRECTION_FACTOR
            * Math.max(0.0, Math.abs(config.dy()) - Math.abs(musicalDy));
    }

    /**
     * Port of {@code score_slope_ideal}: penalizes deviation from the damped slope.
     *
     * @param config the candidate to score
     */
    private void scoreSlopeIdeal(BeamConfiguration config) {
        var dampedDy = unquantedRightY - unquantedLeftY;
        var deviation = Math.abs(dampedDy) - Math.abs(config.dy());
        config.demerits += shrinkExtraWeight(deviation, SHRINK_EXTRA_WEIGHT_FACTOR) * IDEAL_SLOPE_FACTOR;
    }

    /**
     * Port of {@code score_horizontal_inter_quants}. For a horizontal beam inside
     * the staff, an inter quant puts a staff line in a visually wrong place, which
     * is worse than the generic forbidden-quant case.
     *
     * @param config the candidate to score
     */
    private void scoreHorizontalInterQuants(BeamConfiguration config) {
        if (config.dy() != 0.0 || Math.abs(config.leftY) >= STAFF_RADIUS_SS) {
            return;
        }

        var yshift = config.leftY - ROUND_HALF_UP_OFFSET;
        var rounded = Math.floor(yshift + ROUND_HALF_UP_OFFSET);

        if (Math.abs(rounded - yshift) < HORIZONTAL_INTER_QUANT_TOLERANCE_SS) {
            config.demerits += HORIZONTAL_INTER_QUANT_PENALTY;
        }
    }

    /**
     * Port of {@code score_forbidden_quants}. The first block penalizes staff lines
     * falling inside the white gaps between stacked beams; the second penalizes the
     * specific quants that make a secondary beam land badly.
     *
     * @param config the candidate to score
     */
    private void scoreForbiddenQuants(BeamConfiguration config) {
        var edgeBeamCountLeft = stems.get(0).beamCount();
        var edgeBeamCountRight = stems.get(stems.size() - 1).beamCount();
        var maxEdgeBeamCount = Math.max(edgeBeamCountLeft, edgeBeamCountRight);
        var extraDemerit = SECONDARY_BEAM_DEMERIT / maxEdgeBeamCount;
        var dy = config.dy();
        var demerits = 0.0;

        for (var edge = LEFT_EDGE; edge <= RIGHT_EDGE; edge++) {
            var edgeY = config.y(edge);
            var edgeBeamCount = edge == LEFT_EDGE ? edgeBeamCountLeft : edgeBeamCountRight;

            for (var j = 1; j <= edgeBeamCount; j++) {
                var gap1 = edgeY - dirSign * ((j - 1) * BEAM_TRANSLATION_SS
                    + BEAM_THICKNESS_SS / 2.0
                    - STAFF_LINE_THICKNESS_SS / FORBIDDEN_QUANT_FUDGE_FACTOR);
                var gap2 = edgeY - dirSign * (j * BEAM_TRANSLATION_SS
                    - BEAM_THICKNESS_SS / 2.0
                    + STAFF_LINE_THICKNESS_SS / FORBIDDEN_QUANT_FUDGE_FACTOR);
                var gapBottom = Math.min(gap1, gap2);
                var gapTop = Math.max(gap1, gap2);
                var gapLength = gapTop - gapBottom;

                for (var k = -STAFF_RADIUS_SS; k <= STAFF_RADIUS_SS + BEAM_EPS; k += STAFF_LINE_STEP_SS) {
                    if (k < gapBottom || k > gapTop) {
                        continue;
                    }

                    var distance = Math.min(Math.abs(gapTop - k), Math.abs(gapBottom - k));
                    demerits += extraDemerit * (FORBIDDEN_QUANT_FIXED_DEMERIT
                        + (1.0 - FORBIDDEN_QUANT_FIXED_DEMERIT) * (distance / gapLength) * 2.0);
                }
            }
        }

        if (maxEdgeBeamCount >= 2) {
            for (var edge = LEFT_EDGE; edge <= RIGHT_EDGE; edge++) {
                var edgeY = config.y(edge);
                var edgeBeamCount = edge == LEFT_EDGE ? edgeBeamCountLeft : edgeBeamCountRight;
                var fraction = myModf(edgeY);
                var wrongWay = dirSign > 0 ? dy <= BEAM_EPS : dy >= BEAM_EPS;
                var badQuant = dirSign > 0 ? SIT_SS : HANG_SS;

                if (edgeBeamCount >= 2
                    && Math.abs(edgeY - dirSign * BEAM_TRANSLATION_SS) < STAFF_RADIUS_SS + INTER_SS
                    && wrongWay
                    && Math.abs(fraction - badQuant) < BEAM_EPS) {
                    demerits += extraDemerit;
                }

                if (edgeBeamCount >= 3
                    && Math.abs(edgeY - 2.0 * dirSign * BEAM_TRANSLATION_SS) < STAFF_RADIUS_SS + INTER_SS
                    && wrongWay
                    && Math.abs(fraction - STRADDLE_SS) < BEAM_EPS) {
                    demerits += extraDemerit;
                }
            }
        }

        config.demerits += demerits;
    }

    // ---------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------

    /**
     * Solves the beam position for one beam group.
     *
     * @param stems          the group's stems, in element order, in scoring space
     * @param dirSign        +1 for stems up, -1 for stems down
     * @param forcedFraction the fraction of the group's stems whose direction is
     *                       forced (0-1)
     * @return the beam's outer-beam center Y at the first and last stem, Y-up ss
     */
    static BeamPosition solve(List<StemInput> stems, int dirSign, double forcedFraction) {
        return new BeamScoring(stems, dirSign, forcedFraction).solve();
    }

    /**
     * Runs the whole pipeline. Degenerate groups short-circuit here, which is why
     * none of the scorers or the damping/shift routines need a zero-span guard.
     *
     * @return the best-scoring beam position
     */
    private BeamPosition solve() {
        computeStemInfos();

        if (stems.size() < 2 || xSpan == 0.0) {
            var y = stemInfos.isEmpty() ? 0.0 : stemInfos.get(0).idealYUpSs();
            return new BeamPosition(y, y);
        }

        computeUnquantedPositions();

        var configs = generateQuants();

        if (configs.isEmpty()) {
            LOG.debug(
                "No viable beam quanting found; using unquanted y. stems={} xSpan={} left={} right={}",
                stems.size(),
                xSpan,
                unquantedLeftY,
                unquantedRightY
            );
            return new BeamPosition(unquantedLeftY, unquantedRightY);
        }

        var best = configs.get(0);
        score(best);

        for (var i = 1; i < configs.size(); i++) {
            var config = configs.get(i);
            score(config);

            if (config.demerits < best.demerits) {
                best = config;
            }
        }

        return new BeamPosition(best.leftY, best.rightY);
    }

    // ---------------------------------------------------------------------
    // Accessors for the pipeline state (used by later stages and by tests)
    // ---------------------------------------------------------------------

    List<StemInput> stems() {
        return stems;
    }

    int dirSign() {
        return dirSign;
    }

    double forcedFraction() {
        return forcedFraction;
    }

    int maxBeamCount() {
        return maxBeamCount;
    }

    double xSpan() {
        return xSpan;
    }

    List<StemInfo> stemInfos() {
        return stemInfos;
    }

    double unquantedLeftY() {
        return unquantedLeftY;
    }

    double unquantedRightY() {
        return unquantedRightY;
    }

    double musicalDy() {
        return musicalDy;
    }
}
