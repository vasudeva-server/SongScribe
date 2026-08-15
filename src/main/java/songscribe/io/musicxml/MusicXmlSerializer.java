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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.util.StreamWriterDelegate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.io.SafeXmlParser;

/**
 * The single JAXB seam between a {@link ScorePartwise} object graph and MusicXML
 * text. Everything above it works on objects; everything below it is XML.
 *
 * <p><b>Why not {@code org.audiveris.proxymusic.util.Marshalling}.</b> ProxyMusic's
 * own helper hand-writes a {@code <!DOCTYPE score-partwise PUBLIC ...>} line and
 * injects a comment before every part and every measure. SongScribe's files carry
 * neither, and the reader's provenance gate and the corpus fixtures both assume
 * today's shape: an XML declaration, then {@code <score-partwise version="4.0">}
 * with no namespace declaration, two-space indentation, no DOCTYPE, no comments.
 *
 * <p><b>Why the output goes through a stream-writer delegate.</b> JAXB hoists an
 * {@code xmlns:ns2="http://www.w3.org/1999/xlink"} declaration onto the root
 * element even for a document that contains no {@code xlink:} attribute, and the
 * JDK's {@code XMLStreamWriter} emits no whitespace of its own. {@link IndentingWriter}
 * does both jobs: it drops namespace declarations and owns every line break and
 * indent in the file.
 *
 * <p><b>Why marshalling is buffered.</b> {@link #marshal} builds the whole document
 * in memory and touches the caller's {@link PrintWriter} only once it is complete.
 * A failure part-way through a direct marshal would leave a truncated
 * {@code .musicxml} on disk while the save reported success. Documents are tens of
 * kilobytes, so the buffer costs nothing.
 */
