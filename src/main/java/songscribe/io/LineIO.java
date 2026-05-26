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

import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.dom.Hairpin;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

public final class LineIO {

    private static final String XML_LINE = "line";
    static final String XML_KEYS = "keys";
    static final String XML_KEYTYPE = "keytype";
    static final String XML_NOTE_DIST_CHANGE = "notedistchange";
    static final String XML_LYRICS_YPOS = "lyricsypos";
    static final String XML_FSENDING_YPOS = "fsendingypos";
    static final String XML_TEMPO_CHANGE_YPOS = "tempochangeypos";
    static final String XML_BEAT_CHANGE_YPOS = "beatchangeypos";
    static final String XML_TRILL_YPOS = "trillypos";
    static final String XML_BEAMINGS = "beamings";
    static final String XML_TIES = "ties";
    static final String XML_TRIPLETS = "triplets"; // the old version of triplets
    static final String XML_TUPLETS = "tuplets";
    static final String XML_FSENDINGS = "fsendings";
    private static final String XML_NOTES = "notes";
    static final String XML_CRESCENDO = "crescendo";
    static final String XML_DIMINUENDO = "diminuendo";
    private static final String XML_TRILLS = "trills";

    private LineIO() {
    }

    public static void writeLine(Line line, PrintWriter pw) {
        pw.println("    <" + XML_LINE + '>');
        XML.setIndent(6);

        var song = line.getSong();
        var lineKeyType = line.getKeyType();

        if (
            (line.getKeyAccidentalCount() != song.getDefaultKeyAccidentalCount()) ||
                (lineKeyType != song.getDefaultKeyType())
        ) {
            XML.writeValue(
                pw,
                XML_KEYS,
                Integer.toString(line.getKeyAccidentalCount())
            );

            if (lineKeyType != null) {
                XML.writeValue(pw, XML_KEYTYPE, lineKeyType.name());
            }
        }

        if (line.getElementSpacingRatio() != 1f) {
            XML.writeValue(
                pw,
                XML_NOTE_DIST_CHANGE,
                Float.toString(line.getElementSpacingRatio())
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
            Double.toString(line.getLyricsYPosSs())
        );

        var beams = line.findRangeElements(Beam.class);

        if (!beams.isEmpty()) {
            XML.writeValue(pw, XML_BEAMINGS, beamsToString(beams));
        }

        var ties = line.findTies();

        if (!ties.isEmpty()) {
            XML.writeValue(pw, XML_TIES, tiesToString(ties));
        }

        var tuplets = line.findRangeElements(Tuplet.class);

        if (!tuplets.isEmpty()) {
            XML.writeValue(pw, XML_TUPLETS, tupletsToString(tuplets));
        }

        var endings = LineEndingSupport.findEndings(line);

        if (!endings.isEmpty()) {
            XML.writeValue(
                pw,
                XML_FSENDINGS,
                endingsToString(endings)
            );
        }

        var crescendos = line.getCrescendos();

        if (!crescendos.isEmpty()) {
            XML.writeValue(pw, XML_CRESCENDO, hairpinsToString(crescendos));
        }

        var diminuendos = line.getDiminuendos();

        if (!diminuendos.isEmpty()) {
            XML.writeValue(pw, XML_DIMINUENDO, hairpinsToString(diminuendos));
        }

        var trills = line.findRangeElements(Trill.class);

        if (!trills.isEmpty()) {
            XML.writeValue(pw, XML_TRILLS, trillsToString(trills));
        }

        pw.println("      <" + XML_NOTES + '>');

        for (var i = 0; i < line.elementCount(); i++) {
            StaffElementIO.writeElement(line.getElement(i), pw, line, i);
        }

        pw.println("      </" + XML_NOTES + '>');
        pw.println("    </" + XML_LINE + '>');
    }

    private static String tiesToString(List<? extends Tie> ties) {
        var sb = new StringBuilder(27);

        for (var tie : ties) {
            sb.append(tie.getAnchorElementIndex());
            sb.append(',');
            sb.append(tie.getEndElementIndex());
            sb.append(';');
        }

        return sb.toString();
    }

    static String trillsToString(List<? extends Trill> trills) {
        var sb = new StringBuilder(27);

        for (var trill : trills) {
            sb.append(trill.getAnchorElementIndex());
            sb.append(',');
            sb.append(trill.getEndElementIndex());

            if (trill.getYPositionSs() != 0) {
                sb.append(',');
                sb.append(trill.getYPositionSs());
            }

            sb.append(';');
        }

        return sb.toString();
    }

    static String endingsToString(List<? extends Ending> endings) {
        var sb = new StringBuilder(27);

        for (var ending : endings) {
            sb.append(ending.getAnchorElementIndex());
            sb.append(',');
            sb.append(ending.getEndElementIndex());
            sb.append(';');
        }

        return sb.toString();
    }

    static String beamsToString(List<? extends Beam> beams) {
        var sb = new StringBuilder(27);

        for (var beam : beams) {
            sb.append(beam.getAnchorElementIndex());
            sb.append(',');
            sb.append(beam.getEndElementIndex());
            sb.append(';');
        }

        return sb.toString();
    }

    static String tupletsToString(List<? extends Tuplet> tuplets) {
        var sb = new StringBuilder(27);

        for (var tuplet : tuplets) {
            sb.append(tuplet.getAnchorElementIndex());
            sb.append(',');
            sb.append(tuplet.getEndElementIndex());
            sb.append(',');
            sb.append(tuplet.getGrade());

            if (tuplet.getVerticalPositionSs() != 0) {
                sb.append(',');
                sb.append(tuplet.getVerticalPositionSs());
            }

            sb.append(';');
        }

        return sb.toString();
    }

    static String hairpinsToString(List<? extends Hairpin> hairpins) {
        var sb = new StringBuilder(27);

        for (var hairpin : hairpins) {
            sb.append(hairpin.getAnchorElementIndex());
            sb.append(',');
            sb.append(hairpin.getEndElementIndex());

            var x1 = hairpin.getX1ShiftSs();
            var x2 = hairpin.getX2ShiftSs();
            var y = hairpin.getYShiftSs();

            if (x1 != 0 || x2 != 0 || y != 0) {
                sb.append(',');
                sb.append(x1);
                sb.append(',');
                sb.append(x2);
                sb.append(',');
                sb.append(y);
            }

            sb.append(';');
        }

        return sb.toString();
    }

    @FunctionalInterface
    interface SegmentConsumer {
        void accept(int begin, int end);
    }

    static void forEachSegment(String str, SegmentConsumer consumer) {
        var begin = 0;
        var end = str.indexOf(';', begin);

        while (end != -1) {
            consumer.accept(begin, end);
            begin = end + 1;
            end = str.indexOf(';', begin);
        }
    }

    public static class LineReader {

        private final Song song;

        @Nullable
        Line line = null;

        @Nullable
        String lastTag;

        @org.jetbrains.annotations.Nullable
        private StaffElementIO.StaffElementReader noteReader = null;
        private final StringBuilder value = new StringBuilder(20);

        @Nullable
        Where where = null;

        /** Temporarily holds parsed fsendings index pairs (start, end) until elements are loaded. */
        private final List<int[]> pendingEndingPairs = new ArrayList<>();

        /**
         * Temporarily holds parsed trill index pairs (anchorIndex, endIndex, yPositionSs) until
         * elements are loaded. The third element is the optional Y offset (0 if absent).
         * Used for both legacy trill flags (coalesced into runs) and new {@code <trills>} data.
         */
        private final List<int[]> pendingTrillPairs = new ArrayList<>();

        /** Temporarily holds parsed tie index pairs (anchorIndex, endIndex) until elements are loaded. */
        private final List<int[]> pendingTiePairs = new ArrayList<>();

        /**
         * Temporarily holds parsed crescendo data (anchorIndex, endIndex, x1ShiftSs * 1000 as int,
         * x2ShiftSs * 1000 as int, yShiftSs * 1000 as int) until elements are loaded.
         * Shifts are stored as raw double arrays; indices 0–1 are int, 2–4 are double.
         */
        private final List<double[]> pendingCrescendoPairs = new ArrayList<>();

        /** Temporarily holds parsed diminuendo data (same format as crescendo). */
        private final List<double[]> pendingDiminuendoPairs = new ArrayList<>();

        public LineReader(Song song) {
            this.song = song;
        }

        private record SegmentParts(int a, int b, int secondComma) {}

        private static SegmentParts parseSegmentAB(String str, int begin, int end) {
            var firstComma = str.indexOf(',', begin);
            var secondComma = str.indexOf(',', firstComma + 1);

            if (secondComma > end) {
                secondComma = -1;
            }

            var a = Integer.parseInt(str.substring(begin, firstComma));
            var b = Integer.parseInt(
                str.substring(firstComma + 1, (secondComma == -1) ? end : secondComma)
            );
            return new SegmentParts(a, b, secondComma);
        }

        /**
         * Temporarily holds parsed beam index pairs (anchorIndex, endIndex) until
         * elements are loaded.
         */
        private final List<int[]> pendingBeamPairs = new ArrayList<>();

        /**
         * Parses the {@code <beamings>} line-scope data into pending pairs.
         * Format: {@code anchor,end;...}
         */
        private void parseBeamPairs(String str) {
            forEachSegment(str, (begin, end) -> {
                var firstComma = str.indexOf(',', begin);
                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(str.substring(firstComma + 1, end));
                pendingBeamPairs.add(new int[]{a, b});
            });
        }

        /**
         * Creates {@link Beam} range elements from the parsed beam index pairs.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createBeamsFromPending(Line line) {
            for (var pair : pendingBeamPairs) {
                if (pair[0] < 0 || pair[1] >= line.elementCount() || pair[0] > pair[1]) {
                    continue;
                }

                var anchorElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                line.addRangeElement(new Beam(anchorElement, endElement));
            }

            pendingBeamPairs.clear();
        }

        /**
         * Creates {@link Tie} range elements from the parsed tie index pairs.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createTiesFromPendingPairs(Line line) {
            for (var pair : pendingTiePairs) {
                var anchorElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                var tie = new Tie(anchorElement, endElement);
                line.addRangeElement(tie);
            }

            pendingTiePairs.clear();
        }

        /**
         * Creates {@link Crescendo} range elements from the parsed crescendo data.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createCrescendosFromPending(Line line) {
            for (var data : pendingCrescendoPairs) {
                var anchorElement = line.getElement((int) data[0]);
                var endElement = line.getElement((int) data[1]);
                var crescendo = new Crescendo(anchorElement, endElement);
                crescendo.setX1ShiftSs(data[2]);
                crescendo.setX2ShiftSs(data[3]);
                crescendo.setYShiftSs(data[4]);
                line.addRangeElement(crescendo);
            }

            pendingCrescendoPairs.clear();
        }

        /**
         * Creates {@link Diminuendo} range elements from the parsed diminuendo data.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createDiminuendosFromPending(Line line) {
            for (var data : pendingDiminuendoPairs) {
                var anchorElement = line.getElement((int) data[0]);
                var endElement = line.getElement((int) data[1]);
                var diminuendo = new Diminuendo(anchorElement, endElement);
                diminuendo.setX1ShiftSs(data[2]);
                diminuendo.setX2ShiftSs(data[3]);
                diminuendo.setYShiftSs(data[4]);
                line.addRangeElement(diminuendo);
            }

            pendingDiminuendoPairs.clear();
        }

        /**
         * Parses the {@code <ties>} line-scope data into pending pairs.
         * Format: {@code anchor,end;...}
         */
        private void parseTiePairs(String str) {
            forEachSegment(str, (begin, end) -> {
                var firstComma = str.indexOf(',', begin);
                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(str.substring(firstComma + 1, end));
                pendingTiePairs.add(new int[]{a, b});
            });
        }

        /**
         * Stores parsed tuplet data until elements are loaded.
         * Format per entry: [anchorIndex, endIndex, grade, verticalPositionSs * 1000 as int].
         * verticalPositionSs is stored as-is (a double) in a wrapper; using a double[] for simplicity.
         */
        private final List<double[]> pendingTupletData = new ArrayList<>();

        private void parseTupletData(String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var grade = 3; // default to triplet
                var verticalPositionSs = 0.0;

                if (segment.secondComma() != -1) {
                    // Has data portion: grade[,vertPos]
                    var parts = str.substring(segment.secondComma() + 1, end).split(",");

                    try {
                        grade = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException _) {
                        // Leave grade at default of 3
                    }

                    if (parts.length > 1) {
                        try {
                            verticalPositionSs = Double.parseDouble(parts[1]);
                        } catch (NumberFormatException e) {
                            verticalPositionSs = 0.0;
                        }
                    }
                }

                pendingTupletData.add(new double[]{segment.a(), segment.b(), grade, verticalPositionSs});
            });
        }

