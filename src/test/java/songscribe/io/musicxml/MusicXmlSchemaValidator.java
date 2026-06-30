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
import java.nio.file.Paths;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

/**
 * Test-side utility that compiles the bundled MusicXML 4.0 XSD once (via a
 * {@code static} field) and exposes a {@link #validate(String)} method for
 * schema-checking writer output.  Not a JUnit test itself — used by the
 * MusicXML round-trip test classes.
 *
 * <p>The XSD and its sibling imports live under {@code docs/musicxml-4.0-schema/}.
 * Pointing the {@link StreamSource} at the XSD file object sets the system ID
 * (base URI) automatically, so the schema factory resolves sibling imports
 * without a custom resolver.
 *
 * <p>The compiled {@link Schema} is held in a {@code static} field so the
 * expensive MusicXML 4.0 XSD parse (which includes {@code .mod}/{@code .ent}
 * imports) happens only once per JVM, regardless of how many validator
 * instances are created.
 */
final class MusicXmlSchemaValidator {

    private static final String SCHEMA_PATH = "docs/musicxml-4.0-schema/musicxml.xsd";

    // Compiled once per JVM; the MusicXML 4.0 XSD with its .mod/.ent imports
    // is expensive to parse, so recompiling per instance would slow tests
    // significantly as the suite grows.
    private static final Schema SCHEMA;

    static {
        try {
            var xsdFile = Paths.get(SCHEMA_PATH).toFile();
            var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // StreamSource(File) sets the systemId to the file's URI, which lets
            // the factory resolve sibling .mod/.ent/.xsd imports locally.
            SCHEMA = factory.newSchema(new StreamSource(xsdFile));
        } catch (SAXException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    MusicXmlSchemaValidator() {}

    /**
     * Validates {@code xml} against the MusicXML 4.0 schema.
     *
     * @throws SAXException if the document violates the schema
     * @throws IOException  if an I/O error occurs during validation
     */
    void validate(String xml) throws SAXException, IOException {
        Validator validator = SCHEMA.newValidator();
        validator.validate(new StreamSource(new StringReader(xml)));
    }
}
