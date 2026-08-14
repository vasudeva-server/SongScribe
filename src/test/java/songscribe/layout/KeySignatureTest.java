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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.KeyChange;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.dom.ScaleContext;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Exercises the contract of {@link KeySignature}, the header's positioned layout box for a
 * {@link Key}.
 *
 * <p><b>Valid domain</b> — enumerated, not sampled: every key
 * {@link Key#allSignatures()} names, driven from that list so a change to the domain reaches
 * these cases on its own. A {@code KeySignature} can hold nothing else — {@code Key}'s own
 * constructor rejects the pairs that used to be clamped here, and {@code KeyTest} owns those
 * cases.
 *
 * <p><b>Width</b> — the accidentals nest at their glyph's ink width, so a signature is that
 * width times its accidental count. Asserted per key rather than for a sharp and a flat
 * example, since the two glyphs differ in width and every count is a distinct answer.
 *
 * <p><b>The shared-measurement invariant</b> — the header's width for a key equals
 * {@link KeyChange}'s width for a change into that key from C major, which is the same run of
 * accidentals reached by the other route. This is the promise that keeps a header and a
 * cautionary key change from drifting apart, and the one clause here that would survive a
 * rewrite of how either side computes.
 *
 * <p><b>Boundary</b> — {@link KeyType#NONE} draws nothing and measures zero in both
 * dimensions; it is the only key that does.
 *
 * <p><b>Unit conversion</b> — the {@code Px} accessors derive from the {@code Ss} ones. One
 * representative key, since the conversion does not vary with the key.
 */
class KeySignatureTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    /** A key with accidentals, for the cases that do not vary across the domain. */
    private static final Key REPRESENTATIVE_KEY = new Key(KeyType.SHARPS, 2);

    static Stream<Key> allKeys() {
        return Key.allSignatures().stream();
    }

    static Stream<Key> keysWithAccidentals() {
        return allKeys().filter(key -> key.keyType() != KeyType.NONE);
    }

    // -----------------------------------------------------------------------
    // The key it was built for
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("allKeys")
    void testTheSignatureHoldsTheKeyItWasBuiltFor(Key key) {
        assertThat(new KeySignature(key).getKey()).isEqualTo(key);
    }

    // -----------------------------------------------------------------------
    // Width
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("keysWithAccidentals")
    void testWidthIsTheAccidentalCountTimesItsGlyphInkWidth(Key key) {
        var glyphWidthSs =
            StaffHeaderMetrics.accidentalInkBboxSs(KeyChange.accidentalGlyph(key.keyType()));

        assertThat(new KeySignature(key).getContentWidthSs())
            .isCloseTo(key.accidentalCount() * glyphWidthSs, within(EPSILON));
    }

    /**
     * The header and a cautionary key change measure the same run of accidentals, so a change
     * into {@code key} from C major — which draws no cancellation, only the new signature — is
     * exactly as wide as the header for {@code key}.
     */
    @ParameterizedTest
    @MethodSource("allKeys")
    void testWidthAgreesWithTheWidthOfAChangeIntoTheSameKey(Key key) {
        assertThat(KeySignature.widthSs(key))
            .isCloseTo(KeyChange.widthSs(new Key(KeyType.NONE, 0), key), within(EPSILON));
    }

    // -----------------------------------------------------------------------
    // Height
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("keysWithAccidentals")
    void testHeightIsTheAccidentalGlyphBBoxHeight(Key key) {
        var bbox = SMuFLMetadata.requireBBox(KeyChange.accidentalGlyph(key.keyType()));

        assertThat(new KeySignature(key).getContentHeightSs())
            .isCloseTo(bbox.height(), within(EPSILON));
    }

    // -----------------------------------------------------------------------
    // The C major boundary — the one key that draws nothing
    // -----------------------------------------------------------------------

    @Test
    void testCMajorMeasuresZeroInBothDimensions() {
        var keySig = new KeySignature(new Key(KeyType.NONE, 0));

        assertThat(keySig.getContentWidthSs()).isZero();
        assertThat(keySig.getContentHeightSs()).isZero();
        assertThat(keySig.getContentWidthPx()).isZero();
        assertThat(keySig.getContentHeightPx()).isZero();
    }

    @ParameterizedTest
    @MethodSource("keysWithAccidentals")
    void testEveryKeyWithAccidentalsMeasuresMoreThanZero(Key key) {
        var keySig = new KeySignature(key);

        assertThat(keySig.getContentWidthSs()).isPositive();
        assertThat(keySig.getContentHeightSs()).isPositive();
    }

    // -----------------------------------------------------------------------
    // Unit conversion
    // -----------------------------------------------------------------------

    @Test
    void testPxDerivesFromSs() {
        var keySig = new KeySignature(REPRESENTATIVE_KEY);

        assertThat(keySig.getContentWidthPx())
            .isCloseTo(ScaleContext.ssToPx(keySig.getContentWidthSs()), within(EPSILON));
        assertThat(keySig.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(keySig.getContentHeightSs()), within(EPSILON));
    }
}
