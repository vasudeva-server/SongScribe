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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.io.LegacyAccidentals;

/**
 * Accumulates the child values of a single {@code <note>} element parsed by
 * {@link MusicXmlReader}: each child sets a field below via the setters in
 * this class (reset at every {@code <note>} start by {@link #reset()}), and
 * {@link #appendStaffElement} assembles them into a {@link StaffElement} at
 * {@code </note>}.
 *
 * <p>Where-state subtree (note children and their {@code <notations>} descendants):
 *
 * <pre>
 *   MEASURE
 *     └─ NOTE ── relative-x (attr)        ─► xOffset
 *          ├─ GRACE  (marker)             ─► isGrace
 *          ├─ REST   (marker)             ─► isRest
 *          ├─ PITCH
 *          │    ├─ STEP   (text)          ─► step
 *          │    ├─ ALTER  (text)          ─► ignored (pitch from step/octave)
 *          │    └─ OCTAVE (text)          ─► octave
 *          ├─ DURATION (text)             ─► ignored (recomputed from type)
 *          ├─ NOTE_TYPE (text)            ─► typeToken
 *          ├─ DOT (marker, repeats)       ─► dotCount++
 *          ├─ ACCIDENTAL (text+attr)      ─► accidental glyph + parentheses
 *          ├─ STEM (text)                 ─► upper / stemDirectionAuto=false
 *          ├─ TIE (sound, @type)          ─► ignored (write-forward only)
 *          ├─ TIME_MODIFICATION
 *          │    ├─ ACTUAL_NOTES (text)    ─► actualNotes (tuplet grade)
 *          │    ├─ NORMAL_NOTES (text)    ─► normalNotes
 *          │    ├─ NORMAL_TYPE  (text)    ─► normalTypeToken
 *          │    └─ NORMAL_DOT (marker, repeats) ─► normalDotCount++
 *          ├─ BEAM (@number, text)        ─► beam1Type (number=1 only)
 *          ├─ NOTATIONS
 *          │    ├─ ARTICULATIONS
 *          │    │    ├─ ACCENT      ─► ACCENT articulation
 *          │    │    ├─ STACCATO    ─► STACCATO articulation
 *          │    │    ├─ FALLOFF     ─► setFall()
 *          │    │    └─ BREATH_MARK ─► append BREATH_MARK element after note
 *          │    ├─ FERMATA            ─► FermataAttachment
 *          │    ├─ DYNAMICS
 *          │    │    └─ DYNAMIC_MARK ─► DynamicAttachment
 *          │    ├─ SLIDE (@type)      ─► slideType (glissando pairing done by
 *          │    │                        MusicXmlReader.resolveSlide)
 *          │    ├─ TIED (@type)       ─► tiedStart / tiedStop
 *          │    ├─ TUPLET (@type,@rel-y) ─► tupletStart / tupletStop
 *          │    └─ ORNAMENTS
 *          │         ├─ TRILL_MARK        ─► (decorative; pairing via WAVY_LINE)
 *          │         └─ WAVY_LINE (@type) ─► trillStart / trillStop
 *          └─ LYRIC (@number)             ─► one pending Lyric per verse
 *               ├─ SYLLABIC (text)        ─► syllabic token
 *               ├─ LYRIC_TEXT (text)      ─► syllable text (compound marker stripped)
 *               └─ EXTEND (@type)         ─► melisma extender state
 * </pre>
 *
 * <p>The span-marker fields ({@code beam1Type}, {@code tiedStart}/{@code
 * tiedStop}, {@code tupletStart}/{@code tupletStop} + the stated {@code <time-modification>}
 * ratio + relative-y, {@code trillStart}/{@code trillStop} + relative-y) are this note's raw
 * markers; {@link #spanMarkers()} snapshots them for {@link RangeSpanResolver}
 * to pair with the run's pending anchor and build the
 * {@code Beam}/{@code Tie}/{@code Tuplet}/{@code Trill} RangeElements.
 */
