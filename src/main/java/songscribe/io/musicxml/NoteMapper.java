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
import java.util.Set;

import jakarta.xml.bind.JAXBElement;

import org.audiveris.proxymusic.Articulations;
import org.audiveris.proxymusic.BeamValue;
import org.audiveris.proxymusic.Dynamics;
import org.audiveris.proxymusic.Fermata;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.Ornaments;
import org.audiveris.proxymusic.Slide;
import org.audiveris.proxymusic.StartStop;
import org.audiveris.proxymusic.StartStopContinue;
import org.audiveris.proxymusic.Syllabic;
import org.audiveris.proxymusic.TextElementData;
import org.audiveris.proxymusic.Tied;
import org.audiveris.proxymusic.TiedType;
import org.audiveris.proxymusic.Tuplet;
import org.audiveris.proxymusic.WavyLine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Direction;
import songscribe.dom.Dots;
import songscribe.dom.Dottable;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Stem;
import songscribe.engraving.PitchOutOfRangeException;
import songscribe.io.DocumentValidation;
import songscribe.io.LegacyAccidentals;
import songscribe.io.LyricNumbers;

/**
 * Maps one unmarshalled {@code <note>} onto a {@link StaffElement} appended to a
 * {@link Line}, together with its per-note attachments and lyrics.
 *
 * <p>A {@code Note} object already holds every child, so each value is read where it is used —
 * there is no per-note scratch accumulator.
 *
 * <p>Three children are deliberately not read. {@code <duration>} is recomputed
 * from {@code <type>} and its dots; {@code <alter>} is the sounding semitone,
 * while the written pitch comes from {@code <step>}/{@code <octave>}; and the
 * sounding {@code <tie>} is write-forward, {@code <tied>} being the source of truth
 * for span reconstruction.
 *
 * <p>One thing a note carries does <em>not</em> belong to the element it produces
 * and is handed back to {@link MeasureMapper} instead: a {@code <breath-mark>}
 * articulation, which becomes a separate {@code BREATH_MARK} element appended after
 * this note.
 *
 * <p>Range spans are not handed back either. A beam, tie, tuplet bracket, trill or
 * glissando pairs two notes, so it cannot be settled while standing on one — but the
 * whole part is already parsed, so {@link MeasureMapper}'s span passes come back and
 * read each note's markers when they need them. The readers below are what they call:
 * one per span kind, each returning exactly what its pass consumes.
 */
final class NoteMapper {

    private static final Logger LOG = LoggerFactory.getLogger(NoteMapper.class);

    private NoteMapper() {}

    /**
     * The result of mapping one {@code <note>}: the appended element plus the three
     * facts about the note that its own element cannot carry.
     *
     * @param element               the element appended to the line
     * @param usedLegacyAccidental  true when the {@code <accidental>} token was a
     *                              retired one converted to its replacement
     * @param droppedLyric          true when a {@code <lyric>} was dropped because its
     *                              number names no script
     * @param hasBreathMark         true when a {@code BREATH_MARK} element must be
     *                              appended immediately after this note
     */
    record MappedNote(
        StaffElement element,
        boolean usedLegacyAccidental,
        boolean droppedLyric,
        boolean hasBreathMark
    ) {}

    /**
     * One note's {@code <tuplet>} bracket marker, together with the
     * {@code <time-modification>} the bracket's anchor states.
     *
     * <p>{@code yOffsetSs} is already converted to staff spaces; an absent
     * {@code relative-y} reads as 0, the value the model treats as "unset".
     */
    record TupletMarker(
        boolean start,
        boolean stop,
        double yOffsetSs,
        int actualNotes,
        int normalNotes,
        @Nullable String normalTypeToken,
        Dots normalDots
    ) {}

    /**
     * One note's {@code <wavy-line>} markers. A note carrying both bounds a
     * single-note trill. {@code yOffsetSs} is already in staff spaces.
     */
    record TrillMarker(boolean start, boolean stop, double yOffsetSs) {}

