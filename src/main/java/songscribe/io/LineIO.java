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

import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.PendingBeam;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingHairpin;
import songscribe.dom.PendingMarks;
import songscribe.dom.PendingTie;
import songscribe.dom.PendingTrill;
import songscribe.dom.PendingTuplet;
import songscribe.dom.Song;
import songscribe.dom.TupletGrade;
import songscribe.dom.UserOffset;
import songscribe.util.StringUtils;

public final class LineIO {

    private static final String XML_LINE = "line";
    static final String XML_KEYS = "keys";
    static final String XML_KEYTYPE = "keytype";
    static final String XML_NOTE_DIST_CHANGE = "notedistchange";
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

    /**
     * Each list of pending marks is complete once parsing is.
     */
    public static class LineReader {

        private static final Logger LOG = LoggerFactory.getLogger(LineReader.class);

        private final Song song;

        @Nullable
        private Line line = null;

        private final LeafTagText leaf = new LeafTagText();

        @org.jetbrains.annotations.Nullable
        private StaffElementIO.StaffElementReader noteReader = null;

        private final ReaderChanges changes;

        @Nullable
        private Where where = null;

        private final List<int[]> pendingEndingPairs = new ArrayList<>();

        private final List<PendingEnding> pendingEndingData = new ArrayList<>();

        private final List<PendingHairpin> pendingHairpinData = new ArrayList<>();

        private record TrillPair(int anchor, int end) {}

        private final List<TrillPair> pendingTrillPairs = new ArrayList<>();

        private final List<PendingTrill> pendingTrillData = new ArrayList<>();

        private final List<Integer> pendingTieAnchors = new ArrayList<>();

        private final List<PendingTie> pendingTieData = new ArrayList<>();

        private record HairpinEntry(int anchor, int end) {}

        private final EnumMap<Hairpin.Kind, List<HairpinEntry>> pendingHairpinEntries =
            new EnumMap<>(Hairpin.Kind.class);

        // The <keys> and <keytype> tags arrive as two separate elements, and neither half is a
        // key on its own, so they are held until </line> and turned into one Key there.
        private int parsedKeyAccidentalCount = 0;
        private @Nullable LegacyKeyType parsedKeyType = null;

        /**
         * @param changes the changes of the read this reader is part of
         */
        LineReader(Song song, ReaderChanges changes) {
            this.song = song;
            this.changes = changes;
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

        private final List<PendingBeam> pendingBeamData = new ArrayList<>();

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

        private void createBeamsFromPending(Line line) throws SAXException {
            for (var pair : pendingBeamPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair.anchor(), pair.end(), line.elementCount(), "beam");

                pendingBeamData.add(new PendingBeam(line.getElement(pair.anchor()), line.getElement(pair.end())));
            }

            pendingBeamPairs.clear();
        }

