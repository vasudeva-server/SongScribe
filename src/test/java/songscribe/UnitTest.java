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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.error.RuntimeErrorTestHelper;
import songscribe.font.DocumentFonts;
import songscribe.io.SongIO;
import songscribe.io.SongLoadResult;
import songscribe.io.SongLoader;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.message.MessageCenterTestHelper;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.undo.UndoController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

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
        installFlatLafDefaults();

        if (!bannerShown) {
            bannerShown = true;
            SongScribe.logBanner("SongScribe (Unit Tests)");
        }
    }

    // Both calls do what closing the bus scope cannot. Actions.initialize() installs a
    // generation of action singletons; retiring them releases the mocked MainFrame they
    // captured. UndoController is a static singleton, so its undo and redo stacks and its
    // pending op-name outlive a test even though its subscription does not. Both are
    // null-safe (they skip uninitialized fields) and idempotent, so this is a harmless
    // no-op for a test that never touches either.
    @AfterEach
    void teardown() {
        Actions.deinitialize();
        UndoController.deinitialize();

        // Discards this test's bus and every listener on it, so nothing a production
        // constructor subscribed can fire against torn-down mocks in a later test.
        MessageCenterTestHelper.closeScope();

        // After the close, so the discard always runs: fail loudly if any @Handler threw
        // during a post in this test — MBassador swallows the error and silently aborts
        // delivery to the post's remaining subscribers, so nothing else reports it.
        MessageCenterTestHelper.assertNoPublicationErrors();

        // Also last: fail loudly if a background thread (e.g. PlayThread, a poll thread)
        // threw and nothing joined it — the JVM's default handler would otherwise just
        // print the stack trace and let the test pass.
        UncaughtExceptionTestHelper.assertNoUncaughtExceptions();
    }

    @BeforeEach
    void setup() {
        RuntimeErrorTestHelper.reset();

        // Opened before the test can construct anything, so every subscription it makes
        // lands on this test's bus and leaves with it.
        MessageCenterTestHelper.openScope();

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
     * Resolves a legacy {@code .mssw} fixture on the classpath. Fixture files live in
     * {@code src/test/resources/fixtures/}.
     *
     * <p>Tests that exercise the legacy reader itself call this directly. Everything else goes
     * through {@link #loadFixtureResult}, which prefers a MusicXML fixture of the same name.
     */
    protected static File fixtureFile(String fixtureName) throws URISyntaxException {
        var url = UnitTest.class.getClassLoader().getResource("fixtures/" + fixtureName + ".mssw");

        if (url == null) {
            throw new IllegalArgumentException("Fixture not found: " + fixtureName);
        }

        return new File(url.toURI());
    }

    /**
     * Resolves a {@code .musicxml} fixture on the classpath, or null when the fixture exists only
     * in the legacy format.
     */
    private static @Nullable File musicXmlFixtureFile(String fixtureName) throws URISyntaxException {
        var url = UnitTest.class.getClassLoader().getResource("fixtures/" + fixtureName + ".musicxml");

        if (url == null) {
            return null;
        }

        return new File(url.toURI());
    }

    /**
     * Loads a fixture file and returns the parsed song, preferring a MusicXML fixture over a legacy
     * one of the same name (see {@link #loadFixtureResult}).
     */
    public static Song loadFixture(String fixtureName) throws IllegalArgumentException, IOException, SAXException, URISyntaxException {
        return loadFixtureResult(fixtureName).song();
    }

    /**
     * Like {@link #loadFixture} but also returns the document fonts parsed from the fixture.
     *
     * <p>A MusicXML fixture wins over a legacy one of the same name, so a new fixture is written in
     * the current storage format and read back through the current reader. The legacy fixtures that
     * predate the format switch keep loading unchanged.
     */
    public static SongLoadResult.Success loadFixtureResult(String fixtureName) throws URISyntaxException {
        var musicXmlFile = musicXmlFixtureFile(fixtureName);

        if (musicXmlFile != null) {
            try {
                return MusicXmlReader.read(musicXmlFile);
            } catch (IOException | SAXException e) {
                throw new IllegalStateException("Fixture '" + fixtureName + "' failed to load", e);
            }
        }

        var result = SongLoader.load(fixtureFile(fixtureName));

        if (result instanceof SongLoadResult.Success success) {
            return success;
        }

        throw new IllegalStateException("Fixture '" + fixtureName + "' failed to load: " + result);
    }

    /**
     * A line width no fixture built here can overflow, so the fit gates never refuse an edit a
     * test did not set out to have refused. A test that exercises a gate stubs its own width.
     * An unstubbed mock would report 0, which every gate reads as a line with no room at all.
     */
    public static final double UNCONSTRAINED_LINE_WIDTH_SS = 10_000.0;

    /**
     * Creates a minimal Song mock with mutation tracking suspended and both
     * {@code withModification} overloads (plain and labeled) plus
     * {@code withBeatDefiningEdit} delegating directly to the runnable. Shared by
     * {@link #detachedLine()} and
     * {@link songscribe.ui.selection.ReflectionTestHelper}.
     *
     * <p>Every one of these is a chokepoint that wraps the caller's real work, so an
     * unstubbed mock would swallow the runnable entirely and the edit under test would
     * simply never happen — a silent no-op rather than a failure that names itself.
     * {@code maintainKeyInvariant} is deliberately not among them: a mock has no line list to
     * propagate keys across, so its default no-op is the correct behavior, and a test that
     * cares about propagation builds a real Song.
     */
    @SuppressWarnings("ReturnOfNull")
    public static Song minimalSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isMutationTrackingSuspended()).thenReturn(true);
        when(songMock.getDefaultRestLengthSs()).thenReturn(Song.DEFAULT_REST_LENGTH_SS);
        when(songMock.getLineWidthSs()).thenReturn(new Ss(UNCONSTRAINED_LINE_WIDTH_SS));
        // An unstubbed mock reports verse 0, which no lyric ever carries, so every lyric on
        // every fixture element would read as absent to layout and to the editor.
        when(songMock.getActiveVerse()).thenReturn(Lyric.FIRST_VERSE);
        doAnswer(answerVoid(Runnable::run))
            .when(songMock).withModification(any(Runnable.class));
        doAnswer(answerVoid((String label, Runnable body) -> body.run()))
            .when(songMock).withModification(any(String.class), any(Runnable.class));
        // Reports no tuplets removed: a mock has no tuplets to invalidate, and a test that
        // cares about the removal path builds a real Song.
        doAnswer(inv -> { ((Runnable) inv.getArgument(2)).run(); return false; })
            .when(songMock).withBeatDefiningEdit(anyInt(), anyInt(), any(Runnable.class));
        return songMock;
    }

    /**
     * Asserts {@link Song}'s key invariant over every line of {@code song}: line 0 establishes a
     * key of its own, and every line that establishes none is in the key in effect at the end of
     * the line before it.
     *
     * <p>Shared rather than local to one test class on purpose. The failure a missed propagation
     * produces is wrong pitches on every line downstream, with no error and nothing visible to
     * notice, and it can be produced by any edit that moves a key — so the assertion belongs after
     * every such edit, in whichever class makes it.
     */
    public static void assertKeyPropagationInvariant(Song song) {
        assertThat(song.getLine(0).getKey())
            .as("line 0 has nothing to inherit from, so it must establish a key of its own")
            .isNotNull();

        assertInheritedKeyChainInvariant(song);
    }

    /**
     * Asserts the inheritance half of {@link Song}'s key invariant only: every line that
     * establishes no key of its own is in the key in effect at the end of the line before it.
     * Says nothing about line 0.
     *
     * <p>This half is what holds under {@link Song#withoutMutationTracking(Runnable)}, where the
     * line-0 clause is deliberately deferred: a reader adds lines one at a time, so line 0 is
     * briefly the only line and the key the document ends up in is not yet known. The reader
     * settles it with {@link Song#rebuildInheritedKeysAfterParsing()} at the end of the load, and
     * only then does the full invariant hold. Use {@link #assertKeyPropagationInvariant} anywhere
     * a modification bracket was open — which is every edit the user can make.
     */
    public static void assertInheritedKeyChainInvariant(Song song) {
        for (var lineIndex = 1; lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);

            if (line.getKey() != null) {
                continue;
            }

            assertThat(line.getRunningKey())
                .as("line %d establishes no key, so it is in the key line %d ends in",
                    lineIndex, lineIndex - 1)
                .isEqualTo(song.getLine(lineIndex - 1).keyAtEndOfLine());
        }
    }

    /**
     * Creates a Line backed by a minimal Song mock, in C major.
     *
     * <p>Nothing precedes a detached line, so it has to establish a key of its own before anything
     * asks what key it is in — the same rule {@link songscribe.dom.Song} keeps for line 0. C major
     * is the key that draws nothing, so a fixture that does not care about the key gets a header
     * with no accidentals in it. A test that does care sets its own.
     */
    protected static Line detachedLine() {
        return detachedLine(minimalSongMock());
    }

    /**
     * The same for a fixture that has to supply its own {@link Song} — a mock it has stubbed
     * further, or a real song with mutation tracking suspended. A line the song's line list does
     * not hold still has nothing before it, so it establishes its own key just as line 0 does.
     *
     * @param song the song the line belongs to; must have mutation tracking suspended, since
     *             establishing the line's key is a tracked change
     */
    public static Line detachedLine(Song song) {
        var line = new Line(song);
        line.setKey(Key.NO_ACCIDENTALS);
        return line;
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
        var note = crotchet();
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
