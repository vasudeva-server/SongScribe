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
package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;

import com.sun.net.httpserver.HttpServer;

import javax.xml.stream.XMLStreamException;

import org.audiveris.proxymusic.Attributes;
import org.audiveris.proxymusic.ClefSign;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.Step;
import org.audiveris.proxymusic.YesNo;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.UnitTest;
import songscribe.io.SafeXmlParser;

/**
 * Covers the byte-level shape of a saved document and the hardening of the parse
 * entry point — the two things neither a round-trip test nor schema validation can
 * see. A round trip is blind to formatting because both directions agree on it, and
 * the schema is blind because {@code <foo/>} and {@code <foo></foo>} are equally
 * valid, as is any indentation.
 */
class MusicXmlSerializerTest extends UnitTest {

    /**
     * A {@code DOCTYPE} naming a file that does not exist. Anything that tries to
     * fetch it fails loudly, which is what makes "nothing was fetched" observable.
     */
    private static final String EXTERNAL_DOCTYPE =
        "<!DOCTYPE score-partwise SYSTEM \"file:///nonexistent/songscribe-should-never-fetch-this.dtd\">";

    private static final String DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /** The path a fetch-detecting {@code DOCTYPE} points at; any request to it is a failure. */
    private static final String DTD_PATH = "/dtds/partwise.dtd";

    private static final int HTTP_NOT_FOUND = 404;

    private final ObjectFactory factory = new ObjectFactory();

    // -------------------------------------------------------------------------
    // Output shape
    // -------------------------------------------------------------------------

    @Test
    void testMarshalWritesOurXmlDeclaration() {
        assertThat(marshalToString(sampleScore())).startsWith(DECLARATION + "\n<score-partwise");
    }

    @Test
    void testMarshalEmitsNoDoctype() {
        assertThat(marshalToString(sampleScore())).doesNotContain("<!DOCTYPE");
    }

    @Test
    void testMarshalEmitsNoNamespaceDeclaration() {
        // JAXB hoists an xmlns:ns2 xlink declaration onto the root of a document
        // that contains no xlink: attribute; the serializer's delegate drops it.
        assertThat(marshalToString(sampleScore())).doesNotContain("xmlns");
    }

    @Test
    void testMarshalIndentsTwoSpacesPerLevel() {
        var xml = marshalToString(sampleScore());

        assertThat(xml)
            .contains("\n  <part-list>\n")
            .contains("\n    <score-part id=\"P1\">\n")
            .contains("\n      <part-name>Music</part-name>\n")
            // Attributes and text on one element keep its end tag on the same line.
            .contains("\n    <creator type=\"composer\">Sri Chinmoy</creator>\n")
            .contains("\n      <attributes>\n")
            .contains("\n        <divisions>1</divisions>\n");
    }

    @Test
    void testMarshalCollapsesChildlessElementToSelfClosingTag() {
        var xml = marshalToString(sampleScore());

        // Attribute order is JAXB's, not ours; the tag's shape is what this asserts.
        assertThat(xml).containsPattern("<supports [^>]+/>");
        assertThat(xml).doesNotContain("</supports>");
    }

    @Test
    void testMarshalEndsWithASingleTrailingNewline() {
        assertThat(marshalToString(sampleScore())).endsWith("</score-partwise>\n");
    }

    @Test
    void testMarshalledDocumentValidatesAgainstTheSchema() throws Exception {
        new MusicXmlSchemaValidator().validate(marshalToString(sampleScore()));
    }

    // -------------------------------------------------------------------------
    // Round trip through the parse entry point
    // -------------------------------------------------------------------------

