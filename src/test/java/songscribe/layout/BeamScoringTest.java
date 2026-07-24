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
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Guards the Y-sign boundary of {@link BeamScoring}: inside the class everything
 * is Y-up positive, so a beam-side stem end must be more positive than its
 * notehead for stems-up and more negative for stems-down. A transposed sign here
 * would silently produce a wrong-looking beam rather than an error.
 */
class BeamScoringTest extends UnitTest {

    private static final int DIR_UP = 1;
    private static final int DIR_DOWN = -1;

    /** No stem in the group has a forced direction, so no shortening applies. */
    private static final double NO_FORCED_STEMS = 0.0;

    private static final int EIGHTH_NOTE_BEAM_COUNT = 1;

    // A head one staff space below the middle line: staffPosition = +2 in
    // SongScribe's Y-down half-space units, hence -1.0 ss Y-up.
    private static final double HEAD_BELOW_MIDDLE_Y_UP_SS = -1.0;
    private static final int HEAD_BELOW_MIDDLE_HALF_POS = -2;

    // The mirrored head, one staff space above the middle line.
    private static final double HEAD_ABOVE_MIDDLE_Y_UP_SS = 1.0;
    private static final int HEAD_ABOVE_MIDDLE_HALF_POS = 2;

    // A single-beam stem length is ~3 ss; these bounds catch a magnitude that is
    // nonsense (collapsed to zero, doubled) without restating the formula.
    private static final double MIN_PLAUSIBLE_STEM_LENGTH_SS = 2.5;
    private static final double MAX_PLAUSIBLE_STEM_LENGTH_SS = 4.0;

    private static final double FIRST_STEM_X_SS = 0.0;

    /** Typical eighth-note column spacing, in staff spaces. */
    private static final double STEM_SPACING_SS = 3.0;

    // A concave stems-up triplet: the middle head is far toward the beam side,
    // while the outer heads sit low, so the beam must be forced flat.
    private static final int[] CONCAVE_HALF_POSITIONS = { -4, 2, -2 };

    // A monotonically ascending stems-up triplet: no concaveness, so the beam
    // must keep a positive (Y-up) slope.
    private static final int[] ASCENDING_HALF_POSITIONS = { -6, -4, -2 };

    // LilyPond's `\relative c'' { g16[ d'16 16 g,8] }`: G4 D5 D5 G4, stems down.
    // The edge heads share a pitch but not a beam count, which is what makes this
    // a regression guard — see testMixedBeamCountsWithEqualEdgeHeadsIsFlat.
    private static final int[] MIXED_COUNT_HALF_POSITIONS = { -2, 2, 2, -2 };
    private static final int[] MIXED_COUNT_BEAM_COUNTS = { 2, 2, 2, 1 };

    /** The two G4s are stems-down against their default, the two D5s are not. */
    private static final double HALF_THE_STEMS_FORCED = 0.5;

    /** Every stem of the group is forced against its natural direction. */
    private static final double ALL_STEMS_FORCED = 1.0;

    /** LilyPond 2.26 reports {@code Beam.positions = (-4.0 . -4.0)} for that group. */
    private static final double LILYPOND_FLAT_BEAM_Y_UP_SS = -4.0;

    private static final int SIXTEENTH_NOTE_BEAM_COUNT = 2;

    // Concave trigger (a), `is_concave_single_notes` above && below: inner heads
    // reach past the interval covered by the first and last head on both sides.
    private static final int[] CONCAVE_COVERING_HALF_POSITIONS = { 0, 4, -4, 1 };

    // Concave trigger (b), beam-quanting.cc:645-655: an inner step runs against
    // the group's overall direction from a head already at the closest edge.
    private static final int[] CONCAVE_OPPOSITE_TREND_HALF_POSITIONS = { -6, -2, -4, -2 };

