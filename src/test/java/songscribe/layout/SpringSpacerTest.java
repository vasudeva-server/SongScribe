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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for the freeze-on-strut spring solver. Everything here is synthetic — the solver
 * is pure arithmetic over {@link Spring} values and needs no columns, fonts or rendering.
 */
class SpringSpacerTest extends UnitTest {

    /** Ss comparison tolerance — water-filling leaves float dust. */
    private static final double TOLERANCE_SS = 1e-6;

    private static final double DEFAULT_REST_SS = 2.5;
    private static final double DEFAULT_STRUT_SS = 1.0;

    /** A gap with plenty of slack, and one with little, for the proportional-split tests. */
    private static final double SLACK_REST_SS = 4.0;
    private static final double STIFF_REST_SS = 2.0;

    /** How far a test chain is pushed past the available span. */
    private static final double TEST_DEFICIT_SS = 2.0;

    /** Span headroom beyond a chain's natural length — enough that nothing compresses. */
    private static final double GENEROUS_HEADROOM_SS = 2.5;

    /** How far below the total strut an infeasible span is placed. */
    private static final double BELOW_TOTAL_STRUT_SS = 0.5;

    /** A gap whose rest already sits on its strut: zero slack, frozen from the start. */
    private static final double TIGHT_REST_SS = 1.0;
    private static final double TIGHT_STRUT_SS = 1.0;

    /** A wide-glyph gap: the glyph's collision floor exceeds the ideal gap, so rest < strut. */
    private static final double WIDE_GLYPH_REST_SS = 1.0;
    private static final double WIDE_GLYPH_STRUT_SS = 3.0;

    private static final double NO_COMPLIANCE_SS = 0.0;

    private static final int PROPERTY_SEED = 20330;
    private static final int PROPERTY_TRIALS = 500;
    private static final int PROPERTY_MAX_GAPS = 12;
    private static final double PROPERTY_MAX_STRUT_SS = 2.0;

    /** Slack is sampled across zero: a negative draw models a wide-glyph gap (rest < strut). */
    private static final double PROPERTY_MIN_SLACK_SS = -1.0;
    private static final double PROPERTY_MAX_SLACK_SS = 2.0;

    /**
     * A pair of gaps sharing a rest and a compliance but differing in strut, so that one
     * bottoms out on the first solve iteration and the other absorbs its share on the second.
     */
    private static final double FREEZE_REST_SS = 3.0;
    private static final double FREEZE_SHALLOW_STRUT_SS = 2.9;
    private static final double FREEZE_DEEP_STRUT_SS = 1.0;
    private static final double FREEZE_DEFICIT_SS = 1.5;

    private static final int LONG_CHAIN_GAP_COUNT = 50;
    private static final double LONG_CHAIN_REST_SS = 8.0;
    private static final double LONG_CHAIN_STRUT_STEP_SS = 0.05;
    private static final double LONG_CHAIN_HEADROOM_SS = 0.5;

    /** A tight beam-internal gap: it levels to {@link #TIGHT_WEIGHT} of a normal gap's length. */
    private static final double TIGHT_WEIGHT = 0.6;
    private static final double WEIGHTED_REST_SS = 5.0;
    private static final double WEIGHTED_STRUT_SS = 1.0;
    /** A span that forces compression (natural is 3 × rest) but leaves every strut slack. */
    private static final double WEIGHTED_SPAN_SS = 6.0;

    /** A rigid gap (grace→host) and its normal neighbours, for the pin-and-exclude tests. */
    private static final double RIGID_REST_SS = 3.0;
    private static final double RIGID_STRUT_SS = 1.0;
    private static final double RIGID_NEIGHBOR_REST_SS = 5.0;
    private static final double RIGID_NEIGHBOR_STRUT_SS = 1.0;
    /** Compresses the two neighbours (natural 13) while the rigid gap holds its default. */
    private static final double RIGID_COMPRESS_SPAN_SS = 9.0;

