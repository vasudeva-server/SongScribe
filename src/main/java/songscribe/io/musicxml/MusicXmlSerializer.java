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
 * Converts between a {@link ScorePartwise} object graph and MusicXML
 * text. Everything above it works on objects; everything below it is XML.
 *
 * <p>ProxyMusic's own {@code Marshalling} helper is not used: it writes a {@code DOCTYPE} and
 * comments, and the reader's provenance gate and the corpus fixtures assume files without them.
 */
public final class MusicXmlSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlSerializer.class);

    /** Matches the two-space indentation of every existing file. */
    private static final String INDENT = "  ";

    private static final String LINE_SEPARATOR = "\n";

    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private static final String ENCODING = "UTF-8";

    private static final String ENTITY_EXPANSION_LIMIT_PROPERTY = "jdk.xml.entityExpansionLimit";

    private static final String ENTITY_EXPANSION_LIMIT = "64000";

    /**
     * Shared because creation is expensive. {@code Marshaller} and {@code Unmarshaller} are not
     * thread-safe, so they are created per call.
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
     * Reads nothing outside the document it is given, as {@link SafeXmlParser#newHardenedFactory()}
     * does for SAX.
     */
    private static final XMLInputFactory INPUT_FACTORY = newHardenedInputFactory();

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();

    private MusicXmlSerializer() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called ahead of time because saving runs on the event dispatch thread, where the first
     * save would otherwise build the binding model.
     */
    public static void warmUp() {
        marshal(new ScorePartwise(), new PrintWriter(Writer.nullWriter()));
    }

    /**
     * {@code pw} is written only once the whole document exists, so a marshalling failure leaves
     * it untouched. It is neither flushed nor closed here.
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
     * A parsed document, together with the root element name and {@code version} attribute that
     * the object graph cannot report.
     *
     * @param version {@code null} if the document omits the root {@code version} attribute
     */
    public record ParsedDocument(ScorePartwise score, String rootElement, @Nullable String version) {}

    /**
     * Takes an {@link InputSource} so that {@code MusicXmlReader}'s two {@code read} overloads
     * share one parse path.
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
     * Never throws: it runs in a {@code finally}, where a throw would replace the parse
     * diagnostic.
     */
    private static void closeQuietly(XMLStreamReader streamReader) {
        try {
            streamReader.close();
        } catch (XMLStreamException e) {
            LOG.warn("Failed to close the MusicXML stream reader", e);
        }
    }

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
     * Diagnostic only: it changes what a failure says, never what is accepted.
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
     * Suppresses namespace declarations and supplies all of the document's whitespace. JAXB
     * hoists an unused {@code xlink} declaration onto the root, which no file this project
     * writes carries.
     */
    private static final class IndentingWriter extends StreamWriterDelegate {

        private record PendingAttribute(String localName, String value) {}

        @Nullable
        private String pendingElement = null;

        private final List<PendingAttribute> pendingAttributes = new ArrayList<>();

        private int depth = 0;

        private final List<Boolean> hasChildElements = new ArrayList<>();

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
         * {@code xml:} needs no declaration and is the only legal spelling of
         * {@code xml:lang}; a bare {@code lang} fails schema validation.
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
     * Restores the schema order of {@code <metronome>}: the generated model marshals
     * {@code metronome-relation} after every {@code metronome-note}, which the schema rejects.
     * The relation is moved before the <em>last</em> note, the boundary for every modulation
     * this program writes ({@code DirectionBuilder} emits one note on each side); with several
     * notes per side no boundary is recorded and none is invented.
     */
    private static final class MetronomeReorderingWriter extends StreamWriterDelegate {

        @FunctionalInterface
        private interface BufferedCall {
            void replay(XMLStreamWriter writer) throws XMLStreamException;
        }

        private record Child(String name, List<BufferedCall> calls) {}

        private final XMLStreamWriter formatter;

        /** {@code null} when no {@code <metronome>} is open. */
        @Nullable
        private List<Child> children = null;

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

        private boolean record(BufferedCall call) {
            var recorded = children;

            if (recorded == null || depth == 0) {
                return false;
            }

            recorded.getLast().calls().add(call);
            return true;
        }

        private static List<BufferedCall> orderedCalls(List<Child> children) {
            var relationIndex = indexOfChild(children, MusicXmlTags.METRONOME_RELATION);
            var lastNoteIndex = lastIndexOfChild(children, MusicXmlTags.METRONOME_NOTE);
            var reordered = new ArrayList<>(children);

            if (relationIndex != null && lastNoteIndex != null && relationIndex > lastNoteIndex) {
                reordered.add(lastNoteIndex, reordered.remove((int) relationIndex));
            }

            return reordered.stream().flatMap(child -> child.calls().stream()).toList();
        }

        private static @Nullable Integer indexOfChild(List<Child> children, String name) {
            for (var i = 0; i < children.size(); i++) {
                if (children.get(i).name().equals(name)) {
                    return i;
                }
            }

            return null;
        }

        private static @Nullable Integer lastIndexOfChild(List<Child> children, String name) {
            for (var i = children.size() - 1; i >= 0; i--) {
                if (children.get(i).name().equals(name)) {
                    return i;
                }
            }

            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Unmarshalling
    // -------------------------------------------------------------------------

    /**
     * The StAX equivalent of {@link SafeXmlParser#newHardenedFactory()}: a {@code DOCTYPE} is
     * read but nothing it names is fetched. Rejecting it would report a foreign export as
     * damaged.
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
     * A stream the caller supplied belongs to the caller: this class never closes it.
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

    private static Exception causeOf(JAXBException e) {
        var linked = e.getLinkedException();

        if (linked instanceof Exception exception) {
            return exception;
        }

        return e;
    }
}
