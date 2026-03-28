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

package songscribe;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;

import javax.swing.UIManager;
import javax.xml.parsers.SAXParserFactory;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.xml.sax.InputSource;

import songscribe.io.CompositionIO;
import songscribe.io.CompositionLoader;
import songscribe.music.Composition;
import songscribe.ui.OptionDialogs;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for unit tests. Suppresses modal error dialogs
 * so tests don't block on user interaction.
 */
public abstract class UnitTest {

    private static volatile boolean bannerShown = false;
    private static volatile boolean flatLafInstalled = false;

    @BeforeAll
    static void suppressDialogs() {
        OptionDialogs.setSuppressDialogs(true);

        if (!bannerShown) {
            bannerShown = true;
            SongScribe.logBanner("SongScribe (Unit Tests)");
        }
    }

    /**
     * Installs a FlatLaf theme and registers the production FlatLaf.properties,
     * making all {@code SongScribe.*} properties available via {@code UIManager}
     * (and therefore via {@link songscribe.ui.FlatLafProps#get}).
     *
     * <p>Call from a {@code @BeforeAll} method in test classes that need
     * FlatLaf properties. Safe to call multiple times; only the first
     * invocation performs the actual setup.
     */
    protected static void installFlatLafDefaults() throws Exception {
        if (flatLafInstalled) {
            return;
        }

        FlatLaf.registerCustomDefaultsSource("songscribe");
        UIManager.setLookAndFeel(new FlatLightLaf());
        flatLafInstalled = true;
    }

    /**
     * Loads a fixture file and returns the parsed composition.
     * Fixture files live in {@code src/test/resources/fixtures/{name}.mssw}.
     */
    public static Composition loadFixture(String fixtureName) throws IllegalArgumentException, java.io.IOException, org.xml.sax.SAXException, URISyntaxException {
        var url = UnitTest.class.getClassLoader().getResource("fixtures/" + fixtureName + ".mssw");

        if (url == null) {
            throw new IllegalArgumentException("Fixture not found: " + fixtureName);
        }

        return CompositionLoader.load(new File(url.toURI()));
    }

    /**
     * Serializes a composition to XML and parses it back, verifying round-trip fidelity.
     */
    public static Composition roundTrip(Composition original) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        CompositionIO.writeComposition(original, pw);
        pw.flush();
        var xml = sw.toString();

        var factory = SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        var reader = new CompositionIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);

        return reader.getComposition();
    }
}
