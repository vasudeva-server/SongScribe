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

import org.jetbrains.annotations.Nullable;

import org.xml.sax.Attributes;

import songscribe.data.IntervalSet;
import songscribe.music.KeyType;
import songscribe.music.Line;

public final class LineIO {

    private static final String XML_LINE = "line";
    private static final String XML_KEYS = "keys";
    private static final String XML_KEYTYPE = "keytype";
    private static final String XML_NOTE_DIST_CHANGE = "notedistchange";
    private static final String XML_LYRICS_YPOS = "lyricsypos";
    private static final String XML_FSENDING_YPOS = "fsendingypos";
    private static final String XML_TEMPO_CHANGE_YPOS = "tempochangeypos";
    private static final String XML_BEAT_CHANGE_YPOS = "beatchangeypos";
    private static final String XML_TRILL_YPOS = "trillypos";
    private static final String XML_BEAMINGS = "beamings";
    private static final String XML_TIES = "ties";
    private static final String XML_TRIPLETS = "triplets"; // the old version of triplets
    private static final String XML_TUPLETS = "tuplets";
    private static final String XML_FSENDINGS = "fsendings";
    private static final String XML_NOTES = "notes";
    private static final String XML_CRESCENDO = "crescendo";
    private static final String XML_DIMINUENDO = "diminuendo";

    private LineIO() {}

    public static void writeLine(Line l, PrintWriter pw) {
        pw.println("    <" + XML_LINE + '>');
        XML.setIndent(6);

        if (
            (l.getKeyAccidentalCount() !=
                l.getComposition().getDefaultKeyAccidentalCount()) ||
            (l.getKeyType() != l.getComposition().getDefaultKeyType())
        ) {
            XML.writeValue(
                pw,
                XML_KEYS,
                Integer.toString(l.getKeyAccidentalCount())
            );
            XML.writeValue(pw, XML_KEYTYPE, l.getKeyType().name());
        }

        if (l.getNoteDistChangeRatio() != 1f) {
            XML.writeValue(
                pw,
                XML_NOTE_DIST_CHANGE,
                Float.toString(l.getNoteDistChangeRatio())
            );
        }

        if (l.getFirstTempoChange() > -1) {
            XML.writeValue(
                pw,
                XML_TEMPO_CHANGE_YPOS,
                Integer.toString(l.getTempoChangeYPos())
            );
        }

        if (l.getFirstBeatChange() > -1) {
            XML.writeValue(
                pw,
                XML_BEAT_CHANGE_YPOS,
                Integer.toString(l.getBeatChangeYPos())
            );
        }

        XML.writeValue(
            pw,
            XML_LYRICS_YPOS,
            Integer.toString(l.getLyricsYPos())
        );

        if (!l.getFirstSecondEndings().isEmpty()) {
            XML.writeValue(
                pw,
                XML_FSENDING_YPOS,
                Integer.toString(l.getFirstSecondEndingYPos())
            );
        }

        if (l.getFirstTrill() > -1) {
            XML.writeValue(
                pw,
                XML_TRILL_YPOS,
                Integer.toString(l.getTrillYPos())
            );
        }

        if (!l.getBeamings().isEmpty()) {
            XML.writeValue(pw, XML_BEAMINGS, intervalToString(l.getBeamings()));
        }

        if (!l.getTies().isEmpty()) {
            XML.writeValue(pw, XML_TIES, intervalToString(l.getTies()));
        }

        if (!l.getTuplets().isEmpty()) {
            XML.writeValue(pw, XML_TUPLETS, intervalToString(l.getTuplets()));
        }

        if (!l.getFirstSecondEndings().isEmpty()) {
            XML.writeValue(
                pw,
                XML_FSENDINGS,
                intervalToString(l.getFirstSecondEndings())
            );
        }

        if (!l.getCrescendos().isEmpty()) {
            XML.writeValue(
                pw,
                XML_CRESCENDO,
                intervalToString(l.getCrescendos())
            );
        }

        if (!l.getDiminuendos().isEmpty()) {
            XML.writeValue(
                pw,
                XML_DIMINUENDO,
                intervalToString(l.getDiminuendos())
            );
        }

        pw.println("      <" + XML_NOTES + '>');

        for (var i = 0; i < l.noteCount(); i++) {
            NoteIO.writeNote(l.getNote(i), pw);
        }

        pw.println("      </" + XML_NOTES + '>');
        pw.println("    </" + XML_LINE + '>');
    }

