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
    class ToTypographic {

        // -- Double quotes --

        @Test
        void testToTypographicConvertsDoubleQuotedPhrase() {
            var input = "\"hi\"";
            var expected = "“hi”";
            assertThat(StringUtils.toTypographic(input)).isEqualTo(expected);
        }

        @Test
        void testToTypographicDoubleQuoteIsIdempotent() {
            var input = "\"hi\"";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Single quotes --

        @Test
        void testToTypographicConvertsSingleQuotedPhrase() {
            var input = "'hello'";
            var expected = "‘hello’";
            assertThat(StringUtils.toTypographic(input)).isEqualTo(expected);
        }

        @Test
        void testToTypographicSingleQuotedPhraseIsIdempotent() {
            var input = "'hello'";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Apostrophes (contractions & possessives) --

        @Test
        void testToTypographicConvertsContraction() {
            var input = "don't";
            var expected = "don’t";
            assertThat(StringUtils.toTypographic(input)).isEqualTo(expected);
        }

        @Test
        void testToTypographicContractionIsIdempotent() {
            var input = "don't";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        @Test
        void testToTypographicConvertsPossessive() {
            var input = "God's";
            var expected = "God’s";
            assertThat(StringUtils.toTypographic(input)).isEqualTo(expected);
        }

        @Test
        void testToTypographicPossessiveIsIdempotent() {
            var input = "God's";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Em dash (greedy collapse) --

        @Test
        void testToTypographicConvertsDoubleDashToEmDash() {
            var expected = "—";
            assertThat(StringUtils.toTypographic("--")).isEqualTo(expected);
        }

        @Test
        void testToTypographicConvertsTripleDashToSingleEmDash() {
            var expected = "—";
            assertThat(StringUtils.toTypographic("---")).isEqualTo(expected);
        }

        @Test
        void testToTypographicConvertsQuadrupleDashToSingleEmDash() {
            var expected = "—";
            assertThat(StringUtils.toTypographic("----")).isEqualTo(expected);
        }

        @Test
        void testToTypographicPreservesSingleDash() {
            assertThat(StringUtils.toTypographic("-")).isEqualTo("-");
        }

        @Test
        void testToTypographicEmDashIsIdempotent() {
            var input = "--";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Ellipsis (greedy collapse) --

        @Test
        void testToTypographicConvertsThreeDotsToEllipsis() {
            var expected = "…";
            assertThat(StringUtils.toTypographic("...")).isEqualTo(expected);
        }

        @Test
        void testToTypographicConvertsFourDotsToSingleEllipsis() {
            var expected = "…";
            assertThat(StringUtils.toTypographic("....")).isEqualTo(expected);
        }

        @Test
        void testToTypographicPreservesSpacedDots() {
            assertThat(StringUtils.toTypographic(". . .")).isEqualTo(". . .");
        }

        @Test
        void testToTypographicEllipsisIsIdempotent() {
            var input = "...";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Closing-before-punctuation lookaheads --

        @Test
        void testToTypographicClosingDoubleQuoteBeforePunctuation() {
            // The closing double-quote pattern's [.,!?;:"] lookahead fires before a period.
            assertThat(StringUtils.toTypographic("\"hi\".")).isEqualTo("“hi”.");
        }

        @Test
        void testToTypographicClosingSingleQuoteBeforePunctuation() {
            // The closing single-quote pattern's [.,!?;:"] lookahead fires before a comma.
            assertThat(StringUtils.toTypographic("'word',")).isEqualTo("‘word’,");
        }

        // -- Fallback replacements (no contextual match) --

        @Test
        void testToTypographicLoneDoubleQuoteUsesClosingFallback() {
            // A double quote with no opening/closing context falls back to closing.
            assertThat(StringUtils.toTypographic("a\"b")).isEqualTo("a”b");
        }

        @Test
        void testToTypographicLoneSingleQuoteUsesApostropheFallback() {
            // A word-interior apostrophe (not a contraction lookahead) falls back to ’.
            assertThat(StringUtils.toTypographic("O'clock")).isEqualTo("O’clock");
        }

        // -- ll/ve/re contraction lookaheads --

        @Test
        void testToTypographicConvertsWillContraction() {
            assertThat(StringUtils.toTypographic("we'll")).isEqualTo("we’ll");
        }

        @Test
        void testToTypographicConvertsHaveContraction() {
            assertThat(StringUtils.toTypographic("we've")).isEqualTo("we’ve");
        }

        @Test
        void testToTypographicConvertsAreContraction() {
            assertThat(StringUtils.toTypographic("we're")).isEqualTo("we’re");
        }

        // -- Nested single quote inside double quote (regression) --

        @Test
        void testToTypographicNestedSingleQuoteInsideDoubleQuote() {
            // The double-quote pass converts the outer " to a curly “ first, so the
            // opening single-quote lookbehind must accept “ to open the inner ‘.
            assertThat(StringUtils.toTypographic("\"'word'\"")).isEqualTo("“‘word’”");
        }

        // -- Combined input (all substitution types in one string) --

        @Test
        void testToTypographicConvertsCombinedInput() {
            assertThat(StringUtils.toTypographic("\"don't\" -- yes...")).isEqualTo("“don’t” — yes…");
        }

        @Test
        void testToTypographicCombinedInputIsIdempotent() {
            var once = StringUtils.toTypographic("\"don't\" -- yes...");
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Multiline opening quote --

        @Test
        void testToTypographicOpensQuoteOnInteriorLine() {
            // The MULTILINE flag makes ^ match interior line starts, so the opening
            // double quote after \n is treated as an opening curly quote.
            var input = "a\n\"hi\"";
            var expected = "a\n“hi”";
            assertThat(StringUtils.toTypographic(input)).isEqualTo(expected);
        }

        @Test
        void testToTypographicMultilineIsIdempotent() {
            var input = "a\n\"hi\"";
            var once = StringUtils.toTypographic(input);
            assertThat(StringUtils.toTypographic(once)).isEqualTo(once);
        }

        // -- Empty string --

        @Test
        void testToTypographicEmptyStringPassesThrough() {
            assertThat(StringUtils.toTypographic("")).isEqualTo("");
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
