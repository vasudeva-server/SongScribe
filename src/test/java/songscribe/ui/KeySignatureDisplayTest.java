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

package songscribe.ui;

import java.text.CharacterIterator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.KeyType;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.ui.KeySignatureDisplay.FLAT_TONICS;
import static songscribe.ui.KeySignatureDisplay.SHARP_TONICS;

class KeySignatureDisplayTest extends UnitTest {

    @BeforeAll
    static void setUpFlatLaf() throws Exception {
        installFlatLafDefaults();
    }

    // --- helper -----------------------------------------------------------

    /**
     * Extracts all characters from the AttributedString into a plain String.
     */
    private static String textOf(java.text.AttributedString attributed) {
        var iter = attributed.getIterator();
        var sb = new StringBuilder();

        for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
            sb.append(c);
        }

        return sb.toString();
    }

    // --- Row 1: tonicFor SHARPS ------------------------------------------

    @Test
    void testTonicForSharpsCount0ReturnsEmptyString() {
        // SHARP_TONICS[0]="" → full string is empty (count=0 also empties the suffix)
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 0));
        assertThat(result).isEmpty();
    }

    @Test
    void testTonicForSharpsCount1ReturnsG() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 1));
        assertThat(result).startsWith(SHARP_TONICS[1]);
    }

    @Test
    void testTonicForSharpsCount2ReturnsD() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 2));
        assertThat(result).startsWith(SHARP_TONICS[2]);
    }

    @Test
    void testTonicForSharpsCount3ReturnsA() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 3));
        assertThat(result).startsWith(SHARP_TONICS[3]);
    }

    @Test
    void testTonicForSharpsCount4ReturnsE() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 4));
        assertThat(result).startsWith(SHARP_TONICS[4]);
    }

    @Test
    void testTonicForSharpsCount5ReturnsB() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 5));
        assertThat(result).startsWith(SHARP_TONICS[5]);
    }

    @Test
    void testTonicForSharpsCount6ReturnsFSharp() {
        // SHARP_TONICS[6] = "F" + SHARP_GLYPH — two-char tonic
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 6));
        assertThat(result).startsWith(SHARP_TONICS[6]);
    }

    @Test
    void testTonicForSharpsCount7ReturnsCSharp() {
        // SHARP_TONICS[7] = "C" + SHARP_GLYPH — two-char tonic
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 7));
        assertThat(result).startsWith(SHARP_TONICS[7]);
    }

    // --- Row 2: tonicFor FLATS -------------------------------------------

    @Test
    void testTonicForFlatsCount0ReturnsC() {
        // FLAT_TONICS[0]="C", count=0 → suffix="" → full="C"
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 0));
        assertThat(result).isEqualTo(FLAT_TONICS[0]);
    }

    @Test
    void testTonicForFlatsCount1ReturnsF() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 1));
        assertThat(result).startsWith(FLAT_TONICS[1]);
    }

    @Test
    void testTonicForFlatsCount2ReturnsBFlat() {
        // FLAT_TONICS[2] = "B" + FLAT_GLYPH — two-char tonic
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 2));
        assertThat(result).startsWith(FLAT_TONICS[2]);
    }

    @Test
    void testTonicForFlatsCount3ReturnsEFlat() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 3));
        assertThat(result).startsWith(FLAT_TONICS[3]);
    }

    @Test
    void testTonicForFlatsCount4ReturnsAFlat() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 4));
        assertThat(result).startsWith(FLAT_TONICS[4]);
    }

    @Test
    void testTonicForFlatsCount5ReturnsDFlat() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 5));
        assertThat(result).startsWith(FLAT_TONICS[5]);
    }

    @Test
    void testTonicForFlatsCount6ReturnsGFlat() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 6));
        assertThat(result).startsWith(FLAT_TONICS[6]);
    }

    @Test
    void testTonicForFlatsCount7ReturnsCFlat() {
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 7));
        assertThat(result).startsWith(FLAT_TONICS[7]);
    }

    // --- Row 3: suffixFor returns empty for NONE or count==0 --------------

    @Test
    void testSuffixForNoneTypeEqualsTonicOnly() {
        // NONE → suffix="" → full is just the tonic, no suffix appended
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.NONE, 3));
        // tonic for NONE is FLAT_TONICS[3] = "E" + FLAT_GLYPH, suffix=""
        var tonic = FLAT_TONICS[3];
        assertThat(result).isEqualTo(tonic);
    }

    // --- Row 4: suffixFor returns non-empty suffix with count for SHARPS --

    @Test
    void testSuffixForSharpsCount1ContainsSingularSharp() {
        // suffix = " ・ 1 sharp"
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 1));
        assertThat(result).contains("1").contains("sharp");
    }

    @Test
    void testSuffixForSharpsCount2ContainsPluralSharps() {
        // suffix = " ・ 2 sharps"
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 2));
        assertThat(result).contains("2").contains("sharps");
    }

    @Test
    void testSuffixForSharpsCountContainsCountAndWord() {
        // Verify the choice format for counts 3–7 (counts 1 and 2 are covered by the preceding tests)
        for (int count = 3; count <= SHARP_TONICS.length - 1; count++) {
            var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, count));
            assertThat(result)
                .as("count=" + count)
                .contains(String.valueOf(count))
                .containsPattern("sharp(s)?");
        }
    }
}
