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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.xml.sax.SAXException;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.message.MessageCenter;

@SuppressWarnings("PackageVisibleInnerClass")
class TempoIOTest extends UnitTest {

    // -- Helpers --

    private static String writeTempo(Tempo t) {
        var sw = new StringWriter();
        TempoIO.writeTempo(t, new PrintWriter(sw), 0);
        return sw.toString();
    }

    private static TempoIO.TempoReader driveReader10(String innerXml) throws SAXException {
        var reader = new TempoIO.TempoReader();
        reader.startElement10(TempoIO.XML_TEMPO_CHANGE);

        for (var rawLine : innerXml.lines().toList()) {
            var line = rawLine.strip();

            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("</")) {
                var tag = line.substring(2, line.indexOf('>'));
                reader.endElement10(tag);
            } else if (line.startsWith("<") && !line.endsWith("/>")) {
                var tag = line.substring(1, line.indexOf('>'));
                reader.startElement10(tag);
                var closeOpen = line.indexOf("</");

                if (closeOpen >= 0) {
                    var tagEnd = line.indexOf('>');
                    var content = line.substring(tagEnd + 1, closeOpen);
                    reader.characters(content.toCharArray(), 0, content.length());
                    reader.endElement10(tag);
                }
            } else if (line.startsWith("<") && line.endsWith("/>")) {
                var tag = line.substring(1, line.length() - 2);
                reader.startElement10(tag);
                reader.endElement10(tag);
            }
        }

