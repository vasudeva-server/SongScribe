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

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.UnitTest;

class XMLTest extends UnitTest {

    private static final int INDENT_NONE = 0;
    private static final int INDENT_TWO = 2;
    private static final int INDENT_FOUR = 4;

    @AfterEach
    void resetIndent() {
        XML.setIndent(INDENT_NONE);
    }

    // Row 62 — escapeXML: each special character individually, plus combined

    @Test
    void testEscapeXMLAmpersand() {
        assertThat(XML.escapeXML("a&b")).isEqualTo("a&amp;b");
    }

    @Test
    void testEscapeXMLDoubleQuote() {
        assertThat(XML.escapeXML("a\"b")).isEqualTo("a&quot;b");
    }

    @Test
    void testEscapeXMLGreaterThan() {
        assertThat(XML.escapeXML("a>b")).isEqualTo("a&gt;b");
    }

    @Test
    void testEscapeXMLLessThan() {
        assertThat(XML.escapeXML("a<b")).isEqualTo("a&lt;b");
    }

    @Test
    void testEscapeXMLAllFourSpecialsCombined() {
        assertThat(XML.escapeXML("&<>\"")).isEqualTo("&amp;&lt;&gt;&quot;");
    }

    // Row 63 — escapeXML: passthrough and empty

    @Test
    void testEscapeXMLEmptyStringReturnsEmpty() {
        assertThat(XML.escapeXML("")).isEmpty();
    }

    @Test
    void testEscapeXMLNoSpecialsPassthrough() {
        var plain = "Hello World 123";
        assertThat(XML.escapeXML(plain)).isEqualTo(plain);
    }

    // Row 64 — writeValue: <tag>escaped</tag> on its own line

    @Test
    void testWriteValueProducesTaggedLine() {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeValue(pw, "title", "Hello");
        pw.flush();
        assertThat(sw.toString()).isEqualTo("<title>Hello</title>" + System.lineSeparator());
    }

    @Test
    void testWriteValueEscapesSpecialCharacters() {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeValue(pw, "v", "a&b");
        pw.flush();
        assertThat(sw.toString()).isEqualTo("<v>a&amp;b</v>" + System.lineSeparator());
    }

    // Row 65 — writeValue: indent prefix applied

    @Test
    void testWriteValueRespectsIndent() {
        XML.setIndent(INDENT_TWO);
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeValue(pw, "n", "x");
        pw.flush();
        assertThat(sw.toString()).startsWith("  ");
    }

    // Row 66 — writeEmptyTag, writeBeginTag, writeEndTag

    @Test
    void testWriteBeginTagProducesOpenTag() {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeBeginTag(pw, "song");
        pw.flush();
        assertThat(sw.toString()).isEqualTo("<song>" + System.lineSeparator());
    }

    @Test
    void testWriteEmptyTagProducesSelfClosingTag() {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeEmptyTag(pw, "br");
        pw.flush();
        assertThat(sw.toString()).isEqualTo("<br />" + System.lineSeparator());
    }

    @Test
    void testWriteEndTagProducesCloseTag() {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeEndTag(pw, "song");
        pw.flush();
        assertThat(sw.toString()).isEqualTo("</song>" + System.lineSeparator());
    }

    @Test
    void testWriteTagsRespectIndent() {
        XML.setIndent(INDENT_TWO);
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeBeginTag(pw, "a");
        XML.writeEmptyTag(pw, "b");
        XML.writeEndTag(pw, "a");
        pw.flush();
        var lines = sw.toString().lines().toList();
        assertThat(lines).allMatch(line -> line.startsWith("  "));
    }

    // Row 67 — setIndent/printIndent: shared static state

    @Test
    void testSetIndentFourProducesFourSpacePrefix() {
        // XML.indent is static — changes affect all concurrent code in the same JVM.
        XML.setIndent(INDENT_FOUR);
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeEmptyTag(pw, "x");
        pw.flush();
        assertThat(sw.toString()).startsWith("    ");
    }

    // Row 68 — writeValue round-trip: SAX parser recovers original unescaped content

    @Test
    void testWriteValueRoundTripViaXMLParser() throws Exception {
        var original = "Fish & Chips < 5 > 3 with \"quotes\"";
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        XML.writeBeginTag(pw, "root");
        XML.writeValue(pw, "v", original);
        XML.writeEndTag(pw, "root");
        pw.flush();

        var xml = sw.toString();
        var parsed = new StringBuilder();
        var handler = new DefaultHandler() {
            private boolean inValue;

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                inValue = "v".equals(qName);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if ("v".equals(qName)) {
                    inValue = false;
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (inValue) {
                    parsed.append(ch, start, length);
                }
            }
        };

        var factory = SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        parser.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)), handler);

        assertThat(parsed.toString()).isEqualTo(original);
    }
}