    // Concave trigger (c): every inner head is strictly closer to the beam than
    // either edge head is.
    private static final int[] CONCAVE_ALL_INNER_CLOSER_HALF_POSITIONS = { -6, -2, -3, -7 };

    // Both heads sit far enough below the staff that their ideal stem ends clamp
    // to the middle line, which is what triggers the artificial-slope branch.
    private static final int[] CLAMPED_DESCENDING_PAIR_HALF_POSITIONS = { -8, -10 };
    private static final int[] CLAMPED_ASCENDING_PAIR_HALF_POSITIONS = { -10, -8 };

    // A contour steep enough that the least-squares slope would push the low-side
    // stems below their minimum length if nothing held them up.
    private static final int[] STEEP_ASCENDING_HALF_POSITIONS = { -12, -6, 0 };

    /** Contours whose solved edges must all land on quants, exercising several code paths. */
    private static final int[][] REPRESENTATIVE_CONTOURS = {
        CONCAVE_HALF_POSITIONS,
        ASCENDING_HALF_POSITIONS,
        STEEP_ASCENDING_HALF_POSITIONS,
        CLAMPED_DESCENDING_PAIR_HALF_POSITIONS
    };

    /**
     * The quanting soft floor is enforced by a demerit, not a hard clamp, so a
     * solved stem may fall this far below its minimum length before the test calls
     * it a regression.
     */
    private static final double STEM_LENGTH_FLOOR_TOLERANCE_SS = 0.05;

    private static List<BeamScoring.StemInput> stemsFor(int[] halfPositions, int beamCount) {
        var stems = new ArrayList<BeamScoring.StemInput>(halfPositions.length);

        for (var i = 0; i < halfPositions.length; i++) {
            stems.add(new BeamScoring.StemInput(
                FIRST_STEM_X_SS + i * STEM_SPACING_SS,
                halfPositions[i] / 2.0,
                halfPositions[i],
                beamCount
            ));
        }

        return stems;
    }

    private static BeamScoring scoringFor(int[] halfPositions, int dirSign) {
        return scoringFor(halfPositions, dirSign, EIGHTH_NOTE_BEAM_COUNT, NO_FORCED_STEMS);
    }

    private static BeamScoring scoringFor(
        int[] halfPositions,
        int dirSign,
        int beamCount,
        double forcedFraction
    ) {
        var scoring = new BeamScoring(stemsFor(halfPositions, beamCount), dirSign, forcedFraction);
        scoring.computeStemInfos();
        scoring.computeUnquantedPositions();
        return scoring;
    }

    /** Port of the scorer's {@code myModf}, so the test reads quants the way solve() writes them. */
    private static double fractionOf(double y) {
        return y - Math.floor(y);
    }

    /**
     * @param y a solved beam edge Y
     * @return how far that edge is from the nearest straddle/sit/inter/hang quant
     */
    private static double distanceToNearestQuant(double y) {
        var fraction = fractionOf(y);
        var quants = new double[] {
            BeamScoring.STRADDLE_SS,
            BeamScoring.SIT_SS,
            BeamScoring.INTER_SS,
            BeamScoring.HANG_SS,
            // Straddle again, wrapped around the top of the fractional range.
            1.0 + BeamScoring.STRADDLE_SS
        };
        var distance = Double.MAX_VALUE;

        for (var quant : quants) {
            distance = Math.min(distance, Math.abs(fraction - quant));
        }

        return distance;
    }

