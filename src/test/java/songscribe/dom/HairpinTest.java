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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.smufl.Engraving;

/**
 * Tests for {@link Hairpin} — getContentHeightSs and getSpanWidthSs.
 *
 * <p>Hairpin is abstract (sealed); tests use {@link Crescendo} as the concrete subclass,
 * since the tested methods are fully implemented in Hairpin.
 */
class HairpinTest extends UnitTest {

    // Floating-point tolerance for comparisons where exact arithmetic is expected.
    private static final double DOUBLE_EPSILON = 1e-9;

    private static Crescendo createHairpin(StaffElement anchor, StaffElement end) {
        return new Crescendo(anchor, end);
    }

    // -------------------------------------------------------------------------
    // Row 12 — getContentHeightSs() returns HAIRPIN_OPENING_HEIGHT_SS
    // -------------------------------------------------------------------------

    @Test
    void testGetContentHeightSsEqualsHairpinOpeningHeightSs() {
        var anchor = new StaffElement(ElementType.CROTCHET);
        var end = new StaffElement(ElementType.CROTCHET);
        var hairpin = createHairpin(anchor, end);

        assertThat(hairpin.getContentHeightSs())
            .isCloseTo(Hairpin.HAIRPIN_OPENING_HEIGHT_SS, within(DOUBLE_EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 13 — getSpanWidthSs(): max(HAIRPIN_OPENING_HEIGHT_SS, endX-anchorX+NOTE_HEAD_WIDTH)
    //          Both branches: clamp branch (span < minimum) and geometry branch (span >= minimum)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetSpanWidthSs {

        /**
         * When endX - anchorX + NOTE_HEAD_WIDTH_SS is smaller than HAIRPIN_OPENING_HEIGHT_SS,
         * the result must be clamped to HAIRPIN_OPENING_HEIGHT_SS.
         */
        @Test
        void testGetSpanWidthSsClampedToMinimumWhenGeometryIsTooNarrow() {
            // anchorX == endX => geometry = NOTE_HEAD_WIDTH_SS, which is less than
            // HAIRPIN_OPENING_HEIGHT_SS (1.25), so the clamp applies.
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var hairpin = createHairpin(anchor, end);

            double anchorXSs = 0.0;
            double endXSs = 0.0;
            double geometryWidthSs = endXSs - anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS;

            assertThat(geometryWidthSs)
                .as("precondition: geometry width must be less than the minimum for clamp branch")
                .isLessThan(Hairpin.HAIRPIN_OPENING_HEIGHT_SS);

            assertThat(hairpin.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(Hairpin.HAIRPIN_OPENING_HEIGHT_SS, within(DOUBLE_EPSILON));
        }

        /**
         * When endX - anchorX + NOTE_HEAD_WIDTH_SS exceeds HAIRPIN_OPENING_HEIGHT_SS,
         * the result must equal the geometry, not the minimum.
         */
        @Test
        void testGetSpanWidthSsReturnsGeometryWhenLargerThanMinimum() {
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var hairpin = createHairpin(anchor, end);

            // Use a span large enough that geometry dominates.
            double anchorXSs = 0.0;
            double endXSs = Hairpin.HAIRPIN_OPENING_HEIGHT_SS + 2.0;
            double expectedWidthSs = endXSs - anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS;

            assertThat(expectedWidthSs)
                .as("precondition: geometry width must exceed the minimum for geometry branch")
                .isGreaterThan(Hairpin.HAIRPIN_OPENING_HEIGHT_SS);

            assertThat(hairpin.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(expectedWidthSs, within(DOUBLE_EPSILON));
        }
    }
}
