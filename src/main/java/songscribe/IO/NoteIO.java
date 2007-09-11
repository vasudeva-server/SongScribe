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

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import songscribe.music.BeatChange;
import songscribe.music.DurationArticulation;
import songscribe.music.ForceArticulation;
import songscribe.music.GraceSemiQuaver;
import songscribe.music.Note;
import songscribe.music.NoteType;

public final class NoteIO {

    // version 1.0
    private static final String XML_NOTE = "note";
    private static final String XML_TYPE = "type";
    private static final String XML_YPOS = "ypos";
    private static final String XML_DOTTED = "dotted";
    private static final String XML_PREFIX = "prefix";
    private static final String XML_VOLUME = "volume";
    private static final String XML_GLISSANDO = "glissando";

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
    private static final String XML_GLISSANDO_X1_TRANSLATE =
        "glissandox1translate";
    private static final String XML_GLISSANDO_X2_TRANSLATE =
        "glissandox2translate";
    private static final String XML_GRACE_SEMIQUAVER_Y0_POS = "y0pos";
    private static final String XML_GRACE_SEMIQUAVER_X2_DIFFPOS = "x2diffpos";
    private static final String XML_INVERT_FRACTION_BEAM_ORIENTATION =
        "invertfractionbeamorientation";
    private static final Map<String, Note.Accidental> NOTE_ACCIDENTAL_MAP =
        new HashMap<>();
    private static final Map<String, BeatChange> BEAT_CHANGE_MAP =
        new HashMap<>();

    static {
        for (var accidental : Note.Accidental.values()) {
            var accidentalName = accidental.name();
            NOTE_ACCIDENTAL_MAP.put(accidentalName, accidental);

            if (accidentalName.contains("_")) {
                NOTE_ACCIDENTAL_MAP.put(
                    accidentalName.replace("_", ""),
                    accidental
                );
            }
        }
        for (var beatChange : BeatChange.values()) {
            BEAT_CHANGE_MAP.put(beatChange.name(), beatChange);
        }
        // OLD NAMES
        BEAT_CHANGE_MAP.put(
            "QUAVEREQUALSQUAVER",
            BeatChange.QUAVER_EQUALS_QUAVER
        );
        BEAT_CHANGE_MAP.put(
            "DOTTEDCROCHETEQUALSMINIM",
            BeatChange.DOTTED_CROCHET_EQUALS_MINIM
        );
        BEAT_CHANGE_MAP.put(
            "MINIMEQUALSDOTTEDCROCHET",
            BeatChange.MINIM_EQUALS_DOTTED_CROCHET
        );
        BEAT_CHANGE_MAP.put(
            "CROTCHETQUALSDOTTEDCROCHET",
            BeatChange.CROTCHET_EQUALS_DOTTED_CROCHET
        );
        BEAT_CHANGE_MAP.put(
            "DOTTEDCROCHETQUALSCROCHET",
            BeatChange.DOTTED_CROCHET_EQUALS_CROCHET
        );
    }

    private NoteIO() {}

