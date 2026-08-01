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
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.TupletLoadPass;
import songscribe.font.DocumentFonts;
import songscribe.io.SafeXmlParser;
import songscribe.io.SongLoadResult;
import songscribe.util.Utils;

/**
 * SAX reader that parses MusicXML 4.0 documents produced by {@link MusicXmlWriter}
 * back into a {@link Song}.
 * <p>
 * This is not a general MusicXML importer — it only handles SongScribe's own output.
 * <p>
 * <b>Orchestrator role.</b> This class owns the two SAX dispatch switches
 * ({@code startElement}/{@code endElement}) and the shared parse spine
 * ({@code where}, {@code value}, {@code song}, {@code currentLine}, plus the key-
 * carry state). Each content {@link Where} state's case is delegated to the
 * sub-reader that owns it; the lifecycle/convergence cases stay inline. The
 * sub-readers reach the spine through the reader-as-context accessors on this
 * class ({@code valueString}, {@code setWhere}, {@code startTransition},
 * {@code getCurrentLine}, {@code songOrNull}).
 * <p>
 * <b>{@link Where} state-transition graph.</b> The transitions are driven from
 * several files via {@code reader.setWhere(...)}. Ownership is annotated per group
 * (states not yet extracted remain inline in this orchestrator):
 *
 * <pre>
 *   NONE ──&lt;score-partwise&gt;──▶ SCORE_PARTWISE            [orchestrator: lifecycle]
 *
 *   SCORE_PARTWISE (hub) ──┬─▶ MOVEMENT_TITLE / MOVEMENT_NUMBER   ┐
 *                          ├─▶ IDENTIFICATION ─▶ CREATOR          │
 *                          │                   ├▶ RIGHTS          │
 *                          │                   ├▶ ENCODING ─▶ SOFTWARE / ENCODING_DATE
 *                          │                   └▶ MISCELLANEOUS ─▶ MISCELLANEOUS_FIELD
 *                          ├─▶ DEFAULTS ─▶ DEFAULTS_SCALING       │  MusicXmlHeaderReader
 *                          │            ├▶ DEFAULTS_PAGE_LAYOUT ─▶ DEFAULTS_PAGE_WIDTH
 *                          │            └▶ DEFAULTS_STAFF_LAYOUT  │
 *                          ├─▶ CREDIT ─▶ CREDIT_TYPE / CREDIT_WORDS┘
 *                          ├─▶ PART_LIST ─▶ SCORE_PART            ┐
 *                          └─▶ PART ─▶ MEASURE (hub)             │  MusicXmlMeasureReader
 *                                       ├▶ ATTRIBUTES ─▶ KEY ─▶ FIFTHS  (measure leaf states;
 *                                       ├▶ BARLINE ─▶ BAR_STYLE   │   the MEASURE dispatch
 *                                       ├▶ &lt;print new-system&gt;      ┘   hub stays inline)
 *                                       ├▶ NOTE ...               (MusicXmlNoteReader)
 *                                       └▶ DIRECTION ...          (MusicXmlDirectionReader)
 * </pre>
 *
 * States moved to {@link MusicXmlHeaderReader}: {@code MOVEMENT_TITLE},
 * {@code MOVEMENT_NUMBER}, {@code IDENTIFICATION}, {@code CREATOR}, {@code RIGHTS},
 * {@code ENCODING}, {@code SOFTWARE}, {@code ENCODING_DATE}, {@code MISCELLANEOUS},
 * {@code MISCELLANEOUS_FIELD}, {@code DEFAULTS}, {@code DEFAULTS_SCALING},
 * {@code DEFAULTS_PAGE_LAYOUT}, {@code DEFAULTS_PAGE_WIDTH},
 * {@code DEFAULTS_STAFF_LAYOUT}, {@code CREDIT}, {@code CREDIT_TYPE},
 * {@code CREDIT_WORDS}.
 * <p>
 * Measure leaf states moved to {@link MusicXmlMeasureReader}: {@code PART_LIST},
 * {@code SCORE_PART}, {@code PART}, {@code ATTRIBUTES}, {@code KEY}, {@code FIFTHS},
 * {@code BARLINE}, {@code BAR_STYLE}, and the {@code MEASURE} end transition. The
 * {@code MEASURE} start dispatch is a cross-group hub (note/direction/barline/
 * {@code <print new-system>}) and, like the {@code SCORE_PARTWISE} hub, stays inline
 * in this orchestrator (decision 6), as does the {@code </part>} flush block.
 * <p>
 * Direction-group states moved to {@link MusicXmlDirectionReader}:
 * {@code DIRECTION}, {@code DIRECTION_TYPE}, {@code WEDGE}, {@code SOUND},
 * {@code METRONOME}, {@code BEAT_UNIT}, {@code BEAT_UNIT_DOT}, {@code PER_MINUTE},
 * {@code WORDS}, {@code METRONOME_NOTE}, {@code METRONOME_TYPE},
 * {@code METRONOME_DOT}, {@code METRONOME_RELATION}. The note-group states are
 * owned by {@link MusicXmlNoteReader}. Every content case now delegates to a
 * sub-reader; only the lifecycle/convergence cases ({@code NONE},
 * {@code SCORE_PARTWISE}, the {@code MEASURE} hub, the {@code </part>} flush, the
 * {@code NOTE} start/end, and {@code <print new-system>}) and the convergence
 * methods remain inline in this orchestrator (decision 6).
 */
