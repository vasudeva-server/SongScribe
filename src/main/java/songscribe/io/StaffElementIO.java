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
package songscribe.io;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.dom.Accidental;
import songscribe.dom.AccidentalState;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.BeatUnit;
import songscribe.dom.Direction;
import songscribe.dom.Dots;
import songscribe.dom.Dottable;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.StaffElement;
import songscribe.dom.Stem;
import songscribe.dom.TempoChangeAttachment;
import songscribe.engraving.PitchOutOfRangeException;
import songscribe.engraving.StaffPosition;

public final class StaffElementIO {

    private static final Logger LOG = LoggerFactory.getLogger(StaffElementIO.class);

    // version 1.0
    private static final String XML_NOTE = "note";
    private static final String XML_TYPE = "type";
    private static final String XML_YPOS = "ypos"; // legacy, read for compat
    private static final String XML_STAFF_POSITION = "staffposition";
    private static final String XML_DOTTED = "dotted";
    private static final String XML_PREFIX = "prefix";
    private static final String XML_VOLUME = "volume";
    private static final String XML_GLISSANDO = "glissando";
    private static final String XML_FALL = "fall";

    // version 1.1
    private static final String XML_XPOS = "xpos";
    private static final String XML_PREFIX_IN_PARENTHESIS =
        "prefixinparenthesis";
    private static final String XML_UPPER = "upper";
    private static final String XML_FORCE_ARTICULATION = "forcearticulation";
    private static final String XML_DURATION_ARTICULATION =
        "durationarticulation";
    private static final String XML_SYLLABLE_MOVEMENT = "syllablemovement";
    private static final String XML_SYLLABLE_RELATION_MOVEMENT =
        "syllablerelationmovement";
    private static final String XML_TRILL = "trill";
    private static final String XML_FERMATA = "fermata";
    private static final String XML_FORCE_SYLLABLE = "forcesyllable";
    private static final String XML_BEAT_CHANGE = "beatchange";
    private static final String XML_BEAT_CHANGE_DURATION = "duration";
    private static final String XML_BEAT_CHANGE_BEAT = "beat";
    // Both translate tags are retained for legacy-read compatibility only —
    // recognized on read, never written.
    private static final String XML_GLISSANDO_X1_TRANSLATE =
        "glissandox1translate";
    private static final String XML_GLISSANDO_X2_TRANSLATE =
        "glissandox2translate";
    private static final String XML_STEM_DIRECTION_AUTO = "stemDirectionAuto";
    private static final String XML_INVERT_FRACTION_BEAM_ORIENTATION =
        "invertfractionbeamorientation";

    // version 2.3
    private static final String XML_DYNAMIC = "dynamic";

    // version 2.6
    private static final String XML_LYRIC = "lyric";
    private static final String XML_LYRIC_NUMBER = "number";
    private static final String XML_SYLLABIC = "syllabic";
    private static final String XML_LYRIC_TEXT = "text";
    private static final String XML_EXTEND_TAG = "extend";

    // Maps every legacy element-type spelling — the underscore-less aliases and the retired
    // names the v1.0/v1.1 formats used — to the canonical ElementType constant. Shared by startElement10 and
    // startElement11 via resolveElementType so an unknown token is rejected identically
    // on both.
    private static final Map<String, ElementType> LEGACY_ELEMENT_TYPE_NAMES = Map.ofEntries(
        Map.entry("SEMIBREVEREST", ElementType.SEMIBREVE_REST),
        Map.entry("MINIMREST", ElementType.MINIM_REST),
        Map.entry("CROTCHETREST", ElementType.CROTCHET_REST),
        Map.entry("QUAVERREST", ElementType.QUAVER_REST),
        Map.entry("SEMIQUAVERREST", ElementType.SEMIQUAVER_REST),
        Map.entry("DEMISEMIQUAVERREST", ElementType.DEMI_SEMIQUAVER_REST),
        Map.entry("GRACEQUAVER", ElementType.GRACE_QUAVER),
        Map.entry("REPEATLEFT", ElementType.REPEAT_LEFT),
        Map.entry("REPEATRIGHT", ElementType.REPEAT_RIGHT),
        Map.entry("REPEATLEFTRIGHT", ElementType.REPEAT_LEFT_RIGHT),
        Map.entry("BREATHMARK", ElementType.BREATH_MARK),
        Map.entry("SINGLEBARLINE", ElementType.SINGLE_BARLINE),
        Map.entry("DOUBLEBARLINE", ElementType.DOUBLE_BARLINE),
        Map.entry("FINALDOUBLEBARLINE", ElementType.FINAL_DOUBLE_BARLINE),
        Map.entry("LINE", ElementType.SINGLE_BARLINE),
        Map.entry("VERTICALLINE", ElementType.SINGLE_BARLINE),
        Map.entry("GRACE_SEMIQUAVER", ElementType.GRACE_QUAVER),
        Map.entry("GRACE_SEMIQUAVER_EDIT_STEP1", ElementType.GRACE_QUAVER),
        Map.entry("GRACESEMIQUAVER", ElementType.GRACE_QUAVER)
    );