    public static void writeNote(Note note, PrintWriter writer) {
        writer.println(
            "          <" +
            XML_NOTE +
            ' ' +
            XML_TYPE +
            "=\"" +
            note.getNoteType().name() +
            "\">"
        );
        XML.setIndent(12);
        XML.writeValue(writer, XML_XPOS, Integer.toString(note.getXPos()));
        XML.writeValue(writer, XML_YPOS, Integer.toString(note.getYPos()));

        if (note.getDotCount() != 0) {
            XML.writeValue(
                writer,
                XML_DOTTED,
                Integer.toString(note.getDotCount())
            );
        }

        if (note.getAccidental() != Note.Accidental.NONE) {
            XML.writeValue(writer, XML_PREFIX, note.getAccidental().name());
        }

        if (note.isAccidentalInParentheses()) {
            XML.writeEmptyTag(writer, XML_PREFIX_IN_PARENTHESIS);
        }

        if (note.getForceArticulation() != null) {
            XML.writeValue(
                writer,
                XML_FORCE_ARTICULATION,
                note.getForceArticulation().name()
            );
        }

        if (note.getDurationArticulation() != null) {
            XML.writeValue(
                writer,
                XML_DURATION_ARTICULATION,
                note.getDurationArticulation().name()
            );
        }

        //noinspection ObjectEquality
        if (note.getGlissando() != Note.NO_GLISSANDO) {
            XML.writeValue(
                writer,
                XML_GLISSANDO,
                Integer.toString(note.getGlissando().pitch)
            );

            if (note.getGlissando().x1Translate != 0) {
                XML.writeValue(
                    writer,
                    XML_GLISSANDO_X1_TRANSLATE,
                    Integer.toString(note.getGlissando().x1Translate)
                );
            }

            if (note.getGlissando().x2Translate != 0) {
                XML.writeValue(
                    writer,
                    XML_GLISSANDO_X2_TRANSLATE,
                    Integer.toString(note.getGlissando().x2Translate)
                );
            }
        }

        if (note.isUpper()) {
            XML.writeEmptyTag(writer, XML_UPPER);
        }

        if (note.getSyllableMovement() != 0) {
            XML.writeValue(
                writer,
                XML_SYLLABLE_MOVEMENT,
                Integer.toString(note.getSyllableMovement())
            );
        }

        if (note.getSyllableRelationMovement() != 0) {
            XML.writeValue(
                writer,
                XML_SYLLABLE_RELATION_MOVEMENT,
                Integer.toString(note.getSyllableRelationMovement())
            );
        }

        if (note.getTempoChange() != null) {
            TempoIO.writeTempo(note.getTempoChange(), writer, 12);
        }

        if (note.getAnnotation() != null) {
            AnnotationIO.writeAnnotation(note.getAnnotation(), writer, 12);
        }

        if (note.isTrill()) {
            XML.writeEmptyTag(writer, XML_TRILL);
        }

        if (note.isFermata()) {
            XML.writeEmptyTag(writer, XML_FERMATA);
        }

        if (note.isForceSyllable()) {
            XML.writeEmptyTag(writer, XML_FORCE_SYLLABLE);
        }

        if (note.isInvertFractionBeamOrientation()) {
            XML.writeEmptyTag(writer, XML_INVERT_FRACTION_BEAM_ORIENTATION);
        }

        if (note.getBeatChange() != null) {
            XML.writeValue(
                writer,
                XML_BEAT_CHANGE,
                note.getBeatChange().name()
            );
        }

        if (note.getNoteType() == NoteType.GRACE_SEMIQUAVER) {
            XML.writeValue(
                writer,
                XML_GRACE_SEMIQUAVER_Y0_POS,
                Integer.toString(((GraceSemiQuaver) note).getY0Pos())
            );
            XML.writeValue(
                writer,
                XML_GRACE_SEMIQUAVER_X2_DIFFPOS,
                Integer.toString(((GraceSemiQuaver) note).getX2DiffPos())
            );
        }

        writer.println("          </" + XML_NOTE + '>');
    }

    public static class NoteReader {

        private Note note = null;

        @Nullable
        private String lastTag;

        private TempoIO.TempoReader tempoReader = null;
        private AnnotationIO.AnnotationReader annotationReader = null;
        private final StringBuilder value = new StringBuilder(20);

        @Nullable
        private Where where;

