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
package songscribe.io;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Builds the SAX parser configuration shared by every reader that parses a user-opened file,
 * so neither reader can be secured while the other is forgotten.
 *
 * <p>A {@code DOCTYPE} is let through with nothing it names acted upon, not rejected: the checks
 * that tell a foreign file from a damaged one run from element callbacks, which a rejection
 * would preempt. The parser acts on everything above the root element before the first
 * callback, so only the configuration here defends against external-entity attacks.
 *
 * <p>Callers should also refuse to resolve entities at the handler level; see
 * {@link #emptyEntitySource()}.
 */
public final class SafeXmlParser {

    private SafeXmlParser() {}

    /**
     * Resolves nothing outside the document it is given. Namespace handling is left to the
     * caller: that is document policy, not hardening.
     */
    public static SAXParserFactory newHardenedFactory() {
        var factory = SAXParserFactory.newInstance();

        // Validating would send the parser after the document-type definition, which
        // is exactly what the settings below are here to prevent.
        factory.setValidating(false);

        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
        } catch (SAXNotRecognizedException | SAXNotSupportedException | ParserConfigurationException e) {
            throw new IllegalStateException("XML parser refused the security settings", e);
        }

        return factory;
    }

    /**
     * A handler's {@code resolveEntity} returns this so that "resolve nothing" does not rest on the
     * {@link #newHardenedFactory()} flags alone. Fresh per call because the parser consumes the
     * reader it is handed.
     */
    public static InputSource emptyEntitySource() {
        return new InputSource(new StringReader(""));
    }
}
