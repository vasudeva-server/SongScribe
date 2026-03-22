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

package songscribe.ui.component;

import module java.desktop;

import songscribe.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ThemeAwareMatteBorderTest extends UnitTest {

    private static final String TEST_KEY = "Test.borderColor";

    @AfterEach
    void tearDown() {
        UIManager.put(TEST_KEY, null);
    }

    @Test
    void testBorderInsetsMatchConstructorValues() {
        var border = new ThemeAwareMatteBorder(1, 2, 3, 4, TEST_KEY);
        var insets = border.getBorderInsets(null);

        assertThat(insets.top).isEqualTo(1);
        assertThat(insets.left).isEqualTo(2);
        assertThat(insets.bottom).isEqualTo(3);
        assertThat(insets.right).isEqualTo(4);
    }

    @Test
    void testBorderInsetsReusesProvidedObject() {
        var border = new ThemeAwareMatteBorder(5, 6, 7, 8, TEST_KEY);
        var insets = new Insets(0, 0, 0, 0);
        var result = border.getBorderInsets(null, insets);

        assertThat(result).isSameAs(insets);
        assertThat(insets.top).isEqualTo(5);
        assertThat(insets.left).isEqualTo(6);
        assertThat(insets.bottom).isEqualTo(7);
        assertThat(insets.right).isEqualTo(8);
    }

    @Test
    void testIsBorderOpaque() {
        var border = new ThemeAwareMatteBorder(1, 0, 0, 0, TEST_KEY);

        assertThat(border.isBorderOpaque()).isTrue();
    }

    @Test
    void testPaintBorderFallsBackToDefaultWhenKeyMissing() {
        var border = new ThemeAwareMatteBorder(1, 0, 0, 0, TEST_KEY);
        var g = mock(Graphics.class);

        border.paintBorder(null, g, 0, 0, 100, 50);

        verify(g).setColor(Color.GRAY);
        verify(g).fillRect(0, 0, 100, 1);
    }

    @Test
    void testPaintBorderReadsColorFromUIManager() {
        var customColor = new Color(0x112233);
        UIManager.put(TEST_KEY, customColor);

        var border = new ThemeAwareMatteBorder(0, 0, 2, 0, TEST_KEY);
        var g = mock(Graphics.class);

        border.paintBorder(null, g, 0, 0, 100, 50);

        verify(g).setColor(customColor);
        verify(g).fillRect(0, 48, 100, 2);
    }

    @Test
    void testPaintBorderReReadsColorOnEachCall() {
        var border = new ThemeAwareMatteBorder(1, 0, 0, 0, TEST_KEY);
        var g = mock(Graphics.class);

        // First call: no color set, should use default
        border.paintBorder(null, g, 0, 0, 100, 50);
        verify(g).setColor(Color.GRAY);

        // Set a color
        var newColor = new Color(0xFF0000);
        UIManager.put(TEST_KEY, newColor);

        // Second call: should use the new color
        border.paintBorder(null, g, 0, 0, 100, 50);
        verify(g).setColor(newColor);
    }

    @Test
    void testPaintBorderSkipsZeroThicknessSides() {
        var border = new ThemeAwareMatteBorder(0, 0, 0, 0, TEST_KEY);
        var g = mock(Graphics.class);

        border.paintBorder(null, g, 0, 0, 100, 50);

        verify(g).setColor(any());
        verify(g, never()).fillRect(anyInt(), anyInt(), anyInt(), anyInt());
    }
}