    @Test
    void testStemsUpStemInfoEndsAboveTheNotehead() {
        var stem = new BeamScoring.StemInput(
            FIRST_STEM_X_SS,
            HEAD_BELOW_MIDDLE_Y_UP_SS,
            HEAD_BELOW_MIDDLE_HALF_POS,
            EIGHTH_NOTE_BEAM_COUNT
        );
        var scoring = new BeamScoring(List.of(stem), DIR_UP, NO_FORCED_STEMS);

        var info = scoring.calcStemInfo(stem);

        assertAll(
            () -> assertThat(info.idealYUpSs())
                .as("ideal stem end is above the head (more positive Y-up)")
                .isGreaterThan(HEAD_BELOW_MIDDLE_Y_UP_SS),
            () -> assertThat(info.shortestYUpSs())
                .as("shortest stem end is still above the head")
                .isGreaterThan(HEAD_BELOW_MIDDLE_Y_UP_SS),
            () -> assertThat(info.idealYUpSs())
                .as("the ideal stem is no shorter than the shortest allowed stem")
                .isGreaterThanOrEqualTo(info.shortestYUpSs()),
            () -> assertThat(info.idealYUpSs() - HEAD_BELOW_MIDDLE_Y_UP_SS)
                .as("ideal beamed stem length")
                .isBetween(MIN_PLAUSIBLE_STEM_LENGTH_SS, MAX_PLAUSIBLE_STEM_LENGTH_SS)
        );
    }

    @Test
    void testStemsDownStemInfoEndsBelowTheNotehead() {
        var stem = new BeamScoring.StemInput(
            FIRST_STEM_X_SS,
            HEAD_ABOVE_MIDDLE_Y_UP_SS,
            HEAD_ABOVE_MIDDLE_HALF_POS,
            EIGHTH_NOTE_BEAM_COUNT
        );
        var scoring = new BeamScoring(List.of(stem), DIR_DOWN, NO_FORCED_STEMS);

        var info = scoring.calcStemInfo(stem);

        assertAll(
            () -> assertThat(info.idealYUpSs())
                .as("ideal stem end is below the head (more negative Y-up)")
                .isLessThan(HEAD_ABOVE_MIDDLE_Y_UP_SS),
            () -> assertThat(info.shortestYUpSs())
                .as("shortest stem end is still below the head")
                .isLessThan(HEAD_ABOVE_MIDDLE_Y_UP_SS),
            () -> assertThat(info.idealYUpSs())
                .as("the ideal stem is no shorter than the shortest allowed stem")
                .isLessThanOrEqualTo(info.shortestYUpSs()),
            () -> assertThat(HEAD_ABOVE_MIDDLE_Y_UP_SS - info.idealYUpSs())
                .as("ideal beamed stem length")
                .isBetween(MIN_PLAUSIBLE_STEM_LENGTH_SS, MAX_PLAUSIBLE_STEM_LENGTH_SS)
        );
    }

    @Test
    void testConcaveGroupIsForcedFlat() {
        var scoring = scoringFor(CONCAVE_HALF_POSITIONS, DIR_UP);

        assertAll(
            () -> assertThat(scoring.unquantedRightY())
                .as("a concave beam is forced flat")
                .isCloseTo(scoring.unquantedLeftY(), within(BeamScoring.BEAM_EPS)),
            () -> assertThat(scoring.musicalDy())
                .as("a forced-flat beam has no musical dy")
                .isCloseTo(0.0, within(BeamScoring.BEAM_EPS))
        );
    }

    @Test
    void testAscendingGroupKeepsAnAscendingSlope() {
        var scoring = scoringFor(ASCENDING_HALF_POSITIONS, DIR_UP);

        assertThat(scoring.unquantedRightY() - scoring.unquantedLeftY())
            .as("ascending heads give a beam rising in Y-up space")
            .isGreaterThan(BeamScoring.BEAM_EPS);
    }

