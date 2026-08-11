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
 * Unit tests for base-class geometry and child-management behaviour defined in
 * {@link LineElement}.  Exercises rows 25–27, 29–32 of the 1c test-matrix
 * (all targeting the concrete {@link StaffElement} subclass as the vehicle).
 */
class LineElementTest extends UnitTest {

    // ------------------------------------------------------------------
    // Position and margin values used across multiple tests
    // ------------------------------------------------------------------

    /** Top margin used in multi-side margin tests. */
    private static final double MARGIN_TOP_SS = 2.0;

    /** Right margin used in multi-side margin tests. */
    private static final double MARGIN_RIGHT_SS = 4.0;

    /** Bottom margin used in multi-side margin tests. */
    private static final double MARGIN_BOTTOM_SS = 6.0;

    /** Left margin used in multi-side margin tests. */
    private static final double MARGIN_LEFT_SS = 8.0;

    /** Uniform margin used in the single-argument setMarginSs test. */
    private static final double UNIFORM_MARGIN_SS = 3.5;

    /** Larger margin for CSS max-collapse "a > b" cases. */
    private static final double LARGER_MARGIN_SS = 5.0;

    /** Smaller margin for CSS max-collapse "a < b" cases. */
    private static final double SMALLER_MARGIN_SS = 2.0;

    // ------------------------------------------------------------------
    // Row 25: getMarginBounds
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // Row 26: collapsedVerticalMarginWith — CSS max-collapse
    // ------------------------------------------------------------------

    /** Row 26a — bottom > neighbour top: this element's bottom margin wins. */
    @Test
    void testCollapsedVerticalMarginWithBottomGreaterThanNeighbourTop() {
        var above = StaffElementFactory.crotchet();
        var below = StaffElementFactory.crotchet();
        above.setMarginSs(0, 0, LARGER_MARGIN_SS, 0);
        below.setMarginSs(SMALLER_MARGIN_SS, 0, 0, 0);

        assertThat(above.collapsedVerticalMarginWith(below)).isEqualTo(LARGER_MARGIN_SS);
    }

    /** Row 26b — bottom < neighbour top: the neighbour's top margin wins. */
    @Test
    void testCollapsedVerticalMarginWithNeighbourTopGreaterThanBottom() {
        var above = StaffElementFactory.crotchet();
        var below = StaffElementFactory.crotchet();
        above.setMarginSs(0, 0, SMALLER_MARGIN_SS, 0);
        below.setMarginSs(LARGER_MARGIN_SS, 0, 0, 0);

        assertThat(above.collapsedVerticalMarginWith(below)).isEqualTo(LARGER_MARGIN_SS);
    }

    /** Row 26c — bottom == neighbour top: either value is the result. */
    @Test
    void testCollapsedVerticalMarginWithEqualMargins() {
        var above = StaffElementFactory.crotchet();
        var below = StaffElementFactory.crotchet();
        above.setMarginSs(0, 0, LARGER_MARGIN_SS, 0);
        below.setMarginSs(LARGER_MARGIN_SS, 0, 0, 0);

        assertThat(above.collapsedVerticalMarginWith(below)).isEqualTo(LARGER_MARGIN_SS);
    }

    // ------------------------------------------------------------------
    // Row 27: collapsedHorizontalMarginWith — CSS max-collapse
    // ------------------------------------------------------------------

    /** Row 27a — right > neighbour left: this element's right margin wins. */
    @Test
    void testCollapsedHorizontalMarginWithRightGreaterThanNeighbourLeft() {
        var left = StaffElementFactory.crotchet();
        var right = StaffElementFactory.crotchet();
        left.setMarginSs(0, LARGER_MARGIN_SS, 0, 0);
        right.setMarginSs(0, 0, 0, SMALLER_MARGIN_SS);

        assertThat(left.collapsedHorizontalMarginWith(right)).isEqualTo(LARGER_MARGIN_SS);
    }

    /** Row 27b — right < neighbour left: the neighbour's left margin wins. */
    @Test
    void testCollapsedHorizontalMarginWithNeighbourLeftGreaterThanRight() {
        var left = StaffElementFactory.crotchet();
        var right = StaffElementFactory.crotchet();
        left.setMarginSs(0, SMALLER_MARGIN_SS, 0, 0);
        right.setMarginSs(0, 0, 0, LARGER_MARGIN_SS);

        assertThat(left.collapsedHorizontalMarginWith(right)).isEqualTo(LARGER_MARGIN_SS);
    }

