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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.UnitTest;

class StaffElementIOTest extends UnitTest {

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();

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
                .hasMessageContaining("Unknown beat change: BOGUS_BEAT_CHANGE")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -- Helpers --

    private static void parseXml(String xml) throws Exception {
        var parser = PARSER_FACTORY.newSAXParser();
        var reader = new CompositionIO.DocumentReader();
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
