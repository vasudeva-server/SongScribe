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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link StructuralElement} — getStaffPosition, getDotCount, and clone.
 */
class StructuralElementTest extends UnitTest {

    // A dot count value distinguishable from 0 and 1 defaults
    private static final int TWO_DOTS = 2;

    // A staff position that differs from every ElementType's default, used to
    // confirm that StructuralElement.getStaffPosition() ignores stored pitch.
    private static final int NON_DEFAULT_STAFF_POSITION = 5;

    // -----------------------------------------------------------------------
    // Row 50: getStaffPosition() always returns the type's default, ignoring
    //         any stored pitch value
    // -----------------------------------------------------------------------

    @Test
    void testGetStaffPositionIgnoresStoredPitchAndReturnsTypeDefault() {
        // CROTCHET_REST default staff position is 0 (the rest sits on the middle line).
        // Setting a different stored position via the superclass setter must have no
        // effect — StructuralElement overrides getStaffPosition() to always defer to
        // the type default.
        var element = new StructuralElement(ElementType.CROTCHET_REST);
        var typeDefault = ElementType.CROTCHET_REST.getDefaultStaffPosition();

        // Directly set a position that differs from the type default to prove it's ignored
        element.setStaffPosition(NON_DEFAULT_STAFF_POSITION);

        assertThat(element.getStaffPosition()).isEqualTo(typeDefault);
        assertThat(element.getStaffPosition()).isNotEqualTo(NON_DEFAULT_STAFF_POSITION);
    }

    // -----------------------------------------------------------------------
    // Row 51: getDotCount() — rests delegate to super (preserving dots);
    //         non-rests always return 0
    // -----------------------------------------------------------------------

    // A rest preserves the dot count set via setDotCount
    @Test
    void testGetDotCountRestPreservesSetDotCount() {
        var rest = new StructuralElement(ElementType.CROTCHET_REST);
        rest.setDotCount(TWO_DOTS);

        assertThat(rest.getDotCount()).isEqualTo(TWO_DOTS);
    }

    // A non-rest (barline) always returns 0, even after setDotCount
    @Test
    void testGetDotCountNonRestAlwaysReturnsZero() {
        var barline = new StructuralElement(ElementType.SINGLE_BARLINE);
        barline.setDotCount(TWO_DOTS);

        assertThat(barline.getDotCount()).isZero();
    }

    // -----------------------------------------------------------------------
    // Row 53: clone() — returns a StructuralElement with matching type and
    //         dot count
    // -----------------------------------------------------------------------

    @Test
    void testCloneReturnsStructuralElementWithMatchingTypeAndDotCount() {
        var original = new StructuralElement(ElementType.CROTCHET_REST);
        original.setDotCount(TWO_DOTS);

        var clone = original.clone();

        assertThat(clone).isInstanceOf(StructuralElement.class);
        assertThat(clone.getType()).isEqualTo(ElementType.CROTCHET_REST);
        // Rests propagate dots; the clone must carry the same dot count
        assertThat(clone.getDotCount()).isEqualTo(TWO_DOTS);
    }
}
