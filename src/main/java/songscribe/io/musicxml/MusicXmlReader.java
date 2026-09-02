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
package songscribe.io.musicxml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.io.SongLoadResult;

/**
 * Reads a MusicXML 4.0 document produced by {@link MusicXmlWriter} back into a
 * {@link SongLoadResult.Success}.
 * <p>
 * This is not a general MusicXML importer — it only handles SongScribe's own output.
 * <p>
 * Parses via {@link MusicXmlSerializer#unmarshal} into a
 * {@link org.audiveris.proxymusic.ScorePartwise} object graph, then hands that graph to
 * {@link SongMapper#map} to build the {@link songscribe.dom.Song}. {@link #read(File)} is a
 * one-line delegation to {@link #read(InputSource)}, so there is exactly one parse path.
 * <p>
 * The two exceptions below are thrown by the mapping classes ({@link SongMapper},
 * {@link HeaderMapper}, {@link ProxyMusicAccess}, {@link NoteMapper}) rather than by this
 * class directly; they are nested here because {@link songscribe.io.SongFileLoader} and
 * callers throughout the codebase catch them by this class's name.
 */
public final class MusicXmlReader {

    private MusicXmlReader() {}

    /**
     * Parses a MusicXML document from the given {@link InputSource} and returns
     * the resulting {@link SongLoadResult.Success} (the parsed song plus its
     * document fonts). A malformed document surfaces as a thrown exception; the
     * warning list carries only the non-fatal conditions, of which the tuplet
     * load pass is currently the sole producer on this path.
     * <p>
     * When the document carries no {@code <defaults>} font block, the result's
     * fonts default to {@link songscribe.font.DocumentFonts#defaultFonts()}.
     *
     * @param source the MusicXML input to parse
     * @return the parsed song plus its document fonts
     * @throws IOException  on I/O errors
     * @throws SAXException on parse errors
     */
    public static SongLoadResult.Success read(InputSource source) throws IOException, SAXException {
        return SongMapper.map(MusicXmlSerializer.unmarshal(source));
    }

    /**
     * Parses a MusicXML document from the given {@link File} and returns the
     * resulting {@link SongLoadResult.Success}.
     *
     * <p>The file is opened here, not left to the parser. A parse source built from a
     * location alone is opened lazily, so a path that does not exist would surface
     * mid-parse as a parse error; {@link songscribe.io.SongFileLoader} maps the two to
     * different results, so the distinction is user-visible — "this file is not there"
     * against "this file is damaged". Opening it here is also what closes it: the parser
     * never closes a stream it was handed.
     *
     * @param file the MusicXML file to parse
     * @return the parsed song plus its document fonts
     * @throws IOException  on I/O errors
     * @throws SAXException on parse errors
     */
    public static SongLoadResult.Success read(File file) throws IOException, SAXException {
        try (var stream = new FileInputStream(file)) {
            var source = new InputSource(stream);
            // The location travels with the stream so the parser can still resolve
            // anything the document states relative to the file.
            source.setSystemId(file.toURI().toString());
            return read(source);
        }
    }

    /**
     * Thrown when the document's {@code <software>} provenance tag is missing,
     * blank, or does not identify SongScribe.
     */
    public static final class ForeignSoftwareException extends SAXException {

        @Nullable
        private final String software;

        ForeignSoftwareException(@Nullable String software) {
            super(SongLoadResult.WrongSoftware.message(software));
            this.software = software;
        }

        @Nullable
        public String software() {
            return software;
        }
    }

    /**
     * Thrown when a document cannot be read as this program's format: the root element
     * is not {@code <score-partwise>}; its {@code version} is missing, unparseable, or
     * older than {@link MusicXmlTags#VERSION_VALUE}; its {@code <software>} names this
     * program but carries a version that is not semver, or one older than
     * {@link SoftwareProvenance#MIN_VERSION}; a required element the mappers
     * cannot proceed without is absent (see {@link ProxyMusicAccess#require}); or a
     * token names something no model value maps to.
     *
     * <p>{@link #detail()} carries the specific reason, which
     * {@code SongFileLoader.load} surfaces to the user via
     * {@code SongLoadResult.UnsupportedFileFormat}, so each throw site states a
     * distinct one.
     */
    public static final class UnsupportedFormatException extends SAXException {

        private final String detail;

        UnsupportedFormatException(String detail) {
            super("Unsupported MusicXML format: " + detail);
            this.detail = detail;
        }

        public String detail() {
            return detail;
        }
    }
}
