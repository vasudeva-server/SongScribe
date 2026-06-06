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

import java.awt.Insets;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link ThemeAwareMatteBorder} covering:
 * <ul>
 *   <li>Row 16 — {@code getBorderInsets()}: returns insets matching constructor arguments</li>
 * </ul>
 */
class ThemeAwareMatteBorderTest extends UnitTest {

    // First border test data
    private static final int TOP_1 = 3;
    private static final int LEFT_1 = 5;
    private static final int BOTTOM_1 = 7;
    private static final int RIGHT_1 = 11;

    // Second border test data (in-place mutation overload)
    private static final int TOP_2 = 2;
    private static final int LEFT_2 = 4;
    private static final int BOTTOM_2 = 6;
    private static final int RIGHT_2 = 8;

    // -----------------------------------------------------------------------
    // Row 16: getBorderInsets() returns correct insets
    // -----------------------------------------------------------------------

    @Test
    void testGetBorderInsetsReturnsConstructorValues() {
        var border = new ThemeAwareMatteBorder(TOP_1, LEFT_1, BOTTOM_1, RIGHT_1, "some.color.key");
        var insets = border.getBorderInsets(null);
        assertThat(insets.top).isEqualTo(TOP_1);
        assertThat(insets.left).isEqualTo(LEFT_1);
        assertThat(insets.bottom).isEqualTo(BOTTOM_1);
        assertThat(insets.right).isEqualTo(RIGHT_1);
    }

    @Test
    void testGetBorderInsetsWithExistingInsetsObjectReturnsConstructorValues() {
        var border = new ThemeAwareMatteBorder(TOP_2, LEFT_2, BOTTOM_2, RIGHT_2, "some.color.key");
        var insets = new Insets(0, 0, 0, 0);
        var result = border.getBorderInsets(null, insets);
        assertThat(result.top).isEqualTo(TOP_2);
        assertThat(result.left).isEqualTo(LEFT_2);
        assertThat(result.bottom).isEqualTo(BOTTOM_2);
        assertThat(result.right).isEqualTo(RIGHT_2);
        // The same object is returned and mutated in-place
        assertThat(result).isSameAs(insets);
    }
}