    /**
     * Maps {@code pmNote} to a {@link StaffElement}, appends it to {@code line},
     * and resolves its non-span attachments (articulations, fermata, dynamic,
     * fall) and its lyrics. {@code relative-x} is taken as the user's horizontal offset
     * only when {@code features} declares it. A {@code <lyric>} whose number names no
     * script does not refuse the document: it is dropped, and the result says so, for the
     * caller to report with {@link songscribe.dom.ParsedSong#lyricDroppedEntry}.
     *
     * @throws SAXException if the note is missing its {@code <type>} element, or carries an
     *                      unrecognised one; a {@code <pitch>} that is unspellable or lies
     *                      off the staff; more than two {@code <dot>} elements; or a second
     *                      fermata or dynamic for the element
     */
    static MappedNote map(Note pmNote, Line line, Set<SupportedFeature> features) throws SAXException {
        var typeToken = ProxyMusicAccess.requireNoteTypeToken(pmNote).trim();
        var isRest = ProxyMusicAccess.isRest(pmNote);
        var elementType = NoteTypeMapping.forTypeToken(typeToken, isRest, ProxyMusicAccess.isGrace(pmNote));

        if (elementType == null) {
            throw new MusicXmlReader.InvalidFormatException("Unrecognised <type> token: '" + typeToken + '\'');
        }

        var element = elementType.newInstance();

        // <step> + <octave> are authoritative for pitch (key-independent); <alter>
        // is ignored on read. Rests carry no pitch.
        var pitch = ProxyMusicAccess.pitch(pmNote);
        var usedLegacyAccidental = false;

        if (element instanceof NoteElement note) {
            if (pitch != null) {
                // A pitch beyond the outermost ledger line is valid MusicXML stating something
                // this program cannot represent, not a bug in the mapper, so it is refused here
                // rather than left to the reader's runtime-exception backstop. Its detail is the
                // only thing that reaches the user, and it names only the staff position, which
                // the document never states, so the pitch as the document spells it leads it.
                try {
                    note.setStaffPosition(PitchSpelling.staffPositionFor(pitch.step(), pitch.octave()));
                } catch (PitchOutOfRangeException e) {
                    throw new MusicXmlReader.InvalidFormatException(
                        "<pitch> " + pitch.step() + pitch.octave() + ": " + e.detail());
                }
            }

            usedLegacyAccidental = applyAccidental(pmNote, note);

            // A present <stem> is a manual override; its absence means auto direction. A grace
            // note's stem is always up, whatever the document says, so it never carries a
            // forced stem: NoteElement.direction() answers UP for it before consulting the stem.
            if (ProxyMusicAccess.hasStem(pmNote) && !note.getType().isGraceNote()) {
                note.setStem(new Stem.Forced(
                    ProxyMusicAccess.stemIsUp(pmNote) ? Direction.UP : Direction.DOWN));
            } else {
                note.setStem(Stem.AUTO);
            }
        }

        if (element instanceof Dottable dottable) {
            dottable.setDots(dotsOf(pmNote.getDot().size(), MusicXmlTags.DOT));
        }

        // A document that does not declare relative-x wrote its layout position there,
        // so taking it as an offset would displace every note by its own X.
        if (features.contains(SupportedFeature.NOTE_RELATIVE_X)) {
            var relativeXTenths = ProxyMusicAccess.decimal(pmNote.getRelativeX());

            if (relativeXTenths != null) {
                element.setUserXOffsetSs(relativeXTenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE);
            }
        }

        // Append before adding attachments so each attachment's parent line is wired.
        line.addElement(element);

        var notations = readNotations(pmNote);
        applyNotationAttachments(notations, element);
        var droppedLyric = putLyrics(pmNote, element);

        return new MappedNote(element, usedLegacyAccidental, droppedLyric, notations.hasBreathMark());
    }

    // -------------------------------------------------------------------------
    // Note body
    // -------------------------------------------------------------------------

    /**
     * Applies the accidental state, which comes from {@code <accidental>} and never
     * from {@code <alter>}. A note without an {@code <accidental>} stays {@code Adopting}.
     * Returns true when the token was a retired legacy one converted to its
     * replacement — the caller ORs that into the document-wide accidentals-converted
     * flag.
     */
    private static boolean applyAccidental(Note pmNote, NoteElement element) {
        var accidentalToken = ProxyMusicAccess.accidentalToken(pmNote);

        if (accidentalToken == null) {
            return false;
        }

        var accidental = AccidentalMapping.forToken(accidentalToken);
        var usedLegacyAccidental = false;

        // A token the live map does not know may still be a retired one.
        if (accidental == null) {
            accidental = LegacyAccidentals.forLegacyToken(accidentalToken);
            usedLegacyAccidental = accidental != null;
        }

        if (accidental != null) {
            element.choose(accidental);
        }

        return usedLegacyAccidental;
    }

