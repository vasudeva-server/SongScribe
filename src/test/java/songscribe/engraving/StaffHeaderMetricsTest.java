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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Pins the LilyPond-derived staff header metrics.
 */
class StaffHeaderMetricsTest extends UnitTest {

    /** Staff position of the top-line F, where the first sharp of a key signature sits. */
    private static final int TOP_LINE_SP = -4;

    /** Staff positions are numbered downward, so a positive step moves down the staff. */
    private static final int THREE_POSITIONS = 3;
    private static final int FOUR_POSITIONS = 4;

    private static final double OVERLAP_KERNING_SS = 0.3;
    private static final double TOUCHING_KERNING_SS = 0.15;

    // The LilyPond space-alist distances these constants were transcribed from, read out of
    // scm/define-grobs.scm. Written as literals on purpose: the transcription is the fact
    // under test, so re-deriving them from the constants would prove nothing.
    private static final double LILYPOND_CLEF_TO_KEY_SIGNATURE_SS = 0.82;
    private static final double LILYPOND_CANCELLATION_TO_KEY_SS = 0.5;
    private static final double LILYPOND_KEY_SIGNATURE_TO_FIRST_NOTE_SS = 2.5;
    private static final double LILYPOND_CLEF_TO_FIRST_NOTE_SS = 5.0;

    // ==========================================================================
    // Ported LilyPond distances
    // ==========================================================================

    @Test
    void testClefGapMatchesItsLilyPondSpaceAlistEntry() {
        // Clef space-alist, (key-signature . (extra-space . 0.82)).
        assertThat(StaffHeaderMetrics.CLEF_GAP_SS).isEqualTo(LILYPOND_CLEF_TO_KEY_SIGNATURE_SS);
    }

    @Test
    void testCancellationToKeyGapMatchesItsLilyPondSpaceAlistEntry() {
        // The cancellation is the left item in LilyPond's break-align order, so the gap comes
        // from KeyCancellation's space-alist, (key-signature . (extra-space . 0.5)).
        assertThat(StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS)
            .isEqualTo(LILYPOND_CANCELLATION_TO_KEY_SS);
    }

    @Test
    void testKeySignatureToFirstNoteGapMatchesItsLilyPondSpaceAlistEntry() {
        // KeySignature space-alist, (first-note . (shrink-space . 2.5)).
        assertThat(StaffHeaderMetrics.KEY_SIGNATURE_FIRST_NOTE_GAP_SS)
            .isEqualTo(LILYPOND_KEY_SIGNATURE_TO_FIRST_NOTE_SS);
    }

    @Test
    void testClefToFirstNoteSpanMatchesItsLilyPondSpaceAlistEntry() {
        // Clef space-alist, (first-note . (minimum-fixed-space . 5.0)).
        assertThat(StaffHeaderMetrics.CLEF_FIRST_NOTE_SPAN_SS)
            .isEqualTo(LILYPOND_CLEF_TO_FIRST_NOTE_SS);
    }

    // ==========================================================================
    // accidentalInkBboxSs
    // ==========================================================================

    @Test
    void testAccidentalAdvanceIsTheGlyphBoundingBoxWidth() {
        // LilyPond leaves the KeySignature grob's padding at zero, so an accidental
        // advances by nothing more than its own glyph width.
        for (var glyph : new SMuFLGlyph[]{
            SMuFLGlyph.ACCIDENTAL_SHARP,
            SMuFLGlyph.ACCIDENTAL_FLAT,
            SMuFLGlyph.ACCIDENTAL_NATURAL}) {
            assertThat(StaffHeaderMetrics.accidentalInkBboxSs(glyph))
                .describedAs("advance for %s", glyph)
                .isEqualTo(SMuFLMetadata.requireBBox(glyph).width());
        }
    }

    @Test
    void testFlatAdvancesLessThanSharp() {
        assertThat(StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_FLAT))
            .isLessThan(StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_SHARP));
    }

    // ==========================================================================
    // naturalKerningSs
    // ==========================================================================

    @Test
    void testNaturalsAtTheSamePositionOverlap() {
        assertThat(StaffHeaderMetrics.naturalKerningSs(0, 0)).isEqualTo(OVERLAP_KERNING_SS);
    }

    @Test
    void testNaturalWithNeighbourThreePositionsLowerOverlaps() {
        // The sharp cancellation order steps three positions down between its first two
        // naturals (top-line F to third-space C), whose extents overlap.
        assertThat(StaffHeaderMetrics.naturalKerningSs(TOP_LINE_SP, TOP_LINE_SP + THREE_POSITIONS))
            .isEqualTo(OVERLAP_KERNING_SS);
    }

    @Test
    void testNaturalWithNeighbourThreePositionsHigherJustTouches() {
        // The flat cancellation order steps three positions up between its first two
        // naturals (middle-line B to top-line F), where the extents meet at a corner.
        assertThat(StaffHeaderMetrics.naturalKerningSs(0, -THREE_POSITIONS))
            .isEqualTo(TOUCHING_KERNING_SS);
    }

    @Test
    void testNaturalWithNeighbourFourPositionsHigherNeedsNoKerning() {
        // Four positions up, the natural's right descender clears the next natural's
        // left riser entirely.
        assertThat(StaffHeaderMetrics.naturalKerningSs(0, -FOUR_POSITIONS)).isEqualTo(0);
    }
}
