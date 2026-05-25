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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Tie;

class TieTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    // The minimum span width enforced by the production Math.max clamp.
    private static final double MIN_SPAN_WIDTH_SS = 1.0;

    // Anchor X used for span-width tests.
    private static final double ANCHOR_X_SS = 5.0;

    // end−anchor = 0.5, below the clamp floor.
    private static final double END_X_BELOW_FLOOR_SS = ANCHOR_X_SS + 0.5;

    // end−anchor = 1.0, exactly at the clamp floor.
    private static final double END_X_AT_FLOOR_SS = ANCHOR_X_SS + MIN_SPAN_WIDTH_SS;

    // end−anchor = 3.0, above the clamp floor.
    private static final double END_X_ABOVE_FLOOR_SS = ANCHOR_X_SS + 3.0;

    private static Tie createTie() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return new Tie(anchor, end);
    }

    @Test
    void testContentHeightPxIsToPixelsOfSs() {
        var tie = createTie();
        assertThat(tie.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(Tie.TIE_ARC_HEIGHT_SS),
                within(EPSILON));
    }

    @Test
    void testContentHeightSsMatchesStylesheetConstant() {
        var tie = createTie();

        assertThat(tie.getContentHeightSs())
            .isEqualTo(Tie.TIE_ARC_HEIGHT_SS);
    }

    // -----------------------------------------------------------------------
    // Row 47 — getSpanWidthSs: Math.max(MIN_SPAN_WIDTH_SS, end−anchor) clamp
    // -----------------------------------------------------------------------

    @Test
    void testGetSpanWidthSsReturnsClamFloorWhenDifferenceIsBelowFloor() {
        var tie = createTie();

        assertThat(tie.getSpanWidthSs(ANCHOR_X_SS, END_X_BELOW_FLOOR_SS))
            .isCloseTo(MIN_SPAN_WIDTH_SS, within(EPSILON));
    }

    @Test
    void testGetSpanWidthSsReturnsClamFloorWhenDifferenceEqualsFloor() {
        var tie = createTie();

        assertThat(tie.getSpanWidthSs(ANCHOR_X_SS, END_X_AT_FLOOR_SS))
            .isCloseTo(MIN_SPAN_WIDTH_SS, within(EPSILON));
    }

    @Test
    void testGetSpanWidthSsReturnsDifferenceWhenAboveFloor() {
        var tie = createTie();
        double expectedSpan = END_X_ABOVE_FLOOR_SS - ANCHOR_X_SS;

        assertThat(tie.getSpanWidthSs(ANCHOR_X_SS, END_X_ABOVE_FLOOR_SS))
            .isCloseTo(expectedSpan, within(EPSILON));
    }

    // -----------------------------------------------------------------------
    // Row 48 — isAbove: anchor.isUpper() → true; not upper → false; null anchor → false
    // -----------------------------------------------------------------------

    @Test
    void testIsAboveReturnsTrueWhenAnchorIsUpper() {
        var anchor = ElementType.QUAVER.newInstance();
        anchor.setUpper(true);
        var tie = new Tie(anchor, ElementType.QUAVER.newInstance());

        assertThat(tie.isAbove()).isTrue();
    }

    @Test
    void testIsAboveReturnsFalseWhenAnchorIsNotUpper() {
        var anchor = ElementType.QUAVER.newInstance();
        anchor.setUpper(false);
        var tie = new Tie(anchor, ElementType.QUAVER.newInstance());

        assertThat(tie.isAbove()).isFalse();
    }

    @Test
    void testIsAboveReturnsFalseWhenAnchorIsNull() {
        var tie = createTie();
        tie.setAnchorElement(null);

        assertThat(tie.isAbove()).isFalse();
    }
}
