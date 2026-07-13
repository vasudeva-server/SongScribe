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

package songscribe.shape;

import static org.assertj.core.api.Assertions.assertThat;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class TupletBracketShapeTest extends UnitTest {

    private static final double LEFT_X_SS = 10.0;
    private static final double GAP_LEFT_X_SS = 20.0;
    private static final double GAP_RIGHT_X_SS = 24.0;
    private static final double RIGHT_X_SS = 34.0;
    private static final double LEFT_Y_SS = -5.0;
    private static final double GAP_LEFT_Y_SS = -5.5;
    private static final double GAP_RIGHT_Y_SS = -6.0;
    private static final double RIGHT_Y_SS = -6.5;
    private static final double ARM_BOTTOM_Y_SS = -2.0;

    @Test
    void testLeftArmOrdersPointsVerticalUpThenAcrossToGap() {
        var points = TupletBracketShape.leftArm(
            LEFT_X_SS, GAP_LEFT_X_SS, LEFT_Y_SS, GAP_LEFT_Y_SS, ARM_BOTTOM_Y_SS);

        assertThat(points).containsExactly(
            new Point2D.Double(LEFT_X_SS, ARM_BOTTOM_Y_SS),
            new Point2D.Double(LEFT_X_SS, LEFT_Y_SS),
            new Point2D.Double(GAP_LEFT_X_SS, GAP_LEFT_Y_SS));
    }

    @Test
    void testRightArmOrdersPointsAcrossFromGapThenVerticalDown() {
        var points = TupletBracketShape.rightArm(
            GAP_RIGHT_X_SS, RIGHT_X_SS, GAP_RIGHT_Y_SS, RIGHT_Y_SS, ARM_BOTTOM_Y_SS);

        assertThat(points).containsExactly(
            new Point2D.Double(GAP_RIGHT_X_SS, GAP_RIGHT_Y_SS),
            new Point2D.Double(RIGHT_X_SS, RIGHT_Y_SS),
            new Point2D.Double(RIGHT_X_SS, ARM_BOTTOM_Y_SS));
    }

    @Test
    void testArmsShareTheSameXAtTheirOwnVerticalRunAndSlopedCorner() {
        var leftPoints = TupletBracketShape.leftArm(
            LEFT_X_SS, GAP_LEFT_X_SS, LEFT_Y_SS, GAP_LEFT_Y_SS, ARM_BOTTOM_Y_SS);
        var rightPoints = TupletBracketShape.rightArm(
            GAP_RIGHT_X_SS, RIGHT_X_SS, GAP_RIGHT_Y_SS, RIGHT_Y_SS, ARM_BOTTOM_Y_SS);

        // The left arm's vertical (index 0-1) stays at leftXSs; the right arm's vertical
        // (index 1-2) stays at rightXSs — each corner's own X, not a shared/averaged one.
        assertThat(leftPoints[0].getX()).isEqualTo(LEFT_X_SS);
        assertThat(leftPoints[1].getX()).isEqualTo(LEFT_X_SS);
        assertThat(rightPoints[1].getX()).isEqualTo(RIGHT_X_SS);
        assertThat(rightPoints[2].getX()).isEqualTo(RIGHT_X_SS);
    }
}
