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

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilsTest extends UnitTest {

    private static final int CHARACTER_WIDTH_PX = 10;
    private static final int FONT_SIZE = 12;

    /**
     * Metrics in which every character is exactly {@link #CHARACTER_WIDTH_PX} wide, so a
     * line's width is its character count and each case's expected split can be read off
     * the text. A real font would make the same cases depend on which faces the machine
     * running the suite happens to have.
     */
    private static final FontMetrics FIXED_WIDTH_METRICS = new FontMetrics(
        new Font(Font.DIALOG, Font.PLAIN, FONT_SIZE)
    ) {
        @Override
        public int stringWidth(String text) {
            return text.length() * CHARACTER_WIDTH_PX;
        }
    };

    /**
     * @param maxWidthColumns the width to wrap at, in characters, so a row reads against
     *                        its own text rather than against a pixel count
     */
    private record WrapCase(
        String description,
        String text,
        int maxWidthColumns,
        List<String> expectedLines
    ) {

        List<String> wrap() {
            return StringUtils.wrapText(
                text,
                FIXED_WIDTH_METRICS,
                maxWidthColumns * CHARACTER_WIDTH_PX
            );
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrapCases")
    void testWrapTextSplitsTextIntoBalancedLines(WrapCase testCase) {
        assertThat(testCase.wrap()).containsExactlyElementsOf(testCase.expectedLines());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrapCases")
    void testWrapTextKeepsEveryWordInOrder(WrapCase testCase) {
        assertThat(String.join(" ", testCase.wrap()))
            .isEqualTo(StringUtils.collapseMultipleSpaces(testCase.text().strip()));
    }

    static Stream<WrapCase> wrapCases() {
        return Stream.of(
            new WrapCase(
                "text narrower than the width stays on one line",
                "one two",
                20,
                List.of("one two")
            ),
            new WrapCase(
                // Greedy would fill the first line and leave "cc" alone below it.
                "an even split beats filling the first line",
                "aaaaaa bbbbbb cc",
                13,
                List.of("aaaaaa", "bbbbbb cc")
            ),
            new WrapCase(
                "equally even splits keep the longer line on top",
                "aaaa bbbb cccc",
                9,
                List.of("aaaa bbbb", "cccc")
            ),
            new WrapCase(
                "a word too wide to fit takes a line of its own",
                "aa bbbbbbbbbb cc",
                5,
                List.of("aa", "bbbbbbbbbb", "cc")
            ),
            new WrapCase(
                "a repeated word survives on both lines",
                "the sun and the moon",
                12,
                List.of("the sun and", "the moon")
            ),
            new WrapCase(
                "whitespace runs collapse and the text is stripped",
                "  the   quick  ",
                20,
                List.of("the quick")
            ),
            new WrapCase(
                "text of nothing but whitespace yields one empty line",
                "   ",
                20,
                List.of("")
            ),
            new WrapCase("empty text yields one empty line", "", 20, List.of(""))
        );
    }
}