    /**
     * Stem info must be derived from the group's maximum beam count, not each
     * stem's own, exactly as {@code Stem::calc_stem_info} does via
     * {@code Beam::get_direction_beam_count}. Per-stem counts give the two edge
     * stems different ideal ends despite identical heads, which hides the
     * {@code dy == 0} case from {@code least_squares_positions} and tilts a beam
     * LilyPond draws flat.
     */
    @Test
    void testMixedBeamCountsWithEqualEdgeHeadsIsFlat() {
        var stems = new ArrayList<BeamScoring.StemInput>(MIXED_COUNT_HALF_POSITIONS.length);

        for (var i = 0; i < MIXED_COUNT_HALF_POSITIONS.length; i++) {
            stems.add(new BeamScoring.StemInput(
                FIRST_STEM_X_SS + i * STEM_SPACING_SS,
                MIXED_COUNT_HALF_POSITIONS[i] / 2.0,
                MIXED_COUNT_HALF_POSITIONS[i],
                MIXED_COUNT_BEAM_COUNTS[i]
            ));
        }

        var position = BeamScoring.solve(stems, DIR_DOWN, HALF_THE_STEMS_FORCED);

        assertAll(
            () -> assertThat(position.rightYUpSs())
                .as("equal edge heads give a flat beam")
                .isCloseTo(position.leftYUpSs(), within(BeamScoring.BEAM_EPS)),
            () -> assertThat(position.leftYUpSs())
                .as("beam sits where LilyPond puts it")
                .isCloseTo(LILYPOND_FLAT_BEAM_Y_UP_SS, within(BeamScoring.BEAM_EPS))
        );
    }

