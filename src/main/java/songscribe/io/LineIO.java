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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.Ending;

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
    static final String XML_TRILLS = "trills";

    private LineIO() {
    }

    public static void writeLine(Line line, PrintWriter pw) {
        pw.println("    <" + XML_LINE + '>');
        XML.setIndent(6);

        // A line writes its key only where it establishes one. A line that inherits writes no key
        // tags at all, which is what the reader reads back as inheriting; line 0 always
        // establishes one, so the song's starting key is always in the file.
        var key = line.getKey();

        if (key != null) {
            XML.writeValue(pw, XML_KEYS, Integer.toString(key.accidentalCount()));
            XML.writeValue(pw, XML_KEYTYPE, key.keyType().name());
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

        var beams = line.findSpans(Beam.class);

        if (!beams.isEmpty()) {
            XML.writeValue(pw, XML_BEAMINGS, spansToString(beams));
        }

        var ties = line.findTies();

        if (!ties.isEmpty()) {
            XML.writeValue(pw, XML_TIES, spansToString(ties));
        }

        var tuplets = line.findSpans(Tuplet.class);

        if (!tuplets.isEmpty()) {
            XML.writeValue(pw, XML_TUPLETS, spansToString(tuplets));
        }

        var endings = line.findEndings();

        if (!endings.isEmpty()) {
            XML.writeValue(
                pw,
                XML_FSENDINGS,
                spansToString(endings)
            );
        }

        var crescendos = line.getCrescendos();

        if (!crescendos.isEmpty()) {
            XML.writeValue(pw, XML_CRESCENDO, spansToString(crescendos));
        }

        var diminuendos = line.getDiminuendos();

        if (!diminuendos.isEmpty()) {
            XML.writeValue(pw, XML_DIMINUENDO, spansToString(diminuendos));
        }

        var trills = line.findSpans(Trill.class);

        if (!trills.isEmpty()) {
            XML.writeValue(pw, XML_TRILLS, spansToString(trills));
        }

        pw.println("      <" + XML_NOTES + '>');

        for (var i = 0; i < line.elementCount(); i++) {
            StaffElementIO.writeElement(line.getElement(i), pw, line, i);
        }

        pw.println("      </" + XML_NOTES + '>');
        pw.println("    </" + XML_LINE + '>');
    }

    static String spansToString(List<? extends Span> elements) {
        var sb = new StringBuilder(27);

        for (var element : elements) {
            sb.append(element.toIndexString());
        }

        return sb.toString();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
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

        private static final Logger LOG = LoggerFactory.getLogger(LineReader.class);

        private final Song song;

        @Nullable
        private Line line = null;

        @Nullable
        private String lastTag;

        @org.jetbrains.annotations.Nullable
        private StaffElementIO.StaffElementReader noteReader = null;
        private final StringBuilder value = new StringBuilder(20);

        // True once any note in any line read so far named a retired accidental that was
        // converted. Accumulated here because noteReader is replaced for each <line>, so
        // its own flag does not survive to the end of the document.
        private boolean sawLegacyAccidental = false;

        @Nullable
        private Where where = null;

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

        // Legacy Y position fields — populated from pre-Phase 11 XML tags and retrieved
        // via getLegacyOffsets() before this reader moves to the next line.
        private int legacyTempoChangeYPosPx;
        private int legacyBeatChangeYPosPx;
        private int legacyFsEndingYPosPx;
        private int legacyTrillYPosPx;

        // The <keys> and <keytype> tags arrive as two separate elements, and neither half is a
        // key on its own, so they are held until </line> and turned into one Key there.
        private int parsedKeyAccidentalCount = 0;
        private @Nullable KeyType parsedKeyType = null;

        public LineReader(Song song) {
            this.song = song;
            resetLegacyOffsets();
        }

        /**
         * Establishes on {@code line} the key its {@code <keys>} and {@code <keytype>} tags
         * describe, if it carried any. A line with neither tag establishes no key of its own and
         * inherits the one already in effect.
         *
         * <p>The two halves are only a key together, so {@link Key} is what judges the pair: a
         * combination it rejects is a corrupt document rather than something to interpret. Its
         * range check also covers an out-of-range accidental count — {@code Key} is the sole
         * authority on the domain, so this method does not duplicate that check.
         *
         * <p>The established key always applies from the start of the line: {@code .mssw} has no
         * representation for a mid-line key change, so this reader never produces one.
         *
         * <p>Whether the established key collapses to null because it equals the key this line
         * would inherit is {@link Line#setKey} — not this method — deciding: this method always
         * calls {@code setKey} with the parsed key and relies on {@code setKey} to normalize it.
         *
         * @param line the line being closed
         * @throws SAXException if the parsed pair is not a valid key signature
         */
        private void applyParsedKey(Line line) throws SAXException {
            var parsedType = parsedKeyType;
            var accidentalCount = parsedKeyAccidentalCount;

            if (parsedType == null && accidentalCount == 0) {
                return;
            }

            var keyType = parsedType != null ? parsedType : KeyType.NONE;

            try {
                line.setKey(new Key(keyType, accidentalCount));
            } catch (IllegalArgumentException e) {
                throw DocumentValidation.corrupt(
                    LOG, "Corrupt document: invalid key signature: {} with {} accidentals", keyType, accidentalCount);
            }
        }

        private void resetLegacyOffsets() {
            legacyTempoChangeYPosPx = LegacyLineOffsets.DEFAULTS.tempoChangeYPosPx();
            legacyBeatChangeYPosPx = LegacyLineOffsets.DEFAULTS.beatChangeYPosPx();
            legacyFsEndingYPosPx = LegacyLineOffsets.DEFAULTS.firstSecondEndingYPosPx();
            legacyTrillYPosPx = LegacyLineOffsets.DEFAULTS.trillYPosPx();
        }

        LegacyLineOffsets getLegacyOffsets() {
            return new LegacyLineOffsets(legacyTempoChangeYPosPx, legacyBeatChangeYPosPx, legacyFsEndingYPosPx, legacyTrillYPosPx);
        }

        /**
         * Returns true if any note in any line read so far named a retired accidental,
         * which was converted to the accidental that sounds the same.
         */
        public boolean sawLegacyAccidental() {
            return sawLegacyAccidental;
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

        private void parseIndexPairs(String str, List<int[]> pendingList) {
            forEachSegment(str, (begin, end) -> {
                var firstComma = str.indexOf(',', begin);
                var a = Integer.parseInt(str.substring(begin, firstComma));
                var b = Integer.parseInt(str.substring(firstComma + 1, end));
                pendingList.add(new int[]{a, b});
            });
        }

        /** Parses the {@code <beamings>} line-scope data into pending pairs. Format: {@code anchor,end;...} */
        private void parseBeamPairs(String str) {
            parseIndexPairs(str, pendingBeamPairs);
        }

        /**
         * Creates {@link Beam} spans from the parsed beam index pairs.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createBeamsFromPending(Line line) throws SAXException {
            for (var pair : pendingBeamPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), "beam");

                var anchorElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                line.addSpan(new Beam(anchorElement, endElement));
            }

            pendingBeamPairs.clear();
        }

        /**
         * Creates {@link Tie} spans from the parsed tie index pairs.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createTiesFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingTiePairs) {
                DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), "tie");

                var anchorElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                var tie = new Tie(anchorElement, endElement);
                line.addSpan(tie);
            }

            pendingTiePairs.clear();
        }

        /**
         * Creates {@link Crescendo} spans from the parsed crescendo data.
         * Called at end-of-line after all elements have been loaded.
         * <p>
         * Added through {@link Line#addCrescendo} rather than the raw
         * {@code addSpan} the other span kinds use, so a legacy file's two
         * same-type hairpins that overlap or merely touch migrate to the one hairpin
         * they musically are.
         */
        private void createCrescendosFromPending(Line line) throws SAXException {
            for (var data : pendingCrescendoPairs) {
                var start = (int) data[0];
                var end = (int) data[1];
                DocumentValidation.requireIndexInRange(LOG, start, end, line.elementCount(), "crescendo");

                var anchorElement = line.getElement(start);
                var endElement = line.getElement(end);
                var crescendo = new Crescendo(anchorElement, endElement);
                crescendo.setX1ShiftSs(data[2]);
                crescendo.setX2ShiftSs(data[3]);
                crescendo.setYShiftSs(data[4]);
                line.addCrescendo(crescendo);
            }

            pendingCrescendoPairs.clear();
        }

        /**
         * Creates {@link Diminuendo} spans from the parsed diminuendo data.
         * Called at end-of-line after all elements have been loaded. Merge semantics
         * mirror {@link #createCrescendosFromPending}.
         */
        private void createDiminuendosFromPending(Line line) throws SAXException {
            for (var data : pendingDiminuendoPairs) {
                var start = (int) data[0];
                var end = (int) data[1];
                DocumentValidation.requireIndexInRange(LOG, start, end, line.elementCount(), "diminuendo");

                var anchorElement = line.getElement(start);
                var endElement = line.getElement(end);
                var diminuendo = new Diminuendo(anchorElement, endElement);
                diminuendo.setX1ShiftSs(data[2]);
                diminuendo.setX2ShiftSs(data[3]);
                diminuendo.setYShiftSs(data[4]);
                line.addDiminuendo(diminuendo);
            }

            pendingDiminuendoPairs.clear();
        }

        /** Parses the {@code <ties>} line-scope data into pending pairs. Format: {@code anchor,end;...} */
        private void parseTiePairs(String str) {
            parseIndexPairs(str, pendingTiePairs);
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
                        LOG.warn("Corrupt document: malformed tuplet grade: '{}', using default {}", parts[0], grade);
                        // Leave grade at default of 3
                    }

                    if (parts.length > 1) {
                        try {
                            verticalPositionSs = Double.parseDouble(parts[1]);
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed tuplet verticalPositionSs: '{}', using default {}", parts[1], verticalPositionSs);
                            verticalPositionSs = 0.0;
                        }
                    }
                }

                pendingTupletData.add(new double[]{segment.a(), segment.b(), grade, verticalPositionSs});
            });
        }

        /**
         * Creates {@link Tuplet} spans from the pending tuplet data.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createTupletsFromPending(Line line) throws SAXException {
            for (var data : pendingTupletData) {
                var anchorIdx = (int) data[0];
                var endIdx = (int) data[1];
                var grade = (int) data[2];
                var verticalPositionSs = (int) data[3];
                DocumentValidation.requireIndexInRange(LOG, anchorIdx, endIdx, line.elementCount(), "tuplet");

                var anchorElement = line.getElement(anchorIdx);
                var endElement = line.getElement(endIdx);
                // Mid-parse: the beat may still change in a later line, so the ratio is
                // left to the post-load pass.
                var tuplet = Tuplet.withUnresolvedRatio(anchorElement, endElement, grade);

                if (!tuplet.hasValidSpan(line)) {
                    throw DocumentValidation.corrupt(
                        LOG, "Corrupt document: tuplet does not span at least two non-rest notes: {}, {}", anchorIdx, endIdx);
                }

                tuplet.setVerticalPositionSs(verticalPositionSs);
                line.addSpan(tuplet);
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
                    var substring = str.substring(segment.secondComma() + 1, end);
                    var parts = substring.split(",");

                    if (parts.length >= 3) {
                        try {
                            x1 = Double.parseDouble(parts[0]);
                            x2 = Double.parseDouble(parts[1]);
                            y = Double.parseDouble(parts[2]);
                        } catch (NumberFormatException ignored) {
                            LOG.warn("Corrupt document: malformed hairpin shift values '{}', using zero",
                                substring
                            );
                            // Leave shifts at 0
                        }
                    }
                }

                pendingList.add(new double[]{segment.a(), segment.b(), x1, x2, y});
            });
        }

        /** Parses the {@code <fsendings>} line-scope data into pending pairs. Format: {@code anchor,end;...} */
        private void parseEndingPairs(String str) {
            parseIndexPairs(str, pendingEndingPairs);
        }

        public void startElement11(String qName, Attributes attributes) throws SAXException {
            if (where == null) {
                if (qName.equals(XML_LINE)) {
                    where = Where.LINE;
                    line = new Line(song);
                    lastTag = null;
                    noteReader = new StaffElementIO.StaffElementReader();
                    resetLegacyOffsets();
                    parsedKeyAccidentalCount = 0;
                    parsedKeyType = null;
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
        public Line endElement11(String qName) throws SAXException {
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

                    if (noteReader.sawLegacyAccidental()) {
                        sawLegacyAccidental = true;
                    }

                    line.addElement(n);
                }
            } else if (where == Where.LINE) {
                if (qName.equals(XML_LINE)) {
                    where = null;
                    applyParsedKey(line);
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
                        case XML_KEYS -> parsedKeyAccidentalCount = DocumentValidation.parseIntOrThrow(LOG, XML_KEYS, str);
                        case XML_KEYTYPE -> {
                            try {
                                parsedKeyType = KeyType.valueOf(str);
                            } catch (IllegalArgumentException e) {
                                throw DocumentValidation.corrupt(LOG, "Corrupt document: unknown key type: '{}'", str);
                            }
                        }
                        case XML_NOTE_DIST_CHANGE -> line.changeElementSpacingRatio(DocumentValidation.parseFloatOrThrow(LOG, XML_NOTE_DIST_CHANGE, str));
                        case XML_TEMPO_CHANGE_YPOS -> legacyTempoChangeYPosPx = DocumentValidation.parseIntOrThrow(LOG, XML_TEMPO_CHANGE_YPOS, str);
                        case XML_BEAT_CHANGE_YPOS -> legacyBeatChangeYPosPx = DocumentValidation.parseIntOrThrow(LOG, XML_BEAT_CHANGE_YPOS, str);
                        case XML_LYRICS_YPOS -> line.setLyricsYPosSs(DocumentValidation.parseDoubleOrThrow(LOG, XML_LYRICS_YPOS, str));
                        case XML_FSENDING_YPOS -> legacyFsEndingYPosPx = DocumentValidation.parseIntOrThrow(LOG, XML_FSENDING_YPOS, str);
                        case XML_TRILL_YPOS -> legacyTrillYPosPx = DocumentValidation.parseIntOrThrow(LOG, XML_TRILL_YPOS, str);
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
         * Creates Ending spans from the parsed fsendings index pairs.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createEndingsFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingEndingPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), "ending");

                var startElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                var ending = new Ending(startElement, endElement);

                // Every ending must have a REPEAT splitting its two brackets (issue #306).
                // A split-less span — e.g. a pre-#306 single-bracket ending in an older
                // .mssw file — would throw during layout or MIDI generation, so drop it on
                // load, mirroring the MusicXML import path (EndingResolver.buildEnding).
                if (ending.findRepeatSplitElement(line) == null) {
                    LOG.warn("Dropping split-less <ending> on load (no repeat between anchor and end)");
                    continue;
                }

                line.addSpan(ending);
            }

            pendingEndingPairs.clear();
        }

        /**
         * Creates Trill spans from the pending trill index pairs.
         * Called at end-of-line after all elements have been loaded.
         * Handles both new {@code <trills>} data and legacy per-element trill flags (already
         * coalesced into contiguous runs by {@link #accumulateLegacyTrillFlag}).
         */
        private void createTrillsFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingTrillPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), "trill");

                var anchorElement = line.getElement(pair[0]);
                var endElement = line.getElement(pair[1]);
                var trill = new Trill(anchorElement, endElement);
                trill.setYPositionSs(pair[2]);
                line.addSpan(trill);
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

        @Nullable
        Line getLine() {
            return line;
        }

        @Nullable
        String getLastTag() {
            return lastTag;
        }

        @Nullable
        Where getWhere() {
            return where;
        }

        @SuppressWarnings("PackageVisibleInnerClass")
        enum Where {
            LINE,
            NOTES,
        }
    }
}
