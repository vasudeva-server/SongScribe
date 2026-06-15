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

package songscribe.util;

import static org.assertj.core.api.Assertions.assertThat;

import module java.desktop;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.UnitTest;

class UtilsTest extends UnitTest {

    @Nested
    class ArrayIndexOf {

        @Test
        void testArrayIndexOfReturnsIndexWhenElementFoundAtStart() {
            var array = new Object[]{"a", "b", "c"};
            assertThat(Utils.arrayIndexOf(array, "a")).isEqualTo(0);
        }

        @Test
        void testArrayIndexOfReturnsIndexWhenElementFoundInMiddle() {
            var array = new Object[]{"a", "b", "c"};
            assertThat(Utils.arrayIndexOf(array, "b")).isEqualTo(1);
        }

        @Test
        void testArrayIndexOfReturnsMinusOneWhenElementNotFound() {
            var array = new Object[]{"a", "b", "c"};
            assertThat(Utils.arrayIndexOf(array, "z")).isEqualTo(-1);
        }

        @Test
        void testArrayIndexOfReturnsMinusOneWhenArrayContainsNullAtSearchPosition() {
            // element.equals(null) is false for any non-null element, so nulls in the array
            // are safely skipped without NullPointerException
            var array = new Object[]{"a", null, "c"};
            assertThat(Utils.arrayIndexOf(array, "z")).isEqualTo(-1);
        }
    }

    @Nested
    class LineCount {
        private static final int THREE_LINES = 3;

        @Test
        void testLineCountWithEmptyStringReturnsZero() {
            assertThat(Utils.lineCount("")).isEqualTo(0);
        }

        @Test
        void testLineCountWithSingleLineReturnsOne() {
            assertThat(Utils.lineCount("hello")).isEqualTo(1);
        }

        @Test
        void testLineCountWithMultipleLinesReturnsCount() {
            assertThat(Utils.lineCount("a\nb\nc")).isEqualTo(THREE_LINES);
        }

        @Test
        void testLineCountWithWhitespaceOnlyReturnsOne() {
            // trim() reduces "   " to "", split("\n") on "" yields [""], length = 1
            assertThat(Utils.lineCount("   ")).isEqualTo(1);
        }
    }

    @Nested
    class RoundToTwoDecimalPlaces {
        private static final double INPUT_ROUND_DOWN = 1.234;
        private static final double EXPECTED_ROUND_DOWN = 1.23;
        private static final double INPUT_ROUND_UP = 1.236;
        private static final double EXPECTED_ROUND_UP = 1.24;
        private static final double INPUT_NEGATIVE = -1.234;
        private static final double EXPECTED_NEGATIVE = -1.23;
        private static final double INPUT_HALF_BOUNDARY = 0.005;
        private static final double EXPECTED_HALF_BOUNDARY = 0.01;

        @Test
        void testRoundToTwoDecimalPlacesRoundsDown() {
            assertThat(Utils.roundToTwoDecimalPlaces(INPUT_ROUND_DOWN)).isEqualTo(EXPECTED_ROUND_DOWN);
        }

        @Test
        void testRoundToTwoDecimalPlacesRoundsUp() {
            assertThat(Utils.roundToTwoDecimalPlaces(INPUT_ROUND_UP)).isEqualTo(EXPECTED_ROUND_UP);
        }

        @Test
        void testRoundToTwoDecimalPlacesWithNegativeValue() {
            assertThat(Utils.roundToTwoDecimalPlaces(INPUT_NEGATIVE)).isEqualTo(EXPECTED_NEGATIVE);
        }

        @Test
        void testRoundToTwoDecimalPlacesAtHalfBoundaryRoundsUp() {
            assertThat(Utils.roundToTwoDecimalPlaces(INPUT_HALF_BOUNDARY)).isEqualTo(EXPECTED_HALF_BOUNDARY);
        }

        @Test
        void testRoundToTwoDecimalPlacesWithAlreadyRoundedValueIsUnchanged() {
            assertThat(Utils.roundToTwoDecimalPlaces(1.0)).isEqualTo(1.0);
        }
    }

    @Nested
    class GetPlatformKeyStrokeString {

        @Test
        void testGetPlatformKeyStrokeStringEnterKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("Enter");
        }

        @Test
        void testGetPlatformKeyStrokeStringBackspaceKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("⌫");
        }

        @Test
        void testGetPlatformKeyStrokeStringDeleteKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("⌦");
        }

        @Test
        void testGetPlatformKeyStrokeStringEscapeKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("Esc");
        }

        @Test
        void testGetPlatformKeyStrokeStringTabKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("Tab");
        }

        @Test
        void testGetPlatformKeyStrokeStringSpaceKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("Space");
        }

        @Test
        void testGetPlatformKeyStrokeStringRegularLetterKey() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo("A");
        }

        @Test
        void testGetPlatformKeyStrokeStringCtrlModifier() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
            var expected = SystemInfo.isMacOS ? "⌃A" : "Ctrl+A";
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo(expected);
        }

        @Test
        void testGetPlatformKeyStrokeStringAltModifier() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK);
            var expected = SystemInfo.isMacOS ? "⌥A" : "Alt+A";
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo(expected);
        }

        @Test
        void testGetPlatformKeyStrokeStringMetaModifier() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.META_DOWN_MASK);
            String expected;
            if (SystemInfo.isMacOS) {
                expected = "⌘A";
            } else if (SystemInfo.isLinux) {
                expected = "Meta+A";
            } else {
                expected = "Win+A";
            }
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo(expected);
        }

        @Test
        void testGetPlatformKeyStrokeStringShiftModifier() {
            var key = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK);
            var expected = SystemInfo.isMacOS ? "⇧A" : "Shift+A";
            assertThat(Utils.getPlatformKeyStrokeString(key)).isEqualTo(expected);
        }
    }

    @Nested
    class GetResourcePath {
        private static final String RESOURCE_NAME = "junit-platform.properties";
        private static final String RESOURCE_WITH_SLASH = "/" + RESOURCE_NAME;
        private static final String NONEXISTENT_RESOURCE = "nonexistent-resource.txt";

        @Test
        void testGetResourcePathWithoutLeadingSlashReturnsExistingPath() {
            var path = Utils.getResourcePath(RESOURCE_NAME);
            assertThat(path).endsWith(RESOURCE_NAME);
        }

        @Test
        void testGetResourcePathWithLeadingSlashStripsSlash() {
            var path = Utils.getResourcePath(RESOURCE_WITH_SLASH);
            assertThat(path).endsWith(RESOURCE_NAME);
        }

        @Test
        void testGetResourcePathWithAndWithoutLeadingSlashReturnSamePath() {
            assertThat(Utils.getResourcePath(RESOURCE_WITH_SLASH))
                .isEqualTo(Utils.getResourcePath(RESOURCE_NAME));
        }

        @Test
        void testGetResourcePathNonExistentResourceReturnsFallbackPath() {
            // Falls back to classloader root + path when resource is not on the classpath
            var path = Utils.getResourcePath(NONEXISTENT_RESOURCE);
            assertThat(path).endsWith(NONEXISTENT_RESOURCE);
        }
    }
}
