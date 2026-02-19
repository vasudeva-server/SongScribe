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

import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.Constants;

/**
 * Processes lyrics text and assigns syllables to notes.
 *
 * This class handles parsing lyrics strings and mapping syllables to musical notes,
 * respecting syllable continuation markers (hyphens) and extenders (underscores).
 */
public class LyricsProcessor {

    private static final Logger LOG = Logger.getLogger(LyricsProcessor.class.getName());

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
        line.beginRelation = Note.SyllableRelation.NO;

        for (var n = 0; n < line.noteCount(); n++) {
            var note = line.getNote(n);
            note.properties.syllable = "";
            note.properties.syllableRelation = Note.SyllableRelation.NO;
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
            line.beginRelation = Note.SyllableRelation.ONE_DASH;
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
                Note.SyllableRelation syllableRelation;

                if ((c == '\n') || (c == ' ')) {
                    syllableRelation = Note.SyllableRelation.NO;
                    noteIndex = setSyllableForNextNote(
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
                        syllableRelation = Note.SyllableRelation.ONE_DASH;
                        i++;
                    } else {
                        syllableRelation = Note.SyllableRelation.ONE_DASH;
                    }

                    noteIndex = setSyllableForNextNote(
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
                    syllableRelation = Note.SyllableRelation.EXTENDER;

                    if (i > 0) {
                        noteIndex = setSyllableForNextNote(
                            line,
                            noteIndex,
                            syllable,
                            syllableRelation
                        );
                    } else {
                        line.beginRelation = syllableRelation;
                    }
                }

                if (noteIndex >= line.noteCount()) {
                    break;
                }

                begin = i + 1;
            }
        }

        LOG.fine("Line: " + composition.indexOfLine(line));
        LOG.fine("BeginRelation: " + line.beginRelation);

        for (var i = 0; i < line.noteCount(); i++) {
            LOG.fine(
                line.getNote(i).properties.syllable +
                    "   Relation: " +
                    line.getNote(i).properties.syllableRelation.name()
            );
        }
    }

    private static int setSyllableForNextNote(
        @NotNull Line line,
        int noteIndex,
        String syllable,
        Note.SyllableRelation syllableRelation
    ) {
        var index = noteIndex;

        while (
            (index < line.noteCount()) &&
                !line.getNote(index).getNoteType().isNote() &&
                !line.getNote(index).isForceSyllable()
        ) {
            index++;
        }

        if (index < line.noteCount()) {
            line.getNote(index).properties.syllable = syllable;
            line.getNote(index).properties.syllableRelation =
                syllableRelation;
        }

        return index + 1;
    }
}
