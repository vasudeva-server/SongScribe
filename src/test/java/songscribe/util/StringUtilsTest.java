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
            .isEqualTo(
                StringUtils.collapseMultipleSpaces(
                    StringUtils.stripLinefeeds(testCase.text().strip())
                )
            );
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
            new WrapCase("empty text yields one empty line", "", 20, List.of("")),
            new WrapCase(
                // Both words fit on one line at this width, so only the break splits them.
                "a line break ends a line the width would have kept together",
                "one\ntwo",
                20,
                List.of("one", "two")
            ),
            new WrapCase(
                "each hard-broken segment is balance-wrapped on its own",
                "aaaaaa bbbbbb cc\ndd",
                13,
                List.of("aaaaaa", "bbbbbb cc", "dd")
            ),
            new WrapCase(
                "a blank segment between breaks yields no line",
                "  the   \n\n quick  brown \n",
                20,
                List.of("the", "quick brown")
            ),
            new WrapCase(
                "text of nothing but breaks yields one empty line",
                "\n \n",
                20,
                List.of("")
            )
        );
    }

    // Budgets the fold is exercised at: one is its minimum, two is the general case. These
    // are inputs to the operation, not the line caps any particular field happens to use.
    private static final int ONE_LINE_BUDGET = 1;
    private static final int TWO_LINE_BUDGET = 2;

    /** Raw text, the line budget it is folded to, and what the fold promises to make of it. */
    private record FoldCase(String description, String raw, int maxLines, String expected) {}

    static Stream<FoldCase> foldCases() {
        return Stream.of(
            new FoldCase(
                "a blank line is dropped rather than spending the budget on it",
                "\nOne\nTwo",
                TWO_LINE_BUDGET,
                "One\nTwo"
            ),
            new FoldCase(
                "a blank line between two lines is dropped the same way",
                "One\n\nTwo",
                TWO_LINE_BUDGET,
                "One\nTwo"
            ),
            new FoldCase(
                "text ending in a break keeps it, so a break typed at the end of a line survives",
                "One\n",
                TWO_LINE_BUDGET,
                "One\n"
            ),
            new FoldCase(
                "a break past the budget joins its line onto the last one",
                "One\nTwo\nThree",
                TWO_LINE_BUDGET,
                "One\nTwo Three"
            ),
            new FoldCase(
                "a break typed at the end of the last line has no room and is not kept",
                "One\nTwo\n",
                TWO_LINE_BUDGET,
                "One\nTwo"
            ),
            new FoldCase(
                "a one-line budget joins everything onto one line",
                "One\nTwo\nThree",
                ONE_LINE_BUDGET,
                "One Two Three"
            ),
            new FoldCase(
                "text of nothing but whitespace and breaks folds to nothing",
                "  \n \t \n  ",
                TWO_LINE_BUDGET,
                ""
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foldCases")
    void testFoldSurplusLineBreaks(FoldCase testCase) {
        assertThat(StringUtils.foldSurplusLineBreaks(testCase.raw(), testCase.maxLines()))
            .isEqualTo(testCase.expected());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foldCases")
    void testFoldSurplusLineBreaksIsIdempotent(FoldCase testCase) {
        var once = StringUtils.foldSurplusLineBreaks(testCase.raw(), testCase.maxLines());

        assertThat(StringUtils.foldSurplusLineBreaks(once, testCase.maxLines())).isEqualTo(once);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foldCases")
    void testFoldSurplusLineBreaksReadsEveryLineEndingTheSameWay(FoldCase testCase) {
        var raw = testCase.raw();

        assertThat(StringUtils.foldSurplusLineBreaks(raw.replace("\n", "\r\n"), testCase.maxLines()))
            .isEqualTo(testCase.expected());
        assertThat(StringUtils.foldSurplusLineBreaks(raw.replace("\n", "\r"), testCase.maxLines()))
            .isEqualTo(testCase.expected());
    }
}
