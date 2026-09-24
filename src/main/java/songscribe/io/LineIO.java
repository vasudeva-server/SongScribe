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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.PendingTuplet;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Trill;
import songscribe.dom.TupletGrade;
import songscribe.util.StringUtils;

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

    @SuppressWarnings("PackageVisibleInnerClass")
    @FunctionalInterface
    interface SegmentConsumer {
        void accept(int begin, int end);
    }

    static void forEachSegment(String str, SegmentConsumer consumer) {
        var begin = 0;
        var end = str.indexOf(';', begin);

        while (end != StringUtils.INDEX_NOT_FOUND) {
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
        private @Nullable LegacyKeyType parsedKeyType = null;

        public LineReader(Song song) {
            this.song = song;
            resetLegacyOffsets();
        }

        /**
         * Establishes on {@code line} the key its {@code <keys>} and {@code <keytype>} tags
         * describe, if it carried any. A line with neither tag establishes no key of its own and
         * inherits the one already in effect.
         *
         * <p>The two halves are only a key together, so {@link LegacyKeyType#keyFor} is what judges
         * the pair, for both this reader and the song-level one — including which unrepresentable
         * combinations mean C major and which are corrupt.
         *
         * <p>The established key always applies from the start of the line: {@code .mssw} has no
         * representation for a mid-line key change, so this reader never produces one.
         *
         * <p>Whether the established key collapses to null because it equals the key this line
         * would inherit is {@link Line#setKey} — not this method — deciding: this method always
         * calls {@code setKey} with the parsed key and relies on {@code setKey} to normalize it.
         *
         * @param line the line being closed
         * @throws SAXException if the parsed pair is not a valid key
         */
        private void applyParsedKey(Line line) throws SAXException {
            var parsedType = parsedKeyType;
            var accidentalCount = parsedKeyAccidentalCount;

            if (parsedType == null && accidentalCount == 0) {
                return;
            }

            var keyType = parsedType != null ? parsedType : LegacyKeyType.NONE;
            line.setKey(keyType.keyFor(LOG, accidentalCount));
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

        /**
         * The two leading integers of a segment and, when the segment has a second comma,
         * the text after it.
         *
         * @param a the first integer
         * @param b the second integer
         * @param data the text after the second comma, or null when the segment has none
         */
        private record SegmentParts(int a, int b, @Nullable String data) {}

        private static SegmentParts parseSegmentAB(String str, int begin, int end) {
            var firstComma = str.indexOf(',', begin);
            var secondComma = str.indexOf(',', firstComma + 1);
            var hasData = secondComma != StringUtils.INDEX_NOT_FOUND && secondComma < end;
            var bEnd = hasData ? secondComma : end;
            var a = Integer.parseInt(str.substring(begin, firstComma));
            var b = Integer.parseInt(str.substring(firstComma + 1, bEnd));
            var data = hasData ? str.substring(secondComma + 1, end) : null;
            return new SegmentParts(a, b, data);
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
                addNoteSpanFromPair(line, pair, "beam", Beam::new);
            }

            pendingBeamPairs.clear();
        }

        /**
         * Ties the anchor named by each parsed tie index pair. The end is derived, not read from
         * the file, so the pair's second index is not used; a pair whose anchor is not a
         * {@link ElementType.Kind#NOTE} note is ignored, the way a reader converts at its
         * boundary. Called at end-of-line after all elements have been loaded.
         */
        private void createTiesFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingTiePairs) {
                var anchorIndex = pair[0];

                if (anchorIndex < 0 || anchorIndex >= line.elementCount()) {
                    throw DocumentValidation.corrupt(LOG, "tie anchor index out of range: {}", anchorIndex);
                }

                if (line.getElement(anchorIndex) instanceof NoteElement anchor && anchor.getType().isPitchedNote()) {
                    line.addTie(anchor);
                }
            }

            pendingTiePairs.clear();
        }

        /**
         * Adds one span built from a pair of element indices, which is all the legacy format stores
         * for a beam or a tie. Both kinds span notes, so an index naming anything else is a corrupt
         * file rather than a state the document model can hold.
         *
         * @param line        the line the pair indexes, and the line to add the span to
         * @param pair        the anchor index and the end index, in that order
         * @param label       the span kind, for the diagnostic a bad pair produces
         * @param spanFactory builds the span from the two notes the indices name
         * @throws SAXException if either index is out of range or does not name a note
         */
        private void addNoteSpanFromPair(
            Line line, int[] pair, String label, BiFunction<NoteElement, NoteElement, Span<?>> spanFactory)
            throws SAXException {
            DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), label);
            spanFactory.apply(
                noteEndpoint(line, pair[0], label), noteEndpoint(line, pair[1], label)).addTo(line);
        }

        /**
         * Narrows one endpoint index of a legacy beam or tie to the note it must name.
         *
         * @param line  the line the index names an element of
         * @param index the endpoint index
         * @param label the span kind, for the diagnostic
         * @return the element at {@code index}, as a note
         * @throws SAXException if the element at {@code index} is not a note
         */
        private static NoteElement noteEndpoint(Line line, int index, String label) throws SAXException {
            var element = line.getElement(index);

            if (element instanceof NoteElement note) {
                return note;
            }

            throw DocumentValidation.corrupt(
                LOG, "{} endpoint at index {} is not a note: {}", label, index, element);
        }

        /**
         * Creates crescendo {@link Hairpin} spans from the parsed crescendo data.
         * Called at end-of-line after all elements have been loaded.
         * <p>
         * Added through {@link Hairpin#addTo}, whose displacement policy merges a
         * legacy file's two same-type hairpins that overlap or merely touch into the one
         * hairpin they musically are.
         */
        private void createCrescendosFromPending(Line line) throws SAXException {
            for (var data : pendingCrescendoPairs) {
                var start = (int) data[0];
                var end = (int) data[1];
                DocumentValidation.requireIndexInRange(LOG, start, end, line.elementCount(), "crescendo");

                var anchorElement = line.getElement(start);
                var endElement = line.getElement(end);
                var crescendo = new Hairpin(Hairpin.Kind.CRESCENDO, anchorElement, endElement);
                crescendo.setX1ShiftSs(data[2]);
                crescendo.setX2ShiftSs(data[3]);
                crescendo.setUserYOffsetSs(data[4]);
                crescendo.addTo(line);
            }

            pendingCrescendoPairs.clear();
        }

        /**
         * Creates diminuendo {@link Hairpin} spans from the parsed diminuendo data.
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
                var diminuendo = new Hairpin(Hairpin.Kind.DIMINUENDO, anchorElement, endElement);
                diminuendo.setX1ShiftSs(data[2]);
                diminuendo.setX2ShiftSs(data[3]);
                diminuendo.setUserYOffsetSs(data[4]);
                diminuendo.addTo(line);
            }

            pendingDiminuendoPairs.clear();
        }

        /** Parses the {@code <ties>} line-scope data into pending pairs. Format: {@code anchor,end;...} */
        private void parseTiePairs(String str) {
            parseIndexPairs(str, pendingTiePairs);
        }

        /**
         * Stores parsed tuplet data until elements are loaded, one line at a time.
         * Format per entry: [anchorIndex, endIndex, grade, yOffsetSs].
         */
        private final List<double[]> pendingTupletSegments = new ArrayList<>();

        /**
         * Every tuplet this reader has resolved so far, across the whole document. Kept for
         * the whole document rather than cleared per line, since the ratio a stated-less
         * tuplet needs is only settled once the whole song has been read: {@link
         * songscribe.dom.TupletLoadPass#run} derives it from the beat at the anchor, which a
         * tempo change later in the file may still redefine while this reader is mid-parse.
         */
        private final List<PendingTuplet> pendingTupletData = new ArrayList<>();

        private void parseTupletData(String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var grade = 3; // default to triplet
                var yOffsetSs = 0.0;

                var data = segment.data();

                if (data != null) {
                    // Has data portion: grade[,vertPos]
                    var parts = data.split(",");

                    try {
                        grade = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException _) {
                        LOG.warn("Corrupt document: malformed tuplet grade: '{}', using default {}", parts[0], grade);
                        // Leave grade at default of 3
                    }

                    if (parts.length > 1) {
                        try {
                            yOffsetSs = Double.parseDouble(parts[1]);
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed tuplet yOffsetSs: '{}', using default {}", parts[1], yOffsetSs);
                            yOffsetSs = 0.0;
                        }
                    }
                }

                pendingTupletSegments.add(new double[]{segment.a(), segment.b(), grade, yOffsetSs});
            });
        }

        /**
         * Resolves each parsed tuplet segment's anchor and end against {@code line}'s
         * now-complete elements and appends a {@link PendingTuplet} with no stated ratio.
         * Called at end-of-line after all elements have been loaded.
         */
        private void createTupletsFromPending(Line line) throws SAXException {
            for (var data : pendingTupletSegments) {
                var anchorIdx = (int) data[0];
                var endIdx = (int) data[1];
                var parsedGrade = (int) data[2];
                var yOffsetSs = data[3];
                DocumentValidation.requireIndexInRange(LOG, anchorIdx, endIdx, line.elementCount(), "tuplet");

                var grade = TupletGrade.of(parsedGrade);

                if (grade == null) {
                    LOG.warn("Corrupt document: tuplet grade {} is outside 2..7, dropping tuplet", parsedGrade);
                    continue;
                }

                var anchorElement = line.getElement(anchorIdx);
                var endElement = line.getElement(endIdx);

                pendingTupletData.add(new PendingTuplet(anchorElement, endElement, grade, null, yOffsetSs));
            }

            pendingTupletSegments.clear();
        }

        /**
         * Returns every tuplet this reader has found in the document, with no stated ratio.
         */
        public List<PendingTuplet> pendingTuplets() {
            return List.copyOf(pendingTupletData);
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

                var data = segment.data();

                if (data != null) {
                    // Has data portion: x1,x2,y
                    var parts = data.split(",");

                    if (parts.length >= 3) {
                        try {
                            x1 = Double.parseDouble(parts[0]);
                            x2 = Double.parseDouble(parts[1]);
                            y = Double.parseDouble(parts[2]);
                        } catch (NumberFormatException ignored) {
                            LOG.warn("Corrupt document: malformed hairpin shift values '{}', using zero",
                                data
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
                        case XML_KEYTYPE -> parsedKeyType = LegacyKeyType.parse(LOG, str);
                        // Legacy line-compression ratio - nothing reads it; ignore for backwards compatibility
                        case XML_NOTE_DIST_CHANGE -> {
                        }
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

                var ending = Ending.spanning(line, new ElementRange(pair[0], pair[1]));

                // A pre-#306 single-bracket ending in an older .mssw file has no repeat
                // splitting its two brackets, which Ending's invariant requires, so it is
                // dropped on load as the MusicXML import path drops one.
                if (ending == null) {
                    LOG.warn("Dropping split-less <ending> on load (no repeat between anchor and end)");
                    continue;
                }

                ending.addTo(line);
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
                trill.setUserYOffsetSs(pair[2]);
                trill.addTo(line);
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
                var segment = parseSegmentAB(str, begin, end);
                var data = segment.data();
                var yPositionSs = (data == null) ? 0 : Integer.parseInt(data);
                pendingTrillPairs.add(new int[]{segment.a(), segment.b(), yPositionSs});
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
