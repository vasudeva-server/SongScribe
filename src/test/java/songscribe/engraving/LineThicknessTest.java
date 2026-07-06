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

package songscribe.engraving;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LineThicknessTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 18: each field = LILYPOND_BASE_THICKNESS_SS × multiplier
    // -----------------------------------------------------------------------

    @Nested
    class ThicknessMultipliers {

        @Test
        void testStemThicknessEqualsBaseTimesMultiplier() {
            double expectedStemSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * LineThickness.STEM_MULTIPLIER;
            assertThat(LineThickness.STEM_SS).isEqualTo(expectedStemSs);
        }

        @Test
        void testLedgerLineThicknessEqualsBaseTimesTwo() {
            double expectedLedgerLineSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * 2;
            assertThat(LineThickness.LEDGER_LINE_SS).isEqualTo(expectedLedgerLineSs);
        }

        @Test
        void testHairpinThicknessEqualsBase() {
            // HAIRPIN_MULTIPLIER = 1.0: hairpin is the same thickness as the base staff line
            assertThat(LineThickness.HAIRPIN_SS).isEqualTo(LineThickness.LILYPOND_BASE_THICKNESS_SS);
        }

        @Test
        void testVoltaBracketThicknessEqualsBaseTimesMultiplier() {
            double expectedVoltaBracketSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * LineThickness.VOLTA_BRACKET_MULTIPLIER;
            assertThat(LineThickness.VOLTA_BRACKET_SS).isEqualTo(expectedVoltaBracketSs);
        }

        @Test
        void testTupletBracketThicknessEqualsBaseTimesMultiplier() {
            double expectedTupletBracketSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * LineThickness.TUPLET_BRACKET_MULTIPLIER;
            assertThat(LineThickness.TUPLET_BRACKET_SS).isEqualTo(expectedTupletBracketSs);
        }
    }

    // -----------------------------------------------------------------------
    // Row 20: REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS / REPEAT_RIGHT_AFTER_THICK_X_SS arithmetic
    // -----------------------------------------------------------------------

    @Test
    void testRepeatRightThinBarlineCenterXSsMatchesFormula() {
        // formula: dotsAdvanceWidth + barlineSeparation + thinBarline/2
        double expected = SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS
                + LineThickness.BARLINE_SEPARATION_SS
                + LineThickness.THIN_BARLINE_SS / 2;
        assertThat(LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS).isEqualTo(expected);
    }

    @Test
    void testRepeatRightAfterThickXSsMatchesFormula() {
        // formula: dotsAdvanceWidth + sep + thin + sep + thick
        var sep = LineThickness.BARLINE_SEPARATION_SS;
        double expected = SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS
                + sep + LineThickness.THIN_BARLINE_SS + sep + LineThickness.THICK_BARLINE_SS;
        assertAll(
            () -> assertThat(LineThickness.REPEAT_RIGHT_AFTER_THICK_X_SS).isEqualTo(expected),
            // after-thick must be greater than thin-center (thick barline is to the right)
            () -> assertThat(LineThickness.REPEAT_RIGHT_AFTER_THICK_X_SS)
                      .isGreaterThan(LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS)
        );
    }
}
