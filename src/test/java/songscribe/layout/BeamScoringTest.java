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
    private static final int THIRTY_SECOND_NOTE_BEAM_COUNT = 3;

    // Heads far enough below the staff that the ideal stem end would land below the
    // middle line, so both of calc_stem_info's clamps come into play.
    private static final int[] LOW_HALF_POSITIONS = { -12, -12, -12 };

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

    // ------------------------------------------------------------------
    // Three-beam (32nd note) groups
    // ------------------------------------------------------------------

    /**
     * @param beamCount beams per stem
     * @return the height of that beam stack, center of outermost to outer edge
     */
    private static double heightOfBeams(int beamCount) {
        return BeamScoring.BEAM_THICKNESS_SS + (beamCount - 1) * BeamScoring.BEAM_TRANSLATION_SS;
    }

    /**
     * {@code calc_stem_info}'s second clamp keeps the <em>lowest</em> beam of a
     * stack off the second staff line. At one or two beams the stack is short
     * enough that the bound sits below the middle line, where the preceding
     * clamp-to-zero already dominates, so the line is inert for every group the
     * rest of this suite builds. Only a three-beam stack pushes the bound above
     * zero and makes it bind — without this test the clamp could be deleted
     * outright and nothing would fail.
     */
    @Test
    void testThreeBeamStackClampsTheIdealStemEndToTheSecondStaffLine() {
        var lowHeadYUpSs = LOW_HALF_POSITIONS[0] / 2.0;
        var stem = new BeamScoring.StemInput(
            FIRST_STEM_X_SS, lowHeadYUpSs, LOW_HALF_POSITIONS[0], THIRTY_SECOND_NOTE_BEAM_COUNT);
        var stems = stemsFor(LOW_HALF_POSITIONS, THIRTY_SECOND_NOTE_BEAM_COUNT);

        var threeBeamIdealYUpSs = new BeamScoring(stems, DIR_UP, NO_FORCED_STEMS)
            .calcStemInfo(stem)
            .idealYUpSs();
        var oneBeamIdealYUpSs =
            new BeamScoring(stemsFor(LOW_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS)
                .calcStemInfo(stem)
                .idealYUpSs();

        var expectedFloorSs = -BeamScoring.STAFF_LINE_STEP_SS
            - BeamScoring.BEAM_THICKNESS_SS
            + heightOfBeams(THIRTY_SECOND_NOTE_BEAM_COUNT);

        assertAll(
            () -> assertThat(threeBeamIdealYUpSs)
                .as("a three-beam stack is held at the second-staff-line floor")
                .isCloseTo(expectedFloorSs, within(BeamScoring.BEAM_EPS)),
            () -> assertThat(oneBeamIdealYUpSs)
                .as("one beam clamps to the middle line instead, leaving the floor inert")
                .isCloseTo(0.0, within(BeamScoring.BEAM_EPS)),
            () -> assertThat(expectedFloorSs)
                .as("the floor only binds because it is above the clamp-to-zero")
                .isGreaterThan(0.0));
    }

    /**
     * {@code BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS} has only two entries, so a
     * three-beam group indexes past its end and relies on {@code robust_list_ref}'s
     * clamp to the last entry. An unclamped lookup would throw here.
     */
    @Test
    void testThreeBeamStackClampsTheExtremeMinimumLookupToTheLastEntry() {
        var extremeMinimums = BeamScoring.BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS;
        var lowHeadYUpSs = LOW_HALF_POSITIONS[0] / 2.0;
        var stem = new BeamScoring.StemInput(
            FIRST_STEM_X_SS, lowHeadYUpSs, LOW_HALF_POSITIONS[0], THIRTY_SECOND_NOTE_BEAM_COUNT);

        var shortestYUpSs = new BeamScoring(
            stemsFor(LOW_HALF_POSITIONS, THIRTY_SECOND_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS)
            .calcStemInfo(stem)
            .shortestYUpSs();

        var expectedShortestYUpSs = lowHeadYUpSs
            + extremeMinimums[extremeMinimums.length - 1]
            + heightOfBeams(THIRTY_SECOND_NOTE_BEAM_COUNT)
            - BeamScoring.BEAM_THICKNESS_SS / 2.0;

        assertAll(
            () -> assertThat(THIRTY_SECOND_NOTE_BEAM_COUNT)
                .as("the lookup really does run past the end of the table")
                .isGreaterThan(extremeMinimums.length),
            () -> assertThat(shortestYUpSs)
                .as("the last entry is reused for the out-of-range beam count")
                .isCloseTo(expectedShortestYUpSs, within(BeamScoring.BEAM_EPS)));
    }

    // ------------------------------------------------------------------
    // Individual demerit scorers
    // ------------------------------------------------------------------

    /**
     * @param beamCount beams per stem of the group
     * @return the demerit {@code score_forbidden_quants} charges per hit
     */
    private static double extraDemeritFor(int beamCount) {
        return BeamScoring.SECONDARY_BEAM_DEMERIT / beamCount;
    }

    /**
     * A staff line falling inside the white gap between a beam and the next beam
     * position reads as a printing error. Nothing in the solve-level tests can tell
     * this scorer apart from the other five — it could return a constant 0 and every
     * one of them would still pass — so it is charged directly here.
     */
    @Test
    void testStaffLinesInsideABeamGapAreCharged() {
        var scoring = new BeamScoring(
            stemsFor(ASCENDING_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

        // The gap below a stems-up beam at y spans roughly (y - 0.62, y - 0.19), so
        // the middle staff line sits inside it at 0.4 and clear of it at 1.0.
        var beamOverMiddleLineY = 0.4;
        var beamClearOfEveryLineY = 1.0;

        assertAll(
            () -> assertThat(scoring.staffLinesInBeamGaps(beamOverMiddleLineY, EIGHTH_NOTE_BEAM_COUNT))
                .as("a staff line inside the gap is charged")
                .isGreaterThan(0.0),
            () -> assertThat(scoring.staffLinesInBeamGaps(beamClearOfEveryLineY, EIGHTH_NOTE_BEAM_COUNT))
                .as("a gap with no staff line in it is free")
                .isEqualTo(0.0));
    }

    /**
     * The second block of {@code score_forbidden_quants} penalizes a secondary beam
     * that lands badly against the staff, but only when the beam slopes the wrong
     * way for its direction. Each guard is exercised on its own.
     */
    @Test
    void testSecondaryBeamQuantIsChargedOnlyWhenTheBeamSlopesTheWrongWay() {
        var scoring = new BeamScoring(
            stemsFor(ASCENDING_HALF_POSITIONS, SIXTEENTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

        // A stems-up beam sitting on a staff line, flat — the bad case.
        var sitY = BeamScoring.SIT_SS;
        var flatDy = 0.0;
        var rightWayDy = 1.0;

        assertAll(
            () -> assertThat(scoring.badSecondaryQuant(sitY, SIXTEENTH_NOTE_BEAM_COUNT, flatDy))
                .as("a flat stems-up beam on a sit quant is charged")
                .isCloseTo(extraDemeritFor(SIXTEENTH_NOTE_BEAM_COUNT), within(BeamScoring.BEAM_EPS)),
            () -> assertThat(scoring.badSecondaryQuant(sitY, SIXTEENTH_NOTE_BEAM_COUNT, rightWayDy))
                .as("the same quant is free once the beam slopes with its direction")
                .isEqualTo(0.0),
            () -> assertThat(scoring.badSecondaryQuant(sitY, EIGHTH_NOTE_BEAM_COUNT, flatDy))
                .as("a single beam has no secondary beam to place badly")
                .isEqualTo(0.0));
    }

    /**
     * The three-beam arm of the same block, which no other test can reach: it
     * penalizes {@code straddle} rather than {@code sit}, and is gated on a beam
     * count the rest of the suite never builds.
     */
    @Test
    void testThreeBeamStraddleQuantIsChargedSeparatelyFromTheTwoBeamCase() {
        var scoring = new BeamScoring(
            stemsFor(ASCENDING_HALF_POSITIONS, THIRTY_SECOND_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

        // An integral Y is a straddle quant; flat, so the slope is the wrong way.
        var straddleY = 1.0;
        var flatDy = 0.0;

        assertAll(
            () -> assertThat(scoring.badSecondaryQuant(straddleY, THIRTY_SECOND_NOTE_BEAM_COUNT, flatDy))
                .as("three beams on a straddle quant are charged")
                .isCloseTo(extraDemeritFor(THIRTY_SECOND_NOTE_BEAM_COUNT), within(BeamScoring.BEAM_EPS)),
            () -> assertThat(scoring.badSecondaryQuant(straddleY, SIXTEENTH_NOTE_BEAM_COUNT, flatDy))
                .as("two beams leave the straddle quant alone")
                .isEqualTo(0.0));
    }

    /**
     * {@code score_horizontal_inter_quants} exists to steer a flat beam inside the
     * staff off an inter quant. The solve-level tests only check that the winner
     * landed on <em>some</em> quant, so they cannot see this scorer at all.
     */
    @Test
    void testHorizontalInterQuantIsChargedOnlyForFlatBeamsInsideTheStaff() {
        var scoring = new BeamScoring(
            stemsFor(ASCENDING_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);

        var interYInsideStaff = BeamScoring.INTER_SS;
        var interYOutsideStaff = BeamScoring.STAFF_RADIUS_SS + BeamScoring.INTER_SS;
        var straddleYInsideStaff = 0.0;
        var slopedRightY = interYInsideStaff + 1.0;

        assertAll(
            () -> assertThat(scoring.scoreHorizontalInterQuants(interYInsideStaff, interYInsideStaff))
                .as("a flat beam on an inter quant inside the staff is charged")
                .isCloseTo(BeamScoring.HORIZONTAL_INTER_QUANT_PENALTY, within(BeamScoring.BEAM_EPS)),
            () -> assertThat(scoring.scoreHorizontalInterQuants(interYInsideStaff, slopedRightY))
                .as("a sloped beam is not this scorer's concern")
                .isEqualTo(0.0),
            () -> assertThat(scoring.scoreHorizontalInterQuants(straddleYInsideStaff, straddleYInsideStaff))
                .as("a straddle quant is fine")
                .isEqualTo(0.0),
            () -> assertThat(scoring.scoreHorizontalInterQuants(interYOutsideStaff, interYOutsideStaff))
                .as("outside the staff there is no staff line to sit wrong against")
                .isEqualTo(0.0));
    }

    /**
     * The stem-length scorer charges the {@code STEM_LENGTH_LIMIT_PENALTY} cliff
     * only when a stem is driven below its shortest allowed end. Driving one stem
     * past that point must cost orders of magnitude more than the gentle
     * deviation-from-ideal term.
     */
    @Test
    void testStemLengthScorerChargesTheLimitPenaltyBelowTheShortestEnd() {
        var scoring = scoringFor(ASCENDING_HALF_POSITIONS, DIR_UP);
        var idealLeftY = scoring.unquantedLeftY();
        var idealRightY = scoring.unquantedRightY();

        // Pulling a stems-up beam down shortens every stem beneath its minimum.
        var collapsedDropSs = 4.0;

        var atIdeal = scoring.scoreStemLengths(idealLeftY, idealRightY);
        var collapsed = scoring.scoreStemLengths(
            idealLeftY - collapsedDropSs, idealRightY - collapsedDropSs);

        assertAll(
            () -> assertThat(collapsed)
                .as("stems below their shortest end hit the penalty cliff")
                .isGreaterThan(atIdeal + BeamScoring.STEM_LENGTH_LIMIT_PENALTY),
            () -> assertThat(atIdeal)
                .as("the fitted position sits near the ideal, well under the cliff")
                .isLessThan(BeamScoring.STEM_LENGTH_LIMIT_PENALTY));
    }

    // ------------------------------------------------------------------
    // Quant range and feasibility
    // ------------------------------------------------------------------

    /**
     * The quant range is inclusive of its bound. Narrowing {@code >=} to {@code >}
     * would silently discard a legitimate candidate at the edge of the window.
     */
    @Test
    void testQuantRangeIncludesItsOwnBound() {
        var headYUpSs = HEAD_BELOW_MIDDLE_Y_UP_SS;
        var upward = new BeamScoring(
            stemsFor(ASCENDING_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_UP, NO_FORCED_STEMS);
        var downward = new BeamScoring(
            stemsFor(ASCENDING_HALF_POSITIONS, EIGHTH_NOTE_BEAM_COUNT), DIR_DOWN, NO_FORCED_STEMS);

        var upBound = upward.quantBound(headYUpSs, EIGHTH_NOTE_BEAM_COUNT);
        var downBound = downward.quantBound(headYUpSs, EIGHTH_NOTE_BEAM_COUNT);
        var pastBoundSs = 1.0;

        assertAll(
            () -> assertThat(upward.withinQuantRange(upBound, upBound))
                .as("stems up: a candidate exactly on the bound is in range")
                .isTrue(),
            () -> assertThat(upward.withinQuantRange(upBound - pastBoundSs, upBound))
                .as("stems up: below the bound is out of range")
                .isFalse(),
            () -> assertThat(downward.withinQuantRange(downBound, downBound))
                .as("stems down: a candidate exactly on the bound is in range")
                .isTrue(),
            () -> assertThat(downward.withinQuantRange(downBound + pastBoundSs, downBound))
                .as("stems down: above the bound is out of range")
                .isFalse(),
            () -> assertThat(upBound)
                .as("the bound clears the notehead on the stem side")
                .isGreaterThan(headYUpSs),
            () -> assertThat(downBound)
                .as("mirrored for stems down")
                .isLessThan(headYUpSs));
    }

    /**
     * {@code shift_region_to_valid} relocates a beam that would leave a stem below
     * its shortest end, and LilyPond's {@code point_in_interval} steps
     * {@code FEASIBLE_POINT_INSET_SS} <em>past</em> the bound rather than sitting on
     * it — otherwise half the quant window would land back in infeasible territory.
     * Asserting the outcome tolerance-style (as the stem-length floor test does)
     * cannot tell that shift apart from the stem-length demerit doing the work, so
     * the relocated position is pinned exactly here.
     */
    @Test
    void testFeasibilityShiftStepsPastTheBoundRatherThanOntoIt() {
        var contour = STEEP_ASCENDING_HALF_POSITIONS;
        var scoring = scoringFor(contour, DIR_UP);
        var stems = stemsFor(contour, EIGHTH_NOTE_BEAM_COUNT);
        var xSpan = stems.get(stems.size() - 1).xSs() - stems.get(0).xSs();
        var slope = (scoring.unquantedRightY() - scoring.unquantedLeftY()) / xSpan;

        // The intersection over stems of the feasible left-end Y values.
        var feasibleBound = -Double.MAX_VALUE;

        for (var stem : stems) {
            var shortestYUpSs = scoring.calcStemInfo(stem).shortestYUpSs();
            feasibleBound = Math.max(feasibleBound, shortestYUpSs - slope * stem.xSs());
        }

        assertThat(scoring.unquantedLeftY())
            .as("the shift lands a full inset past the feasibility bound")
            .isCloseTo(feasibleBound + BeamScoring.FEASIBLE_POINT_INSET_SS, within(BeamScoring.BEAM_EPS));
    }

    /**
     * {@code solve}'s documented contract for a group with no stems at all. The
     * LayoutEngine guards against this today, but the entry point is package-visible
     * and states the behavior, so it is pinned rather than left to that caller.
     */
    @Test
    void testSolveWithNoStemsReturnsAFlatBeamOnTheMiddleLine() {
        var position = BeamScoring.solve(List.of(), DIR_UP, NO_FORCED_STEMS);

        assertAll(
            () -> assertThat(position.leftYUpSs()).isEqualTo(0.0),
            () -> assertThat(position.rightYUpSs()).isEqualTo(0.0));
    }
}
