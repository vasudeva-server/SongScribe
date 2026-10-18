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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.FileExtensions;
import songscribe.dom.StrandedKeyChangeException;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.util.FileUtils;

/**
 * Routes an open request to the correct reader based on file extension.
 * <p>
 * Opening a file never writes to it: the caller decides whether to save, because rewriting here
 * would delete musical content from disk before the user had seen it.
 */
public final class SongFileLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SongFileLoader.class);

    private SongFileLoader() {}

    /**
     * @return by the file's extension, matched case-insensitively:
     *     <ul>
     *     <li>{@code .mssw}: what {@link SongLoader#load} returns for it.
     *     <li>{@code .musicxml} or {@code .xml}: {@link SongLoadResult.Success};
     *         {@link SongLoadResult.InvalidFileFormat} for a root element other than
     *         {@code <score-partwise>}, or a MusicXML version that is missing, unparseable or
     *         older than the one this program writes; otherwise, whatever the content,
     *         {@link SongLoadResult.WrongSoftware} for a document another program wrote and
     *         {@link SongLoadResult.UnsupportedVersion} for one written by a SongScribe version
     *         this program does not read; {@link SongLoadResult.StrandedKeyChange};
     *         {@link SongLoadResult.ParseError} for a document that is not well formed or holds a
     *         value damaged beyond reading; {@link SongLoadResult.IoError} when the file cannot be
     *         read.
     *     <li>any other extension, or none: {@link SongLoadResult.UnsupportedFileFormat}, naming
     *         the extension.
     *     </ul>
     *     A reader that fails on a defect in this program, not in the file, gives
     *     {@link SongLoadResult.ParseError}.
     */
    public static SongLoadResult load(File file) {
        try {
            if (FileUtils.hasExtension(file, FileExtensions.SONGWRITER)) {
                return SongLoader.load(file);
            }

            if (FileUtils.hasExtension(file, FileExtensions.MUSICXML, FileExtensions.XML)) {
                return loadMusicXml(file);
            }
        } catch (RuntimeException e) {
            // A RuntimeException out of a reader or its load is a bug — for MusicXML most
            // plausibly a null read off the generated ProxyMusic graph, which NullAway cannot
            // check. It is logged with its stack trace so the bug stays findable, and reported as
            // a damaged file, because taking the application down is the wrong answer to a file
            // the user merely tried to open.
            LOG.error("Unexpected failure reading file '{}'", file.getName(), e);
            return new SongLoadResult.ParseError(file, new SAXException("Unexpected reader failure", e));
        }

        return new SongLoadResult.UnsupportedFileFormat(file, FileUtils.getExtension(file.getName()));
    }

    private static SongLoadResult loadMusicXml(File file) {
        try {
            return MusicXmlReader.read(file);
        } catch (MusicXmlReader.ForeignSoftwareException e) {
            return new SongLoadResult.WrongSoftware(file, e.software());
        } catch (MusicXmlReader.UnsupportedVersionException e) {
            return new SongLoadResult.UnsupportedVersion(file, e.version());
        } catch (MusicXmlReader.InvalidFormatException e) {
            return new SongLoadResult.InvalidFileFormat(file, e.detail());
        } catch (StrandedKeyChangeException e) {
            return new SongLoadResult.StrandedKeyChange(file, e);
        } catch (SAXException e) {
            return new SongLoadResult.ParseError(file, e);
        } catch (IOException e) {
            return new SongLoadResult.IoError(file, e);
        }
    }
}