    private static final Map<String, Accidental> ACCIDENTAL_MAP =
        new HashMap<>();

    // Every name in ACCIDENTAL_MAP that belongs to a retired accidental, so a reader
    // can tell a name it converted from one it read as-is. Holds both the canonical
    // legacy names and the underscore-less aliases synthesized from them.
    private static final Set<String> LEGACY_ACCIDENTAL_NAMES = new HashSet<>();

    static {
        for (var accidental : Accidental.values()) {
            putAccidentalNameWithAlias(accidental.name(), accidental, false);
        }

        // Retired accidentals (NATURAL_FLAT, NATURAL_SHARP, DOUBLE_NATURAL)
        // do not exist as enum constants, but old files still serialize
        // them by name. Route their canonical legacy names through the same
        // alias-synthesis loop so the underscore-less variants they were
        // ever written as (e.g. NATURALFLAT) still resolve.
        for (var entry : LegacyAccidentals.legacyNames().entrySet()) {
            putAccidentalNameWithAlias(entry.getKey(), entry.getValue(), true);
        }
    }

    private static void putAccidentalNameWithAlias(
        String accidentalName,
        Accidental accidental,
        boolean isLegacy
    ) {
        putAccidentalName(accidentalName, accidental, isLegacy);

        if (accidentalName.contains("_")) {
            putAccidentalName(accidentalName.replace("_", ""), accidental, isLegacy);
        }
    }

    private static void putAccidentalName(
        String accidentalName,
        Accidental accidental,
        boolean isLegacy
    ) {
        ACCIDENTAL_MAP.put(accidentalName, accidental);

        if (isLegacy) {
            LEGACY_ACCIDENTAL_NAMES.add(accidentalName);
        }
    }

    private StaffElementIO() {
    }

    // Resolves an element-type token to an ElementType, accepting both the canonical
    // enum names and the legacy tokens LEGACY_ELEMENT_TYPE_NAMES maps. Shared by the
    // v1.0 and v1.1 read paths so an unknown token is rejected identically on both.
    private static ElementType resolveElementType(String name) throws SAXException {
        var elementType = LEGACY_ELEMENT_TYPE_NAMES.get(name);

        if (elementType == null) {
            try {
                elementType = ElementType.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw DocumentValidation.corrupt(LOG, "Corrupt document: unknown element type: '{}'", name);
            }
        }

        return elementType;
    }

    /**
     * A null or unrecognized value (including the legacy self-closed {@code <extend/>}
     * written by pre-enum versions) defaults to {@link Lyric.Extend#START}.
     */
    static Lyric.Extend parseExtendType(@Nullable String typeAttr) {
        if (typeAttr == null) {
            return Lyric.Extend.START;
        }

        return switch (typeAttr) {
            case "stop" -> Lyric.Extend.STOP;
            case "continue" -> Lyric.Extend.CONTINUE;
            default -> Lyric.Extend.START;
        };
    }

    public static class StaffElementReader {

        @Nullable
        private StaffElement element = null;

        @Nullable
        private String lastTag;

        @org.jetbrains.annotations.Nullable
        private TempoIO.TempoReader tempoReader = null;

        @org.jetbrains.annotations.Nullable
        private AnnotationIO.AnnotationReader annotationReader = null;
        private final StringBuilder value = new StringBuilder(20);

        @Nullable
        private Where where;

        // True when the current element had a legacy <trill> tag.
        // LineIO.LineReader reads this after each note and coalesces runs into Trill spans.
        private boolean trillFlagged = false;

        // The legacy stem tags of the current element, combined when the element closes: the
        // <stemdirectionauto> tag marks a stem the notator forced, and <upper> says which way.
        // An <upper> on a stem the notator did not force is a stale cached direction and is
        // ignored, as the layout of the day would have overwritten it.
        private boolean stemForced = false;
        private boolean stemUp = false;

