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

    /** What {@link String#indexOf} and {@link String#lastIndexOf} answer when the needle is absent. */
    public static final int INDEX_NOT_FOUND = -1;

    public static final Pattern DIACRITICS_PATTERN = Pattern.compile(
        "\\p{InCombiningDiacriticalMarks}+"
    );

    private static final Pattern NOT_ALPHA_NUM_PATTERN = Pattern.compile(
        "[^\\p{L}\\p{M}\\p{N}]+"
    );

    private static final Pattern PLUS_MINUS_PATTERN = Pattern.compile("-+");

    public static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile(
        "(?<=\\S) +"
    );

    public static final Pattern LF_PATTERN = Pattern.compile("\n");

    private static final Pattern FOREIGN_LINE_ENDING_PATTERN = Pattern.compile("\r\n?");

    public static final Pattern TRIM_END_PATTERN = Pattern.compile("\\s+$");

    // Separates words when wrapping. Any whitespace run breaks a line, and the wrapped
    // result rejoins with single spaces, so how the words were spaced does not survive.
    private static final Pattern WHITESPACE_RUN_PATTERN = Pattern.compile("\\s+");

    // Stands for "no way to set these words in this many lines" while balancing. Never
    // added to, only compared against, so it needs no headroom.
    private static final long UNREACHABLE = Long.MAX_VALUE;

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

    /**
     * Returns {@code text} with its line endings normalized and reduced to at most
     * {@code maxLines} lines, so text of any shape can be held in a field offering a fixed
     * number of rows.
     *
     * <p>The budget is spent on lines that hold something. A blank line is an artifact of
     * where the text came from rather than a line anyone asked for, so it is dropped
     * instead of taking a line — which is what keeps a title pasted with a leading empty
     * line from arriving as one line instead of two. The lines that survive keep their
     * order; those past the budget are joined onto the last one with single spaces.
     *
     * <p><strong>A trailing blank line is the exception, and is kept while the budget has
     * room for it.</strong> Pressing Enter at the end of a line leaves the text ending in a
     * break, and that empty last row is the one the caret is now sitting on. Dropping it
     * would delete the break the instant it was typed and make the next line unreachable.
     * Committing that text is what drops it; see {@link songscribe.dom.SongMetadata}.
     *
     * @param text     the text to fold
     * @param maxLines the number of lines the result may occupy; at least 1
     * @return the folded text
     * @invariant the result holds at most {@code maxLines - 1} line breaks
     * @invariant no carriage return survives
     * @invariant no line of the result is blank, except a trailing one carried over from
     * {@code text} ending in a line break
     * @invariant word order and content are unchanged; apart from line endings being
     * normalized, the only difference is a surplus break becoming a space
     * @invariant idempotent: folding an already-folded text returns it unchanged
     */
    public static String foldSurplusLineBreaks(String text, int maxLines) {
        var normalized = normalizeLineEndings(text);
        var lines = new ArrayList<String>();

        for (var segment : LF_PATTERN.split(normalized, -1)) {
            if (!segment.isBlank()) {
                lines.add(segment);
            }
        }

        // The caret's own row when the text ends in a break, so it is not an artifact.
        if (normalized.endsWith("\n") && lines.size() < maxLines) {
            lines.add("");
        }

        if (lines.size() <= maxLines) {
            return String.join("\n", lines);
        }

        var kept = new ArrayList<>(lines.subList(0, maxLines - 1));
        kept.add(String.join(" ", lines.subList(maxLines - 1, lines.size())));

        return String.join("\n", kept);
    }

    public static String normalizeLineEndings(String text) {
        return FOREIGN_LINE_ENDING_PATTERN.matcher(text).replaceAll("\n");
    }

    public static String trimEnd(String str) {
        return TRIM_END_PATTERN.matcher(str).replaceAll("");
    }

    public static String collapseMultipleSpaces(String str) {
        return MULTIPLE_SPACES_PATTERN.matcher(str).replaceAll(" ");
    }

    /** Does not trim. Idempotent. */
    public static String toTypographic(String text) {
        if (text.isEmpty()) {
            return text;
        }

        // Leading-apostrophe elisions ('tis, '90s) are not disambiguated: the contextual rules
        // below are exact for well-formed input, and malformed input gets whatever they produce.
        var result = OPENING_DOUBLE_QUOTE_PATTERN.matcher(text).replaceAll(OPENING_DOUBLE_QUOTE);
        result = CLOSING_DOUBLE_QUOTE_PATTERN.matcher(result).replaceAll(CLOSING_DOUBLE_QUOTE);
        result = result.replace("\"", CLOSING_DOUBLE_QUOTE);

        result = OPENING_SINGLE_QUOTE_PATTERN.matcher(result).replaceAll(OPENING_SINGLE_QUOTE);
        result = CLOSING_SINGLE_QUOTE_PATTERN.matcher(result).replaceAll(CLOSING_SINGLE_QUOTE);
        result = result.replace("'", CLOSING_SINGLE_QUOTE);

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
     * <p>A line break in {@code text} is honored rather than reflowed: it divides the text
     * into segments, each of which is balance-wrapped on its own and set below the one
     * before it. A segment holding no words contributes no line, so an author who breaks
     * twice in a row does not get a blank line out of it.
     *
     * @param text     the text to wrap; runs of whitespace separate words, and the result
     *                 carries single spaces between them however they were spaced here,
     *                 while a line feed ends the line it falls on
     * @param metrics  the metrics of the font the text will be drawn in, which decide
     *                 where the breaks fall
     * @param maxWidth the width in pixels a line may occupy
     * @return the wrapped lines, in order, top to bottom
     * @invariant the result is never empty; text with no words yields one empty line
     * @invariant no line in the result is empty, except the single empty line yielded by
     * text with no words
     * @invariant a line feed in {@code text} ends a line of the result, unless the stretch
     * it closes holds no words, in which case it contributes no line. A carriage return is
     * ordinary whitespace here and ends nothing
     * @invariant a line exceeds {@code maxWidth} only when it holds a single word too wide
     * to fit, which is set on its own line rather than split
     * @invariant each segment uses the fewest lines {@code maxWidth} allows, so balancing
     * never costs vertical space
     * @invariant among equally balanced splits of a segment the one with the longest first
     * line wins, so a centered title is top-heavy rather than bottom-heavy
     * @invariant joining the result with single spaces yields {@code text} with each line
     * break replaced by a space, stripped, with its whitespace runs collapsed — no word is
     * dropped, duplicated or reordered
     */
    public static List<String> wrapText(
        String text,
        FontMetrics metrics,
        int maxWidth
    ) {
        var lines = new ArrayList<String>();

        for (var segment : LF_PATTERN.split(text, -1)) {
            lines.addAll(wrapSegment(segment, metrics, maxWidth));
        }

        if (lines.isEmpty()) {
            return List.of("");
        }

        return lines;
    }

    private static List<String> wrapSegment(
        String segment,
        FontMetrics metrics,
        int maxWidth
    ) {
        var stripped = segment.strip();

        if (stripped.isEmpty()) {
            return List.of();
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
     * {@code widthUpTo[i]} is the width of the first {@code i} words on one line, each followed
     * by a space.
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

        private int lineWidth(int from, int to) {
            return widthUpTo[to] - widthUpTo[from] - spaceWidth;
        }

        /** A single word too wide to fit still occupies one line. */
        private boolean fits(int from, int to) {
            return to - from == 1 || lineWidth(from, to) <= maxWidth;
        }

        /** Floored at zero so an over-wide single word counts as a full line. */
        private long slack(int from, int to) {
            return Math.max(0, maxWidth - lineWidth(from, to));
        }

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
     * What {@link #processText} does with a short A ({@code ă}, {@code Ă}).
     */
    public enum ShortA {
        STRIP,
        KEEP
    }

    /**
     * Cleans the text of one field: trims it, collapses each run of spaces after a character
     * that is not whitespace to one space, makes it typographic as {@link #toTypographic} does,
     * and under {@link ShortA#STRIP} replaces each {@code ă} with {@code a} and each {@code Ă}
     * with {@code A}.
     *
     * <p>Idempotent because the song model and the song settings dialog both apply it.
     *
     * @invariant idempotent: {@code processText(processText(text, shortA), shortA)} equals
     *     {@code processText(text, shortA)}
     * @invariant under {@link ShortA#STRIP} the result holds no {@code ă} and no {@code Ă}
     */
    public static String processText(String text, ShortA shortA) {
        var trimmed = text.trim();

        if (trimmed.isEmpty()) {
            return trimmed;
        }

        var result = collapseMultipleSpaces(toTypographic(trimmed));

        if (shortA == ShortA.STRIP && SHORT_A_PATTERN.matcher(result).find()) {
            result = result.replace("ă", "a").replace("Ă", "A");
        }

        return result;
    }
}
