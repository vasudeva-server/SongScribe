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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * SAX reader that parses MusicXML 4.0 documents produced by {@link MusicXmlWriter}
 * back into a {@link Song}.
 * <p>
 * This is not a general MusicXML importer — it only handles SongScribe's own output.
 */
public final class MusicXmlReader extends DefaultHandler {

    // Element names, attribute names, and shared values are in MusicXmlTags.

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlReader.class);

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
    // Note-reconstruction state
    //
    // A <note> is parsed by accumulating its child values into the fields below
    // (reset at every <note> start) and assembling the StaffElement at </note>.
    //
    // Where-state subtree (note children and their <notations> descendants):
    //
    //   MEASURE
    //     └─ NOTE ── relative-x (attr) ─► xOffset
    //          ├─ GRACE  (marker)        ─► isGrace
    //          ├─ REST   (marker)        ─► isRest
    //          ├─ PITCH
    //          │    ├─ STEP   (text)     ─► step
    //          │    ├─ ALTER  (text)     ─► ignored (pitch from step/octave)
    //          │    └─ OCTAVE (text)     ─► octave
    //          ├─ DURATION (text)        ─► ignored (recomputed from type)
    //          ├─ NOTE_TYPE (text)       ─► typeToken
    //          ├─ DOT (marker, repeats)  ─► dotCount++
    //          ├─ ACCIDENTAL (text+attr) ─► accidental glyph + parentheses
    //          ├─ STEM (text)            ─► upper / stemDirectionAuto=false
    //          └─ NOTATIONS
    //               ├─ ARTICULATIONS
    //               │    ├─ ACCENT      ─► ACCENT articulation
    //               │    ├─ STACCATO    ─► STACCATO articulation
    //               │    ├─ FALLOFF     ─► setFall()
    //               │    └─ BREATH_MARK ─► append BREATH_MARK element after note
    //               ├─ FERMATA          ─► FermataAttachment
    //               ├─ DYNAMICS
    //               │    └─ DYNAMIC_MARK ─► DynamicAttachment
    //               └─ SLIDE (type attr) ─► glissando start/stop pairing
    //
    // Glissando pairing (mirrors pendingRepeatRight): a <slide type="start"> note
    // is held in pendingSlideStart until the NEXT note's <slide type="stop"> is
    // seen, at which point setGlissando() is called on the held start note:
    //
    //   noteA <slide start> ─► pendingSlideStart = noteA
    //   noteB <slide stop>  ─► pendingSlideStart.setGlissando(); clear
    //
    // A dangling start (its stop never arrives, e.g. a truncated file) is dropped
    // — never turned into a glissando — and logged at the part-end flush.
    // -------------------------------------------------------------------------

    @Nullable
    private String noteTypeToken = null;

    private boolean noteIsRest = false;
    private boolean noteIsGrace = false;
    private boolean noteHasPitch = false;
    private char noteStep = ' ';
    private int noteOctave = 0;
    private int noteDotCount = 0;

    @Nullable
    private String noteAccidentalToken = null;

    private boolean noteAccidentalParenthesized = false;
    private boolean noteStemPresent = false;
    private boolean noteStemUp = false;
    private boolean noteRelativeXPresent = false;
    private double noteRelativeXTenths = 0.0;
    private boolean noteHasAccent = false;
    private boolean noteHasStaccato = false;
    private boolean noteHasFermata = false;

    @Nullable
    private DynamicType noteDynamicType = null;

    private boolean noteHasFall = false;

    @Nullable
    private String noteSlideType = null;

    private boolean noteHasBreathMark = false;

    // Holds a <slide type="start"> note until the next note's stop is seen.
    @Nullable
    private StaffElement pendingSlideStart = null;

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
                } else if (qName.equals(MusicXmlTags.NOTE)) {
                    startNote(attributes);
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
            case NOTE -> {
                if (qName.equals(MusicXmlTags.GRACE)) {
                    noteIsGrace = true;
                    where = Where.GRACE;
                } else if (qName.equals(MusicXmlTags.REST)) {
                    noteIsRest = true;
                    where = Where.REST;
                } else if (qName.equals(MusicXmlTags.PITCH)) {
                    where = Where.PITCH;
                } else if (qName.equals(MusicXmlTags.DURATION)) {
                    where = Where.DURATION;
                } else if (qName.equals(MusicXmlTags.NOTE_TYPE)) {
                    where = Where.NOTE_TYPE;
                } else if (qName.equals(MusicXmlTags.DOT)) {
                    noteDotCount++;
                    where = Where.DOT;
                } else if (qName.equals(MusicXmlTags.ACCIDENTAL)) {
                    noteAccidentalParenthesized =
                        MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_CAUTIONARY))
                            || MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_PARENTHESES));
                    where = Where.ACCIDENTAL;
                } else if (qName.equals(MusicXmlTags.STEM)) {
                    noteStemPresent = true;
                    where = Where.STEM;
                } else if (qName.equals(MusicXmlTags.NOTATIONS)) {
                    where = Where.NOTATIONS;
                }
            }
            case PITCH -> {
                if (qName.equals(MusicXmlTags.STEP)) {
                    where = Where.STEP;
                } else if (qName.equals(MusicXmlTags.ALTER)) {
                    where = Where.ALTER;
                } else if (qName.equals(MusicXmlTags.OCTAVE)) {
                    where = Where.OCTAVE;
                }
            }
            case NOTATIONS -> {
                if (qName.equals(MusicXmlTags.ARTICULATIONS)) {
                    where = Where.ARTICULATIONS;
                } else if (qName.equals(MusicXmlTags.FERMATA)) {
                    noteHasFermata = true;
                    where = Where.FERMATA;
                } else if (qName.equals(MusicXmlTags.DYNAMICS)) {
                    where = Where.DYNAMICS;
                } else if (qName.equals(MusicXmlTags.SLIDE)) {
                    noteSlideType = attributes.getValue(MusicXmlTags.ATTR_TYPE);
                    where = Where.SLIDE;
                }
            }
            case ARTICULATIONS -> {
                if (qName.equals(MusicXmlTags.ACCENT)) {
                    noteHasAccent = true;
                    where = Where.ACCENT;
                } else if (qName.equals(MusicXmlTags.STACCATO)) {
                    noteHasStaccato = true;
                    where = Where.STACCATO;
                } else if (qName.equals(MusicXmlTags.FALLOFF)) {
                    noteHasFall = true;
                    where = Where.FALLOFF;
                } else if (qName.equals(MusicXmlTags.BREATH_MARK)) {
                    noteHasBreathMark = true;
                    where = Where.BREATH_MARK;
                }
            }
            case DYNAMICS -> {
                // A <dynamics> child element's name is the dynamic symbol itself
                // (e.g. <f/>, <mf/>); resolve it to a DynamicType.
                var dynamicType = DynamicType.fromSymbol(qName);

                if (dynamicType != null) {
                    noteDynamicType = dynamicType;
                }

                where = Where.DYNAMIC_MARK;
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
            case STEP -> {
                if (qName.equals(MusicXmlTags.STEP)) {
                    var step = value.toString().trim();

                    if (!step.isEmpty()) {
                        noteStep = step.charAt(0);
                        noteHasPitch = true;
                    }

                    where = Where.PITCH;
                }
            }
            case ALTER -> {
                if (qName.equals(MusicXmlTags.ALTER)) {
                    // <alter> is the sounding semitone; pitch is recovered from
                    // <step>/<octave>, so the value is intentionally ignored.
                    where = Where.PITCH;
                }
            }
            case OCTAVE -> {
                if (qName.equals(MusicXmlTags.OCTAVE)) {
                    noteOctave = parseIntOrThrow(MusicXmlTags.OCTAVE, value.toString());
                    noteHasPitch = true;
                    where = Where.PITCH;
                }
            }
            case PITCH -> {
                if (qName.equals(MusicXmlTags.PITCH)) {
                    where = Where.NOTE;
                }
            }
            case REST -> {
                if (qName.equals(MusicXmlTags.REST)) {
                    where = Where.NOTE;
                }
            }
            case GRACE -> {
                if (qName.equals(MusicXmlTags.GRACE)) {
                    where = Where.NOTE;
                }
            }
            case DURATION -> {
                if (qName.equals(MusicXmlTags.DURATION)) {
                    // <duration> is recomputed from <type> + dots, so it is ignored.
                    where = Where.NOTE;
                }
            }
            case NOTE_TYPE -> {
                if (qName.equals(MusicXmlTags.NOTE_TYPE)) {
                    noteTypeToken = value.toString().trim();
                    where = Where.NOTE;
                }
            }
            case DOT -> {
                if (qName.equals(MusicXmlTags.DOT)) {
                    where = Where.NOTE;
                }
            }
            case ACCIDENTAL -> {
                if (qName.equals(MusicXmlTags.ACCIDENTAL)) {
                    noteAccidentalToken = value.toString().trim();
                    where = Where.NOTE;
                }
            }
            case STEM -> {
                if (qName.equals(MusicXmlTags.STEM)) {
                    noteStemUp = MusicXmlTags.STEM_UP.equals(value.toString().trim());
                    where = Where.NOTE;
                }
            }
            case ACCENT, STACCATO, FALLOFF, BREATH_MARK -> {
                where = Where.ARTICULATIONS;
            }
            case ARTICULATIONS -> {
                if (qName.equals(MusicXmlTags.ARTICULATIONS)) {
                    where = Where.NOTATIONS;
                }
            }
            case FERMATA -> {
                if (qName.equals(MusicXmlTags.FERMATA)) {
                    where = Where.NOTATIONS;
                }
            }
            case DYNAMIC_MARK -> {
                where = Where.DYNAMICS;
            }
            case DYNAMICS -> {
                if (qName.equals(MusicXmlTags.DYNAMICS)) {
                    where = Where.NOTATIONS;
                }
            }
            case SLIDE -> {
                if (qName.equals(MusicXmlTags.SLIDE)) {
                    where = Where.NOTATIONS;
                }
            }
            case NOTATIONS -> {
                if (qName.equals(MusicXmlTags.NOTATIONS)) {
                    where = Where.NOTE;
                }
            }
            case NOTE -> {
                if (qName.equals(MusicXmlTags.NOTE)) {
                    finishNote();
                    where = Where.MEASURE;
                }
            }
            case PART -> {
                if (qName.equals(MusicXmlTags.PART)) {
                    flushPendingRepeatRight();
                    flushPendingSlideStart();
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
        // Accumulate unconditionally: startElement clears the buffer for every
        // element, so each leaf element's endElement reads only its own text.
        // This avoids the silent-empty-value risk of forgetting to add a new
        // text-bearing state to a where-based guard.
        value.append(ch, start, length);
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
     * Resets the note-in-progress state and captures the {@code <note>}'s
     * {@code relative-x} offset attribute. The {@code default-x} attribute (the
     * write-forward computed base X) is intentionally ignored — layout recomputes
     * it on load.
     */
    private void startNote(Attributes attributes) throws SAXException {
        resetNoteState();

        var relativeX = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_X);

        if (relativeX != null) {
            noteRelativeXPresent = true;
            noteRelativeXTenths = parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_X, relativeX);
        }

        where = Where.NOTE;
    }

    /**
     * Clears all per-note accumulation fields. Called at every {@code <note>} start.
     */
    private void resetNoteState() {
        noteTypeToken = null;
        noteIsRest = false;
        noteIsGrace = false;
        noteHasPitch = false;
        noteStep = ' ';
        noteOctave = 0;
        noteDotCount = 0;
        noteAccidentalToken = null;
        noteAccidentalParenthesized = false;
        noteStemPresent = false;
        noteStemUp = false;
        noteRelativeXPresent = false;
        noteRelativeXTenths = 0.0;
        noteHasAccent = false;
        noteHasStaccato = false;
        noteHasFermata = false;
        noteDynamicType = null;
        noteHasFall = false;
        noteSlideType = null;
        noteHasBreathMark = false;
    }

    /**
     * Assembles the accumulated note state into a {@link StaffElement}, appends it
     * to the current line, and resolves its per-note attachments. Called at
     * {@code </note>}.
     */
    private void finishNote() throws SAXException {
        if (currentLine == null) {
            throw new SAXException("<note> encountered before any line was started");
        }

        if (noteTypeToken == null) {
            throw new SAXException("<note> is missing its <type> element");
        }

        var elementType = NoteTypeMapping.forTypeToken(noteTypeToken, noteIsRest, noteIsGrace);

        if (elementType == null) {
            throw new SAXException("Unrecognised <type> token: '" + noteTypeToken + "'");
        }

        var note = elementType.newInstance();

        // <step> + <octave> are authoritative for pitch (key-independent); <alter>
        // is ignored on read. Rests carry no pitch.
        if (noteHasPitch) {
            note.setStaffPosition(PitchSpelling.staffPositionFor(noteStep, noteOctave));
        }

        note.setDotCount(noteDotCount);

        // The displayed accidental glyph comes from <accidental>, not <alter>.
        if (noteAccidentalToken != null) {
            var accidental = AccidentalMapping.forToken(noteAccidentalToken);

            if (accidental != null) {
                note.setAccidental(accidental);
                // setAccidentalInParentheses must follow setAccidental: it only
                // takes effect when an accidental is present.
                note.setAccidentalInParentheses(noteAccidentalParenthesized);
            }
        }

        // A present <stem> is a manual override; its absence means auto direction.
        if (noteStemPresent) {
            note.setUpper(noteStemUp);
            note.setStemDirectionAuto(false);
        } else {
            note.setStemDirectionAuto(true);
        }

        if (noteRelativeXPresent) {
            note.setXOffsetPx(ScaleContext.ssToRoundedPx(noteRelativeXTenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE));
        }

        // Append before adding attachments so each attachment's parent line is wired.
        currentLine.addElement(note);

        if (noteHasAccent) {
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        }

        if (noteHasStaccato) {
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        }

        if (noteHasFermata) {
            note.addAttachment(new FermataAttachment(note));
        }

        if (noteDynamicType != null) {
            note.addAttachment(new DynamicAttachment(note, noteDynamicType));
        }

        if (noteHasFall) {
            note.setFall();
        }

        resolveSlide(note);

        // A breath-mark attached to this note's <notations> becomes a standalone
        // BREATH_MARK element immediately after the note.
        if (noteHasBreathMark) {
            appendToCurrentLine(ElementType.BREATH_MARK);
        }
    }

    /**
     * Pairs this note's {@code <slide>} (if any) with its partner: a
     * {@code type="start"} note is held in {@link #pendingSlideStart} until the
     * next note's {@code type="stop"} is seen, at which point the held start note
     * is marked as a glissando. A glissando needs two notes, so a start is never
     * itself marked on read.
     */
    private void resolveSlide(StaffElement note) {
        if (MusicXmlTags.SLIDE_STOP.equals(noteSlideType)) {
            if (pendingSlideStart != null) {
                pendingSlideStart.setGlissando();
                pendingSlideStart = null;
            }
            // A stop with no pending start is stray; ignore it.
        } else if (MusicXmlTags.SLIDE_START.equals(noteSlideType)) {
            // A still-pending earlier start never received its stop — drop it.
            flushPendingSlideStart();
            pendingSlideStart = note;
        }
    }

    /**
     * Drops a dangling {@code <slide type="start">} whose matching
     * {@code type="stop"} never arrived (e.g. a truncated file). A glissando needs
     * two notes, so the start note keeps no glissando.
     */
    private void flushPendingSlideStart() {
        if (pendingSlideStart != null) {
            LOG.warn("Dropping dangling <slide type=\"start\"> with no matching <slide type=\"stop\">");
            pendingSlideStart = null;
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

    /**
     * Parses {@code raw} as a double, throwing a {@link SAXException} if it is not
     * a valid number. Used for positional attributes (tenths), which MusicXML
     * permits to be fractional.
     */
    private static double parseDoubleOrThrow(String attr, String raw) throws SAXException {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new SAXException(
                "Corrupt document: malformed '" + attr + "' value: '" + raw + "'", e
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

        // Note subtree.
        NOTE,
        PITCH,
        STEP,
        ALTER,
        OCTAVE,
        REST,
        GRACE,
        DURATION,
        NOTE_TYPE,
        DOT,
        ACCIDENTAL,
        STEM,
        NOTATIONS,
        ARTICULATIONS,
        ACCENT,
        STACCATO,
        FALLOFF,
        BREATH_MARK,
        FERMATA,
        DYNAMICS,
        DYNAMIC_MARK,
        SLIDE,
    }
}