        public void startElement10(String qName, Attributes attributes)
            throws NewLineException {
            if (qName.equals(XML_NOTE)) {
                lastTag = null;
                where = Where.NOTE;
                var type = attributes.getValue(XML_TYPE);

                if (type.equals("NEWLINE")) {
                    where = null;
                    throw new NewLineException();
                }
                if (type.equals("LINE")) {
                    type = NoteType.SINGLE_BARLINE.name();
                }

                note = NoteType.valueOf(type).newInstance();
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        public void startElement11(String qName, Attributes attributes) {
            if (where == Where.TEMPO_CHANGE) {
                tempoReader.startElement11(qName);
            } else if (where == Where.ANNOTATION) {
                annotationReader.startElement11(qName);
            } else if (qName.equals(XML_NOTE)) {
                lastTag = null;
                where = Where.NOTE;

                if (attributes.getValue(XML_TYPE).equals("VERTICALLINE")) {
                    note = NoteType.SINGLE_BARLINE.newInstance();
                } else {
                    note = NoteType.valueOf(
                        attributes.getValue(XML_TYPE)
                    ).newInstance();
                }
            } else if (qName.equals(TempoIO.XML_TEMPO)) {
                where = Where.TEMPO_CHANGE;
                tempoReader = new TempoIO.TempoReader();
                tempoReader.startElement11(qName);
            } else if (qName.equals(AnnotationIO.XML_ANNOTATION)) {
                where = Where.ANNOTATION;
                annotationReader = new AnnotationIO.AnnotationReader();
                annotationReader.startElement11(qName);
            } else if (where == Where.NOTE) {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        @Nullable
        public Note endElement10(String qName) {
            return endElement11(qName);
        }

        @Nullable
        public Note endElement11(String qName) {
            if (where == Where.TEMPO_CHANGE) {
                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    note.setTempoChange(t);
                    where = Where.NOTE;
                }
            } else if (where == Where.ANNOTATION) {
                var a = annotationReader.endElement11(qName);

                if (a != null) {
                    note.setAnnotation(a);
                    where = Where.NOTE;
                }
            } else if (where == Where.NOTE) {
                if (qName.equals(XML_NOTE)) {
                    return note;
                }
                if (qName.equals(lastTag)) {
                    var str = value.toString();

                    if (lastTag.equals(XML_XPOS)) {
                        note.setXPos(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_YPOS)) {
                        note.setYPos(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_DOTTED)) {
                        note.setDotCount(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_PREFIX)) {
                        note.setAccidental(NOTE_ACCIDENTAL_MAP.get(str));
                    } else if (lastTag.equals(XML_PREFIX_IN_PARENTHESIS)) {
                        note.setAccidentalInParentheses(true);
                    } else if (
                        lastTag.equals(XML_VOLUME) && str.equals("LOUDER")
                    ) { //old
                        note.setForceArticulation(ForceArticulation.ACCENT);
                    } else if (lastTag.equals(XML_FORCE_ARTICULATION)) {
                        note.setForceArticulation(
                            ForceArticulation.valueOf(str)
                        );
                    } else if (lastTag.equals(XML_DURATION_ARTICULATION)) {
                        note.setDurationArticulation(
                            DurationArticulation.valueOf(str)
                        );
                    } else if (lastTag.equals(XML_GLISSANDO)) {
                        note.setGlissando(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_GLISSANDO_X1_TRANSLATE)) {
                        note.getGlissando().x1Translate = Integer.parseInt(str);
                    } else if (lastTag.equals(XML_GLISSANDO_X2_TRANSLATE)) {
                        note.getGlissando().x2Translate = Integer.parseInt(str);
                    } else if (lastTag.equals(XML_UPPER)) {
                        note.setUpper(true);
                    } else if (lastTag.equals(XML_SYLLABLE_MOVEMENT)) {
                        note.setSyllableMovement(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_SYLLABLE_RELATION_MOVEMENT)) {
                        note.setSyllableRelationMovement(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_TRILL)) {
                        note.setTrill(true);
                    } else if (lastTag.equals(XML_FERMATA)) {
                        note.setFermata(true);
                    } else if (lastTag.equals(XML_FORCE_SYLLABLE)) {
                        note.setForceSyllable(true);
                    } else if (
                        lastTag.equals(XML_INVERT_FRACTION_BEAM_ORIENTATION)
                    ) {
                        note.setInvertFractionBeamOrientation(true);
                    } else if (lastTag.equals(XML_BEAT_CHANGE)) {
                        note.setBeatChange(BEAT_CHANGE_MAP.get(str));
                    } else if (lastTag.equals(XML_GRACE_SEMIQUAVER_Y0_POS)) {
                        ((GraceSemiQuaver) note).setY0Pos(
                                Integer.parseInt(str)
                            );
                    } else if (
                        lastTag.equals(XML_GRACE_SEMIQUAVER_X2_DIFFPOS)
                    ) {
                        ((GraceSemiQuaver) note).setX2DiffPos(
                                Integer.parseInt(str)
                            );
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        public void characters(char[] ch, int start, int lenght) {
            if (where == Where.TEMPO_CHANGE) {
                tempoReader.characters(ch, start, lenght);
            } else if (where == Where.ANNOTATION) {
                annotationReader.characters(ch, start, lenght);
            } else if ((where == Where.NOTE) && (lastTag != null)) {
                value.append(ch, start, lenght);
            }
        }

        private enum Where {
            NOTE,
            TEMPO_CHANGE,
            ANNOTATION,
        }
    }
}