final class NoteAccumulator {

    @Nullable
    private String typeToken = null;

    private boolean isRest = false;
    private boolean isGrace = false;
    private boolean hasPitch = false;
    private char step = ' ';
    private int octave = 0;
    private int dotCount = 0;

    @Nullable
    private String accidentalToken = null;

    private boolean accidentalParenthesized = false;
    private boolean stemPresent = false;
    private boolean stemUp = false;
    private boolean relativeXPresent = false;
    private double relativeXTenths = 0.0;
    private boolean hasAccent = false;
    private boolean hasStaccato = false;
    private boolean hasFermata = false;

    @Nullable
    private DynamicType dynamicType = null;

    private boolean hasFall = false;

    @Nullable
    private String slideType = null;

    private boolean hasBreathMark = false;

    // True when this note's <accidental> token was a retired legacy token
    // (e.g. natural-flat) converted to its replacement via LegacyAccidentals.
    // Per-note, like hasBreathMark; MusicXmlReader ORs it into its own
    // persistent accidentalsConverted flag after appendStaffElement returns.
    private boolean usedLegacyAccidental = false;

    // Tuplet grade for the current note, parsed from <actual-notes> (0 if absent).
    private int actualNotes = 0;

    // The rest of the current note's <time-modification>: the count of written
    // values the group occupies, that written value's <type> token, and its dots.
    // A null token means the file stated no <normal-type>, which is how the
    // reader tells a stated ratio it can trust from one it must derive.
    private int normalNotes = 0;

    @Nullable
    private String normalTypeToken = null;

    private int normalDotCount = 0;

    // Primary-beam (number="1") value for the current note (begin/continue/end);
    // null when this note carries no number="1" <beam>. true while the <beam>
    // currently being parsed is number="1".
    @Nullable
    private String beam1Type = null;

    private boolean beamLevelIsOne = false;

    private boolean tiedStart = false;
    private boolean tiedStop = false;

    private boolean tupletStart = false;
    private boolean tupletStop = false;
    private boolean tupletRelativeYPresent = false;
    private double tupletRelativeYTenths = 0.0;

    private boolean trillStart = false;
    private boolean trillStop = false;
    private boolean trillRelativeYPresent = false;
    private double trillRelativeYTenths = 0.0;

    // -------------------------------------------------------------------------
    // Lyric accumulation. Each <lyric> child of this <note> is finalized at
    // </lyric> into `lyrics` (one Lyric per verse), then copied onto
    // element.lyrics in appendStaffElement. The scratch fields below hold the
    // <lyric> currently being parsed; beginLyric resets them at each <lyric> start.
    // -------------------------------------------------------------------------

    private final List<Lyric> lyrics = new ArrayList<>();

    private int lyricVerse = 1;
    private String lyricSyllabicToken = "";
    private String lyricText = "";
    private Lyric.Extend lyricExtend = Lyric.Extend.NONE;

    /**
     * Clears all per-note accumulation fields. Called at every {@code <note>} start.
     */
    void reset() {
        typeToken = null;
        isRest = false;
        isGrace = false;
        hasPitch = false;
        step = ' ';
        octave = 0;
        dotCount = 0;
        accidentalToken = null;
        accidentalParenthesized = false;
        stemPresent = false;
        stemUp = false;
        relativeXPresent = false;
        relativeXTenths = 0.0;
        hasAccent = false;
        hasStaccato = false;
        hasFermata = false;
        dynamicType = null;
        hasFall = false;
        slideType = null;
        hasBreathMark = false;
        usedLegacyAccidental = false;
        actualNotes = 0;
        normalNotes = 0;
        normalTypeToken = null;
        normalDotCount = 0;
        beam1Type = null;
        beamLevelIsOne = false;
        tiedStart = false;
        tiedStop = false;
        tupletStart = false;
        tupletStop = false;
        tupletRelativeYPresent = false;
        tupletRelativeYTenths = 0.0;
        trillStart = false;
        trillStop = false;
        trillRelativeYPresent = false;
        trillRelativeYTenths = 0.0;

        // The accumulated lyrics are note-scoped: clearing them here is what stops
        // one note's <lyric> children from bleeding onto the following note.
        lyrics.clear();
    }

