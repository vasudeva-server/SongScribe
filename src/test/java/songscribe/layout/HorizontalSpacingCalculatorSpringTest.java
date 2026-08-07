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
import static org.mockito.Mockito.when;

import java.awt.Font;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

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

    /** A notehead wider than the plain head, so its center offset differs and the {@code c} term shows. */
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
    /** {@link #SYLLABLE_TEXT}'s first grapheme — narrower than the grace notehead it centers on. */
    private static final double FIRST_GRAPHEME_WIDTH_SS = 0.5;
    /** A grace lyric that fits the union on its own, but not once its minimum melisma follows it. */
    private static final double MELISMA_SPILL_SYLLABLE_WIDTH_SS = 3.5;
    /** What is actually drawn for that lyric: the syllable followed by its minimum-length melisma. */
    private static final double MELISMA_SPILL_CONTENT_WIDTH_SS =
        MELISMA_SPILL_SYLLABLE_WIDTH_SS + LyricLayoutBuilder.MIN_MELISMA_LENGTH_SS;
    /** Verse the spacing path lays out — the one a column's measured syllable belongs to. */
    private static final int MAIN_VERSE = 1;
    /** A verse well past the first, to catch any path that still assumes verse 1. */
    private static final int LATER_VERSE = 3;
    private static final boolean HAS_MELISMA = true;
    private static final boolean NO_MELISMA = false;

    private static final int SINGLE_GAP = 1;
    private static final int TWO_GAPS = 2;

    /** A normal accidental, narrow enough to sit inside the comfortable gap. */
    private static final double ACCIDENTAL_LEFT_EXTENT_SS = -0.625;

    /** A staff position low enough to need ledger lines, so the base extent exceeds the notehead. */
    private static final int TWO_LEDGERS_BELOW_SP = 8;
    /** A negative (leftward) target extent, so the glissando floor exceeds the comfortable rest. */
    private static final double TARGET_LEFT_EXTENT_SS = -2.0;

    /** A dot width added to the plain head, so a dotted column's right extent differs from its
     *  augmentation-excluded right extent — proving the level offset uses the latter. */
    private static final double DOT_AUGMENTATION_WIDTH_SS = 0.5;
    private static final double DOTTED_RIGHT_EXTENT_SS = HEAD_RIGHT_EXTENT_SS + DOT_AUGMENTATION_WIDTH_SS;

    /** Wide enough that a three-column line solves without any compression. */
    private static final double GENEROUS_MARGIN_SS = 100.0;
    /** Narrower than the anchor alone, so not even the struts fit. */
    private static final double IMPOSSIBLE_MARGIN_SS = 1.0;

    /**
     * A step below a boundary margin, small enough that only the boundary itself separates the
     * feasible solve from the infeasible one.
     */
    private static final double BELOW_BOUNDARY_NUDGE_SS = 0.125;

    /** A plain word-final syllable in the verse being laid out — carries no melisma of its own. */
    private static final Lyric PLAIN_SYLLABLE_LYRIC =
        new Lyric(MAIN_VERSE, SYLLABLE_TEXT, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

    private static ElementColumn column(
        ElementType type,
        double leftExtentSs,
        double rightExtentSs,
        @Nullable Lyric lyric,
        double syllableWidthSs,
        boolean beamed) {

        return new ElementColumn(
            type.newInstance(),
            Collections.emptyList(),
            leftExtentSs,
            rightExtentSs,
            0.0, 0.0,
            lyric,
            syllableWidthSs,
            beamed);
    }

    /** A plain, lyric-less, unbeamed crotchet column. */
    private static ElementColumn plainColumn() {
        return column(ElementType.CROTCHET, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);
    }

    /** A lyric-less column of a line-terminating type, sized like a plain head. */
    private static ElementColumn terminalColumn(ElementType type) {
        return column(type, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);
    }

    /**
     * A line whose last element really is {@code terminalColumn}'s element and is the song's
     * auto-maintained terminal. {@code trailingReservationSs} asks the song for that identity rather
     * than merely testing the element's type, so a terminal-typed column that is not the line's own
     * terminal — one ending a non-last line, or a projected clone — still owes the trailing rest.
     */
    private static Line lineTerminatedBy(ElementColumn terminalColumn) {
        var line = detachedLine();
        line.addElement(terminalColumn.getElement());
        when(line.getSong().isAutoMaintainedTerminal(terminalColumn.getElement(), line)).thenReturn(true);
        return line;
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
            ElementType.CROTCHET, NO_LEFT_EXTENT_SS, rightExtentSs,
            PLAIN_SYLLABLE_LYRIC, syllableWidthSs, false);
        syllableColumn.setMinCollisionGapToNextSyllableSs(SPACE_COLLISION_GAP_SS);
        return syllableColumn;
    }

    /** A non-hyphenated syllable column with the plain head and default syllable width. */
    private static ElementColumn syllableColumn() {
        return syllableColumn(HEAD_RIGHT_EXTENT_SS, SYLLABLE_WIDTH_SS);
    }

    /** A grace column bearing a lyric of the given width; its host carries no syllable of its own. */
    private static ElementColumn graceSyllableColumn(double syllableWidthSs) {
        return graceSyllableColumn(syllableWidthSs, PLAIN_SYLLABLE_LYRIC);
    }

    /** As above, for the given lyric — the one the spacing path reads off the column. */
    private static ElementColumn graceSyllableColumn(double syllableWidthSs, Lyric lyric) {
        var graceColumn = column(
            ElementType.GRACE_QUAVER, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS,
            lyric, syllableWidthSs, false);
        graceColumn.setMinCollisionGapToNextSyllableSs(SPACE_COLLISION_GAP_SS);
        graceColumn.setSyllableFirstGraphemeWidthSs(FIRST_GRAPHEME_WIDTH_SS);
        return graceColumn;
    }

    /**
     * A grace column whose element starts the pair's own melisma, as {@code LyricRun.syncGraceHostMelisma}
     * writes it. Pair it with {@link #melismaHostColumn()} — the spacing path reads both lyrics
     * before it reserves anything for the extender. The lyric goes on the column too, since that is
     * where the spacing path reads it from: the element holds every verse and cannot say which one
     * is being laid out.
     */
    private static ElementColumn melismaGraceColumn(double syllableWidthSs) {
        return melismaGraceColumn(syllableWidthSs, MAIN_VERSE);
    }

    /** As above, with the pair's melisma written in {@code verse} — the verse the column carries. */
    private static ElementColumn melismaGraceColumn(double syllableWidthSs, int verse) {
        var graceLyric = new Lyric(verse, SYLLABLE_TEXT, Lyric.Extend.START, Lyric.Syllabic.SINGLE, false);
        var graceColumn = graceSyllableColumn(syllableWidthSs, graceLyric);
        graceColumn.getElement().lyrics.add(graceLyric);
        return graceColumn;
    }

    /** A plain column carrying the text-less STOP that ends its grace's melisma. */
    private static ElementColumn melismaHostColumn() {
        return melismaHostColumn(MAIN_VERSE);
    }

    /** As above, for the same {@code verse} the grace's melisma is written in. */
    private static ElementColumn melismaHostColumn(int verse) {
        var hostLyric = new Lyric(verse, "", Lyric.Extend.STOP, null, false);
        var hostColumn = column(
            ElementType.CROTCHET, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, hostLyric, 0.0, false);
        hostColumn.getElement().lyrics.add(hostLyric);
        return hostColumn;
    }

    // --- Real built columns, for the tests that need true flag/notehead/stem geometry (#560) ---

    /** Staff positions (Sp) far enough apart that a flag at one clears a notehead at the other. */
    private static final int HIGH_STAFF_POSITION_SP = -4;   // top staff line
    private static final int MIDDLE_STAFF_POSITION_SP = -1; // space above the middle line
    private static final int LOW_STAFF_POSITION_SP = 2;     // fourth staff line

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /**
     * A column carrying the geometry the layout really builds — flag bounds, stem length, notehead
     * width — rather than the hand-supplied extents the synthetic helpers above use. Detached, so it
     * has no beam membership and keeps its flag.
     */
    private static ElementColumn builtColumn(
        ElementType type, int staffPositionSp, StaffElement.Direction direction) {

        var element = type.newInstance();
        element.setStaffPosition(staffPositionSp);
        element.setDirection(direction);
        return new ElementColumnBuilder(new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0))
            .buildDetachedColumn(element, Lyric.FIRST_VERSE);
    }

    /** A built grace column with an outgoing glissando, so its reservation floor is armed. */
    private static ElementColumn builtGlissandoGraceColumn(int staffPositionSp) {
        var grace = builtColumn(ElementType.GRACE_QUAVER, staffPositionSp, StaffElement.Direction.UP);
        grace.getElement().setGlissando();
        return grace;
    }

    /** A hyphenated syllable column: its collision floor to the next syllable is the bare hyphen. */
    private static ElementColumn hyphenatedSyllableColumn() {
        var syllableColumn = column(
            ElementType.CROTCHET, NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS,
            PLAIN_SYLLABLE_LYRIC, SYLLABLE_WIDTH_SS, false);
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

        // The reduction is carried as the solver weight, so it survives compression, not just rest.
        assertThat(spring.weight()).isEqualTo(BEAM_FACTOR);
        assertThat(spring.liftExempt()).isFalse();
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
        // The grace allowance floor sits above the note-collision floor, so it governs the strut:
        // the gap may give exactly its allowance and no more.
        var expectedStrutSs = expectedRestSs - HorizontalSpacingCalculator.GRACE_HOST_COMPRESSION_ALLOWANCE_SS;

        assertThat(expectedStrutSs)
            .isGreaterThan(GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
        assertThat(spring.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));

        // The give between where the gap starts and where it may not pass is exactly the allowance.
        assertThat(spring.naturalLengthSs() - spring.strutSs())
            .isCloseTo(HorizontalSpacingCalculator.GRACE_HOST_COMPRESSION_ALLOWANCE_SS, within(TOLERANCE));

        // A grace→host gap takes no lyric lift; it still compresses, bounded by the strut above.
        assertThat(spring.liftExempt()).isTrue();
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
    // Level offset (Phase 5): the spring's non-whitespace component
    // ==========================================================================

    // Excludes augmentation, mirroring the comfortable rest — a dot must not widen the offset any
    // more than it widens the base rest.
    @Test
    void testBuildSpringSetsLevelOffsetToPrevRightExtentExcludingAugmentationForNormalGap() {
        var spring = HorizontalSpacingCalculator.buildSpring(
            dottedColumn(DOTTED_RIGHT_EXTENT_SS), plainColumn(), DEFAULT_LINE_REST_SS);

        assertThat(spring.levelOffsetSs()).isCloseTo(HEAD_RIGHT_EXTENT_SS, within(TOLERANCE));
        assertThat(spring.levelOffsetSs()).isLessThan(DOTTED_RIGHT_EXTENT_SS);
    }

    // A grace→host gap measures its offset to the host notehead, so the grace's own flag must not
    // widen it — the full right extent (no augmentation to exclude on a grace column) is correct here.
    @Test
    void testBuildSpringSetsLevelOffsetToPrevRightExtentForGraceToHostGap() {
        var graceColumn = column(
            ElementType.GRACE_QUAVER, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS, null, 0.0, false);

        var spring = HorizontalSpacingCalculator.buildSpring(graceColumn, plainColumn(), DEFAULT_LINE_REST_SS);

        assertThat(spring.levelOffsetSs()).isCloseTo(GRACE_RIGHT_EXTENT_SS, within(TOLERANCE));
    }

    // The Phase-5 consistency invariant: levelOffset + weight × lineRest reproduces the base rest
    // exactly, for both a normal gap (weight 1) and a tight beam-internal gap (weight BEAM_FACTOR) —
    // whitespace levelling is a strict generalisation of the rest model, not a second spacing scheme.
    @Test
    void testBuildSpringLevelOffsetPlusWeightTimesLineRestReproducesBaseRest() {
        var plainSpring = HorizontalSpacingCalculator.buildSpring(plainColumn(), plainColumn(), DEFAULT_LINE_REST_SS);
        assertThat(plainSpring.levelOffsetSs() + plainSpring.weight() * DEFAULT_LINE_REST_SS)
            .isCloseTo(plainSpring.restSs(), within(TOLERANCE));

        var beamSpring = HorizontalSpacingCalculator.buildSpring(
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            beamedSemiquaverColumn(FIRST_BEAM_GROUP_ID),
            DEFAULT_LINE_REST_SS);
        assertThat(beamSpring.levelOffsetSs() + beamSpring.weight() * DEFAULT_LINE_REST_SS)
            .isCloseTo(beamSpring.restSs(), within(TOLERANCE));
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
        // rest < strut, so the gap starts pinned on its floor with nothing to give.
        assertThat(spring.naturalLengthSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
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

    // A lone syllable still reserves its own footprint in the collision floor — the notehead-centerd
    // half that overhangs toward the gap plus one inter-syllable space — even though its unlyriced
    // neighbour has nothing to collide with. A wide enough lone syllable makes that reservation exceed
    // the note-collision floor, so it is the binding constraint.
    @Test
    void testBuildSpringReservesLoneSyllableFootprintInCollisionFloor() {
        var loneSyllable = syllableColumn(HEAD_RIGHT_EXTENT_SS, LONE_SYLLABLE_WIDTH_SS);

        var spring = HorizontalSpacingCalculator.buildSpring(loneSyllable, plainColumn(), DEFAULT_LINE_REST_SS);

        var noteheadCenterOffsetSs = HEAD_RIGHT_EXTENT_SS / 2;
        var reservedRightSs = LONE_SYLLABLE_WIDTH_SS / 2 + noteheadCenterOffsetSs;
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
    // strut stays on the grace allowance floor, keeping the gap's give at its allowance.
    @Test
    void testBuildSpringKeepsGraceToHostStrutOnTheAllowanceFloorUnderWideGraceLyric() {
        var grace = graceSyllableColumn(WIDE_GRACE_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var afterHost = plainColumn();

        var spring = HorizontalSpacingCalculator.buildSpring(
            grace, host, DEFAULT_LINE_REST_SS, null, afterHost);

        var expectedRestSs = GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS;

        // The strut holds on the allowance floor — the wide grace syllable does not inflate it.
        assertThat(spring.strutSs()).isCloseTo(
            expectedRestSs - HorizontalSpacingCalculator.GRACE_HOST_COMPRESSION_ALLOWANCE_SS, within(TOLERANCE));
        // The gap takes no lyric lift and its rest stays at the fixed grace→host distance.
        assertThat(spring.liftExempt()).isTrue();
        assertThat(spring.restSs()).isCloseTo(expectedRestSs, within(TOLERANCE));
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
    // syllable constraint on either flanking gap — each strut stays on the floor it would have with
    // no lyric at all (the grace allowance floor for grace→host, the note-collision floor for
    // host→next).
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
            GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS
                - HorizontalSpacingCalculator.GRACE_HOST_COMPRESSION_ALLOWANCE_SS,
            within(TOLERANCE));
        assertThat(hostToNext.strutSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    // The drawn melisma is part of the grace lyric's footprint: a syllable that fits the union but
    // whose melisma spills past it pushes the host→next strut out by that spill, so the extender
    // keeps its minimum length instead of being clamped off the following syllable.
    @Test
    void testBuildSpringPushesHostToNextStrutOutByTheGraceMelismaTail() {
        var grace = melismaGraceColumn(MELISMA_SPILL_SYLLABLE_WIDTH_SS);
        var host = melismaHostColumn();
        var next = plainColumn();

        // Precondition: the syllable fits the union alone, and overflows it once the melisma is added.
        assertThat(MELISMA_SPILL_SYLLABLE_WIDTH_SS).isLessThan(UNION_WIDTH_SS);
        assertThat(MELISMA_SPILL_CONTENT_WIDTH_SS).isGreaterThan(UNION_WIDTH_SS);

        var spring = HorizontalSpacingCalculator.buildSpring(
            host, next, DEFAULT_LINE_REST_SS, grace, null);

        // Syllable and melisma are centered on the union, so the melisma ends as far past the union's
        // right edge as the syllable begins before its left edge.
        var melismaEndSs = UNION_WIDTH_SS + (MELISMA_SPILL_CONTENT_WIDTH_SS - UNION_WIDTH_SS) / 2;
        var expectedStrutSs = (melismaEndSs - GRACE_HOST_GAP_SS) + SPACE_COLLISION_GAP_SS;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The melisma tail binds over the note-collision floor.
        assertThat(expectedStrutSs)
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
    }

    // The tail is reserved because the pair really carries a melisma, not because the grace bears a
    // lyric: the same syllable on a pair with no melisma reserves the syllable alone, which here
    // stays under the note-collision floor.
    @Test
    void testBuildSpringReservesNoMelismaTailWhenThePairCarriesNone() {
        var grace = graceSyllableColumn(MELISMA_SPILL_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var next = plainColumn();

        var spring = HorizontalSpacingCalculator.buildSpring(
            host, next, DEFAULT_LINE_REST_SS, grace, null);
        var melismaSpring = HorizontalSpacingCalculator.buildSpring(
            melismaHostColumn(), next, DEFAULT_LINE_REST_SS,
            melismaGraceColumn(MELISMA_SPILL_SYLLABLE_WIDTH_SS), null);

        assertThat(spring.strutSs()).isCloseTo(
            HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
        assertThat(spring.strutSs()).isLessThan(melismaSpring.strutSs());
    }

    // The pair's melisma belongs to whichever language the columns were built for, so the space
    // reserved for it must not depend on that language being the first one. The spacing path reads
    // the lyric off the column for exactly this reason: the elements hold every verse at once and
    // cannot say which is being laid out, so a path that asked them would only ever see verse 1 and
    // would reserve nothing here — packing the following note against a melisma that is drawn anyway.
    @Test
    void testBuildSpringReservesTheMelismaTailWhicheverVerseTheColumnsCarry() {
        var next = plainColumn();

        var firstVerseSpring = HorizontalSpacingCalculator.buildSpring(
            melismaHostColumn(Lyric.FIRST_VERSE), next, DEFAULT_LINE_REST_SS,
            melismaGraceColumn(MELISMA_SPILL_SYLLABLE_WIDTH_SS, Lyric.FIRST_VERSE), null);

        var laterVerseSpring = HorizontalSpacingCalculator.buildSpring(
            melismaHostColumn(LATER_VERSE), next, DEFAULT_LINE_REST_SS,
            melismaGraceColumn(MELISMA_SPILL_SYLLABLE_WIDTH_SS, LATER_VERSE), null);

        assertThat(laterVerseSpring.strutSs())
            .as("a melisma in a later language reserves exactly what the same melisma reserves in the first")
            .isCloseTo(firstVerseSpring.strutSs(), within(TOLERANCE));
        // Guards the comparison above: both must be the melisma-widened strut, not two equal
        // no-melisma struts that would agree for the wrong reason.
        assertThat(laterVerseSpring.strutSs())
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
    }

    // A grace syllable that already spans the union has no melisma drawn, so the pair reserves the
    // syllable alone — the undrawn extender adds nothing to the strut.
    @Test
    void testBuildSpringReservesNoMelismaTailOnceTheSyllableSpansTheUnion() {
        var grace = melismaGraceColumn(WIDE_GRACE_SYLLABLE_WIDTH_SS);
        var host = melismaHostColumn();
        var next = plainColumn();

        var spring = HorizontalSpacingCalculator.buildSpring(
            host, next, DEFAULT_LINE_REST_SS, grace, null);

        var expectedStrutSs = WIDE_GRACE_HOST_RIGHT_EXTENT_SS + SPACE_COLLISION_GAP_SS;
        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
    }

    // The syllable floor centers each syllable on its notehead (excluding accidentals/dots), so when
    // two syllable columns have differing notehead widths the floor is right + gap + left with
    // right = w/2 + c_prev and left = w/2 − c_curr — not the naive w + gap that ignores the offsets.
    @Test
    void testBuildSpringCentersSyllableFloorOnNoteheadForDifferingNoteheadWidths() {
        var prev = syllableColumn(WIDE_HEAD_RIGHT_EXTENT_SS, SYLLABLE_WIDTH_SS);
        var curr = syllableColumn(HEAD_RIGHT_EXTENT_SS, SYLLABLE_WIDTH_SS);

        var spring = HorizontalSpacingCalculator.buildSpring(prev, curr, DEFAULT_LINE_REST_SS);

        var cPrev = WIDE_HEAD_RIGHT_EXTENT_SS / 2;
        var cCurr = HEAD_RIGHT_EXTENT_SS / 2;
        var rightSs = SYLLABLE_WIDTH_SS / 2 + cPrev;
        var leftSs = SYLLABLE_WIDTH_SS / 2 - cCurr;
        var expectedStrutSs = rightSs + SPACE_COLLISION_GAP_SS + leftSs;

        assertThat(spring.strutSs()).isCloseTo(expectedStrutSs, within(TOLERANCE));
        // The notehead-centering (c_prev − c_curr) term makes this differ from the naive w + gap.
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

    // ==========================================================================
    // Comfortable-vs-collision crossover (ported from the retired greedy path's tests)
    // ==========================================================================

    /**
     * The delta-X this pair takes at rest — {@code max(rest, strut)}, the gap layout lays out when
     * the line has room. The comfortable rest measures to the notehead (accidentals and
     * augmentation excluded) while the collision strut uses the full extents, so these tests pin
     * which of the two governs, and that the loser still leaves the glyphs clear.
     */
    private static double naturalGapSs(ElementColumn prev, ElementColumn curr) {
        return HorizontalSpacingCalculator.buildSpring(prev, curr, DEFAULT_LINE_REST_SS).naturalLengthSs();
    }

    /** A column with the plain head and the given left extent (negative for an accidental). */
    private static ElementColumn columnWithLeftExtent(double leftExtentSs) {
        return column(ElementType.CROTCHET, leftExtentSs, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);
    }

    /**
     * A column whose full right extent includes augmentation (a dot or fall) but whose comfortable
     * extent stops at the notehead.
     */
    private static ElementColumn dottedColumn(double rightExtentSs) {
        return new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            NO_LEFT_EXTENT_SS, rightExtentSs, HEAD_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false);
    }

    /** An accidental narrow enough to sit inside the comfortable gap must not shift the head (refs #418). */
    @Test
    void testAccidentalWithinTheComfortableGapDoesNotShiftTheHead() {
        var accidentalGapSs = naturalGapSs(plainColumn(), columnWithLeftExtent(ACCIDENTAL_LEFT_EXTENT_SS));

        assertThat(accidentalGapSs)
            .as("an accidental absorbed by the comfortable gap leaves the head where a plain column sits")
            .isCloseTo(naturalGapSs(plainColumn(), plainColumn()), within(TOLERANCE));
    }

    /**
     * A wide accidental makes the collision strut govern: the head shifts by exactly the overflow
     * and the accidental ends up {@link HorizontalSpacingCalculator#MIN_COLUMN_GAP_SS} clear of the
     * previous column's right edge (refs #418).
     */
    @Test
    void testWideAccidentalMakesTheCollisionStrutGovern() {
        var wideGapSs = naturalGapSs(plainColumn(), columnWithLeftExtent(WIDE_GLYPH_LEFT_EXTENT_SS));

        var comfortableGapSs = HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS;
        var collisionGapSs = HEAD_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
            + Math.abs(WIDE_GLYPH_LEFT_EXTENT_SS);

        assertThat(wideGapSs)
            .as("the collision strut governs once it exceeds the comfortable rest")
            .isCloseTo(collisionGapSs, within(TOLERANCE));
        assertThat(wideGapSs - comfortableGapSs)
            .as("the head shifts by exactly the collision overflow")
            .isCloseTo(collisionGapSs - comfortableGapSs, within(TOLERANCE));
        assertThat(wideGapSs + WIDE_GLYPH_LEFT_EXTENT_SS)
            .as("the accidental's left edge clears the previous right edge by the minimum gap")
            .isCloseTo(
                HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    /**
     * The crossover itself: at the left extent where the collision strut exactly equals the
     * comfortable rest, the head still lands at the comfortable position and the accidental is
     * exactly clear. Pins the {@code max(rest, strut)} decision at its tipping point (refs #418).
     */
    @Test
    void testAccidentalAtTheCrossoverLeavesTheAccidentalExactlyClear() {
        var boundaryLeftExtentSs = -(DEFAULT_LINE_REST_SS - HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);

        var boundaryGapSs = naturalGapSs(plainColumn(), columnWithLeftExtent(boundaryLeftExtentSs));

        assertThat(boundaryGapSs)
            .as("rest and strut coincide, so the head sits at the comfortable gap")
            .isCloseTo(HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
        assertThat(boundaryGapSs + boundaryLeftExtentSs)
            .as("the accidental's left edge is exactly the minimum gap clear")
            .isCloseTo(
                HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    /** A dot absorbed by the comfortable gap must not shift the next head (refs #441). */
    @Test
    void testDotWithinTheComfortableGapDoesNotShiftTheNextHead() {
        var dottedGapSs = naturalGapSs(
            dottedColumn(HEAD_RIGHT_EXTENT_SS + NoteGeometry.DOT_SPACING_SS), plainColumn());

        assertThat(dottedGapSs)
            .as("augmentation is excluded from the comfortable rest, so the next head does not move")
            .isCloseTo(naturalGapSs(plainColumn(), plainColumn()), within(TOLERANCE));
    }

    /**
     * A dot wide enough that the collision strut (augmentation included) exceeds the comfortable
     * rest shifts the next head by exactly the overflow, leaving the dot the minimum gap clear
     * (refs #441).
     */
    @Test
    void testWideDotMakesTheCollisionStrutGovern() {
        var wideDotRightExtentSs = HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS;

        var dottedGapSs = naturalGapSs(dottedColumn(wideDotRightExtentSs), plainColumn());

        var collisionGapSs = wideDotRightExtentSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        assertThat(dottedGapSs).isCloseTo(collisionGapSs, within(TOLERANCE));
        assertThat(dottedGapSs - wideDotRightExtentSs)
            .as("the dot's right edge clears the next head by the minimum gap")
            .isCloseTo(HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    /**
     * The dot-side crossover, mirroring the accidental one: where the collision strut exactly
     * equals the comfortable rest, the next head lands at the comfortable position and the dot is
     * exactly clear (refs #441).
     */
    @Test
    void testDotAtTheCrossoverLeavesTheDotExactlyClear() {
        var dotWidthSs = DEFAULT_LINE_REST_SS - HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        var dotRightExtentSs = HEAD_RIGHT_EXTENT_SS + dotWidthSs;

        var dottedGapSs = naturalGapSs(dottedColumn(dotRightExtentSs), plainColumn());

        assertThat(dottedGapSs)
            .as("rest and strut coincide, so the next head sits at the comfortable gap")
            .isCloseTo(HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
        assertThat(dottedGapSs - dotRightExtentSs)
            .as("the dot's right edge is exactly the minimum gap clear")
            .isCloseTo(HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS, within(TOLERANCE));
    }

    /**
     * A glissando from a grace to a host bearing an accidental widens the otherwise-fixed grace→host
     * gap, so the glissando still reaches its minimum visible length against the accidental's edge
     * (refs #443).
     */
    @Test
    void testGraceGlissandoToAnAccidentalHostReservesGlissandoRoom() {
        var graceElement = ElementType.GRACE_QUAVER.newInstance();
        graceElement.setGlissando();
        var graceColumn = new ElementColumn(
            graceElement, Collections.emptyList(),
            NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false);
        var plainGraceColumn = column(
            ElementType.GRACE_QUAVER, NO_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS, null, 0.0, false);
        var hostColumn = columnWithLeftExtent(ACCIDENTAL_LEFT_EXTENT_SS);

        var glissandoGapSs = naturalGapSs(graceColumn, hostColumn);
        var plainGapSs = naturalGapSs(plainGraceColumn, hostColumn);

        var reservedSs = glissandoGapSs + ACCIDENTAL_LEFT_EXTENT_SS - GRACE_RIGHT_EXTENT_SS;
        assertThat(reservedSs)
            .as("the gap to the accidental's edge holds the minimum glissando reservation")
            .isGreaterThanOrEqualTo(NoteGeometry.MIN_GLISSANDO_RESERVATION_SS - TOLERANCE);
        assertThat(glissandoGapSs)
            .as("the glissando is what widens the gap — without it the host packs tighter")
            .isGreaterThan(plainGapSs);
    }

    // ==========================================================================
    // Anchoring and the whole-line solve
    // ==========================================================================

    /** Returns the solved gap lengths, failing with a clear message rather than an NPE. */
    private static double[] solvedGapLengthsSs(SpringSolveResult result) {
        var gapLengthsSs = result.gapLengthsSs();

        assertThat(gapLengthsSs).as("expected a solved result, but the solver reported infeasible").isNotNull();

        return gapLengthsSs;
    }

    /**
     * The anchor places the first column's origin — the notehead's left edge — at the first-note
     * offset, and an accidental does not move it. LilyPond measures to the note column's reference
     * point and lets the accidental hang left of it into the gap the header leaves
     * (refs #684, reverses #121).
     */
    @Test
    void testAnchorPlacesTheFirstNoteheadAtTheFirstNoteOffsetDespiteAnAccidental() {
        var line = detachedLine();
        var accidentalColumn = column(
            ElementType.CROTCHET, WIDE_GLYPH_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);

        assertThat(accidentalColumn.getLeftExtentSs())
            .as("the fixture must actually carry a glyph left of the notehead")
            .isNegative();

        assertThat(HorizontalSpacingCalculator.calculateAnchorXSs(accidentalColumn, line))
            .as("the notehead's left edge sits at the first-note offset, accidental or not")
            .isCloseTo(HorizontalSpacingCalculator.calculateFirstElementXSs(line), within(TOLERANCE));
    }

    /**
     * A first syllable wide enough to overhang the staff header pushes the anchor right, so the
     * lyric does not collide with the clef or key signature.
     */
    @Test
    void testAnchorIsPushedRightByAWideFirstSyllable() {
        var line = detachedLine();
        var wideSyllableColumn = syllableColumn(HEAD_RIGHT_EXTENT_SS, LONE_SYLLABLE_WIDTH_SS);

        var plainAnchorXSs = HorizontalSpacingCalculator.calculateAnchorXSs(plainColumn(), line);
        var syllableAnchorXSs = HorizontalSpacingCalculator.calculateAnchorXSs(wideSyllableColumn, line);

        assertThat(syllableAnchorXSs)
            .as("a wide first syllable pushes the anchor past the plain-column anchor")
            .isGreaterThan(plainAnchorXSs);
    }

    /** A narrow first syllable that clears the header on its own leaves the anchor untouched. */
    @Test
    void testAnchorIgnoresANarrowFirstSyllable() {
        var line = detachedLine();
        var narrowSyllableColumn = syllableColumn(WIDE_HEAD_RIGHT_EXTENT_SS, 0.0);

        assertThat(HorizontalSpacingCalculator.calculateAnchorXSs(narrowSyllableColumn, line))
            .isCloseTo(
                HorizontalSpacingCalculator.calculateAnchorXSs(plainColumn(), line), within(TOLERANCE));
    }

    /**
     * The whole-line solve wires build → lift → anchor → solve together: on a line with room to
     * spare every gap keeps its natural length, and the columns lay out from the anchor. This is
     * the seam the per-stage tests above cannot cover.
     */
    @Test
    void testSolveLineLaysOutUncompressedColumnsFromTheAnchor() {
        var line = detachedLine();
        var columns = List.of(plainColumn(), plainColumn(), plainColumn());

        var solution = HorizontalSpacingCalculator.solveLine(columns, line, GENEROUS_MARGIN_SS);

        assertThat(solution.isInfeasible()).isFalse();
        assertThat(solution.firstXSs())
            .isCloseTo(HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line), within(TOLERANCE));

        var gapLengthsSs = solvedGapLengthsSs(solution.result());
        assertThat(gapLengthsSs).hasSize(TWO_GAPS);

        var naturalGapSs = HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS;
        assertThat(gapLengthsSs[0]).isCloseTo(naturalGapSs, within(TOLERANCE));
        assertThat(gapLengthsSs[1]).isCloseTo(naturalGapSs, within(TOLERANCE));
    }

    /**
     * The whole-line solve runs the optical-spacing pass, not just build → lift → solve. Every other
     * {@code solveLine} test here uses the synthetic column helpers, whose zero-height spans and
     * shared staff position make every correction structurally zero — so none of them would notice
     * if {@link OpticalSpacing#applyCorrections} were dropped from the pipeline. This one uses real
     * built geometry chosen to make the opposite-stem term fire, and pins the solved gap to the
     * uncorrected natural length *plus* that term.
     */
    @Test
    void testSolveLineAppliesOpticalCorrectionsToTheSolvedGap() {
        // An up-stem low on the staff and a down-stem high on it: their spans overlap deeply, which
        // is what arms the opposite-stem correction.
        var prev = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);
        var curr = builtColumn(ElementType.CROTCHET, HIGH_STAFF_POSITION_SP, StaffElement.Direction.DOWN);
        var columns = List.of(prev, curr);

        var correctionSs = OpticalSpacing.oppositeStemCorrectionSs(prev, curr);

        // Guard the fixture: a zero correction here would make the assertion below vacuous.
        assertThat(correctionSs).isNotEqualTo(0.0);

        var uncorrectedNaturalSs = HorizontalSpacingCalculator
            .buildSpring(prev, curr, DEFAULT_LINE_REST_SS)
            .naturalLengthSs();

        var solution = HorizontalSpacingCalculator.solveLine(columns, detachedLine(), GENEROUS_MARGIN_SS);

        assertThat(solution.isInfeasible()).isFalse();

        var gapLengthsSs = solvedGapLengthsSs(solution.result());
        assertThat(gapLengthsSs).hasSize(1);
        assertThat(gapLengthsSs[0]).isCloseTo(uncorrectedNaturalSs + correctionSs, within(TOLERANCE));
    }

    /** A margin too narrow for even the struts makes the whole-line solve report infeasible. */
    @Test
    void testSolveLineReportsInfeasibleWhenTheMarginCannotHoldTheStruts() {
        var columns = List.of(plainColumn(), plainColumn(), plainColumn());

        var solution = HorizontalSpacingCalculator.solveLine(columns, detachedLine(), IMPOSSIBLE_MARGIN_SS);

        assertThat(solution.isInfeasible()).isTrue();
        assertThat(solution.result().gapLengthsSs()).isNull();
    }

    /**
     * The narrowest margin a two-column line fits in: the anchor, the gap frozen on its strut, and
     * whatever {@link HorizontalSpacingCalculator#trailingReservationSs} keeps clear past the last
     * column's origin. Solving at this margin and a nudge below it brackets the reservation from
     * both sides, so a reservation of the wrong <em>magnitude</em> — not merely a missing one —
     * fails the test.
     */
    private static double narrowestFeasibleMarginSs(List<ElementColumn> columns, Line line) {
        var strutGapSs = HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        return HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line)
            + strutGapSs
            + HorizontalSpacingCalculator.trailingReservationSs(columns.getLast(), line);
    }

    /**
     * A non-terminal last element must stop a full line rest short of the margin rather than sitting
     * flush against it (refs #617), so the line's narrowest feasible margin grows by exactly that
     * rest. Bracketing it — feasible at the boundary, infeasible a nudge below — pins the
     * reservation's magnitude, which the boundary alone would not.
     */
    @Test
    void testSolveLineReservesALineRestAfterANonTerminalLastColumn() {
        var line = detachedLine();
        var columns = List.of(plainColumn(), plainColumn());
        var strutGapSs = HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        var boundaryMarginSs = HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line)
            + strutGapSs
            + HEAD_RIGHT_EXTENT_SS
            + DEFAULT_LINE_REST_SS;

        assertThat(narrowestFeasibleMarginSs(columns, line))
            .as("the reservation past a non-terminal last column is its extent plus a full line rest")
            .isCloseTo(boundaryMarginSs, within(TOLERANCE));
        assertThat(HorizontalSpacingCalculator.solveLine(columns, line, boundaryMarginSs).isInfeasible())
            .isFalse();
        assertThat(HorizontalSpacingCalculator
            .solveLine(columns, line, boundaryMarginSs - BELOW_BOUNDARY_NUDGE_SS).isInfeasible())
            .as("a margin below the reserved boundary must not fit")
            .isTrue();
    }

    /**
     * The same bracketing as {@link #testSolveLineReservesALineRestAfterANonTerminalLastColumn}, but
     * the last column carries the auto-maintained terminal barline — it is pinned flush-right by
     * {@code LayoutEngine.positionTerminalFlushRight} rather than reserved a gap here, so its
     * boundary is its bare right extent, one full line rest tighter.
     */
    @Test
    void testSolveLineDoesNotReserveAGapAfterATerminalLastColumn() {
        var terminal = terminalColumn(ElementType.FINAL_DOUBLE_BARLINE);
        var line = lineTerminatedBy(terminal);
        var columns = List.of(plainColumn(), terminal);
        var strutGapSs = HEAD_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        var boundaryMarginSs = HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line)
            + strutGapSs
            + HEAD_RIGHT_EXTENT_SS;

        assertThat(narrowestFeasibleMarginSs(columns, line))
            .as("a terminal last column reserves its bare right extent, with no line rest added")
            .isCloseTo(boundaryMarginSs, within(TOLERANCE));
        assertThat(HorizontalSpacingCalculator.solveLine(columns, line, boundaryMarginSs).isInfeasible())
            .isFalse();
        assertThat(HorizontalSpacingCalculator
            .solveLine(columns, line, boundaryMarginSs - BELOW_BOUNDARY_NUDGE_SS).isInfeasible())
            .as("a margin below the terminal's own extent must not fit")
            .isTrue();
    }

    /**
     * Both {@link ElementType#isValidSongTerminal} types must be recognised, not just the final double
     * barline the boundary tests above use — a repeat sign ends a line just as flush.
     */
    @Test
    void testTrailingReservationSsExcludesTheLineRestForEveryValidTerminal() {
        for (var type : List.of(ElementType.FINAL_DOUBLE_BARLINE, ElementType.REPEAT_RIGHT)) {
            var terminal = terminalColumn(type);

            assertThat(HorizontalSpacingCalculator.trailingReservationSs(terminal, lineTerminatedBy(terminal)))
                .as("%s is the line's auto-maintained terminal, so it reserves its extent alone", type)
                .isCloseTo(HEAD_RIGHT_EXTENT_SS, within(TOLERANCE));
        }
    }

    /**
     * A terminal-typed column that is <em>not</em> the line's auto-maintained terminal — the shape a
     * final barline takes on a non-last line — is not pinned flush-right, so it still owes the
     * trailing rest. A bare {@code isValidSongTerminal()} type check would wrongly waive it.
     */
    @Test
    void testTrailingReservationSsAddsTheLineRestForATerminalTypeThatIsNotTheLinesTerminal() {
        var barline = terminalColumn(ElementType.FINAL_DOUBLE_BARLINE);

        assertThat(HorizontalSpacingCalculator.trailingReservationSs(barline, detachedLine()))
            .isCloseTo(HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
    }

    /** Every non-terminal element type reserves a full line rest past its own extent. */
    @Test
    void testTrailingReservationSsAddsTheLineRestForANonTerminal() {
        assertThat(HorizontalSpacingCalculator.trailingReservationSs(plainColumn(), detachedLine()))
            .isCloseTo(HEAD_RIGHT_EXTENT_SS + DEFAULT_LINE_REST_SS, within(TOLERANCE));
    }

    /**
     * The reservation is read off the song's line rest, not a fixed constant, so a song with a
     * non-default rest reserves proportionally more.
     */
    @Test
    void testTrailingReservationSsScalesWithTheSongsLineRest() {
        var line = detachedLine();
        when(line.getSong().getDefaultRestLengthSs()).thenReturn(SCALED_LINE_REST_SS);

        assertThat(HorizontalSpacingCalculator.trailingReservationSs(plainColumn(), line))
            .isCloseTo(HEAD_RIGHT_EXTENT_SS + SCALED_LINE_REST_SS, within(TOLERANCE));
    }

    /**
     * A one-column line has no gaps to compress, so the reservation is the only thing standing
     * between the anchor and the margin — the degenerate case the two-column boundary tests cannot
     * reach.
     */
    @Test
    void testSolveLineReservesALineRestOnASingleColumnLine() {
        var line = detachedLine();
        var columns = List.of(plainColumn());
        var boundaryMarginSs = HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line)
            + HEAD_RIGHT_EXTENT_SS
            + DEFAULT_LINE_REST_SS;

        assertThat(HorizontalSpacingCalculator.solveLine(columns, line, boundaryMarginSs).isInfeasible())
            .isFalse();
        assertThat(HorizontalSpacingCalculator
            .solveLine(columns, line, boundaryMarginSs - BELOW_BOUNDARY_NUDGE_SS).isInfeasible())
            .as("the lone column must still stop a full line rest short of the margin")
            .isTrue();
    }

    /**
     * A glissando leaving a ledger-line note reserves against the column's own right extent, not
     * the wider ledger base extent — ledger lines are drawn through the gap and must not push the
     * next note away (refs #443).
     */
    @Test
    void testGlissandoFromALedgerNoteReservesAgainstTheColumnExtentNotTheLedger() {
        var ledgerElement = ElementType.CROTCHET.newInstance();
        ledgerElement.setGlissando();
        ledgerElement.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var ledgerRightSs = NoteGeometry.getLedgerLineBaseExtentSs(ledgerElement).rightSs();
        // Half the ledger base right: narrower, so the two candidate floors are distinguishable,
        // while staying a valid (positive) right extent.
        var columnRightExtentSs = ledgerRightSs / 2;

        var glissandoColumn = new ElementColumn(
            ledgerElement, Collections.emptyList(),
            NO_LEFT_EXTENT_SS, columnRightExtentSs, 0.0, 0.0, null, 0.0, false);
        var targetColumn = column(
            ElementType.CROTCHET, TARGET_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, null, 0.0, false);

        var spring = HorizontalSpacingCalculator.buildSpring(
            glissandoColumn, targetColumn, DEFAULT_LINE_REST_SS);

        var columnFloorSs =
            columnRightExtentSs - TARGET_LEFT_EXTENT_SS + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        var ledgerInflatedFloorSs =
            ledgerRightSs - TARGET_LEFT_EXTENT_SS + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;

        assertThat(spring.strutSs()).isCloseTo(columnFloorSs, within(TOLERANCE));
        assertThat(spring.strutSs())
            .as("the ledger base extent must not inflate the glissando reservation")
            .isLessThan(ledgerInflatedFloorSs);
    }

    /**
     * A glissando arriving at a ledger-line note reserves against that column's own left extent,
     * not the wider ledger base extent (refs #443).
     */
    @Test
    void testGlissandoIntoALedgerNoteReservesAgainstTheColumnExtentNotTheLedger() {
        var glissandoElement = ElementType.CROTCHET.newInstance();
        glissandoElement.setGlissando();

        var ledgerElement = ElementType.CROTCHET.newInstance();
        ledgerElement.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        // Left extents are negative; half the ledger base keeps the sign while making the floors
        // differ, so the test can tell which extent governed.
        var ledgerLeftSs = NoteGeometry.getLedgerLineBaseExtentSs(ledgerElement).leftSs();
        var columnLeftExtentSs = ledgerLeftSs / 2;

        var glissandoColumn = new ElementColumn(
            glissandoElement, Collections.emptyList(),
            NO_LEFT_EXTENT_SS, HEAD_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false);
        var ledgerColumn = new ElementColumn(
            ledgerElement, Collections.emptyList(),
            columnLeftExtentSs, HEAD_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false);

        var spring = HorizontalSpacingCalculator.buildSpring(
            glissandoColumn, ledgerColumn, DEFAULT_LINE_REST_SS);

        var columnFloorSs =
            HEAD_RIGHT_EXTENT_SS - columnLeftExtentSs + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        var ledgerInflatedFloorSs =
            HEAD_RIGHT_EXTENT_SS - ledgerLeftSs + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;

        assertThat(spring.strutSs()).isCloseTo(columnFloorSs, within(TOLERANCE));
        assertThat(spring.strutSs())
            .as("the ledger base extent must not inflate the glissando reservation")
            .isLessThan(ledgerInflatedFloorSs);
    }

    /**
     * An unpaired grace — the line's last column, before its host is entered — has no grace→host
     * union to spill past, so only its own extent bounds the overhang (guards against dereferencing
     * a null host).
     */
    @Test
    void testGraceLyricOffsetTreatsAMissingHostAsNoUnion() {
        var grace = graceSyllableColumn(WIDE_GRACE_SYLLABLE_WIDTH_SS);
        var unionWithoutHostSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, null);

        var leftOffsetSs = HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
            WIDE_GRACE_SYLLABLE_WIDTH_SS, FIRST_GRAPHEME_WIDTH_SS, unionWithoutHostSs, grace,
            NO_MELISMA);

        assertThat(unionWithoutHostSs)
            .isCloseTo(GRACE_RIGHT_EXTENT_SS + GRACE_HOST_REST_SS, within(TOLERANCE));
        assertThat(leftOffsetSs)
            .isCloseTo(-(WIDE_GRACE_SYLLABLE_WIDTH_SS - unionWithoutHostSs) / 2, within(TOLERANCE));
    }

    /**
     * A grace lyric that fits the union centers its first grapheme on the grace notehead, so the
     * syllable begins at the grace note rather than being left-anchored on it.
     */
    @Test
    void testGraceLyricOffsetCentersTheFirstGraphemeOnTheNotehead() {
        var grace = graceSyllableColumn(NARROW_GRACE_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var unionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, host);

        var leftOffsetSs = HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
            NARROW_GRACE_SYLLABLE_WIDTH_SS, FIRST_GRAPHEME_WIDTH_SS, unionWidthSs, grace, NO_MELISMA);

        assertThat(leftOffsetSs)
            .isCloseTo((GRACE_RIGHT_EXTENT_SS - FIRST_GRAPHEME_WIDTH_SS) / 2, within(TOLERANCE));
    }

    /**
     * A grace lyric that fits the union on its own but not once its melisma is added is centered —
     * syllable and melisma together — on the union, so the pair's ink overhangs it equally on both
     * sides. Without the melisma the same syllable keeps the first-grapheme centering.
     */
    @Test
    void testGraceLyricOffsetCentersTheSyllableAndItsMelismaOnTheUnion() {
        var grace = graceSyllableColumn(MELISMA_SPILL_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var unionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, host);

        // Precondition: the syllable fits the union alone, and overflows it once the melisma is added.
        assertThat(MELISMA_SPILL_SYLLABLE_WIDTH_SS).isLessThan(UNION_WIDTH_SS);
        assertThat(MELISMA_SPILL_CONTENT_WIDTH_SS).isGreaterThan(UNION_WIDTH_SS);

        var leftOffsetSs = HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
            MELISMA_SPILL_SYLLABLE_WIDTH_SS, FIRST_GRAPHEME_WIDTH_SS, unionWidthSs, grace,
            HAS_MELISMA);

        assertThat(leftOffsetSs)
            .isCloseTo(-(MELISMA_SPILL_CONTENT_WIDTH_SS - UNION_WIDTH_SS) / 2, within(TOLERANCE));

        var noMelismaOffsetSs = HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
            MELISMA_SPILL_SYLLABLE_WIDTH_SS, FIRST_GRAPHEME_WIDTH_SS, unionWidthSs, grace,
            NO_MELISMA);

        assertThat(noMelismaOffsetSs)
            .as("only the melisma pushes the syllable off the first-grapheme centering")
            .isCloseTo((GRACE_RIGHT_EXTENT_SS - FIRST_GRAPHEME_WIDTH_SS) / 2, within(TOLERANCE));
    }

    /**
     * A grace syllable at least as wide as the union is centered on the union, and its melisma —
     * which would start where the syllable already ends — is not drawn, so nothing past the syllable
     * is reserved.
     */
    @Test
    void testGraceLyricOffsetIgnoresTheMelismaOnceTheSyllableSpansTheUnion() {
        var grace = graceSyllableColumn(WIDE_GRACE_SYLLABLE_WIDTH_SS);
        var host = plainColumn();
        var unionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, host);

        assertThat(HorizontalSpacingCalculator.graceSyllableSpansUnion(
            WIDE_GRACE_SYLLABLE_WIDTH_SS, unionWidthSs)).isTrue();

        var leftOffsetSs = HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
            WIDE_GRACE_SYLLABLE_WIDTH_SS, FIRST_GRAPHEME_WIDTH_SS, unionWidthSs, grace, HAS_MELISMA);

        assertThat(leftOffsetSs).isCloseTo(-WIDE_GRACE_OVERHANG_SS, within(TOLERANCE));
    }

    /**
     * The boundary of the spans-the-union rule: a syllable exactly as wide as the union already ends
     * at the host's notehead right edge, which is where the pair's melisma would have ended. An
     * exclusive comparison would place the syllable as though a melisma followed it and draw that
     * melisma with zero length.
     */
    @Test
    void testGraceSyllableExactlyAsWideAsTheUnionSpansIt() {
        var grace = graceSyllableColumn(UNION_WIDTH_SS);
        var host = plainColumn();
        var unionWidthSs = HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, host);

        // Precondition: the column geometry really does put the syllable on the boundary.
        assertThat(unionWidthSs).isCloseTo(UNION_WIDTH_SS, within(TOLERANCE));

        assertThat(HorizontalSpacingCalculator.graceSyllableSpansUnion(UNION_WIDTH_SS, unionWidthSs))
            .as("a syllable exactly as wide as the union already reaches the host")
            .isTrue();

        var leftOffsetSs = HorizontalSpacingCalculator.graceLyricLeftOffsetSs(
            UNION_WIDTH_SS, FIRST_GRAPHEME_WIDTH_SS, unionWidthSs, grace, HAS_MELISMA);

        assertThat(leftOffsetSs)
            .as("the syllable sits on the union with no melisma shifting it left")
            .isCloseTo(0.0, within(TOLERANCE));
    }

    // ==========================================================================
    // The pair's own melisma
    //
    // pairCarriesGraceHostMelisma decides whether anything past the syllable is reserved and placed,
    // so each of its conditions is exercised on its own: a pair one condition short carries no
    // melisma of its own, and treating it as though it did would reserve room for a line that is
    // never drawn — or, for the CONTINUE case, cut a longer melisma off at the immediate host.
    // ==========================================================================

    /** The grace's half of the pair's own melisma: a non-hyphenated syllable that starts it. */
    private static Lyric graceMelismaStart() {
        return new Lyric(MAIN_VERSE, SYLLABLE_TEXT, Lyric.Extend.START, Lyric.Syllabic.SINGLE, false);
    }

    /** The host's half: the text-less STOP carrier that ends it. */
    private static Lyric hostMelismaStop() {
        return new Lyric(MAIN_VERSE, "", Lyric.Extend.STOP, null, false);
    }

    @Test
    void testPairCarriesItsOwnMelismaWhenTheGraceStartsItAndTheHostStopsIt() {
        assertThat(HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(
            graceMelismaStart(), hostMelismaStop())).isTrue();
    }

    @Test
    void testPairCarriesNoMelismaWithoutAGraceLyric() {
        assertThat(HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(null, hostMelismaStop()))
            .as("a host STOP alone belongs to a melisma that started somewhere else")
            .isFalse();
    }

    @Test
    void testPairCarriesNoMelismaWhenTheGraceStartsNone() {
        var plainGraceLyric =
            new Lyric(MAIN_VERSE, SYLLABLE_TEXT, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        assertThat(HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(
            plainGraceLyric, hostMelismaStop()))
            .as("a grace syllable with no START opens no melisma of its own")
            .isFalse();
    }

    @Test
    void testPairCarriesNoMelismaWhenTheGraceSyllableIsHyphenated() {
        var hyphenatedGraceLyric =
            new Lyric(MAIN_VERSE, SYLLABLE_TEXT, Lyric.Extend.START, Lyric.Syllabic.BEGIN, false);

        assertThat(HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(
            hyphenatedGraceLyric, hostMelismaStop()))
            .as("a hyphenated grace syllable draws a hyphen to the next syllable, not a melisma")
            .isFalse();
    }

    @Test
    void testPairCarriesNoMelismaWithoutAHostLyric() {
        assertThat(HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(graceMelismaStart(), null))
            .as("with nothing on the host to end it, the melisma runs past the pair")
            .isFalse();
    }

    @Test
    void testPairCarriesNoMelismaWhenTheHostCarriesItOnward() {
        var hostCarryOn = new Lyric(MAIN_VERSE, "", Lyric.Extend.CONTINUE, null, false);

        assertThat(HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(
            graceMelismaStart(), hostCarryOn))
            .as("a host that carries the melisma onward ends it somewhere past the pair")
            .isFalse();
    }

    // ==========================================================================
    // Vertical-aware grace ink (refs #560)
    //
    // These use real built columns rather than the synthetic ones above, because the behaviour is
    // driven by geometry the synthetic constructor cannot express: the flag's own vertical bounds and
    // the notehead width underneath it.
    // ==========================================================================

    /**
     * A glissando grace whose flag hangs well above its host's notehead reserves only its notehead,
     * so both the ideal gap and the glissando floor shrink by the flag's width. This is the
     * screenshot case in #560: a grace on the top staff line with its host four steps below.
     */
    @Test
    void testGraceHostGapDropsAFlagHangingClearOfTheHost() {
        var grace = builtGlissandoGraceColumn(HIGH_STAFF_POSITION_SP);
        var host = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);

        var spring = HorizontalSpacingCalculator.buildSpring(grace, host, DEFAULT_LINE_REST_SS);

        var noteheadWidthSs = grace.getNoteheadWidthSs();
        assertThat(spring.restSs())
            .as("the grace packs against its host measured from the notehead, not the distant flag")
            .isCloseTo(noteheadWidthSs + GRACE_HOST_REST_SS, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(
            noteheadWidthSs - host.getLeftExtentSs() + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS,
            within(TOLERANCE));
        // The discount is what moved these — without it both would sit a flag's width wider.
        assertThat(grace.getRightExtentSs()).isGreaterThan(noteheadWidthSs);
    }

    /**
     * The same grace–host pair, but with the flag level with the host's notehead, keeps the full
     * flag-inflated extent: the flag really is beside the host there, so the gap must clear it.
     */
    @Test
    void testGraceHostGapChargesAFlagThatMeetsTheHost() {
        var grace = builtGlissandoGraceColumn(LOW_STAFF_POSITION_SP);
        var host = builtColumn(ElementType.CROTCHET, MIDDLE_STAFF_POSITION_SP, StaffElement.Direction.DOWN);

        var spring = HorizontalSpacingCalculator.buildSpring(grace, host, DEFAULT_LINE_REST_SS);

        var rightExtentSs = grace.getRightExtentSs();
        assertThat(spring.restSs()).isCloseTo(rightExtentSs + GRACE_HOST_REST_SS, within(TOLERANCE));
        assertThat(spring.strutSs()).isCloseTo(
            rightExtentSs - host.getLeftExtentSs() + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS,
            within(TOLERANCE));
        assertThat(rightExtentSs).isGreaterThan(grace.getNoteheadWidthSs());
    }

    /**
     * The discount is deliberately scoped to grace notes: an ordinary flagged note in the identical
     * geometry keeps its full extent, so the collision floor of every unbeamed eighth on the page is
     * untouched.
     */
    @Test
    void testFlaggedNonGraceKeepsItsFullExtentFacingAClearHost() {
        var quaver = builtColumn(ElementType.QUAVER, HIGH_STAFF_POSITION_SP, StaffElement.Direction.UP);
        quaver.getElement().setGlissando();
        var host = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);

        // Precondition: this quaver's flag does clear the host's left-facing band, so only the
        // grace-only gate can be keeping the full extent.
        assertThat(quaver.getRightExtentFacingSs(host.getLeftFacingTopYSs(), host.getLeftFacingBottomYSs()))
            .isLessThan(quaver.getRightExtentSs());

        var spring = HorizontalSpacingCalculator.buildSpring(quaver, host, DEFAULT_LINE_REST_SS);

        assertThat(spring.strutSs()).isCloseTo(
            quaver.getRightExtentSs() - host.getLeftExtentSs() + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS,
            within(TOLERANCE));
    }
}
