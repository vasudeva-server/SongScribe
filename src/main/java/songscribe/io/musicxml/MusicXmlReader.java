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
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.font.DocumentFonts;
import songscribe.io.SongLoadResult;

/**
 * Reads a MusicXML 4.0 document produced by {@link MusicXmlWriter} back into a
 * {@link SongLoadResult.Success}.
 *
 * <p>This is not a general MusicXML importer — it only handles SongScribe's own output.
 *
 * <p>The three exceptions below are thrown by the mapping classes, not by this class; they are
 * nested here because callers catch them by this class's name.
 */
public final class MusicXmlReader {

    private MusicXmlReader() {}

    /**
     * The warning list is empty on this path; what the load changed is in the result's report.
     * When the document carries no {@code <defaults>} font block, the result's fonts default to
     * {@link songscribe.font.DocumentFonts#defaultFonts()}.
     */
    public static SongLoadResult.Success read(InputSource source) throws IOException, SAXException {
        var documentFonts = DocumentFonts.defaultFonts();
        var parsed = SongMapper.map(MusicXmlSerializer.unmarshal(source), documentFonts);

        return SongLoadResult.Success.settled(parsed, documentFonts, List.of());
    }

    /**
     * The file is opened here, not left to the parser: a missing path would otherwise surface
     * mid-parse as a parse error, and {@link songscribe.io.SongFileLoader} maps "not there" and
     * "damaged" to different results. Opening it here is also what closes it.
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
     * Thrown when a document cannot be read as this program's format: it breaks the MusicXML
     * schema SongScribe implements, or states, in valid MusicXML, something SongScribe cannot
     * represent.
     *
     * <p>{@link #detail()} reaches the user, so each throw site states a distinct one.
     */
    public static final class InvalidFormatException extends SAXException {

        private final String detail;

        InvalidFormatException(String detail) {
            super("Invalid MusicXML format: " + detail);
            this.detail = detail;
        }

        public String detail() {
            return detail;
        }
    }

    /**
     * Thrown when a document's {@code <software>} names this program but carries a
     * version that is not semver, or one older than {@link SoftwareProvenance#MIN_VERSION}.
     */
    public static final class UnsupportedVersionException extends SAXException {

        private final String version;

        UnsupportedVersionException(String version) {
            super("Unsupported SongScribe version: " + version);
            this.version = version;
        }

        public String version() {
            return version;
        }
    }
}
