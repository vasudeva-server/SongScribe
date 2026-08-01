/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;
import songscribe.message.MessageCenter;

@SuppressWarnings({ "PackageVisibleInnerClass", "OverlyBroadThrowsClause" })
class LineIOTest extends UnitTest {

    private MockedStatic<MessageCenter> messageCenterMock;
    private Song song;
    private Line line;

    @BeforeEach
    void setUp() throws Exception {
        messageCenterMock = mockStatic(MessageCenter.class);
        song = new Song();
        line = song.getLine(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        messageCenterMock.close();
    }

    // -- Helpers --

    private static String writeLine(Line l) {
        var sw = new StringWriter();
        LineIO.writeLine(l, new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Drives a LineReader through a minimal line parse (no notes).
     * Feeds the text content of {@code tagName} and returns the fully closed Line.
     * Uses a mock song with mutation tracking suspended so direct field writes succeed.
     */
    @Nullable
    private static Line parseLineTag(String tagName, String content) throws SAXException {
        var emptyAttrs = new AttributesImpl();
        var reader = new LineIO.LineReader(minimalSongMock());
        reader.startElement11("line", emptyAttrs);
        reader.startElement11(tagName, emptyAttrs);
        reader.characters(content.toCharArray(), 0, content.length());
        reader.endElement11(tagName);
        return reader.endElement11("line");
    }

    /** Same as {@link #parseLineTag} but returns the {@link LegacyLineOffsets} captured by the reader. */
    @Nullable
    private static LegacyLineOffsets parseLegacyOffsets(String tagName, String content) throws SAXException {
        var emptyAttrs = new AttributesImpl();
        var reader = new LineIO.LineReader(minimalSongMock());
        reader.startElement11("line", emptyAttrs);
        reader.startElement11(tagName, emptyAttrs);
        reader.characters(content.toCharArray(), 0, content.length());
        reader.endElement11(tagName);
        return reader.endElement11("line") == null ? null : reader.getLegacyOffsets();
    }

    // -------------------------------------------------------------------------
    // WriteLineDeltaKeySig — row 1
    // -------------------------------------------------------------------------

    @Nested
    class WriteLineDeltaKeySig {

        @Test
        void testDifferingKeyWritesKeysAndKeytypeTags() throws Exception {
            // Line key differs from song default (5 flats) → tags must appear
            song.withModification(() -> {
                line.setKeyAccidentalCount(2);
                line.setKeyType(KeyType.SHARPS);
            });

            var output = writeLine(line);

            assertThat(output).contains("<" + LineIO.XML_KEYS + ">2</" + LineIO.XML_KEYS + ">");
            assertThat(output).contains("<" + LineIO.XML_KEYTYPE + ">SHARPS</" + LineIO.XML_KEYTYPE + ">");
        }

        @Test
        void testMatchingSongDefaultOmitsKeysTags() throws Exception {
            // Line key matches song default → tags must be absent
            var output = writeLine(line);

            assertThat(output).doesNotContain("<" + LineIO.XML_KEYS + ">");
            assertThat(output).doesNotContain("<" + LineIO.XML_KEYTYPE + ">");
        }
    }

    // -------------------------------------------------------------------------
    // WriteLineNoteDist — row 2
    // -------------------------------------------------------------------------

    @Nested
    class WriteLineNoteDist {

        private static final float RATIO_ONE_AND_HALF = 1.5f;

        @Test
        void testNonUnitRatioWritesNoteDist() throws Exception {
            song.withModification(() -> line.changeElementSpacingRatio(RATIO_ONE_AND_HALF));

            var output = writeLine(line);

            assertThat(output).contains("<" + LineIO.XML_NOTE_DIST_CHANGE + ">");
        }

        @Test
        void testUnitRatioOmitsNoteDist() throws Exception {
            // elementSpacingRatio defaults to 1.0 — no changeElementSpacingRatio call
            var output = writeLine(line);

            assertThat(output).doesNotContain("<" + LineIO.XML_NOTE_DIST_CHANGE + ">");
        }
    }

    // -------------------------------------------------------------------------
    // WriteLineLyricsYPos — row 3
    // -------------------------------------------------------------------------

    @Nested
    class WriteLineLyricsYPos {

        @Test
        void testAlwaysWritesLyricsYPosTag() throws Exception {
            var output = writeLine(line);

            assertThat(output).contains("<" + LineIO.XML_LYRICS_YPOS + ">");
        }
    }

    // -------------------------------------------------------------------------
    // WriteLineLegacyYPosTags — row 4
    // -------------------------------------------------------------------------

    @Nested
    class WriteLineLegacyYPosTags {

        @Test
        void testLegacyYPosTagsAbsentInNewDocuments() throws Exception {
            var output = writeLine(line);

            assertThat(output).doesNotContain("<" + LineIO.XML_TEMPO_CHANGE_YPOS + ">");
            assertThat(output).doesNotContain("<" + LineIO.XML_BEAT_CHANGE_YPOS + ">");
            assertThat(output).doesNotContain("<" + LineIO.XML_FSENDING_YPOS + ">");
            assertThat(output).doesNotContain("<" + LineIO.XML_TRILL_YPOS + ">");
        }
    }

    // -------------------------------------------------------------------------
    // BeamsToString — row 5
    // -------------------------------------------------------------------------

    @Nested
    class BeamsToString {

        @Test
        void testKnownBeamListProducesAnchorEndPairs() throws Exception {
            var elements = lineWith(
                ElementType.QUAVER,
                ElementType.QUAVER,
                ElementType.QUAVER,
                ElementType.QUAVER
            );

            var e0 = elements.getElement(0);
            var e1 = elements.getElement(1);
            var e2 = elements.getElement(2);
            var e3 = elements.getElement(3);

            var beams = List.of(
                new Beam(e0, e1),
                new Beam(e2, e3)
            );

            // Wire parent line so getAnchorElementIndex/getEndElementIndex resolve correctly
            for (var beam : beams) {
                elements.addRangeElement(beam);
            }

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Beam.class));

            assertThat(result).isEqualTo("0,1;2,3;");
        }
    }

    // -------------------------------------------------------------------------
    // TrillsToString — row 6
    // -------------------------------------------------------------------------

    @Nested
    class TrillsToString {

        @Test
        void testZeroYPosOmitsYPos() throws Exception {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var trill = new Trill(elements.getElement(0), elements.getElement(1));
            elements.addRangeElement(trill);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Trill.class));

            // yPositionSs == 0 → omitted; format: anchor,end;
            assertThat(result).isEqualTo("0,1;");
        }

