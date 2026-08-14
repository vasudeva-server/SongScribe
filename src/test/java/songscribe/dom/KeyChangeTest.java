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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLGlyph;

/**
 * Exercises the cancellation policy stated on {@link KeyChange}.
 *
 * <p><b>The policy</b> — asserted over every ordered pair of keys
 * {@link Key#allSignatures()} yields, so the claim survives the domain growing rather than
 * covering whatever pairs someone once picked. Each pair is classified from the policy's own
 * prose into one of its three branches — key unchanged, same type, type changed — and each
 * branch has its own case asserting what that branch promises is drawn.
 *
 * <p><b>Invariants</b> — cancellation naturals never follow the signature they cancel; a
 * change to the same key draws nothing and measures zero; the width is the laid-out width of
 * the accidentals the same call yields; exactly one
 * {@link StaffHeaderMetrics#CANCELLATION_TO_KEY_GAP_SS} separates the two runs and no kerning
 * reaches across it; every returned list is immutable.
 *
 * <p><b>Boundaries</b> — changes to and from {@link KeyType#NONE}, which the policy counts as
 * type changes, and {@link Key#MAX_ACCIDENTAL_COUNT}, which is the last index the
 * staff-position tables hold.
 *
 * <p>Where the accidentals sit — the BEADGCF and FCGDAEB orders — is asserted through
 * {@link KeyChange#signatureAccidentals}, the same public answer the staff header draws from,
 * rather than by reading the tables behind it.
 */
class KeyChangeTest extends UnitTest {

