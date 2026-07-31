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

import java.awt.Font;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.KeyType;

import static java.awt.font.TextAttribute.FONT;
import static java.awt.font.TextAttribute.TRACKING;
import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.ui.KeySignatureDisplay.FLAT_TONICS;
import static songscribe.ui.KeySignatureDisplay.MIN_FLAT_COUNT_WITH_ACCIDENTAL;
import static songscribe.ui.KeySignatureDisplay.MIN_SHARP_COUNT_WITH_ACCIDENTAL;
import static songscribe.ui.KeySignatureDisplay.SHARP_TONICS;

class KeySignatureDisplayTest extends UnitTest {

    // A tonic with an accidental glyph is always one letter followed by one glyph character.
    private static final int LETTER_PLUS_GLYPH_LENGTH = 2;

    @BeforeAll
    static void setUpFlatLaf() throws Exception {
        installFlatLafDefaults();
    }

    // --- helpers ----------------------------------------------------------

    /**
     * Extracts all characters from the AttributedString into a plain String.
     */
    private static String textOf(AttributedString attributed) {
        var iter = attributed.getIterator();
        var sb = new StringBuilder();

        for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Returns the Font attribute at the given index in an AttributedString.
     */
    private static Font fontAt(AttributedString attributed, int index) {
        var iter = attributed.getIterator(
            new AttributedCharacterIterator.Attribute[]{FONT}, index, index + 1
        );
        iter.first();
        return (Font) iter.getAttribute(FONT);
    }

    /**
     * Returns the TRACKING attribute embedded in the Font at the given index, or null if absent.
     * The production code stores TRACKING via {@code labelFont.deriveFont(Map.of(TRACKING, v))},
     * so it appears in the Font's attribute map, not as a standalone AttributedString attribute.
     */
    private static @Nullable Float trackingAt(AttributedString attributed, int index) {
        var font = fontAt(attributed, index);

        if (font == null) {
            return null;
        }

        return (Float) font.getAttributes().get(TRACKING);
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
        // suffix = " · 1 sharp"
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 1));
        assertThat(result).contains("1").contains("sharp");
    }

    @Test
    void testSuffixForSharpsCount2ContainsPluralSharps() {
        // suffix = " · 2 sharps"
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

    // --- Row 5: suffixFor returns non-empty suffix with count for FLATS ----

    @Test
    void testSuffixForFlatsCount1ContainsSingularFlat() {
        // suffix = " · 1 flat"
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 1));
        assertThat(result).contains("1").contains("flat");
    }

    @Test
    void testSuffixForFlatsCount2ContainsPluralFlats() {
        // suffix = " · 2 flats"
        var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, 2));
        assertThat(result).contains("2").contains("flats");
    }

    @Test
    void testSuffixForFlatsCountContainsCountAndWord() {
        // Verify the choice format for counts 3–7
        for (int count = 3; count <= FLAT_TONICS.length - 1; count++) {
            var result = textOf(KeySignatureDisplay.getDisplayName(KeyType.FLATS, count));
            assertThat(result)
                .as("count=" + count)
                .contains(String.valueOf(count))
                .containsPattern("flat(s)?");
        }
    }

    // --- Row 6: tonicHasAccidental FLATS boundary at MIN_FLAT_COUNT_WITH_ACCIDENTAL ---

    @Test
    void testTonicHasAccidentalFlatsBelowThresholdHasNoGlyphFont() {
        // FLATS count < MIN_FLAT_COUNT_WITH_ACCIDENTAL (2) → no accidental → no glyph-font override
        // FLATS/1: tonic = "F" (1 char), no accidental branch → only labelFont applied
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.FLATS, 1);
        var fontAtTonic = fontAt(attributed, 0);
        assertThat(trackingAt(attributed, 0)).isNull();
        // Only label font family — not the music font family
        assertThat(fontAtTonic.getName()).doesNotContainIgnoringCase("Bravura");
    }

    @Test
    void testTonicHasAccidentalFlatsAtThresholdHasGlyphFont() {
        // FLATS count == MIN_FLAT_COUNT_WITH_ACCIDENTAL (2) → accidental present
        // FLAT_TONICS[2] = "B♭" (2 chars), glyphIndex = 1
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.FLATS, MIN_FLAT_COUNT_WITH_ACCIDENTAL);
        // Index 1 (♭) must use the music font (Bravura family)
        var glyphFont = fontAt(attributed, 1);
        assertThat(glyphFont.getName()).containsIgnoringCase("Bravura");
        // Index 0 ('B') must have tracking applied
        assertThat(trackingAt(attributed, 0)).isNotNull().isGreaterThan(0f);
    }

    // --- Row 7: tonicHasAccidental SHARPS boundary at MIN_SHARP_COUNT_WITH_ACCIDENTAL ---

    @Test
    void testTonicHasAccidentalSharpsBelowThresholdHasNoGlyphFont() {
        // SHARPS count < MIN_SHARP_COUNT_WITH_ACCIDENTAL (6) → no accidental
        // SHARP_TONICS[5] = "B" (1 char)
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.SHARPS, MIN_SHARP_COUNT_WITH_ACCIDENTAL - 1);
        var fontAtTonic = fontAt(attributed, 0);
        assertThat(trackingAt(attributed, 0)).isNull();
        assertThat(fontAtTonic.getName()).doesNotContainIgnoringCase("Bravura");
    }

    @Test
    void testTonicHasAccidentalSharpsAtThresholdHasGlyphFont() {
        // SHARPS count == MIN_SHARP_COUNT_WITH_ACCIDENTAL (6) → accidental present
        // SHARP_TONICS[6] = "F♯" (2 chars), glyphIndex = 1
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.SHARPS, MIN_SHARP_COUNT_WITH_ACCIDENTAL);
        // Index 1 (♯) must use the music font
        var glyphFont = fontAt(attributed, 1);
        assertThat(glyphFont.getName()).containsIgnoringCase("Bravura");
        // Index 0 ('F') must have tracking applied
        assertThat(trackingAt(attributed, 0)).isNotNull().isGreaterThan(0f);
    }

    // --- Row 8: tonicHasAccidental NONE always returns false ---------------

    @Test
    void testTonicHasAccidentalNoneWithNonzeroCountHasNoGlyphFont() {
        // KeyType.NONE → tonicHasAccidental returns false regardless of count
        // Even with count = 7, no glyph font is applied
        for (int count = 1; count <= FLAT_TONICS.length - 1; count++) {
            var attributed = KeySignatureDisplay.getDisplayName(KeyType.NONE, count);
            var text = textOf(attributed);
            // Tonic is non-empty; font at index 0 must not be Bravura
            var fontAt0 = fontAt(attributed, 0);
            assertThat(fontAt0.getName())
                .as("NONE/count=" + count + " text=" + text)
                .doesNotContainIgnoringCase("Bravura");
        }
    }

    // --- Row 9: getDisplayName with count==0 or NONE: empty-string guard ---

    @Test
    void testGetDisplayNameSharpsCount0ReturnsAttributedOverEmptyString() {
        // SHARP_TONICS[0]="" and suffix for count=0 is "" → full="" → empty-string guard fires
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 0);
        assertThat(textOf(attributed)).isEmpty();
    }

    // --- Row 10: getDisplayName no tonic accidental — single labelFont only ---

    @Test
    void testGetDisplayNameNoAccidentalAppliesSingleLabelFont() {
        // SHARPS/1: tonic="G" (no accidental), suffix=" · 1 sharp"
        // All characters should have the same font (labelFont); no TRACKING attribute anywhere.
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.SHARPS, 1);
        var text = textOf(attributed);
        var fontAt0 = fontAt(attributed, 0);

        for (int i = 0; i < text.length(); i++) {
            assertThat(fontAt(attributed, i))
                .as("font at index " + i)
                .isEqualTo(fontAt0);
            assertThat(trackingAt(attributed, i))
                .as("tracking at index " + i)
                .isNull();
        }

        // The font must not be the music font
        assertThat(fontAt0.getName()).doesNotContainIgnoringCase("Bravura");
    }

    // --- Row 11: getDisplayName with tonic accidental — correct font ranges ---

    @Test
    void testGetDisplayNameWithAccidentalAppliesGlyphFontAndTracking() {
        // FLATS/3: tonic = "E♭" (FLAT_TONICS[3]), glyphIndex = 1
        // Index 0 ('E') → letterWithGapFont (labelFont + TRACKING > 0)
        // Index 1 ('♭') → glyphFont (Bravura family)
        // Index 2+ (suffix chars) → labelFont (no tracking, no Bravura)
        var attributed = KeySignatureDisplay.getDisplayName(KeyType.FLATS, 3);
        var text = textOf(attributed);

        // Tonic is letter + glyph; glyphIndex = 1
        var tonicLength = FLAT_TONICS[3].length();
        assertThat(tonicLength).isEqualTo(LETTER_PLUS_GLYPH_LENGTH);
        var glyphIndex = tonicLength - 1;

        // glyph position: music font
        var glyphFont = fontAt(attributed, glyphIndex);
        assertThat(glyphFont.getName())
            .as("glyph at index " + glyphIndex + " must be Bravura")
            .containsIgnoringCase("Bravura");

        // letter before glyph: tracking applied
        var letterTracking = trackingAt(attributed, glyphIndex - 1);
        assertThat(letterTracking)
            .as("tracking on letter before glyph")
            .isNotNull()
            .isGreaterThan(0f);

        // suffix chars: no glyph font, no tracking
        for (int i = tonicLength; i < text.length(); i++) {
            assertThat(fontAt(attributed, i).getName())
                .as("suffix char at " + i + " must not be Bravura")
                .doesNotContainIgnoringCase("Bravura");
            assertThat(trackingAt(attributed, i))
                .as("no tracking on suffix char at " + i)
                .isNull();
        }
    }
}
