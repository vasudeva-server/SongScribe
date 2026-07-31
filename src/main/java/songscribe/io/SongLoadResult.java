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
package songscribe.io;

import songscribe.dom.Song;
import songscribe.dom.TupletLoadPass;
import songscribe.font.DocumentFonts;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

public sealed interface SongLoadResult permits SongLoadResult.Success, SongLoadResult.Failure {

    /**
     * A successfully loaded song, plus everything the loader wants to tell the
     * caller about how it got there.
     *
     * @param song                 the loaded song
     * @param fonts                the document's fonts
     * @param warnings             every non-fatal problem detected during the load,
     *                             in the order it was detected; empty when the file
     *                             was clean
     * @param accidentalsConverted whether retired accidentals were converted
     * @param tupletReport         every tuplet the post-parse pass removed or
     *                             completed, in document order; empty when the
     *                             file's tuplets were taken as they stood
     */
    record Success(Song song, DocumentFonts fonts, List<LoadWarning> warnings,
                   boolean accidentalsConverted, TupletLoadPass.Report tupletReport)
            implements SongLoadResult {

        // Defensive copy — the record's contract is immutability, and both readers
        // build the warning list incrementally before handing it over.
        public Success {
            warnings = List.copyOf(warnings);
        }

        /**
         * Convenience factory for the common case: no warnings, no retired
         * accidental conversion, no tuplet migration.
         */
        public static Success of(Song song, DocumentFonts fonts) {
            return new Success(song, fonts, List.of(), false, TupletLoadPass.Report.empty());
        }
    }

    sealed interface Failure extends SongLoadResult permits
            SongLoadResult.IoError,
            SongLoadResult.ParseError,
            SongLoadResult.NewerVersion,
            SongLoadResult.LineWidthTooLarge,
            SongLoadResult.WrongSoftware,
            SongLoadResult.UnsupportedFileFormat {
        File file();
    }

    record IoError(File file, IOException cause) implements Failure {}

    record ParseError(File file, SAXException cause) implements Failure {}

    record NewerVersion(File file, SongIO.NewerVersionException cause) implements Failure {}

    record LineWidthTooLarge(File file, double actualInches, double maxInches) implements Failure {}

    record WrongSoftware(File file, @Nullable String software) implements Failure {

        // Single source of truth for this failure's message, shared with the
        // reader exception that produces it, so the wording cannot drift.
        public static String message(@Nullable String software) {
            return "File was not created by SongScribe (software: " + software + ")";
        }
    }

    record UnsupportedFileFormat(File file, @Nullable String detail) implements Failure {

        public static String message(@Nullable String detail) {
            return "Unsupported file format: " + detail;
        }
    }

    default Song songOrThrow() throws IOException, SAXException {
        return switch (this) {
            case Success s -> s.song();
            case IoError e -> throw e.cause();
            case ParseError e -> throw e.cause();
            case NewerVersion e -> throw e.cause();
            case LineWidthTooLarge e -> throw new IOException(
                "Line width " + e.actualInches() + " inches exceeds maximum " + e.maxInches() + " inches");
            case WrongSoftware e -> throw new IOException(WrongSoftware.message(e.software()));
            case UnsupportedFileFormat e -> throw new IOException(UnsupportedFileFormat.message(e.detail()));
        };
    }
}
