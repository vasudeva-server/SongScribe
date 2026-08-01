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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class InsetsSsTest extends UnitTest {

    private double savedScalePxPerSs;

    @BeforeEach
    void setUp() {
        savedScalePxPerSs = ScaleContext.getPixelsPerStaffSpace();
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    @AfterEach
    void tearDown() {
        ScaleContext.setPixelsPerStaffSpace(savedScalePxPerSs);
    }

    @Test
    void testToInsetsPxConvertsAllFourSidesWithKnownScale() {
        // At DEFAULT_PIXELS_PER_STAFF_SPACE (8px/ss): pin exact pixel values for each side
        final var topSs = 1.0;
        final var leftSs = 2.0;
        final var bottomSs = 3.0;
        final var rightSs = 4.0;
        final var expectedTopPx = 8;     // 8.0 × 1.0
        final var expectedLeftPx = 16;   // 8.0 × 2.0
        final var expectedBottomPx = 24; // 8.0 × 3.0
        final var expectedRightPx = 32;  // 8.0 × 4.0

        var insets = new InsetsSs(topSs, leftSs, bottomSs, rightSs);
        var result = insets.toInsetsPx();

        assertAll(
            () -> assertThat(result.top).isEqualTo(expectedTopPx),
            () -> assertThat(result.left).isEqualTo(expectedLeftPx),
            () -> assertThat(result.bottom).isEqualTo(expectedBottomPx),
            () -> assertThat(result.right).isEqualTo(expectedRightPx)
        );
    }

    @Test
    void testToInsetsPxRoundsToNearestNotTruncates() {
        // 0.5625ss × 8.0px/ss = 4.5px; Math.round gives 5, truncation would give 4
        final var halfPointSs = 0.5625;
        final var expectedRoundedPx = 5;

        var insets = new InsetsSs(halfPointSs, halfPointSs, halfPointSs, halfPointSs);
        var result = insets.toInsetsPx();

        assertAll(
            () -> assertThat(result.top).isEqualTo(expectedRoundedPx),
            () -> assertThat(result.left).isEqualTo(expectedRoundedPx),
            () -> assertThat(result.bottom).isEqualTo(expectedRoundedPx),
            () -> assertThat(result.right).isEqualTo(expectedRoundedPx)
        );
    }
}
