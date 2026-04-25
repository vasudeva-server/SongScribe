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

package songscribe.io;

import java.util.List;

import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.StaffElement;

/**
 * Populates per-note {@code Properties.lyrics} records from a legacy
 * {@code <lyrics>} blob.
 *
 * <p>The blob is a song-level, newline-delimited string where each line
 * corresponds to one {@link Line}. Within each line, whitespace- and delimiter-separated
 * tokens are assigned to successive elements:
 *
 * <ul>
 *   <li>{@code word} → {@code Lyric(NONE, word, extend=false)}</li>
 *   <li>{@code word-} → {@code Lyric(SYLLABLE, word, extend=false)}</li>
 *   <li>{@code word--} or {@code word=} → {@code Lyric(COMPOUND_WORD, word, extend=false)}</li>
 *   <li>{@code word_+} → {@code Lyric(<next boundary>, word, extend=true)} plus one
 *       continuation element (no {@link Lyric} record) per trailing {@code _}</li>
 * </ul>
 *
 * <p>A {@code _} immediately followed by a word character (with no intervening space
 * or relation marker) is absorbed into that word's position rather than producing
 * a standalone continuation. This mirrors the legacy convention where
 * {@code heart_ _ _garden} across 4 notes placed "heart" on note 1 and "garden"
 * on note 4, with two continuation notes between them.
 *
 * <p>A line starting with {@code --} (legacy mid-word continuation marker) has
 * those two characters stripped before tokenizing; cross-line continuation is
 * expressed in the new model by the preceding line's last {@link Lyric} having
 * a non-{@code NONE} relation.
 */
public final class LegacyLyricsImporter {

    private LegacyLyricsImporter() {
    }

    /**
     * Walks {@code lyricsBlock} left-to-right and populates
     * {@code Properties.lyrics} on each element of {@code lines}.
     */
    public static void importLegacyLyrics(List<Line> lines, String lyricsBlock) {
        if (lyricsBlock.isBlank()) {
            return;
        }

        var tokenLines = lyricsBlock.split("\n", -1);
        var lineCount = Math.min(lines.size(), tokenLines.length);

        for (var i = 0; i < lineCount; i++) {
            importLine(lines.get(i), tokenLines[i]);
        }
    }

    private static void importLine(Line line, String text) {
        // Strip legacy mid-word continuation marker.
        if (text.startsWith("--")) {
            text = text.substring(2);
        }

        var elementCount = line.effectiveElementCount();
        var elementIdx = 0;
        var i = 0;
        var n = text.length();

        while (i < n && elementIdx < elementCount) {
            var c = text.charAt(i);

            if (c == ' ' || c == '\t') {
                i++;
                continue;
            }

            if (c == '_') {
                // Standalone underscore run (no preceding word in this token).
                var runStart = i;

                while (i < n && text.charAt(i) == '_') {
                    i++;
                }

                var runLen = i - runStart;

                // A trailing `_` that directly abuts a word char is absorbed
                // into that word's position (it does not produce its own
                // continuation element).
                if (i < n && isWordChar(text.charAt(i)) && runLen > 0) {
                    runLen--;
                }

                elementIdx += runLen;
                continue;
            }

            if (c == '-' || c == '=') {
                // Stray relation marker without a preceding word; skip.
                if (c == '-' && i + 1 < n && text.charAt(i + 1) == '-') {
                    i += 2;
                } else {
                    i++;
                }

                continue;
            }

            // Read word characters.
            var wordStart = i;

            while (i < n && isWordChar(text.charAt(i))) {
                i++;
            }

            var word = text.substring(wordStart, i);

            // Read trailing underscore run.
            var extend = Lyric.Extend.NONE;
            var runStart = i;

            while (i < n && text.charAt(i) == '_') {
                extend = Lyric.Extend.START;
                i++;
            }

            var extenderRun = i - runStart;

            // Each `_` in the trailing run = 1 continuation element, except
            // a `_` immediately followed by a word char is absorbed into
            // that word's position. This mirrors the legacy convention for
            // inputs like `heart_ _ _garden` (the final `_garden` token).
            var continuations = extenderRun;

            if (continuations > 0 && i < n && isWordChar(text.charAt(i))) {
                continuations--;
            }

            // Determine relation from the next character.
            var relation = StaffElement.SyllableRelation.NONE;

            if (i < n) {
                var next = text.charAt(i);

                if (next == '=') {
                    relation = StaffElement.SyllableRelation.COMPOUND_WORD;
                    i++;
                } else if (next == '-') {
                    if (i + 1 < n && text.charAt(i + 1) == '-') {
                        relation = StaffElement.SyllableRelation.COMPOUND_WORD;
                        i += 2;
                    } else {
                        relation = StaffElement.SyllableRelation.SYLLABLE;
                        i++;
                    }
                }
            }

            line.getElement(elementIdx).properties.lyrics.add(
                new Lyric(1, relation, word, extend));
            elementIdx++;
            elementIdx += continuations;
        }
    }

    private static boolean isWordChar(char c) {
        return c != ' ' && c != '\t' && c != '_' && c != '-' && c != '=' && c != '\n';
    }
}
