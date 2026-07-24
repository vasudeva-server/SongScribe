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
import songscribe.smufl.SMuFLMetadata;

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
    // Beam geometry follows LilyPond, not Bravura's engravingDefaults
    // -----------------------------------------------------------------------

    @Nested
    class BeamGeometry {

        @Test
        void testBeamGeometryIgnoresFontEngravingDefaults() {
            // The point of these constants: beams are drawn by SongScribe rather
            // than taken from the font, so they follow LilyPond regardless of what
            // the loaded font recommends. Compared against the live font values so
            // no literal is duplicated here.
            var fontDefaults = SMuFLMetadata.getEngravingDefaults();
            var gapSs = LineThickness.BEAM_TRANSLATION_SS - LineThickness.BEAM_THICKNESS_SS;

            assertAll(
                () -> assertThat(LineThickness.BEAM_THICKNESS_SS)
                        .isNotEqualTo(fontDefaults.beamThickness()),
                () -> assertThat(gapSs).isGreaterThan(fontDefaults.beamSpacing()));
        }

        @Test
        void testStackedBeamsCannotOverlap() {
            // Translation is center-to-center, so anything at or below the beam
            // thickness would draw stacked beams touching or merged into one.
            assertThat(LineThickness.BEAM_TRANSLATION_SS)
                    .isGreaterThan(LineThickness.BEAM_THICKNESS_SS);
        }

        @Test
        void testBlotDiameterOnlyRoundsCorners() {
            // A blot at or above the beam thickness would round the beam into a lozenge.
            assertThat(LineThickness.BEAM_BLOT_DIAMETER_SS)
                    .isGreaterThan(0.0)
                    .isLessThan(LineThickness.BEAM_THICKNESS_SS);
        }

        // The values above are all relational, so they pin the constants' intent but
        // not their magnitude: BEAM_THICKNESS_SS could drift to 0.4 and still differ
        // from Bravura's recommendation, and BEAM_TRANSLATION_SS could lose its
        // halving and still exceed the beam thickness. These constants are a port —
        // LilyPond's own numbers are the specification, so they are restated here as
        // a drift guard, which is the one case where duplicating a value earns its
        // keep.

        /** LilyPond {@code beam-thickness}, scm/define-grobs.scm. */
        private static final double LILYPOND_BEAM_THICKNESS_SS = 0.48;

        /** LilyPond {@code blot-diameter}, 0.4 pt at scm/paper.scm's 5 pt staff space. */
        private static final double LILYPOND_BLOT_DIAMETER_SS = 0.08;

        /** A staff space, in staff spaces — the unit LilyPond's formula works in. */
        private static final double STAFF_SPACE_SS = 1.0;

        @Test
        void testBeamConstantsMatchTheirLilyPondSources() {
            assertAll(
                () -> assertThat(LineThickness.BEAM_THICKNESS_SS)
                        .as("LilyPond beam-thickness")
                        .isEqualTo(LILYPOND_BEAM_THICKNESS_SS),
                () -> assertThat(LineThickness.BEAM_BLOT_DIAMETER_SS)
                        .as("LilyPond blot-diameter at a 5 pt staff space")
                        .isEqualTo(LILYPOND_BLOT_DIAMETER_SS));
        }

        @Test
        void testBeamTranslationMatchesLilyPondsFewerThanFourBeamsFormula() {
            // Beam::get_beam_translation, beam.cc: (2 * staffSpace + staffLine -
            // beamThickness) / 2. Recomputed from the other constants so a dropped
            // term or a lost halving in LineThickness fails here.
            var expectedTranslationSs =
                (2 * STAFF_SPACE_SS + LineThickness.STAFF_LINE_SS - LineThickness.BEAM_THICKNESS_SS) / 2;

            assertThat(LineThickness.BEAM_TRANSLATION_SS).isEqualTo(expectedTranslationSs);
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
