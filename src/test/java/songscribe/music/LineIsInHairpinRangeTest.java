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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class LineIsInHairpinRangeTest extends UnitTest {

    @Test
    void testIndexInsideCrescendoRangeReturnsTrue() {
        var line = new Line();
        line.getCrescendos().addSpan(new DynamicsSpan(2, 5));
        assertThat(line.isInHairpinRange(3)).isTrue();
    }

    @Test
    void testIndexInsideDiminuendoRangeReturnsTrue() {
        var line = new Line();
        line.getDiminuendos().addSpan(new DynamicsSpan(2, 5));
        assertThat(line.isInHairpinRange(3)).isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryStartReturnsTrue() {
        var line = new Line();
        line.getCrescendos().addSpan(new DynamicsSpan(2, 5));
        assertThat(line.isInHairpinRange(2)).isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryEndReturnsTrue() {
        var line = new Line();
        line.getCrescendos().addSpan(new DynamicsSpan(2, 5));
        assertThat(line.isInHairpinRange(5)).isTrue();
    }

    @Test
    void testIndexOutsideAnyRangeReturnsFalse() {
        var line = new Line();
        line.getCrescendos().addSpan(new DynamicsSpan(2, 5));
        line.getDiminuendos().addSpan(new DynamicsSpan(8, 10));
        assertThat(line.isInHairpinRange(6)).isFalse();
    }

    @Test
    void testNoHairpinsOnLineReturnsFalse() {
        var line = new Line();
        assertThat(line.isInHairpinRange(0)).isFalse();
    }
}
