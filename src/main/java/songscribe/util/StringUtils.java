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

import module java.desktop;

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
}
