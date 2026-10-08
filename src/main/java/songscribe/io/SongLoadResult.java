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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import songscribe.dom.ChangeReport;
import songscribe.dom.ParsedSong;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;

public sealed interface SongLoadResult permits SongLoadResult.Success, SongLoadResult.Failure {

    /**
     * A successfully loaded song, plus everything the loader wants to tell the
     * caller about how it got there.
     *
     * @param song     the loaded song
     * @param fonts    the document's fonts
     * @param warnings every non-fatal problem detected during the load, in the
     *                 order it was detected; empty when the file was clean
     * @param report   what the load changed in the document, as {@link ParsedSong#settle()}
     *                 reports it; empty when it changed nothing
     */
    record Success(
        Song song, DocumentFonts fonts, List<LoadWarning> warnings, ChangeReport report
    ) implements SongLoadResult {

        // Defensive copy — the record's contract is immutability, and both readers
        // build the warning list incrementally before handing it over.
        public Success {
            warnings = List.copyOf(warnings);
        }

        /**
         * Settles what a reader read and returns the loaded song. Both readers end here, so the
         * load passes run on every route a file is opened by — the UI's and the headless MIDI
         * export's alike — and a song cannot play tuplets one route dropped.
         *
         * @param parsed   what the reader handed over, not yet settled
         * @param fonts    the document's fonts
         * @param warnings every non-fatal problem the reader detected, in the order it was
         *                 detected
         * @return the loaded {@code parsed.song()}, with the report {@link ParsedSong#settle()}
         *     returns
         * @effects runs {@link ParsedSong#settle()} on {@code parsed}, with the effects it states
         */
        public static Success settled(ParsedSong parsed, DocumentFonts fonts, List<LoadWarning> warnings) {
            var report = parsed.settle();

            return new Success(parsed.song(), fonts, warnings, report);
        }
    }

    sealed interface Failure extends SongLoadResult permits
            SongLoadResult.IoError,
            SongLoadResult.ParseError,
            SongLoadResult.NewerVersion,
            SongLoadResult.LineWidthTooLarge,
            SongLoadResult.WrongSoftware,
            SongLoadResult.UnsupportedFileFormat,
            SongLoadResult.InvalidFileFormat,
            SongLoadResult.UnsupportedVersion {
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
            return "File was not created by SongScribe (software: " + software + ')';
        }
    }

    /**
     * The result for a file whose extension SongScribe does not open at all.
     */
    record UnsupportedFileFormat(File file, String extension) implements Failure {

        public static String message(String extension) {
            return "Unsupported file type: " + extension;
        }
    }

    /**
     * The result for a {@code .musicxml}/{@code .xml} document that cannot be read as this
     * program's format: either it breaks the MusicXML schema SongScribe implements, or it
     * states, in valid MusicXML, something SongScribe cannot represent.
     *
     * @param detail the specific reason, as the reader's throw site stated it
     */
    record InvalidFileFormat(File file, String detail) implements Failure {

        public static String message(String detail) {
            return "Invalid file format: " + detail;
        }
    }

    /**
     * The file names SongScribe as its writer but carries a version
     * {@link songscribe.io.musicxml.SoftwareProvenance#check} refuses, either because
     * it is not semver or because it is below the minimum supported version.
     *
     * @param version the offending version string, so the log can name it
     */
    record UnsupportedVersion(File file, String version) implements Failure {}

    default Song songOrThrow() throws IOException, SAXException, SongIO.NewerVersionException {
        return switch (this) {
            case Success s -> s.song();
            case IoError e -> throw e.cause();
            case ParseError e -> throw e.cause();
            case NewerVersion e -> throw e.cause();
            case LineWidthTooLarge e -> throw new IOException(
                "Line width " + e.actualInches() + " inches exceeds maximum " + e.maxInches() + " inches");
            case WrongSoftware e -> throw new IOException(WrongSoftware.message(e.software()));
            case UnsupportedFileFormat e -> throw new IOException(UnsupportedFileFormat.message(e.extension()));
            case InvalidFileFormat e -> throw new IOException(InvalidFileFormat.message(e.detail()));
            case UnsupportedVersion e -> throw new IOException(
                "Unsupported SongScribe version: " + e.version());
        };
    }
}
