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

package songscribe.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;

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
        assertThat(line.isInHairpinRange(3)).isTrue();
    }

    @Test
    void testIndexInsideDiminuendoRangeReturnsTrue() {
        var line = lineWithNotes(6);
        addDiminuendo(line, 2, 5);
        assertThat(line.isInHairpinRange(3)).isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryStartReturnsTrue() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInHairpinRange(2)).isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryEndReturnsTrue() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInHairpinRange(5)).isTrue();
    }

    @Test
    void testIndexOutsideAnyRangeReturnsFalse() {
        var line = lineWithNotes(11);
        addCrescendo(line, 2, 5);
        addDiminuendo(line, 8, 10);
        assertThat(line.isInHairpinRange(6)).isFalse();
    }

    @Test
    void testNoHairpinsOnLineReturnsFalse() {
        var line = detachedLine();
        assertThat(line.isInHairpinRange(0)).isFalse();
    }
}
