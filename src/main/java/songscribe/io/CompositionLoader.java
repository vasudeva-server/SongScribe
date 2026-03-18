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

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;


import org.xml.sax.SAXException;

import songscribe.music.Composition;

/**
 * Loads a {@link Composition} from a file without requiring a {@link songscribe.ui.component.Score}.
 * <p>
 * Use this for headless operations (ABC export, MIDI export) that only need
 * the composition data. For operations that also need rendering (image, PDF,
 * SVG export), use {@link songscribe.ui.component.Score#openFile} instead.
 */
public final class CompositionLoader {

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();

    private CompositionLoader() {}

    /**
     * Parses a SongScribe file and returns the loaded composition.
     *
     * @param file the .mssw file to load
     * @return the loaded composition
     * @throws IOException if the file cannot be read
     * @throws SAXException if the file is malformed
     */
    public static Composition load(File file) throws IOException, SAXException {
        try {
            SAXParser parser = PARSER_FACTORY.newSAXParser();
            var reader = new CompositionIO.DocumentReader();
            parser.parse(file, reader);
            return reader.getComposition();
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new SAXException("Failed to create SAX parser", e);
        }
    }
}
