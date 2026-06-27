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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.dom.ScaleContext;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KeySignatureTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    // Clamp bounds for accidentalCount — mirror the production contract
    private static final int MIN_ACCIDENTAL_COUNT = 0;

    // Sentinel values that lie one step outside the legal range
    private static final int BELOW_MIN_ACCIDENTAL_COUNT = -1;
    private static final int ABOVE_MAX_ACCIDENTAL_COUNT = KeySignature.MAX_ACCIDENTAL_COUNT + 1;

    // -----------------------------------------------------------------------
    // Row 37 + Row 42: constructor and setAccidentalCount clamp to 0–7
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AccidentalCountClamping {

        @Test
        void testConstructorClampsBelowMinToMin() {
            var keySig = new KeySignature(KeyType.SHARPS, BELOW_MIN_ACCIDENTAL_COUNT);

            assertThat(keySig.getAccidentalCount()).isEqualTo(MIN_ACCIDENTAL_COUNT);
        }

        @Test
        void testConstructorClampsAboveMaxToMax() {
            var keySig = new KeySignature(KeyType.SHARPS, ABOVE_MAX_ACCIDENTAL_COUNT);

            assertThat(keySig.getAccidentalCount()).isEqualTo(KeySignature.MAX_ACCIDENTAL_COUNT);
        }

        @Test
        void testSetAccidentalCountClampsBelowMinToMin() {
            var keySig = new KeySignature(KeyType.SHARPS, MIN_ACCIDENTAL_COUNT);
            keySig.setAccidentalCount(BELOW_MIN_ACCIDENTAL_COUNT);

            assertThat(keySig.getAccidentalCount()).isEqualTo(MIN_ACCIDENTAL_COUNT);
        }

        @Test
        void testSetAccidentalCountClampsAboveMaxToMax() {
            var keySig = new KeySignature(KeyType.SHARPS, MIN_ACCIDENTAL_COUNT);
            keySig.setAccidentalCount(ABOVE_MAX_ACCIDENTAL_COUNT);

            assertThat(keySig.getAccidentalCount()).isEqualTo(KeySignature.MAX_ACCIDENTAL_COUNT);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EmptySignature {

        @Test
        void testNoneReturnsZeroDimensions() {
            var keySig = new KeySignature(KeyType.NONE, 0);

            assertThat(keySig.getContentWidthSs()).isZero();
            assertThat(keySig.getContentHeightSs()).isZero();
            assertThat(keySig.getContentWidthPx()).isZero();
            assertThat(keySig.getContentHeightPx()).isZero();
        }

        @Test
        void testZeroAccidentalsReturnsZeroDimensions() {
            var keySig = new KeySignature(KeyType.SHARPS, 0);

            assertThat(keySig.getContentWidthSs()).isZero();
            assertThat(keySig.getContentHeightSs()).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // Row 38: hasAccidentals() — direct assertions for all three conditions
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasAccidentals {

        // count == 0 → false regardless of type
        @Test
        void testHasAccidentalsReturnsFalseWhenCountIsZero() {
            var keySig = new KeySignature(KeyType.SHARPS, MIN_ACCIDENTAL_COUNT);

            assertThat(keySig.hasAccidentals()).isFalse();
        }

        // type NONE → false regardless of count
        @Test
        void testHasAccidentalsReturnsFalseWhenTypeIsNone() {
            var keySig = new KeySignature(KeyType.NONE, KeySignature.MAX_ACCIDENTAL_COUNT);

            assertThat(keySig.hasAccidentals()).isFalse();
        }

        // count > 0 and type != NONE → true
        @Test
        void testHasAccidentalsReturnsTrueWhenCountPositiveAndTypeNotNone() {
            var keySig = new KeySignature(KeyType.SHARPS, KeySignature.MAX_ACCIDENTAL_COUNT);

            assertThat(keySig.hasAccidentals()).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Sharps {

        @Test
        void testWidthIsCountTimesSharpBBoxWidth() {
            var keySig = new KeySignature(KeyType.SHARPS, 3);
            var sharpBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ACCIDENTAL_SHARP);
            var expected = 3 * sharpBBox.width();

            assertThat(keySig.getContentWidthSs()).isCloseTo(expected, within(EPSILON));
        }

        @Test
        void testHeightIsSharpBBoxHeight() {
            var keySig = new KeySignature(KeyType.SHARPS, 3);
            var sharpBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ACCIDENTAL_SHARP);

            assertThat(keySig.getContentHeightSs()).isCloseTo(sharpBBox.height(), within(EPSILON));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Flats {

        @Test
        void testWidthIsCountTimesFlatBBoxWidth() {
            var keySig = new KeySignature(KeyType.FLATS, 4);
            var flatBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ACCIDENTAL_FLAT);
            var expected = 4 * flatBBox.width();

            assertThat(keySig.getContentWidthSs()).isCloseTo(expected, within(EPSILON));
        }

        @Test
        void testHeightIsFlatBBoxHeight() {
            var keySig = new KeySignature(KeyType.FLATS, 4);
            var flatBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ACCIDENTAL_FLAT);

            assertThat(keySig.getContentHeightSs()).isCloseTo(flatBBox.height(), within(EPSILON));
        }
    }

    @Test
    void testPxDerivesFromSs() {
        var keySig = new KeySignature(KeyType.SHARPS, 2);
        assertThat(keySig.getContentWidthPx())
            .isCloseTo(ScaleContext.ssToPx(keySig.getContentWidthSs()), within(EPSILON));
        assertThat(keySig.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(keySig.getContentHeightSs()), within(EPSILON));
    }
}