    /** The forced-flat unquanted position must survive quanting, not be tilted back. */
    @Test
    void testConcaveGroupSolvesFlat() {
        var position = BeamScoring.solve(
            stemsFor(CONCAVE_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

        assertThat(position.rightYUpSs())
            .as("a concave group keeps its forced-flat beam through quanting")
            .isCloseTo(position.leftYUpSs(), within(BeamScoring.BEAM_EPS));
    }

    @Test
    void testAscendingGroupSolvesWithAnAscendingSlope() {
        var position = BeamScoring.solve(
            stemsFor(ASCENDING_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

        assertThat(position.rightYUpSs() - position.leftYUpSs())
            .as("ascending heads give a beam rising in Y-up space")
            .isGreaterThan(BeamScoring.BEAM_EPS);
    }

    /**
     * Every candidate is built as {@code (int) unquanted + (i + quant)}, so both
     * solved edges must sit on a straddle/sit/inter/hang offset. A result that does
     * not means the quant generator was bypassed.
     */
    @Test
    void testSolvedEdgesLandOnQuants() {
        for (var contour : REPRESENTATIVE_CONTOURS) {
            var position = BeamScoring.solve(
                stemsFor(contour, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

            assertAll(
                () -> assertThat(distanceToNearestQuant(position.leftYUpSs()))
                    .as("left edge lands on a quant for %s", Arrays.toString(contour))
                    .isLessThan(BeamScoring.BEAM_EPS),
                () -> assertThat(distanceToNearestQuant(position.rightYUpSs()))
                    .as("right edge lands on a quant for %s", Arrays.toString(contour))
                    .isLessThan(BeamScoring.BEAM_EPS)
            );
        }
    }

    /**
     * {@code Beam::calc_stem_shorten} pulls the beam toward the noteheads in
     * proportion to how many stems are forced. Sixteenth notes keep the shortened
     * ideal above the minimum stem length, so the shift shows up undisturbed by the
     * feasibility clamp.
     */
    @Test
    void testForcedStemsPullTheBeamTowardTheNoteheads() {
        var unforced = scoringFor(
            ASCENDING_HALF_POSITIONS, DIR_UP, SIXTEENTH_NOTE_BEAM_COUNT, NO_FORCED_STEMS);
        var forced = scoringFor(
            ASCENDING_HALF_POSITIONS, DIR_UP, SIXTEENTH_NOTE_BEAM_COUNT, ALL_STEMS_FORCED);
        var shorteningSs = BeamScoring.BEAMED_STEM_SHORTEN_SS[SIXTEENTH_NOTE_BEAM_COUNT - 1];

        assertAll(
            () -> assertThat(forced.unquantedLeftY())
                .as("forced stems put the left beam edge closer to the heads below it")
                .isLessThan(unforced.unquantedLeftY()),
            () -> assertThat(unforced.unquantedLeftY() - forced.unquantedLeftY())
                .as("the whole beam moves by the group's stem shortening")
                .isCloseTo(shorteningSs, within(BeamScoring.BEAM_EPS)),
            () -> assertThat(unforced.unquantedRightY() - forced.unquantedRightY())
                .as("both edges move together, leaving the slope alone")
                .isCloseTo(shorteningSs, within(BeamScoring.BEAM_EPS))
        );
    }

    /**
     * The minimum stem length is enforced by a demerit plus the feasible-region
     * shift, not by a hard clamp, so this is a soft floor — assert it with a
     * tolerance rather than exactly.
     */
    @Test
    void testNoSolvedStemFallsBelowTheMinimumLength() {
        var stems = stemsFor(STEEP_ASCENDING_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT);
        var position = BeamScoring.solve(stems, DIR_UP, NO_FORCED_STEMS);
        var xSpanSs = stems.get(stems.size() - 1).xSs() - stems.get(0).xSs();

        // Stems reach the center of the outer beam, hence the half-thickness term.
        var heightOfBeamsSs = BeamScoring.BEAM_THICKNESS_SS;
        var minimumLengthSs =
            BeamScoring.BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS[EIGHTH_NOTE_BEAM_COUNT - 1]
                + heightOfBeamsSs
                - BeamScoring.BEAM_THICKNESS_SS / 2.0;

        for (var stem : stems) {
            var fraction = stem.xSs() / xSpanSs;
            var beamYUpSs =
                position.leftYUpSs() + (position.rightYUpSs() - position.leftYUpSs()) * fraction;

            assertThat(DIR_UP * (beamYUpSs - stem.headYUpSs()))
                .as("stem length at x=%s", stem.xSs())
                .isGreaterThanOrEqualTo(minimumLengthSs - STEM_LENGTH_FLOOR_TOLERANCE_SS);
        }
    }

    @Test
    void testCoveringIntervalConcavenessForcesFlat() {
        var scoring = scoringFor(CONCAVE_COVERING_HALF_POSITIONS, DIR_UP);

        assertThat(scoring.unquantedRightY())
            .as("inner heads above and below the covering interval force a flat beam")
            .isCloseTo(scoring.unquantedLeftY(), within(BeamScoring.BEAM_EPS));
    }

    @Test
    void testOppositeTrendConcavenessForcesFlat() {
        var scoring = scoringFor(CONCAVE_OPPOSITE_TREND_HALF_POSITIONS, DIR_UP);

        assertThat(scoring.unquantedRightY())
            .as("an against-the-grain inner step at the closest edge forces a flat beam")
            .isCloseTo(scoring.unquantedLeftY(), within(BeamScoring.BEAM_EPS));
    }

    @Test
    void testAllInnerHeadsCloserConcavenessForcesFlat() {
        var scoring = scoringFor(CONCAVE_ALL_INNER_CLOSER_HALF_POSITIONS, DIR_UP);

        assertThat(scoring.unquantedRightY())
            .as("inner heads closer to the beam than both edges force a flat beam")
            .isCloseTo(scoring.unquantedLeftY(), within(BeamScoring.BEAM_EPS));
    }

    /**
     * When both ideal stem ends clamp to the middle line the ideal positions cannot
     * express a slope, so {@code least_squares_positions} invents one of a beam
     * thickness, tilted toward the higher head.
     */
    @Test
    void testEqualClampedIdealsGetAnArtificialSlope() {
        var descending = scoringFor(CLAMPED_DESCENDING_PAIR_HALF_POSITIONS, DIR_UP);
        var ascending = scoringFor(CLAMPED_ASCENDING_PAIR_HALF_POSITIONS, DIR_UP);

        assertAll(
            () -> assertThat(descending.musicalDy())
                .as("the artificial slope drops a beam thickness toward the lower head")
                .isCloseTo(-BeamScoring.BEAM_THICKNESS_SS, within(BeamScoring.BEAM_EPS)),
            () -> assertThat(ascending.musicalDy())
                .as("and rises a beam thickness toward the higher head")
                .isCloseTo(BeamScoring.BEAM_THICKNESS_SS, within(BeamScoring.BEAM_EPS)),
            () -> assertThat(descending.unquantedRightY())
                .as("damping keeps the artificial slope's direction")
                .isLessThan(descending.unquantedLeftY()),
            () -> assertThat(ascending.unquantedRightY())
                .as("damping keeps the artificial slope's direction")
                .isGreaterThan(ascending.unquantedLeftY())
        );
    }

    /**
     * A group with nothing to slope between short-circuits before any division by
     * the X span.
     */
    @Test
    void testDegenerateGroupsReturnAFiniteFlatBeam() {
        var singleStem = List.of(new BeamScoring.StemInput(
            FIRST_STEM_X_SS,
            HEAD_BELOW_MIDDLE_Y_UP_SS,
            HEAD_BELOW_MIDDLE_HALF_POS,
            EIGHTH_NOTE_BEAM_COUNT
        ));
        var stackedStems = List.of(
            singleStem.get(0),
            new BeamScoring.StemInput(
                FIRST_STEM_X_SS,
                HEAD_ABOVE_MIDDLE_Y_UP_SS,
                HEAD_ABOVE_MIDDLE_HALF_POS,
                EIGHTH_NOTE_BEAM_COUNT
            )
        );

        var single = BeamScoring.solve(singleStem, DIR_UP, NO_FORCED_STEMS);
        var stacked = BeamScoring.solve(stackedStems, DIR_UP, NO_FORCED_STEMS);

        assertAll(
            () -> assertThat(single.leftYUpSs()).as("single stem left").isFinite(),
            () -> assertThat(single.rightYUpSs())
                .as("a single stem gives a flat beam")
                .isCloseTo(single.leftYUpSs(), within(BeamScoring.BEAM_EPS)),
            () -> assertThat(stacked.leftYUpSs()).as("zero-span left").isFinite(),
            () -> assertThat(stacked.rightYUpSs())
                .as("a zero-span group gives a flat beam")
                .isCloseTo(stacked.leftYUpSs(), within(BeamScoring.BEAM_EPS))
        );
    }

    /**
     * The empty-candidate fallback in {@code solve} is unreachable from the pipeline
     * that feeds it, and this test pins the reason rather than leaving the branch
     * silently untested.
     *
     * <p>{@code shift_region_to_valid} guarantees the unquanted beam clears every
     * stem's <em>shortest</em> end, which is farther from the notehead than the
     * quant range's bound (half a staff space plus the beam stack). The generator's
     * offsets reach from {@code -REGION_SIZE} up to nearly {@code +REGION_SIZE}, and
     * the truncation in {@code new_config} only ever moves a candidate away from
     * zero by less than 1, so the highest offset alone always produces a candidate
     * past the bound. The fallback would therefore need an input that breaks the
     * feasibility shift itself. What the test can assert is the observable
     * consequence: the solved edges are quantized, which the fallback's raw
     * unquanted position would not be.
     */
    @Test
    void testQuantCandidatesAreNeverExhausted() {
        for (var contour : REPRESENTATIVE_CONTOURS) {
            for (var dirSign : new int[] { DIR_UP, DIR_DOWN }) {
                var position = BeamScoring.solve(
                    stemsFor(contour, EIGHTH_NOTE_BEAM_COUNT), dirSign, ALL_STEMS_FORCED);

                assertThat(distanceToNearestQuant(position.leftYUpSs()))
                    .as("quanting ran for %s at dir %d", Arrays.toString(contour), dirSign)
                    .isLessThan(BeamScoring.BEAM_EPS);
            }
        }
    }
}
