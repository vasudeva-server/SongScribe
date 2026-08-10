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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.Song;

/**
 * Tests for {@link LyricLift#applyLyricLift} — the shortfall / lift / spike derivation, on synthetic
 * springs and columns (no rendering). The even lift is the largest absolute shortfall
 * ({@code requirement − base rest}), added to each gap in proportion to that gap's reducing factor;
 * a gap whose requirement still exceeds its proportional share takes a local spike. The lift is
 * uncapped — a lyric-heavy line lifts evenly to its widest requirement.
 */
class LyricLiftTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    /** Collision floor for every synthetic spring: low enough to leave slack at any rest here. */
    private static final double STRUT_SS = 0.5;

    /** The line rest passed to the pass; base rests are multiples of it. */
    private static final double DEFAULT_LINE_REST_SS = Song.DEFAULT_REST_LENGTH_SS;

    private static final double BEAM_FACTOR = HorizontalSpacingCalculator.BEAM_GROUP_INTERNAL_REST_FACTOR;
    /** A tight beam gap's base rest at the default line rest: {@code 0.8 × 2.5 = 2.0}. */
    private static final double BEAM_REST_SS = BEAM_FACTOR * DEFAULT_LINE_REST_SS;

    private static final String SYLLABLE_TEXT = "la";
    // These columns are synthetic — the lyric lives on the column alone, which is all the lift
    // rules read.
    private static final Lyric SYLLABLE_LYRIC =
        new Lyric(Lyric.FIRST_VERSE, SYLLABLE_TEXT, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
    private static final double NO_SYLLABLE_WIDTH_SS = 0.0;
    private static final int NO_BEAM_GROUP_ID = 0;
    private static final int FIRST_BEAM_GROUP_ID = 0;

    // --- Narrow syllable: 0.5 wide, 0.5 min gap -> a 1.0 requirement against another narrow
    // syllable, which every base rest here already covers.
    private static final double NARROW_SYLLABLE_WIDTH_SS = 0.5;
    private static final double NARROW_MIN_GAP_SS = 0.5;
    private static final double NARROW_PAIR_REQUIREMENT_SS = 1.0;

    // --- Even lift: a lead syllable requires 2.5/2 + 1.5 + 0.5/2 = 3.0, a shortfall over the line
    // rest base. The largest shortfall on the line is the even lift, so every gap rises by it.
    private static final double LEAD_SYLLABLE_WIDTH_SS = 2.5;
    private static final double LEAD_MIN_GAP_SS = 1.5;
    private static final double LEAD_REQUIREMENT_SS = 3.0;
    private static final double EVEN_LIFT_SS = LEAD_REQUIREMENT_SS - DEFAULT_LINE_REST_SS;
    private static final double EVEN_LIFTED_REST_SS = DEFAULT_LINE_REST_SS + EVEN_LIFT_SS;

    // --- Wide syllable: requires 3.5/2 + 2.0 + 0.5/2 = 4.0 on each of its two flanking gaps, a
    // shortfall of 1.5 — the largest on the line. With no cap the whole line lifts evenly by 1.5, so
    // every normal gap settles at 2.5 + 1.5 = 4.0, uniform, with no local spike.
    private static final double WIDE_SYLLABLE_WIDTH_SS = 3.5;
    private static final double WIDE_MIN_GAP_SS = 2.0;
    private static final double WIDE_PAIR_REQUIREMENT_SS = 4.0;
    private static final double UNIFORM_LIFTED_REST_SS = WIDE_PAIR_REQUIREMENT_SS;
    // --- One-sided: a wide syllable beside an unlyriced neighbour still reserves its own overhanging
    // half plus the inter-syllable gap: 3.5/2 + 2.0 = 3.75. The gap lifts to that requirement.
    private static final double ONE_SIDED_REQUIREMENT_SS = WIDE_SYLLABLE_WIDTH_SS / 2 + WIDE_MIN_GAP_SS;

    // --- Beamed spike: a syllable on a tight beamed gap (base 2.0) requires 1.0/2 + 1.5 + 1.0/2 =
    // 2.5. Its absolute shortfall is 2.5 - 2.0 = 0.5, so the even lift is 0.5 and the neighbouring
    // normal gap rises only to 3.0 — well short of the old ratio behaviour (2.5 × 1.25 = 3.125),
    // where a tight gap's requirement inflated the whole line. The beamed gap spikes locally to 2.5.
    private static final double BEAM_SYLLABLE_WIDTH_SS = 1.0;
    private static final double BEAM_MIN_GAP_SS = 1.5;
    private static final double BEAM_GAP_REQUIREMENT_SS = 2.5;
    private static final double BEAM_DRIVEN_LIFT_SS = BEAM_GAP_REQUIREMENT_SS - BEAM_REST_SS;
    private static final double NORMAL_REST_AFTER_BEAM_LIFT_SS = DEFAULT_LINE_REST_SS + BEAM_DRIVEN_LIFT_SS;
    /** The old (Phase 2) ratio behaviour that 5a's absolute shortfall deliberately avoids. */
    private static final double PHASE2_RATIO_NORMAL_REST_SS =
        DEFAULT_LINE_REST_SS * (BEAM_GAP_REQUIREMENT_SS / BEAM_REST_SS);

    // --- Notehead-centering: two syllables whose noteheads differ in width, so the (c_prev − c_curr)
    // term shifts the requirement away from the naive w + minGap.
    private static final double PLAIN_HEAD_RIGHT_EXTENT_SS = 1.0;
    private static final double WIDE_HEAD_RIGHT_EXTENT_SS = 1.5;
    private static final double NOTEHEAD_SYLLABLE_WIDTH_SS = 2.0;
    private static final double NOTEHEAD_MIN_GAP_SS = 1.0;

    // --- Grace–host lyric union geometry (mirrors the plan's shared derivation) ---
    private static final double GRACE_RIGHT_EXTENT_SS = 0.75;
    private static final double GRACE_HOST_REST_SS = HorizontalSpacingCalculator.GRACE_HOST_REST_SS;
    /** Delta-X between the grace and host origins: the grace's right extent plus the fixed rest. */
    private static final double GRACE_HOST_GAP_SS = GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS;
    /** The grace-notehead-left → host-notehead-right union a grace lyric is measured against. */
    private static final double UNION_WIDTH_SS = GRACE_HOST_GAP_SS + PLAIN_HEAD_RIGHT_EXTENT_SS;
    /** A wide grace lyric overhangs the union by this much on each side. */
    private static final double WIDE_GRACE_OVERHANG_SS = 1.0;
    private static final double WIDE_GRACE_SYLLABLE_WIDTH_SS = UNION_WIDTH_SS + 2 * WIDE_GRACE_OVERHANG_SS;
    /** The host's lyric right extent under a wide grace lyric: {@code (w − overhang) − graceHostGap}. */
    private static final double WIDE_GRACE_HOST_RIGHT_EXTENT_SS =
        (WIDE_GRACE_SYLLABLE_WIDTH_SS - WIDE_GRACE_OVERHANG_SS) - GRACE_HOST_GAP_SS;
    /** A grace lyric that fits within the union (≤ the grace→host gap), so it overhangs nothing. */
    private static final double NARROW_GRACE_SYLLABLE_WIDTH_SS = 2.0;
    private static final double GRACE_MIN_GAP_SS = 1.0;

    private static final int FIRST_GAP = 0;
    private static final int SECOND_GAP = 1;
    private static final int THIRD_GAP = 2;
    private static final int FOURTH_GAP = 3;

    private static final int TWO_GAPS = 2;
    private static final int FOUR_GAPS = 4;

    private static ElementColumn column(
        ElementType type,
        @Nullable Lyric lyric,
        double syllableWidthSs,
        double minGapSs,
        boolean beamed,
        int beamGroupId) {

        var elementColumn = new ElementColumn(
            type.newInstance(),
            Collections.emptyList(),
            0.0, 0.0,
            0.0, 0.0,
            lyric,
            syllableWidthSs,
            beamed);
        elementColumn.setMinGapToNextSyllableSs(minGapSs);

        if (beamed) {
            elementColumn.setBeamGroupId(beamGroupId);
        }

        return elementColumn;
    }

    private static ElementColumn plainSyllableColumn(double syllableWidthSs, double minGapSs) {
        return column(ElementType.CROTCHET, SYLLABLE_LYRIC, syllableWidthSs, minGapSs, false, NO_BEAM_GROUP_ID);
    }

    private static ElementColumn narrowSyllableColumn() {
        return plainSyllableColumn(NARROW_SYLLABLE_WIDTH_SS, NARROW_MIN_GAP_SS);
    }

    private static ElementColumn beamedSyllableColumn(double syllableWidthSs, double minGapSs) {
        return column(ElementType.SEMIQUAVER, SYLLABLE_LYRIC, syllableWidthSs, minGapSs, true, FIRST_BEAM_GROUP_ID);
    }

    private static ElementColumn lyricLessColumn() {
        return column(ElementType.CROTCHET, null, NO_SYLLABLE_WIDTH_SS, NARROW_MIN_GAP_SS, false, NO_BEAM_GROUP_ID);
    }

    /** A lift-exempt gap's input rest, low enough that any lift would visibly change it if applied. */
    private static final double LIFT_EXEMPT_INPUT_REST_SS = 2.0;

    private static Spring spring(double restSs) {
        return Spring.of(restSs, STRUT_SS);
    }

    private static Spring liftExemptSpring(double restSs) {
        return Spring.of(restSs, STRUT_SS, Spring.NORMAL_WEIGHT, true);
    }

    /** A grace column bearing a lyric, with the given notehead right extent and comfortable gap. */
    private static ElementColumn graceColumn(double rightExtentSs, double syllableWidthSs, double minGapSs) {
        var graceColumn = new ElementColumn(
            graceQuaver(), Collections.emptyList(),
            0.0, rightExtentSs, 0.0, 0.0, SYLLABLE_LYRIC, syllableWidthSs, false);
        graceColumn.setMinGapToNextSyllableSs(minGapSs);
        return graceColumn;
    }

    /** A grace's host column: bears no syllable of its own (the grace carries the lyric). */
    private static ElementColumn hostColumn(double rightExtentSs) {
        return new ElementColumn(
            crotchet(), Collections.emptyList(),
            0.0, rightExtentSs, 0.0, 0.0, null, NO_SYLLABLE_WIDTH_SS, false);
    }

    /** A syllable column with the given notehead right extent, so its center offset can be exercised. */
    private static ElementColumn syllableColumnWithHead(
        double rightExtentSs, double syllableWidthSs, double minGapSs) {

        var syllableColumn = new ElementColumn(
            crotchet(), Collections.emptyList(),
            0.0, rightExtentSs, 0.0, 0.0, SYLLABLE_LYRIC, syllableWidthSs, false);
        syllableColumn.setMinGapToNextSyllableSs(minGapSs);
        return syllableColumn;
    }

    // ==========================================================================
    // Even lift: the whole line lifts evenly, with no local spike
    // ==========================================================================

    @Test
    void testAppliesTheEvenLiftToEveryGap() {
        var columns = List.of(
            plainSyllableColumn(LEAD_SYLLABLE_WIDTH_SS, LEAD_MIN_GAP_SS),
            narrowSyllableColumn(),
            narrowSyllableColumn());
        var springs = List.of(spring(DEFAULT_LINE_REST_SS), spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        assertThat(lifted).hasSize(TWO_GAPS);
        // Gap 0 carries the binding requirement (3.0) and the even lift lands exactly on it.
        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(LEAD_REQUIREMENT_SS, within(TOLERANCE));
        // Gap 1 takes the same 0.5 lift — its own requirement (1.0) is already covered, so no spike.
        assertThat(lifted.get(SECOND_GAP).restSs()).isCloseTo(EVEN_LIFTED_REST_SS, within(TOLERANCE));
        assertThat(lifted.get(SECOND_GAP).restSs()).isGreaterThan(NARROW_PAIR_REQUIREMENT_SS);
    }

    @Test
    void testKeepsStrutsWhileLiftingTheRest() {
        var columns = List.of(
            plainSyllableColumn(LEAD_SYLLABLE_WIDTH_SS, LEAD_MIN_GAP_SS),
            narrowSyllableColumn());
        var springs = List.of(spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        // The lift moves the rest only; the strut is a collision floor and must survive untouched,
        // leaving the gap free to give back down to it under compression.
        var liftedSpring = lifted.get(FIRST_GAP);
        assertThat(liftedSpring.strutSs()).isCloseTo(STRUT_SS, within(TOLERANCE));
        assertThat(liftedSpring.restSs()).isCloseTo(LEAD_REQUIREMENT_SS, within(TOLERANCE));
        assertThat(liftedSpring.naturalLengthSs()).isCloseTo(LEAD_REQUIREMENT_SS, within(TOLERANCE));
    }

    // ==========================================================================
    // Wide syllable, no cap: the whole line lifts evenly to the widest requirement
    // ==========================================================================

    // Five notes at the default rest, a wide syllable under N3 whose two flanking gaps require 4.0.
    // With no cap the largest shortfall (1.5) lifts every gap evenly to 4.0 — uniform note spacing,
    // no local spike.
    @Test
    void testLiftsTheWholeLineEvenlyToTheWidestRequirement() {
        var columns = List.of(
            narrowSyllableColumn(),
            plainSyllableColumn(NARROW_SYLLABLE_WIDTH_SS, WIDE_MIN_GAP_SS),
            plainSyllableColumn(WIDE_SYLLABLE_WIDTH_SS, WIDE_MIN_GAP_SS),
            narrowSyllableColumn(),
            narrowSyllableColumn());
        var springs = List.of(
            spring(DEFAULT_LINE_REST_SS), spring(DEFAULT_LINE_REST_SS),
            spring(DEFAULT_LINE_REST_SS), spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        assertThat(lifted).hasSize(FOUR_GAPS);
        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(UNIFORM_LIFTED_REST_SS, within(TOLERANCE));
        assertThat(lifted.get(SECOND_GAP).restSs()).isCloseTo(UNIFORM_LIFTED_REST_SS, within(TOLERANCE));
        assertThat(lifted.get(THIRD_GAP).restSs()).isCloseTo(UNIFORM_LIFTED_REST_SS, within(TOLERANCE));
        assertThat(lifted.get(FOURTH_GAP).restSs()).isCloseTo(UNIFORM_LIFTED_REST_SS, within(TOLERANCE));
    }

    // ==========================================================================
    // Beamed gap: an absolute shortfall, not a ratio, so the line is not inflated
    // ==========================================================================

    // The semantics change vs. Phase 2: a syllable on a tight beamed gap resolves as a local spike.
    // Its shortfall (0.5) drives only a 0.5 even lift, so the neighbouring normal gap rises to 3.0 —
    // not to the ratio-driven 3.125 the old pass produced from the same tight-gap requirement.
    @Test
    void testResolvesABeamedGapRequirementAsALocalSpikeNotALineWideInflation() {
        var columns = List.of(
            beamedSyllableColumn(BEAM_SYLLABLE_WIDTH_SS, BEAM_MIN_GAP_SS),
            beamedSyllableColumn(BEAM_SYLLABLE_WIDTH_SS, BEAM_MIN_GAP_SS),
            lyricLessColumn());
        // Gap 0 is a tight beamed gap (base 2.0); gap 1 is a normal gap (base 2.5) with no lyric.
        var springs = List.of(spring(BEAM_REST_SS), spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        // The beamed gap spikes locally to its own requirement.
        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(BEAM_GAP_REQUIREMENT_SS, within(TOLERANCE));
        // The normal gap lifts by only the absolute shortfall (BEAM_DRIVEN_LIFT_SS), not the old ratio cap.
        assertThat(lifted.get(SECOND_GAP).restSs())
            .isCloseTo(NORMAL_REST_AFTER_BEAM_LIFT_SS, within(TOLERANCE));
        assertThat(lifted.get(SECOND_GAP).restSs()).isLessThan(PHASE2_RATIO_NORMAL_REST_SS);
    }

    // ==========================================================================
    // No lyric requirement
    // ==========================================================================

    @Test
    void testLeavesSpringsUnchangedForALyricLessLine() {
        var columns = List.of(lyricLessColumn(), lyricLessColumn(), lyricLessColumn());
        var springs = List.of(spring(DEFAULT_LINE_REST_SS), spring(BEAM_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        assertThat(lifted).isEqualTo(springs);
    }

    // A syllable reserves its own overhanging half even when its neighbour bears no syllable, so a
    // lone or first-entered wide lyric spaces the gap immediately rather than waiting for both sides.
    @Test
    void testReservesFootprintWhereOnlyOneColumnBearsASyllable() {
        var columns = List.of(plainSyllableColumn(WIDE_SYLLABLE_WIDTH_SS, WIDE_MIN_GAP_SS), lyricLessColumn());
        var springs = List.of(spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(ONE_SIDED_REQUIREMENT_SS, within(TOLERANCE));
    }

    @Test
    void testReturnsNoSpringsForALineWithNoGaps() {
        var lifted = LyricLift.applyLyricLift(
            Collections.emptyList(), List.of(narrowSyllableColumn()));

        assertThat(lifted).isEmpty();
    }

    // A lift-exempt gap (grace→host) keeps its default rest and imposes no lyric requirement: the wide
    // syllable on the normal gap drives the even lift, and the exempt gap is passed through untouched.
    @Test
    void testLiftExemptGapIsNotLiftedAndDoesNotDriveTheLift() {
        var columns = List.of(
            plainSyllableColumn(WIDE_SYLLABLE_WIDTH_SS, WIDE_MIN_GAP_SS),
            narrowSyllableColumn(),
            lyricLessColumn());
        var springs = List.of(spring(DEFAULT_LINE_REST_SS), liftExemptSpring(LIFT_EXEMPT_INPUT_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        // The wide pair lifts the normal gap; the exempt gap keeps its default rest, unlifted.
        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(UNIFORM_LIFTED_REST_SS, within(TOLERANCE));
        assertThat(lifted.get(SECOND_GAP).restSs()).isCloseTo(LIFT_EXEMPT_INPUT_REST_SS, within(TOLERANCE));
        assertThat(lifted.get(SECOND_GAP).liftExempt()).isTrue();
    }

    // ==========================================================================
    // Grace–host lyric union requirements
    // ==========================================================================

    // A wide grace lyric spills past the grace→host union; that right overhang is carried into the
    // host→next gap and lifts it, while the exempt grace→host gap is passed through untouched.
    @Test
    void testLiftsHostToNextGapByWideGraceLyricOverhang() {
        var columns = List.of(
            graceColumn(GRACE_RIGHT_EXTENT_SS, WIDE_GRACE_SYLLABLE_WIDTH_SS, GRACE_MIN_GAP_SS),
            hostColumn(PLAIN_HEAD_RIGHT_EXTENT_SS),
            lyricLessColumn());
        var springs = List.of(liftExemptSpring(LIFT_EXEMPT_INPUT_REST_SS), spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        // The exempt grace→host gap imposes no requirement and keeps its default rest.
        assertThat(lifted.get(FIRST_GAP).liftExempt()).isTrue();
        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(LIFT_EXEMPT_INPUT_REST_SS, within(TOLERANCE));
        // The host→next gap lifts to the grace lyric's right overhang plus the grace's comfortable gap.
        var expectedRequirementSs = WIDE_GRACE_HOST_RIGHT_EXTENT_SS + GRACE_MIN_GAP_SS;
        assertThat(lifted.get(SECOND_GAP).restSs()).isCloseTo(expectedRequirementSs, within(TOLERANCE));
        assertThat(lifted.get(SECOND_GAP).restSs()).isGreaterThan(DEFAULT_LINE_REST_SS);
    }

    // A grace lyric that fits within the grace→host union overhangs nothing, so the host→next gap
    // gets no lyric requirement and is not lifted.
    @Test
    void testImposesNoHostToNextRequirementUnderNarrowGraceLyric() {
        var columns = List.of(
            graceColumn(GRACE_RIGHT_EXTENT_SS, NARROW_GRACE_SYLLABLE_WIDTH_SS, GRACE_MIN_GAP_SS),
            hostColumn(PLAIN_HEAD_RIGHT_EXTENT_SS),
            lyricLessColumn());
        var springs = List.of(liftExemptSpring(LIFT_EXEMPT_INPUT_REST_SS), spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        assertThat(lifted.get(SECOND_GAP).restSs()).isCloseTo(DEFAULT_LINE_REST_SS, within(TOLERANCE));
    }

    // The lyric requirement centers each syllable on its notehead (excluding accidentals/dots), so
    // when two syllable columns have differing notehead widths the requirement is right + minGap +
    // left with right = w/2 + c_prev and left = w/2 − c_curr — not the naive w + minGap.
    @Test
    void testCentersLyricRequirementOnNoteheadForDifferingNoteheadWidths() {
        var columns = List.of(
            syllableColumnWithHead(WIDE_HEAD_RIGHT_EXTENT_SS, NOTEHEAD_SYLLABLE_WIDTH_SS, NOTEHEAD_MIN_GAP_SS),
            syllableColumnWithHead(PLAIN_HEAD_RIGHT_EXTENT_SS, NOTEHEAD_SYLLABLE_WIDTH_SS, NOTEHEAD_MIN_GAP_SS));
        var springs = List.of(spring(DEFAULT_LINE_REST_SS));

        var lifted = LyricLift.applyLyricLift(springs, columns);

        var cPrev = WIDE_HEAD_RIGHT_EXTENT_SS / 2;
        var cCurr = PLAIN_HEAD_RIGHT_EXTENT_SS / 2;
        var rightSs = NOTEHEAD_SYLLABLE_WIDTH_SS / 2 + cPrev;
        var leftSs = NOTEHEAD_SYLLABLE_WIDTH_SS / 2 - cCurr;
        var expectedRequirementSs = rightSs + NOTEHEAD_MIN_GAP_SS + leftSs;

        assertThat(lifted.get(FIRST_GAP).restSs()).isCloseTo(expectedRequirementSs, within(TOLERANCE));
        // The notehead-centering (c_prev − c_curr) term makes this differ from the naive w + minGap.
        assertThat(expectedRequirementSs)
            .isNotCloseTo(NOTEHEAD_SYLLABLE_WIDTH_SS + NOTEHEAD_MIN_GAP_SS, within(TOLERANCE));
    }
}
