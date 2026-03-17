/*
 * This file is part of SongScribe.
 *
 * SongScribe is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SongScribe.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2024 Aparajita
 */

package songscribe.music;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.Constants;

/**
 * Processes lyrics text and assigns syllables to notes.
 *
 * This class handles parsing lyrics strings and mapping syllables to musical notes,
 * respecting syllable continuation markers (hyphens) and extenders (underscores).
 */
public class LyricsProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(LyricsProcessor.class);

    private LyricsProcessor() {
        // Utility class
    }

    /**
     * Processes lyrics for all lines in a composition.
     *
     * @param composition The composition containing lines to process
     */
    public static void spellLyrics(@NotNull Composition composition) {
        for (var l = 0; l < composition.lineCount(); l++) {
            spellLyrics(composition.getLine(l));
        }
    }

    /**
     * Processes lyrics for a single line, parsing the lyrics string and assigning syllables to notes.
     *
     * @param line The line to process
     */
    public static void spellLyrics(@NotNull Line line) {
        // delete the current values
        line.beginRelation = StaffElement.SyllableRelation.NO;

        for (var n = 0; n < line.elementCount(); n++) {
            var note = line.getElement(n);
            note.properties.syllable = "";
            note.properties.syllableRelation = StaffElement.SyllableRelation.NO;
        }

        // get the lyrics slice
        var beginIndex = 0;
        var composition = line.getComposition();

        for (var j = composition.indexOfLine(line); j > 0; j--) {
            beginIndex = composition.getLyrics().indexOf('\n', beginIndex) + 1;
            if (beginIndex == 0) {
                return;
            }
        }

        var endIndex = composition.getLyrics().indexOf('\n', beginIndex);

        if (endIndex == -1) {
            endIndex = composition.getLyrics().length();
        }

        if (beginIndex == endIndex) {
            return;
        }

        var lyrics =
            composition.getLyrics().substring(beginIndex, endIndex) + '\n';

        // calculate the begin relations
        if (lyrics.startsWith("--")) {
            line.beginRelation = StaffElement.SyllableRelation.ONE_DASH;
            lyrics = lyrics.substring(2);
            beginIndex += 2;
        }

        // make the lyrics
        var begin = 0;
        var noteIndex = 0;

        for (var i = 0; i < lyrics.length(); i++) {
            var c = lyrics.charAt(i);

            if ((c == '\n') || (c == ' ') || (c == '-') || (c == '_')) { //word end
                var syllable = (begin < i)
                    ? lyrics.substring(begin, i)
                    : Constants.UNDERSCORE;
                StaffElement.SyllableRelation syllableRelation;

                if ((c == '\n') || (c == ' ')) {
                    syllableRelation = StaffElement.SyllableRelation.NO;
                    noteIndex = setSyllableForNextElement(
                        line,
                        noteIndex,
                        syllable,
                        syllableRelation
                    );
                } else if (c == '-') {
                    // Gould/Ross: hyphen = syllable division only (always single hyphen)
                    if (
                        (lyrics.charAt(i + 1) == '-') ||
                            (lyrics.charAt(i + 1) == '\n')
                    ) {
                        syllableRelation = StaffElement.SyllableRelation.ONE_DASH;
                        i++;
                    } else {
                        syllableRelation = StaffElement.SyllableRelation.ONE_DASH;
                    }

                    noteIndex = setSyllableForNextElement(
                        line,
                        noteIndex,
                        syllable,
                        syllableRelation
                    );
                } else { // c == '_'
                    // Gould/Ross: underscore = duration only (always extender line)
                    var eus = beginIndex + i + 1;

                    while (
                        ((eus < composition.getLyrics().length()) &&
                            (composition.getLyrics().charAt(eus) == '_')) ||
                            (((eus + 1) < composition.getLyrics().length()) &&
                                (composition.getLyrics().charAt(eus) == '\n') &&
                                (composition.getLyrics().charAt(eus + 1) == '_'))
                    ) {
                        eus++;
                    }

                    // Always use EXTENDER for underscores (duration indication)
                    syllableRelation = StaffElement.SyllableRelation.EXTENDER;

                    if (i > 0) {
                        noteIndex = setSyllableForNextElement(
                            line,
                            noteIndex,
                            syllable,
                            syllableRelation
                        );
                    } else {
                        line.beginRelation = syllableRelation;
                    }
                }

                if (noteIndex >= line.elementCount()) {
                    break;
                }

                begin = i + 1;
            }
        }

    }

    private static int setSyllableForNextElement(
        @NotNull Line line,
        int noteIndex,
        String syllable,
        StaffElement.SyllableRelation syllableRelation
    ) {
        var index = noteIndex;

        while (
            (index < line.elementCount()) &&
                !line.getElement(index).getType().isNote() &&
                !line.getElement(index).isForceSyllable()
        ) {
            index++;
        }

        if (index < line.elementCount()) {
            line.getElement(index).properties.syllable = syllable;
            line.getElement(index).properties.syllableRelation =
                syllableRelation;
        }

        return index + 1;
    }
}
