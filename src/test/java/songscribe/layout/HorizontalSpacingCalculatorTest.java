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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.engraving.SMuFLConstants;

/**
 * Tests for the staff-header geometry {@link HorizontalSpacingCalculator} exposes. Column-to-column
 * spacing lives in {@link HorizontalSpacingCalculatorSpringTest}, which drives the spring engine
 * every rendered position now goes through.
 */
class HorizontalSpacingCalculatorTest extends UnitTest {

    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);
    private static final Key THREE_SHARPS = new Key(KeyType.SHARPS, 3);
    private static final Key SEVEN_SHARPS = new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT);
    private static final Key SEVEN_FLATS = new Key(KeyType.FLATS, Key.MAX_ACCIDENTAL_COUNT);

    /** The clef's right edge — where the header ends when there is no key signature. */
    private static final double CLEF_RIGHT_EDGE_SS =
        LayoutEngine.CLEF_X_POSITION_SS + SMuFLConstants.G_CLEF_WIDTH_SS;

    // ==========================================================================
    // calculateKeySignatureXSs
    // ==========================================================================

    @Test
    void testKeySignatureStartsAtLilyPondGapPastTheClef() {
        assertThat(HorizontalSpacingCalculator.calculateKeySignatureXSs())
            .isEqualTo(CLEF_RIGHT_EDGE_SS + StaffHeaderMetrics.CLEF_GAP_SS);
    }

    // ==========================================================================
    // calculateHeaderRightEdgeSs
    // ==========================================================================

    @Test
    void testHeaderRightEdgeWithoutKeySignatureIsTheClefRightEdge() {
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(C_MAJOR))
            .isEqualTo(CLEF_RIGHT_EDGE_SS);
    }

    @Test
    void testHeaderRightEdgeWithThreeSharps() {
        var expected = HorizontalSpacingCalculator.calculateKeySignatureXSs()
            + KeySignature.widthSs(THREE_SHARPS);
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(THREE_SHARPS))
            .isEqualTo(expected);
    }

    @Test
    void testHeaderRightEdgeWithSevenFlats() {
        var expected = HorizontalSpacingCalculator.calculateKeySignatureXSs()
            + KeySignature.widthSs(SEVEN_FLATS);
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(SEVEN_FLATS))
            .isEqualTo(expected);
    }

    @Test
    void testFlatsMakeANarrowerHeaderThanSharps() {
        // The flat glyph is narrower than the sharp, and LilyPond spaces each accidental
        // by its own glyph width rather than a shared column width.
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(SEVEN_FLATS))
            .isLessThan(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(SEVEN_SHARPS));
    }

    // ==========================================================================
    // calculateFirstElementXSs — LilyPond measures it from a different edge
    // depending on whether a key signature intervenes
    // ==========================================================================

    @Test
    void testFirstNoteWithoutKeySignatureSpansFromTheClefLeftEdge() {
        // minimum-fixed-space is a floor on the whole span, and the clef does not fill it,
        // so the clef's own width drops out of the answer entirely.
        assertThat(HorizontalSpacingCalculator.calculateFirstElementXSs(C_MAJOR))
            .isEqualTo(LayoutEngine.CLEF_X_POSITION_SS + StaffHeaderMetrics.CLEF_FIRST_NOTE_SPAN_SS);
    }

    @Test
    void testFirstNoteWithoutKeySignatureStillClearsAClefWiderThanTheSpan() {
        assertThat(StaffHeaderMetrics.CLEF_FIRST_NOTE_SPAN_SS)
            .describedAs("the max() below is only exercised when the clef is the narrower of the two")
            .isGreaterThan(SMuFLConstants.G_CLEF_WIDTH_SS);
        assertThat(HorizontalSpacingCalculator.calculateFirstElementXSs(C_MAJOR))
            .isGreaterThanOrEqualTo(CLEF_RIGHT_EDGE_SS);
    }

    @Test
    void testFirstNoteWithKeySignatureSitsPastItsRightEdge() {
        var expected = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(THREE_SHARPS)
            + StaffHeaderMetrics.KEY_SIGNATURE_FIRST_NOTE_GAP_SS;
        assertThat(HorizontalSpacingCalculator.calculateFirstElementXSs(THREE_SHARPS))
            .isEqualTo(expected);
    }

    @Test
    void testFirstNoteMovesRightAsTheKeySignatureGrows() {
        assertThat(HorizontalSpacingCalculator.calculateFirstElementXSs(THREE_SHARPS))
            .isLessThan(HorizontalSpacingCalculator.calculateFirstElementXSs(SEVEN_SHARPS));
    }
}
