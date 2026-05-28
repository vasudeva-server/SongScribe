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

import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;

/**
 * SAX reader that parses MusicXML 4.0 documents produced by {@link MusicXmlWriter}
 * back into a {@link Song}.
 * <p>
 * This is not a general MusicXML importer — it only handles SongScribe's own output.
 */
public final class MusicXmlReader extends DefaultHandler {

    // Element names, attribute names, and shared values are in MusicXmlTags.

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
    // Line-reconstruction state
    // -------------------------------------------------------------------------

    @Nullable
    private Line currentLine = null;

    // Barline-in-progress fields — valid while where == BARLINE or its children
    @Nullable
    private String barlineLocation = null;

    @Nullable
    private String barStyle = null;

    @Nullable
    private String repeatDirection = null;

    // true while a REPEAT_RIGHT is held pending the next barline.
    private boolean pendingRepeatRight = false;

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
                if (qName.equals(MusicXmlTags.SCORE_PARTWISE)) {
                    var version = attributes.getValue(MusicXmlTags.ATTR_VERSION);

                    if (!MusicXmlTags.VERSION_VALUE.equals(version)) {
                        throw new SAXException(
                            "Unsupported MusicXML version: '" + version +
                            "'; only " + MusicXmlTags.VERSION_VALUE + " is supported."
                        );
                    }

                    song = Song.newParsingStub();
                    song.beginSuspendMutationTracking();
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PARTWISE -> {
                if (qName.equals(MusicXmlTags.PART_LIST)) {
                    where = Where.PART_LIST;
                } else if (qName.equals(MusicXmlTags.PART)) {
                    where = Where.PART;
                }
            }
            case PART_LIST -> {
                if (qName.equals(MusicXmlTags.SCORE_PART)) {
                    where = Where.SCORE_PART;
                }
            }
            case PART -> {
                if (qName.equals(MusicXmlTags.MEASURE)) {
                    where = Where.MEASURE;
                }
            }
            case MEASURE -> {
                if (qName.equals(MusicXmlTags.ATTRIBUTES)) {
                    where = Where.ATTRIBUTES;
                } else if (qName.equals(MusicXmlTags.PRINT)) {
                    if (MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_NEW_SYSTEM))) {
                        startNewLine();
                    }
                } else if (qName.equals(MusicXmlTags.BARLINE)) {
                    barlineLocation = attributes.getValue(MusicXmlTags.ATTR_LOCATION);
                    barStyle = null;
                    repeatDirection = null;
                    where = Where.BARLINE;
                }
            }
            case ATTRIBUTES -> {
                if (qName.equals(MusicXmlTags.KEY)) {
                    where = Where.KEY;
                }
            }
            case KEY -> {
                if (qName.equals(MusicXmlTags.FIFTHS)) {
                    where = Where.FIFTHS;
                }
            }
            case BARLINE -> {
                if (qName.equals(MusicXmlTags.BAR_STYLE)) {
                    where = Where.BAR_STYLE;
                } else if (qName.equals(MusicXmlTags.REPEAT)) {
                    repeatDirection = attributes.getValue(MusicXmlTags.ATTR_DIRECTION);
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
            case BAR_STYLE -> {
                if (qName.equals(MusicXmlTags.BAR_STYLE)) {
                    barStyle = value.toString();
                    where = Where.BARLINE;
                }
            }
            case BARLINE -> {
                if (qName.equals(MusicXmlTags.BARLINE)) {
                    processBarline();
                    where = Where.MEASURE;
                }
            }
            case FIFTHS -> {
                if (qName.equals(MusicXmlTags.FIFTHS)) {
                    var fifths = parseIntOrThrow(MusicXmlTags.FIFTHS, value.toString());

                    if (song == null) {
                        throw new SAXException("Unexpected <fifths> outside <score-partwise>");
                    }

                    song.setDefaultKeyAccidentalCount(Math.abs(fifths));
                    song.setDefaultKeyType(keyTypeFromFifths(fifths));
                    where = Where.KEY;
                }
            }
            case KEY -> {
                if (qName.equals(MusicXmlTags.KEY)) {
                    where = Where.ATTRIBUTES;
                }
            }
            case ATTRIBUTES -> {
                if (qName.equals(MusicXmlTags.ATTRIBUTES)) {
                    where = Where.MEASURE;
                }
            }
            case MEASURE -> {
                if (qName.equals(MusicXmlTags.MEASURE)) {
                    where = Where.PART;
                }
            }
            case PART -> {
                if (qName.equals(MusicXmlTags.PART)) {
                    flushPendingRepeatRight();
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PART -> {
                if (qName.equals(MusicXmlTags.SCORE_PART)) {
                    where = Where.PART_LIST;
                }
            }
            case PART_LIST -> {
                if (qName.equals(MusicXmlTags.PART_LIST)) {
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PARTWISE -> {
                if (qName.equals(MusicXmlTags.SCORE_PARTWISE)) {
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
        if (where == Where.FIFTHS || where == Where.BAR_STYLE) {
            value.append(ch, start, length);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a new {@link Line}, adds it to the song, and sets it as current.
     */
    private void startNewLine() throws SAXException {
        if (song == null) {
            throw new SAXException("Unexpected <print new-system> outside <score-partwise>");
        }

        // A REPEAT_LEFT_RIGHT pair never straddles a line break: the writer
        // always opens the forward-left half with a plain measure, never a
        // new-system one. So a REPEAT_RIGHT still pending at a line boundary is
        // a standalone barline belonging to the line that is now ending — flush
        // it there before the new line becomes current.
        flushPendingRepeatRight();

        currentLine = new Line(song);
        song.addLine(currentLine);
    }

    /**
     * Resolves the completed {@code <barline>} and appends the appropriate
     * {@link ElementType} to the current line, or nothing for an invisible barline.
     *
     * <p>The REPEAT_LEFT_RIGHT straddling pair is handled here: a completed
     * REPEAT_RIGHT is held as {@link #pendingRepeatRight}. If the very next
     * barline is a REPEAT_LEFT on the left side, both are merged into a single
     * REPEAT_LEFT_RIGHT element. If not, the pending REPEAT_RIGHT is flushed
     * first, then the new barline is processed normally.
     */
    private void processBarline() throws SAXException {
        if (barStyle == null || BarlineStyleMapping.BAR_STYLE_NONE.equals(barStyle)) {
            // Invisible barline — line-break marker only; insert nothing.
            // A pending REPEAT_RIGHT is flushed because an invisible barline
            // can never be the forward half of a REPEAT_LEFT_RIGHT pair.
            flushPendingRepeatRight();
            return;
        }

        var elementType = BarlineStyleMapping.forBarStyle(barStyle, repeatDirection);

        if (elementType == null) {
            // Unknown combination — silently skip rather than corrupt the model.
            flushPendingRepeatRight();
            return;
        }

        if (pendingRepeatRight) {
            if (elementType == ElementType.REPEAT_LEFT
                    && BarlineStyleMapping.LOCATION_LEFT.equals(barlineLocation)) {
                // This left-forward barline is the second half of a straddling
                // REPEAT_LEFT_RIGHT pair — merge and emit the combined element.
                pendingRepeatRight = false;
                appendToCurrentLine(ElementType.REPEAT_LEFT_RIGHT);
            } else {
                // The pending REPEAT_RIGHT was not followed by a REPEAT_LEFT —
                // flush it as a standalone element, then process the current one.
                flushPendingRepeatRight();
                appendOrHold(elementType);
            }
        } else {
            appendOrHold(elementType);
        }
    }

    /**
     * Either holds {@code elementType} as {@link #pendingRepeatRight} (for
     * deferred REPEAT_LEFT_RIGHT pair detection) or appends it immediately.
     */
    private void appendOrHold(ElementType elementType) throws SAXException {
        if (elementType == ElementType.REPEAT_RIGHT) {
            // Defer: the next barline may be the forward half of a pair.
            pendingRepeatRight = true;
        } else {
            appendToCurrentLine(elementType);
        }
    }

    /**
     * Flushes a held {@link #pendingRepeatRight} as a standalone element.
     */
    private void flushPendingRepeatRight() throws SAXException {
        if (pendingRepeatRight) {
            appendToCurrentLine(ElementType.REPEAT_RIGHT);
            pendingRepeatRight = false;
        }
    }

    /**
     * Appends a structural {@link ElementType} to the current line.
     *
     * <p>Structural (non-duration) elements are created via {@link ElementType#newInstance()},
     * which clones the pre-built default instance — the same pattern used by
     * {@code StaffElementIO.StaffElementReader} for barline elements.
     */
    private void appendToCurrentLine(ElementType elementType) throws SAXException {
        if (currentLine == null) {
            throw new SAXException(
                "Barline element encountered before any line was started"
            );
        }

        currentLine.addElement(elementType.newInstance());
    }

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
        BARLINE,
        BAR_STYLE,
    }
}
