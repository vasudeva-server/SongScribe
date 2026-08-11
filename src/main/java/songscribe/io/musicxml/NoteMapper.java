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

    private NoteMapper() {}

    /**
     * The result of mapping one {@code <note>}: the appended element plus the two
     * facts about the note that its own element cannot carry.
     *
     * @param element               the element appended to the line
     * @param usedLegacyAccidental  true when the {@code <accidental>} token was a
     *                              retired one converted to its replacement
     * @param hasBreathMark         true when a {@code BREATH_MARK} element must be
     *                              appended immediately after this note
     */
    record MappedNote(
        StaffElement element,
        boolean usedLegacyAccidental,
        boolean hasBreathMark
    ) {}

    /**
     * One note's {@code <tied>} markers. An interior note of a tie chain carries
     * both: it closes the pair before it and opens the pair after it.
     */
    record TieMarker(boolean start, boolean stop) {}

    /**
     * One note's {@code <tuplet>} bracket marker, together with the
     * {@code <time-modification>} the bracket's anchor states.
     *
     * <p>{@code verticalPositionSs} is already converted to staff spaces; an absent
     * {@code relative-y} reads as 0, the value the model treats as "unset".
     */
    record TupletMarker(
        boolean start,
        boolean stop,
        int verticalPositionSs,
        int actualNotes,
        int normalNotes,
        @Nullable String normalTypeToken,
        int normalDotCount
    ) {}

    /**
     * One note's {@code <wavy-line>} markers. A note carrying both bounds a
     * single-note trill. {@code yPositionSs} is already in staff spaces.
     */
    record TrillMarker(boolean start, boolean stop, int yPositionSs) {}

    /**
     * Maps {@code pmNote} to a {@link StaffElement}, appends it to {@code line},
     * and resolves its non-span attachments (articulations, fermata, dynamic,
     * fall) and its lyrics.
     *
     * @throws SAXException if the note is missing its {@code <type>} element, or
     *                      carries an unrecognised one
     */
    static MappedNote map(Note pmNote, Line line) throws SAXException {
        var typeToken = ProxyMusicAccess.requireNoteTypeToken(pmNote).trim();
        var isRest = ProxyMusicAccess.isRest(pmNote);
        var elementType = NoteTypeMapping.forTypeToken(typeToken, isRest, ProxyMusicAccess.isGrace(pmNote));

        if (elementType == null) {
            throw new MusicXmlReader.UnsupportedFormatException("Unrecognised <type> token: '" + typeToken + '\'');
        }

        var element = elementType.newInstance();

        // <step> + <octave> are authoritative for pitch (key-independent); <alter>
        // is ignored on read. Rests carry no pitch.
        var pitch = ProxyMusicAccess.pitch(pmNote);

        if (pitch != null) {
            element.setStaffPosition(PitchSpelling.staffPositionFor(pitch.step(), pitch.octave()));
        }

        element.setDotCount(pmNote.getDot().size());

        var usedLegacyAccidental = applyAccidental(pmNote, element);

        // A present <stem> is a manual override; its absence means auto direction.
        if (ProxyMusicAccess.hasStem(pmNote)) {
            element.setUpper(ProxyMusicAccess.stemIsUp(pmNote));
            element.setStemDirectionAuto(false);
        } else {
            element.setStemDirectionAuto(true);
        }

        var relativeXTenths = ProxyMusicAccess.decimal(pmNote.getRelativeX());

        if (relativeXTenths != null) {
            element.setXOffsetPx(
                ScaleContext.ssToRoundedPx(relativeXTenths / MusicXmlTags.TENTHS_PER_STAFF_SPACE)
            );
        }

        // Append before adding attachments so each attachment's parent line is wired.
        line.addElement(element);

        var notations = readNotations(pmNote);
        applyNotationAttachments(notations, element);
        element.lyrics.addAll(mapLyrics(pmNote));

        return new MappedNote(element, usedLegacyAccidental, notations.hasBreathMark());
    }

    // -------------------------------------------------------------------------
    // Note body
    // -------------------------------------------------------------------------

    /**
     * Applies the displayed accidental glyph, which comes from {@code <accidental>}
     * and never from {@code <alter>}. Returns true when the token was a retired
     * legacy one converted to its replacement — the caller ORs that into the
     * document-wide accidentals-converted flag.
     */
    private static boolean applyAccidental(Note pmNote, StaffElement element) {
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
            element.setAccidental(accidental);
            // setAccidentalInParentheses must follow setAccidental: it only takes
            // effect when an accidental is present.
            element.setAccidentalInParentheses(ProxyMusicAccess.accidentalIsParenthesized(pmNote));
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

    private static void applyNotationAttachments(NoteNotations notations, StaffElement element) {
        if (notations.hasAccent()) {
            element.addArticulation(new Articulation(element, ArticulationType.ACCENT));
        }

        if (notations.hasStaccato()) {
            element.addArticulation(new Articulation(element, ArticulationType.STACCATO));
        }

        if (notations.hasFermata()) {
            element.addAttachment(new FermataAttachment(element));
        }

        var dynamicType = notations.dynamicType();

        if (dynamicType != null) {
            element.addAttachment(new DynamicAttachment(element, dynamicType));
        }

        if (notations.hasFall()) {
            element.setFall();
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

    /** This note's {@code <tied>} markers. */
    static TieMarker tieMarker(Note pmNote) {
        var start = false;
        var stop = false;

        for (var child : notationChildren(pmNote)) {
            if (child instanceof Tied tied) {
                var type = tied.getType();
                start = start || type == TiedType.START;
                stop = stop || type == TiedType.STOP;
            }
        }

        return new TieMarker(start, stop);
    }

    /**
     * This note's {@code <tuplet>} bracket marker and the {@code <time-modification>}
     * beside it. The vertical offset is carried on the start bracket, which is where
     * the writer puts it.
     */
    static TupletMarker tupletMarker(Note pmNote) {
        var start = false;
        var stop = false;
        var verticalPositionSs = 0;

        for (var child : notationChildren(pmNote)) {
            if (child instanceof Tuplet tuplet) {
                var type = tuplet.getType();

                if (type == StartStop.START) {
                    start = true;
                    verticalPositionSs = ProxyMusicAccess.tenthsToSs(tuplet.getRelativeY());
                } else if (type == StartStop.STOP) {
                    stop = true;
                }
            }
        }

        var timeModification = pmNote.getTimeModification();

        if (timeModification == null) {
            return new TupletMarker(start, stop, verticalPositionSs, 0, 0, null, 0);
        }

        return new TupletMarker(
            start,
            stop,
            verticalPositionSs,
            ProxyMusicAccess.integer(timeModification.getActualNotes(), 0),
            ProxyMusicAccess.integer(timeModification.getNormalNotes(), 0),
            timeModification.getNormalType(),
            timeModification.getNormalDot().size()
        );
    }

    /**
     * This note's {@code <wavy-line>} markers, found inside its {@code <ornaments>}.
     * {@code <trill-mark>} is decorative; the wavy-line pair is what bounds the span.
     */
    static TrillMarker trillMarker(Note pmNote) {
        var start = false;
        var stop = false;
        var yPositionSs = 0;

        for (var child : notationChildren(pmNote)) {
            if (!(child instanceof Ornaments ornaments)) {
                continue;
            }

            for (var wavyLine : ornamentWavyLines(ornaments)) {
                var type = wavyLine.getType();

                if (type == StartStopContinue.START) {
                    start = true;
                    yPositionSs = ProxyMusicAccess.tenthsToSs(wavyLine.getRelativeY());
                } else if (type == StartStopContinue.STOP) {
                    stop = true;
                }
            }
        }

        return new TrillMarker(start, stop, yPositionSs);
    }

    // -------------------------------------------------------------------------
    // Lyrics
    // -------------------------------------------------------------------------

    /**
     * Maps this note's {@code <lyric>} children, one {@link Lyric} per verse.
     * Mirrors {@code StaffElementIO.endElement11}'s lyric-close branch: a carrier
     * ({@code <extend type="stop|continue"/>} with no text of its own) gets a null
     * syllabic and empty text; for a BEGIN/MIDDLE syllabic a trailing compound-word
     * marker is stripped and {@code compound} set, so the word boundary
     * round-trips. The {@link Lyric} constructor re-validates the
     * carrier/syllabic contract.
     */
    private static List<Lyric> mapLyrics(Note pmNote) {
        var lyrics = new ArrayList<Lyric>(pmNote.getLyric().size());

        for (var pmLyric : pmNote.getLyric()) {
            lyrics.add(mapLyric(pmLyric));
        }

        return lyrics;
    }

    private static Lyric mapLyric(org.audiveris.proxymusic.Lyric pmLyric) {
        var verse = ProxyMusicAccess.lyricVerse(pmLyric);
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

        return new Lyric(verse, text, extend, syllabic, compound);
    }
}