@SuppressWarnings("NestedSwitchStatement")
public final class MusicXmlReader extends DefaultHandler {

    // Element names, attribute names, and shared values are in MusicXmlTags.

    // A DOCTYPE is tolerated but nothing it names is acted upon — see SafeXmlParser
    // for why both halves of that are necessary.
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SafeXmlParser.newHardenedFactory();
        PARSER_FACTORY.setNamespaceAware(true);
    }

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
     * The line currently being built, or {@code null} before the first
     * {@code <print new-system>} opens a line. Read by {@link BarlineParser} and
     * {@link EndingResolver}, which append to and build on it through this
     * reader rather than duplicating ownership of it; callers that dereference
     * it must null-guard (e.g. {@code finishNote} throws when it is still
     * {@code null}).
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

    // True once any note's <accidental> token was a retired legacy token
    // converted to its replacement (see NoteAccumulator.usedLegacyAccidental).
    // Lives here rather than on NoteAccumulator because NoteAccumulator.reset()
    // runs once per note; this flag must persist across the whole document.
    private boolean accidentalsConverted = false;

    // -------------------------------------------------------------------------
    // Document-metadata reconstruction state — the document fonts recovered from
    // <defaults> (<word-font> → ANNOTATION, <lyric-font> → LYRICS) and the
    // <miscellaneous> sub-attribution-font/-size fields (→ SUB_ATTRIBUTION).
    // Starts from the canonical default set; each recovered role overrides one
    // entry. A document with no <defaults> fonts leaves this at the defaults,
    // which is returned as-is via SongLoadResult.Success. The reference is held
    // here for the result, but every write into it is performed by the header
    // reader (which shares this same mutable instance).
    // -------------------------------------------------------------------------

    private final DocumentFonts documentFonts = DocumentFonts.defaultFonts();

    // Parses the score-header / document-metadata subtree (<movement-*>,
    // <identification>, <credit>, <defaults>) and owns its scratch state. Built in
    // the constructor body, after documentFonts is initialized (decision 7).
    private final MusicXmlHeaderReader headerReader;

    // Parses the part/measure/attributes/key/barline subtree. Built in the
    // constructor body, after the barlines resolver is initialized (decision 7).
    private final MusicXmlMeasureReader measureReader;

    // Parses the <note> subtree (pitch/duration/notations/lyrics/range-span
    // markers). Built in the constructor body, after the note accumulator is
    // initialized (decision 7). startNote/finishNote stay in this orchestrator.
    private final MusicXmlNoteReader noteReader;

    // Parses the <direction> subtree (dynamics-as-direction wedges, metronome
    // tempo / metric-modulation marks, annotation words). Built in the constructor
    // body, after the metronome/wedges/annotations resolvers are initialized
    // (decision 7).
    private final MusicXmlDirectionReader directionReader;

    MusicXmlReader() {
        headerReader = new MusicXmlHeaderReader(this, documentFonts);
        measureReader = new MusicXmlMeasureReader(this, barlines);
        noteReader = new MusicXmlNoteReader(this, note);
        directionReader = new MusicXmlDirectionReader(this, metronome, wedges, annotations);
    }

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
     * document fonts). A malformed document surfaces as a thrown exception; the
     * warning list carries only the non-fatal conditions, of which the tuplet
     * load pass is currently the sole producer on this path.
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
            var parsedSong = handler.getSong();

            // Run here rather than in the UI: SongLoader's headless route runs it too, and a
            // song whose tuplets were settled in only one of the two routes would export a
            // different MIDI file than it displays.
            var tupletReport = TupletLoadPass.run(parsedSong);

            return new SongLoadResult.Success(
                parsedSong,
                handler.documentFonts,
                List.of(),
                handler.accidentalsConverted,
                tupletReport);
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

    /**
     * Refuses to fetch anything a {@code DOCTYPE} names, handing the parser an
     * empty stand-in instead. {@code SAXParser.parse} installs this handler as the
     * entity resolver, so this override is all it takes — see
     * {@link SafeXmlParser#emptyEntitySource()}.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        return SafeXmlParser.emptyEntitySource();
    }

    // -------------------------------------------------------------------------
    // Reader-as-context accessors
    //
    // The shared parse spine (the current text buffer, the `where` state cursor,
    // the current line, and the song) lives here on the orchestrator; the
    // sub-readers read and advance it through these package-private accessors
    // rather than owning a copy. Declared as one block up front: some accessors
    // are unused by MusicXmlHeaderReader and exist for the later sub-readers
    // (measure/note/direction) that reuse this same contract.
    // -------------------------------------------------------------------------

    /**
     * A snapshot of the text accumulated for the element currently ending. Always
     * a fresh {@link String} — never the live buffer, which is overwritten on the
     * next element, so a caller may hold or compare the returned value freely.
     */
    String valueString() {
        return value.toString();
    }

    /** Advances the shared {@link Where} state cursor. */
    void setWhere(Where next) {
        where = next;
    }

    /**
     * The trivial end-transition idiom: when {@code qName} closes {@code tag},
     * advance the state cursor to {@code next}. Collapses the ~25 pure-transition
     * {@code endElement} cases to a single delegating call each. Used only by this
     * orchestrator's {@code endElement} switch, where the pure end-cases stay
     * inline; the sub-readers' pure start-transitions use {@link #startTransition}.
     */
    private void endTransition(String qName, String tag, Where next) {
        if (qName.equals(tag)) {
            where = next;
        }
    }

    /**
     * The trivial start-transition idiom: when {@code qName} opens {@code tag},
     * advance the state cursor to {@code next}. The start-side twin of
     * {@link #endTransition}, called by the sub-readers' pure {@code handleStart}
     * dispatchers so the {@code if (qName.equals(tag)) setWhere(next)} idiom lives
     * in one place.
     */
    void startTransition(String qName, String tag, Where next) {
        if (qName.equals(tag)) {
            where = next;
        }
    }

    /**
     * The parsed song while parsing is in progress, or {@code null} before the
     * {@code <score-partwise>} stub is created. Unlike {@link #getSong()} (which
     * throws until parsing completes), this returns the raw field so the
     * sub-readers can null-guard defensively during the parse.
     */
    @Nullable
    Song songOrNull() {
        return song;
    }

    /**
     * Advances the signed-fifths key signature currently in effect. The measure
     * reader's {@code FIFTHS} handler sets it; the orchestrator's
     * {@link #startNewLine()} reads the {@code runningFifths} field to seed each
     * new line.
     */
    void setRunningFifths(int fifths) {
        runningFifths = fifths;
    }

    /**
     * Whether the measure-1 {@code <key>} has set the song default and seeded the
     * running key (see {@link #songDefaultKeySet}).
     */
    boolean songDefaultKeySet() {
        return songDefaultKeySet;
    }

    /** Records that the song default key has been set (see {@link #songDefaultKeySet()}). */
    void setSongDefaultKeySet(boolean set) {
        songDefaultKeySet = set;
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
    ) throws SAXException, UnsupportedFormatException {
        value.delete(0, value.length());

        switch (where) {
            case NONE -> {
                if (qName.equals(MusicXmlTags.SCORE_PARTWISE)) {
                    var version = attributes.getValue(MusicXmlTags.ATTR_VERSION);

                    if (version == null) {
                        throw new UnsupportedFormatException("missing version attribute");
                    }

                    int comparison;

                    try {
                        comparison = Utils.compareVersions(version, MusicXmlTags.VERSION_VALUE);
                    } catch (NumberFormatException e) {
                        throw new UnsupportedFormatException("unparseable version '" + version + '\'');
                    }

                    if (comparison < 0) {
                        throw new UnsupportedFormatException(
                            "unsupported MusicXML version '" + version +
                            "'; requires " + MusicXmlTags.VERSION_VALUE + " or later"
                        );
                    }

                    song = Song.newParsingStub();
                    song.beginSuspendMutationTracking();
                    where = Where.SCORE_PARTWISE;
                } else {
                    throw new UnsupportedFormatException("root <" + qName + '>');
                }
            }
            case SCORE_PARTWISE -> {
                switch (qName) {
                    case MusicXmlTags.MOVEMENT_TITLE -> where = Where.MOVEMENT_TITLE;
                    case MusicXmlTags.MOVEMENT_NUMBER -> where = Where.MOVEMENT_NUMBER;
                    case MusicXmlTags.IDENTIFICATION -> where = Where.IDENTIFICATION;
                    case MusicXmlTags.DEFAULTS -> where = Where.DEFAULTS;
                    case MusicXmlTags.PART_LIST -> where = Where.PART_LIST;
                    case MusicXmlTags.PART -> where = Where.PART;
                    case MusicXmlTags.CREDIT -> headerReader.beginCredit();
                }
            }
            case IDENTIFICATION -> headerReader.handleStartIdentification(qName, attributes);
            case ENCODING -> headerReader.handleStartEncoding(qName);
            case MISCELLANEOUS -> headerReader.handleStartMiscellaneous(qName, attributes);
            case DEFAULTS -> headerReader.handleStartDefaults(qName, attributes);
            case DEFAULTS_PAGE_LAYOUT -> headerReader.handleStartDefaultsPageLayout(qName);
            case CREDIT -> headerReader.handleStartCredit(qName, attributes);
            case PART_LIST -> measureReader.handleStartPartList(qName);
            case PART -> measureReader.handleStartPart(qName);
            case MEASURE -> {
                switch (qName) {
                    case MusicXmlTags.ATTRIBUTES -> where = Where.ATTRIBUTES;
                    case MusicXmlTags.PRINT -> {
                        if (MusicXmlTags.YES.equals(attributes.getValue(MusicXmlTags.ATTR_NEW_SYSTEM))) {
                            startNewLine();
                        }
                    }
                    case MusicXmlTags.BARLINE -> {
                        barlines.beginBarline(attributes.getValue(MusicXmlTags.ATTR_LOCATION));
                        where = Where.BARLINE;
                    }
                    case MusicXmlTags.DIRECTION -> {
                        // A placement attribute (and no <metronome>) marks this
                        // direction as an annotation; tempo/metric-mod/wedge
                        // directions never carry placement — see AnnotationResolver.
                        annotations.beginDirection(
                            AnnotationResolver.placementFor(attributes.getValue(MusicXmlTags.ATTR_PLACEMENT))
                        );
                        where = Where.DIRECTION;
                    }
                    case MusicXmlTags.NOTE -> startNote(attributes);
                }
            }
            case ATTRIBUTES -> measureReader.handleStartAttributes(qName);
            case KEY -> measureReader.handleStartKey(qName);
            case BARLINE -> measureReader.handleStartBarline(qName, attributes);
            case DIRECTION -> directionReader.handleStartDirection(qName);
            case DIRECTION_TYPE -> directionReader.handleStartDirectionType(qName, attributes);
            case METRONOME -> directionReader.handleStartMetronome(qName);
            case METRONOME_NOTE -> directionReader.handleStartMetronomeNote(qName);
            case NOTE -> noteReader.handleStartNote(qName, attributes);
            case LYRIC -> noteReader.handleStartLyric(qName, attributes);
            case PITCH -> noteReader.handleStartPitch(qName);
            case NOTATIONS -> noteReader.handleStartNotations(qName, attributes);
            case TIME_MODIFICATION -> noteReader.handleStartTimeModification(qName);
            case ORNAMENTS -> noteReader.handleStartOrnaments(qName, attributes);
            case ARTICULATIONS -> noteReader.handleStartArticulations(qName);
            case DYNAMICS -> noteReader.handleStartDynamics(qName);
            default -> {
                // All other states: no nested elements of interest
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException, ForeignSoftwareException {
        switch (where) {
            case BAR_STYLE -> measureReader.handleEndBarStyle(qName);
            case BARLINE -> measureReader.handleEndBarline(qName);
            case FIFTHS -> measureReader.handleEndFifths(qName);
            case KEY -> endTransition(qName, MusicXmlTags.KEY, Where.ATTRIBUTES);
            case ATTRIBUTES -> endTransition(qName, MusicXmlTags.ATTRIBUTES, Where.MEASURE);
            case MEASURE -> endTransition(qName, MusicXmlTags.MEASURE, Where.PART);
            case STEP -> noteReader.handleEndStep(qName);
            // <alter> is the sounding semitone; pitch is recovered from
            // <step>/<octave>, so the value is intentionally ignored.
            case ALTER -> endTransition(qName, MusicXmlTags.ALTER, Where.PITCH);
            case OCTAVE -> noteReader.handleEndOctave(qName);
            case PITCH -> endTransition(qName, MusicXmlTags.PITCH, Where.NOTE);
            case REST -> endTransition(qName, MusicXmlTags.REST, Where.NOTE);
            case GRACE -> endTransition(qName, MusicXmlTags.GRACE, Where.NOTE);
            // <duration> is recomputed from <type> + dots, so it is ignored.
            case DURATION -> endTransition(qName, MusicXmlTags.DURATION, Where.NOTE);
            case NOTE_TYPE -> noteReader.handleEndNoteType(qName);
            case DOT -> endTransition(qName, MusicXmlTags.DOT, Where.NOTE);
            case ACCIDENTAL -> noteReader.handleEndAccidental(qName);
            case STEM -> noteReader.handleEndStem(qName);
            // <accent>/<staccato>/<falloff>/<breath-mark> are empty elements, so
            // their end fires immediately and unconditionally returns to articulations.
            case ACCENT, STACCATO, FALLOFF, BREATH_MARK -> where = Where.ARTICULATIONS;
            case ARTICULATIONS -> endTransition(qName, MusicXmlTags.ARTICULATIONS, Where.NOTATIONS);
            case FERMATA -> endTransition(qName, MusicXmlTags.FERMATA, Where.NOTATIONS);
            // The dynamic symbol is an empty element; its end unconditionally
            // returns to the <dynamics> container.
            case DYNAMIC_MARK -> where = Where.DYNAMICS;
            case DYNAMICS -> endTransition(qName, MusicXmlTags.DYNAMICS, Where.NOTATIONS);
            case SLIDE -> endTransition(qName, MusicXmlTags.SLIDE, Where.NOTATIONS);
            case TIE -> noteReader.handleEndTie(qName);
            case BEAM -> noteReader.handleEndBeam(qName);
            case ACTUAL_NOTES -> noteReader.handleEndActualNotes(qName);
            case NORMAL_NOTES -> noteReader.handleEndNormalNotes(qName);
            case TIME_MODIFICATION -> noteReader.handleEndTimeModification(qName);
            case TIED -> endTransition(qName, MusicXmlTags.TIED, Where.NOTATIONS);
            case TUPLET -> endTransition(qName, MusicXmlTags.TUPLET, Where.NOTATIONS);
            case TRILL_MARK -> endTransition(qName, MusicXmlTags.TRILL_MARK, Where.ORNAMENTS);
            case WAVY_LINE -> endTransition(qName, MusicXmlTags.WAVY_LINE, Where.ORNAMENTS);
            case ORNAMENTS -> endTransition(qName, MusicXmlTags.ORNAMENTS, Where.NOTATIONS);
            case NOTATIONS -> endTransition(qName, MusicXmlTags.NOTATIONS, Where.NOTE);
            case SYLLABIC -> noteReader.handleEndSyllabic(qName);
            case LYRIC_TEXT -> noteReader.handleEndLyricText(qName);
            case EXTEND -> endTransition(qName, MusicXmlTags.EXTEND, Where.LYRIC);
            case LYRIC -> noteReader.handleEndLyric(qName);
            case WEDGE -> endTransition(qName, MusicXmlTags.WEDGE, Where.DIRECTION_TYPE);
            case BEAT_UNIT -> directionReader.handleEndBeatUnit(qName);
            case BEAT_UNIT_DOT -> endTransition(qName, MusicXmlTags.BEAT_UNIT_DOT, Where.METRONOME);
            case PER_MINUTE -> directionReader.handleEndPerMinute(qName);
            case METRONOME -> endTransition(qName, MusicXmlTags.METRONOME, Where.DIRECTION_TYPE);
            case METRONOME_TYPE -> directionReader.handleEndMetronomeType(qName);
            case METRONOME_DOT -> endTransition(qName, MusicXmlTags.METRONOME_DOT, Where.METRONOME_NOTE);
            case METRONOME_NOTE -> directionReader.handleEndMetronomeNote(qName);
            case METRONOME_RELATION -> endTransition(qName, MusicXmlTags.METRONOME_RELATION, Where.METRONOME);
            case WORDS -> directionReader.handleEndWords(qName);
            case SOUND -> endTransition(qName, MusicXmlTags.SOUND, Where.DIRECTION);
            case DIRECTION_TYPE -> endTransition(qName, MusicXmlTags.DIRECTION_TYPE, Where.DIRECTION);
            case DIRECTION -> directionReader.handleEndDirection(qName);
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
            case SCORE_PART -> endTransition(qName, MusicXmlTags.SCORE_PART, Where.PART_LIST);
            case PART_LIST -> endTransition(qName, MusicXmlTags.PART_LIST, Where.SCORE_PARTWISE);
            case MOVEMENT_TITLE -> headerReader.handleEndMovementTitle(qName);
            case MOVEMENT_NUMBER -> headerReader.handleEndMovementNumber(qName);
            case IDENTIFICATION -> endTransition(qName, MusicXmlTags.IDENTIFICATION, Where.SCORE_PARTWISE);
            case CREATOR -> headerReader.handleEndCreator(qName);
            // <rights> is write-forward (a fixed copyright string); consumed, not read.
            case RIGHTS -> endTransition(qName, MusicXmlTags.RIGHTS, Where.IDENTIFICATION);
            case ENCODING -> endTransition(qName, MusicXmlTags.ENCODING, Where.IDENTIFICATION);
            case SOFTWARE -> headerReader.handleEndSoftware(qName);
            // <encoding-date> is write-forward; consumed, not read.
            case ENCODING_DATE -> endTransition(qName, MusicXmlTags.ENCODING_DATE, Where.ENCODING);
            case MISCELLANEOUS -> endTransition(qName, MusicXmlTags.MISCELLANEOUS, Where.IDENTIFICATION);
            case MISCELLANEOUS_FIELD -> headerReader.handleEndMiscellaneousField(qName);
            case DEFAULTS -> endTransition(qName, MusicXmlTags.DEFAULTS, Where.SCORE_PARTWISE);
            case DEFAULTS_SCALING -> endTransition(qName, MusicXmlTags.SCALING, Where.DEFAULTS);
            case DEFAULTS_PAGE_LAYOUT -> endTransition(qName, MusicXmlTags.PAGE_LAYOUT, Where.DEFAULTS);
            case DEFAULTS_PAGE_WIDTH -> headerReader.handleEndDefaultsPageWidth(qName);
            case DEFAULTS_STAFF_LAYOUT -> endTransition(qName, MusicXmlTags.STAFF_LAYOUT, Where.DEFAULTS);
            case CREDIT_TYPE -> headerReader.handleEndCreditType(qName);
            case CREDIT_WORDS -> headerReader.handleEndCreditWords(qName);
            case CREDIT -> headerReader.handleEndCredit(qName);
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
                        headerReader.applyHeadMetadata();
                        song.installTerminalAfterParsing();
                        // Grace-host pairing is only settled once every <slide> has been
                        // resolved, so the melisma repair runs here rather than per note. A
                        // file written before the melisma was automatic may put the syllable
                        // on the host, or leave the grace's syllable with no melisma at all.
                        song.getLines().forEach(Line::repairGraceHostMelismas);
                        headerReader.applyInitialTempo();
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

    @Override
    public void endDocument() throws ForeignSoftwareException {
        // Provenance gate: only SongScribe-authored documents are accepted. The
        // <software> tag is captured and owned by the header reader.
        headerReader.checkProvenance();
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
     *
     * <p>Package-private (not private): {@link MusicXmlMeasureReader}'s
     * {@code FIFTHS} handler applies a per-line key change through the reader
     * rather than duplicating the mapping.
     */
    void applyFifthsToLine(Line line, int fifths) {
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
            note.setRelativeX(MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_X, relativeX));
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

        if (note.usedLegacyAccidental()) {
            accidentalsConverted = true;
        }

        // A note-anchored ending start (issue #306) was hosted on an invisible left
        // barline preceding this note; bind that pending anchor to this element.
        endings.resolvePendingNextAnchor(element);

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

    // -------------------------------------------------------------------------
    // State enum
    // -------------------------------------------------------------------------

    enum Where {
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

    /**
     * Thrown at endDocument when the document's {@code <software>} provenance tag
     * is missing, blank, or does not identify SongScribe.
     */
    public static final class ForeignSoftwareException extends SAXException {

        @Nullable
        private final String software;

        ForeignSoftwareException(@Nullable String software) {
            super(SongLoadResult.WrongSoftware.message(software));
            this.software = software;
        }

        @Nullable
        public String software() {
            return software;
        }
    }

    /**
     * Thrown at startElement when the root element is not {@code <score-partwise>}
     * or its {@code version} is missing, unparseable, or older than
     * {@link MusicXmlTags#VERSION_VALUE}.
     */
    public static final class UnsupportedFormatException extends SAXException {

        private final String detail;

        UnsupportedFormatException(String detail) {
            super("Unsupported MusicXML format: " + detail);
            this.detail = detail;
        }

        public String detail() {
            return detail;
        }
    }
}
