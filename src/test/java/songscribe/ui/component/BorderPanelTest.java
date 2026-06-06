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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link BorderPanel} covering:
 * <ul>
 *   <li>Row 7 — {@code getMyBorder()}: simple mode returns {@code MyBorder(uniformValue)}</li>
 *   <li>Row 8 — {@code getMyBorder()}: expert mode returns per-edge {@code MyBorder}</li>
 *   <li>Row 9 — {@code setExpertBorder(true)}: copies uniform spinner value into all four edge spinners</li>
 * </ul>
 */
class BorderPanelTest extends UnitTest {

    private static final int UNIFORM_SIZE = 20;
    private static final int CUSTOM_UNIFORM_SIZE = 25;
    private static final int EXPERT_TOP = 1;
    private static final int EXPERT_BOTTOM = 2;
    private static final int EXPERT_LEFT = 3;
    private static final int EXPERT_RIGHT = 4;

    private BorderPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        installFlatLafDefaults();
        panel = new BorderPanel();
    }

    // -----------------------------------------------------------------------
    // Row 7: getMyBorder() in simple mode returns MyBorder(uniformValue)
    // -----------------------------------------------------------------------

    @Test
    void testGetMyBorderInSimpleModeReturnsUniformBorder() {
        panel.setUniformBorderValue(UNIFORM_SIZE);
        var border = panel.getMyBorder();
        assertThat(border.getTop()).isEqualTo(UNIFORM_SIZE);
        assertThat(border.getBottom()).isEqualTo(UNIFORM_SIZE);
        assertThat(border.getLeft()).isEqualTo(UNIFORM_SIZE);
        assertThat(border.getRight()).isEqualTo(UNIFORM_SIZE);
    }

    // -----------------------------------------------------------------------
    // Row 8: getMyBorder() in expert mode returns per-edge MyBorder
    // -----------------------------------------------------------------------

    @Test
    void testGetMyBorderInExpertModeReturnsPerEdgeBorder() {
        panel.setExpertBorder(true);
        // Set distinct values on each edge spinner
        panel.setEdgeBorderValues(EXPERT_TOP, EXPERT_BOTTOM, EXPERT_LEFT, EXPERT_RIGHT);
        var border = panel.getMyBorder();
        assertThat(border.getTop()).as("top").isEqualTo(EXPERT_TOP);
        assertThat(border.getBottom()).as("bottom").isEqualTo(EXPERT_BOTTOM);
        assertThat(border.getLeft()).as("left").isEqualTo(EXPERT_LEFT);
        assertThat(border.getRight()).as("right").isEqualTo(EXPERT_RIGHT);
    }

    // -----------------------------------------------------------------------
    // Row 9: setExpertBorder(true) copies uniform spinner value into all four edge spinners
    // -----------------------------------------------------------------------

    @Test
    void testSetExpertBorderTrueCopiesUniformValueToEdgeSpinners() {
        // The panel starts in simple mode with borderSpinner at DEFAULT_BORDER_SIZE.
        // Switching to expert mode should propagate that value to all four edge spinners.
        panel.setExpertBorder(true);
        // After the switch, getMyBorder() reads from edge spinners (all set to DEFAULT_BORDER_SIZE)
        var border = panel.getMyBorder();
        assertThat(border.getTop()).as("top spinner should receive uniform value").isEqualTo(BorderPanel.DEFAULT_BORDER_SIZE);
        assertThat(border.getBottom()).as("bottom spinner should receive uniform value").isEqualTo(BorderPanel.DEFAULT_BORDER_SIZE);
        assertThat(border.getLeft()).as("left spinner should receive uniform value").isEqualTo(BorderPanel.DEFAULT_BORDER_SIZE);
        assertThat(border.getRight()).as("right spinner should receive uniform value").isEqualTo(BorderPanel.DEFAULT_BORDER_SIZE);
    }

    @Test
    void testSetExpertBorderTrueWithCustomUniformValueCopiesCorrectValue() {
        // Set a non-default uniform value before switching to expert mode
        panel.setUniformBorderValue(CUSTOM_UNIFORM_SIZE);
        panel.setExpertBorder(true);
        var border = panel.getMyBorder();
        assertThat(border.getTop()).isEqualTo(CUSTOM_UNIFORM_SIZE);
        assertThat(border.getBottom()).isEqualTo(CUSTOM_UNIFORM_SIZE);
        assertThat(border.getLeft()).isEqualTo(CUSTOM_UNIFORM_SIZE);
        assertThat(border.getRight()).isEqualTo(CUSTOM_UNIFORM_SIZE);
    }
}
