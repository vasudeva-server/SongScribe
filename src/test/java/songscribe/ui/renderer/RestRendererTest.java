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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.minimRest;
import static songscribe.dom.StaffElementFactory.semibreveRest;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.engraving.Staff;
import songscribe.smufl.SMuFLGlyph;

class RestRendererTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    // A representative middle-line Y used for calculateRestYSs tests
    private static final double MIDDLE_Y_SS = 10.0;

    // ==========================================================================
    // getRestGlyph (row 14)
    // ==========================================================================

    @Test
    void testGetRestGlyphReturnsSemibreveRestGlyph() {
        assertThat(RestRenderer.getRestGlyph(ElementType.SEMIBREVE_REST))
            .isEqualTo(SMuFLGlyph.REST_WHOLE);
    }

    @Test
    void testGetRestGlyphReturnsMinimRestGlyph() {
        assertThat(RestRenderer.getRestGlyph(ElementType.MINIM_REST))
            .isEqualTo(SMuFLGlyph.REST_HALF);
    }

    @Test
    void testGetRestGlyphReturnsCrotchetRestGlyph() {
        assertThat(RestRenderer.getRestGlyph(ElementType.CROTCHET_REST))
            .isEqualTo(SMuFLGlyph.REST_QUARTER);
    }

    @Test
    void testGetRestGlyphReturnsQuaverRestGlyph() {
        assertThat(RestRenderer.getRestGlyph(ElementType.QUAVER_REST))
            .isEqualTo(SMuFLGlyph.REST_8TH);
    }

    @Test
    void testGetRestGlyphReturnsSemiquaverRestGlyph() {
        assertThat(RestRenderer.getRestGlyph(ElementType.SEMIQUAVER_REST))
            .isEqualTo(SMuFLGlyph.REST_16TH);
    }

    @Test
    void testGetRestGlyphReturnsDemiSemiquaverRestGlyph() {
        assertThat(RestRenderer.getRestGlyph(ElementType.DEMI_SEMIQUAVER_REST))
            .isEqualTo(SMuFLGlyph.REST_32ND);
    }

    @Test
    void testGetRestGlyphReturnsNullForNonRestType() {
        assertThat(RestRenderer.getRestGlyph(ElementType.CROTCHET)).isNull();
        assertThat(RestRenderer.getRestGlyph(ElementType.SINGLE_BARLINE)).isNull();
    }

    // ==========================================================================
    // calculateRestYSs (row 15)
    // ==========================================================================

    @Test
    void testCalculateRestYSsForSemibreveRestUsesFixedOffset() {
        var note = semibreveRest();
        var expected = MIDDLE_Y_SS + Staff.spToSs(RestRenderer.SEMIBREVE_REST_Y_OFFSET);

        assertThat(RestRenderer.getInstance().calculateRestYSs(note, MIDDLE_Y_SS))
            .isCloseTo(expected, within(TOLERANCE));
    }

    @Test
    void testCalculateRestYSsForMinimRestUsesFixedOffset() {
        var note = minimRest();
        var expected = MIDDLE_Y_SS + Staff.spToSs(RestRenderer.MINIM_REST_Y_OFFSET);

        assertThat(RestRenderer.getInstance().calculateRestYSs(note, MIDDLE_Y_SS))
            .isCloseTo(expected, within(TOLERANCE));
    }

    @Test
    void testCalculateRestYSsForOtherRestUsesTypeDefaultStaffPosition() {
        // StructuralElement.getStaffPosition() returns the type's default staff position,
        // not a settable value — so the branch uses ElementType.getDefaultStaffPosition().
        var note = crotchetRest();
        var expected = MIDDLE_Y_SS + Staff.spToSs(note.getType().getDefaultStaffPosition());

        assertThat(RestRenderer.getInstance().calculateRestYSs(note, MIDDLE_Y_SS))
            .isCloseTo(expected, within(TOLERANCE));
    }
}
