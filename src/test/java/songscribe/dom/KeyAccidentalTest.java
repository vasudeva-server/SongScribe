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

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Key#alterationFor}'s promise to answer the corpus's recorded accidental — or
 * {@link Accidental#NATURAL} — for every pitch class, for every key.
 *
 * <p>The corpus is the fifteen key signatures themselves: each names the pitch classes it alters,
 * in the order a copyist writes them. The cases cross that with the whole of {@link PitchClass},
 * so every key is asked about every pitch class it does not alter as well as every one it does.
 */
class KeyAccidentalTest extends UnitTest {

    /**
     * One key signature: the pitch classes it alters, in writing order, and the accidental it
     * writes on each of them. {@code accidental} is unused when nothing is altered.
     */
    private record KeySignature(Key key, Accidental accidental, List<PitchClass> altered) {

        Accidental on(PitchClass pitchClass) {
            return altered.contains(pitchClass) ? accidental : Accidental.NATURAL;
        }
    }

    private record KeyAccidentalCase(Key key, PitchClass pitchClass, Accidental expected) {}

    private static final List<KeySignature> SIGNATURES = List.of(
        new KeySignature(Key.NO_ACCIDENTALS, Accidental.NATURAL, List.of()),
        sharps(Key.ONE_SHARP, PitchClass.F),
        sharps(Key.TWO_SHARPS, PitchClass.F, PitchClass.C),
        sharps(Key.THREE_SHARPS, PitchClass.F, PitchClass.C, PitchClass.G),
        sharps(Key.FOUR_SHARPS, PitchClass.F, PitchClass.C, PitchClass.G, PitchClass.D),
        sharps(Key.FIVE_SHARPS, PitchClass.F, PitchClass.C, PitchClass.G, PitchClass.D, PitchClass.A),
        sharps(Key.SIX_SHARPS,
            PitchClass.F, PitchClass.C, PitchClass.G, PitchClass.D, PitchClass.A, PitchClass.E),
        sharps(Key.SEVEN_SHARPS,
            PitchClass.F, PitchClass.C, PitchClass.G, PitchClass.D, PitchClass.A, PitchClass.E, PitchClass.B),
        flats(Key.ONE_FLAT, PitchClass.B),
        flats(Key.TWO_FLATS, PitchClass.B, PitchClass.E),
        flats(Key.THREE_FLATS, PitchClass.B, PitchClass.E, PitchClass.A),
        flats(Key.FOUR_FLATS, PitchClass.B, PitchClass.E, PitchClass.A, PitchClass.D),
        flats(Key.FIVE_FLATS, PitchClass.B, PitchClass.E, PitchClass.A, PitchClass.D, PitchClass.G),
        flats(Key.SIX_FLATS,
            PitchClass.B, PitchClass.E, PitchClass.A, PitchClass.D, PitchClass.G, PitchClass.C),
        flats(Key.SEVEN_FLATS,
            PitchClass.B, PitchClass.E, PitchClass.A, PitchClass.D, PitchClass.G, PitchClass.C, PitchClass.F)
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testAlterationForMatchesTheCorpus(KeyAccidentalCase testCase) {
        assertThat(testCase.key().alterationFor(testCase.pitchClass())).isEqualTo(testCase.expected());
    }

    @Test
    void testCorpusCoversEveryKey() {
        assertThat(SIGNATURES.stream().map(KeySignature::key))
            .containsExactlyInAnyOrder(Key.values());
    }

    @Test
    void testCorpusCoversEveryKeyAndPitchClass() {
        assertThat(cases()).hasSize(Key.values().length * PitchClass.values().length);
    }

    static Stream<KeyAccidentalCase> cases() {
        return SIGNATURES.stream()
            .flatMap(signature -> Stream.of(PitchClass.values())
                .map(pitchClass -> new KeyAccidentalCase(
                    signature.key(), pitchClass, signature.on(pitchClass))));
    }

    private static KeySignature sharps(Key key, PitchClass... altered) {
        return new KeySignature(key, Accidental.SHARP, List.of(altered));
    }

    private static KeySignature flats(Key key, PitchClass... altered) {
        return new KeySignature(key, Accidental.FLAT, List.of(altered));
    }
}
