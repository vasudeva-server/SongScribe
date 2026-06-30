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
import java.util.ArrayList;
import java.util.List;

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
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.io.musicxml.WedgeTypeMapping.WedgeKind;
import songscribe.layout.Ending;

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

    // <ending> markers collected while parsing the current <barline> (reset at
    // every <barline> start). Resolved to the barline's StaffElement once it is
    // appended — see attachBarlineEndings.
    private final List<EndingMarker> currentBarlineEndings = new ArrayList<>();

    // <ending> markers held alongside a deferred REPEAT_RIGHT (pendingRepeatRight):
    // a REPEAT_RIGHT is not appended at parse time, so its ending markers wait here
    // until the element is created (flushed or merged into REPEAT_LEFT_RIGHT).
    private List<EndingMarker> pendingRepeatRightEndings = List.of();

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

    // -------------------------------------------------------------------------
    // Per-note range-span markers (beam, tie, tuplet, trill).
    //
    // Each per-note span is a run over consecutive notes in [anchor, end]. The
    // writer distributes a begin/continue/end (or start/stop) marker per note;
    // the reader re-collapses a maximal marked run into one RangeElement by
    // holding the run's anchor note in a pending field (mirroring
    // pendingSlideStart) and pairing it with the closing marker in finishNote.
    //
    //   Where states (note children + <notations> descendants):
    //
    //     NOTE
    //       ├─ TIE (sound, @type)          ─► ignored (write-forward only)
    //       ├─ TIME_MODIFICATION
    //       │    ├─ ACTUAL_NOTES (text)    ─► noteActualNotes (tuplet grade)
    //       │    └─ NORMAL_NOTES (text)    ─► ignored (write-forward only)
    //       ├─ BEAM (@number, text)        ─► noteBeam1Type (number=1 only)
    //       └─ NOTATIONS
    //            ├─ TIED (@type)           ─► noteTiedStart / noteTiedStop
    //            ├─ TUPLET (@type,@rel-y)  ─► noteTupletStart / noteTupletStop
    //            └─ ORNAMENTS
    //                 ├─ TRILL_MARK        ─► (decorative; pairing via WAVY_LINE)
    //                 └─ WAVY_LINE (@type) ─► noteTrillStart / noteTrillStop
    //
    //   Per-span pending-anchor pairing (built in finishNote, like resolveSlide):
    //
    //     BEAM     begin ─► pendingBeamStart;   end  ─► Beam(anchor, note)
    //     TIED     start ─► pendingTieStart;     stop ─► Tie(anchor, note)
    //                (interior note: stop then start chains the run; addTie
    //                 merges the pairs into one Tie(firstAnchor, lastEnd))
    //     TUPLET   start ─► pendingTupletStart (grade captured from
    //                       <time-modification>); stop ─► Tuplet(anchor, note, grade)
    //     WAVY     start ─► pendingTrillStart;   stop ─► Trill(anchor, note)
    //                (anchor == note ─► single-note Trill(anchor))
    //
    //   Lenient read (both directions, like resolveSlide):
    //     dangling start (no matching end) ─► drop + log at part-end flush
    //     orphan stop/end (no pending anchor) ─► ignore + log
    // -------------------------------------------------------------------------

    // Tuplet grade for the current note, parsed from <actual-notes> (0 if absent).
    private int noteActualNotes = 0;

    // Primary-beam (number="1") value for the current note (begin/continue/end);
    // null when this note carries no number="1" <beam>. true while the <beam>
    // currently being parsed is number="1".
    @Nullable
    private String noteBeam1Type = null;

    private boolean beamLevelIsOne = false;

    private boolean noteTiedStart = false;
    private boolean noteTiedStop = false;

    private boolean noteTupletStart = false;
    private boolean noteTupletStop = false;
    private boolean noteTupletRelativeYPresent = false;
    private double noteTupletRelativeYTenths = 0.0;

    private boolean noteTrillStart = false;
    private boolean noteTrillStop = false;
    private boolean noteTrillRelativeYPresent = false;
    private double noteTrillRelativeYTenths = 0.0;

    // Holds a <slide type="start"> note until the next note's stop is seen.
    @Nullable
    private StaffElement pendingSlideStart = null;

    // Per-span run anchors, held until the closing marker arrives (see diagram).
    @Nullable
    private StaffElement pendingBeamStart = null;

    @Nullable
    private StaffElement pendingTieStart = null;

    @Nullable
    private StaffElement pendingTupletStart = null;

    private int pendingTupletGrade = 0;
    private int pendingTupletVerticalPositionSs = 0;

    @Nullable
    private StaffElement pendingTrillStart = null;

    private int pendingTrillYPositionSs = 0;

    // -------------------------------------------------------------------------
    // Measure-level range-span state (hairpin wedges, endings).
    //
    // Hairpin wedges (binding rule — inverts the writer's both-before-the-note
    // placement): the writer emits BOTH wedges of a hairpin immediately before
    // their bound <note>, so the reader uses one uniform rule — each wedge binds
    // to the NEXT <note> after it. The start wedge's next note is the hairpin
    // anchor; the stop wedge's next note is the hairpin end.
    //
    //   <wedge crescendo/diminuendo> ─► pendingStartWedge*  (awaits anchor note)
    //   anchor note finished         ─► pendingWedge* (open hairpin, awaits end)
    //   <wedge stop>                 ─► pendingStopWedge     (awaits end note)
    //   end note finished            ─► addCrescendo/addDiminuendo(anchor, end)
    //
    // A single pendingWedge assumes only one hairpin is ever open (the app must
    // not produce overlapping wedges — a pre-existing bug fixed later): a second
    // start while one is already open (and not in the process of closing) is
    // logged and dropped. A dangling start (no stop) is dropped at flush; an
    // orphan stop (no open hairpin) is logged and ignored.
    //
    // Endings (two voltas ─► one Ending — inverts the writer's split expansion):
    //
    //   anchor barline          split barline(s)            end barline
    //   <ending 1 start>        <ending 1 stop>             <ending 2 stop>
    //                           <ending 2 start>
    //        |                        |     |                    |
    //        '------- volta 1 -------'      '----- volta 2 ------'
    //
    //   1 start ─► pendingEndingAnchor (the barline StaffElement)
    //   2 stop  ─► Ending(anchor, this barline); the split is recomputed live
    //             via Ending.findRepeatSplitIndex — never stored.
    //   1 stop  ─► tentative split-less end (overwritten by a later 2 stop)
    //   2 start ─► marks a second volta (distinguishes a partial two-bracket
    //             ending — dropped — from a complete split-less ending — built).
    //
    // A split-less ending (only 1 start ─► 1 stop) builds Ending(anchor, end)
    // directly. The build is deferred to the next anchor or to the line/part
    // flush so a trailing 2 start can still be observed.
    // -------------------------------------------------------------------------

    // A start <wedge> whose anchor note has not yet been seen.
    @Nullable
    private WedgeKind pendingStartWedgeKind = null;

    private int pendingStartWedgeX1Ss = 0;
    private int pendingStartWedgeYSs = 0;

    // The currently open hairpin: anchor note resolved, awaiting its end note.
    @Nullable
    private StaffElement pendingWedgeAnchor = null;

    @Nullable
    private WedgeKind pendingWedgeKind = null;

    private int pendingWedgeX1Ss = 0;
    private int pendingWedgeYSs = 0;

    // A stop <wedge> whose end note has not yet been seen.
    private boolean pendingStopWedge = false;
    private int pendingStopWedgeX2Ss = 0;

    // The open ending's anchor barline (set on <ending number="1" type="start">).
    @Nullable
    private StaffElement pendingEndingAnchor = null;

    // The tentative end barline of a split-less ending (set on a number="1" stop),
    // overwritten by a number="2" stop in a two-bracket ending.
    @Nullable
    private StaffElement pendingEndingEnd = null;

    // True once a <ending number="2" type="start"> is seen: marks a two-bracket
    // ending, so a missing number="2" stop drops the ending instead of building a
    // (wrong) split-less one from the tentative end.
    private boolean pendingEndingSawSecondVolta = false;

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
                    currentBarlineEndings.clear();
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
                    // barline is appended (see processBarline / attachBarlineEndings).
                    currentBarlineEndings.add(new EndingMarker(
                        attributes.getValue(MusicXmlTags.ATTR_NUMBER),
                        attributes.getValue(MusicXmlTags.ATTR_TYPE)
                    ));
                } else if (qName.equals(MusicXmlTags.REPEAT)) {
                    repeatDirection = attributes.getValue(MusicXmlTags.ATTR_DIRECTION);
                }
            }
            case DIRECTION -> {
                if (qName.equals(MusicXmlTags.DIRECTION_TYPE)) {
                    where = Where.DIRECTION_TYPE;
                }
            }
            case DIRECTION_TYPE -> {
                if (qName.equals(MusicXmlTags.WEDGE)) {
                    handleWedge(attributes);
                    where = Where.WEDGE;
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
                } else if (qName.equals(MusicXmlTags.TIE)) {
                    // Sound tie — write-forward only; <tied> is the source of truth
                    // for span reconstruction, so this carries no read state.
                    where = Where.TIE;
                } else if (qName.equals(MusicXmlTags.TIME_MOD)) {
                    where = Where.TIME_MODIFICATION;
                } else if (qName.equals(MusicXmlTags.BEAM)) {
                    // Only the primary beam (number="1") drives span collapse;
                    // secondary levels and hooks are write-forward (layout re-derives).
                    beamLevelIsOne =
                        MusicXmlTags.NUMBER_1.equals(attributes.getValue(MusicXmlTags.ATTR_NUMBER));
                    where = Where.BEAM;
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
                } else if (qName.equals(MusicXmlTags.TIED)) {
                    var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);
                    noteTiedStart |= MusicXmlTags.TYPE_START.equals(type);
                    noteTiedStop |= MusicXmlTags.TYPE_STOP.equals(type);
                    where = Where.TIED;
                } else if (qName.equals(MusicXmlTags.TUPLET)) {
                    var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

                    if (MusicXmlTags.TYPE_START.equals(type)) {
                        noteTupletStart = true;
                        captureTupletRelativeY(attributes);
                    } else if (MusicXmlTags.TYPE_STOP.equals(type)) {
                        noteTupletStop = true;
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
                        noteTrillStart = true;
                        captureTrillRelativeY(attributes);
                    } else if (MusicXmlTags.TYPE_STOP.equals(type)) {
                        noteTrillStop = true;
                    }

                    where = Where.WAVY_LINE;
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
            case TIE -> {
                if (qName.equals(MusicXmlTags.TIE)) {
                    where = Where.NOTE;
                }
            }
            case BEAM -> {
                if (qName.equals(MusicXmlTags.BEAM)) {
                    // Capture the primary-beam value only; secondary levels and
                    // hooks are write-forward and ignored on read.
                    if (beamLevelIsOne) {
                        noteBeam1Type = value.toString().trim();
                    }

                    where = Where.NOTE;
                }
            }
            case ACTUAL_NOTES -> {
                if (qName.equals(MusicXmlTags.ACTUAL_NOTES)) {
                    noteActualNotes = parseIntOrThrow(MusicXmlTags.ACTUAL_NOTES, value.toString());
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
            case WEDGE -> {
                if (qName.equals(MusicXmlTags.WEDGE)) {
                    where = Where.DIRECTION_TYPE;
                }
            }
            case DIRECTION_TYPE -> {
                if (qName.equals(MusicXmlTags.DIRECTION_TYPE)) {
                    where = Where.DIRECTION;
                }
            }
            case DIRECTION -> {
                if (qName.equals(MusicXmlTags.DIRECTION)) {
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
                    flushPendingRepeatRight();
                    flushPendingSlideStart();
                    flushPendingSpanStarts();
                    flushPendingWedge();
                    flushPendingEnding();
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

        // Hairpins and endings are intra-line range spans; flush any still open
        // against the line that is now ending, before the new line becomes current
        // (the build/drop targets currentLine). flushPendingRepeatRight ran first so
        // a deferred REPEAT_RIGHT carrying an ending end marker is already appended.
        flushPendingEnding();
        flushPendingWedge();

        // Commit the line that just ended, then start the next one detached from
        // the song. The new line is NOT added until it, too, is complete (next
        // line break or </part>) — see commitCurrentLine. While a line is detached
        // it is never the song's last line, so Line.addElement appends elements in
        // their exact document order instead of inserting them before a valid-
        // terminal barline (REPEAT_RIGHT / FINAL_DOUBLE_BARLINE) that lands mid-line.
        commitCurrentLine();
        currentLine = new Line(song);
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
            // An invisible LEFT barline can still host a volta-2 <ending number="2"
            // type="start"> after a REPEAT_RIGHT split; that marker carries no
            // stored value (the split is recomputed live), so no element is needed.
            attachBarlineEndings(null, currentBarlineEndings);
            return;
        }

        var elementType = BarlineStyleMapping.forBarStyle(barStyle, repeatDirection);

        if (elementType == null) {
            // Unknown combination — silently skip rather than corrupt the model.
            flushPendingRepeatRight();
            attachBarlineEndings(null, currentBarlineEndings);
            return;
        }

        if (pendingRepeatRight) {
            if (elementType == ElementType.REPEAT_LEFT
                    && BarlineStyleMapping.LOCATION_LEFT.equals(barlineLocation)) {
                // This left-forward barline is the second half of a straddling
                // REPEAT_LEFT_RIGHT pair — merge and emit the combined element.
                // Both halves' ending markers attach to that single element: the
                // held backward-right markers (volta-1 stop / ending end) first,
                // then this forward-left barline's (volta-2 start / ending anchor).
                pendingRepeatRight = false;
                var heldEndings = pendingRepeatRightEndings;
                pendingRepeatRightEndings = List.of();
                var element = appendToCurrentLine(ElementType.REPEAT_LEFT_RIGHT);
                attachBarlineEndings(element, heldEndings);
                attachBarlineEndings(element, currentBarlineEndings);
            } else {
                // The pending REPEAT_RIGHT was not followed by a REPEAT_LEFT —
                // flush it as a standalone element, then process the current one.
                flushPendingRepeatRight();
                appendOrHold(elementType, currentBarlineEndings);
            }
        } else {
            appendOrHold(elementType, currentBarlineEndings);
        }
    }

    /**
     * Either holds {@code elementType} as {@link #pendingRepeatRight} (for
     * deferred REPEAT_LEFT_RIGHT pair detection) or appends it immediately,
     * attaching {@code endings} to the resulting barline element. A held
     * REPEAT_RIGHT carries its ending markers in {@link #pendingRepeatRightEndings}
     * until the element is created (flushed or merged).
     */
    private void appendOrHold(ElementType elementType, List<EndingMarker> endings) throws SAXException {
        if (elementType == ElementType.REPEAT_RIGHT) {
            // Defer: the next barline may be the forward half of a pair.
            pendingRepeatRight = true;
            pendingRepeatRightEndings = new ArrayList<>(endings);
        } else {
            var element = appendToCurrentLine(elementType);
            attachBarlineEndings(element, endings);
        }
    }

    /**
     * Flushes a held {@link #pendingRepeatRight} as a standalone element,
     * attaching the ending markers held with it.
     */
    private void flushPendingRepeatRight() throws SAXException {
        if (pendingRepeatRight) {
            var element = appendToCurrentLine(ElementType.REPEAT_RIGHT);
            var heldEndings = pendingRepeatRightEndings;
            pendingRepeatRightEndings = List.of();
            pendingRepeatRight = false;
            attachBarlineEndings(element, heldEndings);
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
        noteActualNotes = 0;
        noteBeam1Type = null;
        noteTiedStart = false;
        noteTiedStop = false;
        noteTupletStart = false;
        noteTupletStop = false;
        noteTupletRelativeYPresent = false;
        noteTupletRelativeYTenths = 0.0;
        noteTrillStart = false;
        noteTrillStop = false;
        noteTrillRelativeYPresent = false;
        noteTrillRelativeYTenths = 0.0;
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

        // A note can never be the forward half of a REPEAT_LEFT_RIGHT pair, so a
        // REPEAT_RIGHT still held pending here is a standalone backward repeat that
        // precedes this note. Flush it first so it lands ahead of the note in the
        // element order instead of being deferred past it to the next barline.
        flushPendingRepeatRight();

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

        // Collapse this note's per-note range-span markers into RangeElements,
        // pairing each run's pending anchor with its closing marker.
        resolveBeam(currentLine, note);
        resolveTie(currentLine, note);
        resolveTuplet(currentLine, note);
        resolveTrill(currentLine, note);
        resolveWedge(currentLine, note);

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
     * Captures the optional {@code relative-y} (tenths) on a {@code <tuplet>}
     * start marker; the writer carries {@code verticalPositionSs} here.
     */
    private void captureTupletRelativeY(Attributes attributes) throws SAXException {
        var relativeY = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_Y);

        if (relativeY != null) {
            noteTupletRelativeYPresent = true;
            noteTupletRelativeYTenths = parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_Y, relativeY);
        }
    }

    /**
     * Captures the optional {@code relative-y} (tenths) on a {@code <wavy-line>}
     * start marker; the writer carries {@code yPositionSs} here.
     */
    private void captureTrillRelativeY(Attributes attributes) throws SAXException {
        var relativeY = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_Y);

        if (relativeY != null) {
            noteTrillRelativeYPresent = true;
            noteTrillRelativeYTenths = parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_Y, relativeY);
        }
    }

    /**
     * Collapses this note's primary {@code <beam number="1">} marker into the
     * active beam run: {@code begin} opens the run (holding this note as the
     * anchor), {@code continue} keeps it open, and {@code end} builds the
     * {@link Beam}. A dangling start (no {@code end}) is dropped; an orphan
     * {@code end} (no pending anchor) is ignored — both logged.
     */
    private void resolveBeam(Line line, StaffElement note) {
        var beamType = noteBeam1Type;

        if (beamType == null) {
            return;
        }

        if (MusicXmlTags.BEAM_BEGIN.equals(beamType)) {
            // A still-pending earlier begin never received its end — drop it.
            flushPendingBeamStart();
            pendingBeamStart = note;
        } else if (MusicXmlTags.BEAM_END.equals(beamType)) {
            if (pendingBeamStart != null) {
                line.addBeaming(new Beam(pendingBeamStart, note));
                pendingBeamStart = null;
            } else {
                LOG.warn("Ignoring <beam>end</beam> with no matching begin");
            }
        }
        // BEAM_CONTINUE keeps the run open — no action.
    }

    /**
     * Collapses this note's {@code <tied>} markers into the active tie run.
     * Stop is processed before start so an interior note (which emits stop then
     * start) closes its pair and immediately re-opens the next; {@link Line#addTie}
     * merges the adjacent pairs into a single {@link Tie} over the whole chain. A
     * dangling start is dropped, an orphan stop ignored — both logged.
     */
    private void resolveTie(Line line, StaffElement note) {
        if (noteTiedStop) {
            if (pendingTieStart != null) {
                line.addTie(new Tie(pendingTieStart, note));
                pendingTieStart = null;
            } else {
                LOG.warn("Ignoring <tied type=\"stop\"> with no matching start");
            }
        }

        if (noteTiedStart) {
            flushPendingTieStart();
            pendingTieStart = note;
        }
    }

    /**
     * Collapses this note's {@code <tuplet>} bracket markers into a {@link Tuplet}.
     * Start is processed before stop so a single-note tuplet builds correctly. The
     * {@code grade} is captured from {@code <actual-notes>} (the per-note
     * {@code <time-modification>}, which precedes {@code <notations>}); the
     * repeated per-note {@code <time-modification>} is otherwise ignored. The
     * {@code verticalPositionSs} is restored from the start marker's
     * {@code relative-y}. A dangling start is dropped, an orphan stop ignored.
     */
    private void resolveTuplet(Line line, StaffElement note) {
        if (noteTupletStart) {
            // A still-pending earlier start never received its stop — drop it.
            flushPendingTupletStart();
            pendingTupletStart = note;
            pendingTupletGrade = noteActualNotes;
            pendingTupletVerticalPositionSs = noteTupletRelativeYPresent
                ? tenthsToSs(noteTupletRelativeYTenths)
                : 0;
        }

        if (noteTupletStop) {
            if (pendingTupletStart != null) {
                var tuplet = new Tuplet(pendingTupletStart, note, pendingTupletGrade);

                if (pendingTupletVerticalPositionSs != 0) {
                    tuplet.setVerticalPositionSs(pendingTupletVerticalPositionSs);
                }

                line.addTuplet(tuplet);
                pendingTupletStart = null;
            } else {
                LOG.warn("Ignoring <tuplet type=\"stop\"> with no matching start");
            }
        }
    }

    /**
     * Collapses this note's {@code <wavy-line>} markers into a {@link Trill}.
     * Start is processed before stop so a single-note trill (start and stop on one
     * note) builds the single-note {@link Trill}. The {@code yPositionSs} is
     * restored from the start marker's {@code relative-y}. A dangling start is
     * dropped, an orphan stop ignored — both logged.
     */
    private void resolveTrill(Line line, StaffElement note) {
        if (noteTrillStart) {
            // A still-pending earlier start never received its stop — drop it.
            flushPendingTrillStart();
            pendingTrillStart = note;
            pendingTrillYPositionSs = noteTrillRelativeYPresent
                ? tenthsToSs(noteTrillRelativeYTenths)
                : 0;
        }

        if (noteTrillStop) {
            if (pendingTrillStart != null) {
                Trill trill;

                if (pendingTrillStart == note) {
                    trill = new Trill(pendingTrillStart);
                } else {
                    trill = new Trill(pendingTrillStart, note);
                }

                if (pendingTrillYPositionSs != 0) {
                    trill.setYPositionSs(pendingTrillYPositionSs);
                }

                line.addRangeElement(trill);
                pendingTrillStart = null;
            } else {
                LOG.warn("Ignoring <wavy-line type=\"stop\"> with no matching start");
            }
        }
    }

    /**
     * Drops any per-note range-span run still open at the end of the part — each
     * is a dangling start whose closing marker never arrived (e.g. truncated
     * input). A range needs both endpoints, so the run builds nothing.
     */
    private void flushPendingSpanStarts() {
        flushPendingBeamStart();
        flushPendingTieStart();
        flushPendingTupletStart();
        flushPendingTrillStart();
    }

    private void flushPendingBeamStart() {
        pendingBeamStart = warnIfDangling(pendingBeamStart, "Dropping dangling beam begin with no matching end");
    }

    private void flushPendingTieStart() {
        pendingTieStart = warnIfDangling(pendingTieStart, "Dropping dangling <tied type=\"start\"> with no matching stop");
    }

    private void flushPendingTupletStart() {
        pendingTupletStart = warnIfDangling(pendingTupletStart, "Dropping dangling <tuplet type=\"start\"> with no matching stop");
    }

    private void flushPendingTrillStart() {
        pendingTrillStart = warnIfDangling(pendingTrillStart, "Dropping dangling <wavy-line type=\"start\"> with no matching stop");
    }

    /**
     * Logs {@code warnMessage} when {@code pending} is a dangling span start
     * (non-null, with no closing marker) and returns {@code null} so the caller
     * can clear its field. Shared by the four {@code flushPending*Start} methods,
     * which differ only in the field cleared and the message logged.
     */
    @Nullable
    private <T> T warnIfDangling(@Nullable T pending, String warnMessage) {
        if (pending != null) {
            LOG.warn(warnMessage);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Hairpin wedges
    // -------------------------------------------------------------------------

    /**
     * Handles a measure-level {@code <wedge>}. A start wedge
     * ({@code crescendo}/{@code diminuendo}) is held until the next note binds it
     * as the hairpin anchor; a stop wedge is held until the next note binds it as
     * the hairpin end (see the wedge-binding diagram on the pending-wedge fields).
     */
    private void handleWedge(Attributes attributes) throws SAXException {
        var type = attributes.getValue(MusicXmlTags.ATTR_TYPE);

        if (MusicXmlTags.TYPE_STOP.equals(type)) {
            pendingStopWedge = true;
            pendingStopWedgeX2Ss = optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_X);
            return;
        }

        var wedgeKind = WedgeTypeMapping.wedgeKind(type);

        if (wedgeKind == null) {
            // Not a crescendo/diminuendo start (nor a stop) — unrecognised; ignore.
            return;
        }

        // Defensive overlap drop: only one hairpin is ever open. A start while a
        // hairpin is already open (and not in the process of closing), or while an
        // earlier start has not yet bound to its anchor note, is logged and dropped.
        if (pendingStartWedgeKind != null
                || (pendingWedgeAnchor != null && !pendingStopWedge)) {
            LOG.warn("Dropping overlapping <wedge> start; a hairpin is already open");
            return;
        }

        pendingStartWedgeKind = wedgeKind;
        pendingStartWedgeX1Ss = optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_X);
        pendingStartWedgeYSs = optionalTenthsAttrToSs(attributes, MusicXmlTags.ATTR_RELATIVE_Y);
    }

    /**
     * Binds this note to any pending wedge. Each wedge binds to the next note: a
     * start wedge makes this note the hairpin anchor; a stop wedge makes it the
     * end.
     *
     * <p>When this note opens a hairpin (a start is pending and none is open), the
     * start is applied first so a same-note stop closes a single-note hairpin.
     * Otherwise the stop is applied first so a hairpin already open is closed
     * before this note opens the next (back-to-back hairpins sharing a note).
     */
    private void resolveWedge(Line line, StaffElement note) {
        var openingHere = pendingWedgeAnchor == null && pendingStartWedgeKind != null;

        if (openingHere) {
            applyPendingStartWedge(note);
            applyPendingStopWedge(line, note);
        } else {
            applyPendingStopWedge(line, note);
            applyPendingStartWedge(note);
        }
    }

    /**
     * Promotes a pending start wedge into the open hairpin, with {@code note} as
     * its anchor. No-op when no start wedge is pending.
     */
    private void applyPendingStartWedge(StaffElement note) {
        if (pendingStartWedgeKind == null) {
            return;
        }

        pendingWedgeAnchor = note;
        pendingWedgeKind = pendingStartWedgeKind;
        pendingWedgeX1Ss = pendingStartWedgeX1Ss;
        pendingWedgeYSs = pendingStartWedgeYSs;
        pendingStartWedgeKind = null;
        pendingStartWedgeX1Ss = 0;
        pendingStartWedgeYSs = 0;
    }

    /**
     * Closes the open hairpin with {@code note} as its end, building the
     * {@link Crescendo}/{@link Diminuendo}. An orphan stop (no open hairpin) is
     * logged and ignored. No-op when no stop wedge is pending.
     */
    private void applyPendingStopWedge(Line line, StaffElement note) {
        if (!pendingStopWedge) {
            return;
        }

        if (pendingWedgeAnchor != null && pendingWedgeKind != null) {
            buildHairpin(line, pendingWedgeAnchor, pendingWedgeKind, note);
            clearPendingWedge();
        } else {
            LOG.warn("Ignoring <wedge type=\"stop\"> with no open hairpin");
        }

        pendingStopWedge = false;
        pendingStopWedgeX2Ss = 0;
    }

    /**
     * Builds a {@link Crescendo} or {@link Diminuendo} over [{@code anchor},
     * {@code end}] and adds it to {@code line}. {@code x1ShiftSs}/{@code yShiftSs}
     * come from the start wedge, {@code x2ShiftSs} from the stop wedge — each set
     * only when non-zero.
     */
    private void buildHairpin(
        Line line,
        StaffElement anchor,
        WedgeKind kind,
        StaffElement end
    ) {
        if (kind == WedgeKind.CRESCENDO) {
            var crescendo = new Crescendo(anchor, end);
            applyHairpinShifts(crescendo);
            line.addCrescendo(crescendo);
        } else {
            var diminuendo = new Diminuendo(anchor, end);
            applyHairpinShifts(diminuendo);
            line.addDiminuendo(diminuendo);
        }
    }

    /**
     * Restores the user-adjustable hairpin offsets from the pending wedge state.
     * Reads the start-wedge shifts ({@link #pendingWedgeX1Ss}/{@link #pendingWedgeYSs})
     * and the stop-wedge shift ({@link #pendingStopWedgeX2Ss}), which are still set
     * when this runs (cleared after the build).
     */
    private void applyHairpinShifts(Hairpin hairpin) {
        if (pendingWedgeX1Ss != 0) {
            hairpin.setX1ShiftSs(pendingWedgeX1Ss);
        }

        if (pendingStopWedgeX2Ss != 0) {
            hairpin.setX2ShiftSs(pendingStopWedgeX2Ss);
        }

        if (pendingWedgeYSs != 0) {
            hairpin.setYShiftSs(pendingWedgeYSs);
        }
    }

    private void clearPendingWedge() {
        pendingWedgeAnchor = null;
        pendingWedgeKind = null;
        pendingWedgeX1Ss = 0;
        pendingWedgeYSs = 0;
    }

    /**
     * Drops any wedge state still open at a line or part boundary: a start wedge
     * with no bound note, a dangling hairpin with no stop, or a stop wedge with no
     * bound note. A hairpin needs both endpoints, so an incomplete one builds nothing.
     */
    private void flushPendingWedge() {
        if (pendingStartWedgeKind != null) {
            LOG.warn("Dropping <wedge> start with no bound note");
            pendingStartWedgeKind = null;
            pendingStartWedgeX1Ss = 0;
            pendingStartWedgeYSs = 0;
        }

        if (pendingWedgeAnchor != null) {
            LOG.warn("Dropping dangling hairpin with no stop wedge");
            clearPendingWedge();
        }

        if (pendingStopWedge) {
            LOG.warn("Dropping <wedge type=\"stop\"> with no bound note");
            pendingStopWedge = false;
            pendingStopWedgeX2Ss = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Endings (two voltas collapse into one Ending span)
    //
    //   anchor barline          split barline(s)            end barline
    //   <ending 1 start>        <ending 1 stop>             <ending 2 stop>
    //                           <ending 2 start>
    //        |                        |     |                    |
    //        '------- volta 1 -------'      '----- volta 2 ------'
    //                                                            v
    //                                       Ending(anchorBarline, endBarline)
    //
    // The split (REPEAT_RIGHT / REPEAT_LEFT_RIGHT) is recomputed live by
    // Ending.findRepeatSplitIndex, so the volta-1-stop / volta-2-start markers
    // carry no stored value. A split-less ending is just 1 start -> 1 stop.
    // -------------------------------------------------------------------------

    /**
     * Resolves the {@code <ending>} markers collected on one {@code <barline>}
     * against that barline's just-created {@link StaffElement}, advancing the
     * ending state machine.
     *
     * <p>{@code element} is null only for invisible barlines, which host at most a
     * volta-2 {@code number="2" type="start"} split marker — a no-op needing no
     * element.
     */
    private void attachBarlineEndings(@Nullable StaffElement element, List<EndingMarker> endings) {
        for (var ending : endings) {
            var isOne = MusicXmlTags.NUMBER_1.equals(ending.number());
            var isTwo = MusicXmlTags.NUMBER_2.equals(ending.number());
            var isStart = MusicXmlTags.TYPE_START.equals(ending.type());
            var isStop = MusicXmlTags.TYPE_STOP.equals(ending.type())
                || MusicXmlTags.ENDING_DISCONTINUE.equals(ending.type());

            if (isOne && isStart) {
                // Anchor of a new ending. Finalize any still-open ending first
                // (build a complete split-less one, or drop a dangling/partial one).
                finalizeOrDropPendingEnding();

                if (element == null) {
                    LOG.warn("Ignoring <ending number=\"1\" type=\"start\"> on a barline with no element");
                    continue;
                }

                pendingEndingAnchor = element;
                pendingEndingEnd = null;
                pendingEndingSawSecondVolta = false;
            } else if (isTwo && isStop) {
                // Definite end of a two-bracket ending.
                if (pendingEndingAnchor != null && element != null) {
                    buildEnding(pendingEndingAnchor, element);
                    clearPendingEnding();
                } else {
                    LOG.warn("Ignoring <ending number=\"2\" type=\"stop\"> with no open ending");
                }
            } else if (isOne && isStop) {
                // Either a volta-1 split stop or a split-less end — tentative until a
                // number="2" start (split) or number="2" stop (two-bracket end) decides.
                if (pendingEndingAnchor != null && element != null) {
                    pendingEndingEnd = element;
                } else {
                    LOG.warn("Ignoring <ending number=\"1\" type=\"stop\"> with no open ending");
                }
            } else if (isTwo && isStart) {
                // Volta-2 split start — no stored value (the split is recomputed
                // live), but it marks the ending as two-bracket.
                pendingEndingSawSecondVolta = true;
            }
        }
    }

    /**
     * Completes a pending split-less ending (anchor + tentative end, no second
     * volta) by building it, or drops a dangling/partial one (no end, or a second
     * volta seen but no {@code number="2"} stop). Clears the pending ending state.
     */
    private void finalizeOrDropPendingEnding() {
        if (pendingEndingAnchor == null) {
            return;
        }

        if (pendingEndingEnd != null && !pendingEndingSawSecondVolta) {
            buildEnding(pendingEndingAnchor, pendingEndingEnd);
        } else {
            LOG.warn("Dropping incomplete <ending> with no number=\"2\" stop");
        }

        clearPendingEnding();
    }

    /**
     * Builds one {@link Ending} over [{@code anchor}, {@code end}] and adds it to
     * the current line. Both endpoints are barline {@link StaffElement}s already
     * appended to the line, so the ending's index pair recovers via
     * {@code getAnchorElementIndex()}/{@code getEndElementIndex()}.
     */
    private void buildEnding(StaffElement anchor, StaffElement end) {
        if (currentLine == null) {
            return;
        }

        currentLine.addRangeElement(new Ending(anchor, end));
    }

    private void clearPendingEnding() {
        pendingEndingAnchor = null;
        pendingEndingEnd = null;
        pendingEndingSawSecondVolta = false;
    }

    /**
     * Builds or drops a pending ending still open at a line or part boundary.
     */
    private void flushPendingEnding() {
        finalizeOrDropPendingEnding();
    }

    /**
     * Reads an optional {@code tenths}-valued attribute and converts it to
     * SongScribe staff-spaces, returning 0 when the attribute is absent.
     */
    private int optionalTenthsAttrToSs(Attributes attributes, String attrName) throws SAXException {
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
    private static int tenthsToSs(double tenths) {
        return (int) Math.round(tenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE);
    }

    /**
     * Appends a structural {@link ElementType} to the current line.
     *
     * <p>Structural (non-duration) elements are created via {@link ElementType#newInstance()},
     * which clones the pre-built default instance — the same pattern used by
     * {@code StaffElementIO.StaffElementReader} for barline elements.
     */
    private StaffElement appendToCurrentLine(ElementType elementType) throws SAXException {
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
    }

    /**
     * One {@code <ending number type>} child parsed from a {@code <barline>},
     * collected during barline parsing and resolved to the barline's
     * {@link StaffElement} once it is appended. {@code number} and {@code type}
     * may be null for malformed input; the consumers compare with null-safe
     * {@code equals}.
     */
    private record EndingMarker(@Nullable String number, @Nullable String type) {}
}
