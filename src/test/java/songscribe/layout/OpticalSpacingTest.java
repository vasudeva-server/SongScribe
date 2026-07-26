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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;

/**
 * Tests for {@link OpticalSpacing#applyCorrections} — the opposite-stem, same-direction and
 * downstem-after-barline corrections — on synthetic columns and springs (no rendering). Each
 * correction is exercised through the public entry point so the guard clauses and the additive
 * combination of the three corrections are covered exactly as production code sees them; the
 * accessor sign convention the corrections depend on ({@link ElementColumn#getPositionSs},
 * {@link ElementColumn#getAbsoluteTopYSs}, {@link ElementColumn#getAbsoluteBottomYSs}) is asserted
 * directly first, since a flipped sign there would silently mis-space every correction.
 */
class OpticalSpacingTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    private static final double NO_LEFT_EXTENT_SS = 0.0;
    private static final double HEAD_RIGHT_EXTENT_SS = 1.0;

    /** A staff position whose Ss conversion is 0, used wherever the test cares only about span shape. */
    private static final int NEUTRAL_STAFF_POSITION_SP = 0;
    private static final double NEUTRAL_STEM_TOP_SS = 0.0;
    private static final double NEUTRAL_STEM_BOTTOM_SS = 0.0;

    // --- Overlap magnitudes shared by the opposite-stem and downstem-after-barline ramps, both of
    // which saturate at OpticalSpacing.STEM_OVERLAP_SATURATION_SS. ---
    private static final double OVERLAP_BELOW_SATURATION_SS = OpticalSpacing.STEM_OVERLAP_SATURATION_SS / 2;
    private static final double OVERLAP_AT_OR_ABOVE_SATURATION_SS = OpticalSpacing.STEM_OVERLAP_SATURATION_SS + 1.0;
    private static final double ZERO_OVERLAP_SS = 0.0;

    // --- Two columns whose spans are pulled apart by a gap, for the negative-overlap boundary. ---
    private static final double NEGATIVE_OVERLAP_PREV_BOTTOM_SS = 0.0;
    private static final double NEGATIVE_OVERLAP_GAP_SS = 1.0;
    private static final double NEGATIVE_OVERLAP_SPAN_HEIGHT_SS = 1.0;

    // --- A span that guarantees full overlap with the barline's staff-height span regardless of the
    // actual value of Staff.STAFF_HALF_SS. ---
    private static final double FAR_BEYOND_STAFF_SS = 10.0;

    // --- A span entirely below the staff, for the downstem-after-barline negative-overlap case. ---
    private static final double BELOW_STAFF_MARGIN_SS = 1.0;
    private static final double BELOW_STAFF_SPAN_HEIGHT_SS = 1.0;

    // --- Staff-position deltas: Staff.STAFF_POSITION_OFFSET_SS is 0.5 Ss per staff-position unit, so
    // a delta of 1 sp is exactly SAME_DIRECTION_THRESHOLD_SS (0.5 Ss). ---
    private static final int SAME_DIRECTION_AT_THRESHOLD_SP = 1;
    private static final int SAME_DIRECTION_ABOVE_THRESHOLD_SP = 2;
    private static final int SAME_DIRECTION_FAR_ABOVE_THRESHOLD_SP = 6;

    private static final int FIRST_BEAM_GROUP_ID = 0;

    private static final double BASE_REST_SS = 5.0;
    private static final double BASE_STRUT_SS = 1.0;
    private static final double BASE_LEVEL_OFFSET_SS = 0.75;
    private static final double CHANNEL_TEST_WEIGHT = 0.6;

    // ==========================================================================
    // Column builders
    // ==========================================================================

    private static ElementColumn stemColumn(
        ElementType type,
        int staffPositionSp,
        StaffElement.Direction direction,
        double stemTopSs,
        double stemBottomSs) {

        var column = new ElementColumn(
            type.newInstance(), Collections.emptyList(),
            NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, stemTopSs, stemBottomSs, null, 0.0, false);
        column.getElement().setStaffPosition(staffPositionSp);
        column.getElement().setDirection(direction);

        return column;
    }

    /** A stemmed note whose span, paired with another built the same way, overlaps by exactly {@code overlapSs}. */
    private static ElementColumn overlappingStemColumn(StaffElement.Direction direction, double overlapSs) {
        return stemColumn(ElementType.CROTCHET, NEUTRAL_STAFF_POSITION_SP, direction, NEUTRAL_STEM_TOP_SS, overlapSs);
    }

    /** A grace-note counterpart to {@link #overlappingStemColumn}, for the grace-note tests. */
    private static ElementColumn graceOverlappingColumn(StaffElement.Direction direction, double overlapSs) {
        return stemColumn(
            ElementType.GRACE_QUAVER, NEUTRAL_STAFF_POSITION_SP, direction, NEUTRAL_STEM_TOP_SS, overlapSs);
    }

    /** A stemmed note at a given staff position, with a degenerate (zero-height) span. */
    private static ElementColumn positionedColumn(StaffElement.Direction direction, int staffPositionSp) {
        return stemColumn(ElementType.CROTCHET, staffPositionSp, direction, NEUTRAL_STEM_TOP_SS, NEUTRAL_STEM_BOTTOM_SS);
    }

    /** A grace-note counterpart to {@link #positionedColumn}, for the grace-note tests. */
    private static ElementColumn gracePositionedColumn(StaffElement.Direction direction, int staffPositionSp) {
        return stemColumn(
            ElementType.GRACE_QUAVER, staffPositionSp, direction, NEUTRAL_STEM_TOP_SS, NEUTRAL_STEM_BOTTOM_SS);
    }

    private static ElementColumn negativeOverlapPrevColumn(StaffElement.Direction direction) {
        return stemColumn(
            ElementType.CROTCHET, NEUTRAL_STAFF_POSITION_SP, direction,
            NEUTRAL_STEM_TOP_SS, NEGATIVE_OVERLAP_PREV_BOTTOM_SS);
    }

    private static ElementColumn negativeOverlapCurrColumn(StaffElement.Direction direction) {
        var topSs = NEGATIVE_OVERLAP_PREV_BOTTOM_SS + NEGATIVE_OVERLAP_GAP_SS;
        return stemColumn(
            ElementType.CROTCHET, NEUTRAL_STAFF_POSITION_SP, direction,
            topSs, topSs + NEGATIVE_OVERLAP_SPAN_HEIGHT_SS);
    }

    private static ElementColumn barlineColumn() {
        return new ElementColumn(
            ElementType.SINGLE_BARLINE.newInstance(), Collections.emptyList(),
            NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, NEUTRAL_STEM_TOP_SS, NEUTRAL_STEM_BOTTOM_SS, null, 0.0, false);
    }

    private static ElementColumn restColumn() {
        return new ElementColumn(
            ElementType.CROTCHET_REST.newInstance(), Collections.emptyList(),
            NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, NEUTRAL_STEM_TOP_SS, NEUTRAL_STEM_BOTTOM_SS, null, 0.0, false);
    }

    /**
     * A candidate {@code curr} for the downstem-after-barline correction: its span, measured against
     * the barline's full staff-height span, overlaps by exactly {@code overlapSs} (for
     * {@code overlapSs} up to {@code 2 * Staff.STAFF_HALF_SS}).
     */
    private static ElementColumn barlineOverlapCandidateColumn(StaffElement.Direction direction, double overlapSs) {
        var topSs = -Staff.STAFF_HALF_SS;
        return stemColumn(ElementType.CROTCHET, NEUTRAL_STAFF_POSITION_SP, direction, topSs, topSs + overlapSs);
    }

    /** A downstem column whose span sits entirely below the staff: negative overlap. */
    private static ElementColumn downstemColumnBelowStaff() {
        var topSs = Staff.STAFF_HALF_SS + BELOW_STAFF_MARGIN_SS;
        return stemColumn(
            ElementType.CROTCHET, NEUTRAL_STAFF_POSITION_SP, StaffElement.Direction.DOWN,
            topSs, topSs + BELOW_STAFF_SPAN_HEIGHT_SS);
    }

    /** A downstem column whose span fully contains the staff, regardless of its actual height. */
    private static ElementColumn downstemColumnSpanningFarBeyondStaff() {
        return stemColumn(
            ElementType.CROTCHET, NEUTRAL_STAFF_POSITION_SP, StaffElement.Direction.DOWN,
            -FAR_BEYOND_STAFF_SS, FAR_BEYOND_STAFF_SS);
    }

    /** A grace-note counterpart to {@link #downstemColumnSpanningFarBeyondStaff}, for the grace-note tests. */
    private static ElementColumn graceDownstemColumnSpanningFarBeyondStaff() {
        return stemColumn(
            ElementType.GRACE_QUAVER, NEUTRAL_STAFF_POSITION_SP, StaffElement.Direction.DOWN,
            -FAR_BEYOND_STAFF_SS, FAR_BEYOND_STAFF_SS);
    }

    private static Spring baseSpring() {
        return Spring.of(BASE_REST_SS, BASE_STRUT_SS);
    }

    /** Runs {@code prev, curr} through {@link OpticalSpacing#applyCorrections} and returns the net delta to restSs. */
    private static double correctionDeltaSs(ElementColumn prev, ElementColumn curr) {
        var corrected = OpticalSpacing.applyCorrections(List.of(baseSpring()), List.of(prev, curr));
        return corrected.getFirst().restSs() - BASE_REST_SS;
    }

    // ==========================================================================
    // Accessor sign convention (ElementColumn#getPositionSs / getAbsoluteTopYSs / getAbsoluteBottomYSs)
    // ==========================================================================

    private static final int KNOWN_STAFF_POSITION_SP = 4;
    private static final double KNOWN_STEM_TOP_SS = -3.0;
    private static final double KNOWN_STEM_BOTTOM_SS = 1.5;

    @Test
    void testGetPositionSsConvertsStaffPositionViaStaffSpToSs() {
        var column = stemColumn(
            ElementType.CROTCHET, KNOWN_STAFF_POSITION_SP, StaffElement.Direction.UP,
            KNOWN_STEM_TOP_SS, KNOWN_STEM_BOTTOM_SS);

        assertThat(column.getPositionSs()).isCloseTo(Staff.spToSs(KNOWN_STAFF_POSITION_SP), within(TOLERANCE));
    }

    @Test
    void testGetAbsoluteBottomYSsEqualsPositionPlusStemBottom() {
        var column = stemColumn(
            ElementType.CROTCHET, KNOWN_STAFF_POSITION_SP, StaffElement.Direction.UP,
            KNOWN_STEM_TOP_SS, KNOWN_STEM_BOTTOM_SS);

        assertThat(column.getAbsoluteBottomYSs())
            .isCloseTo(column.getPositionSs() + column.getStemBottomSs(), within(TOLERANCE));
    }

    @Test
    void testGetAbsoluteBottomYSsIsNumericallyGreaterThanTop() {
        var column = stemColumn(
            ElementType.CROTCHET, KNOWN_STAFF_POSITION_SP, StaffElement.Direction.UP,
            KNOWN_STEM_TOP_SS, KNOWN_STEM_BOTTOM_SS);

        assertThat(column.getAbsoluteBottomYSs()).isGreaterThan(column.getAbsoluteTopYSs());
    }

    // ==========================================================================
    // Opposite-stem correction
    // ==========================================================================

    @Test
    void testOppositeStemCorrectionScalesLinearlyBelowSaturation() {
        var prev = overlappingStemColumn(StaffElement.Direction.UP, OVERLAP_BELOW_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_BELOW_SATURATION_SS);

        var expectedCorrectionSs = (OVERLAP_BELOW_SATURATION_SS / OpticalSpacing.STEM_OVERLAP_SATURATION_SS)
            * OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS;

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(expectedCorrectionSs, within(TOLERANCE));
    }

    @Test
    void testOppositeStemCorrectionWidensAndSaturatesAboveSaturation() {
        var prev = overlappingStemColumn(StaffElement.Direction.UP, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testOppositeStemCorrectionNarrowsWhenPrevStemsDownAndCurrStemsUp() {
        var prev = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.UP, OVERLAP_AT_OR_ABOVE_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(-OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testOppositeStemCorrectionIsZeroAtZeroOverlap() {
        var prev = overlappingStemColumn(StaffElement.Direction.UP, ZERO_OVERLAP_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, ZERO_OVERLAP_SS);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testOppositeStemCorrectionIsZeroAtNegativeOverlap() {
        var prev = negativeOverlapPrevColumn(StaffElement.Direction.UP);
        var curr = negativeOverlapCurrColumn(StaffElement.Direction.DOWN);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    // ==========================================================================
    // Same-direction correction
    // ==========================================================================

    @Test
    void testSameDirectionCorrectionIsZeroAtOrBelowThreshold() {
        var prev = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var curr = positionedColumn(StaffElement.Direction.DOWN, SAME_DIRECTION_AT_THRESHOLD_SP);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testSameDirectionCorrectionNarrowsWhenCurrIsLower() {
        var prev = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var curr = positionedColumn(StaffElement.Direction.DOWN, SAME_DIRECTION_ABOVE_THRESHOLD_SP);

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(-OpticalSpacing.SAME_DIRECTION_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testSameDirectionCorrectionWidensWhenCurrIsHigher() {
        var prev = positionedColumn(StaffElement.Direction.DOWN, SAME_DIRECTION_ABOVE_THRESHOLD_SP);
        var curr = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(OpticalSpacing.SAME_DIRECTION_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testSameDirectionCorrectionMagnitudeIsFixedRegardlessOfDistanceAboveThreshold() {
        var nearPrev = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var nearCurr = positionedColumn(StaffElement.Direction.DOWN, SAME_DIRECTION_ABOVE_THRESHOLD_SP);
        var farPrev = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var farCurr = positionedColumn(StaffElement.Direction.DOWN, SAME_DIRECTION_FAR_ABOVE_THRESHOLD_SP);

        assertThat(correctionDeltaSs(nearPrev, nearCurr))
            .isCloseTo(correctionDeltaSs(farPrev, farCurr), within(TOLERANCE));
    }

    // ==========================================================================
    // Downstem-after-barline correction
    // ==========================================================================

    @Test
    void testDownstemAfterBarlineCorrectionIsZeroWhenPrevIsNotABarline() {
        var prev = restColumn();
        var curr = barlineOverlapCandidateColumn(StaffElement.Direction.DOWN, OVERLAP_BELOW_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testDownstemAfterBarlineCorrectionIsZeroWhenCurrDirectionIsNotDown() {
        var prev = barlineColumn();
        var curr = barlineOverlapCandidateColumn(StaffElement.Direction.UP, OVERLAP_BELOW_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testDownstemAfterBarlineCorrectionIsZeroWhenOverlapIsZeroOrNegative() {
        var prev = barlineColumn();
        var atZero = barlineOverlapCandidateColumn(StaffElement.Direction.DOWN, ZERO_OVERLAP_SS);
        var negative = downstemColumnBelowStaff();

        assertThat(correctionDeltaSs(prev, atZero)).isCloseTo(0.0, within(TOLERANCE));
        assertThat(correctionDeltaSs(prev, negative)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testDownstemAfterBarlineCorrectionScalesLinearlyBelowSaturation() {
        var prev = barlineColumn();
        var curr = barlineOverlapCandidateColumn(StaffElement.Direction.DOWN, OVERLAP_BELOW_SATURATION_SS);

        var expectedCorrectionSs = (OVERLAP_BELOW_SATURATION_SS / OpticalSpacing.STEM_OVERLAP_SATURATION_SS)
            * OpticalSpacing.DOWNSTEM_BARLINE_MAX_CORRECTION_SS;

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(expectedCorrectionSs, within(TOLERANCE));
    }

    @Test
    void testDownstemAfterBarlineCorrectionSaturatesAboveSaturation() {
        var prev = barlineColumn();
        var curr = downstemColumnSpanningFarBeyondStaff();

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(OpticalSpacing.DOWNSTEM_BARLINE_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    // ==========================================================================
    // Grace notes — corrected like any other column, always on an up stem
    // ==========================================================================

    @Test
    void testGraceNoteColumnAlwaysReportsAnUpStemWhateverDirectionItStores() {
        var grace = gracePositionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);

        assertThat(grace.getDirection()).isEqualTo(StaffElement.Direction.UP);
    }

    @Test
    void testOppositeStemCorrectionWidensAGraceToDownstemHostGap() {
        var prev = graceOverlappingColumn(StaffElement.Direction.UP, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testOppositeStemCorrectionNarrowsADownstemToGraceGap() {
        // The gap into a grace note: the grace's own up stem makes this the down-then-up case, so it
        // narrows exactly as a normal down-then-up pair would.
        var prev = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = graceOverlappingColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr))
            .isCloseTo(-OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testSameDirectionCorrectionAppliesBetweenAGraceNoteAndAnUpstemNeighbour() {
        var gracePrev = gracePositionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var lowerCurr = positionedColumn(StaffElement.Direction.UP, SAME_DIRECTION_ABOVE_THRESHOLD_SP);

        assertThat(correctionDeltaSs(gracePrev, lowerCurr))
            .isCloseTo(-OpticalSpacing.SAME_DIRECTION_MAX_CORRECTION_SS, within(TOLERANCE));
    }

    @Test
    void testDownstemAfterBarlineCorrectionIsZeroForAGraceNoteBecauseItStemsUp() {
        var prev = barlineColumn();
        var curr = graceDownstemColumnSpanningFarBeyondStaff();

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    // ==========================================================================
    // Guard conditions shared by the opposite-stem and same-direction corrections
    // ==========================================================================

    @Test
    void testCorrectionIsZeroWhenPrevLacksAStem() {
        var prev = restColumn();
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testCorrectionIsZeroWhenCurrLacksAStem() {
        var prev = overlappingStemColumn(StaffElement.Direction.UP, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = restColumn();

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void testOppositeStemCorrectionIsZeroForAKneeWithinOneBeamGroup() {
        var prev = overlappingStemColumn(StaffElement.Direction.UP, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        prev.setBeamGroupId(FIRST_BEAM_GROUP_ID);
        curr.setBeamGroupId(FIRST_BEAM_GROUP_ID);

        assertThat(correctionDeltaSs(prev, curr)).isCloseTo(0.0, within(TOLERANCE));
    }

    // ==========================================================================
    // applyCorrections behavior
    // ==========================================================================

    @Test
    void testLiftExemptGraceHostSpringIsCorrectedAndStaysExempt() {
        // The exemption is from the lyric lift only, so the grace→host distance takes the same
        // optical nudge as any other gap.
        var prev = graceOverlappingColumn(StaffElement.Direction.UP, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_AT_OR_ABOVE_SATURATION_SS);
        var exemptSpring = Spring.of(BASE_REST_SS, BASE_STRUT_SS, Spring.NORMAL_WEIGHT, true);

        var corrected = OpticalSpacing.applyCorrections(List.of(exemptSpring), List.of(prev, curr)).getFirst();

        assertThat(corrected.restSs())
            .isCloseTo(BASE_REST_SS + OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS, within(TOLERANCE));
        assertThat(corrected.naturalLengthSs())
            .isCloseTo(BASE_REST_SS + OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS, within(TOLERANCE));
        assertThat(corrected.liftExempt()).isTrue();
        assertThat(corrected.strutSs()).isEqualTo(BASE_STRUT_SS);
    }

    @Test
    void testZeroNetCorrectionGapPassesThroughUnchanged() {
        var prev = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var curr = positionedColumn(StaffElement.Direction.DOWN, NEUTRAL_STAFF_POSITION_SP);
        var spring = baseSpring();

        var corrected = OpticalSpacing.applyCorrections(List.of(spring), List.of(prev, curr));

        assertThat(corrected.getFirst()).isSameAs(spring);
    }

    @Test
    void testBarlineDownstemGapAppliesOnlyTheBarlineCorrectionOnce() {
        var prev = barlineColumn();
        var curr = barlineOverlapCandidateColumn(StaffElement.Direction.DOWN, OVERLAP_BELOW_SATURATION_SS);
        var spring = baseSpring();

        var corrected = OpticalSpacing.applyCorrections(List.of(spring), List.of(prev, curr));

        var expectedCorrectionSs = (OVERLAP_BELOW_SATURATION_SS / OpticalSpacing.STEM_OVERLAP_SATURATION_SS)
            * OpticalSpacing.DOWNSTEM_BARLINE_MAX_CORRECTION_SS;
        assertThat(corrected.getFirst().restSs()).isCloseTo(BASE_REST_SS + expectedCorrectionSs, within(TOLERANCE));
    }

    // ==========================================================================
    // Corrections land in both channels (Phase 5)
    // ==========================================================================

    @Test
    void testCorrectionShiftsRestAndLevelOffsetByTheSameDeltaAndLeavesStrutWeightExemptionUntouched() {
        var prev = overlappingStemColumn(StaffElement.Direction.UP, OVERLAP_BELOW_SATURATION_SS);
        var curr = overlappingStemColumn(StaffElement.Direction.DOWN, OVERLAP_BELOW_SATURATION_SS);
        var originalSpring = Spring.of(BASE_REST_SS, BASE_STRUT_SS, CHANNEL_TEST_WEIGHT, false, BASE_LEVEL_OFFSET_SS);

        var corrected = OpticalSpacing.applyCorrections(List.of(originalSpring), List.of(prev, curr)).getFirst();

        var expectedCorrectionSs = (OVERLAP_BELOW_SATURATION_SS / OpticalSpacing.STEM_OVERLAP_SATURATION_SS)
            * OpticalSpacing.OPPOSITE_STEM_MAX_CORRECTION_SS;
        assertThat(corrected.restSs() - originalSpring.restSs()).isCloseTo(expectedCorrectionSs, within(TOLERANCE));
        assertThat(corrected.levelOffsetSs() - originalSpring.levelOffsetSs())
            .isCloseTo(expectedCorrectionSs, within(TOLERANCE));
        assertThat(corrected.strutSs()).isEqualTo(originalSpring.strutSs());
        assertThat(corrected.weight()).isEqualTo(originalSpring.weight());
        assertThat(corrected.liftExempt()).isEqualTo(originalSpring.liftExempt());
    }
}