    // -------------------------------------------------------------------------
    // <notations>
    // -------------------------------------------------------------------------

    /**
     * The {@code <notations>} content that belongs to the note itself. The
     * span-borne children — {@code <tied>}, {@code <tuplet>}, {@code <slide>} and
     * {@code <wavy-line>} — are not here: they pair across notes and are read by the
     * span passes through the accessors further down.
     */
    private record NoteNotations(
        boolean hasAccent,
        boolean hasStaccato,
        boolean hasFall,
        boolean hasBreathMark,
        boolean hasFermata,
        @Nullable DynamicType dynamicType
    ) {}

    private static NoteNotations readNotations(Note pmNote) {
        var hasAccent = false;
        var hasStaccato = false;
        var hasFall = false;
        var hasBreathMark = false;
        var hasFermata = false;
        DynamicType dynamicType = null;

        for (var child : notationChildren(pmNote)) {
            switch (child) {
                case Articulations articulations -> {
                    for (var name : namesOf(articulations)) {
                        switch (name) {
                            case MusicXmlTags.ACCENT -> hasAccent = true;
                            case MusicXmlTags.STACCATO -> hasStaccato = true;
                            case MusicXmlTags.FALLOFF -> hasFall = true;
                            case MusicXmlTags.BREATH_MARK -> hasBreathMark = true;
                            default -> {
                                // Other articulations are not modelled.
                            }
                        }
                    }
                }
                case Fermata _ -> hasFermata = true;
                case Dynamics dynamics -> {
                    // The child element's name is the dynamic symbol itself
                    // (e.g. <f/>, <mf/>); an unrecognised one is ignored.
                    for (var name : namesOf(dynamics)) {
                        var symbol = DynamicType.fromSymbol(name);

                        if (symbol != null) {
                            dynamicType = symbol;
                        }
                    }
                }
                default -> {
                    // Span-borne children, slurs, technicals and the rest.
                }
            }
        }

        return new NoteNotations(hasAccent, hasStaccato, hasFall, hasBreathMark, hasFermata, dynamicType);
    }

    /**
     * Every {@code <notations>} child of {@code pmNote}, across all its
     * {@code <notations>} elements. A note may carry more than one, and their
     * children are read as one set — exactly as the SAX reader accumulated them.
     *
     * <p>The span passes call this once each per note, so the ordinary case of a single
     * {@code <notations>} hands back its own list rather than a copy of it.
     */
    private static List<Object> notationChildren(Note pmNote) {
        var notations = pmNote.getNotations();

        if (notations.isEmpty()) {
            return List.of();
        }

        if (notations.size() == 1) {
            return notations.getFirst().getTiedOrSlurOrTuplet();
        }

        var children = new ArrayList<>();

        for (var each : notations) {
            children.addAll(each.getTiedOrSlurOrTuplet());
        }

        return children;
    }

    private static List<String> namesOf(Articulations articulations) {
        return elementNames(articulations.getAccentOrStrongAccentOrStaccato());
    }

    private static List<String> namesOf(Dynamics dynamics) {
        return elementNames(dynamics.getPOrPpOrPpp());
    }

    private static List<String> elementNames(List<JAXBElement<?>> elements) {
        var names = new ArrayList<String>(elements.size());

        for (var element : elements) {
            var name = ProxyMusicAccess.elementName(element);

            if (name != null) {
                names.add(name);
            }
        }

        return names;
    }

    private static List<WavyLine> ornamentWavyLines(Ornaments ornaments) {
        return ProxyMusicAccess.jaxbValues(
            ornaments.getTrillMarkOrTurnOrDelayedTurn(),
            MusicXmlTags.WAVY_LINE,
            WavyLine.class
        );
    }