    /** Widths are sums of font metrics, so compare in staff spaces with this much slack. */
    private static final double TOLERANCE_SS = 1e-9;

    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);

    /**
     * The three branches of the policy, as its prose states them — not as the implementation
     * happens to branch.
     */
    private enum Branch {
        /** The key does not change. */
        UNCHANGED,
        /** Same type, a different number of accidentals. */
        SAME_TYPE,
        /** Sharps to flats, flats to sharps, or to or from no key at all. */
        TYPE_CHANGED
    }

    private static Branch branchOf(Key previous, Key next) {
        if (previous.equals(next)) {
            return Branch.UNCHANGED;
        }

        return previous.keyType() == next.keyType() ? Branch.SAME_TYPE : Branch.TYPE_CHANGED;
    }

    static Stream<Arguments> allOrderedPairs() {
        var signatures = Key.allSignatures();

        return signatures.stream()
            .flatMap(previous -> signatures.stream().map(next -> Arguments.of(previous, next)));
    }

    private static Stream<Arguments> pairsIn(Branch branch) {
        return allOrderedPairs()
            .filter(pair -> branchOf((Key) pair.get()[0], (Key) pair.get()[1]) == branch);
    }

    static Stream<Arguments> unchangedPairs() {
        return pairsIn(Branch.UNCHANGED);
    }

    static Stream<Arguments> sameTypePairs() {
        return pairsIn(Branch.SAME_TYPE);
    }

    static Stream<Arguments> typeChangedPairs() {
        return pairsIn(Branch.TYPE_CHANGED);
    }

    static Stream<Key> allSignatures() {
        return Key.allSignatures().stream();
    }

    @Test
    void testTheBranchTablesTogetherCoverEveryOrderedPairOfKeys() {
        var signatureCount = Key.allSignatures().size();
        var branchedCount = unchangedPairs().count() + sameTypePairs().count() + typeChangedPairs().count();

        assertThat(branchedCount).isEqualTo((long) signatureCount * signatureCount);
        assertThat(unchangedPairs()).isNotEmpty();
        assertThat(sameTypePairs()).isNotEmpty();
        assertThat(typeChangedPairs()).isNotEmpty();
    }

    // ==========================================================================
    // The policy, branch by branch
    // ==========================================================================

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("unchangedPairs")
    void testUnchangedKeyDrawsNothing(Key previous, Key next) {
        assertThat(KeyChange.accidentals(previous, next)).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("sameTypePairs")
    void testSameTypeDrawsTheNewSignatureAndNoNaturals(Key previous, Key next) {
        assertThat(KeyChange.accidentals(previous, next))
            .as("the new signature supersedes the old one, whether it adds or drops accidentals")
            .hasSize(next.accidentalCount())
            .allSatisfy(accidental ->
                assertThat(accidental.glyph()).isEqualTo(KeyChange.accidentalGlyph(next.keyType())));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("typeChangedPairs")
    void testTypeChangeCancelsTheWholePreviousSignatureThenDrawsTheNewOne(Key previous, Key next) {
        var accidentals = KeyChange.accidentals(previous, next);
        var cancelledCount = previous.accidentalCount();

        assertThat(accidentals).hasSize(cancelledCount + next.accidentalCount());
        assertThat(accidentals.subList(0, cancelledCount))
            .as("the entire previous signature is cancelled, not only the accidentals it loses")
            .allSatisfy(accidental ->
                assertThat(accidental.glyph()).isEqualTo(SMuFLGlyph.ACCIDENTAL_NATURAL));
        assertThat(accidentals.subList(cancelledCount, accidentals.size()))
            .allSatisfy(accidental ->
                assertThat(accidental.glyph()).isEqualTo(KeyChange.accidentalGlyph(next.keyType())));
    }

    // ==========================================================================
    // Invariants over the whole domain
    // ==========================================================================

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allOrderedPairs")
    void testNaturalsNeverFollowTheSignatureTheyCancel(Key previous, Key next) {
        var accidentals = KeyChange.accidentals(previous, next);
        var seenNonNatural = false;

        for (var i = 0; i < accidentals.size(); i++) {
            if (accidentals.get(i).glyph() == SMuFLGlyph.ACCIDENTAL_NATURAL) {
                assertThat(seenNonNatural)
                    .as("the natural at index %d follows the signature it cancels", i)
                    .isFalse();
            } else {
                seenNonNatural = true;
            }
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("typeChangedPairs")
    void testExactlyOneGapSeparatesACancellationFromTheSignatureAfterIt(Key previous, Key next) {
        var accidentals = KeyChange.accidentals(previous, next);
        var gapCount = accidentals.stream().filter(accidental -> accidental.leadingGapSs() != 0).count();

        if (previous.keyType() == KeyType.NONE || next.keyType() == KeyType.NONE) {
            assertThat(gapCount)
                .as("only one run is drawn, so there is nothing to be separated from")
                .isZero();
            return;
        }

        assertThat(gapCount).isEqualTo(1);
        assertThat(accidentals.get(previous.accidentalCount()).leadingGapSs())
            .isCloseTo(StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS, within(TOLERANCE_SS));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("sameTypePairs")
    void testASingleRunCarriesNoLeadingGap(Key previous, Key next) {
        assertThat(KeyChange.accidentals(previous, next))
            .allSatisfy(accidental -> assertThat(accidental.leadingGapSs()).isEqualTo(0));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allOrderedPairs")
    void testWidthIsTheLaidOutWidthOfWhatIsDrawn(Key previous, Key next) {
        var accidentals = KeyChange.accidentals(previous, next);
        var widthSs = KeyChange.widthSs(previous, next);

        assertThat(widthSs).isCloseTo(KeyChange.totalWidthSs(accidentals), within(TOLERANCE_SS));
        assertThat(widthSs).isNotNegative();
        assertThat(widthSs > 0)
            .as("a width of zero means nothing is drawn, and vice versa")
            .isEqualTo(!accidentals.isEmpty());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allOrderedPairs")
    void testAccidentalsAreImmutable(Key previous, Key next) {
        var accidentals = KeyChange.accidentals(previous, next);
        var intruder = new KeyChange.DrawnAccidental(SMuFLGlyph.ACCIDENTAL_NATURAL, 0, 0);

        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> accidentals.add(intruder));
    }

    // ==========================================================================
    // The equal-keys clause, which the branch tables alone do not pin
    // ==========================================================================

    @ParameterizedTest
    @MethodSource("allSignatures")
    void testChangingToTheSameKeyDrawsNothingAndMeasuresZero(Key key) {
        assertThat(KeyChange.accidentals(key, key)).isEmpty();
        assertThat(KeyChange.widthSs(key, key)).isEqualTo(0);
    }

    // ==========================================================================
    // Width and kerning
    // ==========================================================================

    @Test
    void testWidthOfASameTypeChangeIsJustTheNewSignaturesGlyphs() {
        final var previousCount = 4;
        final var nextCount = 2;

        var widthSs = KeyChange.widthSs(
            new Key(KeyType.SHARPS, previousCount), new Key(KeyType.SHARPS, nextCount));

        assertThat(widthSs).isCloseTo(
            nextCount * StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_SHARP),
            within(TOLERANCE_SS));
    }

    @Test
    void testWidthOfATypeChangeIsBothRunsPlusTheGapBetweenThem() {
        final var previousCount = 3;
        final var nextCount = 2;

        var previous = new Key(KeyType.SHARPS, previousCount);
        var next = new Key(KeyType.FLATS, nextCount);
        var naturalInkSs = StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_NATURAL);
        var flatInkSs = StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_FLAT);
        var cancelledPositions = staffPositionsOf(previous);
        var kerningSs = 0.0;

        for (var i = 0; i < previousCount - 1; i++) {
            kerningSs += StaffHeaderMetrics.naturalKerningSs(
                cancelledPositions[i], cancelledPositions[i + 1]);
        }

        var expectedSs = previousCount * naturalInkSs
            + kerningSs
            + StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS
            + nextCount * flatInkSs;

        assertThat(KeyChange.widthSs(previous, next)).isCloseTo(expectedSs, within(TOLERANCE_SS));
    }

    @Test
    void testAdjacentNaturalsAreKernedApartButNotAcrossTheGroupBoundary() {
        final var previousCount = 3;
        final var nextCount = 2;

        var accidentals = KeyChange.accidentals(
            new Key(KeyType.SHARPS, previousCount), new Key(KeyType.FLATS, nextCount));
        var naturalInkSs = StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_NATURAL);
        var totalKerningSs = 0.0;

        for (var i = 0; i < previousCount - 1; i++) {
            var kerningSs = StaffHeaderMetrics.naturalKerningSs(
                accidentals.get(i).staffPositionSp(), accidentals.get(i + 1).staffPositionSp());
            totalKerningSs += kerningSs;

            assertThat(KeyChange.advanceSs(accidentals, i))
                .as("advance from natural %d to %d", i, i + 1)
                .isCloseTo(naturalInkSs + kerningSs, within(TOLERANCE_SS));
        }

        assertThat(totalKerningSs)
            .as("the first naturals of a sharp cancellation overlap, so at least one pair is pushed apart")
            .isPositive();
        assertThat(KeyChange.advanceSs(accidentals, previousCount - 1))
            .as("the gap to the next run already holds the last natural clear of it")
            .isCloseTo(naturalInkSs, within(TOLERANCE_SS));
    }

    @Test
    void testTotalWidthOfAnEmptyRunIsZero() {
        assertThat(KeyChange.totalWidthSs(List.of())).isEqualTo(0);
    }

    // ==========================================================================
    // signatureAccidentals — what a staff header draws
    // ==========================================================================

    @Test
    void testCMajorSignatureDrawsNothing() {
        assertThat(KeyChange.signatureAccidentals(C_MAJOR)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("allSignatures")
    void testSignatureDrawsOneGlyphPerAccidentalWithNoGaps(Key key) {
        var accidentals = KeyChange.signatureAccidentals(key);

        assertThat(accidentals).hasSize(key.accidentalCount());
        assertThat(accidentals).allSatisfy(accidental -> {
            assertThat(accidental.leadingGapSs())
                .as("a signature is a single run, so nothing inside it is pushed away")
                .isEqualTo(0);

            if (key.keyType() != KeyType.NONE) {
                assertThat(accidental.glyph()).isEqualTo(KeyChange.accidentalGlyph(key.keyType()));
            }
        });
    }

    @Test
    void testFlatsAreStackedInBeadgcfOrder() {
        // Staff positions relative to the middle line, B E A D G C F.
        int[] expectedPositions = {0, -3, 1, -2, 2, -1, 3};

        assertThat(staffPositionsOf(new Key(KeyType.FLATS, Key.MAX_ACCIDENTAL_COUNT)))
            .containsExactly(expectedPositions);
    }

    @Test
    void testSharpsAreStackedInFcgdaebOrder() {
        // Staff positions relative to the middle line, F C G D A E B.
        int[] expectedPositions = {-4, -1, -5, -2, 1, -3, 0};

        assertThat(staffPositionsOf(new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT)))
            .containsExactly(expectedPositions);
    }

    @ParameterizedTest
    @MethodSource("allSignatures")
    void testASignaturesPositionsAreAPrefixOfItsTypesFullOrder(Key key) {
        if (key.keyType() == KeyType.NONE) {
            assertThat(staffPositionsOf(key)).isEmpty();
            return;
        }

        var fullOrder = staffPositionsOf(new Key(key.keyType(), Key.MAX_ACCIDENTAL_COUNT));

        assertThat(staffPositionsOf(key))
            .containsExactly(Arrays.copyOf(fullOrder, key.accidentalCount()));
    }

    private static int[] staffPositionsOf(Key key) {
        return KeyChange.signatureAccidentals(key).stream()
            .mapToInt(KeyChange.DrawnAccidental::staffPositionSp)
            .toArray();
    }

    // ==========================================================================
    // accidentalGlyph
    // ==========================================================================

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testAccidentalGlyphAnswersForEveryTypeThatDrawsOneAndRejectsTheOneThatDoesNot(KeyType keyType) {
        if (keyType == KeyType.NONE) {
            assertThatIllegalArgumentException().isThrownBy(() -> KeyChange.accidentalGlyph(keyType));
            return;
        }

        var expected = keyType == KeyType.FLATS
            ? SMuFLGlyph.ACCIDENTAL_FLAT
            : SMuFLGlyph.ACCIDENTAL_SHARP;

        assertThat(KeyChange.accidentalGlyph(keyType)).isEqualTo(expected);
    }
}
