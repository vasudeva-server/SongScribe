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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.dom.SongLoad;
import songscribe.dom.StrandedKeyChangeException;

/**
 * Loads a {@link songscribe.dom.Song} from a file without requiring a {@code ScoreView}.
 */
public final class SongLoader {

    // A .mssw file is user-supplied like any other, so it gets the same hardening
    // as the MusicXML path — see SafeXmlParser.
    private static final SAXParserFactory PARSER_FACTORY = SafeXmlParser.newHardenedFactory();

    private SongLoader() {}

    /**
     * @param file a {@code .mssw} document
     * @return {@link SongLoadResult.Success}; {@link SongLoadResult.NewerVersion} for a document
     *     whose version is newer than this program reads; {@link SongLoadResult.ParseError} for
     *     one that is not well formed, has no {@code <song>} or {@code <composition>} element,
     *     has no readable {@code version} attribute on that element, or carries a value damaged
     *     beyond reading; {@link SongLoadResult.StrandedKeyChange};
     *     {@link SongLoadResult.IoError} when the file cannot be read
     */
    public static SongLoadResult load(File file) {
        try {
            return read(new InputSource(file.toURI().toString()));
        } catch (SongIO.NewerVersionException e) {
            return new SongLoadResult.NewerVersion(file, e);
        } catch (SAXException e) {
            return new SongLoadResult.ParseError(file, e);
        } catch (IOException e) {
            return new SongLoadResult.IoError(file, e);
        } catch (StrandedKeyChangeException e) {
            return new SongLoadResult.StrandedKeyChange(file, e);
        }
    }

    private static SongLoadResult.Success read(InputSource source)
        throws SAXException, IOException, StrandedKeyChangeException {
        SAXParser parser;

        try {
            parser = PARSER_FACTORY.newSAXParser();
        } catch (ParserConfigurationException e) {
            throw new SAXException("Failed to create SAX parser", e);
        }

        var loaded = SongLoad.<LegacyBuild, SAXException, IOException>run(song -> {
            var reader = new SongIO.DocumentReader(song);
            parser.parse(source, reader);
            return reader.finishBuild();
        });

        return SongLoadResult.Success.loaded(loaded);
    }
}
