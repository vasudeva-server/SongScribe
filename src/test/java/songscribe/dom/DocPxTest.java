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
 * Unit tests for {@link DocPx}: the two crossing rules — {@link DocPx#roundedPx()}
 * (nearest, for positions) and {@link DocPx#ceilPx()} (up, for sizes) — at the
 * boundary values where they diverge from a naive truncation.
 */
class DocPxTest extends UnitTest {

    @Test
    void testRoundedPxRoundsDownBelowHalf() {
        assertThat(new DocPx(3.4).roundedPx()).isEqualTo(3);
    }

    @Test
    void testRoundedPxRoundsUpAtOrAboveHalf() {
        assertThat(new DocPx(3.5).roundedPx()).isEqualTo(4);
    }

    @Test
    void testCeilPxRoundsUpForAnyFraction() {
        assertThat(new DocPx(3.4).ceilPx()).isEqualTo(4);
        assertThat(new DocPx(3.5).ceilPx()).isEqualTo(4);
    }

    @Test
    void testCeilPxLeavesWholeNumberUnchanged() {
        assertThat(new DocPx(5.0).ceilPx()).isEqualTo(5);
    }

    @Test
    void testRoundedPxLeavesWholeNumberUnchanged() {
        assertThat(new DocPx(5.0).roundedPx()).isEqualTo(5);
    }
}