        /**
         * Creates {@link Tuplet} range elements from the pending tuplet data.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createTupletsFromPending(Line line) {
            for (var data : pendingTupletData) {
                var anchorIdx = (int) data[0];
                var endIdx = (int) data[1];
                var grade = (int) data[2];
                var verticalPositionSs = (int) data[3];

                if (anchorIdx < 0 || endIdx >= line.elementCount() || anchorIdx > endIdx) {
                    continue;
                }

                var anchorElement = line.getElement(anchorIdx);
                var endElement = line.getElement(endIdx);
                var tuplet = new Tuplet(anchorElement, endElement, grade);
                tuplet.setVerticalPositionSs(verticalPositionSs);
                line.addRangeElement(tuplet);
            }

            pendingTupletData.clear();
        }

        /**
         * Parses hairpin (crescendo or diminuendo) data into the given pending list.
         * Format: {@code anchorIdx,endIdx[,x1ShiftSs,x2ShiftSs,yShiftSs];...}
         */
        private void parseHairpinPairs(List<double[]> pendingList, String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var x1 = 0.0;
                var x2 = 0.0;
                var y = 0.0;

                if (segment.secondComma() != -1) {
                    // Has data portion: x1,x2,y
                    var parts = str.substring(segment.secondComma() + 1, end).split(",");

                    if (parts.length >= 3) {
                        try {
                            x1 = Double.parseDouble(parts[0]);
                            x2 = Double.parseDouble(parts[1]);
                            y = Double.parseDouble(parts[2]);
                        } catch (NumberFormatException ignored) {
                            // Leave shifts at 0
                        }
                    }
                }

                pendingList.add(new double[]{segment.a(), segment.b(), x1, x2, y});
            });
        }

        private void parseEndingPairs(String str) {
            pendingEndingPairs.clear();
            forEachSegment(str, (begin, end) -> {
                var firstComma = str.indexOf(',', begin);
                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(str.substring(firstComma + 1, end));
                pendingEndingPairs.add(new int[]{a, b});
            });
        }

        public void startElement11(String qName, Attributes attributes) {
            if (where == null) {
                if (qName.equals(XML_LINE)) {
                    where = Where.LINE;
                    line = new Line(song);
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
                    var elementIndex = line.elementCount();

                    if (noteReader.isTrillFlagged()) {
                        accumulateLegacyTrillFlag(elementIndex);
                    }

                    line.addElement(n);
                }
            } else if (where == Where.LINE) {
                if (qName.equals(XML_LINE)) {
                    where = null;
                    createTiesFromPendingPairs(line);
                    createEndingsFromPendingPairs(line);
                    createTrillsFromPendingPairs(line);
                    createCrescendosFromPending(line);
                    createDiminuendosFromPending(line);
                    createTupletsFromPending(line);
                    createBeamsFromPending(line);
                    return line;
                }
                //noinspection PointlessNullCheck
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
                        case XML_BEAMINGS -> parseBeamPairs(str);
                        case XML_TIES -> parseTiePairs(str);
                        // Slurs no longer supported - ignore for backwards compatibility
                        case "slurs" -> {
                        }
                        case XML_CRESCENDO -> parseHairpinPairs(pendingCrescendoPairs, str);
                        case XML_DIMINUENDO -> parseHairpinPairs(pendingDiminuendoPairs, str);
                        case XML_TUPLETS, XML_TRIPLETS -> parseTupletData(str);
                        case XML_FSENDINGS -> parseEndingPairs(str);
                        case XML_TRILLS -> parseTrillPairs(str);
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        public void characters(char[] ch, int start, int length) {
            if (where == Where.NOTES && noteReader != null) {
                noteReader.characters(ch, start, length);
            } else if (lastTag != null) {
                value.append(ch, start, length);
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

        /**
         * Creates Trill range elements from the pending trill index pairs.
         * Called at end-of-line after all elements have been loaded.
         * Handles both new {@code <trills>} data and legacy per-element trill flags (already
         * coalesced into contiguous runs by {@link #accumulateLegacyTrillFlag}).
         */
        private void createTrillsFromPendingPairs(Line line) {
            for (var pair : pendingTrillPairs) {
                var anchorElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                var trill = new Trill(anchorElement, endElement);
                trill.setYPositionSs(pair[2]);
                line.addRangeElement(trill);
            }

            pendingTrillPairs.clear();
        }

        /**
         * Accumulates a legacy trill-flagged element index into contiguous runs.
         * Each run (consecutive indices) becomes a single {@code [anchor, end, 0]} triple in
         * {@link #pendingTrillPairs}. Called before the element is added to the line, so
         * {@code elementIndex} is the index the element will occupy.
         */
        private void accumulateLegacyTrillFlag(int elementIndex) {
            if (!pendingTrillPairs.isEmpty()) {
                var last = pendingTrillPairs.getLast();

                // Extend the current run if this element is contiguous
                if (last[1] == elementIndex - 1) {
                    last[1] = elementIndex;
                    return;
                }
            }

            // Start a new run: anchor == end == elementIndex, yPositionSs == 0
            pendingTrillPairs.add(new int[]{elementIndex, elementIndex, 0});
        }

        /**
         * Parses the new {@code <trills>} line-scope data into pending pairs.
         * Format: {@code anchor,end[,yPositionSs];...}
         */
        private void parseTrillPairs(String str) {
            forEachSegment(str, (begin, end) -> {
                var firstComma = str.indexOf(',', begin);
                var secondComma = str.indexOf(',', firstComma + 1);

                if (secondComma > end) {
                    secondComma = -1;
                }

                var anchor = Integer.parseInt(str.substring(begin, firstComma));
                var endIdx = Integer.parseInt(
                    str.substring(firstComma + 1, secondComma == -1 ? end : secondComma)
                );
                var yPositionSs = (secondComma == -1) ? 0
                    : Integer.parseInt(str.substring(secondComma + 1, end));
                pendingTrillPairs.add(new int[]{anchor, endIdx, yPositionSs});
            });
        }

        enum Where {
            LINE,
            NOTES,
        }
    }
}
