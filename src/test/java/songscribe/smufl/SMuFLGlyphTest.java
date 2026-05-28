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

package songscribe.smufl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Spot-checks {@link SMuFLGlyph} constants against independently-known SMuFL 1.4 spec
 * values, so that a transposed codepoint or misspelled name is detectable.
 *
 * Expected values are taken directly from the SMuFL specification
 * (https://w3c-cg.github.io/smufl/latest/) -- NOT derived from the production
 * accessors under test.
 */
class SMuFLGlyphTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Spec-canonical SMuFL names (camelCase) -- Row 20
    // -------------------------------------------------------------------------

    private static final String SPEC_NAME_G_CLEF           = "gClef";
    private static final String SPEC_NAME_C_CLEF           = "cClef";
    private static final String SPEC_NAME_F_CLEF           = "fClef";
    private static final String SPEC_NAME_NOTEHEAD_BLACK   = "noteheadBlack";
    private static final String SPEC_NAME_NOTEHEAD_WHOLE   = "noteheadWhole";
    private static final String SPEC_NAME_NOTEHEAD_HALF    = "noteheadHalf";
    private static final String SPEC_NAME_ACCIDENTAL_SHARP = "accidentalSharp";
    private static final String SPEC_NAME_ACCIDENTAL_FLAT  = "accidentalFlat";
    private static final String SPEC_NAME_FERMATA_ABOVE    = "fermataAbove";
    private static final String SPEC_NAME_FERMATA_BELOW    = "fermataBelow";

    // -------------------------------------------------------------------------
    // Spec-canonical codepoints (Unicode, SMuFL 1.4) -- Row 21
    //
    // Source: https://w3c-cg.github.io/smufl/latest/tables/
    // Using Unicode escapes (backslash-u + hex) to match production code style.
    // -------------------------------------------------------------------------
    // Clefs -- U+E050 range
    private static final char SPEC_CODEPOINT_G_CLEF = '\uE050';
    private static final char SPEC_CODEPOINT_C_CLEF = '\uE05C';
    private static final char SPEC_CODEPOINT_F_CLEF = '\uE062';

    // Noteheads -- U+E0A0 range
    private static final char SPEC_CODEPOINT_NOTEHEAD_WHOLE = '\uE0A2';
    private static final char SPEC_CODEPOINT_NOTEHEAD_HALF  = '\uE0A3';
    private static final char SPEC_CODEPOINT_NOTEHEAD_BLACK = '\uE0A4';

    // Accidentals -- U+E260 range
    private static final char SPEC_CODEPOINT_ACCIDENTAL_FLAT    = '\uE260';
    private static final char SPEC_CODEPOINT_ACCIDENTAL_NATURAL = '\uE261';
    private static final char SPEC_CODEPOINT_ACCIDENTAL_SHARP   = '\uE262';

    // Holds and pauses -- U+E4C0 range
    private static final char SPEC_CODEPOINT_FERMATA_ABOVE = '\uE4C0';
    private static final char SPEC_CODEPOINT_FERMATA_BELOW = '\uE4C1';

    // -------------------------------------------------------------------------
    // Row 20 -- smuflName() returns canonical SMuFL name string
    // -------------------------------------------------------------------------

    @Test
    void testSmuflNameMatchesSpec() {
        // Clefs
        assertThat(SMuFLGlyph.G_CLEF.smuflName()).isEqualTo(SPEC_NAME_G_CLEF);
        assertThat(SMuFLGlyph.C_CLEF.smuflName()).isEqualTo(SPEC_NAME_C_CLEF);
        assertThat(SMuFLGlyph.F_CLEF.smuflName()).isEqualTo(SPEC_NAME_F_CLEF);

        // Noteheads
        assertThat(SMuFLGlyph.NOTEHEAD_BLACK.smuflName()).isEqualTo(SPEC_NAME_NOTEHEAD_BLACK);
        assertThat(SMuFLGlyph.NOTEHEAD_WHOLE.smuflName()).isEqualTo(SPEC_NAME_NOTEHEAD_WHOLE);
        assertThat(SMuFLGlyph.NOTEHEAD_HALF.smuflName()).isEqualTo(SPEC_NAME_NOTEHEAD_HALF);

        // Accidentals
        assertThat(SMuFLGlyph.ACCIDENTAL_SHARP.smuflName()).isEqualTo(SPEC_NAME_ACCIDENTAL_SHARP);
        assertThat(SMuFLGlyph.ACCIDENTAL_FLAT.smuflName()).isEqualTo(SPEC_NAME_ACCIDENTAL_FLAT);

        // Holds and pauses
        assertThat(SMuFLGlyph.FERMATA_ABOVE.smuflName()).isEqualTo(SPEC_NAME_FERMATA_ABOVE);
        assertThat(SMuFLGlyph.FERMATA_BELOW.smuflName()).isEqualTo(SPEC_NAME_FERMATA_BELOW);
    }

    // -------------------------------------------------------------------------
    // Row 21 -- codepoint() returns correct Unicode codepoint
    // -------------------------------------------------------------------------

    @Test
    void testCodepointMatchesSpec() {
        // Clefs
        assertThat(SMuFLGlyph.G_CLEF.codepoint()).isEqualTo(SPEC_CODEPOINT_G_CLEF);
        assertThat(SMuFLGlyph.C_CLEF.codepoint()).isEqualTo(SPEC_CODEPOINT_C_CLEF);
        assertThat(SMuFLGlyph.F_CLEF.codepoint()).isEqualTo(SPEC_CODEPOINT_F_CLEF);

        // Noteheads
        assertThat(SMuFLGlyph.NOTEHEAD_WHOLE.codepoint()).isEqualTo(SPEC_CODEPOINT_NOTEHEAD_WHOLE);
        assertThat(SMuFLGlyph.NOTEHEAD_HALF.codepoint()).isEqualTo(SPEC_CODEPOINT_NOTEHEAD_HALF);
        assertThat(SMuFLGlyph.NOTEHEAD_BLACK.codepoint()).isEqualTo(SPEC_CODEPOINT_NOTEHEAD_BLACK);

        // Accidentals
        assertThat(SMuFLGlyph.ACCIDENTAL_FLAT.codepoint()).isEqualTo(SPEC_CODEPOINT_ACCIDENTAL_FLAT);
        assertThat(SMuFLGlyph.ACCIDENTAL_NATURAL.codepoint()).isEqualTo(SPEC_CODEPOINT_ACCIDENTAL_NATURAL);
        assertThat(SMuFLGlyph.ACCIDENTAL_SHARP.codepoint()).isEqualTo(SPEC_CODEPOINT_ACCIDENTAL_SHARP);

        // Holds and pauses
        assertThat(SMuFLGlyph.FERMATA_ABOVE.codepoint()).isEqualTo(SPEC_CODEPOINT_FERMATA_ABOVE);
        assertThat(SMuFLGlyph.FERMATA_BELOW.codepoint()).isEqualTo(SPEC_CODEPOINT_FERMATA_BELOW);
    }

    // -------------------------------------------------------------------------
    // Row 22 -- asString() returns single-character string of the codepoint
    // -------------------------------------------------------------------------

    @Test
    void testAsStringIsSingleCharOfCodepoint() {
        // Verified across different glyph ranges to guard against a bug that
        // produces a multi-char string (e.g. surrogate pair) or the wrong char.
        for (var glyph : new SMuFLGlyph[]{
            SMuFLGlyph.G_CLEF,
            SMuFLGlyph.NOTEHEAD_BLACK,
            SMuFLGlyph.ACCIDENTAL_SHARP,
            SMuFLGlyph.FERMATA_ABOVE,
            SMuFLGlyph.REST_QUARTER,
            SMuFLGlyph.DYNAMIC_FORTE
        }) {
            var str = glyph.asString();
            assertThat(str)
                .as("asString() for %s must be a single character", glyph.name())
                .hasSize(1);
            assertThat(str.charAt(0))
                .as("asString() char for %s must equal codepoint()", glyph.name())
                .isEqualTo(glyph.codepoint());
        }
    }
}
