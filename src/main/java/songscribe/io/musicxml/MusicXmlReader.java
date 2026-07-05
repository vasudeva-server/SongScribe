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
import songscribe.dom.SongMetadata;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.io.DocumentValidation;
import songscribe.io.SongLoadResult;
import songscribe.util.DateUtils;

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

    // Measure-level annotation direction state machine — see AnnotationResolver.
    private final AnnotationResolver annotations = new AnnotationResolver();

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
    // Credit-reconstruction state — accumulated per <credit> subtree and routed
    // at </credit> (see dispatchCredit). The subtitle is the one canonical head
    // field carried by a credit (there is no <movement-*> equivalent); it is held
    // here and folded into SongMetadata at the terminal </score-partwise>.
    // -------------------------------------------------------------------------

    // The <credit-type> text of the credit currently being read.
    private String creditType = "";

    // The <credit-words> text of the credit currently being read.
    private String creditWords = "";

    // The raw relative-y attribute of the current <credit-words>, or null when
    // absent. Only attribution credits carry it (see MusicXmlWriter.writeCredit).
    @Nullable
    private String creditWordsRelativeYRaw = null;

    // The subtitle recovered from the subtitle credit; empty when the document
    // carries no subtitle credit (a blank subtitle is never written, so absent
    // and empty are indistinguishable). Folded into SongMetadata at
    // </score-partwise>.
    private String subtitle = "";

    // True once the attribution Y offset has been recovered. The writer emits the
    // same relative-y on every attribution credit, so only the first is read.
    private boolean attributionOffsetRead = false;

    // -------------------------------------------------------------------------
    // Head-metadata scratch — accumulated from <movement-*> and <identification>
    // (creators + <miscellaneous> fields) and assembled, together with the
    // credit-derived subtitle above, into a single SongMetadata record at the
    // terminal </score-partwise> (see applyHeadMetadata). Defaults mirror the
    // SongMetadata defaults so an absent element leaves its field at the value a
    // blank document would carry. Write-forward head elements (<rights>,
    // <software>, <encoding-date>, <supports>) are consumed but not read.
    // -------------------------------------------------------------------------

    private String headTitle = "";
    private String headNumber = "";
    private String headPlace = "";

    // Composer/lyricist default to empty; SongMetadata coerces empty to
    // SRI_CHINMOY, matching the value a document with no <creator> would carry.
    private String headComposer = "";
    private String headLyricist = "";

    // Set true when a <creator type="arranger"> is seen; the flag is the only
    // information the arranger creator carries (its text is always SRI_CHINMOY).
    private boolean headArrangement = false;

    private boolean headUnofficialTranslation = false;
    private Song.LyricsSource headLyricsSource = Song.LyricsSource.LYRICIST;

    // Composition date (composition-date misc-field).
    private String headYear = "";
    private int headMonth = 0;
    private int headDay = 0;

    // Lyrics/words date (lyrics-date misc-field).
    private String headWordsYear = "";
    private int headWordsMonth = 0;
    private int headWordsDay = 0;

    // The type attribute of the <creator> currently being read, routed at
    // </creator>; null when the element omits it (then ignored).
    @Nullable
    private String creatorType = null;

    // The name attribute of the <miscellaneous-field> currently being read,
    // routed at </miscellaneous-field>; null when the element omits it.
    @Nullable
    private String miscFieldName = null;

    // -------------------------------------------------------------------------
    // Defaults-reconstruction state — the document fonts recovered from
    // <defaults> (<word-font> → ANNOTATION, <lyric-font> → LYRICS) and the
    // <miscellaneous> sub-attribution-font/-size fields (→ SUB_ATTRIBUTION).
    // Starts from the canonical default set; each recovered role overrides one
    // entry. A document with no <defaults> fonts leaves this at the defaults,
    // which is returned as-is via SongLoadResult.Success. The line width rides in
    // <page-width> and the row-height delta in a misc-field; both are applied
    // straight onto the song.
    // -------------------------------------------------------------------------

    private final DocumentFonts documentFonts = DocumentFonts.defaultFonts();

    // The sub-attribution font arrives as two separate <miscellaneous-field>s
    // (family, then size); both must be present before the role can be resolved.
    @Nullable
    private String subAttributionFontFamily = null;
    @Nullable
    private Integer subAttributionFontSize = null;

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
     * the resulting {@link SongLoadResult.Success} (the parsed song plus its
     * document fonts; the warning is always {@code null}, as MusicXML parsing has
     * no load-warning path — failure surfaces as a thrown exception).
     * <p>
     * When the document carries no {@code <defaults>} font block, the result's
     * fonts default to {@link DocumentFonts#defaultFonts()}.
     *
     * @param source the MusicXML input to parse
     * @return the parsed song plus its document fonts
     * @throws IOException  on I/O errors
     * @throws SAXException on parse errors
     */
    public static SongLoadResult.Success read(InputSource source) throws IOException, SAXException {
        try {
            var parser = PARSER_FACTORY.newSAXParser();
            var handler = new MusicXmlReader();
            parser.parse(source, handler);
            return new SongLoadResult.Success(handler.getSong(), handler.documentFonts, null);
        } catch (ParserConfigurationException e) {
            throw new SAXException("Failed to create SAX parser", e);
        }
    }

    /**
     * Parses a MusicXML document from the given {@link File} and returns the
     * resulting {@link SongLoadResult.Success}.
     *
     * @param file the MusicXML file to parse
     * @return the parsed song plus its document fonts
     * @throws IOException  on I/O errors
     * @throws SAXException on parse errors
     */
    public static SongLoadResult.Success read(File file) throws IOException, SAXException {
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
                if (qName.equals(MusicXmlTags.MOVEMENT_TITLE)) {
                    where = Where.MOVEMENT_TITLE;
                } else if (qName.equals(MusicXmlTags.MOVEMENT_NUMBER)) {
                    where = Where.MOVEMENT_NUMBER;
                } else if (qName.equals(MusicXmlTags.IDENTIFICATION)) {
                    where = Where.IDENTIFICATION;
                } else if (qName.equals(MusicXmlTags.DEFAULTS)) {
                    where = Where.DEFAULTS;
                } else if (qName.equals(MusicXmlTags.PART_LIST)) {
                    where = Where.PART_LIST;
                } else if (qName.equals(MusicXmlTags.PART)) {
                    where = Where.PART;
                } else if (qName.equals(MusicXmlTags.CREDIT)) {
                    // Reset the per-credit accumulators; the subtree's
                    // <credit-type> and <credit-words> fill them and </credit>
                    // routes on them.
                    creditType = "";
                    creditWords = "";
                    creditWordsRelativeYRaw = null;
                    where = Where.CREDIT;
                }
            }
            case IDENTIFICATION -> {
                if (qName.equals(MusicXmlTags.CREATOR)) {
                    // Capture the routing type here; the name text arrives at
                    // </creator>.
                    creatorType = attributes.getValue(MusicXmlTags.ATTR_TYPE);
                    where = Where.CREATOR;
                } else if (qName.equals(MusicXmlTags.RIGHTS)) {
                    where = Where.RIGHTS;
                } else if (qName.equals(MusicXmlTags.ENCODING)) {
                    where = Where.ENCODING;
                } else if (qName.equals(MusicXmlTags.MISCELLANEOUS)) {
                    where = Where.MISCELLANEOUS;
                }
            }
            case ENCODING -> {
                // <software>/<encoding-date> are write-forward: their subtrees are
                // consumed so their text does not leak, but nothing is read back.
                // <supports> is an empty element with no state, skipped in place.
                if (qName.equals(MusicXmlTags.SOFTWARE)) {
                    where = Where.SOFTWARE;
                } else if (qName.equals(MusicXmlTags.ENCODING_DATE)) {
                    where = Where.ENCODING_DATE;
                }
            }
            case MISCELLANEOUS -> {
                if (qName.equals(MusicXmlTags.MISCELLANEOUS_FIELD)) {
                    // Capture the routing name here; the value text arrives at
                    // </miscellaneous-field>.
                    miscFieldName = attributes.getValue(MusicXmlTags.ATTR_NAME);
                    where = Where.MISCELLANEOUS_FIELD;
                }
            }
            case DEFAULTS -> {
                // <scaling>/<staff-layout> and their leaves are write-forward and
                // consumed by their own states; <music-font>/<lyric-language> are
                // empty write-forward elements skipped in place. Only <page-layout>
                // (for <page-width>) and the <word-font>/<lyric-font> roles read.
                if (qName.equals(MusicXmlTags.SCALING)) {
                    where = Where.DEFAULTS_SCALING;
                } else if (qName.equals(MusicXmlTags.PAGE_LAYOUT)) {
                    where = Where.DEFAULTS_PAGE_LAYOUT;
                } else if (qName.equals(MusicXmlTags.STAFF_LAYOUT)) {
                    where = Where.DEFAULTS_STAFF_LAYOUT;
                } else if (qName.equals(MusicXmlTags.WORD_FONT)) {
                    setDocumentFont(FontKey.ANNOTATION, attributes);
                } else if (qName.equals(MusicXmlTags.LYRIC_FONT)) {
                    setDocumentFont(FontKey.LYRICS, attributes);
                }
            }
            case DEFAULTS_PAGE_LAYOUT -> {
                // <page-height> is write-forward, consumed by staying in place;
                // <page-width> carries the model line width.
                if (qName.equals(MusicXmlTags.PAGE_WIDTH)) {
                    where = Where.DEFAULTS_PAGE_WIDTH;
                }
            }
            case CREDIT -> {
                if (qName.equals(MusicXmlTags.CREDIT_TYPE)) {
                    where = Where.CREDIT_TYPE;
                } else if (qName.equals(MusicXmlTags.CREDIT_WORDS)) {
                    // relative-y is captured here from the attributes; the text
                    // arrives at </credit-words>. Font attributes are write-forward
                    // (recovered from <defaults>) and are not read.
                    creditWordsRelativeYRaw = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_Y);
                    where = Where.CREDIT_WORDS;
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
                    // A placement attribute (and no <metronome>) marks this
                    // direction as an annotation; tempo/metric-mod/wedge
                    // directions never carry placement — see AnnotationResolver.
                    annotations.beginDirection(
                        AnnotationResolver.placementFor(attributes.getValue(MusicXmlTags.ATTR_PLACEMENT))
                    );
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
                    // For an annotation direction, the halign and relative-y
                    // recover the annotation's alignment and user Y offset; the
                    // text arrives at </words>.
                    if (annotations.isAnnotationDirection()) {
                        annotations.setXAlignment(
                            TextAlignmentMapping.xAlignment(attributes.getValue(MusicXmlTags.ATTR_HALIGN))
                        );
                        annotations.setUserYOffsetSs(
                            optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_Y)
                        );
                    }

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
                    // An annotation direction's words build its Annotation; a
                    // tempo direction's words are its optional description.
                    if (annotations.isAnnotationDirection()) {
                        annotations.setWords(value.toString());
                    } else {
                        metronome.setWords(value.toString());
                    }

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
                    // Build any accumulated metronome tempo or annotation now; each
                    // binds to the next note (see MetronomeResolver /
                    // AnnotationResolver). Exactly one builds per direction: a
                    // metronome direction carries no placement, an annotation
                    // direction carries no <metronome>.
                    metronome.endDirection();
                    annotations.endDirection();
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
                    annotations.flushPendingAnnotation();
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
            case MOVEMENT_TITLE -> {
                if (qName.equals(MusicXmlTags.MOVEMENT_TITLE)) {
                    headTitle = value.toString();
                    where = Where.SCORE_PARTWISE;
                }
            }
            case MOVEMENT_NUMBER -> {
                if (qName.equals(MusicXmlTags.MOVEMENT_NUMBER)) {
                    headNumber = value.toString();
                    where = Where.SCORE_PARTWISE;
                }
            }
            case IDENTIFICATION -> {
                if (qName.equals(MusicXmlTags.IDENTIFICATION)) {
                    where = Where.SCORE_PARTWISE;
                }
            }
            case CREATOR -> {
                if (qName.equals(MusicXmlTags.CREATOR)) {
                    applyCreator(value.toString());
                    where = Where.IDENTIFICATION;
                }
            }
            case RIGHTS -> {
                // Write-forward (a fixed copyright string); consumed, not read.
                if (qName.equals(MusicXmlTags.RIGHTS)) {
                    where = Where.IDENTIFICATION;
                }
            }
            case ENCODING -> {
                if (qName.equals(MusicXmlTags.ENCODING)) {
                    where = Where.IDENTIFICATION;
                }
            }
            case SOFTWARE -> {
                // Write-forward; consumed, not read.
                if (qName.equals(MusicXmlTags.SOFTWARE)) {
                    where = Where.ENCODING;
                }
            }
            case ENCODING_DATE -> {
                // Write-forward; consumed, not read.
                if (qName.equals(MusicXmlTags.ENCODING_DATE)) {
                    where = Where.ENCODING;
                }
            }
            case MISCELLANEOUS -> {
                if (qName.equals(MusicXmlTags.MISCELLANEOUS)) {
                    where = Where.IDENTIFICATION;
                }
            }
            case MISCELLANEOUS_FIELD -> {
                if (qName.equals(MusicXmlTags.MISCELLANEOUS_FIELD)) {
                    applyMiscField(miscFieldName, value.toString());
                    where = Where.MISCELLANEOUS;
                }
            }
            case DEFAULTS -> {
                if (qName.equals(MusicXmlTags.DEFAULTS)) {
                    where = Where.SCORE_PARTWISE;
                }
            }
            case DEFAULTS_SCALING -> {
                if (qName.equals(MusicXmlTags.SCALING)) {
                    where = Where.DEFAULTS;
                }
            }
            case DEFAULTS_PAGE_LAYOUT -> {
                if (qName.equals(MusicXmlTags.PAGE_LAYOUT)) {
                    where = Where.DEFAULTS;
                }
            }
            case DEFAULTS_PAGE_WIDTH -> {
                if (qName.equals(MusicXmlTags.PAGE_WIDTH)) {
                    // page-width (tenths) → line width (staff spaces). Write-forward
                    // <page-height>/<scaling> are ignored, so the recovered width is
                    // the sole canonical page-layout value.
                    if (song != null) {
                        song.setLineWidthSs(
                            tenthsToSs(parseDoubleOrThrow(MusicXmlTags.PAGE_WIDTH, value.toString()))
                        );
                    }

                    where = Where.DEFAULTS_PAGE_LAYOUT;
                }
            }
            case DEFAULTS_STAFF_LAYOUT -> {
                if (qName.equals(MusicXmlTags.STAFF_LAYOUT)) {
                    where = Where.DEFAULTS;
                }
            }
            case CREDIT_TYPE -> {
                if (qName.equals(MusicXmlTags.CREDIT_TYPE)) {
                    creditType = value.toString();
                    where = Where.CREDIT;
                }
            }
            case CREDIT_WORDS -> {
                if (qName.equals(MusicXmlTags.CREDIT_WORDS)) {
                    // P-1: read the accumulated text only here, at the end element,
                    // so a long credit split across multiple SAX characters()
                    // chunks (footnotes/underlyrics) is not truncated.
                    creditWords = value.toString();
                    where = Where.CREDIT;
                }
            }
            case CREDIT -> {
                if (qName.equals(MusicXmlTags.CREDIT)) {
                    dispatchCredit();
                    where = Where.SCORE_PARTWISE;
                }
            }
            case SCORE_PARTWISE -> {
                if (qName.equals(MusicXmlTags.SCORE_PARTWISE)) {
                    if (song != null) {
                        // Assemble the accumulated head scratch and the credit-
                        // derived subtitle into SongMetadata, then restore the
                        // terminal invariant while tracking is still suspended so
                        // the fix-ups are silent: the writer emits a line's closing
                        // barline only as a real terminal, but a hand-authored or
                        // partial file may leave the last line ending in a note or a
                        // non-terminal barline.
                        applyHeadMetadata();
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
        annotations.resolveAnnotation(element);

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
     * Routes a fully-read {@code <credit>} by its {@code <credit-type>}. Every
     * credit routes into exactly one of three classes; the reader treats each
     * differently. This is the read side of the writer's data-flow contract: the
     * same value (composer, dates, place) is emitted in BOTH head (canonical) and
     * a credit (display-only), and the reader MUST take head and ignore the credit
     * or a hand-edited credit corrupts the model.
     *
     * <pre>
     *                          WRITER                          READER
     *   Song field ─────────────┬─────────────────┐
     *                           │                 │
     *    ┌──────────────────────▼───┐   ┌─────────▼────────────┐
     *    │ HEAD (identification/     │   │ CREDIT (&lt;credit&gt;)    │
     *    │  movement/miscellaneous)  │   │  fonts + positions   │
     *    └──────────┬────────────────┘   └───┬──────────────┬───┘
     *               │                         │              │
     *    ┌──────────▼──────────┐  ┌───────────▼───┐  ┌───────▼─────────────┐
     *    │ CANONICAL           │  │ DISPLAY-ONLY  │  │ WRITE-FORWARD       │
     *    │ read → model        │  │ ignored;      │  │ ignored;            │
     *    │                     │  │ re-derived    │  │ recomputed/constant │
     *    ├─────────────────────┤  ├───────────────┤  ├─────────────────────┤
     *    │ subtitle credit     │  │ title credit  │  │ rights, software,   │
     *    │ 4 score-below credit│  │ composer/     │  │ encoding-date,      │
     *    │ attribution rel-y   │  │  lyricist/    │  │ supports, scaling,  │
     *    │                     │  │  arranger/    │  │ music-font,         │
     *    │                     │  │  date/rights/ │  │ default-x/default-y │
     *    │                     │  │  place credits│  │ (external renderer) │
     *    └─────────────────────┘  └───────────────┘  └─────────────────────┘
     * </pre>
     */
    private void dispatchCredit() throws SAXException {
        var parsedSong = song;

        if (parsedSong == null) {
            return;
        }

        switch (creditType) {
            // Canonical — the subtitle has no <movement-*> equivalent, so the
            // credit is its source of truth. Held until </score-partwise>, where
            // it is folded into SongMetadata (there is no setSubtitle mutator).
            case MusicXmlTags.CREDIT_SUBTITLE -> subtitle = creditWords;

            // Canonical — the four score-below text blocks are standalone Song
            // fields with direct setters.
            case MusicXmlTags.CREDIT_UNDERLYRICS -> parsedSong.setUnderLyrics(creditWords);
            case MusicXmlTags.CREDIT_BANGLA_LYRICS -> parsedSong.setBanglaLyrics(creditWords);
            case MusicXmlTags.CREDIT_TRANSLATION -> parsedSong.setTranslatedLyrics(creditWords);
            case MusicXmlTags.CREDIT_FOOTNOTES -> parsedSong.setFootnotes(creditWords);

            // Display-only attribution roles — the text is re-derived from the
            // head <creator>/<rights>/misc-fields, so it is ignored; only the
            // shared relative-y (the attribution user Y offset) is recovered, once.
            case MusicXmlTags.CREDIT_COMPOSER,
                 MusicXmlTags.CREDIT_LYRICIST,
                 MusicXmlTags.CREDIT_ARRANGER,
                 MusicXmlTags.CREDIT_COMPOSITION_DATE,
                 MusicXmlTags.CREDIT_LYRICS_DATE,
                 MusicXmlTags.CREDIT_RIGHTS,
                 MusicXmlTags.CREDIT_PLACE -> readAttributionOffsetOnce(parsedSong);

            // Display-only — the title is re-derived from <movement-*>; ignored.
            // Unknown credit-types are skipped for the same reason.
            default -> {
                // no read state
            }
        }
    }

    /**
     * Recovers the attribution user Y offset from the current attribution credit's
     * {@code relative-y}, but only from the first attribution credit that carries
     * it — the writer emits the same {@code relative-y} on every attribution
     * credit, so reading it once is sufficient.
     */
    private void readAttributionOffsetOnce(Song parsedSong) throws SAXException {
        if (attributionOffsetRead || creditWordsRelativeYRaw == null) {
            return;
        }

        var offsetTenths = parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_Y, creditWordsRelativeYRaw);
        parsedSong.getAttributionElement().setUserYOffsetSs(tenthsToSs(offsetTenths));
        attributionOffsetRead = true;
    }

    /**
     * Routes a {@code <creator>}'s text by its captured {@code type} attribute:
     * composer/lyricist into the head scratch, arranger into the arrangement flag
     * (its text is always {@code SRI_CHINMOY}, so only the presence matters).
     * Unknown or missing types are ignored.
     */
    private void applyCreator(String text) {
        if (MusicXmlTags.CREATOR_COMPOSER.equals(creatorType)) {
            headComposer = text;
        } else if (MusicXmlTags.CREATOR_LYRICIST.equals(creatorType)) {
            headLyricist = text;
        } else if (MusicXmlTags.CREATOR_ARRANGER.equals(creatorType)) {
            headArrangement = true;
        }
    }

    /**
     * Routes a {@code <miscellaneous-field>}'s text by its captured {@code name}
     * attribute. The head fields go into the metadata scratch; the two dates go
     * through the shared {@link DateUtils#parseIsoDate} inverse of the writer's
     * {@code toIsoDate} (a malformed date parses to {@code null} and is treated as
     * absent, keeping the scratch date fields at their empty defaults). The
     * defaults residuals — {@code row-height-adjustment} and the sub-attribution
     * font — are applied straight onto the song / {@link #documentFonts}. Unknown
     * misc-fields are ignored.
     */
    private void applyMiscField(@Nullable String name, String text) throws SAXException {
        if (MusicXmlTags.MISC_COMPOSITION_DATE.equals(name)) {
            var parts = DateUtils.parseIsoDate(text);

            if (parts != null) {
                headYear = parts.year();
                headMonth = parts.month();
                headDay = parts.day();
            }
        } else if (MusicXmlTags.MISC_LYRICS_DATE.equals(name)) {
            var parts = DateUtils.parseIsoDate(text);

            if (parts != null) {
                headWordsYear = parts.year();
                headWordsMonth = parts.month();
                headWordsDay = parts.day();
            }
        } else if (MusicXmlTags.MISC_COMPOSITION_PLACE.equals(name)) {
            headPlace = text;
        } else if (MusicXmlTags.MISC_LYRICS_SOURCE.equals(name)) {
            headLyricsSource = lyricsSourceOrThrow(text);
        } else if (MusicXmlTags.MISC_UNOFFICIAL_TRANSLATION.equals(name)) {
            headUnofficialTranslation = Boolean.parseBoolean(text);
        } else if (MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT.equals(name)) {
            // A staff-space delta stored verbatim (the writer omits it when 0).
            if (song != null) {
                song.setRowHeightAdjustmentSs(
                    parseDoubleOrThrow(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, text)
                );
            }
        } else if (MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT.equals(name)) {
            subAttributionFontFamily = text;
            applySubAttributionFont();
        } else if (MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE.equals(name)) {
            subAttributionFontSize = parseIntOrThrow(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, text);
            applySubAttributionFont();
        }
    }

    /**
     * Recovers a document-font role from a {@code <word-font>}/{@code <lyric-font>}
     * element's {@code font-family}/{@code font-size} attributes into
     * {@link #documentFonts}. Weight/style are write-forward (not emitted, not
     * read). An element missing either attribute is left at its default — defensive
     * only, since the writer always emits both.
     */
    private void setDocumentFont(FontKey key, Attributes attributes) throws SAXException {
        var family = attributes.getValue(MusicXmlTags.ATTR_FONT_FAMILY);
        var sizeRaw = attributes.getValue(MusicXmlTags.ATTR_FONT_SIZE);

        if (family == null || sizeRaw == null) {
            return;
        }

        // font-size is a schema decimal; the writer emits an integer point size.
        var size = (int) Math.round(parseDoubleOrThrow(MusicXmlTags.ATTR_FONT_SIZE, sizeRaw));
        documentFonts.setFont(key, family, size);
    }

    /**
     * Sets the {@link FontKey#SUB_ATTRIBUTION} role once both its family and size
     * misc-fields have been read — they arrive as two separate
     * {@code <miscellaneous-field>}s, so neither half alone can resolve the font.
     */
    private void applySubAttributionFont() {
        if (subAttributionFontFamily != null && subAttributionFontSize != null) {
            documentFonts.setFont(FontKey.SUB_ATTRIBUTION, subAttributionFontFamily, subAttributionFontSize);
        }
    }

    /**
     * Resolves a {@code lyrics-source} token to a {@link Song.LyricsSource},
     * throwing a {@link SAXException} on an unknown token. Fails hard rather than
     * defaulting, matching the reader's {@code parseIntOrThrow}/
     * {@code parseDoubleOrThrow} convention — the writer only ever emits an enum
     * constant name, so an unknown token means a corrupt document.
     */
    private static Song.LyricsSource lyricsSourceOrThrow(String token) throws SAXException {
        try {
            return Song.LyricsSource.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new SAXException(
                "Corrupt document: malformed <" + MusicXmlTags.MISC_LYRICS_SOURCE +
                "> value: '" + token + "'", e
            );
        }
    }

    /**
     * Assembles the accumulated head scratch and the credit-derived subtitle into
     * the song's {@link SongMetadata} at the terminal {@code </score-partwise>},
     * mirroring {@code SongIO.DocumentReader -> Song.loadFrom} (one all-args
     * construction). Building the record once here — rather than piecemeal at each
     * container's end — dissolves the {@code </identification>}-before-
     * {@code </credit>} ordering hazard (the subtitle credit follows the head), and
     * needs no {@code setSubtitle}/{@code withSubtitle} mutator (neither exists).
     * Runs while mutation tracking is still suspended, so the assembly is silent.
     */
    private void applyHeadMetadata() {
        var parsedSong = song;

        if (parsedSong == null) {
            return;
        }

        // subtitle is empty when the document carries no subtitle credit,
        // matching a blank document. The SongMetadata compact constructor
        // normalizes/coerces every field, so the scratch values need no
        // pre-normalization here.
        parsedSong.setMetadata(new SongMetadata(
            headTitle, headNumber, headPlace,
            headYear, headMonth, headDay,
            headComposer, headLyricist, headLyricsSource,
            headArrangement, headUnofficialTranslation,
            subtitle,
            headWordsYear, headWordsMonth, headWordsDay
        ));
    }

    /**
     * Parses {@code raw} (trimmed) as an integer, throwing a {@link SAXException}
     * if it is not a valid integer. Delegates to the shared
     * {@link DocumentValidation#parseIntOrThrow}, supplying the reader's logger so
     * the {@code .mssw} and MusicXML readers report corrupt values one way. The
     * trim tolerates the surrounding whitespace SAX character data can carry.
     */
    private static int parseIntOrThrow(String tag, String raw) throws SAXException {
        return DocumentValidation.parseIntOrThrow(LOG, tag, raw.trim());
    }

    /**
     * Parses {@code raw} (trimmed) as a double, throwing a {@link SAXException} if
     * it is not a valid number. Used for positional attributes (tenths), which
     * MusicXML permits to be fractional. Delegates to the shared
     * {@link DocumentValidation#parseDoubleOrThrow}, supplying the reader's logger.
     */
    static double parseDoubleOrThrow(String attr, String raw) throws SAXException {
        return DocumentValidation.parseDoubleOrThrow(LOG, attr, raw.trim());
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

        // Score-header metadata subtree (<movement-*>, <identification> with its
        // <creator>/<rights>/<encoding>/<miscellaneous> children). Each container
        // has its own state so its subtree is consumed cleanly and unknown leaves
        // are skipped; the write-forward leaves (<rights>/<software>/
        // <encoding-date>) are consumed but not read.
        MOVEMENT_TITLE,
        MOVEMENT_NUMBER,
        IDENTIFICATION,
        CREATOR,
        RIGHTS,
        ENCODING,
        SOFTWARE,
        ENCODING_DATE,
        MISCELLANEOUS,
        MISCELLANEOUS_FIELD,

        // Score-header <defaults> subtree. <scaling>/<staff-layout> and
        // <page-height> are write-forward (consumed, not read); <page-width>
        // carries the line width, and <word-font>/<lyric-font> the document
        // fonts. <music-font>/<lyric-language> are empty write-forward elements
        // skipped in place under DEFAULTS.
        DEFAULTS,
        DEFAULTS_SCALING,
        DEFAULTS_PAGE_LAYOUT,
        DEFAULTS_PAGE_WIDTH,
        DEFAULTS_STAFF_LAYOUT,

        // Score-header credit subtree (<credit><credit-type>/<credit-words>).
        CREDIT,
        CREDIT_TYPE,
        CREDIT_WORDS,

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
