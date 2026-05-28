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

package songscribe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class StringUtilsTest extends UnitTest {

    @Nested
    class CapitalizeSentence {

        @Test
        void testCapitalizeSentenceWithEmptyStringReturnsEmpty() {
            assertThat(StringUtils.capitalizeSentence("")).isEqualTo("");
        }

        @Test
        void testCapitalizeSentenceWithAllCapsReturnsFirstCapRestLower() {
            assertThat(StringUtils.capitalizeSentence("HELLO")).isEqualTo("Hello");
        }

        @Test
        void testCapitalizeSentenceWithAlreadyCapitalizedReturnsUnchanged() {
            assertThat(StringUtils.capitalizeSentence("Hello")).isEqualTo("Hello");
        }

        @Test
        void testCapitalizeSentenceUppercasesFirstAndLowercasesRest() {
            assertThat(StringUtils.capitalizeSentence("hELLO wORLD")).isEqualTo("Hello world");
        }
    }

    @Nested
    class ToKebabCase {

        @Test
        void testToKebabCaseWithEmptyStringReturnsEmpty() {
            assertThat(StringUtils.toKebabCase("")).isEqualTo("");
        }

        @Test
        void testToKebabCaseConvertsSpacesToHyphens() {
            assertThat(StringUtils.toKebabCase("Hello World")).isEqualTo("hello-world");
        }

        @Test
        void testToKebabCaseCollapseMultipleSpacesToSingleHyphen() {
            assertThat(StringUtils.toKebabCase("a  b")).isEqualTo("a-b");
        }

        @Test
        void testToKebabCasePreservesAccentedLetters() {
            // Unicode letters (\p{L}) are kept — accented chars are not stripped
            assertThat(StringUtils.toKebabCase("naïve")).isEqualTo("naïve");
        }

        @Test
        void testToKebabCasePreservesLeadingAndTrailingHyphens() {
            assertThat(StringUtils.toKebabCase("-hello-")).isEqualTo("-hello-");
        }
    }

    @Nested
    class StripDiacritics {

        @Test
        void testStripDiacriticsRemovesAccentsFromLatinChars() {
            assertThat(StringUtils.stripDiacritics("résumé")).isEqualTo("resume");
        }

        @Test
        void testStripDiacriticsLeavesPlainLettersUnchanged() {
            assertThat(StringUtils.stripDiacritics("hello")).isEqualTo("hello");
        }
    }

    @Nested
    class StripLinefeeds {

        @Test
        void testStripLinefeedsReplacesNewlineWithSpace() {
            assertThat(StringUtils.stripLinefeeds("hello\nworld")).isEqualTo("hello world");
        }

        @Test
        void testStripLinefeedsReplacesMultipleNewlines() {
            assertThat(StringUtils.stripLinefeeds("a\nb\nc")).isEqualTo("a b c");
        }

        @Test
        void testStripLinefeedsWithNoNewlineReturnsUnchanged() {
            assertThat(StringUtils.stripLinefeeds("hello")).isEqualTo("hello");
        }
    }

    @Nested
    class TrimEnd {

        @Test
        void testTrimEndRemovesTrailingSpace() {
            assertThat(StringUtils.trimEnd("hello ")).isEqualTo("hello");
        }

        @Test
        void testTrimEndRemovesTrailingTab() {
            assertThat(StringUtils.trimEnd("hello\t")).isEqualTo("hello");
        }

        @Test
        void testTrimEndRemovesTrailingNewline() {
            assertThat(StringUtils.trimEnd("hello\n")).isEqualTo("hello");
        }

        @Test
        void testTrimEndWithNoTrailingWhitespaceReturnsUnchanged() {
            assertThat(StringUtils.trimEnd("hello")).isEqualTo("hello");
        }
    }

    @Nested
    class CollapseMultipleSpaces {

        @Test
        void testCollapseMultipleSpacesCollapsesInternalRuns() {
            assertThat(StringUtils.collapseMultipleSpaces("a  b")).isEqualTo("a b");
        }

        @Test
        void testCollapseMultipleSpacesPreservesLeadingSpaces() {
            assertThat(StringUtils.collapseMultipleSpaces("  hello")).isEqualTo("  hello");
        }

        @Test
        void testCollapseMultipleSpacesLeavesSingleSpaceUnchanged() {
            assertThat(StringUtils.collapseMultipleSpaces("a b")).isEqualTo("a b");
        }

        @Test
        void testCollapseMultipleSpacesPreservesLeadingSpacesAndCollapsesInternal() {
            assertThat(StringUtils.collapseMultipleSpaces("  a  b")).isEqualTo("  a b");
        }
    }

    @Nested
    class WrapText {
        private static final int WORD_WIDTH = 3;
        private static final int SPACE_WIDTH = 1;
        private static final int LARGE_MAX_WIDTH = 100;
        // NARROW_MAX_WIDTH: two words (3+1+3=7) fit, three (7+1+3=11) don't
        private static final int NARROW_MAX_WIDTH = 10;

        private FontMetrics metricsWithFixedWordWidth() {
            var metrics = mock(FontMetrics.class);
            when(metrics.stringWidth(anyString())).thenReturn(WORD_WIDTH);
            when(metrics.stringWidth(" ")).thenReturn(SPACE_WIDTH);
            return metrics;
        }

        @Test
        void testWrapTextEmptyInputReturnsListWithEmptyString() {
            assertThat(StringUtils.wrapText("", metricsWithFixedWordWidth(), LARGE_MAX_WIDTH))
                .containsExactly("");
        }

        @Test
        void testWrapTextInputFittingOnOneLineIsNotWrapped() {
            assertThat(StringUtils.wrapText("hello world", metricsWithFixedWordWidth(), LARGE_MAX_WIDTH))
                .containsExactly("hello world");
        }

        @Test
        void testWrapTextSingleWordWiderThanMaxWidthIsKeptOnOneLine() {
            // The loop guard (end > start) ensures the loop always makes progress even
            // when a single word exceeds maxWidth — so the word is kept rather than skipped.
            assertThat(StringUtils.wrapText("wide", metricsWithFixedWordWidth(), 0))
                .containsExactly("wide");
        }

        @Test
        void testWrapTextRebalancingMovesWordsFromPreviousLineToShortLastLine() {
            // "a b c d" wraps to [["a","b"], ["c","d"]]; last line (2 words) is below
            // MINIMUM_WRAPPED_WORD_COUNT (3), so "b" is moved from the previous line.
            assertThat(StringUtils.wrapText("a b c d", metricsWithFixedWordWidth(), NARROW_MAX_WIDTH))
                .containsExactly("a", "b c d");
        }

        @Test
        void testWrapTextRebalancingLeavesEmptyLineWhenPreviousLineTooShortToDonate() {
            // "a b c d e" wraps to [["a","b"],["c","d"],["e"]]; rebalancing drains line 1
            // into line 2 (→["c","d","e"]), leaving line 1 empty. Line 0 has only 2 words
            // but needs to donate 3, so line 1 stays empty in the output.
            assertThat(StringUtils.wrapText("a b c d e", metricsWithFixedWordWidth(), NARROW_MAX_WIDTH))
                .containsExactly("a b", "", "c d e");
        }
    }
}