    private static String intervalToString(IntervalSet is) {
        var sb = new StringBuilder(27);

        for (var li = is.listIterator(); li.hasNext();) {
            var i = li.next();
            sb.append(i.getStart());
            sb.append(',');
            sb.append(i.getEnd());

            if (i.getData() != null) {
                sb.append(',');
                sb.append(i.getData());
            }

            sb.append(';');
        }

        return sb.toString();
    }

    public static class LineReader {

        private Line line = null;

        @Nullable
        private String lastTag;

        private NoteIO.NoteReader noteReader = null;
        private final StringBuilder value = new StringBuilder(20);

        @Nullable
        private Where where = null;

        private static void stringToIntervalSet(IntervalSet is, String str) {
            var begin = 0;
            var end = str.indexOf(';', begin);

            while (end != -1) {
                var firstComma = str.indexOf(',', begin);
                var secondComma = str.indexOf(',', firstComma + 1);

                if (secondComma > end) {
                    secondComma = -1;
                }

                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(
                    str.substring(
                        firstComma + 1,
                        (secondComma == -1) ? end : secondComma
                    )
                );
                var data = (secondComma == -1)
                    ? null
                    : str.substring(secondComma + 1, end);
                is.addInterval(a, b, data);
                begin = str.indexOf(';', begin) + 1;
                end = str.indexOf(';', begin);
            }
        }

        public void startElement11(String qName, Attributes attributes) {
            if (where == null) {
                if (qName.equals(XML_LINE)) {
                    where = Where.LINE;
                    line = new Line();
                    lastTag = null;
                    noteReader = new NoteIO.NoteReader();
                }
            } else if (where == Where.NOTES) {
                noteReader.startElement11(qName, attributes);
            } else {
                if (qName.equals(XML_NOTES)) {
                    where = Where.NOTES;
                } else {
                    lastTag = qName;
                }
            }

            value.delete(0, value.length());
        }

        @Nullable
        public Line endElement11(String qName) {
            if (qName.equals(XML_NOTES)) {
                where = Where.LINE;
            } else if (where == Where.NOTES) {
                var n = noteReader.endElement11(qName);

                if (n != null) {
                    line.addNote(n);
                }
            } else if (where == Where.LINE) {
                if (qName.equals(XML_LINE)) {
                    where = null;
                    return line;
                }
                if (qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> line.setKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> line.setKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NOTE_DIST_CHANGE -> line.mulNoteDistChange(
                            Float.parseFloat(str)
                        );
                        case XML_TEMPO_CHANGE_YPOS -> line.setTempoChangeYPos(
                            Integer.parseInt(str)
                        );
                        case XML_BEAT_CHANGE_YPOS -> line.setBeatChangeYPos(
                            Integer.parseInt(str)
                        );
                        case XML_LYRICS_YPOS -> line.setLyricsYPos(
                            Integer.parseInt(str)
                        );
                        case XML_FSENDING_YPOS -> line.setFirstSecondEndingYPos(
                            Integer.parseInt(str)
                        );
                        case XML_TRILL_YPOS -> line.setTrillYPos(
                            Integer.parseInt(str)
                        );
                        case XML_BEAMINGS -> stringToIntervalSet(
                            line.getBeamings(),
                            str
                        );
                        case XML_TIES -> stringToIntervalSet(
                            line.getTies(),
                            str
                        );
                        // Slurs no longer supported - ignore for backwards compatibility
                        case "slurs" -> {}
                        case XML_CRESCENDO -> stringToIntervalSet(
                            line.getCrescendos(),
                            str
                        );
                        case XML_DIMINUENDO -> stringToIntervalSet(
                            line.getDiminuendos(),
                            str
                        );
                        case XML_TUPLETS, XML_TRIPLETS -> {
                            stringToIntervalSet(line.getTuplets(), str);

                            for (
                                var li = line.getTuplets().listIterator();
                                li.hasNext();
                            ) {
                                var interval = li.next();

                                if (interval.getData() == null) {
                                    interval.setData("3");
                                }
                            }
                        }
                        case XML_FSENDINGS -> stringToIntervalSet(
                            line.getFirstSecondEndings(),
                            str
                        );
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        public void characters(char[] ch, int start, int lenght) {
            if (where == Where.NOTES) {
                noteReader.characters(ch, start, lenght);
            } else if (lastTag != null) {
                value.append(ch, start, lenght);
            }
        }

        private enum Where {
            LINE,
            NOTES,
        }
    }
}
