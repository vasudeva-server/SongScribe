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
import java.util.stream.Collectors;

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

    // When wrapping text, we want each line to have at least this many words
    public static final int MINIMUM_WRAPPED_WORD_COUNT = 3;

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

    public static List<String> wrapText(
        String text,
        FontMetrics metrics,
        int maxWidth
    ) {
        // Wrap the text into lines that fit within the maximum width
        var lines = new ArrayList<ArrayList<String>>();
        var words = Arrays.asList(MULTIPLE_SPACES_PATTERN.split(text));
        var wordCount = words.size();
        var currentLine = new ArrayList<String>();
        var spaceWidth = metrics.stringWidth(" ");

        // Iterate through all of the words in the text, starting from the end of the previous line
        var start = 0;

        while (start < wordCount) {
            var end = start;
            var lineWidth = 0;

            for (; end < wordCount; end++) {
                var word = words.get(end);
                var extraWidth = (end > start) ? spaceWidth : 0;
                var newWidth = lineWidth + metrics.stringWidth(word) + extraWidth;

                // Stop adding words once the line is too wide, but always keep at
                // least one word per line so the loop makes progress even when a
                // single word is wider than maxWidth (e.g. maxWidth == 0).
                if (newWidth > maxWidth && end > start) {
                    break;
                }

                lineWidth = newWidth;
                currentLine.add(word);
            }

            if (!currentLine.isEmpty()) {
                lines.add(new ArrayList<>(currentLine));
                currentLine.clear();
            }

            start = end;
        }

        // If there is more than one line, go through the lines from the list to the first,
        // and move words from earlier lines if the line has less than the minimum word count.
        for (var i = lines.size() - 1; i > 0; i--) {
            var line = lines.get(i);
            var lineSize = line.size();

            if (lineSize < MINIMUM_WRAPPED_WORD_COUNT) {
                var previousLine = lines.get(i - 1);
                var previousLineSize = previousLine.size();
                var moveCount = MINIMUM_WRAPPED_WORD_COUNT - lineSize;

                if (previousLineSize >= moveCount) {
                    var wordsToMove = previousLine.subList(
                        previousLineSize - moveCount,
                        previousLineSize
                    );
                    line.addAll(0, wordsToMove);
                    previousLine.removeAll(wordsToMove);
                }
            }
        }

        return lines
            .stream()
            .map(line -> String.join(" ", line))
            .collect(Collectors.toCollection(ArrayList::new));
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
