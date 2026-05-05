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

import org.jspecify.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;

import songscribe.music.ArticulationType;
import songscribe.music.BeatChange;
import songscribe.music.Duration;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.StaffElement;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.DynamicAttachment;

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

    /**
     * Compound-word boundary marker: a zero-width space (U+200B) appended to a
     * syllable's {@code <text>} content signals that the syllable joins the next
     * via SongScribe's {@code =} operator. Standard MusicXML readers ignore the
     * invisible character and render the text normally.
     */
    private static final String COMPOUND_WORD_MARKER = "​";

    private static final Map<String, StaffElement.Accidental> ACCIDENTAL_MAP =
        new HashMap<>();

    static {
        for (var accidental : StaffElement.Accidental.values()) {
            var accidentalName = accidental.name();
            ACCIDENTAL_MAP.put(accidentalName, accidental);

            if (accidentalName.contains("_")) {
                ACCIDENTAL_MAP.put(
                    accidentalName.replace("_", ""),
                    accidental
                );
            }
        }
    }

    private StaffElementIO() {
    }

    /**
     * Parses a MusicXML-style {@code type} attribute on an {@code <extend>} element.
     * A null or unrecognized value (including the legacy self-closed {@code <extend/>}
     * written by pre-enum versions) defaults to {@link Lyric.Extend#START}.
     */
    private static Lyric.Extend parseExtendType(@Nullable String typeAttr) {
        if (typeAttr == null) {
            return Lyric.Extend.START;
        }

        return switch (typeAttr) {
            case "stop" -> Lyric.Extend.STOP;
            case "continue" -> Lyric.Extend.CONTINUE;
            default -> Lyric.Extend.START;
        };
    }

    /** Returns the MusicXML {@code type} attribute string for the given extend state. */
    private static String extendTypeAttr(Lyric.Extend extend) {
        return switch (extend) {
            case START -> "start";
            case STOP -> "stop";
            case CONTINUE -> "continue";
            case NONE -> throw new IllegalArgumentException("NONE has no type attribute");
        };
    }

    public static void writeElement(
        StaffElement element,
        PrintWriter writer,
        Line line,
        int elementIndex
    ) {
        writer.println(
            "          <" +
                XML_NOTE +
                ' ' +
                XML_TYPE +
                "=\"" +
                element.getType().name() +
                "\">"
        );
        XML.setIndent(12);

        if (element.getXOffsetPx() != 0) {
            XML.writeValue(writer, XML_XPOS, Integer.toString(element.getXOffsetPx()));
        }

        XML.writeValue(writer, XML_STAFF_POSITION, Integer.toString(element.getStaffPosition()));

        if (element.getDotCount() != 0) {
            XML.writeValue(
                writer,
                XML_DOTTED,
                Integer.toString(element.getDotCount())
            );
        }

        if (element.getAccidental() != null) {
            XML.writeValue(writer, XML_PREFIX, element.getAccidental().name());
        }

        if (element.isAccidentalInParentheses()) {
            XML.writeEmptyTag(writer, XML_PREFIX_IN_PARENTHESIS);
        }

        for (var articulation : element.getArticulations()) {
            switch (articulation.getType()) {
                case ACCENT -> XML.writeValue(
                    writer,
                    XML_FORCE_ARTICULATION,
                    ArticulationType.ACCENT.name()
                );
                case STACCATO -> XML.writeValue(
                    writer,
                    XML_DURATION_ARTICULATION,
                    ArticulationType.STACCATO.name()
                );
            }
        }

        if (element.getGlissando() != null) {
            XML.writeValue(
                writer,
                XML_GLISSANDO,
                element.getGlissando().type.name()
            );

            if (element.getGlissando().x1Translate != 0) {
                XML.writeValue(
                    writer,
                    XML_GLISSANDO_X1_TRANSLATE,
                    Double.toString(element.getGlissando().x1Translate)
                );
            }

            if (element.getGlissando().x2Translate != 0) {
                XML.writeValue(
                    writer,
                    XML_GLISSANDO_X2_TRANSLATE,
                    Double.toString(element.getGlissando().x2Translate)
                );
            }
        }

        if (!element.isStemDirectionAuto()) {
            XML.writeEmptyTag(writer, XML_STEM_DIRECTION_AUTO);
        }

        if (element.isUpper()) {
            XML.writeEmptyTag(writer, XML_UPPER);
        }

        if (element.getTempoChange() != null) {
            TempoIO.writeTempo(element.getTempoChange(), writer, 12);
        }

        if (element.getAnnotation() != null) {
            AnnotationIO.writeAnnotation(element.getAnnotation(), writer, 12);
        }

        if (element.isTrill()) {
            XML.writeEmptyTag(writer, XML_TRILL);
        }

        if (element.isFermata()) {
            XML.writeEmptyTag(writer, XML_FERMATA);
        }

        var dynamic = element.findAttachment(DynamicAttachment.class);

        if (dynamic != null) {
            writer.println(
                "            <" + XML_DYNAMIC + ' ' + XML_TYPE + "=\"" +
                    dynamic.getType().name() + "\" />"
            );
        }

        var beatChange = element.getBeatChange();

        if (beatChange != null) {
            XML.writeEmptyTag(
                writer,
                XML_BEAT_CHANGE + " " + XML_BEAT_CHANGE_DURATION + "=\"" +
                    beatChange.duration().name() + "\" " +
                    XML_BEAT_CHANGE_BEAT + "=\"" + beatChange.beat().name() + "\""
            );
        }


        for (var lyric : element.properties.lyrics) {
            writer.println("            <" + XML_LYRIC + " " + XML_LYRIC_NUMBER + "=\"" + lyric.verse() + "\">");

            // STOP/CONTINUE carriers have no text and only emit the extender marker.
            if (lyric.extend() == Lyric.Extend.STOP || lyric.extend() == Lyric.Extend.CONTINUE) {
                writer.println(
                    "              <" + XML_EXTEND_TAG + " " + XML_TYPE + "=\"" +
                        extendTypeAttr(lyric.extend()) + "\"/>"
                );
                writer.println("            </" + XML_LYRIC + ">");
                continue;
            }

            var syllabicValue = switch (lyric.syllabic()) {
                case SINGLE -> "single";
                case BEGIN -> "begin";
                case MIDDLE -> "middle";
                case END -> "end";
                case null -> "single";
            };

            writer.println("              <" + XML_SYLLABIC + ">" + syllabicValue + "</" + XML_SYLLABIC + ">");

            var lyricText = lyric.compound() ? lyric.text() + COMPOUND_WORD_MARKER : lyric.text();
            writer.println(
                "              <" + XML_LYRIC_TEXT + ">" +
                    XML.escapeXML(lyricText) + "</" + XML_LYRIC_TEXT + ">"
            );

            if (lyric.extend() == Lyric.Extend.START) {
                writer.println(
                    "              <" + XML_EXTEND_TAG + " " + XML_TYPE + "=\"start\"/>"
                );
            }

            writer.println("            </" + XML_LYRIC + ">");
        }

        writer.println("          </" + XML_NOTE + '>');
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

        // State for the lyric currently being parsed (valid while where == LYRIC)
        private int lyricNumber;
        private String lyricSyllabic = "";
        private String lyricText = "";
        private Lyric.Extend lyricExtend = Lyric.Extend.NONE;

        public boolean startElement10(String qName, Attributes attributes) {
            if (qName.equals(XML_NOTE)) {
                lastTag = null;
                where = Where.ELEMENT;
                var type = attributes.getValue(XML_TYPE);

                if (type.equals("NEWLINE")) {
                    where = null;
                    return true;
                }
                if (type.equals("LINE")) {
                    type = ElementType.SINGLE_BARLINE.name();
                }
                if (type.equals("GRACE_SEMIQUAVER") ||
                    type.equals("GRACE_SEMIQUAVER_EDIT_STEP1") ||
                    type.equals("GRACESEMIQUAVER")) {
                    type = ElementType.GRACE_QUAVER.name();
                }

                element = ElementType.valueOf(type).newInstance();
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
            return false;
        }

        public void startElement11(String qName, Attributes attributes) {
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

                var type = attributes.getValue(XML_TYPE);

                if (type.equals("VERTICALLINE")) {
                    element = ElementType.SINGLE_BARLINE.newInstance();
                } else {
                    if (type.equals("GRACE_SEMIQUAVER") ||
                        type.equals("GRACE_SEMIQUAVER_EDIT_STEP1")) {
                        type = ElementType.GRACE_QUAVER.name();
                    }

                    element = ElementType.valueOf(type).newInstance();
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
                if (qName.equals(XML_LYRIC)) {
                    where = Where.LYRIC;
                    var numberStr = attributes.getValue(XML_LYRIC_NUMBER);
                    lyricNumber = numberStr != null ? Integer.parseInt(numberStr) : 1;
                    lyricSyllabic = "";
                    lyricText = "";
                    lyricExtend = Lyric.Extend.NONE;
                    lastTag = null;
                } else if (qName.equals(XML_DYNAMIC)) {
                    if (element != null) {
                        var typeStr = attributes.getValue(XML_TYPE);

                        if (typeStr != null) {
                            try {
                                var dynamicType = DynamicAttachment.DynamicType.valueOf(typeStr);
                                element.addAttachment(new DynamicAttachment(element, dynamicType));
                            } catch (IllegalArgumentException e) {
                                LOG.warn("Unknown dynamic type: {}, skipping", typeStr);
                            }
                        }
                    }
                } else if (qName.equals(XML_BEAT_CHANGE)) {
                    var duration = attributes.getValue(XML_BEAT_CHANGE_DURATION);
                    var beat = attributes.getValue(XML_BEAT_CHANGE_BEAT);

                    if (duration != null && beat != null && element != null) {
                        // New two-attribute format (v2.5+)
                        element.setBeatChange(
                            new BeatChange(Duration.valueOf(duration), Duration.valueOf(beat))
                        );
                    } else {
                        // Legacy text-content format — defer to endElement11
                        lastTag = qName;
                    }
                } else {
                    lastTag = qName;
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
        public StaffElement endElement10(String qName) {
            return endElement11(qName);
        }

        @Nullable
        public StaffElement endElement11(String qName) {
            if (where == Where.TEMPO_CHANGE) {
                if (tempoReader == null || element == null) {
                    return null;
                }

                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    element.setTempoChange(t);
                    where = Where.ELEMENT;
                }
            } else if (where == Where.ANNOTATION) {
                if (annotationReader == null || element == null) {
                    return null;
                }

                var a = annotationReader.endElement11(qName);

                if (a != null) {
                    element.setAnnotation(a);
                    where = Where.ELEMENT;
                }
            } else if (where == Where.LYRIC) {
                if (element == null) {
                    return null;
                }

                if (qName.equals(XML_LYRIC)) {
                    // STOP/CONTINUE carriers have no syllabic/text.
                    var syllabic = getSyllabic();
                    var isCompoundEligible = syllabic == Lyric.Syllabic.BEGIN
                        || syllabic == Lyric.Syllabic.MIDDLE;
                    var compound = isCompoundEligible && lyricText.endsWith(COMPOUND_WORD_MARKER);

                    if (compound) {
                        lyricText = lyricText.substring(
                            0, lyricText.length() - COMPOUND_WORD_MARKER.length());
                    }
                    element.properties.lyrics.add(
                        new Lyric(lyricNumber, lyricText, lyricExtend, syllabic, compound)
                    );
                    where = Where.ELEMENT;
                } else if (lastTag != null && qName.equals(lastTag)) {
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
                    return element;
                }
                if (lastTag != null && qName.equals(lastTag)) {
                    var str = value.toString();

                    if (lastTag.equals(XML_XPOS)) {
                        element.setXOffsetPx(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_STAFF_POSITION) ||
                        lastTag.equals(XML_YPOS)) {
                        element.setStaffPosition(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_DOTTED)) {
                        element.setDotCount(Integer.parseInt(str));
                    } else if (lastTag.equals(XML_PREFIX)) {
                        var accidental = ACCIDENTAL_MAP.get(str);

                        if (accidental == null) {
                            throw new IllegalArgumentException(
                                "Unknown accidental: " + str);
                        }

                        element.setAccidental(accidental);
                    } else if (lastTag.equals(XML_PREFIX_IN_PARENTHESIS)) {
                        element.setAccidentalInParentheses(true);
                    } else if (
                        lastTag.equals(XML_VOLUME) && str.equals("LOUDER")
                    ) { //old
                        element.addArticulation(
                            new Articulation(element, ArticulationType.ACCENT)
                        );
                    } else if (lastTag.equals(XML_FORCE_ARTICULATION)) {
                        element.addArticulation(
                            new Articulation(element, ArticulationType.ACCENT)
                        );
                    } else if (lastTag.equals(XML_DURATION_ARTICULATION)) {
                        element.addArticulation(
                            new Articulation(element, ArticulationType.STACCATO)
                        );
                    } else if (lastTag.equals(XML_GLISSANDO)) {
                        // Legacy files stored an integer pitch; treat those as CONNECTED.
                        StaffElement.Glissando.Type type;

                        try {
                            type = StaffElement.Glissando.Type.valueOf(str);
                        } catch (IllegalArgumentException e) {
                            type = StaffElement.Glissando.Type.CONNECTED;
                        }

                        element.setGlissando(type);
                    } else if (lastTag.equals(XML_GLISSANDO_X1_TRANSLATE)) {
                        var glissando = element.getGlissando();

                        if (glissando != null) {
                            glissando.x1Translate = Double.parseDouble(str);
                        }
                    } else if (lastTag.equals(XML_GLISSANDO_X2_TRANSLATE)) {
                        var glissando = element.getGlissando();

                        if (glissando != null) {
                            glissando.x2Translate = Double.parseDouble(str);
                        }
                    } else if (lastTag.equals(XML_UPPER)) {
                        element.setUpper(true);
                    } else if (lastTag.equals(XML_SYLLABLE_MOVEMENT)
                        || lastTag.equals(XML_SYLLABLE_RELATION_MOVEMENT)
                        || lastTag.equals(XML_FORCE_SYLLABLE)) {
                        // Obsolete per-element nudge fields — silently discarded.
                    } else if (lastTag.equals(XML_TRILL)) {
                        element.setTrill(true);
                    } else if (lastTag.equals(XML_FERMATA)) {
                        element.setFermata(true);
                    } else if (lastTag.equals(XML_STEM_DIRECTION_AUTO)) {
                        element.setStemDirectionAuto(false);
                    } else if (
                        lastTag.equals(XML_INVERT_FRACTION_BEAM_ORIENTATION)
                    ) {
                        // Silently ignored — partial beam stub direction is now automatic.
                    } else if (lastTag.equals(XML_BEAT_CHANGE)) {
                        element.setBeatChange(BeatChange.fromLegacyName(str));
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
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

        public void characters(char[] ch, int start, int lenght) {
            if (where == Where.TEMPO_CHANGE) {
                if (tempoReader != null) {
                    tempoReader.characters(ch, start, lenght);
                }
            } else if (where == Where.ANNOTATION) {
                if (annotationReader != null) {
                    annotationReader.characters(ch, start, lenght);
                }
            } else if (lastTag != null &&
                (where == Where.ELEMENT || where == Where.LYRIC)) {
                value.append(ch, start, lenght);
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
