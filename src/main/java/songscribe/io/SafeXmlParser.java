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
 * Builds the SAX parser configuration shared by every reader that parses a file
 * the user opened — the MusicXML reader and the legacy {@code .mssw} reader.
 * Both parse whatever a user hands them, so both need the same hardening; keeping
 * it here means neither can be secured while the other is forgotten.
 *
 * <p><b>What a {@code DOCTYPE} is and why it needs handling.</b> An XML file may
 * carry a {@code <!DOCTYPE ...>} line between the XML declaration and the root
 * element. That line can name a document-type definition to fetch over the
 * network, and can define text shortcuts whose value is the contents of some
 * other file on the machine. Substituting such a shortcut into the document is
 * the XML External Entity attack: a crafted song file walks off with whatever it
 * names.
 *
 * <p>SongScribe never needs anything a {@code DOCTYPE} declares, so the goal is
 * to ignore it. There is no single switch for that — a parser either rejects the
 * file outright or processes the declaration — so "ignore" is assembled from the
 * individual refusals below.
 *
 * <p><b>Why rejecting the declaration outright is not an option.</b> Our writer
 * never emits a {@code DOCTYPE}, but every mainstream notation program does.
 * Rejecting it aborted the parse before the root element was seen, and the
 * checks that tell a foreign file from a damaged one all run from the element
 * callbacks. A user opening a perfectly good Finale export was told the file was
 * damaged. Letting the declaration through — with nothing it names acted upon —
 * lets those checks run and produce the right diagnosis.
 *
 * <p><b>Why the settings carry the weight, not the checks.</b> The parser reads
 * and acts on everything above the root element before a reader's first callback
 * fires. Rejecting a foreign document quickly is a fast diagnosis, not a defense:
 * by then the parser has already had its chance to fetch a URL or splice in a
 * file. Only the configuration below prevents that.
 *
 * <p>Callers should also refuse to resolve entities at the handler level; see
 * {@link #emptyEntitySource()}.
 */
public final class SafeXmlParser {

    private SafeXmlParser() {}

    /**
     * Returns a non-validating parser factory that resolves nothing outside the
     * document it is given. Namespace handling is left to the caller — that is
     * document policy, not hardening.
     *
     * <p>The three feature switches are what actually block external resolution:
     * no general or parameter entity is fetched, and the external part of a
     * document-type definition is never loaded. Secure processing is a backstop —
     * it denies every protocol to the external-definition lookups, so an
     * implementation that ignored one of the switches still could not reach the
     * network or the filesystem. It also caps entity expansion, which bounds the
     * one thing a tolerated {@code DOCTYPE} can still do: define a shortcut that
     * refers to ten more, nested deeply enough to expand a small file into
     * gigabytes. (Recent runtimes impose those caps by default; asking for them
     * explicitly does not rely on that staying true.)
     *
     * @throws IllegalStateException if the parser rejects any of the hardening,
     *         which would leave the application parsing user files unprotected
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
     * An empty stand-in for any entity a document asks the parser to resolve.
     *
     * <p>A handler that extends {@code DefaultHandler} and overrides
     * {@code resolveEntity} to return this makes "resolve nothing outside the
     * document" our own code's decision rather than an inference from three
     * vendor feature flags behaving as documented. Nothing reaches it today —
     * the flags in {@link #newHardenedFactory()} mean the parser never asks — so
     * it earns its keep only if one of them is ever flipped or stops working.
     *
     * <p>Returns a fresh source per call because the parser consumes the reader
     * it is handed.
     */
    public static InputSource emptyEntitySource() {
        return new InputSource(new StringReader(""));
    }
}
