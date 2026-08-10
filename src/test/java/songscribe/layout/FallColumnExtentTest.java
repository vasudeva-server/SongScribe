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
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Verifies that a note with a fall reserves trailing room in its own column. Because
 * {@link ElementColumnBuilder#calculateRightExtentSs} depends only on the element itself, the
 * reservation holds with no following note — the last-element / before-a-barline case that the
 * between-notes glissando reservation in {@code HorizontalSpacingCalculator} cannot cover.
 */
class FallColumnExtentTest extends UnitTest {

    private static final double TOLERANCE_SS = 0.001;

    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    @Test
    void testFallWidensRightExtentByGapPlusGlyphAdvance() {
        var plain = crotchet();
        var withFall = crotchet();
        withFall.setFall();

        var plainRightSs = ElementColumnBuilder.calculateRightExtentSs(plain, false, StaffElement.Direction.UP);
        var fallRightSs = ElementColumnBuilder.calculateRightExtentSs(withFall, false, StaffElement.Direction.UP);

        var fallAdvanceSs = SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
        var expectedReservationSs = NoteGeometry.FALL_GAP_SS + fallAdvanceSs;

        assertThat(fallRightSs - plainRightSs)
            .as("fall reserves the drawn gap plus the glyph advance width")
            .isCloseTo(expectedReservationSs, within(TOLERANCE_SS));
    }

    @Test
    void testFallReservesRoomEvenWithNoFollowingElement() {
        // The extent is computed from the element alone, so the fall's room is reserved regardless
        // of whether a next note, a barline, or the end of the line follows it.
        var withFall = crotchet();
        withFall.setFall();
        var plain = crotchet();

        var fallRightSs = ElementColumnBuilder.calculateRightExtentSs(withFall, false, StaffElement.Direction.UP);
        var plainRightSs = ElementColumnBuilder.calculateRightExtentSs(plain, false, StaffElement.Direction.UP);

        assertThat(fallRightSs)
            .as("a fall note is wider than the same note without one")
            .isGreaterThan(plainRightSs);
    }
}
