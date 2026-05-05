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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;

import javax.swing.UIManager;
import javax.xml.parsers.SAXParserFactory;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.xml.sax.InputSource;

import songscribe.io.SongIO;
import songscribe.io.SongLoader;
import songscribe.music.Line;
import songscribe.music.Song;
import songscribe.ui.OptionDialogs;

import org.junit.jupiter.api.BeforeAll;
import org.xml.sax.SAXException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for unit tests. Suppresses modal error dialogs
 * so tests don't block on user interaction.
 */
public abstract class UnitTest {

    private static volatile boolean bannerShown = false;
    private static volatile boolean flatLafInstalled = false;

    @BeforeAll
    static void suppressDialogs() throws Exception {
        OptionDialogs.setSuppressDialogs(true);
        installFlatLafDefaults();

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
     * Loads a fixture file and returns the parsed song.
     * Fixture files live in {@code src/test/resources/fixtures/{name}.mssw}.
     */
    public static Song loadFixture(String fixtureName) throws IllegalArgumentException, IOException, SAXException, URISyntaxException {
        var url = UnitTest.class.getClassLoader().getResource("fixtures/" + fixtureName + ".mssw");

        if (url == null) {
            throw new IllegalArgumentException("Fixture not found: " + fixtureName);
        }

        return SongLoader.load(new File(url.toURI()));
    }

    /**
     * Serializes a song to XML and parses it back, verifying round-trip fidelity.
     */
    public static Song roundTrip(Song original) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        SongIO.writeSong(original, pw);
        pw.flush();
        var xml = sw.toString();

        var factory = SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        var reader = new SongIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);

        return reader.getSong();
    }

    /**
     * Creates a minimal Song mock with mutation tracking suspended and
     * {@code withModification} delegating directly to the runnable.
     * Shared by {@link #detachedLine()} and
     * {@link songscribe.ui.selection.ReflectionTestHelper}.
     */
    public static Song minimalSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isMutationTrackingSuspended()).thenReturn(true);
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
            .when(songMock).withModification(any(Runnable.class));
        return songMock;
    }

    /** Creates a Line backed by a minimal Song mock. */
    protected static Line detachedLine() {
        return new Line(minimalSongMock());
    }
}
