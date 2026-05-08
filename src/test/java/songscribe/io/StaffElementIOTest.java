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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Song;
import songscribe.music.StaffElement;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

@SuppressWarnings({ "SameReturnValue", "OverlyBroadThrowsClause" })
class StaffElementIOTest extends UnitTest {

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();

    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
        line = new Song().getLine(0);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InvalidMapLookups {

        @Test
        void testUnknownAccidentalThrowsMeaningfulError() {
            var xml = buildXmlWithAccidental("BOGUS_ACCIDENTAL");

            assertThatThrownBy(() -> parseXml(xml))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Unknown accidental: BOGUS_ACCIDENTAL")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testUnknownBeatChangeThrowsMeaningfulError() {
            var xml = buildXmlWithBeatChange("BOGUS_BEAT_CHANGE");

            assertThatThrownBy(() -> parseXml(xml))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("unknown legacy beat change: BOGUS_BEAT_CHANGE")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DynamicSerialization {

        // T30: Write note with DynamicAttachment → XML contains <dynamic type="..."/>
        @Test
        void testWritesDynamicElement() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));

            var output = writeNote(note);

            assertThat(output).contains("<dynamic type=\"FORTE\" />");
        }

        // T31: Read <dynamic type="FORTE"/> → note has DynamicAttachment(FORTE)
        @Test
        void testReadsDynamicElement() throws Exception {
            var song = parseXmlToSong(buildXmlWithDynamic("FORTE"));
            var dynamic = song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.FORTE);
        }

        // T32: Round-trip preserves dynamic type for all 6 UI types
        @ParameterizedTest
        @EnumSource(value = DynamicType.class, names = {"PIANISSIMO", "PIANO", "MEZZO_PIANO", "MEZZO_FORTE", "FORTE", "FORTISSIMO"})
        void testRoundTripPreservesDynamicType(DynamicType dynamicType) throws Exception {
            var comp1 = parseXmlToSong(buildXmlWithDynamic(dynamicType.name()));
            var comp2 = roundTrip(comp1);
            var dynamic = comp2.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- need for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(dynamicType);
        }

        // T33: Read file without dynamics → no DynamicAttachment
        @Test
        void testNoDynamicWhenElementAbsent() throws Exception {
            var song = parseXmlToSong(buildXmlWithoutDynamic());
            var dynamic = song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNull();
        }

        // T34: Read <dynamic type="UNKNOWN"/> → logged warning, attachment skipped
        @Test
        void testUnknownDynamicTypeIsSkipped() {
            var xml = buildXmlWithDynamic("UNKNOWN_DYNAMIC");

            assertThatCode(() -> parseXmlToSong(xml)).doesNotThrowAnyException();
        }

        @Test
        void testUnknownDynamicTypeProducesNoAttachment() throws Exception {
            var song = parseXmlToSong(buildXmlWithDynamic("UNKNOWN_DYNAMIC"));
            var dynamic = song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class);

            assertThat(dynamic).isNull();
        }
    }

    // -- Helpers --

    private String writeNote(StaffElement note) {
        var sw = new StringWriter();
        StaffElementIO.writeElement(note, new PrintWriter(sw), line, 0);
        return sw.toString();
    }

    private static Song parseXmlToSong(String xml) throws Exception {
        var parser = PARSER_FACTORY.newSAXParser();
        var reader = new SongIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);
        return reader.getSong();
    }

    private static String buildXmlWithDynamic(String dynamicType) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <dynamic type="%s" />
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(dynamicType);
    }

    private static String buildXmlWithoutDynamic() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    private static void parseXml(String xml) throws Exception {
        var parser = PARSER_FACTORY.newSAXParser();
        var reader = new SongIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);
    }

    private static String buildXmlWithAccidental(String accidentalValue) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <prefix>%s</prefix>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(accidentalValue);
    }

    private static String buildXmlWithBeatChange(String beatChangeValue) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="1.1">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
                <showtempo>true</showtempo>
              </tempo>
              <songtitle>Test</songtitle>
              <lines>
                <line>
                  <keyCount>0</keyCount>
                  <keyType>SHARPS</keyType>
                  <tempoChangeYPos>-28</tempoChangeYPos>
                  <notes>
                    <note type="CROTCHET">
                      <yPos>0</yPos>
                      <xPos>80</xPos>
                      <beatchange>%s</beatchange>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(beatChangeValue);
    }
}
