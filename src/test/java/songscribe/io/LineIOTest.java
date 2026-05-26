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
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xml.sax.helpers.AttributesImpl;

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
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
        song = new Song();
        line = song.getLine(0);
    }

    @AfterEach
    void tearDown() {
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
    private static Line parseLineTag(String tagName, String content) {
        var emptyAttrs = new AttributesImpl();
        var reader = new LineIO.LineReader(minimalSongMock());
        reader.startElement11("line", emptyAttrs);
        reader.startElement11(tagName, emptyAttrs);
        reader.characters(content.toCharArray(), 0, content.length());
        reader.endElement11(tagName);
        return reader.endElement11("line");
    }

    // -------------------------------------------------------------------------
    // WriteLineDeltaKeySig — row 1
    // -------------------------------------------------------------------------

    @Nested
    class WriteLineDeltaKeySig {

        @Test
        void testDifferingKeyWritesKeysAndKeytypeTags() {
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
        void testMatchingSongDefaultOmitsKeysTags() {
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
        void testNonUnitRatioWritesNoteDist() {
            song.withModification(() -> line.changeElementSpacingRatio(RATIO_ONE_AND_HALF));

            var output = writeLine(line);

            assertThat(output).contains("<" + LineIO.XML_NOTE_DIST_CHANGE + ">");
        }

        @Test
        void testUnitRatioOmitsNoteDist() {
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
        void testAlwaysWritesLyricsYPosTag() {
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
        void testLegacyYPosTagsAbsentInNewDocuments() {
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
        void testKnownBeamListProducesAnchorEndPairs() {
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

            var result = LineIO.beamsToString(elements.findRangeElements(Beam.class));

            assertThat(result).isEqualTo("0,1;2,3;");
        }
    }

    // -------------------------------------------------------------------------
    // TrillsToString — row 6
    // -------------------------------------------------------------------------

    @Nested
    class TrillsToString {

        @Test
        void testZeroYPosOmitsYPos() {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var trill = new Trill(elements.getElement(0), elements.getElement(1));
            elements.addRangeElement(trill);

            var result = LineIO.trillsToString(elements.findRangeElements(Trill.class));

            // yPositionSs == 0 → omitted; format: anchor,end;
            assertThat(result).isEqualTo("0,1;");
        }

        @Test
        void testNonZeroYPosIncludesYPos() {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var trill = new Trill(elements.getElement(0), elements.getElement(1));
            final int Y_POS = 3;
            trill.setYPositionSs(Y_POS);
            elements.addRangeElement(trill);

            var result = LineIO.trillsToString(elements.findRangeElements(Trill.class));

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

        @Test
        void testZeroVertPosOmitsVertPos() {
            var elements = lineWith(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );

            var tuplet = new Tuplet(elements.getElement(0), elements.getElement(2), TRIPLET_GRADE);
            elements.addTuplet(tuplet);

            var result = LineIO.tupletsToString(elements.findRangeElements(Tuplet.class));

            // verticalPositionSs == 0 → omitted; format: anchor,end,grade;
            assertThat(result).isEqualTo("0,2,3;");
        }

        @Test
        void testNonZeroVertPosIncludesVertPos() {
            var elements = lineWith(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );

            var tuplet = new Tuplet(elements.getElement(0), elements.getElement(2), TRIPLET_GRADE);
            final int VERT_POS = 4;
            tuplet.setVerticalPositionSs(VERT_POS);
            elements.addTuplet(tuplet);

            var result = LineIO.tupletsToString(elements.findRangeElements(Tuplet.class));

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
        void testAllShiftsZeroOmitsShifts() {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var crescendo = new Crescendo(elements.getElement(0), elements.getElement(1));
            elements.addRangeElement(crescendo);

            var result = LineIO.hairpinsToString(elements.findRangeElements(Crescendo.class));

            assertThat(result).isEqualTo("0,1;");
        }

        @Test
        void testAnyNonZeroShiftIncludesAllShifts() {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var diminuendo = new Diminuendo(elements.getElement(0), elements.getElement(1));
            final double X1 = 1.5;
            final double X2 = 0.0;
            final double Y = -2.0;
            diminuendo.setX1ShiftSs(X1);
            diminuendo.setX2ShiftSs(X2);
            diminuendo.setYShiftSs(Y);
            elements.addRangeElement(diminuendo);

            var result = LineIO.hairpinsToString(elements.findRangeElements(Diminuendo.class));

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
        void testProducesAnchorEndPairWithoutType() {
            var elements = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
            var ending = new Ending(elements.getElement(0), elements.getElement(1), Ending.Type.SECOND);
            elements.addRangeElement(ending);

            var result = LineIO.endingsToString(elements.findRangeElements(Ending.class));

            // Ending.Type is not serialized — only anchor,end;
            assertThat(result).isEqualTo("0,1;");
        }

        @Test
        void testTypeAlwaysDeserializesAsFirstDueToBug() {
            // Deserializer always creates Ending.Type.FIRST regardless of original type.
            // This test documents the current (buggy) behavior so regressions are detectable.
            var reader = buildReaderWithOneNoteAndEnding("0,0;");
            var parsedLine = reader.endElement11("line");
            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            var endings = parsedLine.findRangeElements(Ending.class);
            assertThat(endings).hasSize(1);
            // Bug: type is not serialized, so deserialization always produces FIRST
            assertThat(endings.getFirst().getType()).isEqualTo(Ending.Type.FIRST);
        }

        private static LineIO.LineReader buildReaderWithOneNoteAndEnding(String endingsStr) {
            var emptyAttrs = new AttributesImpl();
            var noteAttrs = new AttributesImpl();
            noteAttrs.addAttribute("", "type", "type", "CDATA", "CROTCHET");
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", emptyAttrs);
            reader.startElement11("notes", emptyAttrs);
            reader.startElement11("note", noteAttrs);
            reader.startElement11("staffposition", emptyAttrs);
            reader.characters("0".toCharArray(), 0, 1);
            reader.endElement11("staffposition");
            reader.endElement11("note");
            reader.endElement11("notes");
            reader.startElement11(LineIO.XML_FSENDINGS, emptyAttrs);
            reader.characters(endingsStr.toCharArray(), 0, endingsStr.length());
            reader.endElement11(LineIO.XML_FSENDINGS);
            return reader;
        }
    }

    // -------------------------------------------------------------------------
    // ForEachSegment — row 10
    // -------------------------------------------------------------------------

    @Nested
    class ForEachSegment {

        @Test
        void testEmptyStringYieldsZeroIterations() {
            var count = new int[]{0};
            LineIO.forEachSegment("", (begin, end) -> count[0]++);
            assertThat(count[0]).isEqualTo(0);
        }

        @Test
        void testSingleSegmentYieldsOneIteration() {
            var segments = new ArrayList<String>();
            LineIO.forEachSegment("0,1;", (begin, end) -> segments.add("0,1;".substring(begin, end)));
            assertThat(segments).containsExactly("0,1");
        }

        @Test
        void testMultipleSegmentsYieldsOneIterationEach() {
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
        void testLineTagCreatesLineAndSetsWhereLine() {
            var reader = new LineIO.LineReader(minimalSongMock());
            assertThat(reader.line).isNull();
            assertThat(reader.where).isNull();

            reader.startElement11("line", new AttributesImpl());

            assertThat(reader.line).isNotNull();
            assertThat(reader.where).isEqualTo(LineIO.LineReader.Where.LINE);
        }

        @Test
        void testNotesTagTransitionsToWhereNotes() {
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", new AttributesImpl());
            assertThat(reader.where).isEqualTo(LineIO.LineReader.Where.LINE);

            reader.startElement11("notes", new AttributesImpl());

            assertThat(reader.where).isEqualTo(LineIO.LineReader.Where.NOTES);
        }

        @Test
        void testUnknownTagSetsLastTag() {
            var reader = new LineIO.LineReader(minimalSongMock());
            reader.startElement11("line", new AttributesImpl());
            assertThat(reader.lastTag).isNull();

            reader.startElement11("keys", new AttributesImpl());

            assertThat(reader.lastTag).isEqualTo("keys");
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11Keys — row 12
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11Keys {

        private static final int KEY_COUNT = 5;

        @Test
        void testKeysTagSetsKeyAccidentalCount() {
            var parsedLine = parseLineTag(LineIO.XML_KEYS, String.valueOf(KEY_COUNT));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.getKeyAccidentalCount()).isEqualTo(KEY_COUNT);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11Keytype — row 13
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11Keytype {

        @Test
        void testKeytypeTagSetsKeyType() {
            var parsedLine = parseLineTag(LineIO.XML_KEYTYPE, KeyType.FLATS.name());

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testNotedistchangeTagSetsElementSpacingRatio() {
            var parsedLine = parseLineTag(LineIO.XML_NOTE_DIST_CHANGE, String.valueOf(SPACING_RATIO));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testLyricsyposTagSetsLyricsYPosSs() {
            var parsedLine = parseLineTag(LineIO.XML_LYRICS_YPOS, String.valueOf(LYRICS_YPOS));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testBeatchangeyposTagSetsBeatChangeYPosPx() {
            var parsedLine = parseLineTag(LineIO.XML_BEAT_CHANGE_YPOS, String.valueOf(YPOS_PX));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.getBeatChangeYPosPx()).isEqualTo(YPOS_PX);
        }

        @Test
        void testFsendingyposTagSetsFirstSecondEndingYPosPx() {
            var parsedLine = parseLineTag(LineIO.XML_FSENDING_YPOS, String.valueOf(YPOS_PX));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.getFirstSecondEndingYPosPx()).isEqualTo(YPOS_PX);
        }

        @Test
        void testTempochangeyposTagSetsTempoChangeYPosPx() {
            var parsedLine = parseLineTag(LineIO.XML_TEMPO_CHANGE_YPOS, String.valueOf(YPOS_PX));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.getTempoChangeYPosPx()).isEqualTo(YPOS_PX);
        }

        @Test
        void testTrillyposTagSetsTrillYPosPx() {
            var parsedLine = parseLineTag(LineIO.XML_TRILL_YPOS, String.valueOf(YPOS_PX));

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.getTrillYPosPx()).isEqualTo(YPOS_PX);
        }
    }

    // -------------------------------------------------------------------------
    // EndElement11SlursIgnored — row 22
    // -------------------------------------------------------------------------

    @Nested
    class EndElement11SlursIgnored {

        @Test
        void testSlursTagSilentlyIgnored() {
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
        void testBeamRoundTripPreservesAnchorAndEnd() {
            var elements = lineWith(ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER);
            var beam = new Beam(elements.getElement(0), elements.getElement(2));
            elements.addRangeElement(beam);

            var serialized = LineIO.beamsToString(elements.findRangeElements(Beam.class));

            // Feed into a reader with matching notes to reconstruct
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_BEAMINGS, serialized);
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            var foundBeam = parsedLine.findBeamAt(0);
            assertThat(foundBeam).isNotNull();
            if (foundBeam == null) return;
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
        void testOutOfRangePairsProduceZeroBeams() {
            // Line has 2 elements (indices 0-1); supply three malformed pairs:
            //   anchor < 0, end >= count, anchor > end
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);

            // anchor=-1 (anchor<0), end=5 (>=count), anchor=1 end=0 (anchor>end)
            feedTag(reader, LineIO.XML_BEAMINGS, "-1,1;0,5;1,0;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.findRangeElements(Beam.class)).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // CreateTiesFromPendingPairsOutOfRange — row 26
    // -------------------------------------------------------------------------

    @Nested
    class CreateTiesFromPendingPairsOutOfRange {

        @Test
        void testOutOfRangeTiePairThrowsIndexOutOfBoundsException() {
            // createTiesFromPendingPairs has no bounds guard — IndexOutOfBoundsException
            // is expected when a pair references an index beyond the element count.
            // Line.getElement() uses ArrayList.get() which throws IndexOutOfBoundsException.
            var reader = buildReaderWithNotes(ElementType.QUAVER, ElementType.QUAVER);
            feedTag(reader, LineIO.XML_TIES, "0,5;");

            assertThatThrownBy(() -> reader.endElement11("line"))
                .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    // -------------------------------------------------------------------------
    // TupletLegacyTripletsDefaultGrade — row 27
    // -------------------------------------------------------------------------

    @Nested
    class TupletLegacyTripletsDefaultGrade {

        private static final int EXPECTED_GRADE = 3;

        @Test
        void testLegacyTripletsTagDefaultsGradeToThree() {
            // <triplets> format omits grade; parseTupletData defaults to 3
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TRIPLETS, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testExplicitGradeFiveRoundTrip() {
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
            if (parsedLine == null) return;
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
        void testNonZeroVertPosParsedCorrectly() {
            // Serialized: <tuplets>0,2,3,7;</tuplets> → verticalPositionSs must be 7
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TUPLETS, "0,2," + TRIPLET_GRADE + "," + VERT_POS + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testOutOfRangePairsProduceZeroTuplets() {
            // Line has 2 elements (indices 0-1); supply three malformed pairs:
            //   anchor < 0, end >= count, anchor > end
            var reader = buildReaderWithNotes(ElementType.CROTCHET, ElementType.CROTCHET);
            feedTag(reader, LineIO.XML_TUPLETS, "-1,1,3;0,5,3;1,0,3;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            assertThat(parsedLine.findRangeElements(Tuplet.class)).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // ParseTupletDataNfeDefaults — row 31
    // -------------------------------------------------------------------------

    @Nested
    class ParseTupletDataNfeDefaults {

        private static final int DEFAULT_GRADE = 3;
        private static final int DEFAULT_VERT_POS = 0;

        @Test
        void testNonNumericGradeDefaultsToThree() {
            // Non-numeric grade token → defaults to 3
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TUPLETS, "0,2,bad;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            var tuplets = parsedLine.findRangeElements(Tuplet.class);
            assertThat(tuplets).hasSize(1);
            assertThat(tuplets.getFirst().getGrade()).isEqualTo(DEFAULT_GRADE);
        }

        @Test
        void testNonNumericVertPosDefaultsToZero() {
            // Non-numeric vertPos token → defaults to 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_TUPLETS, "0,2,3,bad;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            var tuplets = parsedLine.findRangeElements(Tuplet.class);
            assertThat(tuplets).hasSize(1);
            assertThat(tuplets.getFirst().getVerticalPositionSs()).isEqualTo(DEFAULT_VERT_POS);
        }
    }

    // -------------------------------------------------------------------------
    // CrescendoRoundTripAllZeroShifts — row 32
    // -------------------------------------------------------------------------

    @Nested
    class CrescendoRoundTripAllZeroShifts {

        @Test
        void testAllZeroShiftsRoundTrip() {
            // Serialized: <crescendo>0,2;</crescendo> → all shifts 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_CRESCENDO, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testExplicitShiftsPreservedInRoundTrip() {
            // Serialized: <crescendo>0,2,1.5,-0.5,0.25;</crescendo>
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_CRESCENDO, "0,2," + X1_SHIFT + "," + X2_SHIFT + "," + Y_SHIFT + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
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
        void testAllZeroShiftsRoundTrip() {
            // Serialized: <diminuendo>0,2;</diminuendo> → all shifts 0
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,2;");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            var diminuendos = parsedLine.findRangeElements(Diminuendo.class);
            assertThat(diminuendos).hasSize(1);
            var d = diminuendos.getFirst();
            assertThat(d.getX1ShiftSs()).isEqualTo(0.0);
            assertThat(d.getX2ShiftSs()).isEqualTo(0.0);
            assertThat(d.getYShiftSs()).isEqualTo(0.0);
        }

        @Test
        void testExplicitShiftsPreservedInRoundTrip() {
            // Serialized: <diminuendo>0,2,0.5,-1.0,0.75;</diminuendo>
            var reader = buildReaderWithNotes(
                ElementType.CROTCHET,
                ElementType.CROTCHET,
                ElementType.CROTCHET
            );
            feedTag(reader, LineIO.XML_DIMINUENDO, "0,2," + X1_SHIFT + "," + X2_SHIFT + "," + Y_SHIFT + ";");
            var parsedLine = reader.endElement11("line");

            assertThat(parsedLine).isNotNull();
            if (parsedLine == null) return;
            var diminuendos = parsedLine.findRangeElements(Diminuendo.class);
            assertThat(diminuendos).hasSize(1);
            var d = diminuendos.getFirst();
            assertThat(d.getX1ShiftSs()).isEqualTo(X1_SHIFT);
            assertThat(d.getX2ShiftSs()).isEqualTo(X2_SHIFT);
            assertThat(d.getYShiftSs()).isEqualTo(Y_SHIFT);
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
    private static LineIO.LineReader buildReaderWithNotes(ElementType... types) {
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
    private static void feedTag(LineIO.LineReader reader, String tagName, String content) {
        var emptyAttrs = new AttributesImpl();
        reader.startElement11(tagName, emptyAttrs);
        reader.characters(content.toCharArray(), 0, content.length());
        reader.endElement11(tagName);
    }
}