public final class MusicXmlSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlSerializer.class);

    /** One indent level, matching the two-space indentation of every existing file. */
    private static final String INDENT = "  ";

    private static final String LINE_SEPARATOR = "\n";

    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private static final String ENCODING = "UTF-8";

    /** The JDK's name for the entity-expansion cap; StAX defines no standard one. */
    private static final String ENTITY_EXPANSION_LIMIT_PROPERTY = "jdk.xml.entityExpansionLimit";

    /**
     * Matches the JDK's own default. Far above anything a real song reaches, and far below
     * what it takes to exhaust memory.
     */
    private static final String ENTITY_EXPANSION_LIMIT = "64000";

    /**
     * Built once and shared. Context creation walks ProxyMusic's 355 generated
     * classes and is by far the most expensive part of the first save;
     * {@code JAXBContext} is thread-safe, while {@code Marshaller} and
     * {@code Unmarshaller} are not, so those are created per call.
     */
    private static final JAXBContext CONTEXT;

    static {
        try {
            CONTEXT = JAXBContext.newInstance(ScorePartwise.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to build the MusicXML JAXB context", e);
        }
    }

    /**
     * Reads nothing outside the document it is given, mirroring
     * {@link SafeXmlParser#newHardenedFactory()} for the StAX path — a
     * {@code DOCTYPE} is tolerated so a foreign file still reaches the checks that
     * diagnose it, but nothing the declaration names is ever fetched.
     */
    private static final XMLInputFactory INPUT_FACTORY = newHardenedInputFactory();

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    private MusicXmlSerializer() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds the JAXB context and exercises the marshalling path, so the cost is
     * paid on whatever thread calls this rather than on the first save.
     *
     * <p>{@code MainFrame.saveCurrentFile} calls the writer synchronously from a
     * Swing action, so without a warm-up the binding model would be built on the
     * event dispatch thread the first time the user saves.
     *
     * @throws IllegalStateException if the context or the marshalling path is broken
     */
    public static void warmUp() {
        marshal(new ScorePartwise(), new PrintWriter(Writer.nullWriter()));
    }

    /**
     * Writes {@code score} to {@code pw} as MusicXML text.
     *
     * <p>The document is marshalled into memory first; {@code pw} is written only
     * once the whole document exists, so a marshalling failure leaves it untouched
     * rather than holding a truncated document.
     *
     * @param score the score to write
     * @param pw    the writer to write through; neither flushed nor closed here
     * @throws IllegalStateException if the score cannot be marshalled
     */
    public static void marshal(ScorePartwise score, PrintWriter pw) {
        String document;

        try {
            document = marshalToString(score);
        } catch (JAXBException | XMLStreamException | RuntimeException e) {
            // Unchecked failures are caught alongside the declared ones because JAXB
            // does not confine itself to JAXBException — a graph it cannot serialize
            // surfaces as a ClassCastException from deep inside the runtime. Both are
            // the same event to a caller: nothing was written, and this is why.
            throw new IllegalStateException("Failed to marshal the MusicXML document", e);
        }

        pw.write(document);
    }

    /**
     * A parsed document, together with the two facts about it that the object graph
     * cannot report.
     *
     * <p>Both are read off the raw stream because {@link ScorePartwise} cannot express
     * either. A foreign root element unmarshals into an empty {@code ScorePartwise}
     * without complaint, so the graph cannot say what the document was actually rooted
     * at; and the schema declares {@code version} with a default of {@code "1.0"}, so
     * {@code getVersion()} substitutes that default and cannot separate an omitted
     * attribute from an explicit {@code version="1.0"} — which are rejected with
     * different diagnostics.
     *
     * <p>Reporting them rather than acting on them keeps the format policy in
     * {@code SongMapper}, where the rest of it lives.
     *
     * @param score       the parsed graph
     * @param rootElement the document's root element name, as written
     * @param version     the root {@code version} attribute as written, or {@code null}
     *                    if the document omits it
     */
    public record ParsedDocument(ScorePartwise score, String rootElement, @Nullable String version) {}

    /**
     * Parses {@code source} into a {@link ScorePartwise} graph.
     *
     * <p>Takes an {@link InputSource} rather than a {@link java.io.File} so that
     * {@code MusicXmlReader}'s two {@code read} overloads keep sharing one parse
     * path.
     *
     * @param source the MusicXML input to parse
     * @return the parsed score and the root facts the graph cannot carry
     * @throws IOException  if the input cannot be read
     * @throws SAXException if the document is malformed; the cause carries the
     *                      underlying parser's message with its line and column
     */
    public static ParsedDocument unmarshal(InputSource source) throws IOException, SAXException {
        try {
            var streamReader = INPUT_FACTORY.createXMLStreamReader(streamSourceFor(source));

            try {
                return unmarshal(streamReader);
            } finally {
                // Frees the parser's own buffers. Per the StAX contract this does not
                // touch the underlying stream, which belongs to whoever opened it.
                closeQuietly(streamReader);
            }
        } catch (JAXBException | XMLStreamException e) {
            throw new SAXException(parseFailureMessage(null, e), e);
        }
    }

    private static ParsedDocument unmarshal(XMLStreamReader streamReader)
        throws JAXBException, SAXException, XMLStreamException {
        while (streamReader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            if (!streamReader.hasNext()) {
                throw new SAXException("The MusicXML document has no root element");
            }

            streamReader.next();
        }

        // Read before unmarshalling, while the reader is still on the root tag.
        var rootElement = streamReader.getLocalName();
        var version = streamReader.getAttributeValue(null, MusicXmlTags.ATTR_VERSION);

        var unmarshaller = CONTEXT.createUnmarshaller();

        // Where the reader stood when a value was rejected. JAXB's own message names
        // the value but not the element it came from ("Not a number: xyz"), and the
        // load path's diagnostics are worth more than that.
        var location = new String[1];

        // JAXB's default is to swallow a value it cannot convert and leave the field
        // at its default, so a non-numeric <octave> would load as a valid song.
        //
        // Only conversion failures abort. Everything else a document can surprise JAXB
        // with — chiefly an element the schema does not allow there — stays tolerated,
        // matching the lenience the SAX reader was written for: a file may carry
        // constructs this program does not model, and refusing to open it over one of
        // them is worse than ignoring it. A value that is not the kind of thing it
        // claims to be is different in kind: there is nothing to ignore, only a number
        // that would silently become a default.
        unmarshaller.setEventHandler(event -> {
            if (!(event.getLinkedException() instanceof NumberFormatException)) {
                return true;
            }

            location[0] = describeLocation(streamReader);
            return false;
        });

        try {
            var score = unmarshaller.unmarshal(streamReader, ScorePartwise.class).getValue();
            return new ParsedDocument(score, rootElement, version);
        } catch (JAXBException e) {
            // JAXB wraps the real parse error — an XMLStreamException carrying the
            // line and column — in its linked exception. Dropping it would turn
            // every load-path diagnostic into "unmarshal failed".
            var cause = causeOf(e);
            throw new SAXException(parseFailureMessage(location[0], cause), cause);
        } catch (NumberFormatException e) {
            // Not every conversion reaches the event handler: a malformed decimal goes
            // straight to BigDecimal's constructor and leaves the unmarshaller
            // unwrapped, which would escape read()'s declared exceptions entirely.
            throw new SAXException(parseFailureMessage(describeLocation(streamReader), e), e);
        }
    }

    /**
     * Closes the stream reader, reporting rather than propagating a failure: this runs in
     * a {@code finally}, where a throw would replace the parse diagnostic the caller is
     * about to receive with a far less useful one.
     */
    private static void closeQuietly(XMLStreamReader streamReader) {
        try {
            streamReader.close();
        } catch (XMLStreamException e) {
            LOG.warn("Failed to close the MusicXML stream reader", e);
        }
    }

    /**
     * The message a load failure reports.
     *
     * <p>The underlying parser's own words are folded in rather than left on the cause.
     * They carry the line, the column and the limit or expected-element set that decided
     * the failure, and a caller that logs only {@code getMessage()} — which is most of
     * them — would otherwise report every malformed document identically.
     */
    private static String parseFailureMessage(@Nullable String location, @Nullable Throwable cause) {
        var message = new StringBuilder("Failed to parse the MusicXML document");

        if (location != null && !location.isEmpty()) {
            message.append(", at ").append(location);
        }

        var detail = cause == null ? null : cause.getMessage();

        if (detail != null && !detail.isBlank()) {
            message.append(": ").append(detail);
        }

        return message.toString();
    }

    /**
     * Where the reader is standing, named so a rejected value can be traced back to the
     * element it came from.
     *
     * <p>Attribute names are listed because an attribute's conversion failure never
     * reaches the event handler — the reader is still on the owning start tag, and the
     * exception carries only the offending text. This is diagnostic only: it changes what
     * a failure says, never what is accepted.
     */
    private static String describeLocation(XMLStreamReader reader) {
        try {
            var eventType = reader.getEventType();

            if (eventType != XMLStreamConstants.START_ELEMENT && eventType != XMLStreamConstants.END_ELEMENT) {
                return "";
            }

            var description = new StringBuilder("<").append(reader.getLocalName()).append('>');

            if (eventType == XMLStreamConstants.START_ELEMENT && reader.getAttributeCount() > 0) {
                var names = new ArrayList<String>();

                for (var i = 0; i < reader.getAttributeCount(); i++) {
                    names.add(reader.getAttributeLocalName(i));
                }

                description.append(" (attributes: ").append(String.join(", ", names)).append(')');
            }

            return description.toString();
        } catch (RuntimeException e) {
            // The reader is in no position to describe; an unlocated message still reports
            // the failure, which matters more than where it happened.
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Marshalling
    // -------------------------------------------------------------------------

    private static String marshalToString(ScorePartwise score) throws JAXBException, XMLStreamException {
        var buffer = new StringWriter();

        // The declaration is ours, not JAXB's: JAXB_FRAGMENT suppresses the one it
        // would write, which is the only way to control its exact text.
        buffer.write(XML_DECLARATION);
        buffer.write(LINE_SEPARATOR);

        var streamWriter = OUTPUT_FACTORY.createXMLStreamWriter(buffer);

        // Two filters in a chain: the marshaller writes into the reorderer, which replays
        // ordinary XML calls into the formatter, which writes the text.
        var formatter = new IndentingWriter(streamWriter);
        var reorderer = new MetronomeReorderingWriter(formatter);

        var marshaller = CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        marshaller.setProperty(Marshaller.JAXB_ENCODING, ENCODING);
        marshaller.marshal(score, reorderer);

        // Closes the document's last element, which the JDK writer holds open while
        // it is still undecided between `/>` and `</name>`.
        reorderer.writeEndDocument();
        reorderer.flush();

        buffer.write(LINE_SEPARATOR);
        return buffer.toString();
    }

    /**
     * Suppresses namespace declarations and supplies all of the document's
     * whitespace.
     *
     * <p><b>Namespaces.</b> The MusicXML 4.0 schema has no target namespace, so
     * every element is unqualified — yet JAXB still writes an
     * {@code xmlns:ns2="http://www.w3.org/1999/xlink"} declaration onto the root
     * element of a document with no {@code xlink:} attribute in it. The declaration
     * is valid but appears in no file this project has ever written, so the
     * namespace calls are dropped.
     *
     * <p><b>Whitespace.</b> The JDK's stream writer emits none at all, so every line
     * break and every indent in a saved file originates here.
     *
     * <p><b>Empty elements.</b> A start tag is held back until its first child or
     * text arrives. An element that ends with neither is written as {@code <foo/>}
     * instead of {@code <foo></foo>}.
     *
     * <p>Formatting is all this does. The one place the generated model cannot express a
     * schema-valid document is {@code <metronome>}, and restoring that order is a separate
     * filter above this one — see {@link MetronomeReorderingWriter}.
     */
    private static final class IndentingWriter extends StreamWriterDelegate {

        private record PendingAttribute(String localName, String value) {}

        /** A start tag seen but not yet written, held until its content decides its shape. */
        @Nullable
        private String pendingElement = null;

        private final List<PendingAttribute> pendingAttributes = new ArrayList<>();

        /** Depth of elements whose start tag has been written and whose end tag has not. */
        private int depth = 0;

        /**
         * One entry per open element: whether it has had a child element written into
         * it. An element with child elements gets its end tag on its own indented
         * line; one holding only text keeps its end tag on the same line.
         */
        private final List<Boolean> hasChildElements = new ArrayList<>();

        /** False once anything has been written, so the root gets no leading blank line. */
        private boolean atStart = true;

        IndentingWriter(XMLStreamWriter parent) {
            super(parent);
        }

        // --- namespaces: dropped, see the class doc ---------------------------

        @Override
        public void writeNamespace(String prefix, String namespaceURI) {
            // Intentionally empty.
        }

        @Override
        public void writeDefaultNamespace(String namespaceURI) {
            // Intentionally empty.
        }

        @Override
        public void setPrefix(String prefix, String uri) {
            // Intentionally empty.
        }

        @Override
        public void setDefaultNamespace(String uri) {
            // Intentionally empty.
        }

        // --- elements ---------------------------------------------------------

        @Override
        public void writeStartElement(String localName) throws XMLStreamException {
            openPendingElement();
            pendingElement = localName;
            pendingAttributes.clear();
        }

        @Override
        public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
            writeStartElement(localName);
        }

        @Override
        public void writeStartElement(
            String prefix,
            String localName,
            String namespaceURI
        ) throws XMLStreamException {
            writeStartElement(localName);
        }

        @Override
        public void writeEmptyElement(String localName) throws XMLStreamException {
            writeStartElement(localName);
            writeEndElement();
        }

        @Override
        public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
            writeEmptyElement(localName);
        }

        @Override
        public void writeEmptyElement(
            String prefix,
            String localName,
            String namespaceURI
        ) throws XMLStreamException {
            writeEmptyElement(localName);
        }

        @Override
        public void writeEndElement() throws XMLStreamException {
            var pending = pendingElement;

            if (pending != null) {
                // Nothing was written into it, so it collapses to <foo/>.
                indentTo(depth);
                recordChildOfCurrentElement();
                super.writeEmptyElement(pending);
                writePendingAttributes();
                pendingElement = null;
                return;
            }

            depth--;

            if (hasChildElements.remove(depth)) {
                indentTo(depth);
            }

            super.writeEndElement();
        }

        // --- attributes and text ----------------------------------------------

        @Override
        public void writeAttribute(String localName, String value) throws XMLStreamException {
            if (pendingElement == null) {
                super.writeAttribute(localName, value);
                return;
            }

            pendingAttributes.add(new PendingAttribute(localName, value));
        }

        @Override
        public void writeAttribute(
            String namespaceURI,
            String localName,
            String value
        ) throws XMLStreamException {
            writeAttribute(qualify(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI, localName), value);
        }

        @Override
        public void writeAttribute(
            String prefix,
            String namespaceURI,
            String localName,
            String value
        ) throws XMLStreamException {
            writeAttribute(qualify(prefix, namespaceURI, localName), value);
        }

        /**
         * The name an attribute must be written under once namespace declarations are
         * being dropped.
         *
         * <p>Dropping the declaration is right for the xlink namespace JAXB hoists, whose
         * prefix this document never uses. It is wrong for {@code xml:}, which is bound to
         * its namespace by the XML specification itself, needs no declaration, and is the
         * only legal spelling of the attribute: {@code <lyric-language>} carries
         * {@code xml:lang}, and writing it as a bare {@code lang} makes every document we
         * produce fail schema validation.
         */
        private static String qualify(String prefix, String namespaceURI, String localName) {
            if (XMLConstants.XML_NS_URI.equals(namespaceURI)) {
                return XMLConstants.XML_NS_PREFIX + ':' + localName;
            }

            if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
                return prefix + ':' + localName;
            }

            return localName;
        }

        @Override
        public void writeCharacters(String text) throws XMLStreamException {
            if (text.isEmpty()) {
                return;
            }

            openPendingElement();
            super.writeCharacters(text);
        }

        @Override
        public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
            writeCharacters(new String(text, start, len));
        }

        // --- helpers ------------------------------------------------------------

        /**
         * Writes the held-back start tag, now that content has arrived for it, and
         * makes it the current element.
         */
        private void openPendingElement() throws XMLStreamException {
            var pending = pendingElement;

            if (pending == null) {
                return;
            }

            indentTo(depth);
            recordChildOfCurrentElement();
            super.writeStartElement(pending);
            writePendingAttributes();
            pendingElement = null;
            hasChildElements.add(false);
            depth++;
        }

        private void writePendingAttributes() throws XMLStreamException {
            for (var attribute : pendingAttributes) {
                super.writeAttribute(attribute.localName(), attribute.value());
            }

            pendingAttributes.clear();
        }

        /**
         * Notes that the element now being written is a child element of the one
         * enclosing it, which decides whether that enclosing element's end tag goes
         * on its own line.
         */
        private void recordChildOfCurrentElement() {
            if (depth > 0) {
                hasChildElements.set(depth - 1, true);
            }
        }

        private void indentTo(int level) throws XMLStreamException {
            if (atStart) {
                atStart = false;
                return;
            }

            super.writeCharacters(LINE_SEPARATOR);
            super.writeCharacters(INDENT.repeat(level));
        }
    }

    /**
     * Writes a {@code <metronome>}'s children in the order the schema requires.
     *
     * <p>The schema's content model is
     * {@code metronome-note+, (metronome-relation, metronome-note+)?} — the relation sits
     * <em>between</em> the two note groups. XJC collapsed both groups into a single
     * {@code List<MetronomeNote>} and made the relation a separate property, and the
     * generated {@code propOrder} puts {@code metronomeRelation} last. So however
     * {@code DirectionBuilder.buildMetricModulationDirection} builds the graph, JAXB
     * marshals {@code <metronome-note/><metronome-note/><metronome-relation/>}, which
     * Xerces rejects with <em>"Element 'metronome': Missing child element(s). Expected is (
     * metronome-note )"</em>. The graph cannot say where the boundary was, so the order is
     * restored here, on the way out.
     *
     * <p>The relation is moved to sit immediately before the <em>last</em>
     * {@code <metronome-note>}, which is the boundary for every metric modulation this
     * program writes: {@code DirectionBuilder} emits exactly one note on each side of the
     * relation. A document with several notes per side has no boundary the flattened list
     * still records, and none is invented here.
     *
     * <p>This is a filter above the formatter, not part of it. Between a
     * {@code <metronome>} and its end tag it records each call instead of passing it on,
     * then replays the recorded calls into the writer beneath in the order it wants;
     * everything else goes straight through. Neither class has to know what the other does.
     */
    private static final class MetronomeReorderingWriter extends StreamWriterDelegate {

        /** A recorded write, replayed once the child order is known. */
        @FunctionalInterface
        private interface BufferedCall {
            void replay(XMLStreamWriter writer) throws XMLStreamException;
        }

        /** One child element of the {@code <metronome>}, as the calls that produce it. */
        private record Child(String name, List<BufferedCall> calls) {}

        private final XMLStreamWriter formatter;

        /** The children recorded so far, or null when no {@code <metronome>} is open. */
        @Nullable
        private List<Child> children = null;

        /** Nesting depth below the {@code <metronome>}; 0 between its own children. */
        private int depth = 0;

        MetronomeReorderingWriter(XMLStreamWriter formatter) {
            super(formatter);
            this.formatter = formatter;
        }

        // --- elements ---------------------------------------------------------

        @Override
        public void writeStartElement(String localName) throws XMLStreamException {
            var recorded = children;

            if (recorded == null) {
                super.writeStartElement(localName);

                if (MusicXmlTags.METRONOME.equals(localName)) {
                    // Its own start tag is through; from here to the matching end tag the
                    // children are recorded rather than written.
                    children = new ArrayList<>();
                    depth = 0;
                }

                return;
            }

            if (depth == 0) {
                recorded.add(new Child(localName, new ArrayList<>()));
            }

            depth++;
            record(writer -> writer.writeStartElement(localName));
        }

        @Override
        public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
            writeStartElement(localName);
        }

        @Override
        public void writeStartElement(
            String prefix,
            String localName,
            String namespaceURI
        ) throws XMLStreamException {
            writeStartElement(localName);
        }

        @Override
        public void writeEmptyElement(String localName) throws XMLStreamException {
            // The formatter collapses an element written with no content to <foo/>, so an
            // empty element needs no recorded form of its own.
            writeStartElement(localName);
            writeEndElement();
        }

        @Override
        public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
            writeEmptyElement(localName);
        }

        @Override
        public void writeEmptyElement(
            String prefix,
            String localName,
            String namespaceURI
        ) throws XMLStreamException {
            writeEmptyElement(localName);
        }

        @Override
        public void writeEndElement() throws XMLStreamException {
            var recorded = children;

            if (recorded == null) {
                super.writeEndElement();
                return;
            }

            if (depth > 0) {
                record(XMLStreamWriter::writeEndElement);
                depth--;
                return;
            }

            // The </metronome> itself: replay the children in schema order, then close.
            children = null;

            for (var call : orderedCalls(recorded)) {
                call.replay(formatter);
            }

            super.writeEndElement();
        }

        // --- attributes and text ----------------------------------------------

        @Override
        public void writeAttribute(String localName, String value) throws XMLStreamException {
            if (!record(writer -> writer.writeAttribute(localName, value))) {
                super.writeAttribute(localName, value);
            }
        }

        @Override
        public void writeAttribute(
            String namespaceURI,
            String localName,
            String value
        ) throws XMLStreamException {
            if (!record(writer -> writer.writeAttribute(namespaceURI, localName, value))) {
                super.writeAttribute(namespaceURI, localName, value);
            }
        }

        @Override
        public void writeAttribute(
            String prefix,
            String namespaceURI,
            String localName,
            String value
        ) throws XMLStreamException {
            if (!record(writer -> writer.writeAttribute(prefix, namespaceURI, localName, value))) {
                super.writeAttribute(prefix, namespaceURI, localName, value);
            }
        }

        @Override
        public void writeCharacters(String text) throws XMLStreamException {
            if (!record(writer -> writer.writeCharacters(text))) {
                super.writeCharacters(text);
            }
        }

        @Override
        public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
            writeCharacters(new String(text, start, len));
        }

        // --- helpers ------------------------------------------------------------

        /**
         * Records {@code call} when it belongs to a child of an open {@code <metronome>},
         * and reports whether it did. A call arriving at the metronome's own level — its
         * attributes — belongs to no child and goes straight through.
         */
        private boolean record(BufferedCall call) {
            var recorded = children;

            if (recorded == null || depth == 0) {
                return false;
            }

            recorded.getLast().calls().add(call);
            return true;
        }

        /** Every recorded call, with the children in the order the schema requires. */
        private static List<BufferedCall> orderedCalls(List<Child> children) {
            var relationIndex = indexOfChild(children, MusicXmlTags.METRONOME_RELATION);
            var lastNoteIndex = lastIndexOfChild(children, MusicXmlTags.METRONOME_NOTE);
            var reordered = new ArrayList<>(children);

            if (relationIndex >= 0 && lastNoteIndex >= 0 && relationIndex > lastNoteIndex) {
                reordered.add(lastNoteIndex, reordered.remove(relationIndex));
            }

            return reordered.stream().flatMap(child -> child.calls().stream()).toList();
        }

        private static int indexOfChild(List<Child> children, String name) {
            for (var i = 0; i < children.size(); i++) {
                if (children.get(i).name().equals(name)) {
                    return i;
                }
            }

            return -1;
        }

        private static int lastIndexOfChild(List<Child> children, String name) {
            for (var i = children.size() - 1; i >= 0; i--) {
                if (children.get(i).name().equals(name)) {
                    return i;
                }
            }

            return -1;
        }
    }

    // -------------------------------------------------------------------------
    // Unmarshalling
    // -------------------------------------------------------------------------

    /**
     * The StAX equivalent of {@link SafeXmlParser#newHardenedFactory()}: a
     * {@code DOCTYPE} is read but nothing it names is fetched, so a crafted song
     * file cannot reach the network or the filesystem.
     *
     * <p>Rejecting the declaration outright is not an option for the same reason it
     * is not on the SAX path — every other notation program writes one, and aborting
     * the parse before the root element would report a perfectly good foreign export
     * as damaged instead of as foreign.
     *
     * @throws IllegalStateException if the parser refuses any of the hardening,
     *         which would leave the application parsing user files unprotected
     */
    private static XMLInputFactory newHardenedInputFactory() {
        var factory = XMLInputFactory.newInstance();

        try {
            // Read the declaration rather than skip it. Skipping leaves the entities it
            // declares undeclared, so a document referring to one fails as malformed
            // instead of behaving the way the SAX path behaves — an internal entity is
            // expanded, an external one is dropped and leaves its element empty.
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
            // Declare it, never fetch it. This is what empties an external entity's
            // element rather than filling it with whatever the reference points at.
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("XML parser refused the security settings", e);
        }

        // Caps entity expansion, which bounds the one thing a tolerated DOCTYPE can still
        // do: declare a shortcut referring to ten more, nested deeply enough to expand a
        // small file into gigabytes. Asked for explicitly rather than left to the runtime's
        // default, but not required — StAX has no standard name for it, so an
        // implementation that does not recognize this one still parses, under whatever
        // default it applies.
        try {
            factory.setProperty(ENTITY_EXPANSION_LIMIT_PROPERTY, ENTITY_EXPANSION_LIMIT);
        } catch (IllegalArgumentException e) {
            // Not recognized here; the runtime's own cap is what bounds expansion.
        }

        // Every external reference resolves to nothing at all: the declaration is read,
        // and whatever it names is never opened.
        //
        // Deliberately not ACCESS_EXTERNAL_DTD="": with DTD support on, an emptied
        // protocol allow-list makes an external declaration a fatal parse error, which
        // would report a foreign export — they all carry a DOCTYPE naming musicxml.org —
        // as damaged rather than as foreign. Returning nothing refuses the fetch just as
        // completely without failing the parse.
        //
        // An empty stream, not an empty Reader. XMLResolver is specified to return an
        // InputStream, XMLStreamReader or Source; anything else is discarded silently and
        // the parser then resolves the system id ITSELF — which means a real HTTP request
        // to whatever the document names, from whatever machine opened the file. Measured,
        // not theorized: a Reader here fetches http://www.musicxml.org/dtds/partwise.dtd
        // for any export carrying the standard MusicXML DOCTYPE. The visible symptom is a
        // parse failure reading "The markup declarations contained or pointed to by the
        // document type declaration must be well-formed" — that message means content came
        // back, so treat it as evidence of a fetch, not as a malformed fixture.
        factory.setXMLResolver(
            (publicID, systemID, baseURI, namespace) -> new ByteArrayInputStream(new byte[0]));

        return factory;
    }

    /**
     * Converts a SAX {@link InputSource} to the {@link StreamSource} StAX consumes,
     * preserving whichever of the three forms the caller supplied.
     *
     * <p>A stream the caller supplied belongs to the caller: this class reads it and
     * never closes it. {@code MusicXmlReader.read(File)} is the one place that opens a
     * file for parsing, and it closes what it opened.
     */
    private static StreamSource streamSourceFor(InputSource source) throws IOException {
        var characterStream = source.getCharacterStream();
        var byteStream = source.getByteStream();
        var systemId = source.getSystemId();

        StreamSource streamSource;

        if (characterStream != null) {
            streamSource = new StreamSource(characterStream);
        } else if (byteStream != null) {
            streamSource = new StreamSource(byteStream);
        } else if (systemId != null) {
            streamSource = new StreamSource(systemId);
        } else {
            throw new IOException("MusicXML input source carries no stream and no system id");
        }

        if (systemId != null) {
            streamSource.setSystemId(systemId);
        }

        return streamSource;
    }

    /**
     * The exception worth reporting from a {@link JAXBException}: its linked
     * exception when there is one, since that is what carries the parser's message
     * with its line and column.
     */
    private static Exception causeOf(JAXBException e) {
        var linked = e.getLinkedException();

        if (linked instanceof Exception exception) {
            return exception;
        }

        return e;
    }
}
