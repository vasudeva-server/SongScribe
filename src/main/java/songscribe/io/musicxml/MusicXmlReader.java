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

import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;

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

    /** Verse number for a {@code <lyric>} that omits its {@code number} attribute. */
    private static final int DEFAULT_LYRIC_VERSE = 1;

    private Where where = Where.NONE;
    private final StringBuilder value = new StringBuilder(200);

    @Nullable
    private Song song = null;

    // Per-note range-span pending anchors (slide, beam, tie, tuplet, trill) — see
    // RangeSpanResolver.
    private final RangeSpanResolver spans = new RangeSpanResolver();

    // Measure-level hairpin wedge state machine — see WedgeResolver.
    private final WedgeResolver wedges = new WedgeResolver();

    // Measure-level tempo direction state machine — see MetronomeResolver.
    private final MetronomeResolver metronome = new MetronomeResolver();

    // Resolves <ending> markers collected on barlines into Ending range spans
    // on the current line — see EndingResolver.
    private final EndingResolver endings = new EndingResolver(this);

    // Parses <barline> elements (bar-style/repeat/ending accumulation and the
    // REPEAT_LEFT_RIGHT straddling pair) — see BarlineParser.
    private final BarlineParser barlines = new BarlineParser(this, endings);

    // -------------------------------------------------------------------------
    // Line-reconstruction state
    // -------------------------------------------------------------------------

    @Nullable
    private Line currentLine = null;

    /**
     * The line currently being built, or {@code null} before the first line
     * starts. Read by {@link BarlineParser} and {@link EndingResolver}, which
     * append to and build on it through this reader rather than duplicating
     * ownership of it.
     */
    @Nullable
    Line getCurrentLine() {
        return currentLine;
    }

    /**
     * The signed-fifths key signature currently in effect. Measure 1 seeds this
     * from the song default; a later line's {@code <key>} advances it. It is
     * applied to each new line so a key persists across lines until restated —
     * mirroring the writer, which emits a {@code <key>} only when a line's key
     * differs from this running value.
     */
    private int runningFifths = 0;

    /**
     * True once the measure-1 {@code <key>} has set the song default and seeded
     * {@link #runningFifths}. Distinguishes the song-default key (measure 1) from
     * a per-line key change (any later measure).
     */
    private boolean songDefaultKeySet = false;

    // -------------------------------------------------------------------------
    // Note-reconstruction state — accumulated per <note> in NoteAccumulator;
    // see its class doc for the <note> child → field mapping diagram.
    // -------------------------------------------------------------------------

    private final NoteAccumulator note = new NoteAccumulator();

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
                    barlines.beginBarline(attributes.getValue(MusicXmlTags.ATTR_LOCATION));
                    where = Where.BARLINE;
                } else if (qName.equals(MusicXmlTags.DIRECTION)) {
                    where = Where.DIRECTION;
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
                } else if (qName.equals(MusicXmlTags.ENDING)) {
                    // <ending> is an empty element; collect it and stay in BARLINE.
                    // Resolution to the barline's StaffElement happens once the
                    // barline is appended (see BarlineParser.processBarline /
                    // EndingResolver.attachBarlineEndings).
                    barlines.addEndingMarker(new EndingResolver.EndingMarker(
                        attributes.getValue(MusicXmlTags.ATTR_NUMBER),
                        attributes.getValue(MusicXmlTags.ATTR_TYPE)
                    ));
                } else if (qName.equals(MusicXmlTags.REPEAT)) {
                    barlines.setRepeatDirection(attributes.getValue(MusicXmlTags.ATTR_DIRECTION));
                }
            }
            case DIRECTION -> {
                if (qName.equals(MusicXmlTags.DIRECTION_TYPE)) {
                    where = Where.DIRECTION_TYPE;
                } else if (qName.equals(MusicXmlTags.SOUND)) {
                    // <sound tempo> is write-forward only; the visible tempo is
                    // recovered from <metronome>, so this carries no read state.
                    where = Where.SOUND;
                }
            }
            case DIRECTION_TYPE -> {
                if (qName.equals(MusicXmlTags.WEDGE)) {
                    wedges.handleWedge(attributes);
                    where = Where.WEDGE;
                } else if (qName.equals(MusicXmlTags.METRONOME)) {
                    metronome.beginMetronome(attributes);
                    where = Where.METRONOME;
                } else if (qName.equals(MusicXmlTags.WORDS)) {
                    where = Where.WORDS;
                }
            }
            case METRONOME -> {
                if (qName.equals(MusicXmlTags.BEAT_UNIT)) {
                    where = Where.BEAT_UNIT;
                } else if (qName.equals(MusicXmlTags.BEAT_UNIT_DOT)) {
                    metronome.addBeatUnitDot();
                    where = Where.BEAT_UNIT_DOT;
                } else if (qName.equals(MusicXmlTags.PER_MINUTE)) {
                    where = Where.PER_MINUTE;
                } else if (qName.equals(MusicXmlTags.METRONOME_NOTE)) {
                    metronome.beginMetronomeNote();
                    where = Where.METRONOME_NOTE;
                } else if (qName.equals(MusicXmlTags.METRONOME_RELATION)) {
                    // <metronome-relation> is always "equals"; its position (between
                    // the two note groups) carries no read state beyond marking the
                    // modulation form, which the metronome-notes already do.
                    where = Where.METRONOME_RELATION;
                }
            }
            case METRONOME_NOTE -> {
                if (qName.equals(MusicXmlTags.METRONOME_TYPE)) {
                    where = Where.METRONOME_TYPE;
                } else if (qName.equals(MusicXmlTags.METRONOME_DOT)) {
                    metronome.addMetronomeDot();
                    where = Where.METRONOME_DOT;
                }
            }
            case NOTE -> {
                if (qName.equals(MusicXmlTags.GRACE)) {
                    note.markGrace();
                    where = Where.GRACE;
                } else if (qName.equals(MusicXmlTags.REST)) {
                    note.markRest();
                    where = Where.REST;
                } else if (qName.equals(MusicXmlTags.PITCH)) {
                    where = Where.PITCH;
                } else if (qName.equals(MusicXmlTags.DURATION)) {
                    where = Where.DURATION;
                } else if (qName.equals(MusicXmlTags.NOTE_TYPE)) {
                    where = Where.NOTE_TYPE;
                } else if (qName.equals(MusicXmlTags.DOT)) {
                    note.incrementDotCount();
                    where = Where.DOT;
                } else if (qName.equals(MusicXmlTags.ACCIDENTAL)) {
                    note.setAccidentalParenthesized(
                        MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_CAUTIONARY))
                            || MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_PARENTHESES))
                    );
                    where = Where.ACCIDENTAL;
                } else if (qName.equals(MusicXmlTags.STEM)) {
                    note.markStemPresent();
                    where = Where.STEM;
                } else if (qName.equals(MusicXmlTags.TIE)) {
                    // Sound tie — write-forward only; <tied> is the source of truth
                    // for span reconstruction, so this carries no read state.
                    where = Where.TIE;
                } else if (qName.equals(MusicXmlTags.TIME_MOD)) {
                    where = Where.TIME_MODIFICATION;
                } else if (qName.equals(MusicXmlTags.BEAM)) {
                    // Only the primary beam (number="1") drives span collapse;
                    // secondary levels and hooks are write-forward (layout re-derives).
                    note.setBeamLevelIsOne(
                        MusicXmlTags.NUMBER_1.equals(attributes.getValue(MusicXmlTags.ATTR_NUMBER))
                    );
                    where = Where.BEAM;
                } else if (qName.equals(MusicXmlTags.NOTATIONS)) {
                    where = Where.NOTATIONS;
                } else if (qName.equals(MusicXmlTags.LYRIC)) {
                    // A <lyric number="N"> opens one verse. Absent number → verse 1
                    // (lenient, matching StaffElementIO).
                    var numberAttr = attributes.getValue(MusicXmlTags.ATTR_NUMBER);
                    var verse = numberAttr != null
                        ? parseIntOrThrow(MusicXmlTags.ATTR_NUMBER, numberAttr)
                        : DEFAULT_LYRIC_VERSE;
                    note.beginLyric(verse);
                    where = Where.LYRIC;
                }
            }
            case LYRIC -> {
                if (qName.equals(MusicXmlTags.SYLLABIC)) {
                    where = Where.SYLLABIC;
                } else if (qName.equals(MusicXmlTags.LYRIC_TEXT)) {
                    where = Where.LYRIC_TEXT;
                } else if (qName.equals(MusicXmlTags.EXTEND)) {
                    // <extend> is an empty element; its type attr is the only data.
                    // Absent/unrecognized type → START (see SyllabicMapping).
                    note.setLyricExtend(
                        SyllabicMapping.forExtendToken(attributes.getValue(MusicXmlTags.ATTR_TYPE))
                    );
                    where = Where.EXTEND;
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
                    note.markFermata();
                    where = Where.FERMATA;
                } else if (qName.equals(MusicXmlTags.DYNAMICS)) {
                    where = Where.DYNAMICS;
                } else if (qName.equals(MusicXmlTags.SLIDE)) {
                    note.setSlideType(attributes.getValue(MusicXmlTags.ATTR_TYPE));
                    where = Where.SLIDE;
                } else if (qName.equals(MusicXmlTags.TIED)) {
                    note.addTied(attributes.getValue(MusicXmlTags.ATTR_TYPE));
                    where = Where.TIED;
                } else if (qName.equals(MusicXmlTags.TUPLET)) {
                    var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

                    if (MusicXmlTags.TYPE_START.equals(type)) {
                        note.markTupletStart();
                        note.captureTupletRelativeY(attributes);
                    } else if (MusicXmlTags.TYPE_STOP.equals(type)) {
                        note.markTupletStop();
                    }

                    where = Where.TUPLET;
                } else if (qName.equals(MusicXmlTags.ORNAMENTS)) {
                    where = Where.ORNAMENTS;
                }
            }
            case TIME_MODIFICATION -> {
                if (qName.equals(MusicXmlTags.ACTUAL_NOTES)) {
                    where = Where.ACTUAL_NOTES;
                } else if (qName.equals(MusicXmlTags.NORMAL_NOTES)) {
                    where = Where.NORMAL_NOTES;
                }
            }
            case ORNAMENTS -> {
                if (qName.equals(MusicXmlTags.TRILL_MARK)) {
                    where = Where.TRILL_MARK;
                } else if (qName.equals(MusicXmlTags.WAVY_LINE)) {
                    var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

                    if (MusicXmlTags.TYPE_START.equals(type)) {
                        note.markTrillStart();
                        note.captureTrillRelativeY(attributes);
                    } else if (MusicXmlTags.TYPE_STOP.equals(type)) {
                        note.markTrillStop();
                    }

                    where = Where.WAVY_LINE;
                }
            }
            case ARTICULATIONS -> {
                if (qName.equals(MusicXmlTags.ACCENT)) {
                    note.markAccent();
                    where = Where.ACCENT;
                } else if (qName.equals(MusicXmlTags.STACCATO)) {
                    note.markStaccato();
                    where = Where.STACCATO;
                } else if (qName.equals(MusicXmlTags.FALLOFF)) {
                    note.markFall();
                    where = Where.FALLOFF;
                } else if (qName.equals(MusicXmlTags.BREATH_MARK)) {
                    note.markBreathMark();
                    where = Where.BREATH_MARK;
                }
            }
            case DYNAMICS -> {
                // A <dynamics> child element's name is the dynamic symbol itself
                // (e.g. <f/>, <mf/>); resolve it to a DynamicType.
                var dynamicType = DynamicType.fromSymbol(qName);

                if (dynamicType != null) {
                    note.setDynamicType(dynamicType);
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
                    barlines.setBarStyle(value.toString());
                    where = Where.BARLINE;
                }
            }
            case BARLINE -> {
                if (qName.equals(MusicXmlTags.BARLINE)) {
                    barlines.processBarline();
                    where = Where.MEASURE;
                }
            }
            case FIFTHS -> {
                if (qName.equals(MusicXmlTags.FIFTHS)) {
                    var fifths = parseIntOrThrow(MusicXmlTags.FIFTHS, value.toString());

                    if (song == null) {
                        throw new SAXException("Unexpected <fifths> outside <score-partwise>");
                    }

                    if (songDefaultKeySet) {
                        // A later line's key change: advance the running key and
                        // apply it to the line now being built.
                        runningFifths = fifths;

                        if (currentLine != null) {
                            applyFifthsToLine(currentLine, fifths);
                        }
                    } else {
                        // Measure 1: the song default, which also seeds the running
                        // key. Line 1 itself is materialized from the default when
                        // it is added, so it is not set here.
                        song.setDefaultKeyAccidentalCount(KeySignatureMapping.accidentalCount(fifths));
                        song.setDefaultKeyType(KeySignatureMapping.keyType(fifths));
                        runningFifths = fifths;
                        songDefaultKeySet = true;
                    }

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
                        note.setStep(step.charAt(0));
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
                    note.setOctave(parseIntOrThrow(MusicXmlTags.OCTAVE, value.toString()));
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
                    note.setTypeToken(value.toString().trim());
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
                    note.setAccidentalToken(value.toString().trim());
                    where = Where.NOTE;
                }
            }
            case STEM -> {
                if (qName.equals(MusicXmlTags.STEM)) {
                    note.setStemUp(MusicXmlTags.STEM_UP.equals(value.toString().trim()));
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
            case TIE -> {
                if (qName.equals(MusicXmlTags.TIE)) {
                    where = Where.NOTE;
                }
            }
            case BEAM -> {
                if (qName.equals(MusicXmlTags.BEAM)) {
                    // Capture the primary-beam value only; secondary levels and
                    // hooks are write-forward and ignored on read.
                    note.endBeam(value.toString().trim());
                    where = Where.NOTE;
                }
            }
            case ACTUAL_NOTES -> {
                if (qName.equals(MusicXmlTags.ACTUAL_NOTES)) {
                    note.setActualNotes(parseIntOrThrow(MusicXmlTags.ACTUAL_NOTES, value.toString()));
                    where = Where.TIME_MODIFICATION;
                }
            }
            case NORMAL_NOTES -> {
                if (qName.equals(MusicXmlTags.NORMAL_NOTES)) {
                    // <normal-notes> is write-forward only; grade comes from
                    // <actual-notes>, so the value is intentionally ignored.
                    where = Where.TIME_MODIFICATION;
                }
            }
            case TIME_MODIFICATION -> {
                if (qName.equals(MusicXmlTags.TIME_MOD)) {
                    where = Where.NOTE;
                }
            }
            case TIED -> {
                if (qName.equals(MusicXmlTags.TIED)) {
                    where = Where.NOTATIONS;
                }
            }
            case TUPLET -> {
                if (qName.equals(MusicXmlTags.TUPLET)) {
                    where = Where.NOTATIONS;
                }
            }
            case TRILL_MARK -> {
                if (qName.equals(MusicXmlTags.TRILL_MARK)) {
                    where = Where.ORNAMENTS;
                }
            }
            case WAVY_LINE -> {
                if (qName.equals(MusicXmlTags.WAVY_LINE)) {
                    where = Where.ORNAMENTS;
                }
            }
            case ORNAMENTS -> {
                if (qName.equals(MusicXmlTags.ORNAMENTS)) {
                    where = Where.NOTATIONS;
                }
            }
            case NOTATIONS -> {
                if (qName.equals(MusicXmlTags.NOTATIONS)) {
                    where = Where.NOTE;
                }
            }
            case SYLLABIC -> {
                if (qName.equals(MusicXmlTags.SYLLABIC)) {
                    note.setLyricSyllabicToken(value.toString().trim());
                    where = Where.LYRIC;
                }
            }
            case LYRIC_TEXT -> {
                if (qName.equals(MusicXmlTags.LYRIC_TEXT)) {
                    // Not trimmed: the text is emitted inline with no surrounding
                    // whitespace, and a trailing compound marker must survive intact.
                    note.setLyricText(value.toString());
                    where = Where.LYRIC;
                }
            }
            case EXTEND -> {
                if (qName.equals(MusicXmlTags.EXTEND)) {
                    where = Where.LYRIC;
                }
            }
            case LYRIC -> {
                if (qName.equals(MusicXmlTags.LYRIC)) {
                    note.endLyric();
                    where = Where.NOTE;
                }
            }
            case WEDGE -> {
                if (qName.equals(MusicXmlTags.WEDGE)) {
                    where = Where.DIRECTION_TYPE;
                }
            }
            case BEAT_UNIT -> {
                if (qName.equals(MusicXmlTags.BEAT_UNIT)) {
                    metronome.setBeatUnitToken(value.toString().trim());
                    where = Where.METRONOME;
                }
            }
            case BEAT_UNIT_DOT -> {
                if (qName.equals(MusicXmlTags.BEAT_UNIT_DOT)) {
                    where = Where.METRONOME;
                }
            }
            case PER_MINUTE -> {
                if (qName.equals(MusicXmlTags.PER_MINUTE)) {
                    metronome.setVisibleTempo(parseIntOrThrow(MusicXmlTags.PER_MINUTE, value.toString()));
                    where = Where.METRONOME;
                }
            }
            case METRONOME -> {
                if (qName.equals(MusicXmlTags.METRONOME)) {
                    where = Where.DIRECTION_TYPE;
                }
            }
            case METRONOME_TYPE -> {
                if (qName.equals(MusicXmlTags.METRONOME_TYPE)) {
                    metronome.setMetronomeType(value.toString().trim());
                    where = Where.METRONOME_NOTE;
                }
            }
            case METRONOME_DOT -> {
                if (qName.equals(MusicXmlTags.METRONOME_DOT)) {
                    where = Where.METRONOME_NOTE;
                }
            }
            case METRONOME_NOTE -> {
                if (qName.equals(MusicXmlTags.METRONOME_NOTE)) {
                    metronome.endMetronomeNote();
                    where = Where.METRONOME;
                }
            }
            case METRONOME_RELATION -> {
                if (qName.equals(MusicXmlTags.METRONOME_RELATION)) {
                    where = Where.METRONOME;
                }
            }
            case WORDS -> {
                if (qName.equals(MusicXmlTags.WORDS)) {
                    metronome.setWords(value.toString());
                    where = Where.DIRECTION_TYPE;
                }
            }
            case SOUND -> {
                if (qName.equals(MusicXmlTags.SOUND)) {
                    where = Where.DIRECTION;
                }
            }
            case DIRECTION_TYPE -> {
                if (qName.equals(MusicXmlTags.DIRECTION_TYPE)) {
                    where = Where.DIRECTION;
                }
            }
            case DIRECTION -> {
                if (qName.equals(MusicXmlTags.DIRECTION)) {
                    // Build any accumulated metronome tempo now; it binds to the
                    // next note (see MetronomeResolver).
                    metronome.endDirection();
                    where = Where.MEASURE;
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
                    barlines.flushPendingRepeatRight();
                    spans.flushPendingSlideStart();
                    spans.flushPendingSpanStarts();
                    wedges.flushPendingWedge();
                    metronome.flushPendingTempo();
                    metronome.flushPendingBeatChange();
                    endings.flushPendingEnding();
                    // Commit the final line now that all its elements are in place.
                    commitCurrentLine();
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
                        // Restore the terminal invariant while tracking is still
                        // suspended so the fix-up is silent: the writer emits a line's
                        // closing barline only as a real terminal, but a hand-authored
                        // or partial file may leave the last line ending in a note or a
                        // non-terminal barline.
                        song.installTerminalAfterParsing();
                        applyInitialTempo();
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
        barlines.flushPendingRepeatRight();

        // Hairpins and endings are intra-line range spans; flush any still open
        // against the line that is now ending, before the new line becomes current
        // (the build/drop targets currentLine). flushPendingRepeatRight ran first so
        // a deferred REPEAT_RIGHT carrying an ending end marker is already appended.
        endings.flushPendingEnding();
        wedges.flushPendingWedge();

        // Commit the line that just ended, then start the next one detached from
        // the song. The new line is NOT added until it, too, is complete (next
        // line break or </part>) — see commitCurrentLine. While a line is detached
        // it is never the song's last line, so Line.addElement appends elements in
        // their exact document order instead of inserting them before a valid-
        // terminal barline (REPEAT_RIGHT / FINAL_DOUBLE_BARLINE) that lands mid-line.
        commitCurrentLine();
        currentLine = new Line(song);

        // A key signature persists until restated. For every line after the first,
        // seed the new line with the running key so lines that keep it (the writer
        // emits no <key> for them) still round-trip; a <key> in this line's first
        // measure overrides this via the FIFTHS handler. The first line is skipped:
        // its key is the song default, materialized when the line is added.
        if (songDefaultKeySet) {
            applyFifthsToLine(currentLine, runningFifths);
        }
    }

    /**
     * Applies a signed-fifths key signature to {@code line}, setting both its key
     * type and accidental count. Inverse of the writer's fifths encoding.
     */
    private void applyFifthsToLine(Line line, int fifths) {
        line.setKeyType(KeySignatureMapping.keyType(fifths));
        line.setKeyAccidentalCount(KeySignatureMapping.accidentalCount(fifths));
    }

    /**
     * Adds the current line to the song once all its elements are in place. The
     * reader builds each line detached and commits it here at the next line break
     * or at {@code </part>}, mirroring the build-then-add order the test
     * {@code buildSong} helper relies on: a line that is the song's last line
     * while elements are still being appended would have its auto-maintained
     * terminal slot reorder a mid-line {@code REPEAT_RIGHT}/{@code FINAL_DOUBLE_BARLINE}.
     */
    private void commitCurrentLine() {
        // A non-null currentLine was constructed with a non-null song; the song
        // check keeps NullAway satisfied without changing behavior.
        if (song != null && currentLine != null) {
            song.addLine(currentLine);
        }
    }

    /**
     * Resets the note-in-progress state and captures the {@code <note>}'s
     * {@code relative-x} offset attribute. The {@code default-x} attribute (the
     * write-forward computed base X) is intentionally ignored — layout recomputes
     * it on load.
     */
    private void startNote(Attributes attributes) throws SAXException {
        note.reset();

        var relativeX = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_X);

        if (relativeX != null) {
            note.setRelativeX(parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_X, relativeX));
        }

        where = Where.NOTE;
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

        // A note can never be the forward half of a REPEAT_LEFT_RIGHT pair, so a
        // REPEAT_RIGHT still held pending here is a standalone backward repeat that
        // precedes this note. Flush it first so it lands ahead of the note in the
        // element order instead of being deferred past it to the next barline.
        barlines.flushPendingRepeatRight();

        var element = note.appendStaffElement(currentLine);
        var markers = note.spanMarkers();

        spans.resolveSlide(element, markers);

        // Collapse this note's per-note range-span markers into RangeElements,
        // pairing each run's pending anchor with its closing marker.
        spans.resolveBeam(currentLine, element, markers);
        spans.resolveTie(currentLine, element, markers);
        spans.resolveTuplet(currentLine, element, markers);
        spans.resolveTrill(currentLine, element, markers);
        wedges.resolveWedge(currentLine, element);
        metronome.resolveTempo(element);
        metronome.resolveBeatChange(element);

        // A breath-mark attached to this note's <notations> becomes a standalone
        // BREATH_MARK element immediately after the note.
        if (note.hasBreathMark()) {
            appendToCurrentLine(ElementType.BREATH_MARK);
        }
    }

    /**
     * Reads an optional {@code tenths}-valued attribute and converts it to
     * SongScribe staff-spaces, returning 0 when the attribute is absent.
     */
    static int optionalTenthsAttrToSs(Attributes attributes, String attrName) throws SAXException {
        var raw = attributes.getValue(attrName);

        if (raw == null) {
            return 0;
        }

        return tenthsToSs(parseDoubleOrThrow(attrName, raw));
    }

    /**
     * Converts a MusicXML {@code relative-y}/{@code relative-x} value in tenths to
     * SongScribe staff-spaces (tenths ÷ 10), rounded to the nearest integer.
     */
    static int tenthsToSs(double tenths) {
        return (int) Math.round(tenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE);
    }

    /**
     * Appends a structural {@link ElementType} to the current line.
     *
     * <p>Structural (non-duration) elements are created via {@link ElementType#newInstance()},
     * which clones the pre-built default instance — the same pattern used by
     * {@code StaffElementIO.StaffElementReader} for barline elements.
     *
     * <p>Package-private (not private): {@link BarlineParser} appends barline
     * elements through this reader rather than duplicating line ownership.
     */
    StaffElement appendToCurrentLine(ElementType elementType) throws SAXException {
        if (currentLine == null) {
            throw new SAXException(
                "Barline element encountered before any line was started"
            );
        }

        var element = elementType.newInstance();
        currentLine.addElement(element);
        return element;
    }

    /**
     * Restores the song-level base tempo after assembly. The base tempo is
     * anchored on the first element of the first line (mirroring
     * {@code Line.attachInitialTempoIfNeeded}), so when that element carries a
     * {@link TempoChangeAttachment}, its tempo is the song's base tempo.
     */
    private void applyInitialTempo() {
        if (song == null || song.lineCount() == 0) {
            return;
        }

        var firstLine = song.getLine(0);

        if (firstLine.elementCount() == 0) {
            return;
        }

        var attachment = firstLine.getElement(0).findAttachment(TempoChangeAttachment.class);

        if (attachment != null) {
            song.setTempo(attachment.getTempo());
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
    static double parseDoubleOrThrow(String attr, String raw) throws SAXException {
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

        // Per-note lyric subtree (<lyric><syllabic>/<text>/<extend>).
        LYRIC,
        SYLLABIC,
        LYRIC_TEXT,
        EXTEND,

        // Per-note range-span subtree (beam, tie, tuplet, trill).
        BEAM,
        TIE,
        TIME_MODIFICATION,
        ACTUAL_NOTES,
        NORMAL_NOTES,
        TIED,
        TUPLET,
        ORNAMENTS,
        TRILL_MARK,
        WAVY_LINE,

        // Measure-level hairpin wedge subtree (<direction><direction-type><wedge>).
        DIRECTION,
        DIRECTION_TYPE,
        WEDGE,

        // Measure-level tempo direction subtree
        // (<direction><direction-type><metronome>/<words>, <sound>).
        METRONOME,
        BEAT_UNIT,
        BEAT_UNIT_DOT,
        PER_MINUTE,
        WORDS,
        SOUND,

        // Measure-level metric-modulation subtree
        // (<metronome><metronome-note><metronome-type>/<metronome-dot>,
        // <metronome-relation>).
        METRONOME_NOTE,
        METRONOME_TYPE,
        METRONOME_DOT,
        METRONOME_RELATION,
    }
}
