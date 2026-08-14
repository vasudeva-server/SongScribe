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
 * <p><b>The policy</b> — one case per {@link KeyChangeKind}, which is where the reasoning about
 * why those are the distinct kinds lives. Each case below names the kind it asserts rather than
 * taking it as a parameter, because what each kind promises to draw differs; the invariants that
 * hold across all of them are parameterized over {@link KeyChangeKind} instead.
 *
 * <p><b>Boundaries</b> — changes to and from {@link KeyType#NONE}, which the policy counts as
 * type changes, and {@link Key#MAX_ACCIDENTAL_COUNT}, which is the last index the
 * staff-position tables hold.
 *
 * <p>Where the accidentals sit — the BEADGCF and FCGDAEB orders — is asserted through
 * {@link KeyChange#signatureAccidentals}, the same public answer the staff header draws from,
 * rather than by reading the tables behind it. That method takes a single key, so its cases do
 * enumerate {@link Key#allSignatures()}.
 */
class KeyChangeTest extends UnitTest {

    /** Widths are sums of font metrics, so compare in staff spaces with this much slack. */
    private static final double TOLERANCE_SS = 1e-9;

    static Stream<Key> allSignatures() {
        return Key.allSignatures().stream();
    }

    /** Asserts a single unbroken run of {@code count} {@code glyph}s, which carries no gaps. */
    private static void assertSingleRunOf(
        List<KeyChange.DrawnAccidental> accidentals, SMuFLGlyph glyph, int count) {

        assertThat(accidentals).hasSize(count).allSatisfy(accidental -> {
            assertThat(accidental.glyph()).isEqualTo(glyph);
            assertThat(accidental.leadingGapSs())
                .as("a single run is not pushed apart anywhere inside itself")
                .isEqualTo(0);
        });
    }

    // ==========================================================================
    // The policy, one case per kind of change
    // ==========================================================================

    private static List<KeyChange.DrawnAccidental> accidentalsOf(KeyChangeKind kind) {
        return KeyChange.accidentals(kind.previous(), kind.next());
    }

    @Test
    void testAKeyThatDoesNotChangeDrawsNothingAndMeasuresZero() {
        var kind = KeyChangeKind.UNCHANGED;

        assertThat(accidentalsOf(kind)).isEmpty();
        assertThat(KeyChange.widthSs(kind.previous(), kind.next())).isEqualTo(0);
        assertThat(KeyChange.accidentals(TestKeys.C_MAJOR, TestKeys.C_MAJOR))
            .as("no key at all is unchanged too")
            .isEmpty();
    }

    @Test
    void testAddingAccidentalsOfTheSameTypeDrawsOnlyTheNewSignature() {
        var kind = KeyChangeKind.SAME_TYPE_ADDING;

        assertSingleRunOf(
            accidentalsOf(kind), SMuFLGlyph.ACCIDENTAL_SHARP, kind.next().accidentalCount());
    }

    /**
     * Dropping accidentals is the case a conventional engraver would cancel: the accidentals that
     * are lost would be naturalled. This policy restates the new signature instead, so this case
     * is a decision rather than a restatement of the one above.
     */
    @Test
    void testDroppingAccidentalsOfTheSameTypeStillDrawsNoNaturals() {
        var kind = KeyChangeKind.SAME_TYPE_DROPPING;

        assertSingleRunOf(
            accidentalsOf(kind), SMuFLGlyph.ACCIDENTAL_SHARP, kind.next().accidentalCount());
    }

    @Test
    void testCancellingSharpsToNoKeyDrawsOnlyNaturals() {
        var kind = KeyChangeKind.SHARPS_TO_NO_KEY;

        assertSingleRunOf(
            accidentalsOf(kind), SMuFLGlyph.ACCIDENTAL_NATURAL, kind.previous().accidentalCount());
    }

    @Test
    void testCancellingFlatsToNoKeyDrawsOnlyNaturals() {
        var kind = KeyChangeKind.FLATS_TO_NO_KEY;

        assertSingleRunOf(
            accidentalsOf(kind), SMuFLGlyph.ACCIDENTAL_NATURAL, kind.previous().accidentalCount());
    }

    @Test
    void testLeavingNoKeyAtAllHasNothingToCancel() {
        var kind = KeyChangeKind.NO_KEY_TO_SHARPS;

        assertSingleRunOf(
            accidentalsOf(kind), SMuFLGlyph.ACCIDENTAL_SHARP, kind.next().accidentalCount());
    }

    /**
     * The only shape that draws two runs at once, and so the only one where their order, the gap
     * between them, and the absence of a gap anywhere else are all observable.
     */
    @Test
    void testAChangeOfTypeCancelsThePreviousSignatureThenDrawsTheNewOneAfterOneGap() {
        var kind = KeyChangeKind.SHARPS_TO_FLATS;
        var cancelledCount = kind.previous().accidentalCount();
        var accidentals = accidentalsOf(kind);

        assertThat(accidentals).hasSize(cancelledCount + kind.next().accidentalCount());
        assertThat(accidentals.subList(0, cancelledCount))
            .as("the entire previous signature is cancelled, not only the accidentals it loses")
            .allSatisfy(accidental ->
                assertThat(accidental.glyph()).isEqualTo(SMuFLGlyph.ACCIDENTAL_NATURAL));
        assertThat(accidentals.subList(cancelledCount, accidentals.size()))
            .as("the naturals come first, so none of them follows the signature it cancels")
            .allSatisfy(accidental ->
                assertThat(accidental.glyph()).isEqualTo(SMuFLGlyph.ACCIDENTAL_FLAT));

        assertThat(accidentals.stream().filter(accidental -> accidental.leadingGapSs() != 0))
            .as("one gap, and only one, separates the two runs")
            .hasSize(1);
        assertThat(accidentals.get(cancelledCount).leadingGapSs())
            .isCloseTo(StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS, within(TOLERANCE_SS));
    }

    // ==========================================================================
    // Invariants across every kind of change
    // ==========================================================================

    @ParameterizedTest(name = "{0}")
    @EnumSource(KeyChangeKind.class)
    void testWidthIsTheLaidOutWidthOfWhatIsDrawn(KeyChangeKind kind) {
        var accidentals = accidentalsOf(kind);
        var widthSs = KeyChange.widthSs(kind.previous(), kind.next());

        assertThat(widthSs).isCloseTo(KeyChange.totalWidthSs(accidentals), within(TOLERANCE_SS));
        assertThat(widthSs).isNotNegative();
        assertThat(widthSs > 0)
            .as("a width of zero means nothing is drawn, and vice versa")
            .isEqualTo(!accidentals.isEmpty());
    }

    /** Immutability cannot vary with the keys, only with whether anything was drawn at all. */
    @Test
    void testAccidentalsAreImmutableWhetherOrNotAnythingIsDrawn() {
        var intruder = new KeyChange.DrawnAccidental(SMuFLGlyph.ACCIDENTAL_NATURAL, 0, 0);

        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> accidentalsOf(KeyChangeKind.SHARPS_TO_FLATS).add(intruder));
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> accidentalsOf(KeyChangeKind.UNCHANGED).add(intruder));
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
        assertThat(KeyChange.signatureAccidentals(TestKeys.C_MAJOR)).isEmpty();
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