        // True once any element read by this reader named a retired accidental that was
        // converted to its replacement. Unlike trillFlagged this is never reset: it answers
        // "did anything in this file get converted", which the loader reports so a converted
        // song opens modified. Read out via sawLegacyAccidental().
        private boolean sawLegacyAccidental = false;

        // True when the current element lost a <lyric> whose number names no script.
        // LineIO.LineReader reads this after each note and records the element's line, so each
        // line that lost one is reported once.
        private boolean droppedLyric = false;

        // State for the lyric currently being parsed (valid while where == LYRIC)
        private int lyricNumber;
        private String lyricSyllabic = "";
        private String lyricText = "";
        private Lyric.Extend lyricExtend = Lyric.Extend.NONE;

        /**
         * Valid only after {@code endElement11} returns a non-null element.
         */
        public boolean isTrillFlagged() {
            return trillFlagged;
        }

        public boolean sawLegacyAccidental() {
            return sawLegacyAccidental;
        }

        /**
         * A lyric whose number names no script is dropped, not refused. Valid only after
         * {@code endElement11} returns a non-null element.
         */
        public boolean droppedLyric() {
            return droppedLyric;
        }

        public boolean startElement10(String qName, Attributes attributes) throws SAXException {
            if (qName.equals(XML_NOTE)) {
                lastTag = null;
                where = Where.ELEMENT;
                trillFlagged = false;
                droppedLyric = false;
                stemForced = false;
                stemUp = false;
                var type = attributes.getValue(XML_TYPE);

                if (type.equals("NEWLINE")) {
                    where = null;
                    return true;
                }

                element = resolveElementType(type).newInstance();
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
            return false;
        }

        public void startElement11(String qName, Attributes attributes) throws SAXException {
            if (where == Where.TEMPO_CHANGE) {
                if (tempoReader == null) {
                    return;
                }

                tempoReader.startElement11(qName);
            } else if (where == Where.ANNOTATION) {
                if (annotationReader == null) {
                    return;
                }

                annotationReader.startElement11(qName);
            } else if (qName.equals(XML_NOTE)) {
                lastTag = null;
                where = Where.ELEMENT;
                trillFlagged = false;
                droppedLyric = false;
                stemForced = false;
                stemUp = false;

                var type = attributes.getValue(XML_TYPE);

                try {
                    element = resolveElementType(type).newInstance();
                } catch (NullPointerException e) {
                    throw DocumentValidation.corrupt(LOG, "Corrupt document: unknown element type: '{}'", type);
                }
            } else if (qName.equals(TempoIO.XML_TEMPO)) {
                where = Where.TEMPO_CHANGE;
                tempoReader = new TempoIO.TempoReader();
                tempoReader.startElement11(qName);
            } else if (qName.equals(AnnotationIO.XML_ANNOTATION)) {
                where = Where.ANNOTATION;
                annotationReader = new AnnotationIO.AnnotationReader();
                annotationReader.startElement11(qName);
            } else if (where == Where.ELEMENT) {
                switch (qName) {
                    case XML_LYRIC -> {
                        where = Where.LYRIC;
                        var numberStr = attributes.getValue(XML_LYRIC_NUMBER);
                        lyricNumber = numberStr != null ? Integer.parseInt(numberStr) : 1;
                        lyricSyllabic = "";
                        lyricText = "";
                        lyricExtend = Lyric.Extend.NONE;
                        lastTag = null;
                    }
                    case XML_DYNAMIC -> {
                        if (element != null) {
                            var typeStr = attributes.getValue(XML_TYPE);

                            if (typeStr != null) {
                                try {
                                    var dynamicType = DynamicAttachment.DynamicType.valueOf(typeStr);
                                    DocumentValidation.attachUnique(LOG, element, new DynamicAttachment(dynamicType));
                                } catch (IllegalArgumentException e) {
                                    LOG.warn("Unknown dynamic type: {}, skipping", typeStr);
                                }
                            }
                        }
                    }
                    case XML_BEAT_CHANGE -> {
                        var duration = attributes.getValue(XML_BEAT_CHANGE_DURATION);
                        var beat = attributes.getValue(XML_BEAT_CHANGE_BEAT);

                        if (duration != null && beat != null && element != null) {
                            // New two-attribute format (v2.5+)
                            DocumentValidation.attachUnique(LOG, element, new BeatChangeAttachment(
                                new BeatChange(BeatUnit.valueOf(duration), BeatUnit.valueOf(beat))
                            ));
                        } else {
                            // Legacy text-content format — defer to endElement11
                            lastTag = qName;
                        }
                    }
                    default -> lastTag = qName;
                }
            } else if (where == Where.LYRIC) {
                if (qName.equals(XML_SYLLABIC)) {
                    lastTag = qName;
                } else if (qName.equals(XML_EXTEND_TAG)) {
                    lyricExtend = parseExtendType(attributes.getValue(XML_TYPE));
                } else {
                    lastTag = qName;
                }
            }

            value.delete(0, value.length());
        }

        @Nullable
        public StaffElement endElement10(String qName) throws SAXException {
            return endElement11(qName);
        }

        @SuppressWarnings("StatementWithEmptyBody")
        @Nullable
        public StaffElement endElement11(String qName) throws SAXException {
            if (where == Where.TEMPO_CHANGE) {
                if (tempoReader == null || element == null) {
                    return null;
                }

                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    DocumentValidation.attachUnique(LOG, element, new TempoChangeAttachment(t));
                    where = Where.ELEMENT;
                }
            } else if (where == Where.ANNOTATION) {
                if (annotationReader == null || element == null) {
                    return null;
                }

                var read = annotationReader.endElement11(qName);

                if (read != null) {
                    read.attachTo(element);
                    where = Where.ELEMENT;
                }
            } else if (where == Where.LYRIC) {
                if (element == null) {
                    return null;
                }

                if (qName.equals(XML_LYRIC)) {
                    // STOP/CONTINUE carriers have no syllabic/text.
                    var syllabic = getSyllabic();
                    var compound = false;

                    // A trailing compound-word marker is only meaningful on a
                    // syllable that continues into the next (BEGIN/MIDDLE).
                    if (Lyric.syllabicContinues(syllabic)) {
                        var stripped = Lyric.stripCompoundMarker(lyricText);
                        lyricText = stripped.text();
                        compound = stripped.hadMarker();
                    }

                    var script = LyricNumbers.toScript(lyricNumber);

                    if (script == null) {
                        droppedLyric = true;
                    } else if (!Lyric.isCarrier(lyricExtend) && lyricText.isBlank()) {
                        LOG.warn("Dropping blank lyric syllable for verse {}", lyricNumber);
                    } else {
                        element.putLyric(
                            new Lyric(script, lyricText, lyricExtend, syllabic, compound)
                        );
                    }

                    where = Where.ELEMENT;
                } else //noinspection PointlessNullCheck
                    if (lastTag != null && qName.equals(lastTag)) {
                    var str = value.toString();

                    if (lastTag.equals(XML_SYLLABIC)) {
                        lyricSyllabic = str;
                    } else if (lastTag.equals(XML_LYRIC_TEXT)) {
                        lyricText = str;
                    }
                }
            } else if (where == Where.ELEMENT) {
                if (element == null) {
                    return null;
                }
                if (qName.equals(XML_NOTE)) {
                    if (stemForced && element instanceof NoteElement note) {
                        note.setStem(new Stem.Forced(stemUp ? Direction.UP : Direction.DOWN));
                    }

                    return element;
                }
                //noinspection PointlessNullCheck
                if (lastTag != null && qName.equals(lastTag)) {
                    var str = value.toString();

                    if (lastTag.equals(XML_XPOS)) {
                        // The stored layout position of a v1.1/v1.2 file. An element carries no
                        // position; layout re-spaces every line after load.
                    } else if (lastTag.equals(XML_DOTTED)) {
                        // A legacy file may write dots on an element that takes none; they
                        // have nowhere to go.
                        if (element instanceof Dottable dottable) {
                            dottable.setDots(Dots.of(DocumentValidation.parseIntOrThrow(LOG, lastTag, str)));
                        }
                    } else if (element instanceof NoteElement note && applyNoteTag(note, lastTag, str)) {
                        // A tag only a note carries, written onto the note. A legacy file may
                        // write one on an element that is not a note; it has nowhere to go.
                    } else if (lastTag.equals(XML_GLISSANDO_X1_TRANSLATE)
                        || lastTag.equals(XML_GLISSANDO_X2_TRANSLATE)) {
                        // Legacy manual-adjustment offsets — not supported; ignored.
                    } else if (lastTag.equals(XML_UPPER)) {
                        stemUp = true;
                    } else if (lastTag.equals(XML_SYLLABLE_MOVEMENT)
                        || lastTag.equals(XML_SYLLABLE_RELATION_MOVEMENT)
                        || lastTag.equals(XML_FORCE_SYLLABLE)) {
                        // Obsolete per-element nudge fields — silently discarded.
                    } else if (lastTag.equals(XML_TRILL)) {
                        // Legacy per-element trill tag — record the flag; LineIO coalesces into Trill spans.
                        trillFlagged = true;
                    } else if (lastTag.equals(XML_FERMATA)) {
                        DocumentValidation.attachUnique(LOG, element, new FermataAttachment());
                    } else if (lastTag.equals(XML_STEM_DIRECTION_AUTO)) {
                        stemForced = true;
                    } else if (lastTag.equals(XML_INVERT_FRACTION_BEAM_ORIENTATION)) {
                        // Silently ignored — partial beam stub direction is now automatic.
                    } else if (lastTag.equals(XML_BEAT_CHANGE)) {
                        DocumentValidation.attachUnique(LOG, element, new BeatChangeAttachment(
                            BeatChange.fromLegacyName(str)));
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        private boolean applyNoteTag(NoteElement note, String tag, String str) throws SAXException {
            if (tag.equals(XML_STAFF_POSITION) || tag.equals(XML_YPOS)) {
                var staffPositionSp = DocumentValidation.parseIntOrThrow(LOG, tag, str);

                try {
                    note.setStaffPosition(new StaffPosition(staffPositionSp));
                } catch (PitchOutOfRangeException e) {
                    throw DocumentValidation.corrupt(LOG, "Corrupt document: {}", e.detail());
                }
            } else if (tag.equals(XML_PREFIX)) {
                var accidental = ACCIDENTAL_MAP.get(str);

                if (accidental == null) {
                    throw new IllegalArgumentException(
                        "Unknown accidental: " + str);
                }

                if (LEGACY_ACCIDENTAL_NAMES.contains(str)) {
                    sawLegacyAccidental = true;
                }

                note.choose(accidental);
            } else if (tag.equals(XML_PREFIX_IN_PARENTHESIS)) {
                // Read and ignored: parentheses are no longer tracked.
            } else if (tag.equals(XML_VOLUME) && str.equals("LOUDER")) { //old
                note.addArticulation(new Articulation(ArticulationType.ACCENT));
            } else if (tag.equals(XML_FORCE_ARTICULATION)) {
                note.addArticulation(new Articulation(ArticulationType.ACCENT));
            } else if (tag.equals(XML_DURATION_ARTICULATION)) {
                note.addArticulation(new Articulation(ArticulationType.STACCATO));
            } else if (tag.equals(XML_GLISSANDO)) {
                // Legacy fall glissandos (saved as "SLIDE_OUT") load as falls. Everything else —
                // "CONNECTED", an empty new-style <glissando/> tag, or a legacy
                // integer pitch — loads as a connecting glissando.
                note.setSlide("SLIDE_OUT".equals(str) ? Slide.FALL : Slide.GLISSANDO);
            } else if (tag.equals(XML_FALL)) {
                note.setSlide(Slide.FALL);
            } else {
                return false;
            }

            return true;
        }

        private Lyric.@Nullable Syllabic getSyllabic() {
            var isCarrier = lyricExtend == Lyric.Extend.STOP
                || lyricExtend == Lyric.Extend.CONTINUE;
            Lyric.@Nullable Syllabic syllabic;

            if (isCarrier) {
                syllabic = null;
            } else {
                syllabic = switch (lyricSyllabic) {
                    case "single" -> Lyric.Syllabic.SINGLE;
                    case "begin" -> Lyric.Syllabic.BEGIN;
                    case "middle" -> Lyric.Syllabic.MIDDLE;
                    case "end" -> Lyric.Syllabic.END;
                    // Absent <syllabic>: implies SINGLE; the post-load syllabic
                    // backfill normalizes further if needed.
                    default -> Lyric.Syllabic.SINGLE;
                };
            }
            return syllabic;
        }

        public void characters(char[] ch, int start, int length) {
            if (where == Where.TEMPO_CHANGE) {
                if (tempoReader != null) {
                    tempoReader.characters(ch, start, length);
                }
            } else if (where == Where.ANNOTATION) {
                if (annotationReader != null) {
                    annotationReader.characters(ch, start, length);
                }
            } else if (lastTag != null &&
                (where == Where.ELEMENT || where == Where.LYRIC)) {
                value.append(ch, start, length);
            }
        }

        private enum Where {
            ELEMENT,
            TEMPO_CHANGE,
            ANNOTATION,
            LYRIC,
        }
    }
}
