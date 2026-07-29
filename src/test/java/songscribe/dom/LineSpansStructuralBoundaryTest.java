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
 * Tests for {@link Line#spansStructuralBoundary(int, int)} — the predicate that
 * decides whether an inclusive element range crosses something a hairpin (or any
 * other span) may not cross: a repeat, or a barline other than a single one.
 *
 * <p>The range is inclusive at both ends, so the two cases that matter most are a
 * boundary sitting exactly at {@code begin} and exactly at {@code end}: an
 * off-by-one there would let a hairpin run across a final barline.
 */
class LineSpansStructuralBoundaryTest extends UnitTest {

    /** Every fixture below is a three-element line, so this is its last index. */
    private static final int LAST_IDX = 2;

    @Test
    void testNotesOnlyRangeIsNotStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a range of plain notes crosses nothing structural")
            .isFalse();
    }

    @Test
    void testSingleBarlineIsNotStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.SINGLE_BARLINE, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a single barline is the one barline a span may cross")
            .isFalse();
    }

    @Test
    void testDoubleBarlineIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.DOUBLE_BARLINE, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a double barline is a structural boundary")
            .isTrue();
    }

    @Test
    void testFinalDoubleBarlineIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a final double barline is a structural boundary")
            .isTrue();
    }

    @Test
    void testRepeatLeftIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.REPEAT_LEFT, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a left repeat is a structural boundary")
            .isTrue();
    }

    @Test
    void testRepeatRightIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.REPEAT_RIGHT, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a right repeat is a structural boundary")
            .isTrue();
    }

    @Test
    void testRepeatLeftRightIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.REPEAT_LEFT_RIGHT, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("a left-right repeat is a structural boundary")
            .isTrue();
    }

    @Test
    void testSingleElementRangeOnNoteIsNotStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(1, 1))
            .as("a degenerate range on a note crosses nothing")
            .isFalse();
    }

    @Test
    void testSingleElementRangeOnBoundaryIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.DOUBLE_BARLINE, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(1, 1))
            .as("a degenerate range sitting on the boundary itself must still report it")
            .isTrue();
    }

    @Test
    void testBoundaryExactlyAtRangeStartIsStructural() {
        var line = lineWith(ElementType.DOUBLE_BARLINE, ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("the range is inclusive at begin, so a boundary there counts")
            .isTrue();
    }

    @Test
    void testBoundaryExactlyAtRangeEndIsStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.DOUBLE_BARLINE);

        assertThat(line.spansStructuralBoundary(0, LAST_IDX))
            .as("the range is inclusive at end, so a boundary there counts")
            .isTrue();
    }

    @Test
    void testBoundaryJustBeforeRangeStartIsNotStructural() {
        var line = lineWith(ElementType.DOUBLE_BARLINE, ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(line.spansStructuralBoundary(1, LAST_IDX))
            .as("a boundary outside the range on the left must be ignored")
            .isFalse();
    }

    @Test
    void testBoundaryJustAfterRangeEndIsNotStructural() {
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.DOUBLE_BARLINE);

        assertThat(line.spansStructuralBoundary(0, 1))
            .as("a boundary outside the range on the right must be ignored")
            .isFalse();
    }
}