    /** A rigid gap plus a stiff neighbour, to show the rigid floor is its natural, not its strut. */
    private static final double RIGID_STIFF_NEIGHBOR_REST_SS = 2.0;
    private static final double RIGID_STIFF_NEIGHBOR_STRUT_SS = 1.0;
    /** Below rigid-natural (3) + neighbour-strut (1) = 4, though the two struts alone sum to 2. */
    private static final double RIGID_INFEASIBLE_SPAN_SS = 3.5;
    private static final double RIGID_FEASIBLE_SPAN_SS = 4.0;

    // --- Whitespace levelling (Phase 5): two free gaps sharing a rest/strut/weight but differing by
    // a level offset, compressed enough that neither hits its strut or natural. ---
    private static final double LEVEL_OFFSET_TEST_REST_SS = 5.0;
    private static final double LEVEL_OFFSET_DELTA_SS = 1.0;
    private static final double LEVEL_OFFSET_TEST_SPAN_SS = 7.0;
    private static final double NO_LEVEL_OFFSET_SS = 0.0;

    // --- The Phase-4 barline scenario: a thin-ink (small-offset) gap and a normal-ink gap that share
    // the same whitespace contribution, so whitespace levelling compresses them by the same amount
    // instead of exempting the small-natural gap the way origin-delta levelling did. ---
    private static final double BARLINE_OFFSET_SS = 0.3;
    private static final double NOTEHEAD_OFFSET_SS = 1.0;
    private static final double COMMON_WHITESPACE_SS = 2.5;
    private static final double BARLINE_REST_SS = BARLINE_OFFSET_SS + COMMON_WHITESPACE_SS;
    private static final double NOTEHEAD_REST_SS = NOTEHEAD_OFFSET_SS + COMMON_WHITESPACE_SS;
    private static final double WHITESPACE_LEVELLING_SPAN_SS = 5.0;

    /** A small offset shared by both gaps in the strut-priority test; must not outrank the strut. */
    private static final double STRUT_PRIORITY_OFFSET_SS = 0.2;

    @Test
    void testChainThatFitsKeepsNaturalLengths() {
        var springs = List.of(
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS));
        var generousSpanSs = springs.size() * DEFAULT_REST_SS + GENEROUS_HEADROOM_SS;