        return reader;
    }

    // -- Test classes --

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DontShowTempoSerialization {

        // Row 56 (branch 1): shouldShowTempo true → <dontshowtempo> absent
        @Test
        void testDontShowTempoAbsentWhenShown() {
            var t = new Tempo(120, Duration.CROTCHET, "Allegro", true);

            var output = writeTempo(t);

            assertThat(output).doesNotContain(TempoIO.XML_DONT_SHOW_TEMPO);
        }

        // Row 56 (branch 2): shouldShowTempo false → <dontshowtempo> present
        @Test
        void testDontShowTempoPresentWhenHidden() {
            var t = new Tempo(120, Duration.CROTCHET, "Allegro", false);

            var output = writeTempo(t);

            assertThat(output).contains("<" + TempoIO.XML_DONT_SHOW_TEMPO + " />");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement10DontShowTempo {

        // Row 61: <dontshowtempo> → setShowTempo(false)
        @Test
        void testDontShowTempoTagSetsShowTempoFalse() throws SAXException {
            var reader = new TempoIO.TempoReader();
            reader.startElement10(TempoIO.XML_TEMPO_CHANGE);
            reader.startElement10(TempoIO.XML_DONT_SHOW_TEMPO);
            reader.endElement10(TempoIO.XML_DONT_SHOW_TEMPO);
            var tempo = reader.endElement10(TempoIO.XML_TEMPO_CHANGE);

            assertThat(tempo).isNotNull();

            assertThat(tempo.shouldShowTempo()).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement10LegacyDurationNames {

        // Row 59: no-underscore legacy names map to canonical Duration values
        @ParameterizedTest(name = "legacyName={0}, expected={1}")
        @CsvSource({
            "MINIMDOTTED,    MINIM_DOTTED",
            "CROTCHETDOTTED, CROTCHET_DOTTED",
            "QUAVERDOTTED,   QUAVER_DOTTED",
            "SEMIBREVE,      SEMI_BREVE",
        })
        void testLegacyDurationNameMapsToCanonical(
            String legacyName,
            String expectedDurationName
        ) throws SAXException {
            var reader = new TempoIO.TempoReader();
            reader.startElement10(TempoIO.XML_TEMPO_CHANGE);
            reader.startElement10(TempoIO.XML_TEMPO_TYPE);
            reader.characters(legacyName.toCharArray(), 0, legacyName.length());
            reader.endElement10(TempoIO.XML_TEMPO_TYPE);
            var tempo = reader.endElement10(TempoIO.XML_TEMPO_CHANGE);

            assertThat(tempo).isNotNull();

            assertThat(tempo.getTempoType()).isEqualTo(Duration.valueOf(expectedDurationName));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement10Parse {

        // Row 58: <tempochange> wrapper → Tempo; <position> via getPos10()
        @Test
        void testTempoChangeWrapperParsesFieldsAndPos10() throws SAXException {
            var expectedVisibleTempo = 96;
            var expectedPos = 5;
            var inner = """
                <position>%d</position>
                <visibletempo>%d</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription>Andante</tempodescription>
                """.formatted(expectedPos, expectedVisibleTempo);

            var reader = driveReader10(inner);
            var tempo = reader.endElement10(TempoIO.XML_TEMPO_CHANGE);

            assertThat(tempo).isNotNull();

            assertThat(tempo.getVisibleTempo()).isEqualTo(expectedVisibleTempo);
            assertThat(tempo.getTempoType()).isEqualTo(Duration.CROTCHET);
            assertThat(tempo.getTempoDescription()).isEqualTo("Andante");
            assertThat(reader.getPos10()).isEqualTo(expectedPos);
        }

        // Row 60: canonical duration name takes Duration.valueOf path
        @Test
        void testCanonicalDurationNameYieldsCorrectDuration() throws SAXException {
            var reader = new TempoIO.TempoReader();
            reader.startElement10(TempoIO.XML_TEMPO_CHANGE);
            reader.startElement10(TempoIO.XML_TEMPO_TYPE);
            reader.characters("MINIM".toCharArray(), 0, "MINIM".length());
            reader.endElement10(TempoIO.XML_TEMPO_TYPE);
            var tempo = reader.endElement10(TempoIO.XML_TEMPO_CHANGE);

            assertThat(tempo).isNotNull();

            assertThat(tempo.getTempoType()).isEqualTo(Duration.MINIM);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement11LegacyDurationNames {

        // Row 63: a genuinely-unknown duration name in the v1.1 path → SAXException
        @Test
        void testUnknownDurationNameInV11PathThrowsSAXException() throws Exception {
            var unknownName = "NOTADURATION";
            var reader = new TempoIO.TempoReader();
            reader.startElement11(TempoIO.XML_TEMPO);
            reader.startElement11(TempoIO.XML_TEMPO_TYPE);
            reader.characters(unknownName.toCharArray(), 0, unknownName.length());

            assertThatThrownBy(() -> reader.endElement11(TempoIO.XML_TEMPO_TYPE))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Corrupt document: unknown tempo duration: '" + unknownName + "'");
        }

        // v1.1 files can still carry the underscore-less v1.0 dotted-duration tokens
        // (e.g. real example songs at composition version 1.1), so the v1.1 path must
        // apply the same legacy-name fallback as the v1.0 path rather than reject them.
        @ParameterizedTest(name = "legacyName={0}, expected={1}")
        @CsvSource({
            "MINIMDOTTED,    MINIM_DOTTED",
            "CROTCHETDOTTED, CROTCHET_DOTTED",
            "QUAVERDOTTED,   QUAVER_DOTTED",
            "SEMIBREVE,      SEMI_BREVE",
        })
        void testLegacyDurationNameMapsToCanonicalInV11Path(
            String legacyName,
            String expectedDurationName
        ) throws Exception {
            var reader = new TempoIO.TempoReader();
            reader.startElement11(TempoIO.XML_TEMPO);
            reader.startElement11(TempoIO.XML_TEMPO_TYPE);
            reader.characters(legacyName.toCharArray(), 0, legacyName.length());
            reader.endElement11(TempoIO.XML_TEMPO_TYPE);
            var tempo = reader.endElement11(TempoIO.XML_TEMPO);

            assertThat(tempo).isNotNull();

            assertThat(tempo.getTempoType()).isEqualTo(Duration.valueOf(expectedDurationName));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement11NullGuard {

        // Row 64: endElement11 called before startElement11 (tempo is null) → returns null
        @Test
        void testEndElement11BeforeStartElement11ReturnsNull() throws Exception {
            var reader = new TempoIO.TempoReader();

            var result = reader.endElement11(TempoIO.XML_TEMPO);

            assertThat(result).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RoundTripViaStaffElementReader {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        // Row 57: writeTempo + endElement11 round-trip via StaffElementReader preserves all fields
        @Test
        void testTempoRoundTripViaStaffElementReaderPreservesAllFields() throws Exception {
            var visibleTempo = 144;
            var description = "Vivace";
            var original = new Tempo(visibleTempo, Duration.MINIM, description, false);

            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new TempoChangeAttachment(note, original));

            var line = new Song().getLine(0);
            var sw = new StringWriter();
            StaffElementIO.writeElement(note, new PrintWriter(sw), line, 0);
            var xml = buildNoteXmlWithTempoContent(sw.toString());
            var parsed = parseXml(xml);
            var parsedNote = parsed.getLine(0).getElement(0);
            var attachment = parsedNote.findAttachment(TempoChangeAttachment.class);

            assertThat(attachment).isNotNull();

            var restored = attachment.getTempo();
            assertThat(restored.getVisibleTempo()).isEqualTo(visibleTempo);
            assertThat(restored.getTempoType()).isEqualTo(Duration.MINIM);
            assertThat(restored.getTempoDescription()).isEqualTo(description);
            assertThat(restored.shouldShowTempo()).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WriteTempoFields {

        // Row 55: writeTempo emits <visibletempo>, <tempotype>, <tempodescription>
        @Test
        void testWritesVisibleTempoTypeAndDescription() {
            var visibleTempo = 108;
            var description = "Moderato";
            var t = new Tempo(visibleTempo, Duration.CROTCHET, description, true);

            var output = writeTempo(t);

            assertThat(output)
                .contains("<" + TempoIO.XML_VISIBLE_TEMPO + ">" + visibleTempo + "</" + TempoIO.XML_VISIBLE_TEMPO + ">")
                .contains("<" + TempoIO.XML_TEMPO_TYPE + ">CROTCHET</" + TempoIO.XML_TEMPO_TYPE + ">")
                .contains("<" + TempoIO.XML_TEMPO_DESCRIPTION + ">" + description + "</" + TempoIO.XML_TEMPO_DESCRIPTION + ">");
        }
    }

    // -- Private helpers --

    private static String buildNoteXmlWithTempoContent(String noteXml) {
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
                    %s
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(noteXml.strip());
    }
}
