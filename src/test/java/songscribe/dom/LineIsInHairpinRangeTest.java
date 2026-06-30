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

class LineIsInHairpinRangeTest extends UnitTest {

    /** Adds {@code count} crotchets to line and returns it. */
    private static Line lineWithNotes(int count) {
        var line = detachedLine();

        for (var i = 0; i < count; i++) {
            line.addElement(StaffElementFactory.crotchet());
        }

        return line;
    }

    /** Adds a crescendo from index {@code a} to {@code b} inclusive. */
    private static void addCrescendo(Line line, int a, int b) {
        line.addRangeElement(new Crescendo(line.getElement(a), line.getElement(b)));
    }

    /** Adds a diminuendo from index {@code a} to {@code b} inclusive. */
    private static void addDiminuendo(Line line, int a, int b) {
        line.addRangeElement(new Diminuendo(line.getElement(a), line.getElement(b)));
    }

    @Test
    void testIndexInsideCrescendoRangeReturnsTrue() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInHairpinRange(3))
            .as("index inside a crescendo range must be reported as in-range")
            .isTrue();
    }

    @Test
    void testIndexInsideDiminuendoRangeReturnsTrue() {
        var line = lineWithNotes(6);
        addDiminuendo(line, 2, 5);
        assertThat(line.isInHairpinRange(3))
            .as("index inside a diminuendo range must be reported as in-range")
            .isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryStartReturnsTrue() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInHairpinRange(2))
            .as("index at the range start boundary must be reported as in-range")
            .isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryEndReturnsTrue() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInHairpinRange(5))
            .as("index at the range end boundary must be reported as in-range")
            .isTrue();
    }

    @Test
    void testIndexOutsideAnyRangeReturnsFalse() {
        var line = lineWithNotes(11);
        addCrescendo(line, 2, 5);
        addDiminuendo(line, 8, 10);
        assertThat(line.isInHairpinRange(6))
            .as("index between two hairpin ranges must not be reported as in-range")
            .isFalse();
    }

    @Test
    void testNoHairpinsOnLineReturnsFalse() {
        var line = detachedLine();
        assertThat(line.isInHairpinRange(0))
            .as("a line with no hairpins must never report an index as in-range")
            .isFalse();
    }
}
