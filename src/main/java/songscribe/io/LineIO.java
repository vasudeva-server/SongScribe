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
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingHairpin;
import songscribe.dom.PendingTuplet;
import songscribe.dom.Song;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Tie;
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

        // Each line, in document order, on which a note dropped a lyric whose number has no
        // script. Kept here for the same reason as sawLegacyAccidental.
        private final Set<Line> linesThatDroppedLyrics = new LinkedHashSet<>();

        @Nullable
        private Where where = null;

        private final List<int[]> pendingEndingPairs = new ArrayList<>();

        private final List<PendingEnding> pendingEndingData = new ArrayList<>();

        private final List<PendingHairpin> pendingHairpinData = new ArrayList<>();

        private record TrillPair(int anchor, int end, int yOffsetSs) {}

        private final List<TrillPair> pendingTrillPairs = new ArrayList<>();

        private final List<Integer> pendingTieAnchors = new ArrayList<>();

        private record HairpinEntry(int anchor, int end, double yOffsetSs) {}

        private final EnumMap<Hairpin.Kind, List<HairpinEntry>> pendingHairpinEntries =
            new EnumMap<>(Hairpin.Kind.class);

        // Legacy Y position fields — populated from format v1 XML tags and retrieved
        // via getLegacyOffsets() before this reader moves to the next line.
        private int legacyTempoChangeYPosPx;
        private int legacyBeatChangeYPosPx;
        private int legacyTrillYPosPx;

        // The <keys> and <keytype> tags arrive as two separate elements, and neither half is a
        // key on its own, so they are held until </line> and turned into one Key there.
        private int parsedKeyAccidentalCount = 0;
        private @Nullable LegacyKeyType parsedKeyType = null;

        public LineReader(Song song) {
            this.song = song;
            resetLegacyOffsets();
        }

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
            legacyTrillYPosPx = LegacyLineOffsets.DEFAULTS.trillYPosPx();
        }

        LegacyLineOffsets getLegacyOffsets() {
            return new LegacyLineOffsets(legacyTempoChangeYPosPx, legacyBeatChangeYPosPx, legacyTrillYPosPx);
        }

        public boolean sawLegacyAccidental() {
            return sawLegacyAccidental;
        }

        /**
         * Each line appears once, in document order.
         */
        public List<Line> linesThatDroppedLyrics() {
            return List.copyOf(linesThatDroppedLyrics);
        }

        /**
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

        private record BeamPair(int anchor, int end) {}

        private final List<BeamPair> pendingBeamPairs = new ArrayList<>();

        private static void forEachIndexPair(String str, SegmentConsumer consumer) {
            forEachSegment(str, (begin, end) -> {
                var firstComma = str.indexOf(',', begin);
                var first = Integer.parseInt(str.substring(begin, firstComma));
                var second = Integer.parseInt(str.substring(firstComma + 1, end));
                consumer.accept(first, second);
            });
        }

        private void parseBeamPairs(String str) {
            forEachIndexPair(str, (anchor, end) -> pendingBeamPairs.add(new BeamPair(anchor, end)));
        }

        /**
         * A range the beam rule refuses is dropped with a log line, not reported to the user.
         */
        private void createBeamsFromPending(Line line) throws SAXException {
            for (var pair : pendingBeamPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair.anchor(), pair.end(), line.elementCount(), "beam");

                switch (Beam.spanning(line, new ElementRange(pair.anchor(), pair.end()))) {
                    case Spanning.Spanned<Beam>(var beam, _) -> beam.addTo();
                    case Spanning.Refused<Beam> refused -> LOG.warn(
                        "Corrupt document: dropping beam {}..{}: {}",
                        pair.anchor(), pair.end(), Strings.get(refused.reasonKey()));
                }
            }

            pendingBeamPairs.clear();
        }

        private void createTiesFromPendingAnchors(Line line) throws SAXException {
            for (var anchorIndex : pendingTieAnchors) {
                DocumentValidation.requireIndexInRange(LOG, anchorIndex, anchorIndex, line.elementCount(), "tie");

                var element = line.getElement(anchorIndex);

                if (element instanceof NoteElement anchor && Tie.canAnchor(anchor)) {
                    line.addTie(anchor);
                } else {
                    LOG.warn("Corrupt document: dropping tie on element {} that cannot carry one: {}",
                        anchorIndex, element);
                }
            }

            pendingTieAnchors.clear();
        }

        /**
         * Builds no {@link Hairpin}: the span load pass judges each record.
         */
        private void recordPendingHairpins(Line line) throws SAXException {
            for (var entries : pendingHairpinEntries.entrySet()) {
                var kind = entries.getKey();
                var label = kind.name().toLowerCase(Locale.ROOT);

                for (var entry : entries.getValue()) {
                    DocumentValidation.requireIndexInRange(
                        LOG, entry.anchor(), entry.end(), line.elementCount(), label);

                    pendingHairpinData.add(new PendingHairpin(
                        kind, line.getElement(entry.anchor()), line.getElement(entry.end()), 0, entry.yOffsetSs()));
                }
            }

            pendingHairpinEntries.clear();
        }

        private void parseTiePairs(String str) {
            forEachIndexPair(str, (anchor, _) -> pendingTieAnchors.add(anchor));
        }

        private record TupletSegment(int anchor, int end, int grade, double yOffsetSs) {}

        private final List<TupletSegment> pendingTupletSegments = new ArrayList<>();

        private final List<PendingTuplet> pendingTupletData = new ArrayList<>();

        private void parseTupletData(String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var grade = 3;
                var yOffsetSs = 0.0;

                var data = segment.data();

                if (data != null) {
                    var parts = data.split(",");

                    try {
                        grade = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException _) {
                        LOG.warn("Corrupt document: malformed tuplet grade: '{}', using default {}", parts[0], grade);
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

                pendingTupletSegments.add(new TupletSegment(segment.a(), segment.b(), grade, yOffsetSs));
            });
        }

        private void createTupletsFromPending(Line line) throws SAXException {
            for (var segment : pendingTupletSegments) {
                DocumentValidation.requireIndexInRange(
                    LOG, segment.anchor(), segment.end(), line.elementCount(), "tuplet");

                var grade = TupletGrade.of(segment.grade());

                if (grade == null) {
                    LOG.warn("Corrupt document: tuplet grade {} is outside 2..7, dropping tuplet", segment.grade());
                    continue;
                }

                pendingTupletData.add(new PendingTuplet(
                    line.getElement(segment.anchor()), line.getElement(segment.end()), grade, null,
                    segment.yOffsetSs()));
            }

            pendingTupletSegments.clear();
        }

        /**
         * Offsets are in the units the document stores. Complete once parsing is.
         */
        public List<PendingTuplet> pendingTuplets() {
            return List.copyOf(pendingTupletData);
        }

        /**
         * Complete once parsing is.
         */
        public List<PendingEnding> pendingEndings() {
            return List.copyOf(pendingEndingData);
        }

        /**
         * Vertical offsets are in the units the document stores. Complete once parsing is.
         */
        public List<PendingHairpin> pendingHairpins() {
            return List.copyOf(pendingHairpinData);
        }

        /**
         * Only {@code y} is read: SongScribe has no horizontal shift of a hairpin end.
         */
        private void parseHairpinEntries(Hairpin.Kind kind, String str) {
            var entries = pendingHairpinEntries.computeIfAbsent(kind, _ -> new ArrayList<>());

            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var y = 0.0;

                var data = segment.data();

                // The vertical offset is the third field of x1,x2,y, so a data portion with two
                // commas carries one.
                if (data != null && data.indexOf(',') != data.lastIndexOf(',')) {
                    try {
                        y = Double.parseDouble(data.substring(data.lastIndexOf(',') + 1));
                    } catch (NumberFormatException ignored) {
                        LOG.warn("Corrupt document: malformed hairpin offset '{}', using zero", data);
                    }
                }

                entries.add(new HairpinEntry(segment.a(), segment.b(), y));
            });
        }

        private void parseEndingPairs(String str) {
            forEachIndexPair(str, (anchor, end) -> pendingEndingPairs.add(new int[]{anchor, end}));
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

                    if (noteReader.droppedLyric()) {
                        linesThatDroppedLyrics.add(line);
                    }

                    line.addElement(n);
                }
            } else if (where == Where.LINE) {
                if (qName.equals(XML_LINE)) {
                    where = null;
                    applyParsedKey(line);
                    // Ties before beams: Beam.spanning refuses a beam over a tied pair, so the
                    // beam is what a conflicting file loses, never the tie.
                    createTiesFromPendingAnchors(line);
                    createEndingsFromPendingPairs(line);
                    createTrillsFromPendingPairs(line);
                    recordPendingHairpins(line);
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
                        // Legacy line-level ending offset - endings load with no offset; ignore
                        case XML_FSENDING_YPOS -> {
                        }
                        case XML_TRILL_YPOS -> legacyTrillYPosPx = DocumentValidation.parseIntOrThrow(LOG, XML_TRILL_YPOS, str);
                        case XML_BEAMINGS -> parseBeamPairs(str);
                        case XML_TIES -> parseTiePairs(str);
                        // Slurs are not supported - ignored for backwards compatibility
                        case "slurs" -> {
                        }
                        case XML_CRESCENDO -> parseHairpinEntries(Hairpin.Kind.CRESCENDO, str);
                        case XML_DIMINUENDO -> parseHairpinEntries(Hairpin.Kind.DIMINUENDO, str);
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

        private void createEndingsFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingEndingPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), "ending");

                pendingEndingData.add(new PendingEnding(line.getElement(pair[0]), line.getElement(pair[1]), 0, 0));
            }

            pendingEndingPairs.clear();
        }

        /**
         * A range the trill rule refuses is dropped with a log line, not reported to the user.
         */
        private void createTrillsFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingTrillPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair.anchor(), pair.end(), line.elementCount(), "trill");

                switch (Trill.spanning(line, new ElementRange(pair.anchor(), pair.end()))) {
                    case Spanning.Spanned<Trill>(var trill, _) -> {
                        trill.setUserYOffsetSs(pair.yOffsetSs());
                        trill.addTo();
                    }
                    case Spanning.Refused<Trill> refused -> LOG.warn(
                        "Corrupt document: dropping trill {}..{}: {}",
                        pair.anchor(), pair.end(), Strings.get(refused.reasonKey()));
                }
            }

            pendingTrillPairs.clear();
        }

        /**
         * Called before the element is added to the line; {@code elementIndex} is the index it
         * will occupy.
         */
        private void accumulateLegacyTrillFlag(int elementIndex) {
            if (!pendingTrillPairs.isEmpty()) {
                var last = pendingTrillPairs.getLast();

                if (last.end() == elementIndex - 1) {
                    pendingTrillPairs.set(
                        pendingTrillPairs.size() - 1, new TrillPair(last.anchor(), elementIndex, last.yOffsetSs()));
                    return;
                }
            }

            pendingTrillPairs.add(new TrillPair(elementIndex, elementIndex, 0));
        }

        private void parseTrillPairs(String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var data = segment.data();
                var yOffsetSs = (data == null) ? 0 : Integer.parseInt(data);
                pendingTrillPairs.add(new TrillPair(segment.a(), segment.b(), yOffsetSs));
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
