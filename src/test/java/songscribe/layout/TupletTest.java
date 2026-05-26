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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tuplet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;

class TupletTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    // Minimum span width enforced by getSpanWidthSs (documented clamp value).
    private static final double MIN_SPAN_WIDTH_SS = 1.0;

    private static Tuplet createTuplet() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return new Tuplet(anchor, end, 3);
    }

    @Test
    void testContentHeightSsMatchesStylesheetConstant() {
        var tuplet = createTuplet();

        assertThat(tuplet.getContentHeightSs())
            .isEqualTo(Tuplet.TUPLET_BRACKET_HEIGHT_SS);
    }

    @Test
    void testContentHeightPxIsToPixelsOfSs() {
        var tuplet = createTuplet();
        assertThat(tuplet.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(Tuplet.TUPLET_BRACKET_HEIGHT_SS),
                within(EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 20 — getElementCount() returns the grade (triplet=3, quintuplet=5)
    // -------------------------------------------------------------------------

    // Grade value for a triplet.
    private static final int TRIPLET_GRADE = 3;

    // Grade value for a quintuplet.
    private static final int QUINTUPLET_GRADE = 5;

    @Test
    void testGetElementCountReturnsTripletGrade() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        var tuplet = new Tuplet(anchor, end, TRIPLET_GRADE);

        assertThat(tuplet.getElementCount()).isEqualTo(TRIPLET_GRADE);
    }

    @Test
    void testGetElementCountReturnsQuintupletGrade() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        var tuplet = new Tuplet(anchor, end, QUINTUPLET_GRADE);

        assertThat(tuplet.getElementCount()).isEqualTo(QUINTUPLET_GRADE);
    }

    // -------------------------------------------------------------------------
    // Row 21 — getSpanWidthSs(): max(1.0, endX−anchorX)
    //          Both branches: clamp (span < 1.0) and geometry (span >= 1.0)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetSpanWidthSs {

        /**
         * When endX - anchorX is less than 1.0, the result must be clamped to 1.0.
         */
        @Test
        void testGetSpanWidthSsClampedToMinimumWhenGeometryIsTooNarrow() {
            var tuplet = createTuplet();

            // anchorX == endX => geometry = 0, which is below the minimum of 1.0.
            double anchorXSs = 2.0;
            double endXSs = 2.0;

            assertThat(endXSs - anchorXSs)
                .as("precondition: geometry width must be less than 1.0 for clamp branch")
                .isLessThan(MIN_SPAN_WIDTH_SS);

            assertThat(tuplet.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(MIN_SPAN_WIDTH_SS, within(EPSILON));
        }

        /**
         * When endX - anchorX exceeds 1.0, the result must equal the geometry.
         */
        @Test
        void testGetSpanWidthSsReturnsGeometryWhenLargerThanMinimum() {
            var tuplet = createTuplet();

            double anchorXSs = 0.0;
            double endXSs = 4.0;
            double expectedWidthSs = endXSs - anchorXSs;

            assertThat(expectedWidthSs)
                .as("precondition: geometry width must exceed 1.0 for geometry branch")
                .isGreaterThan(MIN_SPAN_WIDTH_SS);

            assertThat(tuplet.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(expectedWidthSs, within(EPSILON));
        }
    }
}
