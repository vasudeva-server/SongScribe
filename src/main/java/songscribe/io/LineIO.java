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
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.xml.sax.Attributes;

import songscribe.music.BeamSpan;
import songscribe.music.DynamicsSpan;
import songscribe.music.Span;
import songscribe.music.SpanSet;
import songscribe.music.TieSpan;
import songscribe.music.TupletSpan;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.layout.Ending;

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

    private LineIO() {
    }

    public static void writeLine(Line l, PrintWriter pw) {
        pw.println("    <" + XML_LINE + '>');
        XML.setIndent(6);

        var song = l.getSong();
        var lineKeyType = l.getKeyType();

        if (
            (l.getKeyAccidentalCount() != song.getDefaultKeyAccidentalCount()) ||
                (lineKeyType != song.getDefaultKeyType())
        ) {
            XML.writeValue(
                pw,
                XML_KEYS,
                Integer.toString(l.getKeyAccidentalCount())
            );

            if (lineKeyType != null) {
                XML.writeValue(pw, XML_KEYTYPE, lineKeyType.name());
            }
        }

        if (l.getElementSpacingRatio() != 1f) {
            XML.writeValue(
                pw,
                XML_NOTE_DIST_CHANGE,
                Float.toString(l.getElementSpacingRatio())
            );
        }

        // Note: Line-level Y position fields (tempoChangeYPos, beatChangeYPos,
        // firstSecondEndingYPos, trillYPos) are no longer written to new documents.
        // Per-instance offsets on element objects are used instead.
        // These fields are still read by LineReader for backward compatibility.

        // Lyrics Y position is still written (not yet migrated to per-instance)
        XML.writeValue(
            pw,
            XML_LYRICS_YPOS,
            Double.toString(l.getLyricsYPosSs())
        );

        if (!l.getBeamings().isEmpty()) {
            XML.writeValue(pw, XML_BEAMINGS, spanSetToString(l.getBeamings()));
        }

        if (!l.getTies().isEmpty()) {
            XML.writeValue(pw, XML_TIES, spanSetToString(l.getTies()));
        }

        if (!l.getTuplets().isEmpty()) {
            XML.writeValue(pw, XML_TUPLETS, tupletSpanSetToString(l.getTuplets()));
        }

        var endings = l.findEndings();

        if (!endings.isEmpty()) {
            XML.writeValue(
                pw,
                XML_FSENDINGS,
                endingsToString(endings)
            );
        }

        if (!l.getCrescendos().isEmpty()) {
            XML.writeValue(
                pw,
                XML_CRESCENDO,
                dynamicsSpanSetToString(l.getCrescendos())
            );
        }

        if (!l.getDiminuendos().isEmpty()) {
            XML.writeValue(
                pw,
                XML_DIMINUENDO,
                dynamicsSpanSetToString(l.getDiminuendos())
            );
        }

        pw.println("      <" + XML_NOTES + '>');

        for (var i = 0; i < l.elementCount(); i++) {
            StaffElementIO.writeElement(l.getElement(i), pw, l, i);
        }

        pw.println("      </" + XML_NOTES + '>');
        pw.println("    </" + XML_LINE + '>');
    }

    private static String endingsToString(List<Ending> endings) {
        var sb = new StringBuilder(27);

        for (var ending : endings) {
            sb.append(ending.getAnchorElementIndex());
            sb.append(',');
            sb.append(ending.getEndElementIndex());
            sb.append(';');
        }

        return sb.toString();
    }

    private static String spanSetToString(SpanSet<? extends Span> is) {
        var sb = new StringBuilder(27);

        for (var li = is.listIterator(); li.hasNext(); ) {
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

    private static String tupletSpanSetToString(SpanSet<TupletSpan> is) {
        var sb = new StringBuilder(27);

        for (var li = is.listIterator(); li.hasNext(); ) {
            var i = li.next();
            sb.append(i.getStart());
            sb.append(',');
            sb.append(i.getEnd());
            sb.append(',');
            sb.append(i.getGrade());

            if (i.isVerticallyAdjusted()) {
                sb.append(',');
                sb.append(i.getVerticalPositionSs());
            }

            sb.append(';');
        }

        return sb.toString();
    }

    private static String dynamicsSpanSetToString(SpanSet<DynamicsSpan> is) {
        var sb = new StringBuilder(27);

        for (var li = is.listIterator(); li.hasNext(); ) {
            var i = li.next();
            sb.append(i.getStart());
            sb.append(',');
            sb.append(i.getEnd());

            if (i.getX1ShiftSs() != 0 || i.getX2ShiftSs() != 0 || i.getYShiftSs() != 0) {
                sb.append(',');
                sb.append(i.getX1ShiftSs());
                sb.append(',');
                sb.append(i.getX2ShiftSs());
                sb.append(',');
                sb.append(i.getYShiftSs());
            }

            sb.append(';');
        }

        return sb.toString();
    }

    public static class LineReader {

        @Nullable
        private Line line = null;

        @Nullable
        private String lastTag;

        @org.jetbrains.annotations.Nullable
        private StaffElementIO.StaffElementReader noteReader = null;
        private final StringBuilder value = new StringBuilder(20);

        @Nullable
        private Where where = null;

        /** Temporarily holds parsed fsendings index pairs (start, end) until elements are loaded. */
        private final List<int[]> pendingEndingPairs = new ArrayList<>();

        private static void stringToBeamingSpanSet(SpanSet<Span> is, String str) {
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
                is.addSpan(a, b, data);
                begin = str.indexOf(';', begin) + 1;
                end = str.indexOf(';', begin);
            }
        }

        private static void stringToBeamSpanSet(SpanSet<BeamSpan> is, String str) {
            var begin = 0;
            var end = str.indexOf(';', begin);

            while (end != -1) {
                var firstComma = str.indexOf(',', begin);
                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(str.substring(firstComma + 1, end));
                is.addSpan(new BeamSpan(a, b));
                begin = str.indexOf(';', begin) + 1;
                end = str.indexOf(';', begin);
            }
        }

        private static void stringToTieSpanSet(SpanSet<TieSpan> is, String str) {
            var begin = 0;
            var end = str.indexOf(';', begin);

            while (end != -1) {
                var firstComma = str.indexOf(',', begin);
                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(str.substring(firstComma + 1, end));
                is.addSpan(new TieSpan(a, b));
                begin = str.indexOf(';', begin) + 1;
                end = str.indexOf(';', begin);
            }
        }

        private static void stringToTupletSpanSet(SpanSet<TupletSpan> is, String str) {
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

                int grade = 3; // default to triplet
                double vertPos = 0;

                if (secondComma != -1) {
                    // Has data portion: grade[,vertPos]
                    var dataStr = str.substring(secondComma + 1, end);
                    var parts = dataStr.split(",");

                    try {
                        grade = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException e) {
                        grade = 3;
                    }

                    if (parts.length > 1) {
                        try {
                            vertPos = Double.parseDouble(parts[1]);
                        } catch (NumberFormatException e) {
                            vertPos = 0;
                        }
                    }
                }

                var tuplet = new TupletSpan(a, b, grade);
                tuplet.setVerticalPositionSs(vertPos);
                is.addSpan(tuplet);
                begin = str.indexOf(';', begin) + 1;
                end = str.indexOf(';', begin);
            }
        }

        private static void stringToDynamicsSpanSet(SpanSet<DynamicsSpan> is, String str) {
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

                var dynamics = new DynamicsSpan(a, b);

                if (secondComma != -1) {
                    // Has data portion: x1,x2,y
                    var dataStr = str.substring(secondComma + 1, end);
                    var parts = dataStr.split(",");

                    if (parts.length >= 3) {
                        try {
                            dynamics.setX1ShiftSs(Double.parseDouble(parts[0]));
                            dynamics.setX2ShiftSs(Double.parseDouble(parts[1]));
                            dynamics.setYShiftSs(Double.parseDouble(parts[2]));
                        } catch (NumberFormatException ignored) {
                            // Leave shifts at 0
                        }
                    }
                }

                is.addSpan(dynamics);
                begin = str.indexOf(';', begin) + 1;
                end = str.indexOf(';', begin);
            }
        }

        private void parseEndingPairs(String str) {
            pendingEndingPairs.clear();
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
                pendingEndingPairs.add(new int[]{a, b});
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
                    noteReader = new StaffElementIO.StaffElementReader();
                }
            } else if (where == Where.NOTES && noteReader != null) {
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
            if (line == null || noteReader == null) {
                return null;
            }

            if (qName.equals(XML_NOTES)) {
                where = Where.LINE;
            } else if (where == Where.NOTES) {
                var n = noteReader.endElement11(qName);

                if (n != null) {
                    line.addElement(n);
                }
            } else if (where == Where.LINE) {
                if (qName.equals(XML_LINE)) {
                    where = null;
                    createEndingsFromPendingPairs(line);
                    return line;
                }
                if (lastTag != null && qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> line.setKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> line.setKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NOTE_DIST_CHANGE -> line.changeElementSpacingRatio(
                            Float.parseFloat(str)
                        );
                        case XML_TEMPO_CHANGE_YPOS -> line.setTempoChangeYPosPx(
                            Integer.parseInt(str)
                        );
                        case XML_BEAT_CHANGE_YPOS -> line.setBeatChangeYPosPx(
                            Integer.parseInt(str)
                        );
                        case XML_LYRICS_YPOS -> line.setLyricsYPosSs(
                            Double.parseDouble(str)
                        );
                        case XML_FSENDING_YPOS -> line.setFirstSecondEndingYPosPx(
                            Integer.parseInt(str)
                        );
                        case XML_TRILL_YPOS -> line.setTrillYPosPx(
                            Integer.parseInt(str)
                        );
                        case XML_BEAMINGS -> stringToBeamSpanSet(
                            line.getBeamings(),
                            str
                        );
                        case XML_TIES -> stringToTieSpanSet(
                            line.getTies(),
                            str
                        );
                        // Slurs no longer supported - ignore for backwards compatibility
                        case "slurs" -> {
                        }
                        case XML_CRESCENDO -> stringToDynamicsSpanSet(
                            line.getCrescendos(),
                            str
                        );
                        case XML_DIMINUENDO -> stringToDynamicsSpanSet(
                            line.getDiminuendos(),
                            str
                        );
                        case XML_TUPLETS, XML_TRIPLETS -> stringToTupletSpanSet(
                            line.getTuplets(),
                            str
                        );
                        case XML_FSENDINGS -> parseEndingPairs(str);
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        public void characters(char[] ch, int start, int lenght) {
            if (where == Where.NOTES && noteReader != null) {
                noteReader.characters(ch, start, lenght);
            } else if (lastTag != null) {
                value.append(ch, start, lenght);
            }
        }

        /**
         * Creates Ending range elements from the parsed fsendings index pairs.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createEndingsFromPendingPairs(Line line) {
            for (var pair : pendingEndingPairs) {
                var startElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                var ending = new Ending(startElement, endElement, Ending.Type.FIRST);
                line.addRangeElement(ending);
            }

            pendingEndingPairs.clear();
        }

        private enum Where {
            LINE,
            NOTES,
        }
    }
}
