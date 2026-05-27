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
import songscribe.smufl.Engraving;

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
            var lt = LineThickness.getInstance();
            double expectedStemSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * LineThickness.STEM_MULTIPLIER;
            assertThat(lt.stemSs()).isEqualTo(expectedStemSs);
        }

        @Test
        void testLedgerLineThicknessEqualsBaseTimesTwo() {
            var lt = LineThickness.getInstance();
            double expectedLedgerLineSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * 2;
            assertThat(lt.ledgerLineSs()).isEqualTo(expectedLedgerLineSs);
        }

        @Test
        void testHairpinThicknessEqualsBase() {
            var lt = LineThickness.getInstance();
            // HAIRPIN_MULTIPLIER = 1.0: hairpin is the same thickness as the base staff line
            assertThat(lt.hairpinSs()).isEqualTo(LineThickness.LILYPOND_BASE_THICKNESS_SS);
        }

        @Test
        void testVoltaBracketThicknessEqualsBaseTimesMultiplier() {
            var lt = LineThickness.getInstance();
            double expectedVoltaBracketSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * LineThickness.VOLTA_BRACKET_MULTIPLIER;
            assertThat(lt.voltaBracketSs()).isEqualTo(expectedVoltaBracketSs);
        }

        @Test
        void testTupletBracketThicknessEqualsBaseTimesMultiplier() {
            var lt = LineThickness.getInstance();
            double expectedTupletBracketSs = LineThickness.LILYPOND_BASE_THICKNESS_SS * LineThickness.TUPLET_BRACKET_MULTIPLIER;
            assertThat(lt.tupletBracketSs()).isEqualTo(expectedTupletBracketSs);
        }
    }

    // -----------------------------------------------------------------------
    // Row 20: repeatRightThinBarlineCenterXSs / repeatRightAfterThickXSs arithmetic
    // -----------------------------------------------------------------------

    @Test
    void testRepeatRightThinBarlineCenterXSsMatchesFormula() {
        var lt = LineThickness.getInstance();
        // formula: dotsAdvanceWidth + barlineSeparation + thinBarline/2
        double expected = Engraving.REPEAT_DOTS_ADVANCE_WIDTH_SS
                + lt.barlineSeparationSs()
                + lt.thinBarlineSs() / 2;
        assertThat(lt.repeatRightThinBarlineCenterXSs()).isEqualTo(expected);
    }

    @Test
    void testRepeatRightAfterThickXSsMatchesFormula() {
        var lt = LineThickness.getInstance();
        // formula: dotsAdvanceWidth + sep + thin + sep + thick
        var sep = lt.barlineSeparationSs();
        double expected = Engraving.REPEAT_DOTS_ADVANCE_WIDTH_SS
                + sep + lt.thinBarlineSs() + sep + lt.thickBarlineSs();
        assertAll(
            () -> assertThat(lt.repeatRightAfterThickXSs()).isEqualTo(expected),
            // after-thick must be greater than thin-center (thick barline is to the right)
            () -> assertThat(lt.repeatRightAfterThickXSs())
                      .isGreaterThan(lt.repeatRightThinBarlineCenterXSs())
        );
    }
}
