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

package songscribe.io.musicxml;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

/**
 * Test-side utility that compiles the bundled MusicXML 4.0 XSD once per JVM and exposes
 * {@link #validate(String)} and {@link #validate(Path)} for schema-checking writer output.
 * Not a JUnit test itself — used by the MusicXML round-trip test classes.
 *
 * <p>The XSD and its sibling imports live under {@code docs/musicxml-4.0-schema/}.
 * Pointing the {@link StreamSource} at the XSD file object sets the system ID
 * (base URI) automatically, so the schema factory resolves sibling imports
 * without a custom resolver.
 *
 * <p><b>Locating the schema.</b> The XSD is not a classpath resource, so it has to be
 * found on disk. It is searched for by walking up from this class's own code source —
 * the compiled test classes live inside the project tree — and only then from the
 * process working directory. Resolving it against the working directory alone would
 * tie every schema check to Gradle happening to start the test JVM at the project root.
 *
 * <p><b>Why the compile is lazy.</b> The schema is expensive to parse (the XSD pulls in
 * {@code .mod}/{@code .ent} imports), so it is held in a {@code static} field and built
 * once. Building it in a {@code static} initializer instead would turn a missing or
 * unreadable XSD into an {@code ExceptionInInitializerError} for the first caller and a
 * bare {@code NoClassDefFoundError} for the 25 that follow, none of which names the file.
 * Compiling on first use lets the failure say what is missing, every time it is asked.
 */
final class MusicXmlSchemaValidator {

    /** Where the XSD sits relative to the project root. */
    private static final String SCHEMA_PATH = "docs/musicxml-4.0-schema/musicxml.xsd";

    // Compiled once per JVM; the MusicXML 4.0 XSD with its .mod/.ent imports
    // is expensive to parse, so recompiling per instance would slow tests
    // significantly as the suite grows.
    private static @Nullable Schema schema = null;

    /**
     * Validates {@code xml} against the MusicXML 4.0 schema.
     *
     * @throws SAXException if the document violates the schema
     * @throws IOException  if an I/O error occurs during validation
     */
    void validate(String xml) throws SAXException, IOException {
        validate(new StreamSource(new StringReader(xml)));
    }

    /**
     * Validates the document in {@code file} against the MusicXML 4.0 schema.
     *
     * <p>Streams the file rather than reading it into a {@code String} first, so a
     * corpus-wide sweep does not materialize every document in memory.
     *
     * @throws SAXException if the document violates the schema
     * @throws IOException  if an I/O error occurs during validation
     */
    void validate(Path file) throws SAXException, IOException {
        try (var stream = Files.newInputStream(file)) {
            var source = new StreamSource(stream);
            source.setSystemId(file.toUri().toString());
            validate(source);
        }
    }

    private void validate(StreamSource source) throws SAXException, IOException {
        var validator = schema().newValidator();

        // The documents fed to this validator are not ours to trust with a network
        // connection. A real export from another program carries a DOCTYPE naming
        // musicxml.org, and a validator left at its defaults will fetch it — turning a
        // corpus sweep into one HTTP request per document, against a third party, from
        // the test suite. Deny every protocol to both external lookups.
        setIfSupported(validator::setProperty, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setIfSupported(validator::setProperty, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        // Nothing reaches this while the two properties above hold; it is what keeps a
        // document that does declare an external resource from failing outright.
        validator.setResourceResolver((type, namespaceUri, publicId, systemId, baseUri) -> null);

        validator.validate(source);
    }

    /**
     * The compiled schema, built on first use.
     *
     * @throws IllegalStateException if the XSD cannot be found or cannot be compiled;
     *         the message names the file that is missing or broken
     */
    private static synchronized Schema schema() {
        var compiled = schema;

        if (compiled != null) {
            return compiled;
        }

        var xsdFile = locateSchema();

        try {
            var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // The XSD needs no DTD at all, and its imports are the sibling xml.xsd and
            // xlink.xsd resolved relatively — so schema lookups are confined to the
            // filesystem and DTD lookups are refused outright. Without this the bundled
            // schema still compiles locally, but any future edit pointing an import at a
            // URL would start fetching it silently.
            setIfSupported(factory::setProperty, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            setIfSupported(factory::setProperty, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

            // StreamSource(File) sets the systemId to the file's URI, which lets
            // the factory resolve sibling .mod/.ent/.xsd imports locally.
            compiled = factory.newSchema(new StreamSource(xsdFile.toFile()));
        } catch (SAXException e) {
            throw new IllegalStateException("Failed to compile the MusicXML schema at " + xsdFile, e);
        }

        schema = compiled;
        return compiled;
    }

    /** A {@code setProperty} that may or may not know the name, as JAXP allows. */
    @FunctionalInterface
    private interface PropertySetter {
        void set(String name, Object value) throws SAXException;
    }

    /**
     * Applies a hardening property, tolerating an implementation that does not recognize
     * it. A parser that rejects the name is not thereby granted network access — it simply
     * has no such switch, and the resource resolver is what covers that case.
     */
    private static void setIfSupported(PropertySetter setter, String name, String value) {
        try {
            setter.set(name, value);
        } catch (SAXException | IllegalArgumentException | UnsupportedOperationException e) {
            // Not recognized here.
        }
    }

    /**
     * Finds {@value #SCHEMA_PATH} by walking up from this class's code source, then from
     * the process working directory.
     *
     * @throws IllegalStateException if no ancestor of either start point holds the XSD
     */
    private static Path locateSchema() {
        var searched = new ArrayList<Path>();

        for (var start : searchRoots()) {
            for (var directory = start; directory != null; directory = directory.getParent()) {
                var candidate = directory.resolve(SCHEMA_PATH);

                if (Files.isReadable(candidate)) {
                    return candidate;
                }
            }

            searched.add(start);
        }

        throw new IllegalStateException(
            "The MusicXML schema '" + SCHEMA_PATH + "' was not found above any of " + searched);
    }

    /** Directories to walk upwards from, most specific first. */
    private static List<Path> searchRoots() {
        var roots = new ArrayList<Path>();
        var codeSource = MusicXmlSchemaValidator.class.getProtectionDomain().getCodeSource();

        if (codeSource != null) {
            try {
                roots.add(Path.of(codeSource.getLocation().toURI()).toAbsolutePath());
            } catch (URISyntaxException | IllegalArgumentException e) {
                // Not a filesystem location, so there is nothing to walk up from here.
            }
        }

        roots.add(Path.of("").toAbsolutePath());
        return roots;
    }
}
