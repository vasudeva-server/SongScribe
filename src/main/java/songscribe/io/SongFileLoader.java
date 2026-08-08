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

import org.xml.sax.SAXException;

import songscribe.FileExtensions;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.util.FileUtils;

/**
 * Routes an open request to the correct reader based on file extension.
 * <p>
 * Use this for all file-open paths (UI and headless converters) so the
 * extension allow-list and error mapping live in one place.
 * <p>
 * Opening a file never writes to it. A load that migrated or dropped tuplets
 * reports what it did on the {@link SongLoadResult.Success} it returns, and the
 * caller decides what to tell the user and whether to save — rewriting the file
 * here would delete musical content from disk before the user had seen it.
 */
public final class SongFileLoader {

    private SongFileLoader() {}

    /*
     * Open-dispatch, keyed on the file's extension (matched case-insensitively):
     *
     * .mssw goes to the unchanged legacy SongLoader path, which yields Success, IoError,
     * ParseError or NewerVersion.
     *
     * .musicxml and .xml go to MusicXmlReader.read. That reader rejects a root element other than
     * <score-partwise>, and a version that is missing, unparseable or below 4.0, with an
     * UnsupportedFormatException; at endDocument it rejects a <software> value that is null, blank,
     * or does not start with the package name with a ForeignSoftwareException. Otherwise it returns
     * Success, carrying the tuplet load report if there is one. The catch clauses below map those
     * exceptions plus SAXException and IOException onto the corresponding SongLoadResult cases.
     *
     * Anything else (.pdf, .txt, no extension, …) is an UnsupportedFileFormat naming the extension.
     */
    public static SongLoadResult load(File file) {
        if (FileUtils.hasExtension(file, FileExtensions.SONGWRITER)) {
            return SongLoader.load(file);
        }

        if (FileUtils.hasExtension(file, FileExtensions.MUSICXML, FileExtensions.XML)) {
            try {
                return MusicXmlReader.read(file);
            } catch (MusicXmlReader.ForeignSoftwareException e) {
                return new SongLoadResult.WrongSoftware(file, e.software());
            } catch (MusicXmlReader.UnsupportedFormatException e) {
                return new SongLoadResult.UnsupportedFileFormat(file, e.detail());
            } catch (SAXException e) {
                return new SongLoadResult.ParseError(file, e);
            } catch (IOException e) {
                return new SongLoadResult.IoError(file, e);
            }
        }

        return new SongLoadResult.UnsupportedFileFormat(file, FileUtils.getExtension(file.getName()));
    }
}