        @Test
        void testNonZeroYPosIncludesYPos() throws Exception {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var trill = new Trill(elements.getElement(0), elements.getElement(1));
            final int Y_POS = 3;
            trill.setYPositionSs(Y_POS);
            elements.addRangeElement(trill);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Trill.class));

            // yPositionSs != 0 → included; format: anchor,end,yPos;
            assertThat(result).isEqualTo("0,1,3;");
        }
    }

    // -------------------------------------------------------------------------
    // TupletsToString — row 7
    // -------------------------------------------------------------------------

    @Nested
    class TupletsToString {

        private static final int TRIPLET_GRADE = 3;

        /** A triplet occupies the time of two notes of its written value. */
        private static final int TRIPLET_NORMAL_NOTES = 2;

        private static final int NO_DOTS = 0;

        @Test
        void testZeroVertPosOmitsVertPos() throws Exception {
            var elements = lineWith(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );

            var tuplet = new Tuplet(elements.getElement(0), elements.getElement(2), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            elements.addTuplet(tuplet);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Tuplet.class));

            // verticalPositionSs == 0 → omitted; format: anchor,end,grade;
            assertThat(result).isEqualTo("0,2,3;");
        }

        @Test
        void testNonZeroVertPosIncludesVertPos() throws Exception {
            var elements = lineWith(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );

            var tuplet = new Tuplet(elements.getElement(0), elements.getElement(2), TRIPLET_GRADE,
                TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            final int VERT_POS = 4;
            tuplet.setVerticalPositionSs(VERT_POS);
            elements.addTuplet(tuplet);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Tuplet.class));

            // verticalPositionSs != 0 → included; format: anchor,end,grade,vertPos;
            assertThat(result).isEqualTo("0,2,3,4;");
        }
    }

    // -------------------------------------------------------------------------
    // HairpinsToString — row 8
    // -------------------------------------------------------------------------

    @Nested
    class HairpinsToString {

        @Test
        void testAllShiftsZeroOmitsShifts() throws Exception {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var crescendo = new Crescendo(elements.getElement(0), elements.getElement(1));
            elements.addRangeElement(crescendo);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Crescendo.class));

            assertThat(result).isEqualTo("0,1;");
        }

        @Test
        void testAnyNonZeroShiftIncludesAllShifts() throws Exception {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var diminuendo = new Diminuendo(elements.getElement(0), elements.getElement(1));
            final double X1 = 1.5;
            final double X2 = 0.0;
            final double Y = -2.0;
            diminuendo.setX1ShiftSs(X1);
            diminuendo.setX2ShiftSs(X2);
            diminuendo.setYShiftSs(Y);
            elements.addRangeElement(diminuendo);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Diminuendo.class));

            // All three shift fields written when any is non-zero
            assertThat(result).isEqualTo("0,1," + X1 + "," + X2 + "," + Y + ";");
        }
    }

    // -------------------------------------------------------------------------
    // EndingsToString — row 9
    // -------------------------------------------------------------------------

    @Nested
    class EndingsToString {

        @Test
        void testProducesAnchorEndPair() throws Exception {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var ending = new Ending(elements.getElement(0), elements.getElement(1));
            elements.addRangeElement(ending);

            var result = LineIO.rangeElementsToString(elements.findRangeElements(Ending.class));

            // Serialized as anchor,end; pairs only.
            assertThat(result).isEqualTo("0,1;");
        }
    }

    // -------------------------------------------------------------------------
    // ForEachSegment — row 10
    // -------------------------------------------------------------------------

    @Nested
    class ForEachSegment {

        @Test
        void testEmptyStringYieldsZeroIterations() throws Exception {
            var count = new int[]{0};
            LineIO.forEachSegment("", (begin, end) -> count[0]++);
            assertThat(count[0]).isEqualTo(0);
        }

        @Test
        void testSingleSegmentYieldsOneIteration() throws Exception {
            var segments = new ArrayList<String>();
            LineIO.forEachSegment("0,1;", (begin, end) -> segments.add("0,1;".substring(begin, end)));
            assertThat(segments).containsExactly("0,1");
        }

        @Test
        void testMultipleSegmentsYieldsOneIterationEach() throws Exception {
            var segments = new ArrayList<String>();
            var input = "0,1;2,3;4,5;";
            LineIO.forEachSegment(input, (begin, end) -> segments.add(input.substring(begin, end)));
            assertThat(segments).containsExactly("0,1", "2,3", "4,5");
        }
    }

    // -------------------------------------------------------------------------
    // LineReaderStateMachine — row 11
    // -------------------------------------------------------------------------

    @Nested
    class LineReaderStateMachine {

        @Test
        void testLineTagCreatesLineAndSetsWhereLine() throws Exception {
            var reader = new LineIO.LineReader(minimalSongMock());
            assertThat(reader.getLine()).isNull();
            assertThat(reader.getWhere()).isNull();

            reader.startElement11("line", new AttributesImpl());

            assertThat(reader.getLine()).isNotNull();
            assertThat(reader.getWhere()).isEqualTo(LineIO.LineReader.Where.LINE);
        }

        @Test
        void testNotesTagTransitionsToWhereNotes() throws Exception {
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", new AttributesImpl());
            assertThat(reader.getWhere()).isEqualTo(LineIO.LineReader.Where.LINE);

            reader.startElement11("notes", new AttributesImpl());

            assertThat(reader.getWhere()).isEqualTo(LineIO.LineReader.Where.NOTES);
        }

        @Test
        void testUnknownTagSetsLastTag() throws Exception {
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", new AttributesImpl());
            assertThat(reader.getLastTag()).isNull();

            reader.startElement11("keys", new AttributesImpl());

            assertThat(reader.getLastTag()).isEqualTo("keys");
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11Keys — row 12
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11Keys {

        private static final int KEY_COUNT = 5;

        @Test
        void testKeysTagSetsKeyAccidentalCount() throws Exception {
            var parsedLine = parseLineTag(LineIO.XML_KEYS, String.valueOf(KEY_COUNT));

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.getKeyAccidentalCount()).isEqualTo(KEY_COUNT);
        }

        @Test
        void testNonNumericKeysThrowsSAXException() throws Exception {
            // Non-numeric <keys> value → parseIntOrThrow → SAXException with "malformed" and value
            var badValue = "abc";
            var emptyAttrs = new AttributesImpl();
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", emptyAttrs);
            reader.startElement11(LineIO.XML_KEYS, emptyAttrs);
            reader.characters(badValue.toCharArray(), 0, badValue.length());

            assertThatThrownBy(() -> reader.endElement11(LineIO.XML_KEYS))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("malformed")
                .hasMessageContaining(badValue);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11Keytype — row 13
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11Keytype {

        @Test
        void testKeytypeTagSetsKeyType() throws Exception {
            var parsedLine = parseLineTag(LineIO.XML_KEYTYPE, KeyType.FLATS.name());

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.getKeyType()).isEqualTo(KeyType.FLATS);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11NoteDist — row 14
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11NoteDist {

        private static final float SPACING_RATIO = 1.25f;

        @Test
        void testNotedistchangeTagSetsElementSpacingRatio() throws Exception {
            var parsedLine = parseLineTag(LineIO.XML_NOTE_DIST_CHANGE, String.valueOf(SPACING_RATIO));

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.getElementSpacingRatio()).isEqualTo(SPACING_RATIO);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11LyricsYPos — row 20
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11LyricsYPos {

        private static final double LYRICS_YPOS = 5.0;

        @Test
        void testLyricsyposTagSetsLyricsYPosSs() throws Exception {
            var parsedLine = parseLineTag(LineIO.XML_LYRICS_YPOS, String.valueOf(LYRICS_YPOS));

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.getLyricsYPosSs()).isEqualTo(LYRICS_YPOS);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11LegacyYPosTags — row 21
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11LegacyYPosTags {

        private static final int YPOS_PX = 42;

        @Test
        void testBeatchangeyposTagSetsBeatChangeYPosPx() throws Exception {
            var offsets = parseLegacyOffsets(LineIO.XML_BEAT_CHANGE_YPOS, String.valueOf(YPOS_PX));

            assertThat(offsets).isNotNull();

            assertThat(offsets.beatChangeYPosPx()).isEqualTo(YPOS_PX);
        }

        @Test
        void testFsendingyposTagSetsFirstSecondEndingYPosPx() throws Exception {
            var offsets = parseLegacyOffsets(LineIO.XML_FSENDING_YPOS, String.valueOf(YPOS_PX));

            assertThat(offsets).isNotNull();

            assertThat(offsets.firstSecondEndingYPosPx()).isEqualTo(YPOS_PX);
        }

        @Test
        void testTempochangeyposTagSetsTempoChangeYPosPx() throws Exception {
            var offsets = parseLegacyOffsets(LineIO.XML_TEMPO_CHANGE_YPOS, String.valueOf(YPOS_PX));

            assertThat(offsets).isNotNull();

            assertThat(offsets.tempoChangeYPosPx()).isEqualTo(YPOS_PX);
        }

        @Test
        void testTrillyposTagSetsTrillYPosPx() throws Exception {
            var offsets = parseLegacyOffsets(LineIO.XML_TRILL_YPOS, String.valueOf(YPOS_PX));

            assertThat(offsets).isNotNull();

            assertThat(offsets.trillYPosPx()).isEqualTo(YPOS_PX);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11SlursIgnored — row 22
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11SlursIgnored {

        @Test
        void testSlursTagSilentlyIgnored() throws Exception {
            // <slurs> is a removed feature; the parser must silently ignore it
            var parsedLine = parseLineTag("slurs", "0,1;");

            // No exception thrown and line was returned normally
            assertThat(parsedLine).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // BeamRoundTrip — row 23
    // -------------------------------------------------------------------------

    @Nested
    class BeamRoundTrip {

        @Test
        void testBeamRoundTripPreservesAnchorAndEnd() throws Exception {
            var elements = lineWith(ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER);
            var beam = new Beam(elements.getElement(0), elements.getElement(2));
            elements.addRangeElement(beam);

            var serialized = LineIO.rangeElementsToString(elements.findRangeElements(Beam.class));

            // Feed into a reader with matching notes to reconstruct
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_BEAMINGS, serialized);
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var foundBeam = parsedLine.findBeamAt(0);
            assertThat(foundBeam).isNotNull();

            assertThat(foundBeam.getAnchorElementIndex()).isEqualTo(0);
            assertThat(foundBeam.getEndElementIndex()).isEqualTo(2);
        }
    }

    // -------------------------------------------------------------------------
    // CreateBeamsFromPendingOutOfRange — row 24
    // -------------------------------------------------------------------------

    @Nested
    class CreateBeamsFromPendingOutOfRange {

        @Test
        void testAnchorBelowZeroThrowsSAXException() throws Exception {
            // anchor < 0 → SAXException (previously silently skipped)
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_BEAMINGS, "-1,1;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("beam index out of range");
        }

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_BEAMINGS, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("beam index out of range");
        }

        @Test
        void testCrossedPairThrowsSAXException() throws Exception {
            // anchor > end → SAXException
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_BEAMINGS, "1,0;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("beam index out of range");
        }
    }

    // -------------------------------------------------------------------------
    // CreateTiesFromPendingPairsOutOfRange — row 26
    // -------------------------------------------------------------------------

    @Nested
    class CreateTiesFromPendingPairsOutOfRange {

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException (bounds guard added in Phase 1)
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_TIES, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tie index out of range");
        }

        @Test
        void testNegativeAnchorThrowsSAXException() throws Exception {
            // anchor < 0 → SAXException
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_TIES, "-1,1;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tie index out of range");
        }

        @Test
        void testCrossedPairThrowsSAXException() throws Exception {
            // anchor > end → SAXException
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_TIES, "1,0;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tie index out of range");
        }

        @Test
        void testValidTiePairLoadsSuccessfully() throws Exception {
            // Valid pair within bounds → Tie is created, no exception
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_TIES, "0,1;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.findRangeElements(Tie.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // TupletLegacyTripletsDefaultGrade — row 27
    // -------------------------------------------------------------------------

    @Nested
    class TupletLegacyTripletsDefaultGrade {

        private static final int EXPECTED_GRADE = 3;

        @Test
        void testLegacyTripletsTagDefaultsGradeToThree() throws Exception {
            // <triplets> format omits grade; parseTupletData defaults to 3
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TRIPLETS, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var tuplets = parsedLine.findRangeElements(Tuplet.class);
            assertThat(tuplets).hasSize(1);
            assertThat(tuplets.getFirst().getGrade()).isEqualTo(EXPECTED_GRADE);
        }
    }

    // -------------------------------------------------------------------------
    // TupletExplicitNonThreeGradeRoundTrip — row 28
    // -------------------------------------------------------------------------

    @Nested
    class TupletExplicitNonThreeGradeRoundTrip {

        private static final int QUINTUPLET_GRADE = 5;

        @Test
        void testExplicitGradeFiveRoundTrip() throws Exception {
            // Serialized: <tuplets>0,4,5;</tuplets> → parsed grade must be 5
            var reader = buildReaderWithNotes(
                ElementType.QUAVER,
                ElementType.QUAVER,
                ElementType.QUAVER,
                ElementType.QUAVER,
                ElementType.QUAVER
            );
            feedTag(reader, LineIO.XML_TUPLETS, "0,4,5;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var tuplets = parsedLine.findRangeElements(Tuplet.class);
            assertThat(tuplets).hasSize(1);
            assertThat(tuplets.getFirst().getGrade()).isEqualTo(QUINTUPLET_GRADE);
        }
    }

    // -------------------------------------------------------------------------
    // TupletVerticalPositionRoundTrip — row 29
    // -------------------------------------------------------------------------

    @Nested
    class TupletVerticalPositionRoundTrip {

        private static final int TRIPLET_GRADE = 3;
        private static final int VERT_POS = 7;

        @Test
        void testNonZeroVertPosParsedCorrectly() throws Exception {
            // Serialized: <tuplets>0,2,3,7;</tuplets> → verticalPositionSs must be 7
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TUPLETS, "0,2," + TRIPLET_GRADE + "," + VERT_POS + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var tuplets = parsedLine.findRangeElements(Tuplet.class);
            assertThat(tuplets).hasSize(1);
            assertThat(tuplets.getFirst().getVerticalPositionSs()).isEqualTo(VERT_POS);
        }
    }

    // -------------------------------------------------------------------------
    // CreateTupletsFromPendingOutOfRange — row 30
    // -------------------------------------------------------------------------

    @Nested
    class CreateTupletsFromPendingOutOfRange {

        @Test
        void testAnchorBelowZeroThrowsSAXException() throws Exception {
            // anchor < 0 → SAXException (previously silently skipped)
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TUPLETS, "-1,1,3;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tuplet index out of range");
        }

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TUPLETS, "0,5,3;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tuplet index out of range");
        }

        @Test
        void testCrossedPairThrowsSAXException() throws Exception {
            // anchor > end → SAXException
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TUPLETS, "1,0,3;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tuplet index out of range");
        }

        @Test
        void testSingleNonRestNoteThrowsSAXException() throws Exception {
            // anchor == end (a lone note) → fewer than two non-rest notes spanned
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET_REST, ElementType.CROTCHET_REST);
            feedTag(reader, LineIO.XML_TUPLETS, "0,0,3;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tuplet does not span at least two non-rest notes");
        }

        @Test
        void testNoteRestRestThrowsSAXException() throws Exception {
            // one non-rest note flanked by rests → still only one non-rest note in the span
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET_REST, ElementType.CROTCHET_REST);
            feedTag(reader, LineIO.XML_TUPLETS, "0,2,3;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("tuplet does not span at least two non-rest notes");
        }
    }

    // -------------------------------------------------------------------------
    // ParseTupletDataNfeDefaults — row 31
    // -------------------------------------------------------------------------

    @Nested
    class ParseTupletDataNfeDefaults {

        private static final int DEFAULT_GRADE = 3;
        private static final int DEFAULT_VERT_POS = 0;

        /**
         * Attaches a ListAppender to the LineIO.LineReader logger and returns it.
         * The caller is responsible for detaching after the test.
         */
        private static ListAppender<ILoggingEvent> attachListAppender() throws Exception {
            var logger = (Logger) LoggerFactory.getLogger(LineIO.LineReader.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);
            return appender;
        }

        private static void detachAppender(ListAppender<ILoggingEvent> appender) {
            var logger = (Logger) LoggerFactory.getLogger(LineIO.LineReader.class);
            logger.detachAppender(appender);
        }

        @Test
        void testNonNumericGradeDefaultsToThreeAndLogsWarn() throws Exception {
            // Non-numeric grade token → defaults to 3 and logs WARN (W-1)
            var appender = attachListAppender();
            try {
                var reader = buildReaderWithNotes(
                    ElementType.CROTCHET,
                    ElementType.CROTCHET,
                    ElementType.CROTCHET
                );
                feedTag(reader, LineIO.XML_TUPLETS, "0,2,bad;");
                var parsedLine = reader.endElement11("line");

                assertThat(parsedLine).isNotNull();

                var tuplets = parsedLine.findRangeElements(Tuplet.class);
                assertThat(tuplets).hasSize(1);
                assertThat(tuplets.getFirst().getGrade()).isEqualTo(DEFAULT_GRADE);

                // WARN must have been logged for the malformed grade
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("grade"));
            } finally {
                detachAppender(appender);
            }
        }

        @Test
        void testNonNumericVertPosDefaultsToZeroAndLogsWarn() throws Exception {
            // Non-numeric vertPos token → defaults to 0 and logs WARN (W-1)
            var appender = attachListAppender();
            try {
                var reader = buildReaderWithNotes(
                    ElementType.CROTCHET,
                    ElementType.CROTCHET,
                    ElementType.CROTCHET
                );
                feedTag(reader, LineIO.XML_TUPLETS, "0,2,3,bad;");
                var parsedLine = reader.endElement11("line");

                assertThat(parsedLine).isNotNull();

                var tuplets = parsedLine.findRangeElements(Tuplet.class);
                assertThat(tuplets).hasSize(1);
                assertThat(tuplets.getFirst().getVerticalPositionSs()).isEqualTo(DEFAULT_VERT_POS);

                // WARN must have been logged for the malformed verticalPositionSs
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("verticalPositionSs"));
            } finally {
                detachAppender(appender);
            }
        }
    }

    // -------------------------------------------------------------------------
    // CrescendoRoundTripAllZeroShifts — row 32
    // -------------------------------------------------------------------------

    @Nested
    class CrescendoRoundTripAllZeroShifts {

        @Test
        void testAllZeroShiftsRoundTrip() throws Exception {
            // Serialized: <crescendo>0,2;</crescendo> → all shifts 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_CRESCENDO, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var crescendos = parsedLine.findRangeElements(Crescendo.class);
            assertThat(crescendos).hasSize(1);
            var c = crescendos.getFirst();
            assertThat(c.getX1ShiftSs()).isEqualTo(0.0);
            assertThat(c.getX2ShiftSs()).isEqualTo(0.0);
            assertThat(c.getYShiftSs()).isEqualTo(0.0);
        }
    }

    // -------------------------------------------------------------------------
    // CrescendoRoundTripExplicitShifts — row 33
    // -------------------------------------------------------------------------

    @Nested
    class CrescendoRoundTripExplicitShifts {

        private static final double X1_SHIFT = 1.5;
        private static final double X2_SHIFT = -0.5;
        private static final double Y_SHIFT = 0.25;

        @Test
        void testExplicitShiftsPreservedInRoundTrip() throws Exception {
            // Serialized: <crescendo>0,2,1.5,-0.5,0.25;</crescendo>
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_CRESCENDO, "0,2," + X1_SHIFT + "," + X2_SHIFT + "," + Y_SHIFT + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var crescendos = parsedLine.findRangeElements(Crescendo.class);
            assertThat(crescendos).hasSize(1);
            var c = crescendos.getFirst();
            assertThat(c.getX1ShiftSs()).isEqualTo(X1_SHIFT);
            assertThat(c.getX2ShiftSs()).isEqualTo(X2_SHIFT);
            assertThat(c.getYShiftSs()).isEqualTo(Y_SHIFT);
        }
    }

    // -------------------------------------------------------------------------
    // DiminuendoRoundTrip — row 34
    // -------------------------------------------------------------------------

    @Nested
    class DiminuendoRoundTrip {

        private static final double X1_SHIFT = 0.5;
        private static final double X2_SHIFT = -1.0;
        private static final double Y_SHIFT = 0.75;

        @Test
        void testAllZeroShiftsRoundTrip() throws Exception {
            // Serialized: <diminuendo>0,2;</diminuendo> → all shifts 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var diminuendos = parsedLine.findRangeElements(Diminuendo.class);
            assertThat(diminuendos).hasSize(1);
            var d = diminuendos.getFirst();
            assertThat(d.getX1ShiftSs()).isEqualTo(0.0);
            assertThat(d.getX2ShiftSs()).isEqualTo(0.0);
            assertThat(d.getYShiftSs()).isEqualTo(0.0);
        }

        @Test
        void testExplicitShiftsPreservedInRoundTrip() throws Exception {
            // Serialized: <diminuendo>0,2,0.5,-1.0,0.75;</diminuendo>
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,2," + X1_SHIFT + "," + X2_SHIFT + "," + Y_SHIFT + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var diminuendos = parsedLine.findRangeElements(Diminuendo.class);
            assertThat(diminuendos).hasSize(1);
            var d = diminuendos.getFirst();
            assertThat(d.getX1ShiftSs()).isEqualTo(X1_SHIFT);
            assertThat(d.getX2ShiftSs()).isEqualTo(X2_SHIFT);
            assertThat(d.getYShiftSs()).isEqualTo(Y_SHIFT);
        }
    }

    // -------------------------------------------------------------------------
    // ParseHairpinPairsPartialShift — row 35
    // -------------------------------------------------------------------------

    @Nested
    class ParseHairpinPairsPartialShift {

        @Test
        void testPartialShiftDataKeepsAllShiftsZero() throws Exception {
            // <crescendo>0,2,1.5;</crescendo> has only 1 shift part instead of 3
            // → parts.length < 3, so all shifts remain 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_CRESCENDO, "0,2,1.5;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var crescendos = parsedLine.findRangeElements(Crescendo.class);
            assertThat(crescendos).hasSize(1);
            var c = crescendos.getFirst();
            assertThat(c.getX1ShiftSs()).isEqualTo(0.0);
            assertThat(c.getX2ShiftSs()).isEqualTo(0.0);
            assertThat(c.getYShiftSs()).isEqualTo(0.0);
        }

        @Test
        void testMalformedShiftValuesLoadsWithZeroShiftsAndLogsWarn() throws Exception {
            // Non-numeric shift values → WARN logged, all shifts remain 0 (W-2)
            var logger = (Logger) LoggerFactory.getLogger(LineIO.LineReader.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);
            try {
                var reader = buildReaderWithNotes(
                    ElementType.CROTCHET,
                    ElementType.CROTCHET,
                    ElementType.CROTCHET
                );
                feedTag(reader, LineIO.XML_CRESCENDO, "0,2,bad,worse,terrible;");
                var parsedLine = reader.endElement11("line");

                assertThat(parsedLine).isNotNull();

                var crescendos = parsedLine.findRangeElements(Crescendo.class);
                assertThat(crescendos).hasSize(1);
                var c = crescendos.getFirst();
                assertThat(c.getX1ShiftSs()).isEqualTo(0.0);
                assertThat(c.getX2ShiftSs()).isEqualTo(0.0);
                assertThat(c.getYShiftSs()).isEqualTo(0.0);

                // WARN must have been logged for the malformed shift values
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("hairpin shift values"));
            } finally {
                logger.detachAppender(appender);
            }
        }
    }

    // -------------------------------------------------------------------------
    // ParseEndingPairsAccumulates — row 37
    // -------------------------------------------------------------------------

    @Nested
    class ParseEndingPairsAccumulates {

        @Test
        void testSecondCallAccumulatesBothBatches() throws Exception {
            // parseEndingPairs accumulates, consistent with all other parse* methods.
            // Two XML_FSENDINGS tags → both batches survive. Each span straddles a
            // REPEAT_RIGHT so it survives the split-less drop guard (issue #306).
            //  idx: 0        1            2        3            4
            //       CROTCHET REPEAT_RIGHT CROTCHET REPEAT_RIGHT CROTCHET
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.REPEAT_RIGHT,
                ElementType.CROTCHET,
                ElementType.REPEAT_RIGHT,
                ElementType.CROTCHET
            );
            // First batch: ending at 0–2 (split REPEAT_RIGHT at 1)
            feedTag(reader, LineIO.XML_FSENDINGS, "0,2;");
            // Second batch: ending at 2–4 (split REPEAT_RIGHT at 3) — accumulates with first
            feedTag(reader, LineIO.XML_FSENDINGS, "2,4;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var endings = parsedLine.findRangeElements(Ending.class);
            // Both batches survive — 2 endings total
            assertThat(endings).hasSize(2);
            assertThat(endings.get(0).getAnchorElementIndex()).isEqualTo(0);
            assertThat(endings.get(0).getEndElementIndex()).isEqualTo(2);
            assertThat(endings.get(1).getAnchorElementIndex()).isEqualTo(2);
            assertThat(endings.get(1).getEndElementIndex()).isEqualTo(4);
        }
    }

    // -------------------------------------------------------------------------
    // TrillRoundTrip — row 38
    // -------------------------------------------------------------------------

    @Nested
    class TrillRoundTrip {

        @Test
        void testZeroYPositionRoundTrip() throws Exception {
            // <trills>0,2;</trills> → yPositionSs == 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TRILLS, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var trills = parsedLine.findRangeElements(Trill.class);
            assertThat(trills).hasSize(1);
            assertThat(trills.getFirst().getAnchorElementIndex()).isEqualTo(0);
            assertThat(trills.getFirst().getEndElementIndex()).isEqualTo(2);
            assertThat(trills.getFirst().getYPositionSs()).isEqualTo(0);
        }

        @Test
        void testNonZeroYPositionRoundTrip() throws Exception {
            // <trills>0,2,5;</trills> → yPositionSs == 5
            final int Y_POS = 5;
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TRILLS, "0,2," + Y_POS + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var trills = parsedLine.findRangeElements(Trill.class);
            assertThat(trills).hasSize(1);
            assertThat(trills.getFirst().getYPositionSs()).isEqualTo(Y_POS);
        }
    }

    // -------------------------------------------------------------------------
    // AccumulateLegacyTrillFlag — row 39
    // -------------------------------------------------------------------------

    @Nested
    class AccumulateLegacyTrillFlag {

        /**
         * Builds a reader with {@code count} notes, marking notes at the given indices
         * with a legacy {@code <trill>} tag. Notes not in {@code trillIndices} get no trill flag.
         */
        private static LineIO.LineReader buildReaderWithTrillNotes(int count, int... trillIndices) throws SAXException {
            var emptyAttrs = new AttributesImpl();
            var trillSet = new HashSet<Integer>();
            for (var idx : trillIndices) {
                trillSet.add(idx);
            }
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", emptyAttrs);
            reader.startElement11("notes", emptyAttrs);
            for (var i = 0; i < count; i++) {
                var noteAttrs = new AttributesImpl();
                noteAttrs.addAttribute("", "type", "type", "CDATA", ElementType.CROTCHET.name());
                reader.startElement11("note", noteAttrs);
                reader.startElement11("staffposition", emptyAttrs);
                reader.characters("0".toCharArray(), 0, 1);
                reader.endElement11("staffposition");
                if (trillSet.contains(i)) {
                    reader.startElement11("trill", emptyAttrs);
                    reader.endElement11("trill");
                }
                reader.endElement11("note");
            }
            reader.endElement11("notes");
            return reader;
        }

        @Test
        void testContiguousIndicesCoalesceIntoOneTrill() throws Exception {
            // Notes at indices 2,3,4 are trill-flagged → single Trill covering [2,4]
            final int NOTE_COUNT = 5;
            var reader = buildReaderWithTrillNotes(NOTE_COUNT, 2, 3, 4);
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var trills = parsedLine.findRangeElements(Trill.class);
            assertThat(trills).hasSize(1);
            assertThat(trills.getFirst().getAnchorElementIndex()).isEqualTo(2);
            assertThat(trills.getFirst().getEndElementIndex()).isEqualTo(4);
        }

        @Test
        void testNonContiguousIndicesProduceTwoTrillPairs() throws Exception {
            // Notes at indices 2 and 4 (non-contiguous) → two Trills: [2,2] and [4,4]
            final int NOTE_COUNT = 5;
            var reader = buildReaderWithTrillNotes(NOTE_COUNT, 2, 4);
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            var trills = parsedLine.findRangeElements(Trill.class);
            assertThat(trills).hasSize(2);
            assertThat(trills.get(0).getAnchorElementIndex()).isEqualTo(2);
            assertThat(trills.get(0).getEndElementIndex()).isEqualTo(2);
            assertThat(trills.get(1).getAnchorElementIndex()).isEqualTo(4);
            assertThat(trills.get(1).getEndElementIndex()).isEqualTo(4);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11FullLineParse — row 40
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11FullLineParse {

        @Test
        void testAllCreateMethodsInvokedOnLineClose() throws Exception {
            // Full start/chars/end sequence with all range element types:
            // beam, tie, tuplet, crescendo, diminuendo, trill, ending.
            // Notes occupy indices 0-4 with valid ranges for all types; a trailing
            // REPEAT_RIGHT(5) + CROTCHET(6) give the ending a split so it survives the
            // split-less drop guard (issue #306).
            final int ELEMENT_COUNT = 7;
            var reader = buildReaderWithNotes(
                ElementType.QUAVER,
                ElementType.QUAVER,
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.REPEAT_RIGHT,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_BEAMINGS, "0,1;");
            feedTag(reader, LineIO.XML_TIES, "0,1;");
            feedTag(reader, LineIO.XML_TUPLETS, "2,4,3;");
            feedTag(reader, LineIO.XML_CRESCENDO, "0,1;");
            feedTag(reader, LineIO.XML_DIMINUENDO, "2,3;");
            feedTag(reader, LineIO.XML_TRILLS, "3,4;");
            // Ending 4–6 straddles the REPEAT_RIGHT(5) split.
            feedTag(reader, LineIO.XML_FSENDINGS, "4,6;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.elementCount()).isEqualTo(ELEMENT_COUNT);
            assertThat(parsedLine.findRangeElements(Beam.class)).hasSize(1);
            assertThat(parsedLine.findRangeElements(Tie.class)).hasSize(1);
            assertThat(parsedLine.findRangeElements(Tuplet.class)).hasSize(1);
            assertThat(parsedLine.findRangeElements(Crescendo.class)).hasSize(1);
            assertThat(parsedLine.findRangeElements(Diminuendo.class)).hasSize(1);
            assertThat(parsedLine.findRangeElements(Trill.class)).hasSize(1);
            assertThat(parsedLine.findRangeElements(Ending.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // CreateEndingsFromPendingPairsOutOfRange — C-2
    // -------------------------------------------------------------------------

    @Nested
    class CreateEndingsFromPendingPairsOutOfRange {

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException (Phase 1 bounds guard)
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_FSENDINGS, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("ending index out of range");
        }

        @Test
        void testNegativeAnchorThrowsSAXException() throws Exception {
            // anchor < 0 → SAXException
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_FSENDINGS, "-1,1;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("ending index out of range");
        }

        @Test
        void testCrossedPairThrowsSAXException() throws Exception {
            // anchor > end → SAXException
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_FSENDINGS, "1,0;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("ending index out of range");
        }

        @Test
        void testValidEndingPairLoadsSuccessfully() throws Exception {
            // Valid pair within bounds, straddling a REPEAT_RIGHT split (issue #306) → Ending
            // is created.
            //  idx: 0        1            2
            //       CROTCHET REPEAT_RIGHT CROTCHET
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET, ElementType.REPEAT_RIGHT, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_FSENDINGS, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.findRangeElements(Ending.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // CreateTrillsFromPendingPairsOutOfRange — C-3
    // -------------------------------------------------------------------------

    @Nested
    class CreateTrillsFromPendingPairsOutOfRange {

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException (Phase 1 bounds guard)
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TRILLS, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("trill index out of range");
        }

        @Test
        void testNegativeAnchorThrowsSAXException() throws Exception {
            // anchor < 0 → SAXException
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TRILLS, "-1,1;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("trill index out of range");
        }

        @Test
        void testCrossedPairThrowsSAXException() throws Exception {
            // anchor > end → SAXException
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TRILLS, "1,0;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("trill index out of range");
        }

        @Test
        void testValidTrillPairLoadsSuccessfully() throws Exception {
            // Valid pair within bounds → Trill is created
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TRILLS, "0,1;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.findRangeElements(Trill.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // CreateCrescendosFromPendingOutOfRange — C-4
    // -------------------------------------------------------------------------

    @Nested
    class CreateCrescendosFromPendingOutOfRange {

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException (Phase 1 bounds guard with double→int cast)
            // The parser reads integer indices; createCrescendosFromPending casts double→int
            // before the bounds check. Index 5 > elementCount(2) so bounds guard fires.
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_CRESCENDO, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("crescendo index out of range");
        }

        @Test
        void testEndAtExactCountBoundaryThrowsSAXException() throws Exception {
            // end == elementCount (2 with 2 elements, 0-based: valid = 0,1) → SAXException
            // The double→int cast: index 2 is stored as double 2.0, cast back to int 2
            // = elementCount, so the bounds check fires.
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_CRESCENDO, "0,2;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("crescendo index out of range");
        }

        @Test
        void testValidCrescendoPairLoadsSuccessfully() throws Exception {
            // Valid pair within bounds → Crescendo is created
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_CRESCENDO, "0,1;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.findRangeElements(Crescendo.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // CreateDiminuendosFromPendingOutOfRange — C-5
    // -------------------------------------------------------------------------

    @Nested
    class CreateDiminuendosFromPendingOutOfRange {

        @Test
        void testEndBeyondCountThrowsSAXException() throws Exception {
            // end >= elementCount → SAXException (Phase 1 bounds guard with double→int cast)
            // The parser reads integer indices; createDiminuendosFromPending casts double→int
            // before the bounds check. Index 5 > elementCount(2) so bounds guard fires.
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("diminuendo index out of range");
        }

        @Test
        void testEndAtExactCountBoundaryThrowsSAXException() throws Exception {
            // end == elementCount (2 with 2 elements, 0-based: valid = 0,1) → SAXException
            // The double→int cast: index 2 is stored as double 2.0, cast back to int 2
            // = elementCount, so the bounds check fires.
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,2;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("diminuendo index out of range");
        }

        @Test
        void testValidDiminuendoPairLoadsSuccessfully() throws Exception {
            // Valid pair within bounds → Diminuendo is created
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,1;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();

            assertThat(parsedLine.findRangeElements(Diminuendo.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11KeytypeInvalid — C-9a
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11KeytypeInvalid {

        @Test
        void testUnknownKeytypeStringThrowsSAXException() throws Exception {
            // Unknown KeyType string → SAXException (Phase 2, C-9a)
            var emptyAttrs = new AttributesImpl();
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", emptyAttrs);
            reader.startElement11(LineIO.XML_KEYTYPE, emptyAttrs);
            reader.characters("BOGUS_TYPE".toCharArray(), 0, "BOGUS_TYPE".length());

            assertThatThrownBy(() -> reader.endElement11(LineIO.XML_KEYTYPE))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("unknown key type");
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11TrillYPosInvalid — C-9b
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11TrillYPosInvalid {

        @Test
        void testNonNumericTrillYPosThrowsSAXException() throws Exception {
            // Non-numeric value for XML_TRILL_YPOS → SAXException (Phase 2, C-9b)
            var content = "notanumber";
            var emptyAttrs = new AttributesImpl();
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", emptyAttrs);
            reader.startElement11(LineIO.XML_TRILL_YPOS, emptyAttrs);
            reader.characters(content.toCharArray(), 0, content.length());

            assertThatThrownBy(() -> reader.endElement11(LineIO.XML_TRILL_YPOS))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining(LineIO.XML_TRILL_YPOS);
        }
    }

    // -------------------------------------------------------------------------
    // LineReaderNullGuard — row 41
    // -------------------------------------------------------------------------

    @Nested
    class LineReaderNullGuard {

        @Test
        void testEndElementBeforeStartElementReturnsNull() throws Exception {
            // endElement11 before any startElement → line == null → returns null
            var reader = new LineIO.LineReader(minimalSongMock());
            var result = reader.endElement11("line");

            assertThat(result).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers for reader-based tests
    // -------------------------------------------------------------------------

    /**
     * Creates a LineReader with the given note types already fed in (in a
     * {@code <notes>} block). The reader is left open at WHERE.LINE,
     * ready to accept additional tags before {@code endElement11("line")}.
     */
    private static LineIO.LineReader buildReaderWithNotes(ElementType... types) throws SAXException {
        var emptyAttrs = new AttributesImpl();
        var reader = new LineIO.LineReader(minimalSongMock());
        reader.startElement11("line", emptyAttrs);
        reader.startElement11("notes", emptyAttrs);

        for (var type : types) {
            var noteAttrs = new AttributesImpl();
            noteAttrs.addAttribute("", "type", "type", "CDATA", type.name());
            reader.startElement11("note", noteAttrs);
            reader.startElement11("staffposition", emptyAttrs);
            reader.characters("0".toCharArray(), 0, 1);
            reader.endElement11("staffposition");
            reader.endElement11("note");
        }

        reader.endElement11("notes");
        return reader;
    }

    /**
     * Feeds a single tag with text content to an open LineReader (WHERE.LINE state).
     */
    private static void feedTag(LineIO.LineReader reader, String tagName, String content) throws SAXException {
        var emptyAttrs = new AttributesImpl();
        reader.startElement11(tagName, emptyAttrs);
        reader.characters(content.toCharArray(), 0, content.length());
        reader.endElement11(tagName);
    }
}