    /**
     * Puts the note-borne attachments on {@code element}. A {@code <notations>} set may state
     * one fermata and one dynamic, and the element holds at most one of each class, so each
     * goes on through {@link DocumentValidation#attachUnique}, which refuses the document
     * rather than the attachment when the element already carries one.
     *
     * @throws SAXException if {@code element} already carries a fermata or a dynamic
     */
    private static void applyNotationAttachments(NoteNotations notations, StaffElement element)
            throws SAXException {
        // Only a note takes articulations and a fall; a rest's <notations> can carry the
        // fermata and the dynamic below.
        if (element instanceof NoteElement note) {
            if (notations.hasAccent()) {
                note.addArticulation(new Articulation(ArticulationType.ACCENT));
            }

            if (notations.hasStaccato()) {
                note.addArticulation(new Articulation(ArticulationType.STACCATO));
            }

            if (notations.hasFall()) {
                note.setSlide(songscribe.dom.Slide.FALL);
            }
        }

        if (notations.hasFermata()) {
            DocumentValidation.attachUnique(LOG, element, new FermataAttachment());
        }

        var dynamicType = notations.dynamicType();

        if (dynamicType != null) {
            DocumentValidation.attachUnique(LOG, element, new DynamicAttachment(dynamicType));
        }
    }

    // -------------------------------------------------------------------------
    // Range-span markers — read by MeasureMapper's span passes, one reader each
    // -------------------------------------------------------------------------

    /** This note's {@code <slide>} type token, or {@code null} when it carries none. */
    static @Nullable String slideType(Note pmNote) {
        for (var child : notationChildren(pmNote)) {
            if (child instanceof Slide slide) {
                return ProxyMusicAccess.token(slide.getType(), StartStop::value);
            }
        }

        return null;
    }

    /**
     * This note's primary {@code <beam number="1">} value, or {@code null} when it
     * carries none. Secondary levels and hooks are write-forward — layout re-derives
     * them — so only the primary beam drives span collapse.
     */
    static @Nullable String primaryBeamType(Note pmNote) {
        String beamType = null;

        for (var beam : pmNote.getBeam()) {
            if (beam.getNumber() == 1) {
                beamType = ProxyMusicAccess.token(beam.getValue(), BeamValue::value);
            }
        }

        return beamType;
    }

    /**
     * Answers whether this note opens a tie. Only the start is read: the note a tie ends on is
     * derived from the anchor, so a {@code <tied type="stop">} carries nothing the reader needs.
     *
     * @return {@code true} when one of the note's {@code <tied>} markers is a start
     */
    static boolean startsTie(Note pmNote) {
        for (var child : notationChildren(pmNote)) {
            if (child instanceof Tied tied && tied.getType() == TiedType.START) {
                return true;
            }
        }

        return false;
    }

    /**
     * This note's {@code <tuplet>} bracket marker and the {@code <time-modification>}
     * beside it. The vertical offset is carried on the start bracket, which is where
     * the writer puts it.
     *
     * @throws SAXException if the {@code <time-modification>} states more than two
     *                      {@code <normal-dot>} elements
     */
    static TupletMarker tupletMarker(Note pmNote) throws SAXException {
        var start = false;
        var stop = false;
        var yOffsetSs = 0.0;

        for (var child : notationChildren(pmNote)) {
            if (child instanceof Tuplet tuplet) {
                var type = tuplet.getType();

                if (type == StartStop.START) {
                    start = true;
                    yOffsetSs = MusicXmlUnits.tenthsToExactSs(tuplet.getRelativeY());
                } else if (type == StartStop.STOP) {
                    stop = true;
                }
            }
        }

        var timeModification = pmNote.getTimeModification();

        if (timeModification == null) {
            return new TupletMarker(start, stop, yOffsetSs, 0, 0, null, Dots.NONE);
        }

        return new TupletMarker(
            start,
            stop,
            yOffsetSs,
            ProxyMusicAccess.integer(timeModification.getActualNotes(), 0),
            ProxyMusicAccess.integer(timeModification.getNormalNotes(), 0),
            timeModification.getNormalType(),
            dotsOf(timeModification.getNormalDot().size(), MusicXmlTags.NORMAL_DOT)
        );
    }

    /**
     * Converts a dot count the document states into {@link Dots}, refusing the document when
     * the count is one this program cannot represent. Valid MusicXML may state any number of
     * dots; the model holds at most two.
     *
     * @param count   the number of dot elements the document carries
     * @param element the name of the dot element counted, so the detail names it
     * @throws SAXException if {@code count} is more than {@link Dots} can hold
     */
    private static Dots dotsOf(int count, String element) throws SAXException {
        try {
            return Dots.of(count);
        } catch (IllegalArgumentException e) {
            throw new MusicXmlReader.InvalidFormatException(
                "<" + element + "> count " + count + " exceeds the two dots SongScribe can represent");
        }
    }

