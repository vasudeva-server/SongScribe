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

package songscribe.ui.component.toolbar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.awt.Insets;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.component.ThemeAwareMatteBorder;

/**
 * Verifies the bitmask-to-border-width logic in
 * {@link MainToolbarPanel#createToolbarPanel(int)}.
 *
 * <p>The method accepts a flags integer composed of {@code LEFT_BORDER} and
 * {@code RIGHT_BORDER} bits and translates each set bit into a 1-pixel inset
 * on the corresponding side of a {@link ThemeAwareMatteBorder}. All four
 * reachable combinations are exercised.
 */
class MainToolbarPanelTest extends UnitTest {

    /**
     * Returns the border insets of the panel created by
     * {@link MainToolbarPanel#createToolbarPanel(int)} for the given flag value.
     */
    private static Insets insetsFor(int flags) {
        var panel = MainToolbarPanel.createToolbarPanel(flags);
        var border = (ThemeAwareMatteBorder) panel.getBorder();
        return border.getBorderInsets(null);
    }

    @Test
    void testNoBorderFlagProducesZeroInsets() {
        var insets = insetsFor(MainToolbarPanel.NO_BORDER);
        assertAll(
            () -> assertThat(insets.left).as("left inset for NO_BORDER").isZero(),
            () -> assertThat(insets.right).as("right inset for NO_BORDER").isZero()
        );
    }

    @Test
    void testLeftBorderFlagProducesOnePixelLeftInset() {
        var insets = insetsFor(MainToolbarPanel.LEFT_BORDER);
        assertAll(
            () -> assertThat(insets.left).as("left inset for LEFT_BORDER").isEqualTo(1),
            () -> assertThat(insets.right).as("right inset for LEFT_BORDER").isZero()
        );
    }

    @Test
    void testRightBorderFlagProducesOnePixelRightInset() {
        var insets = insetsFor(MainToolbarPanel.RIGHT_BORDER);
        assertAll(
            () -> assertThat(insets.left).as("left inset for RIGHT_BORDER").isZero(),
            () -> assertThat(insets.right).as("right inset for RIGHT_BORDER").isEqualTo(1)
        );
    }

    @Test
    void testBothBorderFlagsProducesOnePixelOnEachSide() {
        var insets = insetsFor(MainToolbarPanel.LEFT_BORDER | MainToolbarPanel.RIGHT_BORDER);
        assertAll(
            () -> assertThat(insets.left).as("left inset for LEFT_BORDER|RIGHT_BORDER").isEqualTo(1),
            () -> assertThat(insets.right).as("right inset for LEFT_BORDER|RIGHT_BORDER").isEqualTo(1)
        );
    }
}
