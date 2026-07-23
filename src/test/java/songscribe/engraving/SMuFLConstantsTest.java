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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class SMuFLConstantsTest extends UnitTest {

    // -----------------------------------------------------------------------
    // ROW 37 — G_CLEF_WIDTH_SS reflects the concrete Bravura advance width
    // -----------------------------------------------------------------------

    // Bravura metadata: glyphAdvanceWidths.gClef = 2.684 (staff spaces)
    private static final double BRAVURA_G_CLEF_ADVANCE_WIDTH_SS = 2.684;
    // Tolerance of one unit in the last decimal place recorded in the JSON
    private static final double ADVANCE_WIDTH_TOLERANCE_SS = 0.001;

    @Test
    void testGClefWidthMatchesBravuraAdvanceWidth() {
        assertThat(SMuFLConstants.G_CLEF_WIDTH_SS)
                .isCloseTo(BRAVURA_G_CLEF_ADVANCE_WIDTH_SS, within(ADVANCE_WIDTH_TOLERANCE_SS));
    }

    // -----------------------------------------------------------------------
    // ROW 38 — SS-suffixed engraving constants are positive and within a
    //           plausible staff-space range
    // -----------------------------------------------------------------------

    // Lower bound: any engraving default thinner than this is implausibly small
    private static final double MIN_ENGRAVING_SS = 0.05;
    // Upper bound: no single engraving default should exceed a full staff space
    private static final double MAX_ENGRAVING_SS = 1.0;

    // Beam thickness and spacing are no longer SMuFL-derived — they follow
    // LilyPond and live in LineThickness, covered by LineThicknessTest.

    @Test
    void testRepeatBarlineDotSeparationIsPlausible() {
        assertThat(SMuFLConstants.REPEAT_BARLINE_DOT_SEPARATION_SS)
                .isGreaterThanOrEqualTo(MIN_ENGRAVING_SS)
                .isLessThanOrEqualTo(MAX_ENGRAVING_SS);
    }

    @Test
    void testLedgerLineThicknessIsPlausible() {
        assertThat(SMuFLConstants.LEDGER_LINE_THICKNESS_SS)
                .isGreaterThanOrEqualTo(MIN_ENGRAVING_SS)
                .isLessThanOrEqualTo(MAX_ENGRAVING_SS);
    }

    /** LilyPond {@code length-fraction} default (dimensionless multiplier on notehead width). */
    private static final double LILYPOND_LENGTH_FRACTION_DEFAULT = 0.25;

    @Test
    void testLedgerLineLengthFractionIsLilyPondDefault() {
        assertThat(SMuFLConstants.LEDGER_LINE_LENGTH_FRACTION).isEqualTo(LILYPOND_LENGTH_FRACTION_DEFAULT);
    }

    @Test
    void testTieMidpointThicknessIsPlausible() {
        assertThat(SMuFLConstants.TIE_MIDPOINT_THICKNESS_SS)
                .isGreaterThanOrEqualTo(MIN_ENGRAVING_SS)
                .isLessThanOrEqualTo(MAX_ENGRAVING_SS);
    }

    // -----------------------------------------------------------------------
    // ROW 39 — NOTEHEAD_BLACK anchor coordinates reflect Y-down storage
    //           (fromSMuFL flips Y: stored_y = -smufl_y)
    //
    // Bravura raw values (Y-up SMuFL convention):
    //   noteheadBlack stemUpSE  = [1.18,  0.168]
    //   noteheadBlack stemDownNW = [0.0,  -0.168]
    //
    // After Anchor.fromSMuFL(x, -y) (Y-down storage):
    //   NOTEHEAD_BLACK_STEM_UP_SE   → x =  1.18,  y = -0.168
    //   NOTEHEAD_BLACK_STEM_DOWN_NW → x =  0.0,   y =  0.168
    // -----------------------------------------------------------------------

    // Bravura anchor values for noteheadBlack (staff spaces)
    private static final double BRAVURA_NOTEHEAD_BLACK_STEM_UP_SE_X   =  1.18;
    private static final double BRAVURA_NOTEHEAD_BLACK_STEM_UP_SE_Y   = -0.168;
    private static final double BRAVURA_NOTEHEAD_BLACK_STEM_DOWN_NW_X =  0.0;
    private static final double BRAVURA_NOTEHEAD_BLACK_STEM_DOWN_NW_Y =  0.168;

    // Tolerance of one unit in the last decimal place recorded in the JSON
    private static final double ANCHOR_TOLERANCE_SS = 0.001;

    @Test
    void testNoteheadBlackStemUpSeAnchorMatchesBravura() {
        var anchor = SMuFLConstants.NOTEHEAD_BLACK_STEM_UP_SE;
        assertThat(anchor.x())
                .as("stemUpSE x (right edge, positive in Y-down)")
                .isCloseTo(BRAVURA_NOTEHEAD_BLACK_STEM_UP_SE_X, within(ANCHOR_TOLERANCE_SS));
        assertThat(anchor.y())
                .as("stemUpSE y (above notehead center, negative in Y-down)")
                .isCloseTo(BRAVURA_NOTEHEAD_BLACK_STEM_UP_SE_Y, within(ANCHOR_TOLERANCE_SS));
    }

    @Test
    void testNoteheadBlackStemDownNwAnchorMatchesBravura() {
        var anchor = SMuFLConstants.NOTEHEAD_BLACK_STEM_DOWN_NW;
        assertThat(anchor.x())
                .as("stemDownNW x (left edge, zero in Bravura)")
                .isCloseTo(BRAVURA_NOTEHEAD_BLACK_STEM_DOWN_NW_X, within(ANCHOR_TOLERANCE_SS));
        assertThat(anchor.y())
                .as("stemDownNW y (below notehead center, positive in Y-down)")
                .isCloseTo(BRAVURA_NOTEHEAD_BLACK_STEM_DOWN_NW_Y, within(ANCHOR_TOLERANCE_SS));
    }
}
