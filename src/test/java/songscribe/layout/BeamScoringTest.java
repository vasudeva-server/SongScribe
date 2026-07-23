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

    /** LilyPond 2.26 reports {@code Beam.positions = (-4.0 . -4.0)} for that group. */
    private static final double LILYPOND_FLAT_BEAM_Y_UP_SS = -4.0;

    private static BeamScoring scoringFor(int[] halfPositions, int dirSign) {
        var stems = new ArrayList<BeamScoring.StemInput>(halfPositions.length);

        for (var i = 0; i < halfPositions.length; i++) {
            stems.add(new BeamScoring.StemInput(
                FIRST_STEM_X_SS + i * STEM_SPACING_SS,
                halfPositions[i] / 2.0,
                halfPositions[i],
                EIGHTH_NOTE_BEAM_COUNT
            ));
        }

        var scoring = new BeamScoring(stems, dirSign, NO_FORCED_STEMS);
        scoring.computeStemInfos();
        scoring.computeUnquantedPositions();
        return scoring;
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
}