    /** Row 27c — right == neighbour left: either value is the result. */
    @Test
    void testCollapsedHorizontalMarginWithEqualMargins() {
        var left = StaffElementFactory.crotchet();
        var right = StaffElementFactory.crotchet();
        left.setMarginSs(0, LARGER_MARGIN_SS, 0, 0);
        right.setMarginSs(0, 0, 0, LARGER_MARGIN_SS);

        assertThat(left.collapsedHorizontalMarginWith(right)).isEqualTo(LARGER_MARGIN_SS);
    }

    // ------------------------------------------------------------------
    // Row 29: removeChild — normal removal and ignore-non-child
    // ------------------------------------------------------------------

    /**
     * Row 29a — normal removal: removing a child clears its parentElement.
     */
    @Test
    void testRemoveChildClearsParentElement() {
        var song = new Song();
        var line = song.getLine(0);
        var parent = StaffElementFactory.crotchet();
        var child = StaffElementFactory.quaver();
        song.withoutMutationTracking(() -> line.addElement(parent));
        parent.addChild(child);

        assertThat(child.getParentElement()).isSameAs(parent);
        assertThat(child.getParentLine()).isSameAs(line);

        parent.removeChild(child);

        assertThat(child.getParentElement()).isNull();
        assertThat(child.getParentLine()).isNull();
        assertThat(parent.getChildCount()).isEqualTo(0);
    }

    /**
     * Row 29b — ignore non-child: removing an element that was never added must
     * not throw and must not alter an unrelated child's parentElement.
     */
    @Test
    void testRemoveChildIgnoresNonChild() {
        var parent = StaffElementFactory.crotchet();
        var realChild = StaffElementFactory.quaver();
        var nonChild = StaffElementFactory.crotchetRest();
        parent.addChild(realChild);

        // Must not throw
        parent.removeChild(nonChild);

        // The unrelated child remains intact
        assertThat(realChild.getParentElement()).isSameAs(parent);
        assertThat(parent.getChildCount()).isEqualTo(1);
        // The non-child is untouched
        assertThat(nonChild.getParentElement()).isNull();
    }

    // ------------------------------------------------------------------
    // Row 31: setMarginSs(double) — uniform margin
    // ------------------------------------------------------------------

    /**
     * Row 31 — setMarginSs(double) sets all four margins to the same value.
     */
    @Test
    void testSetMarginSsUniformSetsAllFourSides() {
        var element = StaffElementFactory.crotchet();
        element.setMarginSs(UNIFORM_MARGIN_SS);

        assertThat(element.getMarginTopSs()).isEqualTo(UNIFORM_MARGIN_SS);
        assertThat(element.getMarginRightSs()).isEqualTo(UNIFORM_MARGIN_SS);
        assertThat(element.getMarginBottomSs()).isEqualTo(UNIFORM_MARGIN_SS);
        assertThat(element.getMarginLeftSs()).isEqualTo(UNIFORM_MARGIN_SS);
    }

    // ------------------------------------------------------------------
    // Row 32: setMarginSs(top, right, bottom, left) — CSS shorthand order
    // ------------------------------------------------------------------

    /**
     * Row 32 — setMarginSs(top, right, bottom, left) places each value on the
     * correct side (CSS shorthand order: top, right, bottom, left).
     */
    @Test
    void testSetMarginSsShorthandOrderMapsToCorrectSides() {
        var element = StaffElementFactory.crotchet();
        element.setMarginSs(MARGIN_TOP_SS, MARGIN_RIGHT_SS, MARGIN_BOTTOM_SS, MARGIN_LEFT_SS);

        assertThat(element.getMarginTopSs()).isEqualTo(MARGIN_TOP_SS);
        assertThat(element.getMarginRightSs()).isEqualTo(MARGIN_RIGHT_SS);
        assertThat(element.getMarginBottomSs()).isEqualTo(MARGIN_BOTTOM_SS);
        assertThat(element.getMarginLeftSs()).isEqualTo(MARGIN_LEFT_SS);
    }
}