    /**
     * This note's {@code <wavy-line>} markers, found inside its {@code <ornaments>}.
     * {@code <trill-mark>} is decorative; the wavy-line pair is what bounds the span.
     */
    static TrillMarker trillMarker(Note pmNote) {
        var start = false;
        var stop = false;
        var yOffsetSs = 0.0;

        for (var child : notationChildren(pmNote)) {
            if (!(child instanceof Ornaments ornaments)) {
                continue;
            }

            for (var wavyLine : ornamentWavyLines(ornaments)) {
                var type = wavyLine.getType();

                if (type == StartStopContinue.START) {
                    start = true;
                    yOffsetSs = MusicXmlUnits.tenthsToExactSs(wavyLine.getRelativeY());
                } else if (type == StartStopContinue.STOP) {
                    stop = true;
                }
            }
        }

        return new TrillMarker(start, stop, yOffsetSs);
    }

    // -------------------------------------------------------------------------
    // Lyrics
    // -------------------------------------------------------------------------

    /**
     * Maps this note's {@code <lyric>} children onto {@code element}, one {@link Lyric} per
     * script. Mirrors {@code StaffElementIO.endElement11}'s lyric-close branch: a carrier
     * ({@code <extend type="stop|continue"/>} with no text of its own) gets a null
     * syllabic and empty text; for a BEGIN/MIDDLE syllabic a trailing compound-word
     * marker is stripped and {@code compound} set, so the word boundary
     * round-trips. The {@link Lyric} constructor re-validates the
     * carrier/syllabic contract. A blank text-bearing syllable carries nothing a user wrote,
     * so {@link #mapLyric} drops it rather than the whole document. A {@code <lyric>} whose
     * number names no script is dropped here, whatever it holds, and reported.
     *
     * @return true if a {@code <lyric>} was dropped because its number names no script
     */
    private static boolean putLyrics(Note pmNote, StaffElement element) {
        var droppedLyric = false;

        for (var pmLyric : pmNote.getLyric()) {
            var script = LyricNumbers.toScript(ProxyMusicAccess.lyricVerse(pmLyric));

            if (script == null) {
                droppedLyric = true;
                continue;
            }

            var lyric = mapLyric(pmLyric, script);

            if (lyric != null) {
                element.putLyric(lyric);
            }
        }

        return droppedLyric;
    }

    private static @Nullable Lyric mapLyric(org.audiveris.proxymusic.Lyric pmLyric, Song.Script script) {
        var syllabicToken = "";
        var text = "";

        // <syllabic> and <text> share one heterogeneous choice list; <extend> is a
        // typed property beside it (XJC collapsed the schema's two occurrences of
        // <extend> into one).
        for (var child : pmLyric.getElisionAndSyllabicAndText()) {
            if (child instanceof Syllabic syllabic) {
                syllabicToken = syllabic.value();
            } else if (child instanceof TextElementData textElement) {
                // Not trimmed: the text is emitted inline with no surrounding
                // whitespace, and a trailing compound marker must survive intact.
                var value = textElement.getValue();
                text = value == null ? "" : value;
            }
        }

        var pmExtend = pmLyric.getExtend();
        Lyric.Extend extend;

        if (pmExtend == null) {
            extend = Lyric.Extend.NONE;
        } else {
            // An <extend> with an absent or unrecognised type is a melisma START
            // (see SyllabicMapping).
            extend = SyllabicMapping.forExtendToken(
                ProxyMusicAccess.token(pmExtend.getType(), StartStopContinue::value)
            );
        }

        Lyric.@Nullable Syllabic syllabic;
        var compound = false;

        if (Lyric.isCarrier(extend)) {
            // Carriers mark a melisma boundary on a note with no text of its own.
            syllabic = null;
            text = "";
        } else {
            syllabic = SyllabicMapping.forSyllabicToken(syllabicToken);

            // A trailing compound-word marker is only meaningful on a syllable that
            // continues into the next (BEGIN/MIDDLE).
            if (Lyric.syllabicContinues(syllabic)) {
                var stripped = Lyric.stripCompoundMarker(text);
                text = stripped.text();
                compound = stripped.hadMarker();
            }
        }

        if (!Lyric.isCarrier(extend) && text.isBlank()) {
            LOG.warn("Dropping blank lyric syllable for script {}", script);
            return null;
        }

        return new Lyric(script, text, extend, syllabic, compound);
    }
}
