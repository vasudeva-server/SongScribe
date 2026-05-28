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
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.dom.KeyType;
import songscribe.dom.Song;

/**
 * SAX reader that parses MusicXML 4.0 documents produced by {@link MusicXmlWriter}
 * back into a {@link Song}.
 * <p>
 * This is not a general MusicXML importer — it only handles SongScribe's own output.
 */
public final class MusicXmlReader extends DefaultHandler {

    private static final String SUPPORTED_VERSION = "4.0";
    private static final String ELEM_SCORE_PARTWISE = "score-partwise";
    private static final String ELEM_PART_LIST = "part-list";
    private static final String ELEM_SCORE_PART = "score-part";
    private static final String ELEM_PART = "part";
    private static final String ELEM_MEASURE = "measure";
    private static final String ELEM_ATTRIBUTES = "attributes";
    private static final String ELEM_KEY = "key";
    private static final String ELEM_FIFTHS = "fifths";
    private static final String ATTR_VERSION = "version";

    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
        PARSER_FACTORY.setValidating(false);
        try {
            // Harden against XXE: the writer never emits DOCTYPE, so legitimate
            // input is unaffected; crafted documents are rejected.
            PARSER_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            PARSER_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
            PARSER_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            PARSER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            PARSER_FACTORY.setXIncludeAware(false);
        } catch (SAXNotRecognizedException | SAXNotSupportedException | ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Where where = Where.NONE;
    private final StringBuilder value = new StringBuilder(200);

    @Nullable
    private Song song = null;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the parsed {@link Song} after parsing has completed.
     *
     * @throws IllegalStateException if called before parsing has completed
     */
    public Song getSong() {
        if (song == null) {
            throw new IllegalStateException("Parsing has not completed");
        }

        return song;
    }

    /**
     * Parses a MusicXML document from the given {@link InputSource} and returns
     * the resulting {@link Song}.
     *
     * @param source the MusicXML input to parse
     * @return the parsed song
     * @throws IOException  on I/O errors
     * @throws SAXException on parse errors
     */
    public static Song read(InputSource source) throws IOException, SAXException {
        try {
            var parser = PARSER_FACTORY.newSAXParser();
            var handler = new MusicXmlReader();
            parser.parse(source, handler);
            return handler.getSong();
        } catch (ParserConfigurationException e) {
            throw new SAXException("Failed to create SAX parser", e);
        }
    }

    /**
     * Parses a MusicXML document from the given {@link File} and returns the
     * resulting {@link Song}.
     *
     * @param file the MusicXML file to parse
     * @return the parsed song
     * @throws IOException  on I/O errors
     * @throws SAXException on parse errors
     */
    public static Song read(File file) throws IOException, SAXException {
        return read(new InputSource(file.toURI().toString()));
    }

    // -------------------------------------------------------------------------
    // SAX DefaultHandler overrides
    // -------------------------------------------------------------------------

    @Override
    public void startElement(
        String uri,
        String localName,
        String qName,
        Attributes attributes
    ) throws SAXException {
        value.delete(0, value.length());

        switch (where) {
            case NONE -> {
                if (qName.equals(ELEM_SCORE_PARTWISE)) {
                    var version = attributes.getValue(ATTR_VERSION);

                    if (!SUPPORTED_VERSION.equals(version)) {
                        throw new SAXException(
                            "Unsupported MusicXML version: '" + version +
                            "'; only " + SUPPORTED_VERSION + " is supported."
                        );
                    }

                    song = Song.newParsingStub();
                    song.beginSuspendMutationTracking();
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PARTWISE -> {
                if (qName.equals(ELEM_PART_LIST)) {
                    where = Where.PART_LIST;
                } else if (qName.equals(ELEM_PART)) {
                    where = Where.PART;
                }
            }
            case PART_LIST -> {
                if (qName.equals(ELEM_SCORE_PART)) {
                    where = Where.SCORE_PART;
                }
            }
            case PART -> {
                if (qName.equals(ELEM_MEASURE)) {
                    where = Where.MEASURE;
                }
            }
            case MEASURE -> {
                if (qName.equals(ELEM_ATTRIBUTES)) {
                    where = Where.ATTRIBUTES;
                }
            }
            case ATTRIBUTES -> {
                if (qName.equals(ELEM_KEY)) {
                    where = Where.KEY;
                }
            }
            case KEY -> {
                if (qName.equals(ELEM_FIFTHS)) {
                    where = Where.FIFTHS;
                }
            }
            default -> {
                // All other states: no nested elements of interest
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (where) {
            case FIFTHS -> {
                if (qName.equals(ELEM_FIFTHS)) {
                    var fifths = parseIntOrThrow(ELEM_FIFTHS, value.toString());

                    if (song == null) {
                        throw new SAXException("Unexpected <fifths> outside <score-partwise>");
                    }

                    song.setDefaultKeyAccidentalCount(Math.abs(fifths));
                    song.setDefaultKeyType(keyTypeFromFifths(fifths));
                    where = Where.KEY;
                }
            }
            case KEY -> {
                if (qName.equals(ELEM_KEY)) {
                    where = Where.ATTRIBUTES;
                }
            }
            case ATTRIBUTES -> {
                if (qName.equals(ELEM_ATTRIBUTES)) {
                    where = Where.MEASURE;
                }
            }
            case MEASURE -> {
                if (qName.equals(ELEM_MEASURE)) {
                    where = Where.PART;
                }
            }
            case PART -> {
                if (qName.equals(ELEM_PART)) {
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PART -> {
                if (qName.equals(ELEM_SCORE_PART)) {
                    where = Where.PART_LIST;
                }
            }
            case PART_LIST -> {
                if (qName.equals(ELEM_PART_LIST)) {
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PARTWISE -> {
                if (qName.equals(ELEM_SCORE_PARTWISE)) {
                    if (song != null) {
                        song.endSuspendMutationTracking();
                    }

                    where = Where.NONE;
                }
            }
            default -> {
                // NONE and any unrecognised state: nothing to do
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (where == Where.FIFTHS) {
            value.append(ch, start, length);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a MusicXML {@code <fifths>} value to the corresponding {@link KeyType}.
     * <ul>
     *   <li>0 → {@link KeyType#FLATS} (matches {@link Song#DEFAULT_KEY_TYPE})</li>
     *   <li>positive → {@link KeyType#SHARPS}</li>
     *   <li>negative → {@link KeyType#FLATS}</li>
     * </ul>
     */
    private static KeyType keyTypeFromFifths(int fifths) {
        if (fifths > 0) {
            return KeyType.SHARPS;
        } else {
            // Covers both fifths == 0 (no accidentals, default to FLATS per
            // Song.DEFAULT_KEY_TYPE) and fifths < 0 (explicit flats).
            return KeyType.FLATS;
        }
    }

    /**
     * Parses {@code raw} as an integer, throwing a {@link SAXException} if it
     * is not a valid integer. Mirrors the behaviour of
     * {@code DocumentValidation.parseIntOrThrow}.
     */
    private static int parseIntOrThrow(String tag, String raw) throws SAXException {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new SAXException(
                "Corrupt document: malformed <" + tag + "> value: '" + raw + "'", e
            );
        }
    }

    // -------------------------------------------------------------------------
    // State enum
    // -------------------------------------------------------------------------

    private enum Where {
        NONE,
        SCORE_PARTWISE,
        PART_LIST,
        SCORE_PART,
        PART,
        MEASURE,
        ATTRIBUTES,
        KEY,
        FIFTHS,
    }
}
