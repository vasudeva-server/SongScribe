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

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.engraving.LineThickness;
import songscribe.engraving.Staff;
import songscribe.util.LogUtils;

/**
 * Port of LilyPond's beam-positioning pipeline ({@code Beam_scoring_problem}),
 * as a pure-math, layout-state-free computation.
 *
 * <pre>
 *  SIGN CONVENTION
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
    static final double STAFF_LINE_STEP_SS = 1.0;

    /** Format used for every number in this class's debug output. */
    private static final String LOG_NUMBER_FORMAT = "%.3f";

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
    static final double FEASIBLE_POINT_INSET_SS = 2.0;

    // ---------------------------------------------------------------------
    // Geometry — matches the renderer, NOT LilyPond's raw numbers.
    // ---------------------------------------------------------------------

    /** Half-height of a 5-line staff, in staff spaces (LilyPond's staff radius). */
    static final double STAFF_RADIUS_SS = Staff.STAFF_HALF_SS;

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

    /** +1 for stems up, -1 for stems down. */
    private final int dirSign;

    /** Fraction of the group's stems whose direction is forced (0-1). */
    private final double forcedFraction;

    /** The largest beam count over all stems of the group. */
    private final int maxBeamCount;

    /** X of the last stem, relative to the first (0 for a single-column group). */
    private final double xSpan;

    // The per-stem inputs, unpacked into parallel arrays. LilyPond holds these in
    // vectors of value structs; keeping records here instead would put two levels
    // of indirection inside the scoring loops, which run once per stem per
    // candidate. The StemInput list stays the constructor's API, nothing more.

    private final int stemCount;
    private final double[] stemXs;
    private final double[] stemHeadYs;
    private final int[] stemHeadHalfPositions;

    /** Beam count of the first stem — only the edges' counts are ever scored. */
    private final int edgeBeamCountLeft;

    /** Beam count of the last stem. */
    private final int edgeBeamCountRight;

    /** Demerit charged per forbidden-quant hit, shared by both edges. */
    private final double extraDemerit;

    // Stem-info terms that depend only on the group's max beam count and forced
    // fraction, so they are the same for every stem (see calcStemInfo).

    private final double idealLengthSs;
    private final double minimumLengthSs;
    private final double idealFloorSs;
    private final double stemShortenSs;

    // ---------------------------------------------------------------------
    // Pipeline state
    // ---------------------------------------------------------------------

    private final double[] stemIdealYs;
    private final double[] stemShortestYs;
    private double unquantedLeftY;
    private double unquantedRightY;
    private double musicalDy;

    BeamScoring(List<StemInput> stems, int dirSign, double forcedFraction) {
        this.dirSign = dirSign;
        this.forcedFraction = forcedFraction;

        stemCount = stems.size();
        stemXs = new double[stemCount];
        stemHeadYs = new double[stemCount];
        stemHeadHalfPositions = new int[stemCount];
        stemIdealYs = new double[stemCount];
        stemShortestYs = new double[stemCount];

        var count = 1;

        for (var i = 0; i < stemCount; i++) {
            var stem = stems.get(i);
            stemXs[i] = stem.xSs();
            stemHeadYs[i] = stem.headYUpSs();
            stemHeadHalfPositions[i] = stem.headHalfPos();
            count = Math.max(count, stem.beamCount());
        }

        maxBeamCount = count;
        xSpan = stemCount == 0 ? 0.0 : stemXs[stemCount - 1] - stemXs[0];
        edgeBeamCountLeft = stemCount == 0 ? 1 : stems.getFirst().beamCount();
        edgeBeamCountRight = stemCount == 0 ? 1 : stems.get(stemCount - 1).beamCount();
        extraDemerit = SECONDARY_BEAM_DEMERIT / Math.max(edgeBeamCountLeft, edgeBeamCountRight);

        // Not the stem's own beam count: LilyPond feeds calc_stem_info
        // Beam::get_direction_beam_count, the group's maximum for this direction.
        // Its comment in stem.cc gives the reason — "a8[ a32] must be horizontal"
        // — and only that shared count makes the ideal ends of equal-pitch edge
        // stems agree, which is what lets least_squares_positions see dy == 0.
        var heightOfBeams = LineThickness.beamStackHeightSs(maxBeamCount);
        var halfBeamThickness = BEAM_THICKNESS_SS / 2.0;

        // Stems only extend to the center of the beam, hence the half-thickness terms.
        idealLengthSs = Math.max(
            listRef(BEAMED_LENGTHS_SS, maxBeamCount - 1) - halfBeamThickness,
            listRef(BEAMED_MINIMUM_FREE_LENGTHS_SS, maxBeamCount - 1) + heightOfBeams - halfBeamThickness
        );
        minimumLengthSs = listRef(BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS, maxBeamCount - 1)
            + heightOfBeams
            - halfBeamThickness;

        // The lowest beam of an (UP) beam must never be lower than the second staff line.
        idealFloorSs = -STAFF_LINE_STEP_SS - BEAM_THICKNESS_SS + heightOfBeams;

        // Port of Beam::calc_stem_shorten.
        stemShortenSs = listRef(BEAMED_STEM_SHORTEN_SS, maxBeamCount - 1) * forcedFraction;
    }

    // ---------------------------------------------------------------------
    // Stem info (port of Stem::calc_stem_info, lily/stem.cc)
    // ---------------------------------------------------------------------

    /** Computes and caches the ideal and shortest stem ends for every stem. */
    void computeStemInfos() {
        for (var i = 0; i < stemCount; i++) {
            var info = calcStemInfo(stemHeadYs[i]);
            stemIdealYs[i] = info.idealYUpSs();
            stemShortestYs[i] = info.shortestYUpSs();
        }
    }

    /**
     * Port of {@code Stem::calc_stem_info} with {@code staff_space = 1},
     * {@code length-fraction = 1}, no tremolo, no {@code no-stem-extend} and no
     * knee. LilyPond computes in "direction-multiplied" space (as if the stem
     * pointed up) and multiplies by the direction only at the end; this port
     * keeps that structure so the clamps read the same as the original.
     *
     * <p>Every term but {@code noteStart} is fixed for the whole group and is
     * computed once in the constructor.
     *
     * @param stem the stem to measure
     * @return the stem's ideal and shortest beam-side ends, in real Y-up ss
     */
    StemInfo calcStemInfo(StemInput stem) {
        return calcStemInfo(stem.headYUpSs());
    }

    /**
     * @param headYUpSs the notehead's Y-up position in staff spaces
     * @return the stem's ideal and shortest beam-side ends, in real Y-up ss
     */
    private StemInfo calcStemInfo(double headYUpSs) {
        // Direction-multiplied space: positive is "away from the notehead".
        var noteStart = headYUpSs * dirSign;
        var idealY = noteStart + idealLengthSs;

        // The highest beam of an (UP) beam must never be lower than the middle staff line…
        idealY = Math.max(idealY, 0.0);
        // …and its lowest beam must never be lower than the second staff line.
        idealY = Math.max(idealY, idealFloorSs);

        idealY -= stemShortenSs;

        return new StemInfo(idealY * dirSign, (noteStart + minimumLengthSs) * dirSign);
    }

    /** Port of LilyPond's {@code robust_list_ref}: clamps the index to the last entry. */
    private static double listRef(double[] values, int index) {
        return values[Math.clamp(index, 0, values.length - 1)];
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
        logStage("leastSquares");
        slopeDamping();
        logStage("slopeDamping");
        shiftRegionToValid();
        logStage("shiftRegionToValid");
    }

    /**
     * Logs the unquanted position after one pipeline stage.
     *
     * @param stage the name of the stage that just ran
     */
    private void logStage(String stage) {
        if (!LogUtils.isTracingBeams(LOG)) {
            return;
        }

        var dy = unquantedRightY - unquantedLeftY;
        LOG.debug(
            "  after {}: left={} right={} dy={} slope={} musicalDy={}",
            stage,
            fmt(unquantedLeftY),
            fmt(unquantedRightY),
            fmt(dy),
            fmt(dy / xSpan),
            fmt(musicalDy)
        );
    }

    /**
     * @param value a number destined for debug output
     * @return the value formatted to a readable number of decimals
     */
    private static String fmt(double value) {
        return String.format(LOG_NUMBER_FORMAT, value);
    }

    /**
     * Port of {@code Beam_scoring_problem::least_squares_positions}. Every stem
     * is "normal" here, so the first/last normal stems are simply the first and
     * last, and {@code stem_ypositions_} is uniformly 0 (single staff).
     */
    private void leastSquaresPositions() {
        if (stemCount == 0) {
            return;
        }

        var idealLeft = stemIdealYs[0];
        var idealRight = stemIdealYs[stemCount - 1];

        if (idealRight - idealLeft == 0.0) {
            var chordDy = stemHeadYs[stemCount - 1] - stemHeadYs[0];

            // Simple two-stem beams whose stems both reach the middle line get an
            // artificial slope, since the ideal positions cannot express one.
            if (idealLeft == 0.0 && stemCount == 2 && chordDy != 0.0) {
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

        for (var i = 0; i < stemCount; i++) {
            var stemX = stemXs[i];
            var idealY = stemIdealYs[i];
            sx += stemX;
            sy += idealY;
            sqx += stemX * stemX;
            sxy += stemX * idealY;
        }

        double count = stemCount;
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
        if (stemCount <= 1) {
            return;
        }

        var damping = DAMPING;
        var concaveness = calcConcaveness();
        var forcedFlat = concaveness >= CONCAVE_FORCE_FLAT;

        if (forcedFlat) {
            unquantedLeftY = unquantedRightY;
            musicalDy = 0.0;
            damping = 0.0;
        }

        if (LogUtils.isTracingBeams(LOG)) {
            LOG.debug(
                "  concaveness={} forcedFlat={} (>= {} forces flat)",
                fmt(concaveness),
                forcedFlat,
                fmt(CONCAVE_FORCE_FLAT)
            );
        }

        if (damping != 0.0 && damping + concaveness != 0.0) {
            var dy = unquantedRightY - unquantedLeftY;
            var slope = SLOPE_DAMPING_COEFFICIENT * Math.tanh(dy / xSpan) / (damping + concaveness);
            var dampedDy = setMinimumDy(slope * xSpan);

            unquantedLeftY += (dy - dampedDy) / 2.0;
            unquantedRightY -= (dy - dampedDy) / 2.0;

            if (LogUtils.isTracingBeams(LOG)) {
                LOG.debug("  damping: dy {} -> {}", fmt(dy), fmt(dampedDy));
            }
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
        if (stemCount <= 2) {
            return 0.0;
        }

        var singleNotesConcave = isConcaveSingleNotes(stemHeadHalfPositions, dirSign);

        if (LogUtils.isTracingBeams(LOG)) {
            LOG.debug(
                "  headPositions={} isConcaveSingleNotes={}",
                Arrays.toString(stemHeadHalfPositions),
                singleNotesConcave
            );
        }

        if (singleNotesConcave) {
            return CONCAVE_FORCE_FLAT;
        }

        return calcPositionsConcaveness(stemHeadHalfPositions, dirSign);
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

        if (above && below) {
            return true;
        }

        // A note as close or closer to the beam than begin and end, but reached in
        // the opposite direction from the overall last-first dy.
        var dy = positions[last] - positions[0];
        var closest = Math.max(beamDir * positions[last], beamDir * positions[0]);

        for (var i = 2; i < last; i++) {
            var innerDy = positions[i] - positions[i - 1];

            if (Integer.signum(innerDy) != Integer.signum(dy)
                && (beamDir * positions[i] >= closest || beamDir * positions[i - 1] >= closest)) {
                return true;
            }
        }

        var allCloser = true;

        for (var i = 1; allCloser && i < last; i++) {
            allCloser = beamDir * positions[i] > closest;
        }

        return allCloser;
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
        if (stemCount == 0) {
            return;
        }

        var beamDy = unquantedRightY - unquantedLeftY;
        var slope = beamDy / xSpan;

        // Intersection over stems of the half-line of feasible left-end Y values;
        // it is bounded on the side opposite the stem direction.
        var feasibleBound = stemShortestYs[0] - slope * stemXs[0];

        for (var i = 1; i < stemCount; i++) {
            var leftYBound = stemShortestYs[i] - slope * stemXs[i];
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

    /**
     * The per-scorer demerit contributions for one candidate, kept apart from the
     * running total so debug output can attribute a candidate's score to the
     * scorer responsible for it.
     *
     * @param initial         the seed tie-breaker from {@code newConfig}
     * @param stemLengths     {@link #scoreStemLengths}
     * @param slopeDirection  {@link #scoreSlopeDirection}
     * @param slopeMusical    {@link #scoreSlopeMusical}
     * @param slopeIdeal      {@link #scoreSlopeIdeal}
     * @param horizontalInter {@link #scoreHorizontalInterQuants}
     * @param forbiddenQuants {@link #scoreForbiddenQuants}
     */
    private record Demerits(
        double initial,
        double stemLengths,
        double slopeDirection,
        double slopeMusical,
        double slopeIdeal,
        double horizontalInter,
        double forbiddenQuants
    ) {

        double total() {
            return initial
                + stemLengths
                + slopeDirection
                + slopeMusical
                + slopeIdeal
                + horizontalInter
                + forbiddenQuants;
        }

        @Override
        public String toString() {
            var format = "stemLen=" + LOG_NUMBER_FORMAT
                + " slopeDir=" + LOG_NUMBER_FORMAT
                + " slopeMus=" + LOG_NUMBER_FORMAT
                + " slopeIdeal=" + LOG_NUMBER_FORMAT
                + " horizInter=" + LOG_NUMBER_FORMAT
                + " forbidden=" + LOG_NUMBER_FORMAT
                + " initial=" + LOG_NUMBER_FORMAT;

            return String.format(
                format,
                stemLengths,
                slopeDirection,
                slopeMusical,
                slopeIdeal,
                horizontalInter,
                forbiddenQuants,
                initial
            );
        }
    }

    /**
     * One candidate beam placement and the demerits scored against it. Only the
     * running best (and, under debug logging, the best flat candidate) is ever
     * materialized — see {@link #chooseBestConfiguration()}.
     *
     * @param leftY    the beam center Y at the first stem
     * @param rightY   the beam center Y at the last stem
     * @param initial  the seed tie-breaker demerit, kept for debug attribution
     * @param demerits the total demerits scored against this placement
     */
    private record Candidate(double leftY, double rightY, double initial, double demerits) {

        double dy() {
            return rightY - leftY;
        }
    }

    /**
     * Port of the {@code quant_range_} setup in {@code init_instance_variables}:
     * the beam center must clear the edge stem's notehead by the beam stack's
     * height plus half a staff space.
     *
     * @param headYUpSs the edge stem's notehead Y-up position
     * @param beamCount the edge stem's beam count
     * @return the bound on that edge's candidate Y — a lower bound for stems up,
     *         an upper bound for stems down
     */
    double quantBound(double headYUpSs, int beamCount) {
        var widen = QUANT_RANGE_PADDING_SS
            + (beamCount - 1) * BEAM_TRANSLATION_SS
            + BEAM_THICKNESS_SS / 2.0;
        return headYUpSs + dirSign * widen;
    }

    /**
     * @param y     a candidate edge Y
     * @param bound the bound from {@link #quantBound(double, int)}
     * @return whether the candidate lies inside the edge's quant range
     */
    boolean withinQuantRange(double y, double bound) {
        return dirSign > 0 ? y >= bound : y <= bound;
    }

    /**
     * Port of {@code Beam_scoring_problem::generate_quants}, without the knee and
     * collision region widening and without {@code grid_shift} (which only applies
     * to more than four beams — SongScribe caps out at three).
     *
     * @return the quant offset applied to each edge, one per candidate position
     */
    private static double[] quantOffsets() {
        var baseQuants = new double[] { STRADDLE_SS, SIT_SS, INTER_SS, HANG_SS };
        var offsets = new double[REGION_SIZE * 2 * baseQuants.length];
        var next = 0;

        // Asymmetric on purpose: LilyPond's loop runs i < region_size.
        for (var i = -REGION_SIZE; i < REGION_SIZE; i++) {
            for (var quant : baseQuants) {
                offsets[next] = i + quant;
                next++;
            }
        }

        return offsets;
    }

    /**
     * Generates and scores every in-range left/right quant combination, keeping
     * only the best. LilyPond materializes the whole candidate list and sorts it
     * lazily; nothing here needs a candidate after a better one is found, so
     * generation and scoring are fused and only an improvement allocates.
     *
     * @return the lowest-demerit candidate, or null when every combination fell
     *         outside the edges' quant ranges
     */
    @Nullable
    private Candidate chooseBestConfiguration() {
        var offsets = quantOffsets();
        var boundLeft = quantBound(stemHeadYs[0], edgeBeamCountLeft);
        var boundRight = quantBound(stemHeadYs[stemCount - 1], edgeBeamCountRight);

        // LilyPond truncates the unquanted position toward zero before adding the
        // quant offset; Java's (int) cast has exactly that behavior.
        var baseLeft = (int) unquantedLeftY;
        var baseRight = (int) unquantedRightY;
        var debug = LogUtils.isTracingBeams(LOG);

        Candidate best = null;
        Candidate bestFlat = null;
        var considered = 0;

        for (var offsetLeft : offsets) {
            var leftY = baseLeft + offsetLeft;

            if (!withinQuantRange(leftY, boundLeft)) {
                continue;
            }

            for (var offsetRight : offsets) {
                var rightY = baseRight + offsetRight;

                if (!withinQuantRange(rightY, boundRight)) {
                    continue;
                }

                considered++;

                // Favors the offsets closest to the unquanted position.
                var initial = (Math.abs(offsetLeft) + Math.abs(offsetRight)) / INITIAL_DEMERIT_SCALE;
                var demerits = score(leftY, rightY, initial);

                if (best == null || demerits < best.demerits()) {
                    best = new Candidate(leftY, rightY, initial, demerits);
                }

                if (debug
                    && Math.abs(rightY - leftY) < BEAM_EPS
                    && (bestFlat == null || demerits < bestFlat.demerits())) {
                    bestFlat = new Candidate(leftY, rightY, initial, demerits);
                }
            }
        }

        if (best != null) {
            logChoice(considered, best, bestFlat);
        }

        return best;
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
     * Runs every applicable scorer against a candidate placement.
     *
     * @param leftY   the beam center Y at the first stem
     * @param rightY  the beam center Y at the last stem
     * @param initial the seed tie-breaker demerit
     * @return the candidate's total demerits
     */
    private double score(double leftY, double rightY, double initial) {
        return initial
            + scoreStemLengths(leftY, rightY)
            + scoreSlopeDirection(leftY, rightY)
            + scoreSlopeMusical(leftY, rightY)
            + scoreSlopeIdeal(leftY, rightY)
            + scoreHorizontalInterQuants(leftY, rightY)
            + scoreForbiddenQuants(leftY, rightY);
    }

    /**
     * Re-runs the scorers to attribute a candidate's total to the scorer
     * responsible for it. Only the one or two candidates that reach debug output
     * are broken down this way, so the repeated scoring costs nothing in the
     * normal case.
     *
     * @param candidate the candidate to describe
     * @return the per-scorer contributions behind its total
     */
    private Demerits breakdownOf(Candidate candidate) {
        var leftY = candidate.leftY();
        var rightY = candidate.rightY();
        return new Demerits(
            candidate.initial(),
            scoreStemLengths(leftY, rightY),
            scoreSlopeDirection(leftY, rightY),
            scoreSlopeMusical(leftY, rightY),
            scoreSlopeIdeal(leftY, rightY),
            scoreHorizontalInterQuants(leftY, rightY),
            scoreForbiddenQuants(leftY, rightY)
        );
    }

    /**
     * Port of {@code score_stem_lengths}, minus the knee branches. The whole group
     * shares one direction, so LilyPond's per-direction score/count split collapses
     * to a single running total. The {@code base_lengths_} term is structurally 0
     * here: it is the offset from the beam position to the beam rank a stem
     * terminates at, and every SongScribe stem terminates at the outer beam.
     * French beaming (issue #652) does not change that — it shortens inner stems
     * at drawing time only, and LilyPond likewise excludes it from quanting
     * ("French Beaming is irrelevant for beam quanting", {@code beam-quanting.cc}).
     *
     * @param leftY  the beam center Y at the first stem
     * @param rightY the beam center Y at the last stem
     * @return the demerits charged
     */
    double scoreStemLengths(double leftY, double rightY) {
        var total = 0.0;

        for (var i = 0; i < stemCount; i++) {
            var stemX = stemXs[i];
            var currentY = rightY * stemX / xSpan + leftY * (xSpan - stemX) / xSpan;

            total += STEM_LENGTH_LIMIT_PENALTY * Math.max(0.0, dirSign * (stemShortestYs[i] - currentY));
            total += STEM_LENGTH_DEMERIT_FACTOR
                * shrinkExtraWeight(dirSign * (currentY - stemIdealYs[i]), SHRINK_EXTRA_WEIGHT_FACTOR);
        }

        return total / stemCount;
    }

    /**
     * Port of {@code score_slope_direction}: going against the damped slope is
     * harshly penalized, except that a flat beam is a defensible choice when the
     * damped slope was near zero anyway.
     *
     * @param leftY  the beam center Y at the first stem
     * @param rightY the beam center Y at the last stem
     * @return the demerits charged
     */
    double scoreSlopeDirection(double leftY, double rightY) {
        var dy = rightY - leftY;
        var dampedDy = unquantedRightY - unquantedLeftY;

        if (Math.signum(dampedDy) == Math.signum(dy)) {
            return 0.0;
        }

        if (dy == 0.0 && Math.abs(dampedDy / xSpan) <= ROUND_TO_ZERO_SLOPE) {
            return HINT_DIRECTION_PENALTY;
        }

        return DAMPING_DIRECTION_PENALTY;
    }

    /**
     * Port of {@code score_slope_musical}: penalizes a slope steeper than the one
     * the music itself suggests.
     *
     * @param leftY  the beam center Y at the first stem
     * @param rightY the beam center Y at the last stem
     * @return the demerits charged
     */
    double scoreSlopeMusical(double leftY, double rightY) {
        return MUSICAL_DIRECTION_FACTOR
            * Math.max(0.0, Math.abs(rightY - leftY) - Math.abs(musicalDy));
    }

    /**
     * Port of {@code score_slope_ideal}: penalizes deviation from the damped slope.
     *
     * @param leftY  the beam center Y at the first stem
     * @param rightY the beam center Y at the last stem
     * @return the demerits charged
     */
    double scoreSlopeIdeal(double leftY, double rightY) {
        var dampedDy = unquantedRightY - unquantedLeftY;
        var deviation = Math.abs(dampedDy) - Math.abs(rightY - leftY);
        return shrinkExtraWeight(deviation, SHRINK_EXTRA_WEIGHT_FACTOR) * IDEAL_SLOPE_FACTOR;
    }

    /**
     * Port of {@code score_horizontal_inter_quants}. For a horizontal beam inside
     * the staff, an inter quant puts a staff line in a visually wrong place, which
     * is worse than the generic forbidden-quant case.
     *
     * @param leftY  the beam center Y at the first stem
     * @param rightY the beam center Y at the last stem
     * @return the demerits charged
     */
    double scoreHorizontalInterQuants(double leftY, double rightY) {
        if (rightY - leftY != 0.0 || Math.abs(leftY) >= STAFF_RADIUS_SS) {
            return 0.0;
        }

        var yshift = leftY - ROUND_HALF_UP_OFFSET;
        var rounded = Math.floor(yshift + ROUND_HALF_UP_OFFSET);

        if (Math.abs(rounded - yshift) < HORIZONTAL_INTER_QUANT_TOLERANCE_SS) {
            return HORIZONTAL_INTER_QUANT_PENALTY;
        }

        return 0.0;
    }

    /**
     * Port of {@code score_forbidden_quants}. The first block penalizes staff lines
     * falling inside the white gaps between stacked beams; the second penalizes the
     * specific quants that make a secondary beam land badly.
     *
     * @param leftY  the beam center Y at the first stem
     * @param rightY the beam center Y at the last stem
     * @return the demerits charged
     */
    double scoreForbiddenQuants(double leftY, double rightY) {
        var dy = rightY - leftY;
        var demerits = staffLinesInBeamGaps(leftY, edgeBeamCountLeft)
            + staffLinesInBeamGaps(rightY, edgeBeamCountRight);

        if (Math.max(edgeBeamCountLeft, edgeBeamCountRight) >= 2) {
            demerits += badSecondaryQuant(leftY, edgeBeamCountLeft, dy)
                + badSecondaryQuant(rightY, edgeBeamCountRight, dy);
        }

        return demerits;
    }

    /**
     * First block of {@code score_forbidden_quants}: a staff line falling inside
     * one of the white gaps between this edge's stacked beams reads as a printing
     * error, and the closer it sits to the middle of the gap the worse it looks.
     *
     * @param edgeY         the beam center Y at this edge
     * @param edgeBeamCount how many beams reach this edge
     * @return the demerits charged
     */
    double staffLinesInBeamGaps(double edgeY, int edgeBeamCount) {
        var demerits = 0.0;

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

        return demerits;
    }

    /**
     * Second block of {@code score_forbidden_quants}: with the beam sloping the
     * wrong way, a secondary beam landing on {@code sit} (up) or {@code hang}
     * (down) — or, with three beams, on {@code straddle} — puts the inner beam in
     * a bad place against the staff.
     *
     * @param edgeY         the beam center Y at this edge
     * @param edgeBeamCount how many beams reach this edge
     * @param dy            the candidate's slope, as a rise over the group
     * @return the demerits charged
     */
    double badSecondaryQuant(double edgeY, int edgeBeamCount, double dy) {
        var fraction = myModf(edgeY);
        var wrongWay = dirSign > 0 ? dy <= BEAM_EPS : dy >= BEAM_EPS;
        var badQuant = dirSign > 0 ? SIT_SS : HANG_SS;
        var demerits = 0.0;

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

        return demerits;
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
        logInputs();

        if (stemCount < 2 || xSpan == 0.0) {
            var y = stemCount == 0 ? 0.0 : stemIdealYs[0];
            return new BeamPosition(y, y);
        }

        computeUnquantedPositions();

        var best = chooseBestConfiguration();

        if (best == null) {
            if (LogUtils.isTracingBeams(LOG)) {
                LOG.debug(
                    "No viable beam quanting found; using unquanted y. stems={} xSpan={} left={} right={}",
                    stemCount,
                    xSpan,
                    unquantedLeftY,
                    unquantedRightY
                );
            }

            return new BeamPosition(unquantedLeftY, unquantedRightY);
        }

        return new BeamPosition(best.leftY(), best.rightY());
    }

    // ---------------------------------------------------------------------
    // Debug output
    // ---------------------------------------------------------------------

    /** Logs the group's inputs and the stem info derived from them. */
    private void logInputs() {
        if (!LogUtils.isTracingBeams(LOG)) {
            return;
        }

        LOG.debug(
            "beam group: stems={} dir={} forcedFraction={} maxBeamCount={} xSpan={}",
            stemCount,
            dirSign > 0 ? "up" : "down",
            fmt(forcedFraction),
            maxBeamCount,
            fmt(xSpan)
        );

        for (var i = 0; i < stemCount; i++) {
            LOG.debug(
                "  stem[{}] x={} headPos={} headY={} ideal={} shortest={}",
                i,
                fmt(stemXs[i]),
                stemHeadHalfPositions[i],
                fmt(stemHeadYs[i]),
                fmt(stemIdealYs[i]),
                fmt(stemShortestYs[i])
            );
        }
    }

    /**
     * Logs the winning candidate alongside the best flat candidate, so a beam that
     * came out angled can be compared scorer-by-scorer against the flat option that
     * lost to it.
     *
     * @param considered how many candidates were scored
     * @param best       the winner
     * @param bestFlat   the best flat candidate, or null if none was in range
     */
    private void logChoice(int considered, Candidate best, @Nullable Candidate bestFlat) {
        if (!LogUtils.isTracingBeams(LOG)) {
            return;
        }

        LOG.debug("  candidates={}", considered);
        logCandidate("winner", best);

        if (bestFlat == null) {
            LOG.debug("  best flat: none in range");
        } else if (bestFlat != best) {
            logCandidate("best flat", bestFlat);
        }
    }

    /**
     * @param label     what the candidate is (winner, best flat, …)
     * @param candidate the candidate to describe
     */
    private void logCandidate(String label, Candidate candidate) {
        LOG.debug(
            "  {}: left={} right={} dy={} demerits={} [{}]",
            label,
            fmt(candidate.leftY()),
            fmt(candidate.rightY()),
            fmt(candidate.dy()),
            fmt(candidate.demerits()),
            breakdownOf(candidate)
        );
    }
}
