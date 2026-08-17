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

import java.awt.FontMetrics;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class StringUtils {

    // Finds all diacritics
    public static final Pattern DIACRITICS_PATTERN = Pattern.compile(
        "\\p{InCombiningDiacriticalMarks}+"
    );

    // Finds all non-alphanumeric characters, including characters with combining diacritics.
    // Used to convert to kebab case.
    private static final Pattern NOT_ALPHA_NUM_PATTERN = Pattern.compile(
        "[^\\p{L}\\p{M}\\p{N}]+"
    );

    // When converting to kebab case, "-+" is replaced with "-"
    private static final Pattern PLUS_MINUS_PATTERN = Pattern.compile("-+");

    // Finds multiple spaces not at the beginning of a line
    public static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile(
        "(?<=\\S) +"
    );

    public static final Pattern LF_PATTERN = Pattern.compile("\n");
    public static final Pattern TRIM_END_PATTERN = Pattern.compile("\\s+$");

    // Separates words when wrapping. Any whitespace run breaks a line, and the wrapped
    // result rejoins with single spaces, so how the words were spaced does not survive.
    private static final Pattern WHITESPACE_RUN_PATTERN = Pattern.compile("\\s+");

    // Stands for "no way to set these words in this many lines" while balancing. Never
    // added to, only compared against, so it needs no headroom.
    private static final long UNREACHABLE = Long.MAX_VALUE;

    // -- Typographic substitution (issue #436) --

    // Smart double quotes. Opening after whitespace / line start / a quote;
    // closing before whitespace / line end / closing punctuation; fallback = closing.
    //   "hi" → "hi"
    private static final Pattern OPENING_DOUBLE_QUOTE_PATTERN =
        Pattern.compile("(?<=\\s|^|\")\"", Pattern.MULTILINE);
    private static final Pattern CLOSING_DOUBLE_QUOTE_PATTERN =
        Pattern.compile("\"(?=\\s|$|[.,!?;:\"])", Pattern.MULTILINE);

    // Smart single quotes / apostrophes. Opening after whitespace / line start / a
    // quote; closing/apostrophe before whitespace / line end / closing punctuation
    // or an s/t/ll/ve/re contraction; fallback = apostrophe.
    //   'hello' → 'hello'    don't → don't    God's → God's
    // The lookbehind also accepts the curly left double quote (U+201C): the
    // double-quote pass runs first, so a single quote nested inside double quotes
    // ("'word'") sees a curly “ before it and must still open as ‘.
    private static final Pattern OPENING_SINGLE_QUOTE_PATTERN =
        Pattern.compile("(?<=\\s|^|\"|“)'", Pattern.MULTILINE);
    private static final Pattern CLOSING_SINGLE_QUOTE_PATTERN =
        Pattern.compile("'(?=\\s|$|[.,!?;:\"]|s\\b|t\\b|ll\\b|ve\\b|re\\b)", Pattern.MULTILINE);

    // Greedy collapse: two-or-more hyphens → one em dash; three-or-more dots → one ellipsis.
    //   -- / --- / ---- → —      ... / .... → …      (single "-" and spaced ". . ." untouched)
    private static final Pattern EM_DASH_PATTERN = Pattern.compile("-{2,}");
    private static final Pattern ELLIPSIS_PATTERN = Pattern.compile("\\.{3,}");

    private static final String OPENING_DOUBLE_QUOTE = "“";  // U+201C
    private static final String CLOSING_DOUBLE_QUOTE = "”";  // U+201D
    private static final String OPENING_SINGLE_QUOTE = "‘";  // U+2018
    private static final String CLOSING_SINGLE_QUOTE = "’";  // U+2019
    private static final String EM_DASH = "—";               // U+2014
    private static final String ELLIPSIS = "…";              // U+2026
    // Matches the short-A characters that are stripped when stripShortA is true.
    private static final Pattern SHORT_A_PATTERN = Pattern.compile("[ăĂ]");

    private StringUtils() {}

    public static String capitalizeSentence(String input) {
        if (input.isEmpty()) {
            return input;
        }

        return (
            input.substring(0, 1).toUpperCase() +
            input.substring(1).toLowerCase()
        );
    }

    public static String toKebabCase(String input) {
        if (input.isEmpty()) {
            return input;
        }

        var result = NOT_ALPHA_NUM_PATTERN.matcher(input).replaceAll("-");
        return PLUS_MINUS_PATTERN.matcher(result).replaceAll("-").toLowerCase();
    }

    public static String stripDiacritics(String str) {
        var normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }

    public static String stripLinefeeds(String str) {
        return LF_PATTERN.matcher(str).replaceAll(" ");
    }

    public static String trimEnd(String str) {
        return TRIM_END_PATTERN.matcher(str).replaceAll("");
    }

    public static String collapseMultipleSpaces(String str) {
        return MULTIPLE_SPACES_PATTERN.matcher(str).replaceAll(" ");
    }

    /**
     * Applies typographic substitution: straight quotes → curly, {@code --} runs →
     * em dash, {@code ...} runs → ellipsis. Pure substitution only — does not trim.
     * Idempotent: only straight quotes and runs of straight {@code -}/{@code .} are
     * matched, so already-curly text and existing em dashes/ellipses pass through.
     */
    public static String toTypographic(String text) {
        if (text.isEmpty()) {
            return text;
        }

        // No leading-apostrophe (elision) disambiguation: a corpus check found no
        // 'tis / '90s / rock 'n' roll, so the contextual rules below are exact for
        // this corpus; GIGO accepted for malformed input. (see plan §"Why no disambiguation")
        var result = OPENING_DOUBLE_QUOTE_PATTERN.matcher(text).replaceAll(OPENING_DOUBLE_QUOTE);
        result = CLOSING_DOUBLE_QUOTE_PATTERN.matcher(result).replaceAll(CLOSING_DOUBLE_QUOTE);
        result = result.replace("\"", CLOSING_DOUBLE_QUOTE);  // fallback

        result = OPENING_SINGLE_QUOTE_PATTERN.matcher(result).replaceAll(OPENING_SINGLE_QUOTE);
        result = CLOSING_SINGLE_QUOTE_PATTERN.matcher(result).replaceAll(CLOSING_SINGLE_QUOTE);
        result = result.replace("'", CLOSING_SINGLE_QUOTE);   // fallback

        result = EM_DASH_PATTERN.matcher(result).replaceAll(EM_DASH);
        result = ELLIPSIS_PATTERN.matcher(result).replaceAll(ELLIPSIS);

        return result;
    }

    /**
     * Wraps {@code text} into balanced lines that fit within {@code maxWidth}, breaking
     * only at whitespace.
     *
     * <p>Balanced means the fewest lines the width allows — the same count a greedy wrap
     * would use — divided so the lines come out as close to equal in length as those
     * breaks permit. This is the wrap a centered title wants: greedy alone fills each line
     * to the margin and leaves whatever is left over on the last one, which is how a title
     * ends up with a single trailing word under a full-width line.
     *
     * <p>Evenness is measured as the total squared slack — the sum, over every line
     * including the last, of the square of the width it leaves unused. Squaring is what
     * makes it a balance rather than a total: one line left far short costs more than
     * several left slightly short, so the minimum is the even split.
     *
     * @param text     the text to wrap; runs of whitespace separate words, and the result
     *                 carries single spaces between them however they were spaced here
     * @param metrics  the metrics of the font the text will be drawn in, which decide
     *                 where the breaks fall
     * @param maxWidth the width in pixels a line may occupy
     * @return the wrapped lines, in order, top to bottom
     * @invariant the result is never empty; text with no words yields one empty line
     * @invariant no line in the result is empty
     * @invariant a line exceeds {@code maxWidth} only when it holds a single word too wide
     * to fit, which is set on its own line rather than split
     * @invariant the result uses the fewest lines {@code maxWidth} allows, so balancing
     * never costs vertical space
     * @invariant among equally balanced splits the one with the longest first line wins,
     * so a centered title is top-heavy rather than bottom-heavy
     * @invariant joining the result with single spaces yields {@code text} stripped, with
     * its whitespace runs collapsed — no word is dropped, duplicated or reordered
     */
    public static List<String> wrapText(
        String text,
        FontMetrics metrics,
        int maxWidth
    ) {
        var stripped = text.strip();

        if (stripped.isEmpty()) {
            return List.of(stripped);
        }

        var widths = WordWidths.measure(stripped, metrics, maxWidth);
        var breaks = balancedBreaks(widths, widths.greedyLineCount());
        var lines = new ArrayList<String>(breaks.size());
        var from = 0;

        for (var to : breaks) {
            lines.add(String.join(" ", widths.words().subList(from, to)));
            from = to;
        }

        return lines;
    }

    /**
     * One wrapping run's words and their measured widths.
     *
     * <p>{@code widthUpTo[i]} is the width of the first {@code i} words set on one line,
     * each followed by a space, so any candidate line's width is a subtraction rather than
     * a re-measure — which is what lets the balancing pass consider every possible set of
     * breaks without measuring text more than once.
     */
    private record WordWidths(List<String> words, int[] widthUpTo, int spaceWidth, int maxWidth) {

        private static WordWidths measure(String strippedText, FontMetrics metrics, int maxWidth) {
            var words = List.of(WHITESPACE_RUN_PATTERN.split(strippedText));
            var spaceWidth = metrics.stringWidth(" ");
            var widthUpTo = new int[words.size() + 1];

            for (var i = 0; i < words.size(); i++) {
                widthUpTo[i + 1] = widthUpTo[i] + metrics.stringWidth(words.get(i)) + spaceWidth;
            }

            return new WordWidths(words, widthUpTo, spaceWidth, maxWidth);
        }

        private int count() {
            return words.size();
        }

        /**
         * The width of {@code words[from..to)} set on one line, with single spaces between
         * them and none trailing.
         */
        private int lineWidth(int from, int to) {
            return widthUpTo[to] - widthUpTo[from] - spaceWidth;
        }

        /**
         * Whether {@code words[from..to)} may occupy one line. A single word too wide to
         * fit still may, since the alternative is not setting it at all.
         */
        private boolean fits(int from, int to) {
            return to - from == 1 || lineWidth(from, to) <= maxWidth;
        }

        /**
         * The width this line leaves unused, floored at zero so an over-wide single word
         * counts as a full line rather than as a huge cost the balance would try to avoid.
         */
        private long slack(int from, int to) {
            return Math.max(0, maxWidth - lineWidth(from, to));
        }

        /**
         * The fewest lines these words fit in, found by filling each line as far as it
         * goes. Greedy is optimal for line <em>count</em> — it is only the distribution
         * across those lines that it gets wrong.
         */
        private int greedyLineCount() {
            var lineCount = 0;
            var from = 0;

            while (from < count()) {
                var to = from + 1;

                while (to < count() && fits(from, to + 1)) {
                    to++;
                }

                lineCount++;
                from = to;
            }

            return lineCount;
        }
    }

    /**
     * The word index each line ends at, for the way of setting every word in exactly
     * {@code lineCount} lines with the least total squared slack.
     *
     * <p>Works backwards: the best way to set the words from {@code i} onward in {@code k}
     * lines is the cheapest first line {@code words[i..j)} followed by the already-known
     * best way to set the rest in {@code k - 1}. Each (start, lines-left) pair is solved
     * once and reused, so the whole search costs no more than trying every break position
     * against every line count.
     *
     * @param widths    the run's measured words
     * @param lineCount the exact number of lines to use, which the caller takes from
     *                  {@link WordWidths#greedyLineCount()} so that balancing cannot add a
     *                  line
     * @return each line's exclusive end index, ascending, the last being the word count
     */
    private static List<Integer> balancedBreaks(WordWidths widths, int lineCount) {
        var wordCount = widths.count();

        // cost[k][i]: the least total squared slack for words[i..wordCount) set in exactly
        // k lines, and endOfLine[k][i] the first line's end index in that solution.
        var cost = new long[lineCount + 1][wordCount + 1];
        var endOfLine = new int[lineCount + 1][wordCount + 1];

        for (var row : cost) {
            Arrays.fill(row, UNREACHABLE);
        }

        // No words left and no lines left is the one way to have set everything.
        cost[0][wordCount] = 0;

        for (var k = 1; k <= lineCount; k++) {
            for (var i = wordCount - 1; i >= 0; i--) {
                // Widths grow with j, so once a candidate line no longer fits, no longer
                // one does.
                for (var j = i + 1; j <= wordCount && widths.fits(i, j); j++) {
                    var rest = cost[k - 1][j];

                    if (rest == UNREACHABLE) {
                        continue;
                    }

                    var slack = widths.slack(i, j);
                    var candidate = rest + slack * slack;

                    // Not-worse rather than better, so a tie goes to the longest first
                    // line: equally even splits are equally balanced, and a centered title
                    // reads best with its longer line on top.
                    if (candidate <= cost[k][i]) {
                        cost[k][i] = candidate;
                        endOfLine[k][i] = j;
                    }
                }
            }
        }

        // Reachable because lineCount came from a wrap that achieved it.
        var breaks = new ArrayList<Integer>(lineCount);
        var from = 0;

        for (var k = lineCount; k > 0; k--) {
            var to = endOfLine[k][from];
            breaks.add(to);
            from = to;
        }

        return breaks;
    }

    /**
     * Normalizes text through a fixed pipeline: trim, then typographic
     * substitution (smart quotes, em dash, ellipsis), then collapse runs of two
     * or more spaces to a single space, and — when {@code stripShortA} is
     * {@code true} — replace {@code ă}/{@code Ă} with {@code a}/{@code A}.
     * <p>
     * {@code stripShortA} is the sole gate for short-A stripping; the caller
     * decides whether to apply it (pass {@code true} for title/subtitle/
     * underlyrics; {@code false} for translation/footnotes and for the
     * place/composer/lyricist fields).
     * <p>
     * Idempotent, so song-model construction and the song settings dialog's
     * focus-lost field normalization can share this single copy.
     *
     * @param text       the raw text to normalize
     * @param stripShortA whether to replace {@code ă}/{@code Ă} with {@code a}/{@code A}
     */
    public static String processText(String text, boolean stripShortA) {
        var trimmed = text.trim();

        if (trimmed.isEmpty()) {
            return trimmed;
        }

        var result = collapseMultipleSpaces(toTypographic(trimmed));

        if (stripShortA && SHORT_A_PATTERN.matcher(result).find()) {
            result = result.replace("ă", "a").replace("Ă", "A");
        }

        return result;
    }
}
