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

package songscribe.smufl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class BBoxTest extends UnitTest {

    // coordinate fixtures — named to satisfy the no-magic-numbers rule
    private static final double LEFT   = 1.5;
    private static final double TOP    = 2.0;
    private static final double RIGHT  = 4.5;
    private static final double BOTTOM = 5.5;

    private static final double EXPECTED_WIDTH  = RIGHT - LEFT;   // 3.0
    private static final double EXPECTED_HEIGHT = BOTTOM - TOP;   // 3.5

    // translateX fixture
    private static final double DELTA_X             = 2.5;
    private static final double TRANSLATED_LEFT     = LEFT + DELTA_X;
    private static final double TRANSLATED_RIGHT    = RIGHT + DELTA_X;

    // union fixtures — min/max intentionally comes from DIFFERENT boxes per axis
    //   Box A: left=-3, top=1,  right=7,  bottom=5
    //   Box B: left=-1, top=-2, right=4,  bottom=9
    //   Union: left=-3 (A), top=-2 (B), right=7 (A), bottom=9 (B)
    private static final double A_LEFT   = -3.0;
    private static final double A_TOP    = 1.5;
    private static final double A_RIGHT  = 7.0;
    private static final double A_BOTTOM = 5.0;

    private static final double B_LEFT   = -1.0;
    private static final double B_TOP    = -2.5;
    private static final double B_RIGHT  = 4.0;
    private static final double B_BOTTOM = 9.0;

    private static final double UNION_LEFT   = A_LEFT;    // min(-3, -1) from A
    private static final double UNION_TOP    = B_TOP;     // min(1.5, -2.5) from B
    private static final double UNION_RIGHT  = A_RIGHT;   // max(7, 4) from A
    private static final double UNION_BOTTOM = B_BOTTOM;  // max(5, 9) from B

    // fromSMuFL fixtures — Y-up SMuFL coords → Y-down screen coords
    //   swX=0.5, swY=-0.3, neX=1.2, neY=0.8
    //   → left=swX=0.5, top=-neY=-0.8, right=neX=1.2, bottom=-swY=0.3
    private static final double SW_X               = 0.5;
    private static final double SW_Y               = -0.3;
    private static final double NE_X               = 1.2;
    private static final double NE_Y               = 0.8;
    private static final double SMUFL_EXPECTED_LEFT   = SW_X;
    private static final double SMUFL_EXPECTED_TOP    = -NE_Y;
    private static final double SMUFL_EXPECTED_RIGHT  = NE_X;
    private static final double SMUFL_EXPECTED_BOTTOM = -SW_Y;

    private static final double TOLERANCE = 1e-9;

    @Test
    void testWidthIsRightMinusLeft() {
        var box = new BBox(LEFT, TOP, RIGHT, BOTTOM);
        assertThat(box.width()).isCloseTo(EXPECTED_WIDTH, within(TOLERANCE));
    }

    @Test
    void testHeightIsBottomMinusTop() {
        var box = new BBox(LEFT, TOP, RIGHT, BOTTOM);
        assertThat(box.height()).isCloseTo(EXPECTED_HEIGHT, within(TOLERANCE));
    }

    @Test
    void testTranslateXShiftsHorizontallyOnly() {
        var box = new BBox(LEFT, TOP, RIGHT, BOTTOM);
        var translated = box.translateX(DELTA_X);

        assertThat(translated.left()).isCloseTo(TRANSLATED_LEFT, within(TOLERANCE));
        assertThat(translated.right()).isCloseTo(TRANSLATED_RIGHT, within(TOLERANCE));
        assertThat(translated.top()).isCloseTo(TOP, within(TOLERANCE));
        assertThat(translated.bottom()).isCloseTo(BOTTOM, within(TOLERANCE));
    }

    @Test
    void testUnionReturnsSmallestEnclosingBox() {
        var a = new BBox(A_LEFT, A_TOP, A_RIGHT, A_BOTTOM);
        var b = new BBox(B_LEFT, B_TOP, B_RIGHT, B_BOTTOM);
        var union = a.union(b);

        assertThat(union.left()).isCloseTo(UNION_LEFT, within(TOLERANCE));
        assertThat(union.top()).isCloseTo(UNION_TOP, within(TOLERANCE));
        assertThat(union.right()).isCloseTo(UNION_RIGHT, within(TOLERANCE));
        assertThat(union.bottom()).isCloseTo(UNION_BOTTOM, within(TOLERANCE));
    }

    @Test
    void testFromSmuflFlipsYConvention() {
        var box = BBox.fromSMuFL(SW_X, SW_Y, NE_X, NE_Y);

        assertThat(box.left()).isCloseTo(SMUFL_EXPECTED_LEFT, within(TOLERANCE));
        assertThat(box.top()).isCloseTo(SMUFL_EXPECTED_TOP, within(TOLERANCE));
        assertThat(box.right()).isCloseTo(SMUFL_EXPECTED_RIGHT, within(TOLERANCE));
        assertThat(box.bottom()).isCloseTo(SMUFL_EXPECTED_BOTTOM, within(TOLERANCE));
    }
}
