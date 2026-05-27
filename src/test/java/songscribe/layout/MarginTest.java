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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MarginTest extends UnitTest {

    @Test
    void testUniformSetsAllSidesToGivenValue() {
        final double marginSs = 1.5;

        var margin = Margin.uniform(marginSs);

        assertAll(
            () -> assertThat(margin.leftSs()).isEqualTo(marginSs),
            () -> assertThat(margin.bottomSs()).isEqualTo(marginSs),
            () -> assertThat(margin.rightSs()).isEqualTo(marginSs)
        );
    }

    @Test
    void testNoneHasAllSidesZero() {
        assertAll(
            () -> assertThat(Margin.NONE.leftSs()).isEqualTo(0.0),
            () -> assertThat(Margin.NONE.bottomSs()).isEqualTo(0.0),
            () -> assertThat(Margin.NONE.rightSs()).isEqualTo(0.0)
        );
    }
}