    // -------------------------------------------------------------------------
    // Setters/markers — called from MusicXmlReader's startElement/endElement
    // -------------------------------------------------------------------------

    void markGrace() {
        isGrace = true;
    }

    void markRest() {
        isRest = true;
    }

    void setStep(char step) {
        this.step = step;
        hasPitch = true;
    }

    void setOctave(int octave) {
        this.octave = octave;
        hasPitch = true;
    }

    void incrementDotCount() {
        dotCount++;
    }

    void setTypeToken(String typeToken) {
        this.typeToken = typeToken;
    }

    void setAccidentalToken(String accidentalToken) {
        this.accidentalToken = accidentalToken;
    }

    void setAccidentalParenthesized(boolean accidentalParenthesized) {
        this.accidentalParenthesized = accidentalParenthesized;
    }

    void markStemPresent() {
        stemPresent = true;
    }

    void setStemUp(boolean stemUp) {
        this.stemUp = stemUp;
    }

    void setRelativeX(double relativeXTenths) {
        relativeXPresent = true;
        this.relativeXTenths = relativeXTenths;
    }

    void markAccent() {
        hasAccent = true;
    }

    void markStaccato() {
        hasStaccato = true;
    }

    void markFermata() {
        hasFermata = true;
    }

    void setDynamicType(DynamicType dynamicType) {
        this.dynamicType = dynamicType;
    }

    void markFall() {
        hasFall = true;
    }

    void setSlideType(@Nullable String slideType) {
        this.slideType = slideType;
    }

    void markBreathMark() {
        hasBreathMark = true;
    }

    void setActualNotes(int actualNotes) {
        this.actualNotes = actualNotes;
    }

    void setNormalNotes(int normalNotes) {
        this.normalNotes = normalNotes;
    }

    void setNormalTypeToken(String normalTypeToken) {
        this.normalTypeToken = normalTypeToken;
    }

    void incrementNormalDotCount() {
        normalDotCount++;
    }

    void setBeamLevelIsOne(boolean beamLevelIsOne) {
        this.beamLevelIsOne = beamLevelIsOne;
    }

    /**
     * Captures the primary-beam value only when the just-closed {@code <beam>}
     * was {@code number="1"}; secondary levels and hooks are write-forward and
     * ignored on read.
     */
    void endBeam(String beamText) {
        if (beamLevelIsOne) {
            beam1Type = beamText;
        }
    }

    void addTied(@Nullable String type) {
        tiedStart |= MusicXmlTags.TYPE_START.equals(type);
        tiedStop |= MusicXmlTags.TYPE_STOP.equals(type);
    }

    void markTupletStart() {
        tupletStart = true;
    }

    void markTupletStop() {
        tupletStop = true;
    }

    /**
     * Captures the optional {@code relative-y} (tenths) on a {@code <tuplet>}
     * start marker; the writer carries {@code verticalPositionSs} here.
     */
    void captureTupletRelativeY(Attributes attributes) throws SAXException {
        var relativeY = captureRelativeYTenths(attributes);

        if (relativeY != null) {
            tupletRelativeYPresent = true;
            tupletRelativeYTenths = relativeY;
        }
    }

    void markTrillStart() {
        trillStart = true;
    }

    void markTrillStop() {
        trillStop = true;
    }

