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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;

/**
 * Tests for {@link HorizontalSpacingCalculator#buildSpring} and
 * {@link HorizontalSpacingCalculator#buildSprings} — the per-pair rest / strut / compliance
 * derivation, one test per branch, on synthetic columns (no rendering). Each base rest is a
 * reducing factor of the song's line rest; at the {@code 2.5} default the factors reproduce the
 * legacy absolute gaps for normal (2.5) and grace (2.0) gaps, and yield 2.0 for a tight beam gap
 * (not a legacy value — refs Phase 5a deviation note), and a non-default line rest scales them all
 * proportionally.
 */
class HorizontalSpacingCalculatorSpringTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    /** A plain notehead's right extent; no accidental, so the left extent is 0. */
    private static final double HEAD_RIGHT_EXTENT_SS = 1.0;
    private static final double NO_LEFT_EXTENT_SS = 0.0;

    /**
     * A left extent wide enough that the note-collision floor
     * ({@code prevRight + MIN_COLUMN_GAP_SS + |currLeft|}) exceeds the comfortable rest, so the
     * pair starts with zero compliance.
     */
    private static final double WIDE_GLYPH_LEFT_EXTENT_SS = -3.0;

    /** A grace note head is narrower than a full-size head. */
    private static final double GRACE_RIGHT_EXTENT_SS = 0.75;

    private static final double SYLLABLE_WIDTH_SS = 2.0;
    private static final String SYLLABLE_TEXT = "la";

    /** A stand-in for one lyric space width — the collision floor between non-hyphenated syllables. */
    private static final double SPACE_COLLISION_GAP_SS = 0.5;
    /** A stand-in for the bare hyphen glyph width — the collision floor between hyphenated syllables. */
    private static final double HYPHEN_COLLISION_GAP_SS = 0.375;

    /** The default song line rest; base rests are reducing factors of this (#330). */
    private static final double DEFAULT_LINE_REST_SS = Song.DEFAULT_REST_LENGTH_SS;
    /** A non-default line rest, to prove base rests scale with it rather than a fixed constant. */
    private static final double SCALED_LINE_REST_SS = 4.0;

    private static final double GRACE_HOST_REST_SS = HorizontalSpacingCalculator.GRACE_HOST_REST_SS;
    private static final double BEAM_FACTOR = HorizontalSpacingCalculator.BEAM_GROUP_INTERNAL_REST_FACTOR;

    private static final int FIRST_BEAM_GROUP_ID = 0;
    private static final int SECOND_BEAM_GROUP_ID = 1;

    /** A notehead wider than the plain head, so its centre offset differs and the {@code c} term shows. */
    private static final double WIDE_HEAD_RIGHT_EXTENT_SS = 1.5;

    /** A lone syllable wide enough that its reserved footprint exceeds the note-collision floor. */
    private static final double LONE_SYLLABLE_WIDTH_SS = 3.0;

    // --- Grace–host lyric union geometry (mirrors the plan's shared derivation) ---
    /** Delta-X between the grace and host origins: the grace's right extent plus the fixed rest. */
    private static final double GRACE_HOST_GAP_SS = GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS;
    /** The grace-notehead-left → host-notehead-right union a grace lyric is measured against. */
    private static final double UNION_WIDTH_SS = GRACE_HOST_GAP_SS + HEAD_RIGHT_EXTENT_SS;
    /** A wide grace lyric overhangs the union by this much on each side. */
    private static final double WIDE_GRACE_OVERHANG_SS = 1.0;
    private static final double WIDE_GRACE_SYLLABLE_WIDTH_SS = UNION_WIDTH_SS + 2 * WIDE_GRACE_OVERHANG_SS;
    /** The host's lyric right extent under a wide grace lyric: {@code (w − overhang) − graceHostGap}. */
    private static final double WIDE_GRACE_HOST_RIGHT_EXTENT_SS =
        (WIDE_GRACE_SYLLABLE_WIDTH_SS - WIDE_GRACE_OVERHANG_SS) - GRACE_HOST_GAP_SS;
    /** A grace lyric that fits within the union (≤ the grace→host gap), so it overhangs nothing. */
    private static final double NARROW_GRACE_SYLLABLE_WIDTH_SS = 2.0;

    private static final int SINGLE_GAP = 1;
    private static final int TWO_GAPS = 2;

    private static ElementColumn column(
        ElementType type,
        double leftExtentSs,
        double rightExtentSs,
        @Nullable String syllable,
        double syllableWidthSs,
        boolean beamed) {

        return new ElementColumn(
            type.newInstance(),
            Collections.emptyList(),
            leftExtentSs,
            rightExtentSs,
            0.0, 0.0,
            syllable,
            syllableWidthSs,
            beamed);
    }

    /** A plain, lyric-less, unbeamed crotchet column. */
    private static ElementColumn plainColumn() {
        return column(ElementType.CROTCHET, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);
    }

    /** A beamed semiquaver column (beamCount 2, i.e. shorter than an eighth) in the given group. */
    private static ElementColumn beamedSemiquaverColumn(int beamGroupId) {
        var beamedColumn = column(
            ElementType.SEMIQUAVER, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, true);
        beamedColumn.setBeamGroupId(beamGroupId);
        return beamedColumn;
    }

    /**
     * A non-hyphenated syllable column with the given notehead right extent and syllable width; its
     * collision floor to the next syllable is one space.
     */
    private static ElementColumn syllableColumn(double rightExtentSs, double syllableWidthSs) {
        var syllableColumn = column(
            ElementType.CROTCHET, NO_LEFT_EXTENT_SS, rightExtentSs, SYLLABLE_TEXT, syllableWidthSs, false);
        syllableColumn.setMinCollisionGapToNextSyllableSs(SPACE_COLLISION_GAP_SS);
        return syllableColumn;
    }

    /** A non-hyphenated syllable column with the plain head and default syllable width. */
    private static ElementColumn syllableColumn() {
        return syllableColumn(HEAD_RIGHT_EXTENT_SS, SYLLABLE_WIDTH_SS);
    }

    /** A grace column bearing a lyric of the given width; its host carries no syllable of its own. */
    private static ElementColumn graceSyllableColumn(double syllableWidthSs) {
        var graceColumn = column(
            ElementType.GRACE_QUAVER, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS,
            SYLLABLE_TEXT, syllableWidthSs, false);
        graceColumn.setMinCollisionGapToNextSyllableSs(SPACE_COLLISION_GAP_SS);
        return graceColumn;
    }

    /** A hyphenated syllable column: its collision floor to the next syllable is the bare hyphen. */
    private static ElementColumn hyphenatedSyllableColumn() {
        var syllableColumn = column(
            ElementType.CROTCHET, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS,
            SYLLABLE_TEXT, SYLLABLE_WIDTH_SS, false);
        syllableColumn.setMinCollisionGapToNextSyllableSs(HYPHEN_COLLISION_GAP_SS);
        return syllableColumn;
    }

    // ==========================================================================
    // Base rest branches (at the default line rest, reproducing the legacy gaps)
    // ==========================================================================

    @Test
    void testBuildSpringUsesLineRestForPlainPair() {
        var spring = HorizontalSpacingCalculator.buildSpring(plainColumn(), plainColumn(), DEFAULT_LINE_REST_SS);

        var expectedRestSs = HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS;
        var expectedStrutSs = HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;

        assertThat(spring.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        assertThat(spring.complianceSs()).isCloseTo(expectedRestSs - expectedStrutSs, within(TOLERANCE));
    }

    @Test
    void testBuildSpringUsesBeamFactorForBeamedSemiquaverPairInSameGroup() {
        var spring = HorizontalSpacingCalculator.buildSpring(
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            DEFAULT_LINE_REST_SS);

        var expectedRestSs = HEAD_RIGHT_EXTENT_SS + BEAM_FACTOR * DEFAULT_LINE_REST_SS;
        var expectedStrutSs = HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;

        assertThat(spring.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        assertThat(spring.complianceSs()).isCloseTo(expectedRestSs - expectedStrutSs, within(TOLERANCE));

        // The reduction is carried as the solver weight, so it survives compression, not just rest.
        assertThat(spring.weight()).isEqualTo(BEAM_FACTOR);
        assertThat(spring.rigid()).isFalse();
    }

    // Two adjacent beam groups must not be merged: the gap between them is a normal gap (refs #418).
    @Test
    void testBuildSpringUsesLineRestForBeamedPairInDifferentGroups() {
        var spring = HorizontalSpacingCalculator.buildSpring(
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            beamedSemiquaverColumn(SECOND_BEAM_GROUP_ID),
            DEFAULT_LINE_REST_SS);

        assertThat(spring.restSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
    }

    // A beamed pair touching an eighth note packs at the full line rest; the longer note governs.
    @Test
    void testBuildSpringUsesLineRestForBeamedQuaverPair() {
        var prevColumn = column(ElementType.QUAVER, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, true);
        prevColumn.setBeamGroupId(FIRST_BEAM_GROUP_ID);

        var spring = HorizontalSpacingCalculator.buildSpring(
            prevColumn, beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID), DEFAULT_LINE_REST_SS);

        assertThat(spring.restSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
        // Touching an eighth: a normal gap, so no reduction weight either.
        assertThat(spring.weight()).isEqualTo(Spring.NORMAL_WEIGHT);
    }

    @Test
    void testBuildSpringUsesFixedGraceGapForGraceToHostPair() {
        var graceColumn = column(
            ElementType.GRACE_QUAVER, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS, null, 0.0, false);

        var spring = HorizontalSpacingCalculator.buildSpring(graceColumn, plainColumn(), DEFAULT_LINE_REST_SS);

        var expectedRestSs = GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS;
        var expectedStrutSs = GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;

        assertThat(spring.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        assertThat(spring.complianceSs()).isCloseTo(expectedRestSs - expectedStrutSs, within(TOLERANCE));

        // A grace→host gap is rigid: fixed at its default, never lifted or compressed.
        assertThat(spring.rigid()).isTrue();
    }

    // ==========================================================================
    // Base rests scale with the line rest
    // ==========================================================================

    // A normal or tight-beam factor multiplies the line rest, so a non-default line rest loosens
    // (or tightens) those base rests proportionally: normal ×1, tight beam ×0.8. The grace gap is
    // fixed and excluded here — see testBuildSpringUsesFixedGraceGapIndependentOfLineRest.
    @Test
    void testBuildSpringScalesEveryBaseRestWithTheLineRest() {
        var plain = HorizontalSpacingCalculator.buildSpring(plainColumn(), plainColumn(), SCALED_LINE_REST_SS);
        assertThat(plain.restSs()).isCloseTo(HEAD_RIGHT_EXTENT_SS + SCALED_LINE_REST_SS, within(TOLERANCE));

        var beam = HorizontalSpacingCalculator.buildSpring(
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            SCALED_LINE_REST_SS);
        assertThat(beam.restSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + BEAM_FACTOR * SCALED_LINE_REST_SS, within(TOLERANCE));
    }

    // The grace→host gap never varies with the line rest: it is the same fixed distance at the
    // default rest and at a scaled one.
    @Test
    void testBuildSpringUsesFixedGraceGapIndependentOfLineRest() {
        var graceColumn = column(
            ElementType.GRACE_QUAVER, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS, null, 0.0, false);
        var expectedRestSs = GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS;

        var atDefault = HorizontalSpacingCalculator.buildSpring(
            graceColumn, plainColumn(), DEFAULT_LINE_REST_SS);
        var atScaled = HorizontalSpacingCalculator.buildSpring(
            graceColumn, plainColumn(), SCALED_LINE_REST_SS);

        assertThat(atDefault.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
        assertThat(atScaled.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
    }

    // ==========================================================================
    // Strut branches (independent of the line rest)
    // ==========================================================================

    // A wide left-side glyph pushes the note-collision floor past the comfortable rest, so the gap
    // starts frozen (compliance 0) rather than reporting negative slack.
    @Test
    void testBuildSpringYieldsZeroComplianceWhenNoteCollisionStrutExceedsRest() {
        var currColumn = column(
            ElementType.CROTCHET, WIDE_GLYPH_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);

        var spring = HorizontalSpacingCalculator.buildSpring(plainColumn(), currColumn, DEFAULT_LINE_REST_SS);

        var expectedRestSs = HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS;
        var expectedStrutSs = HEAD_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
            + Math.abs(WIDE_GLYPH_LEFT_EXTENT_SS);

        assertThat(spring.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        assertThat(spring.strutSs()).isGreaterThan(spring.restSs());
        assertThat(spring.complianceSs()).isEqualTo(0.0);
    }

    // Both columns bear a non-hyphenated syllable, so the syllable-collision floor governs the
    // strut: the halves of both syllables plus one lyric space (prev's collision floor).
    @Test
    void testBuildSpringFoldsSyllableCollisionFloorIntoStrutForSyllableBearingPair() {
        var spring = HorizontalSpacingCalculator.buildSpring(
            syllableColumn(), syllableColumn(), DEFAULT_LINE_REST_SS);

        var expectedStrutSs = SYLLABLE_WIDTH_SS / 2.0
            + SPACE_COLLISION_GAP_SS
            + SYLLABLE_WIDTH_SS / 2.0;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // Sanity: the syllable floor, not the note-collision floor, is the binding constraint here.
        assertThat(expectedStrutSs)
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
    }

    // A hyphenated pair packs tighter than a spaced pair: the collision floor is the bare hyphen
    // glyph (prev's collision floor), narrower than a full space, so the two syllables may come
    // closer — but no closer than the hyphen.
    @Test
    void testBuildSpringUsesHyphenCollisionFloorForHyphenatedPair() {
        var spring = HorizontalSpacingCalculator.buildSpring(
            hyphenatedSyllableColumn(), syllableColumn(), DEFAULT_LINE_REST_SS);

        var expectedStrutSs = SYLLABLE_WIDTH_SS / 2.0
            + HYPHEN_COLLISION_GAP_SS
            + SYLLABLE_WIDTH_SS / 2.0;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The hyphen floor is narrower than the space floor a non-hyphenated pair would get.
        assertThat(HYPHEN_COLLISION_GAP_SS).isLessThan(SPACE_COLLISION_GAP_SS);
    }

    // A lone syllable still reserves its own footprint in the collision floor — the notehead-centred
    // half that overhangs toward the gap plus one inter-syllable space — even though its unlyriced
    // neighbour has nothing to collide with. A wide enough lone syllable makes that reservation exceed
    // the note-collision floor, so it is the binding constraint.
    @Test
    void testBuildSpringReservesLoneSyllableFootprintInCollisionFloor() {
        var loneSyllable = syllableColumn(HEAD_RIGHT_EXTENT_SS, LONE_SYLLABLE_WIDTH_SS);

        var spring = HorizontalSpacingCalculator.buildSpring(loneSyllable, plainColumn(), DEFAULT_LINE_REST_SS);

        var noteheadCentreOffsetSs = HEAD_RIGHT_EXTENT_SS / 2;
        var reservedRightSs = LONE_SYLLABLE_WIDTH_SS / 2 + noteheadCentreOffsetSs;
        var expectedStrutSs = reservedRightSs + SPACE_COLLISION_GAP_SS;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The reservation binds over the note-collision floor, proving the lone syllable is not ignored.
        assertThat(expectedStrutSs)
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
    }

    // A glissando on the previous note must clear the next note's left glyphs and keep its
    // minimum visible length; that reservation is folded into the strut, not post-applied.
    @Test
    void testBuildSpringFoldsGlissandoReservationIntoStrut() {
        var prevColumn = plainColumn();
        prevColumn.getElement().setGlissando();

        var spring = HorizontalSpacingCalculator.buildSpring(prevColumn, plainColumn(), DEFAULT_LINE_REST_SS);

        var expectedStrutSs = HEAD_RIGHT_EXTENT_SS - NO_LEFT_EXTENT_SS
            + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The reservation exceeds the note-collision floor, so it is what binds.
        assertThat(expectedStrutSs)
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
        assertThat(spring.complianceSs()).isCloseTo(spring.restSs() - expectedStrutSs, within(TOLERANCE));
    }

    @Test
    void testBuildSpringIgnoresGlissandoReservationWhenPreviousColumnHasNoGlissando() {
        var spring = HorizontalSpacingCalculator.buildSpring(plainColumn(), plainColumn(), DEFAULT_LINE_REST_SS);

        assertThat(spring.strutSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    // ==========================================================================
    // Grace–host lyric union struts (5-arg buildSpring, neighbour-aware)
    // ==========================================================================

    // Bug 1 regression: a wide grace lyric must NOT inflate the grace→host strut. The grace defers
    // its right extent to the host and the host bears no syllable, so the syllable floor is 0 and the
    // strut stays at the note-collision floor — keeping the rigid gap fixed at GRACE_HOST_REST_SS.
    @Test
    void testBuildSpringKeepsGraceToHostStrutAtNoteCollisionFloorUnderWideGraceLyric() {
        var grace = graceSyllableColumn(WIDE_GRACE_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var afterHost = plainColumn();

        var spring = HorizontalSpacingCalculator.buildSpring(
            grace, host, DEFAULT_LINE_REST_SS, null, afterHost);

        // The strut holds at the note-collision floor — the wide grace syllable does not inflate it.
        assertThat(spring.strutSs()).isCloseTo(
            GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
        // The gap is rigid and its rest stays at the fixed grace→host distance.
        assertThat(spring.rigid()).isTrue();
        assertThat(spring.restSs()).isCloseTo(GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS, within(TOLERANCE));
    }

    // Bug 2 fix: the part of a wide grace lyric that spills past the host notehead is attributed to
    // the host, pushing the host→next strut out by the host right extent plus the grace's own
    // inter-syllable gap (the grace, not the empty host, is the gap source).
    @Test
    void testBuildSpringPushesHostToNextStrutOutByGraceLyricOverhangUnderWideGraceLyric() {
        var grace = graceSyllableColumn(WIDE_GRACE_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var next = plainColumn();

        var spring = HorizontalSpacingCalculator.buildSpring(
            host, next, DEFAULT_LINE_REST_SS, grace, null);

        var expectedStrutSs = WIDE_GRACE_HOST_RIGHT_EXTENT_SS + SPACE_COLLISION_GAP_SS;
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The grace overhang binds over the note-collision floor.
        assertThat(expectedStrutSs)
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
    }

    // A grace lyric that fits within the grace→host union overhangs nothing, so it imposes no
    // syllable constraint on either flanking gap — both struts stay at their note-collision floors.
    @Test
    void testBuildSpringImposesNoLyricConstraintOnNeighbourGapsUnderNarrowGraceLyric() {
        var grace = graceSyllableColumn(NARROW_GRACE_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var next = plainColumn();

        var graceToHost = HorizontalSpacingCalculator.buildSpring(
            grace, host, DEFAULT_LINE_REST_SS, null, next);
        var hostToNext = HorizontalSpacingCalculator.buildSpring(
            host, next, DEFAULT_LINE_REST_SS, grace, null);

        assertThat(graceToHost.strutSs()).isCloseTo(
            GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
        assertThat(hostToNext.strutSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    // The syllable floor centres each syllable on its notehead (excluding accidentals/dots), so when
    // two syllable columns have differing notehead widths the floor is right + gap + left with
    // right = w/2 + c_prev and left = w/2 − c_curr — not the naive w + gap that ignores the offsets.
    @Test
    void testBuildSpringCentresSyllableFloorOnNoteheadForDifferingNoteheadWidths() {
        var prev = syllableColumn(WIDE_HEAD_RIGHT_EXTENT_SS, SYLLABLE_WIDTH_SS);
        var curr = syllableColumn(HEAD_RIGHT_EXTENT_SS, SYLLABLE_WIDTH_SS);

        var spring = HorizontalSpacingCalculator.buildSpring(prev, curr, DEFAULT_LINE_REST_SS);

        var cPrev = WIDE_HEAD_RIGHT_EXTENT_SS / 2;
        var cCurr = HEAD_RIGHT_EXTENT_SS / 2;
        var rightSs = SYLLABLE_WIDTH_SS / 2 + cPrev;
        var leftSs = SYLLABLE_WIDTH_SS / 2 - cCurr;
        var expectedStrutSs = rightSs + SPACE_COLLISION_GAP_SS + leftSs;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The notehead-centring (c_prev − c_curr) term makes this differ from the naive w + gap.
        assertThat(expectedStrutSs).isNotCloseTo(SYLLABLE_WIDTH_SS + SPACE_COLLISION_GAP_SS, within(TOLERANCE));
        // The syllable floor binds over the note-collision floor.
        assertThat(expectedStrutSs)
            .isGreaterThan(WIDE_HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
    }

    // ==========================================================================
    // buildSprings (reads the line rest from the line's song)
    // ==========================================================================

    @Test
    void testBuildSpringsReturnsOneSpringPerAdjacentPair() {
        var columns = List.of(plainColumn(), plainColumn(), plainColumn());

        var springs = HorizontalSpacingCalculator.buildSprings(columns, detachedLine());

        assertThat(springs).hasSize(TWO_GAPS);
    }

    @Test
    void testBuildSpringsResolvesBeamInternalRestPerPair() {
        var columns = List.of(
            plainColumn(),
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID));

        var springs = HorizontalSpacingCalculator.buildSprings(columns, detachedLine());

        // Gap 0 enters the beam group from an unbeamed note: a normal gap at the line rest.
        assertThat(springs.get(0).restSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
        // Gap 1 is internal to the beam group, so it takes the reduced beam factor of the line rest.
        assertThat(springs.get(1).restSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + BEAM_FACTOR * DEFAULT_LINE_REST_SS, within(TOLERANCE));
    }

    @Test
    void testBuildSpringsReturnsSingleSpringForTwoColumns() {
        var springs = HorizontalSpacingCalculator.buildSprings(
            List.of(plainColumn(), plainColumn()), detachedLine());

        assertThat(springs).hasSize(SINGLE_GAP);
    }

    @Test
    void testBuildSpringsReturnsNoSpringsForSingleColumn() {
        var springs = HorizontalSpacingCalculator.buildSprings(List.of(plainColumn()), detachedLine());

        assertThat(springs).isEmpty();
    }

    @Test
    void testBuildSpringsReturnsNoSpringsForEmptyColumnList() {
        var springs = HorizontalSpacingCalculator.buildSprings(Collections.emptyList(), detachedLine());

        assertThat(springs).isEmpty();
    }
}
