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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

/**
 * Exercises the contract of {@link Key}.
 *
 * <p><b>Valid domain</b> — enumerated, not sampled: every value returned by
 * {@link Key#allSignatures()}, asserting construction succeeds and the
 * biconditional invariant holds for each.
 *
 * <p><b>Invariants</b> — {@link Key#allSignatures()} returns one key for
 * {@link KeyType#NONE} plus one per accidental count for each of the two other
 * types, in the documented order, with no duplicates, and returns the same
 * immutable list every time; {@link Key#altersPitchClass(int)} alters exactly
 * {@code accidentalCount} pitch classes for every key, and each key's altered set
 * nests the previous key's for the same {@link KeyType}, which is what the fifths
 * order guarantees.
 *
 * <p><b>Boundaries</b> — {@code accidentalCount} at 0 (with {@code NONE}), 1 and
 * {@link Key#MAX_ACCIDENTAL_COUNT} succeed; {@code -1} and
 * {@code MAX_ACCIDENTAL_COUNT + 1} throw.
 *
 * <p><b>Errors</b> — every invariant-violating {@code (keyType, accidentalCount)}
 * pair throws {@code IllegalArgumentException}, enumerated from the domain rather
 * than sampled.
 */
class KeyTest extends UnitTest {

    static Stream<Key> allSignatures() {
        return Key.allSignatures().stream();
    }

    @ParameterizedTest
    @MethodSource("allSignatures")
    void testAllSignaturesConstructSuccessfullyAndSatisfyTheInvariant(Key key) {
        assertThat(key.keyType() == KeyType.NONE).isEqualTo(key.accidentalCount() == 0);
    }

    @Test
    void testAllSignaturesReturnsTheWholeDomainInDocumentedOrderWithNoDuplicates() {
        // C major, plus one key per accidental count for each of FLATS and SHARPS.
        final var expectedSize = 1 + (2 * Key.MAX_ACCIDENTAL_COUNT);

        var expected = new ArrayList<Key>();
        expected.add(new Key(KeyType.NONE, 0));

        for (var count = 1; count <= Key.MAX_ACCIDENTAL_COUNT; count++) {
            expected.add(new Key(KeyType.FLATS, count));
        }

        for (var count = 1; count <= Key.MAX_ACCIDENTAL_COUNT; count++) {
            expected.add(new Key(KeyType.SHARPS, count));
        }

        assertThat(Key.allSignatures())
            .hasSize(expectedSize)
            .doesNotHaveDuplicates()
            .containsExactlyElementsOf(expected);
    }

    @Test
    void testAllSignaturesReturnsTheSameSharedInstanceEveryCall() {
        assertThat(Key.allSignatures()).isSameAs(Key.allSignatures());
    }

    @Test
    void testAllSignaturesRejectsMutation() {
        var signatures = Key.allSignatures();

        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> signatures.add(Key.DEFAULT));
    }

    private record InvalidKey(KeyType keyType, int accidentalCount) {}

    static Stream<InvalidKey> invalidKeys() {
        return Stream.concat(
            IntStream.rangeClosed(1, Key.MAX_ACCIDENTAL_COUNT)
                .mapToObj(count -> new InvalidKey(KeyType.NONE, count)),
            Stream.of(
                new InvalidKey(KeyType.SHARPS, 0),
                new InvalidKey(KeyType.FLATS, 0)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void testConstructorThrowsForInvariantViolations(InvalidKey invalidKey) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Key(invalidKey.keyType(), invalidKey.accidentalCount()));
    }

    @Test
    void testAccidentalCountBelowZeroThrows() {
        final var belowMin = -1;

        assertThatIllegalArgumentException().isThrownBy(() -> new Key(KeyType.FLATS, belowMin));
    }

    @Test
    void testAccidentalCountAboveMaxThrows() {
        final var aboveMax = Key.MAX_ACCIDENTAL_COUNT + 1;

        assertThatIllegalArgumentException().isThrownBy(() -> new Key(KeyType.FLATS, aboveMax));
    }

    @Test
    void testAccidentalCountAtBoundariesSucceeds() {
        final var min = 1;

        assertThat(new Key(KeyType.FLATS, min).accidentalCount()).isEqualTo(min);
        assertThat(new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT).accidentalCount())
            .isEqualTo(Key.MAX_ACCIDENTAL_COUNT);
    }

    @Test
    void testDefaultIsFiveFlatsAndSatisfiesTheInvariant() {
        final var expectedCount = 5;

        assertThat(Key.DEFAULT.keyType()).isEqualTo(KeyType.FLATS);
        assertThat(Key.DEFAULT.accidentalCount()).isEqualTo(expectedCount);
    }

    @ParameterizedTest
    @MethodSource("allSignatures")
    void testAltersPitchClassCountEqualsAccidentalCount(Key key) {
        var alteredCount = (int) IntStream.range(0, PITCH_CLASS_COUNT)
            .filter(key::altersPitchClass)
            .count();

        assertThat(alteredCount).isEqualTo(key.accidentalCount());
    }

    private static final int PITCH_CLASS_COUNT = 7;
    private static final int PITCH_INDEX_B     = 0;
    private static final int PITCH_INDEX_F     = 4;

    @Test
    void testAltersPitchClassOrderStartsWithBForFlatsAndFForSharps() {
        assertThat(new Key(KeyType.FLATS, 1).altersPitchClass(PITCH_INDEX_B)).isTrue();
        assertThat(new Key(KeyType.SHARPS, 1).altersPitchClass(PITCH_INDEX_F)).isTrue();
    }

    @Test
    void testAltersPitchClassSetsNestWithinEachKeyType() {
        for (var keyType : List.of(KeyType.FLATS, KeyType.SHARPS)) {
            for (var count = 2; count <= Key.MAX_ACCIDENTAL_COUNT; count++) {
                var previous = new Key(keyType, count - 1);
                var current  = new Key(keyType, count);

                for (var pitchIndex = 0; pitchIndex < PITCH_CLASS_COUNT; pitchIndex++) {
                    if (previous.altersPitchClass(pitchIndex)) {
                        assertThat(current.altersPitchClass(pitchIndex))
                            .as("%s must alter every pitch class %s does", current, previous)
                            .isTrue();
                    }
                }
            }
        }
    }
}
