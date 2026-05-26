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
import static org.mockito.Mockito.mockStatic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
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
}
