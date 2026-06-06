/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link MyBorder} covering:
 * <ul>
 *   <li>Row 11 — Constructor {@code (size)}: all four edges set to {@code size}</li>
 *   <li>Row 12 — Constructor {@code (horizontal, vertical)}: left/right = horizontal, top/bottom = vertical</li>
 *   <li>Row 13 — Constructor {@code (top, bottom, left, right)}: each edge independent</li>
 *   <li>Row 14 — {@code withOverrides}: -1 leaves edge at defaultSize; non-negative overrides</li>
 *   <li>Row 15 — {@code getWidth()} = left + right; {@code getHeight()} = top + bottom</li>
 * </ul>
 */
class MyBorderTest extends UnitTest {

    // Uniform-constructor test data
    private static final int UNIFORM_SIZE = 7;

    // Horizontal/vertical-constructor test data
    private static final int HORIZONTAL = 3;
    private static final int VERTICAL = 5;

    // Per-edge constructor test data
    private static final int EDGE_TOP = 1;
    private static final int EDGE_BOTTOM = 2;
    private static final int EDGE_LEFT = 3;
    private static final int EDGE_RIGHT = 4;

    // withOverrides test data
    private static final int DEFAULT_SIZE = 10;
    private static final int OVERRIDE_TOP = 1;
    private static final int OVERRIDE_LEFT = 2;
    private static final int OVERRIDE_BOTTOM = 3;
    private static final int OVERRIDE_RIGHT = 4;
    private static final int MIXED_OVERRIDE_RIGHT = 5;

    // Dimension test data (getWidth / getHeight)
    private static final int DIM_TOP = 2;
    private static final int DIM_BOTTOM = 3;
    private static final int DIM_LEFT = 7;
    private static final int DIM_RIGHT = 5;

    // -----------------------------------------------------------------------
    // Row 11: Constructor (size) — all four edges equal size
    // -----------------------------------------------------------------------

    @Test
    void testUniformConstructorSetsAllEdgesToSize() {
        var border = new MyBorder(UNIFORM_SIZE);
        assertThat(border.getTop()).isEqualTo(UNIFORM_SIZE);
        assertThat(border.getBottom()).isEqualTo(UNIFORM_SIZE);
        assertThat(border.getLeft()).isEqualTo(UNIFORM_SIZE);
        assertThat(border.getRight()).isEqualTo(UNIFORM_SIZE);
    }

    // -----------------------------------------------------------------------
    // Row 12: Constructor (horizontal, vertical)
    // -----------------------------------------------------------------------

    @Test
    void testHorizontalVerticalConstructorSetsEdgesCorrectly() {
        var border = new MyBorder(HORIZONTAL, VERTICAL);
        assertThat(border.getLeft()).isEqualTo(HORIZONTAL);
        assertThat(border.getRight()).isEqualTo(HORIZONTAL);
        assertThat(border.getTop()).isEqualTo(VERTICAL);
        assertThat(border.getBottom()).isEqualTo(VERTICAL);
    }

    // -----------------------------------------------------------------------
    // Row 13: Constructor (top, bottom, left, right) — each edge independent
    // -----------------------------------------------------------------------

    @Test
    void testPerEdgeConstructorSetsEachEdgeIndependently() {
        var border = new MyBorder(EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT);
        assertThat(border.getTop()).isEqualTo(EDGE_TOP);
        assertThat(border.getBottom()).isEqualTo(EDGE_BOTTOM);
        assertThat(border.getLeft()).isEqualTo(EDGE_LEFT);
        assertThat(border.getRight()).isEqualTo(EDGE_RIGHT);
    }

    // -----------------------------------------------------------------------
    // Row 14: withOverrides — -1 leaves edge at default; non-negative overrides
    // -----------------------------------------------------------------------

    @Test
    void testWithOverridesAppliesNonNegativeValues() {
        var border = MyBorder.withOverrides(DEFAULT_SIZE, OVERRIDE_TOP, OVERRIDE_LEFT, OVERRIDE_BOTTOM, OVERRIDE_RIGHT);
        assertThat(border.getTop()).isEqualTo(OVERRIDE_TOP);
        assertThat(border.getLeft()).isEqualTo(OVERRIDE_LEFT);
        assertThat(border.getBottom()).isEqualTo(OVERRIDE_BOTTOM);
        assertThat(border.getRight()).isEqualTo(OVERRIDE_RIGHT);
    }

    @Test
    void testWithOverridesLeavesEdgesAtDefaultWhenMinusOne() {
        var border = MyBorder.withOverrides(DEFAULT_SIZE, -1, -1, -1, -1);
        assertThat(border.getTop()).isEqualTo(DEFAULT_SIZE);
        assertThat(border.getLeft()).isEqualTo(DEFAULT_SIZE);
        assertThat(border.getBottom()).isEqualTo(DEFAULT_SIZE);
        assertThat(border.getRight()).isEqualTo(DEFAULT_SIZE);
    }

    @Test
    void testWithOverridesMixedOverridesAndDefaults() {
        // top=-1 (keep DEFAULT_SIZE), left=0 (override), bottom=-1 (keep DEFAULT_SIZE), right=MIXED_OVERRIDE_RIGHT
        var border = MyBorder.withOverrides(DEFAULT_SIZE, -1, 0, -1, MIXED_OVERRIDE_RIGHT);
        assertThat(border.getTop()).isEqualTo(DEFAULT_SIZE);
        assertThat(border.getLeft()).isEqualTo(0);
        assertThat(border.getBottom()).isEqualTo(DEFAULT_SIZE);
        assertThat(border.getRight()).isEqualTo(MIXED_OVERRIDE_RIGHT);
    }

    // -----------------------------------------------------------------------
    // Row 15: getWidth() = left + right; getHeight() = top + bottom
    // -----------------------------------------------------------------------

    @Test
    void testGetWidthReturnsLeftPlusRight() {
        var border = new MyBorder(DIM_TOP, DIM_BOTTOM, DIM_LEFT, DIM_RIGHT);
        assertThat(border.getWidth()).isEqualTo(DIM_LEFT + DIM_RIGHT);
    }

    @Test
    void testGetHeightReturnsTopPlusBottom() {
        var border = new MyBorder(DIM_TOP, DIM_BOTTOM, DIM_LEFT, DIM_RIGHT);
        assertThat(border.getHeight()).isEqualTo(DIM_TOP + DIM_BOTTOM);
    }
}