        var result = SpringSpacer.solve(springs, generousSpanSs);

        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.gapLengthsSs())
            .containsExactly(
                new double[] {DEFAULT_REST_SS, DEFAULT_REST_SS, DEFAULT_REST_SS},
                within(TOLERANCE_SS));
    }

    @Test
    void testChainExactlyAtAvailableSpanKeepsNaturalLengths() {
        var springs = List.of(
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS));
        var exactSpanSs = 2 * DEFAULT_REST_SS;

        var result = SpringSpacer.solve(springs, exactSpanSs);

        assertThat(result.gapLengthsSs())
            .containsExactly(new double[] {DEFAULT_REST_SS, DEFAULT_REST_SS}, within(TOLERANCE_SS));
    }

    @Test
    void testOverflowEqualizesGapLengths() {
        // Water-filling drains both gaps to a common length rather than in proportion to slack: the
        // slack gap (rest 4) and the stiff gap (rest 2) both land at 2.0, not the 2.5 / 1.5 an old
        // proportional split produced. Neither strut (1.0) binds, so the level is unclamped.
        var springs = List.of(
            Spring.of(SLACK_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(STIFF_REST_SS, DEFAULT_STRUT_SS));
        var availableSpanSs = SLACK_REST_SS + STIFF_REST_SS - TEST_DEFICIT_SS;

        var result = SpringSpacer.solve(springs, availableSpanSs);

        var expectedLevelSs = availableSpanSs / springs.size();
        assertThat(result.gapLengthsSs())
            .containsExactly(new double[] {expectedLevelSs, expectedLevelSs}, within(TOLERANCE_SS));
        assertThat(sumOf(result)).isEqualTo(availableSpanSs, within(TOLERANCE_SS));
    }

    @Test
    void testZeroSlackGapDoesNotBlockOtherGapsFromCompressing() {
        // The old uniform-ratio engine rejected the whole line when any single gap could not
        // compress. Here the tight gap simply stays put and the rest of the line absorbs it.
        var springs = List.of(
            Spring.of(TIGHT_REST_SS, TIGHT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS));
        var availableSpanSs = TIGHT_REST_SS + 2 * DEFAULT_REST_SS - TEST_DEFICIT_SS;

        var result = SpringSpacer.solve(springs, availableSpanSs);

        assertThat(result.isInfeasible()).isFalse();

        // The tight gap is already at its natural length, so it caps there and gives nothing; the
        // two roomy gaps water-fill to a common level, which works out to the same as splitting the
        // deficit between them.
        var expectedCompressedSs = DEFAULT_REST_SS - TEST_DEFICIT_SS / 2;
        var gapLengthsSs = solvedGapLengthsSs(result);
        assertThat(gapLengthsSs[0]).isEqualTo(TIGHT_REST_SS, within(TOLERANCE_SS));
        assertThat(gapLengthsSs[1]).isEqualTo(expectedCompressedSs, within(TOLERANCE_SS));
        assertThat(gapLengthsSs[2]).isEqualTo(expectedCompressedSs, within(TOLERANCE_SS));
        assertThat(sumOf(result)).isEqualTo(availableSpanSs, within(TOLERANCE_SS));
    }

    @Test
    void testOverflowBeyondAllStrutsIsInfeasible() {
        var springs = List.of(
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS));
        var belowTotalStrutSs = 2 * DEFAULT_STRUT_SS - BELOW_TOTAL_STRUT_SS;

        var result = SpringSpacer.solve(springs, belowTotalStrutSs);

        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.gapLengthsSs()).isNull();
    }

    @Test
    void testOverflowWithEveryGapAlreadyFrozenIsInfeasible() {
        var springs = List.of(
            Spring.of(TIGHT_REST_SS, TIGHT_STRUT_SS),
            Spring.of(TIGHT_REST_SS, TIGHT_STRUT_SS));

        assertThat(springs.getFirst().complianceSs()).isEqualTo(NO_COMPLIANCE_SS);

        var result = SpringSpacer.solve(springs, 2 * TIGHT_STRUT_SS - BELOW_TOTAL_STRUT_SS);

        assertThat(result.isInfeasible()).isTrue();
    }

    @Test
    void testChainCompressedExactlyToItsStrutsSolves() {
        var springs = List.of(
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS),
            Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS));
        var totalStrutSs = 2 * DEFAULT_STRUT_SS;

        var result = SpringSpacer.solve(springs, totalStrutSs);

        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.gapLengthsSs())
            .containsExactly(
                new double[] {DEFAULT_STRUT_SS, DEFAULT_STRUT_SS}, within(TOLERANCE_SS));
    }

    @Test
    void testSingleGapCompressesToAvailableSpan() {
        var springs = List.of(Spring.of(SLACK_REST_SS, DEFAULT_STRUT_SS));
        var availableSpanSs = SLACK_REST_SS - TEST_DEFICIT_SS;

        var result = SpringSpacer.solve(springs, availableSpanSs);

        assertThat(result.gapLengthsSs())
            .containsExactly(new double[] {availableSpanSs}, within(TOLERANCE_SS));
    }

    @Test
    void testEmptySpringListSolvesToNoGaps() {
        var result = SpringSpacer.solve(List.of(), DEFAULT_REST_SS);

        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.gapLengthsSs()).isEmpty();
    }

    @Test
    void testWideGlyphGapStartsAtItsStrutAndNeverGives() {
        // rest < strut → natural length is the strut and compliance is zero.
        var wideGlyphSpring = Spring.of(WIDE_GLYPH_REST_SS, WIDE_GLYPH_STRUT_SS);
        assertThat(wideGlyphSpring.complianceSs()).isEqualTo(NO_COMPLIANCE_SS);

        var springs = List.of(wideGlyphSpring, Spring.of(DEFAULT_REST_SS, DEFAULT_STRUT_SS));
        var naturalSpanSs = WIDE_GLYPH_STRUT_SS + DEFAULT_REST_SS;
        var deficitSs = 1.0;

        var result = SpringSpacer.solve(springs, naturalSpanSs - deficitSs);

        assertThat(result.gapLengthsSs())
            .containsExactly(
                new double[] {WIDE_GLYPH_STRUT_SS, DEFAULT_REST_SS - deficitSs},
                within(TOLERANCE_SS));
    }

    @Test
    void testWideGlyphGapAloneCannotCompress() {
        var springs = List.of(Spring.of(WIDE_GLYPH_REST_SS, WIDE_GLYPH_STRUT_SS));

        var result = SpringSpacer.solve(springs, WIDE_GLYPH_REST_SS);

        assertThat(result.isInfeasible()).isTrue();
    }

    @Test
    void testNoSolvedGapEverFallsBelowItsStrut() {
        var random = new Random(PROPERTY_SEED);
        var solvedTrials = 0;

        for (var trial = 0; trial < PROPERTY_TRIALS; trial++) {
            var springs = randomSprings(random);
            var totalStrutSs = springs.stream().mapToDouble(Spring::strutSs).sum();
            var totalRestSs = springs.stream().mapToDouble(Spring::restSs).sum();

            // Sample spans from well below every strut to well beyond the natural length, so
            // the fast path, partial freezing and infeasibility are all exercised.
            var availableSpanSs = totalStrutSs / 2 + random.nextDouble() * 2 * totalRestSs;
            var result = SpringSpacer.solve(springs, availableSpanSs);

            if (result.isInfeasible()) {
                continue;
            }

            solvedTrials++;
            var gapLengthsSs = solvedGapLengthsSs(result);

            for (var i = 0; i < springs.size(); i++) {
                assertThat(gapLengthsSs[i])
                    .as("trial %d gap %d must not fall below its strut", trial, i)
                    .isGreaterThanOrEqualTo(springs.get(i).strutSs() - TOLERANCE_SS);
            }

            assertThat(sumOf(result))
                .as("trial %d must not exceed the available span", trial)
                .isLessThanOrEqualTo(availableSpanSs + TOLERANCE_SS);
        }

        // Without this the property holds vacuously if every trial came back INFEASIBLE.
        assertThat(solvedTrials)
            .as("the sampled spans must produce solved results to assert against")
            .isGreaterThan(PROPERTY_TRIALS / 2);
    }

    private static List<Spring> randomSprings(Random random) {
        var gapCount = 1 + random.nextInt(PROPERTY_MAX_GAPS);
        var springs = new ArrayList<Spring>(gapCount);

        for (var i = 0; i < gapCount; i++) {
            var strutSs = random.nextDouble() * PROPERTY_MAX_STRUT_SS;
            var slackRangeSs = PROPERTY_MAX_SLACK_SS - PROPERTY_MIN_SLACK_SS;
            var slackSs = PROPERTY_MIN_SLACK_SS + random.nextDouble() * slackRangeSs;
            springs.add(Spring.of(strutSs + slackSs, strutSs));
        }

        return springs;
    }

    /**
     * A gap whose common level would fall under its strut pins on that strut instead, and the
     * remaining gap takes the room it could not give — so their solved lengths differ even though
     * they share a rest.
     */
    @Test
    void testHighStrutGapPinsAtItsStrutAndOthersAbsorbTheRest() {
        var shallowSpring = Spring.of(FREEZE_REST_SS, FREEZE_SHALLOW_STRUT_SS);
        var deepSpring = Spring.of(FREEZE_REST_SS, FREEZE_DEEP_STRUT_SS);
        var springs = List.of(shallowSpring, deepSpring);
        var availableSpanSs = 2 * FREEZE_REST_SS - FREEZE_DEFICIT_SS;

        var result = SpringSpacer.solve(springs, availableSpanSs);

        var gapLengthsSs = solvedGapLengthsSs(result);

        // The common level dips below the shallow gap's high strut, so it pins there.
        assertThat(gapLengthsSs[0]).isEqualTo(FREEZE_SHALLOW_STRUT_SS, within(TOLERANCE_SS));

        // The deep gap absorbs the remaining budget, ending above its own (lower) strut.
        assertThat(gapLengthsSs[1]).isGreaterThan(FREEZE_DEEP_STRUT_SS);
        assertThat(sumOf(result)).isEqualTo(availableSpanSs, within(TOLERANCE_SS));
    }

    /**
     * The same pair, but with struts whose sum exceeds the span: the solver must report INFEASIBLE
     * rather than spin.
     */
    @Test
    void testChainThatCannotFitEvenAtItsStrutsIsInfeasible() {
        var springs = List.of(
            Spring.of(FREEZE_REST_SS, FREEZE_SHALLOW_STRUT_SS),
            Spring.of(FREEZE_REST_SS, FREEZE_DEEP_STRUT_SS));
        var belowTotalStrutSs =
            FREEZE_SHALLOW_STRUT_SS + FREEZE_DEEP_STRUT_SS - BELOW_TOTAL_STRUT_SS;

        var result = SpringSpacer.solve(springs, belowTotalStrutSs);

        assertThat(result.isInfeasible()).isTrue();
    }

    /**
     * A full-length line must stay inside the solver's {@code springs.size()} pass bound. Exceeding
     * it throws rather than mis-spacing, so this asserts the bound is never hit.
     */
    @Test
    void testLongChainSolvesWithinItsIterationBound() {
        var springs = new ArrayList<Spring>(LONG_CHAIN_GAP_COUNT);

        // Staggered struts: every gap pins on its floor at a different point as the level drops,
        // so the clamp-one-per-pass loop is driven the full length of the chain.
        for (var i = 0; i < LONG_CHAIN_GAP_COUNT; i++) {
            var strutSs = DEFAULT_STRUT_SS + i * LONG_CHAIN_STRUT_STEP_SS;
            springs.add(Spring.of(LONG_CHAIN_REST_SS, strutSs));
        }

        var totalStrutSs = springs.stream().mapToDouble(Spring::strutSs).sum();
        var availableSpanSs = totalStrutSs + LONG_CHAIN_HEADROOM_SS;

        assertThatCode(() -> {
            var result = SpringSpacer.solve(springs, availableSpanSs);

            assertThat(result.isInfeasible()).isFalse();
            assertThat(sumOf(result)).isLessThanOrEqualTo(availableSpanSs + TOLERANCE_SS);
        }).doesNotThrowAnyException();
    }

    /**
     * A tight beam-internal gap keeps its reduction under compression: it levels to
     * {@link #TIGHT_WEIGHT} of its normal neighbours, not to the same common length. No strut binds,
     * so the ratio is exact.
     */
    @Test
    void testWeightedGapLevelsToFractionOfNormalNeighbours() {
        var springs = List.of(
            Spring.of(WEIGHTED_REST_SS, WEIGHTED_STRUT_SS),
            Spring.of(WEIGHTED_REST_SS, WEIGHTED_STRUT_SS, TIGHT_WEIGHT, false),
            Spring.of(WEIGHTED_REST_SS, WEIGHTED_STRUT_SS));

        var result = SpringSpacer.solve(springs, WEIGHTED_SPAN_SS);

        var gapLengthsSs = solvedGapLengthsSs(result);

        assertThat(gapLengthsSs[0]).isEqualTo(gapLengthsSs[2], within(TOLERANCE_SS));
        assertThat(gapLengthsSs[1])
            .isCloseTo(TIGHT_WEIGHT * gapLengthsSs[0], within(TOLERANCE_SS));
        assertThat(sumOf(result)).isEqualTo(WEIGHTED_SPAN_SS, within(TOLERANCE_SS));
    }

    /**
     * A rigid gap (grace→host) holds its natural length under compression while its neighbours give;
     * it consumes a fixed slice of the span rather than levelling with the rest.
     */
    @Test
    void testRigidGapKeepsNaturalLengthWhileNeighboursCompress() {
        var springs = List.of(
            Spring.of(RIGID_REST_SS, RIGID_STRUT_SS, Spring.NORMAL_WEIGHT, true),
            Spring.of(RIGID_NEIGHBOR_REST_SS, RIGID_NEIGHBOR_STRUT_SS),
            Spring.of(RIGID_NEIGHBOR_REST_SS, RIGID_NEIGHBOR_STRUT_SS));

        var result = SpringSpacer.solve(springs, RIGID_COMPRESS_SPAN_SS);

        var gapLengthsSs = solvedGapLengthsSs(result);

        // The rigid gap stays on its default (natural = max(rest, strut) = rest).
        assertThat(gapLengthsSs[0]).isEqualTo(RIGID_REST_SS, within(TOLERANCE_SS));

        // The two normal neighbours split the remaining budget evenly and compress below their rest.
        var expectedNeighborSs = (RIGID_COMPRESS_SPAN_SS - RIGID_REST_SS) / 2;
        assertThat(gapLengthsSs[1]).isEqualTo(expectedNeighborSs, within(TOLERANCE_SS));
        assertThat(gapLengthsSs[2]).isEqualTo(expectedNeighborSs, within(TOLERANCE_SS));
        assertThat(expectedNeighborSs).isLessThan(RIGID_NEIGHBOR_REST_SS);
        assertThat(sumOf(result)).isEqualTo(RIGID_COMPRESS_SPAN_SS, within(TOLERANCE_SS));
    }

    /**
     * A rigid gap's floor is its natural length, not its strut: a span that would fit if the rigid
     * gap could compress to its strut is infeasible, while the span that clears its natural fits.
     */
    @Test
    void testRigidGapFloorIsNaturalNotStrut() {
        var springs = List.of(
            Spring.of(RIGID_REST_SS, RIGID_STRUT_SS, Spring.NORMAL_WEIGHT, true),
            Spring.of(RIGID_STIFF_NEIGHBOR_REST_SS, RIGID_STIFF_NEIGHBOR_STRUT_SS));

        assertThat(SpringSpacer.solve(springs, RIGID_INFEASIBLE_SPAN_SS).isInfeasible()).isTrue();

        var feasible = SpringSpacer.solve(springs, RIGID_FEASIBLE_SPAN_SS);
        var gapLengthsSs = solvedGapLengthsSs(feasible);
        assertThat(gapLengthsSs[0]).isEqualTo(RIGID_REST_SS, within(TOLERANCE_SS));
        assertThat(gapLengthsSs[1]).isEqualTo(RIGID_STIFF_NEIGHBOR_STRUT_SS, within(TOLERANCE_SS));
    }

    /**
     * Two free gaps sharing rest, strut and weight but differing by a level offset of
     * {@link #LEVEL_OFFSET_DELTA_SS}: under compression, with neither gap hitting its strut or
     * natural length, the offset survives as an exact relative shift between their solved lengths.
     */
    @Test
    void testLevelOffsetSurvivesCompressionAsARelativeShift() {
        var noOffsetSpring = Spring.of(
            LEVEL_OFFSET_TEST_REST_SS, DEFAULT_STRUT_SS, Spring.NORMAL_WEIGHT, false, NO_LEVEL_OFFSET_SS);
        var offsetSpring = Spring.of(
            LEVEL_OFFSET_TEST_REST_SS, DEFAULT_STRUT_SS, Spring.NORMAL_WEIGHT, false, LEVEL_OFFSET_DELTA_SS);
        var springs = List.of(noOffsetSpring, offsetSpring);

        var result = SpringSpacer.solve(springs, LEVEL_OFFSET_TEST_SPAN_SS);

        var gapLengthsSs = solvedGapLengthsSs(result);
        assertThat(gapLengthsSs[1] - gapLengthsSs[0]).isCloseTo(LEVEL_OFFSET_DELTA_SS, within(TOLERANCE_SS));
        assertThat(sumOf(result)).isEqualTo(LEVEL_OFFSET_TEST_SPAN_SS, within(TOLERANCE_SS));
    }

    /**
     * The Phase-4 barline scenario: a thin-ink gap (small offset, small natural) and a normal-ink gap
     * that share the same whitespace contribution behind their differing ink. Origin-delta levelling
     * (weight × U alone) would clamp the thin-ink gap at its small natural and exempt it from
     * compression entirely; whitespace levelling compresses both gaps by the identical amount instead,
     * so the thin-ink gap ends up below its own natural just like its neighbour.
     */
    @Test
    void testSmallNaturalOffsetGapCompressesUnderWhitespaceLevellingInsteadOfBeingExempt() {
        var barlineSpring = Spring.of(
            BARLINE_REST_SS, DEFAULT_STRUT_SS, Spring.NORMAL_WEIGHT, false, BARLINE_OFFSET_SS);
        var noteheadSpring = Spring.of(
            NOTEHEAD_REST_SS, DEFAULT_STRUT_SS, Spring.NORMAL_WEIGHT, false, NOTEHEAD_OFFSET_SS);
        var springs = List.of(barlineSpring, noteheadSpring);

        var result = SpringSpacer.solve(springs, WHITESPACE_LEVELLING_SPAN_SS);

        var gapLengthsSs = solvedGapLengthsSs(result);
        assertThat(gapLengthsSs[0])
            .as("the thin-ink gap must compress below its own natural, not be exempted")
            .isLessThan(BARLINE_REST_SS);
        assertThat(gapLengthsSs[1]).isLessThan(NOTEHEAD_REST_SS);
        assertThat(gapLengthsSs[0] - BARLINE_OFFSET_SS)
            .as("both gaps give up the same whitespace once their own ink is excluded")
            .isCloseTo(gapLengthsSs[1] - NOTEHEAD_OFFSET_SS, within(TOLERANCE_SS));
    }

    /**
     * A chain whose springs all carry an explicit zero level offset must solve identically to the
     * pre-Phase-5 origin-delta behaviour ({@link #testOverflowEqualizesGapLengths}) — a regression
     * guard proving whitespace levelling is a strict generalisation, not a second spacing scheme.
     */
    @Test
    void testAllOffsetZeroChainSolvesIdenticallyToOriginDeltaBehavior() {
        var springs = List.of(
            Spring.of(SLACK_REST_SS, DEFAULT_STRUT_SS, Spring.NORMAL_WEIGHT, false, NO_LEVEL_OFFSET_SS),
            Spring.of(STIFF_REST_SS, DEFAULT_STRUT_SS, Spring.NORMAL_WEIGHT, false, NO_LEVEL_OFFSET_SS));
        var availableSpanSs = SLACK_REST_SS + STIFF_REST_SS - TEST_DEFICIT_SS;

        var result = SpringSpacer.solve(springs, availableSpanSs);

        var expectedLevelSs = availableSpanSs / springs.size();
        assertThat(result.gapLengthsSs())
            .containsExactly(new double[] {expectedLevelSs, expectedLevelSs}, within(TOLERANCE_SS));
    }

    /**
     * A strut floor still wins over an offset-based target: the shallow-strut gap's
     * {@code offset + weight × U} falls under its own high strut and pins there, exactly as it does
     * with no offset at all ({@link #testHighStrutGapPinsAtItsStrutAndOthersAbsorbTheRest}).
     */
    @Test
    void testStrutFloorStillWinsOverOffsetBasedTarget() {
        var shallowSpring = Spring.of(
            FREEZE_REST_SS, FREEZE_SHALLOW_STRUT_SS, Spring.NORMAL_WEIGHT, false, STRUT_PRIORITY_OFFSET_SS);
        var deepSpring = Spring.of(
            FREEZE_REST_SS, FREEZE_DEEP_STRUT_SS, Spring.NORMAL_WEIGHT, false, STRUT_PRIORITY_OFFSET_SS);
        var springs = List.of(shallowSpring, deepSpring);
        var availableSpanSs = 2 * FREEZE_REST_SS - FREEZE_DEFICIT_SS;

        var result = SpringSpacer.solve(springs, availableSpanSs);

        var gapLengthsSs = solvedGapLengthsSs(result);
        assertThat(gapLengthsSs[0]).isEqualTo(FREEZE_SHALLOW_STRUT_SS, within(TOLERANCE_SS));
        assertThat(gapLengthsSs[1]).isGreaterThan(FREEZE_DEEP_STRUT_SS);
        assertThat(sumOf(result)).isEqualTo(availableSpanSs, within(TOLERANCE_SS));
    }

    /**
     * The solved lengths, failing the test rather than returning null on an infeasible result.
     */
    private static double[] solvedGapLengthsSs(SpringSolveResult result) {
        var gapLengthsSs = result.gapLengthsSs();

        if (gapLengthsSs == null) {
            throw new AssertionError("expected a solved result, but the solve was INFEASIBLE");
        }

        return gapLengthsSs;
    }

    private static double sumOf(SpringSolveResult result) {
        var gapLengthsSs = solvedGapLengthsSs(result);
        var totalSs = 0.0;

        for (var gapLengthSs : gapLengthsSs) {
            totalSs += gapLengthSs;
        }

        return totalSs;
    }
}