        private void createTiesFromPendingAnchors(Line line) throws SAXException {
            for (var anchorIndex : pendingTieAnchors) {
                DocumentValidation.requireIndexInRange(LOG, anchorIndex, anchorIndex, line.elementCount(), "tie");

                pendingTieData.add(new PendingTie(line.getElement(anchorIndex)));
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
                        kind, line.getElement(entry.anchor()), line.getElement(entry.end()),
                        UserOffset.NONE));
                }
            }

            pendingHairpinEntries.clear();
        }

        private void parseTiePairs(String str) {
            forEachIndexPair(str, (anchor, _) -> pendingTieAnchors.add(anchor));
        }

        private record TupletSegment(int anchor, int end, int grade) {}

        private final List<TupletSegment> pendingTupletSegments = new ArrayList<>();

        private final List<PendingTuplet> pendingTupletData = new ArrayList<>();

        private void parseTupletData(String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                var grade = TupletGrade.TRIPLET.number();

                var data = segment.data();

                if (data != null) {
                    var gradeText = data.split(",")[0];

                    try {
                        grade = Integer.parseInt(gradeText);
                    } catch (NumberFormatException _) {
                        LOG.warn("Corrupt document: malformed tuplet grade: '{}', using default {}", gradeText, grade);
                    }
                }

                pendingTupletSegments.add(new TupletSegment(segment.a(), segment.b(), grade));
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
                    line.getElement(segment.anchor()), line.getElement(segment.end()), grade, null, 0));
            }

            pendingTupletSegments.clear();
        }

        PendingMarks pendingMarks() {
            return new PendingMarks(
                pendingTupletData, pendingEndingData, pendingHairpinData, pendingBeamData, pendingTrillData,
                pendingTieData);
        }

        private void parseHairpinEntries(Hairpin.Kind kind, String str) {
            var entries = pendingHairpinEntries.computeIfAbsent(kind, _ -> new ArrayList<>());

            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                entries.add(new HairpinEntry(segment.a(), segment.b()));
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
                    noteReader = new StaffElementIO.StaffElementReader(changes);
                    parsedKeyAccidentalCount = 0;
                    parsedKeyType = null;
                }
            } else if (where == Where.NOTES && noteReader != null) {
                noteReader.startElement11(qName, attributes);
            } else if (qName.equals(XML_NOTES)) {
                where = Where.NOTES;
            } else {
                leaf.open(qName);
                return;
            }

            leaf.reset();
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

                    if (noteReader.droppedLyric()) {
                        changes.lyricDropped(line);
                    }

                    line.addElement(n);
                }
            } else if (where == Where.LINE) {
                if (qName.equals(XML_LINE)) {
                    where = null;
                    applyParsedKey(line);
                    createTiesFromPendingAnchors(line);
                    createEndingsFromPendingPairs(line);
                    createTrillsFromPendingPairs(line);
                    recordPendingHairpins(line);
                    createTupletsFromPending(line);
                    createBeamsFromPending(line);
                    return line;
                }

                leaf.close(qName, (tag, str) -> {
                    switch (tag) {
                        case XML_KEYS -> parsedKeyAccidentalCount = DocumentValidation.parseIntOrThrow(LOG, XML_KEYS, str);
                        case XML_KEYTYPE -> parsedKeyType = LegacyKeyType.parse(LOG, str);
                        // Legacy line-compression ratio - nothing reads it; ignore for backwards compatibility
                        case XML_NOTE_DIST_CHANGE -> {
                        }
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
                });
            }

            leaf.reset();
            return null;
        }

        public void characters(char[] ch, int start, int length) {
            if (where == Where.NOTES && noteReader != null) {
                noteReader.characters(ch, start, length);
            } else {
                leaf.append(ch, start, length);
            }
        }

        private void createEndingsFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingEndingPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair[0], pair[1], line.elementCount(), "ending");

                pendingEndingData.add(new PendingEnding(line.getElement(pair[0]), line.getElement(pair[1]), UserOffset.NONE));
            }

            pendingEndingPairs.clear();
        }

        private void createTrillsFromPendingPairs(Line line) throws SAXException {
            for (var pair : pendingTrillPairs) {
                DocumentValidation.requireIndexInRange(LOG, pair.anchor(), pair.end(), line.elementCount(), "trill");

                pendingTrillData.add(new PendingTrill(
                    line.getElement(pair.anchor()), line.getElement(pair.end()), 0));
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
                        pendingTrillPairs.size() - 1, new TrillPair(last.anchor(), elementIndex));
                    return;
                }
            }

            pendingTrillPairs.add(new TrillPair(elementIndex, elementIndex));
        }

        private void parseTrillPairs(String str) {
            forEachSegment(str, (begin, end) -> {
                var segment = parseSegmentAB(str, begin, end);
                pendingTrillPairs.add(new TrillPair(segment.a(), segment.b()));
            });
        }

        @Nullable
        Line getLine() {
            return line;
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