    /**
     * Captures the optional {@code relative-y} (tenths) on a {@code <wavy-line>}
     * start marker; the writer carries {@code yPositionSs} here.
     */
    void captureTrillRelativeY(Attributes attributes) throws SAXException {
        var relativeY = captureRelativeYTenths(attributes);

        if (relativeY != null) {
            trillRelativeYPresent = true;
            trillRelativeYTenths = relativeY;
        }
    }

    /**
     * Parses the {@code relative-y} attribute (tenths) if present, shared by
     * {@link #captureTupletRelativeY} and {@link #captureTrillRelativeY}.
     */
    @Nullable
    private static Double captureRelativeYTenths(Attributes attributes) throws SAXException {
        var relativeY = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_Y);

        if (relativeY == null) {
            return null;
        }

        return MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_Y, relativeY);
    }

    // -------------------------------------------------------------------------
    // Lyric accumulation — called from MusicXmlReader for each <lyric> subtree.
    // -------------------------------------------------------------------------

    /**
     * Begins a fresh {@code <lyric>} for the given verse, clearing the per-lyric
     * scratch fields so leftover state from the previous {@code <lyric>} cannot
     * leak into this one.
     */
    void beginLyric(int verse) {
        lyricVerse = verse;
        lyricSyllabicToken = "";
        lyricText = "";
        lyricExtend = Lyric.Extend.NONE;
    }

    void setLyricSyllabicToken(String syllabicToken) {
        lyricSyllabicToken = syllabicToken;
    }

    void setLyricText(String text) {
        lyricText = text;
    }

    void setLyricExtend(Lyric.Extend extend) {
        lyricExtend = extend;
    }

    /**
     * Finalizes the {@code <lyric>} currently being parsed into a {@link Lyric}
     * and appends it to this note's pending list. Mirrors
     * {@code StaffElementIO.endElement11}'s lyric-close branch: STOP/CONTINUE
     * carriers get a null syllabic and empty text; for BEGIN/MIDDLE syllabics a
     * trailing compound-word marker is stripped and {@code compound} set. The
     * {@link Lyric} constructor re-validates the carrier/syllabic contract.
     */
    void endLyric() {
        Lyric.@Nullable Syllabic syllabic;
        var text = lyricText;
        var compound = false;

        if (Lyric.isCarrier(lyricExtend)) {
            // Carriers mark a melisma boundary on a note with no text of its own.
            syllabic = null;
            text = "";
        } else {
            syllabic = SyllabicMapping.forSyllabicToken(lyricSyllabicToken);

            // A trailing compound-word marker is only meaningful on a syllable
            // that continues into the next (BEGIN/MIDDLE); strip it and flag
            // compound so the boundary round-trips.
            if (Lyric.syllabicContinues(syllabic)) {
                var stripped = Lyric.stripCompoundMarker(text);
                text = stripped.text();
                compound = stripped.hadMarker();
            }
        }

        lyrics.add(new Lyric(lyricVerse, text, lyricExtend, syllabic, compound));
    }

    // -------------------------------------------------------------------------
    // Accessors — read by MusicXmlReader's breath-mark logic
    // -------------------------------------------------------------------------

    boolean hasBreathMark() {
        return hasBreathMark;
    }

    /**
     * True when this note's {@code <accidental>} token was a retired legacy
     * token converted to its replacement via {@link LegacyAccidentals}.
     */
    boolean usedLegacyAccidental() {
        return usedLegacyAccidental;
    }

    /**
     * Snapshots this note's raw span markers for {@link RangeSpanResolver} to
     * pair with each run's pending anchor.
     */
    SpanMarkers spanMarkers() {
        return new SpanMarkers(
            slideType,
            beam1Type,
            tiedStart,
            tiedStop,
            tupletStart,
            tupletStop,
            actualNotes,
            normalNotes,
            normalTypeToken,
            normalDotCount,
            tupletRelativeYPresent,
            tupletRelativeYTenths,
            trillStart,
            trillStop,
            trillRelativeYPresent,
            trillRelativeYTenths
        );
    }

    /**
     * This note's raw span markers ({@code beam1Type}, {@code tiedStart}/{@code
     * tiedStop}, {@code tupletStart}/{@code tupletStop} + the stated
     * {@code <time-modification>} ratio + relative-y, {@code trillStart}/{@code
     * trillStop} + relative-y, {@code slideType}),
     * snapshotted by {@link #spanMarkers()} for {@link RangeSpanResolver} to
     * pair with the run's pending anchor and build the
     * {@code Beam}/{@code Tie}/{@code Tuplet}/{@code Trill}/glissando RangeElements.
     */
    record SpanMarkers(
        @Nullable String slideType,
        @Nullable String beam1Type,
        boolean tiedStart,
        boolean tiedStop,
        boolean tupletStart,
        boolean tupletStop,
        int actualNotes,
        int normalNotes,
        @Nullable String normalTypeToken,
        int normalDotCount,
        boolean tupletRelativeYPresent,
        double tupletRelativeYTenths,
        boolean trillStart,
        boolean trillStop,
        boolean trillRelativeYPresent,
        double trillRelativeYTenths
    ) {}

    /**
     * Assembles the accumulated state into a {@link StaffElement}, appends it to
     * {@code line}, and resolves its non-span attachments (articulations,
     * fermata, dynamic, fall). Called at {@code </note>}, after the reader has
     * flushed any pending REPEAT_RIGHT so that barline lands ahead of this note.
     *
     * @throws SAXException if the note is missing its {@code <type>} element, or
     *                       carries an unrecognised one
     */
    StaffElement appendStaffElement(Line line) throws SAXException {
        if (typeToken == null) {
            throw new SAXException("<note> is missing its <type> element");
        }

        var elementType = NoteTypeMapping.forTypeToken(typeToken, isRest, isGrace);

        if (elementType == null) {
            throw new SAXException("Unrecognised <type> token: '" + typeToken + "'");
        }

        var element = elementType.newInstance();

        // <step> + <octave> are authoritative for pitch (key-independent); <alter>
        // is ignored on read. Rests carry no pitch.
        if (hasPitch) {
            element.setStaffPosition(PitchSpelling.staffPositionFor(step, octave));
        }

        element.setDotCount(dotCount);

        // The displayed accidental glyph comes from <accidental>, not <alter>.
        if (accidentalToken != null) {
            var accidental = AccidentalMapping.forToken(accidentalToken);

            // A token the live map does not know may still be a retired one.
            if (accidental == null) {
                accidental = LegacyAccidentals.forLegacyToken(accidentalToken);
                usedLegacyAccidental = accidental != null;
            }

            if (accidental != null) {
                element.setAccidental(accidental);
                // setAccidentalInParentheses must follow setAccidental: it only
                // takes effect when an accidental is present.
                element.setAccidentalInParentheses(accidentalParenthesized);
            }
        }

        // A present <stem> is a manual override; its absence means auto direction.
        if (stemPresent) {
            element.setUpper(stemUp);
            element.setStemDirectionAuto(false);
        } else {
            element.setStemDirectionAuto(true);
        }

        if (relativeXPresent) {
            element.setXOffsetPx(ScaleContext.ssToRoundedPx(relativeXTenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE));
        }

        // Append before adding attachments so each attachment's parent line is wired.
        line.addElement(element);

        if (hasAccent) {
            element.addArticulation(new Articulation(element, ArticulationType.ACCENT));
        }

        if (hasStaccato) {
            element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
        }

        if (hasFermata) {
            element.addAttachment(new FermataAttachment(element));
        }

        if (dynamicType != null) {
            element.addAttachment(new DynamicAttachment(element, dynamicType));
        }

        if (hasFall) {
            element.setFall();
        }

        // Copy this note's accumulated lyrics onto the element; each was already
        // constructed (and validated) at its </lyric> by endLyric.
        element.lyrics.addAll(lyrics);

        return element;
    }
}
