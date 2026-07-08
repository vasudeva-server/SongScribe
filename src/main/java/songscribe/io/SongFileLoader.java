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
 */
public final class SongFileLoader {

    private SongFileLoader() {}

    /*
     * Open-dispatch decision tree:
     *
     *  file
     *   │
     *   ▼  SongFileLoader.load(file)
     *   │   hasExtension(file, …)  (case-insensitive)
     *   │
     *   ├─ .mssw ─────────────► SongLoader.load(file)   (unchanged legacy path)
     *   │                        └─► Success │ IoError │ ParseError │ NewerVersion
     *   │
     *   ├─ .musicxml | .xml ──► MusicXmlReader.read(file)
     *   │      startElement: root ≠ <score-partwise>            ─► UnsupportedFormatException
     *   │      startElement: version missing/unparseable/<4.0   ─► UnsupportedFormatException
     *   │      endDocument:  <software> null/blank/¬startsWith(PACKAGE_NAME) ─► ForeignSoftwareException
     *   │      (otherwise) ─► Success
     *   │      catch ForeignSoftwareException  ► WrongSoftware(file, software)
     *   │      catch UnsupportedFormatException ► UnsupportedFileFormat(file, detail)
     *   │      catch SAXException              ► ParseError(file, e)
     *   │      catch IOException               ► IoError(file, e)
     *   │
     *   └─ else (.pdf, .txt, none, …) ► UnsupportedFileFormat(file, ext)
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
