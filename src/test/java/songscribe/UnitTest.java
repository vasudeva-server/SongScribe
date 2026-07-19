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

import songscribe.font.DocumentFonts;
import songscribe.io.SongIO;
import songscribe.io.SongLoadResult;
import songscribe.io.SongLoader;
import songscribe.message.MessageCenterTestHelper;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.undo.UndoController;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.xml.sax.SAXException;

import songscribe.error.RuntimeErrorTestHelper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for unit tests. Suppresses modal error dialogs
 * so tests don't block on user interaction.
 */
@SuppressWarnings("OverlyBroadThrowsClause")
public abstract class UnitTest {

    private static volatile boolean bannerShown = false;
    private static volatile boolean flatLafInstalled = false;

    @BeforeAll
    static void suppressDialogs() throws Exception {
        OptionDialogs.setSuppressDialogs(true);
        RuntimeErrorTestHelper.install();
        MessageCenterTestHelper.install();
        installFlatLafDefaults();

        if (!bannerShown) {
            bannerShown = true;
            SongScribe.logBanner("SongScribe (Unit Tests)");
        }
    }

    // MBassador holds subscribers weakly, so the action singletons a test creates via
    // Actions.initialize() linger as zombie bus listeners until GC — still handling later
    // tests' notifications and throwing in the torn-down/mocked environment, which routes to
    // RuntimeError and pollutes unrelated tests. Unsubscribe them after every test.
    // Actions.unsubscribeForTest() is null-safe (it skips uninitialized fields) and
    // idempotent, so this is a harmless no-op for tests that never touch Actions.
    @AfterEach
    void unsubscribeActionSubscribers() {
        Actions.unsubscribeForTest();
        UndoController.unsubscribeForTest();

        // Every listener subscribed during this test (usually via production
        // constructors) is removed so it cannot linger as a zombie that fires against
        // torn-down mocks in later tests.
        MessageCenterTestHelper.unsubscribeTrackedListeners();

        // Last, so the unsubscribes above always run: fail loudly if any @Handler threw
        // during a post in this test — MBassador swallows the error and silently aborts
        // delivery to the post's remaining subscribers, so nothing else reports it.
        MessageCenterTestHelper.assertNoPublicationErrors();

        // Also last: fail loudly if a background thread (e.g. PlayThread, a poll thread)
        // threw and nothing joined it — the JVM's default handler would otherwise just
        // print the stack trace and let the test pass.
        UncaughtExceptionTestHelper.assertNoUncaughtExceptions();
    }

    @BeforeEach
    void resetRuntimeError() {
        RuntimeErrorTestHelper.reset();
        MessageCenterTestHelper.clearPublicationErrors();

        // Reinstalled every test: some tests (e.g. SongScribeTest's main() tests) call
        // Thread.setDefaultUncaughtExceptionHandler themselves, replacing this probe.
        UncaughtExceptionTestHelper.install();
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
     * Resolves a fixture file on the classpath.
     * Fixture files live in {@code src/test/resources/fixtures/{name}.mssw}.
     */
    protected static File fixtureFile(String fixtureName) throws URISyntaxException {
        var url = UnitTest.class.getClassLoader().getResource("fixtures/" + fixtureName + ".mssw");

        if (url == null) {
            throw new IllegalArgumentException("Fixture not found: " + fixtureName);
        }

        return new File(url.toURI());
    }

    /**
     * Loads a fixture file and returns the parsed song.
     */
    public static Song loadFixture(String fixtureName) throws IllegalArgumentException, IOException, SAXException, URISyntaxException {
        return SongLoader.load(fixtureFile(fixtureName)).songOrThrow();
    }

    /**
     * Like {@link #loadFixture} but also returns the document fonts parsed from the
     * fixture's {@code <view>} block (or prefs defaults for v1.0 files).
     */
    public static SongLoadResult.Success loadFixtureResult(String fixtureName) throws URISyntaxException {
        var result = SongLoader.load(fixtureFile(fixtureName));

        if (result instanceof SongLoadResult.Success success) {
            return success;
        }

        throw new IllegalStateException("Fixture '" + fixtureName + "' failed to load: " + result);
    }

    /**
     * Serializes a song to XML and parses it back, verifying round-trip fidelity.
     */
    public static Song roundTrip(Song original) throws Exception {
        var fonts = DocumentFonts.defaultFonts();
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        SongIO.writeSong(original, fonts, pw);
        pw.flush();
        var xml = sw.toString();

        var factory = SAXParserFactory.newInstance();
        var parser = factory.newSAXParser();
        var reader = new SongIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);

        return reader.getSong();
    }

    /**
     * Creates a minimal Song mock with mutation tracking suspended and both
     * {@code withModification} overloads (plain and labeled) delegating directly to
     * the runnable. Shared by {@link #detachedLine()} and
     * {@link songscribe.ui.selection.ReflectionTestHelper}.
     */
    @SuppressWarnings("ReturnOfNull")
    public static Song minimalSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isMutationTrackingSuspended()).thenReturn(true);
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
            .when(songMock).withModification(any(Runnable.class));
        doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
            .when(songMock).withModification(any(String.class), any(Runnable.class));
        return songMock;
    }

    /** Creates a Line backed by a minimal Song mock. */
    protected static Line detachedLine() {
        return new Line(minimalSongMock());
    }

    /** Creates a line containing elements of the given types (in order), unattached to a song. */
    protected static Line lineWith(ElementType... types) {
        var line = detachedLine();

        for (var type : types) {
            line.addElement(type.newInstance());
        }

        return line;
    }

    /** Creates a line containing a single crotchet with the given annotation text. */
    protected static Line lineWithAnnotation(String text) {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        note.addAttachment(new AnnotationAttachment(note, new Annotation(text)));
        return line;
    }

    /** Parses a composition XML string into a Song via the SAX document reader. */
    public static Song parseXml(String xml) throws Exception {
        var parser = SAXParserFactory.newInstance().newSAXParser();
        var reader = new SongIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);
        return reader.getSong();
    }
}