    @Test
    void testUnmarshalRecoversTheMarshalledScore() throws Exception {
        var parsed = MusicXmlSerializer.unmarshal(sourceFor(marshalToString(sampleScore())));

        assertThat(parsed.rootElement()).isEqualTo(MusicXmlTags.SCORE_PARTWISE);
        assertThat(parsed.version()).isEqualTo("4.0");
        assertThat(parsed.score().getPart()).hasSize(1);
        assertThat(parsed.score().getPart().get(0).getMeasure()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Failure and hardening
    // -------------------------------------------------------------------------

    @Test
    void testMarshalFailureLeavesTheWriterUntouched() {
        var score = sampleScore();

        // A type the JAXB context knows nothing about: the failure surfaces part-way
        // through marshalling, which is exactly the shape that would truncate a file
        // written to directly.
        score.getPart().get(0).getMeasure().get(0).getNoteOrBackupOrForward().add(new Object());

        var buffer = new StringWriter();

        assertThatThrownBy(() -> MusicXmlSerializer.marshal(score, new PrintWriter(buffer)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(buffer.toString()).isEmpty();
    }

    /**
     * Nothing on the load path may fetch what a document's {@code DOCTYPE} names.
     *
     * <p>A local server is the only way to assert this. A {@code DOCTYPE} pointing at a
     * missing file or a closed port does not discriminate — the parser tolerates a failed
     * fetch and reports success either way, so such a fixture passes whether or not the
     * request went out. Counting requests at a listener is what makes "nothing was
     * fetched" observable rather than merely plausible.
     *
     * <p>This is a real regression, not a hypothetical: an {@code XMLResolver} returning a
     * {@code Reader} — a type StAX discards silently — made the parser resolve the system
     * id itself and fetch {@code musicxml.org} for every export carrying the standard
     * MusicXML {@code DOCTYPE}.
     */
    @Test
    void testUnmarshalDoesNotFetchWhatADoctypeNames() throws Exception {
        assertNothingFetched(url -> {
            var xml = marshalToString(sampleScore())
                .replace(DECLARATION, DECLARATION + "\n" + doctypeNaming(url));

            MusicXmlSerializer.unmarshal(sourceFor(xml));
        });
    }

    /**
     * The same guarantee for the schema check, which runs over documents this project did
     * not write — and a real export from another program names {@code musicxml.org}.
     *
     * <p>Unlike the unmarshal probe above, this one passes with the validator's hardening
     * removed: a default JAXP {@code Validator} does not fetch an instance document's
     * {@code DOCTYPE}. It is a guard, not a reproduction — it holds that behaviour in
     * place, and would catch a future change that started resolving external references.
     */
    @Test
    void testSchemaValidationDoesNotFetchWhatADoctypeNames() throws Exception {
        assertNothingFetched(url -> {
            var xml = marshalToString(sampleScore())
                .replace(DECLARATION, DECLARATION + "\n" + doctypeNaming(url));

            try {
                new MusicXmlSchemaValidator().validate(xml);
            } catch (SAXException e) {
                // Whether this document satisfies the schema is another test's business;
                // this one is only counting requests.
            }
        });
    }

    @FunctionalInterface
    private interface FetchProbe {
        void run(String dtdUrl) throws Exception;
    }

    /**
     * Runs {@code probe} against a {@code DOCTYPE} URL served by a local listener, and
     * fails if the listener was contacted at all.
     */
    private static void assertNothingFetched(FetchProbe probe) throws Exception {
        var requested = Collections.synchronizedList(new ArrayList<String>());
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestURI().toString());
            exchange.sendResponseHeaders(HTTP_NOT_FOUND, -1);
            exchange.close();
        });
        server.start();

        try {
            probe.run("http://" + InetAddress.getLoopbackAddress().getHostAddress()
                + ':' + server.getAddress().getPort() + DTD_PATH);
        } finally {
            server.stop(0);
        }

        assertThat(requested)
            .as("HTTP requests made while parsing a document whose DOCTYPE names a URL")
            .isEmpty();
    }

    private static String doctypeNaming(String dtdUrl) {
        return "<!DOCTYPE score-partwise SYSTEM \"" + dtdUrl + "\">";
    }

    @Test
    void testUnmarshalIgnoresAnExternalDoctype() throws Exception {
        var xml = marshalToString(sampleScore()).replace(DECLARATION, DECLARATION + "\n" + EXTERNAL_DOCTYPE);

        var parsed = MusicXmlSerializer.unmarshal(sourceFor(xml));

        assertThat(parsed.score().getPart()).hasSize(1);
    }

    @Test
    void testHardenedSaxFactoryIgnoresAnExternalDoctype() {
        var xml = DECLARATION + "\n" + EXTERNAL_DOCTYPE + "\n<score-partwise version=\"4.0\"/>\n";

        assertThatCode(() -> {
            var parser = SafeXmlParser.newHardenedFactory().newSAXParser();
            parser.parse(new InputSource(new StringReader(xml)), new DefaultHandler() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) {
                    return SafeXmlParser.emptyEntitySource();
                }
            });
        }).doesNotThrowAnyException();
    }

    @Test
    void testUnmarshalMalformedDocumentThrowsWithTheParseLocation() {
        var xml = DECLARATION + "\n<score-partwise version=\"4.0\">\n  <part-list>\n</score-partwise>\n";

        var thrown = assertThatThrownBy(() -> MusicXmlSerializer.unmarshal(sourceFor(xml)))
            .isInstanceOf(SAXException.class);

        thrown.cause().isInstanceOf(XMLStreamException.class);
        thrown.cause().satisfies(cause -> {
            var location = ((XMLStreamException) cause).getLocation();
            assertThat(location).isNotNull();
            assertThat(location.getLineNumber()).isPositive();
            assertThat(location.getColumnNumber()).isPositive();
        });
    }

    /**
     * An {@link InputSource} carrying no character stream, no byte stream and no system id
     * fails as I/O, never as a parse error.
     *
     * <p>Which exception it is decides what the user is told: {@code SongFileLoader} turns an
     * {@link IOException} into "this file could not be read" and a {@link SAXException} into
     * "this file is damaged", so a source with nothing in it to parse must not be reported as
     * a corrupt document. {@code MusicXmlReader.read(File)} always supplies a stream, but
     * {@code read(InputSource)} is public and delegates straight here, so this is the contract
     * any other caller gets.
     */
    @Test
    void testUnmarshalWithNoStreamAndNoSystemIdFailsAsIo() {
        assertThatThrownBy(() -> MusicXmlSerializer.unmarshal(new InputSource()))
            .isInstanceOf(IOException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String marshalToString(ScorePartwise score) {
        var buffer = new StringWriter();
        MusicXmlSerializer.marshal(score, new PrintWriter(buffer));
        return buffer.toString();
    }

    private static InputSource sourceFor(String xml) {
        return new InputSource(new StringReader(xml));
    }

    /**
     * The smallest schema-valid score that still exercises every shape this class
     * asserts on: nesting four levels deep, an element carrying only text, one
     * carrying attributes and text, and a childless element that carries attributes.
     */
    private ScorePartwise sampleScore() {
        var score = factory.createScorePartwise();
        score.setVersion("4.0");

        var supports = factory.createSupports();
        supports.setElement("accidental");
        supports.setType(YesNo.YES);

        var encoding = factory.createEncoding();
        encoding.getEncodingDateOrEncoderOrSoftware().add(factory.createEncodingSoftware("SongScribe"));
        encoding.getEncodingDateOrEncoderOrSoftware().add(factory.createEncodingSupports(supports));

        var creator = factory.createTypedText();
        creator.setType("composer");
        creator.setValue("Sri Chinmoy");

        var identification = factory.createIdentification();
        identification.getCreator().add(creator);
        identification.setEncoding(encoding);
        score.setIdentification(identification);

        var partName = factory.createPartName();
        partName.setValue("Music");

        var scorePart = factory.createScorePart();
        scorePart.setId("P1");
        scorePart.setPartName(partName);

        var partList = factory.createPartList();
        partList.getPartGroupOrScorePart().add(scorePart);
        score.setPartList(partList);

        var part = factory.createScorePartwisePart();
        // An IDREF: JAXB resolves the ScorePart instance to that part's id.
        part.setId(scorePart);
        score.getPart().add(part);

        var measure = factory.createScorePartwisePartMeasure();
        measure.setNumber("1");
        part.getMeasure().add(measure);

        measure.getNoteOrBackupOrForward().add(sampleAttributes());
        measure.getNoteOrBackupOrForward().add(sampleNote());

        return score;
    }

    private Attributes sampleAttributes() {
        var attributes = factory.createAttributes();
        attributes.setDivisions(BigDecimal.ONE);

        var key = factory.createKey();
        key.setFifths(BigInteger.ZERO);
        attributes.getKey().add(key);

        var time = factory.createTime();
        time.getTimeSignature().add(factory.createTimeBeats("4"));
        time.getTimeSignature().add(factory.createTimeBeatType("4"));
        attributes.getTime().add(time);

        var clef = factory.createClef();
        clef.setSign(ClefSign.G);
        clef.setLine(BigInteger.TWO);
        attributes.getClef().add(clef);

        return attributes;
    }

    private Note sampleNote() {
        var pitch = factory.createPitch();
        pitch.setStep(Step.C);
        pitch.setOctave(4);

        var noteType = factory.createNoteType();
        noteType.setValue("quarter");

        var note = factory.createNote();
        note.setPitch(pitch);
        note.setDuration(BigDecimal.ONE);
        note.setType(noteType);

        return note;
    }
}
