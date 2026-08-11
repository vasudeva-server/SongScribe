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
package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.UnitTest;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.io.musicxml.MusicXmlTags;
import songscribe.io.musicxml.MusicXmlWriter;

/**
 * Unit tests for {@link SongFileLoader}: extension-based routing, the provenance
 * gate ({@code <software>}), and format/version validation.
 *
 * <p>Foreign/malformed fixtures are built by string-editing a valid writer
 * projection (produced from a real fixture via {@link MusicXmlWriter}), so each
 * case exercises exactly one gate condition against otherwise-valid MusicXML.
 */
class SongFileLoaderTest extends UnitTest {

    private static final String MUSICXML_EXTENSION = "musicxml";
    private static final String XML_EXTENSION = "xml";
    private static final String PDF_EXTENSION = "pdf";
    private static final String EXTENSIONLESS_FILE_NAME = "song-with-no-extension";
    private static final String NONEXISTENT_FILE_NAME = "does-not-exist.musicxml";
    private static final String STUB_FILE_NAME = "stub.musicxml";

    // Every element/attribute name the fixtures edit comes from MusicXmlTags, the
    // vocabulary the writer emits, so renaming a tag there cannot leave these
    // fixtures silently editing nothing and passing.
    private static final String FOREIGN_SOFTWARE = "Finale";
    private static final String BLANK_SOFTWARE = "   ";
    // <software> content is text-only, so any run of non-'<' is the whole value.
    private static final String SOFTWARE_TAG_REGEX =
        XmlFixtures.element(MusicXmlTags.SOFTWARE, "[^<]*");

    // score-timewise is the other MusicXML root; the writer never emits it, so it
    // has no MusicXmlTags constant.
    private static final String FOREIGN_ROOT = "score-timewise";
    private static final String NATIVE_ROOT_OPEN_TAG =
        rootOpenTag(MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.VERSION_VALUE);
    private static final String NATIVE_ROOT_CLOSE_TAG = XmlFixtures.closeTag(MusicXmlTags.SCORE_PARTWISE);
    private static final String FOREIGN_ROOT_OPEN_TAG =
        rootOpenTag(FOREIGN_ROOT, MusicXmlTags.VERSION_VALUE);
    private static final String FOREIGN_ROOT_CLOSE_TAG = XmlFixtures.closeTag(FOREIGN_ROOT);

    private static final String TOO_OLD_VERSION = "3.0";
    private static final String UNPARSEABLE_VERSION = "x";
    private static final String VERSION_ATTR = versionAttr(MusicXmlTags.VERSION_VALUE);
    private static final String VERSION_TOO_OLD_ATTR = versionAttr(TOO_OLD_VERSION);
    private static final String VERSION_UNPARSEABLE_ATTR = versionAttr(UNPARSEABLE_VERSION);
    private static final String VERSION_MISSING_TAG = XmlFixtures.openTag(MusicXmlTags.SCORE_PARTWISE);

    // The DOCTYPE a real Finale/Dolet export carries. The public and system
    // identifiers are the vendor's, so they stay verbatim.
    private static final String MUSICXML_DOCTYPE =
        "<!DOCTYPE " + MusicXmlTags.SCORE_PARTWISE +
            " PUBLIC \"-//Recordare//DTD MusicXML 3.0 Partwise//EN\"" +
            " \"http://www.musicxml.org/dtds/partwise.dtd\">";
    private static final String XXE_ENTITY_NAME = "xxe";
    private static final String SECRET_FILE_NAME = "secret.txt";
    private static final String XXE_SECRET = "TOP-SECRET-CONTENTS";

    // Deliberately not valid document-type-definition syntax: a parser that fetched
    // this file would fail on it, so a successful load proves it was never fetched.
    private static final String BROKEN_DTD_FILE_NAME = "broken.dtd";
    private static final String BROKEN_DTD_CONTENT = "this is not a document type definition";

    // A "billion laughs" bomb: each level expands to ENTITY_BOMB_FANOUT copies of the
    // level below it, so referencing the top level demands far more expansions than
    // the runtime permits. Six levels is well past the limit while still building
    // instantly and staying a few hundred bytes on disk.
    private static final int ENTITY_BOMB_LEVELS = 6;
    private static final int ENTITY_BOMB_FANOUT = 10;
    private static final String ENTITY_BOMB_PREFIX = "bomb";

    // Two limits stand between a bomb and heap exhaustion, and the leaf decides which one
    // the document reaches. A leaf carrying text accumulates expanded characters and hits
    // the size cap; an empty leaf expands just as many times while producing nothing, so
    // it can only be stopped by the count cap — the one MusicXmlSerializer asks for by
    // name. One test per leaf, so neither cap can lapse unnoticed behind the other.
    private static final String ENTITY_BOMB_TEXT_LEAF = "aaaaaaaaaa";
    private static final String ENTITY_BOMB_EMPTY_LEAF = "";

    // The runtime names the limit it enforced; matching only that name keeps the tests off
    // the surrounding wording, which is the runtime's to change.
    private static final String ENTITY_COUNT_LIMIT_NAME = "jdk.xml.entityExpansionLimit";
    private static final String ENTITY_SIZE_LIMIT_NAME = "jdk.xml.totalEntitySizeLimit";

    private static final Pattern SOFTWARE_TAG_PATTERN = Pattern.compile(SOFTWARE_TAG_REGEX);

    private static String versionAttr(String version) {
        return XmlFixtures.attr(MusicXmlTags.ATTR_VERSION, version);
    }

    private static String rootOpenTag(String rootName, String version) {
        return XmlFixtures.openTag(rootName, MusicXmlTags.ATTR_VERSION, version);
    }

    private static String doctypeWithInternalSubset(String internalSubset) {
        return "<!DOCTYPE " + MusicXmlTags.SCORE_PARTWISE + " [" + internalSubset + "]>";
    }

    private static String entityReference(int level) {
        return '&' + ENTITY_BOMB_PREFIX + level + ';';
    }

    private static String entityBombDoctype(String leaf) {
        var subset = new StringBuilder(entityDeclaration(0, leaf));

        for (var level = 1; level < ENTITY_BOMB_LEVELS; level++) {
            subset.append(entityDeclaration(level, entityReference(level - 1).repeat(ENTITY_BOMB_FANOUT)));
        }

        return doctypeWithInternalSubset(subset.toString());
    }

    /**
     * The valid document with its {@code <software>} content replaced by a reference to
     * the top of a bomb built on {@code leaf}, and the bomb's declarations prepended.
     * Tolerating the declaration is what makes nested entity definitions reachable at all.
     */
    private static String entityBombXml(String leaf) {
        return SOFTWARE_TAG_PATTERN.matcher(validMusicXml)
            .replaceFirst(XmlFixtures.element(MusicXmlTags.SOFTWARE, entityReference(ENTITY_BOMB_LEVELS - 1)))
            .replace(NATIVE_ROOT_OPEN_TAG, entityBombDoctype(leaf) + NATIVE_ROOT_OPEN_TAG);
    }

    private static String entityDeclaration(int level, String value) {
        return "<!ENTITY " + ENTITY_BOMB_PREFIX + level + " \"" + value + "\">";
    }

    /** The detail of an {@link SongLoadResult.UnsupportedFileFormat}, failing loudly on any other result. */
    @Nullable
    private static String unsupportedFormatDetail(SongLoadResult result) {
        if (!(result instanceof SongLoadResult.UnsupportedFileFormat unsupported)) {
            throw new AssertionError("expected UnsupportedFileFormat but got " + result);
        }

        return unsupported.detail();
    }

    /** The software tag of a {@link SongLoadResult.WrongSoftware}, failing loudly on any other result. */
    @Nullable
    private static String wrongSoftwareTag(SongLoadResult result) {
        if (!(result instanceof SongLoadResult.WrongSoftware wrongSoftware)) {
            throw new AssertionError("expected WrongSoftware but got " + result);
        }

        return wrongSoftware.software();
    }

    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static String validMusicXml;
    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static File corpusMsswFile;

    @BeforeAll
    static void setUpValidProjectionAndCorpusFile() throws Exception {
        var fixture = loadFixtureResult("full-line");
        validMusicXml = writeMusicXml(fixture);
        corpusMsswFile = firstCorpusMsswFile();
    }

    private static String writeMusicXml(SongLoadResult.Success fixture) {
        var stringWriter = new StringWriter();
        var printWriter = new PrintWriter(stringWriter);
        SongFileWriter.write(fixture.song(), fixture.fonts(), printWriter);
        return stringWriter.toString();
    }

    /** Picks the first (name-sorted) real-corpus {@code .mssw} file, without hardcoding a filename. */
    private static File firstCorpusMsswFile() throws IOException {
        try (var files = Files.list(Path.of("src/test/resources/corpus/real"))) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".mssw"))
                .min(Comparator.comparing(path -> path.getFileName().toString()))
                .orElseThrow(() -> new IllegalStateException("real corpus directory is empty"))
                .toFile();
        }
    }

    private static File writeFile(Path dir, String name, String content) throws IOException {
        var file = dir.resolve(name).toFile();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    // -- (a) writer-produced .musicxml -----------------------------------------------------

    @Test
    void testWriterProducedMusicXmlSucceeds(@TempDir Path tempDir) throws IOException {
        var file = writeFile(tempDir, "song." + MUSICXML_EXTENSION, validMusicXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.Success.class);
    }

    // -- (b) legacy .mssw corpus file --------------------------------------------------------

    @Test
    void testLegacyMsswCorpusFileSucceeds() {
        assertThat(SongFileLoader.load(corpusMsswFile)).isInstanceOf(SongLoadResult.Success.class);
    }

    // -- (c) <software> rewritten to a foreign vendor ----------------------------------------

    @Test
    void testMusicXmlWithForeignSoftwareReturnsWrongSoftware(@TempDir Path tempDir) throws IOException {
        var foreignXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml)
            .replaceFirst(XmlFixtures.element(MusicXmlTags.SOFTWARE, FOREIGN_SOFTWARE));
        var file = writeFile(tempDir, "foreign." + MUSICXML_EXTENSION, foreignXml);

        assertThat(wrongSoftwareTag(SongFileLoader.load(file))).isEqualTo(FOREIGN_SOFTWARE);
    }

    // -- (d) <software> element removed entirely ---------------------------------------------

    @Test
    void testMusicXmlWithSoftwareElementRemovedReturnsWrongSoftwareWithNullSoftware(@TempDir Path tempDir)
        throws IOException {

        var noSoftwareXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml).replaceFirst("");
        var file = writeFile(tempDir, "no-software." + MUSICXML_EXTENSION, noSoftwareXml);

        assertThat(wrongSoftwareTag(SongFileLoader.load(file))).isNull();
    }

    // -- (e) <software> whitespace-only -------------------------------------------------------

    @Test
    void testMusicXmlWithBlankSoftwareReturnsWrongSoftware(@TempDir Path tempDir) throws IOException {
        var blankSoftwareXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml)
            .replaceFirst(XmlFixtures.element(MusicXmlTags.SOFTWARE, BLANK_SOFTWARE));
        var file = writeFile(tempDir, "blank-software." + MUSICXML_EXTENSION, blankSoftwareXml);

        assertThat(wrongSoftwareTag(SongFileLoader.load(file))).isBlank();
    }

    // -- (f) writer-produced .xml (same content, .xml name) ----------------------------------

    @Test
    void testWriterProducedXmlExtensionSucceeds(@TempDir Path tempDir) throws IOException {
        var file = writeFile(tempDir, "song." + XML_EXTENSION, validMusicXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.Success.class);
    }

    // -- (g) foreign .xml -----------------------------------------------------------------------

    @Test
    void testForeignXmlExtensionReturnsWrongSoftware(@TempDir Path tempDir) throws IOException {
        var foreignXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml)
            .replaceFirst(XmlFixtures.element(MusicXmlTags.SOFTWARE, FOREIGN_SOFTWARE));
        var file = writeFile(tempDir, "foreign." + XML_EXTENSION, foreignXml);

        assertThat(wrongSoftwareTag(SongFileLoader.load(file))).isEqualTo(FOREIGN_SOFTWARE);
    }

    // -- (h) unsupported extension / no extension --------------------------------------------

    @Test
    void testPdfExtensionReturnsUnsupportedFileFormatWithExtensionInDetail(@TempDir Path tempDir)
        throws IOException {

        var file = writeFile(tempDir, "song." + PDF_EXTENSION, validMusicXml);

        assertThat(unsupportedFormatDetail(SongFileLoader.load(file))).isEqualTo(PDF_EXTENSION);
    }

    @Test
    void testExtensionlessPathReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var file = writeFile(tempDir, EXTENSIONLESS_FILE_NAME, validMusicXml);

        // A name with no extension reports an empty one, distinguishing this from the
        // rejections that name a bad root element or an unusable version.
        assertThat(unsupportedFormatDetail(SongFileLoader.load(file))).isEmpty();
    }

    // -- (i) root element is not <score-partwise> --------------------------------------------

    @Test
    void testWrongRootElementReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var wrongRootXml = validMusicXml
            .replace(NATIVE_ROOT_OPEN_TAG, FOREIGN_ROOT_OPEN_TAG)
            .replace(NATIVE_ROOT_CLOSE_TAG, FOREIGN_ROOT_CLOSE_TAG);
        var file = writeFile(tempDir, "wrong-root." + MUSICXML_EXTENSION, wrongRootXml);

        assertThat(unsupportedFormatDetail(SongFileLoader.load(file))).contains(FOREIGN_ROOT);
    }

    // -- (j) unsupported / missing / unparseable version -------------------------------------

    @Test
    void testVersionTooOldReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var oldVersionXml = validMusicXml.replace(VERSION_ATTR, VERSION_TOO_OLD_ATTR);
        var file = writeFile(tempDir, "old-version." + MUSICXML_EXTENSION, oldVersionXml);

        var detail = unsupportedFormatDetail(SongFileLoader.load(file));

        assertThat(detail).contains(TOO_OLD_VERSION);
        assertThat(detail).contains(MusicXmlTags.VERSION_VALUE);
    }

    @Test
    void testMissingVersionAttributeReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var missingVersionXml = validMusicXml.replace(NATIVE_ROOT_OPEN_TAG, VERSION_MISSING_TAG);
        var file = writeFile(tempDir, "missing-version." + MUSICXML_EXTENSION, missingVersionXml);

        var detail = unsupportedFormatDetail(SongFileLoader.load(file));

        // Names the attribute that is absent, and cannot be quoting a version it never
        // found — which is what separates this from the unparseable-version rejection.
        assertThat(detail).contains(MusicXmlTags.ATTR_VERSION);
        assertThat(detail).doesNotContain("'");
    }

    @Test
    void testUnparseableVersionReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var unparseableVersionXml = validMusicXml.replace(VERSION_ATTR, VERSION_UNPARSEABLE_ATTR);
        var file = writeFile(tempDir, "unparseable-version." + MUSICXML_EXTENSION, unparseableVersionXml);

        assertThat(unsupportedFormatDetail(SongFileLoader.load(file)))
            .contains('\'' + UNPARSEABLE_VERSION + '\'');
    }

    // -- (k) a DOCTYPE is tolerated, and nothing it declares is acted upon ---------------------
    //
    // Every mainstream notation program emits a DOCTYPE, so the parser has to read past
    // one. It reads and acts on everything above the root element before this reader's
    // first callback fires, so rejecting a document quickly afterwards is a diagnosis,
    // not a defense — these tests pin the "nothing is acted upon" half in place.

    @Test
    void testDoctypedValidDocumentSucceeds(@TempDir Path tempDir) throws IOException {
        // The motivating case: a DOCTYPE must make no difference at all to a document
        // that is otherwise exactly what the writer produced.
        var doctypedXml = validMusicXml.replace(NATIVE_ROOT_OPEN_TAG, MUSICXML_DOCTYPE + NATIVE_ROOT_OPEN_TAG);
        var file = writeFile(tempDir, "doctyped-valid." + MUSICXML_EXTENSION, doctypedXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.Success.class);
    }

    @Test
    void testDoctypedOldVersionReturnsUnsupportedFileFormatNotParseError(@TempDir Path tempDir)
        throws IOException {
        // Rejecting the declaration outright aborts the parse before the version gate is
        // reached, so the user is told the document is damaged instead of that it isn't
        // SongScribe MusicXML.
        var doctypedXml = validMusicXml
            .replace(NATIVE_ROOT_OPEN_TAG, MUSICXML_DOCTYPE + NATIVE_ROOT_OPEN_TAG)
            .replace(VERSION_ATTR, VERSION_TOO_OLD_ATTR);
        // Without this the fixture is a plain old-version document and the test would
        // pass while never exercising the DOCTYPE path at all.
        assertThat(doctypedXml).contains(MUSICXML_DOCTYPE);

        var file = writeFile(tempDir, "doctyped." + XML_EXTENSION, doctypedXml);

        assertThat(unsupportedFormatDetail(SongFileLoader.load(file))).contains(TOO_OLD_VERSION);
    }

    @Test
    void testExternalEntityInDoctypeIsNotResolved(@TempDir Path tempDir) throws IOException {
        var secretFile = writeFile(tempDir, SECRET_FILE_NAME, XXE_SECRET);
        var entityDoctype = doctypeWithInternalSubset(
            "<!ENTITY " + XXE_ENTITY_NAME + " SYSTEM \"" + secretFile.toURI() + "\">");
        var xxeXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml)
            .replaceFirst(XmlFixtures.element(MusicXmlTags.SOFTWARE, '&' + XXE_ENTITY_NAME + ';'))
            .replace(NATIVE_ROOT_OPEN_TAG, entityDoctype + NATIVE_ROOT_OPEN_TAG);
        var file = writeFile(tempDir, "xxe." + MUSICXML_EXTENSION, xxeXml);

        // The reference is dropped rather than substituted, so the provenance tag ends up
        // empty. Asserting emptiness rather than "not the secret" catches a leak that
        // arrives with surrounding whitespace, or that pulls in some other file entirely.
        assertThat(wrongSoftwareTag(SongFileLoader.load(file))).isEmpty();
    }

    @Test
    void testExternalDtdSubsetIsNotFetched(@TempDir Path tempDir) throws IOException {
        var brokenDtd = writeFile(tempDir, BROKEN_DTD_FILE_NAME, BROKEN_DTD_CONTENT);
        var externalDoctype =
            "<!DOCTYPE " + MusicXmlTags.SCORE_PARTWISE + " SYSTEM \"" + brokenDtd.toURI() + "\">";
        var externalDtdXml =
            validMusicXml.replace(NATIVE_ROOT_OPEN_TAG, externalDoctype + NATIVE_ROOT_OPEN_TAG);
        var file = writeFile(tempDir, "external-dtd." + MUSICXML_EXTENSION, externalDtdXml);

        // Loading succeeds only because the definition file was never read — its contents
        // are not valid syntax, so fetching it would fail the parse. A real export points
        // this at a URL, where fetching would also stall on the network.
        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.Success.class);
    }

    /**
     * A bomb whose leaf carries text: a few hundred bytes on disk that would expand to
     * gigabytes if nothing capped it, freezing the window it was opened from. Either cap
     * satisfies this — which one a given runtime reaches first is a property of the
     * parser, not of the protection, and pinning one would fail on a JDK that reordered
     * them while the protection still worked.
     */
    @Test
    void testEntityExpansionBombReturnsParseError(@TempDir Path tempDir) throws IOException {
        assertThat(bombFailureMessage(tempDir, "entity-bomb", ENTITY_BOMB_TEXT_LEAF))
            .containsAnyOf(ENTITY_COUNT_LIMIT_NAME, ENTITY_SIZE_LIMIT_NAME);
    }

    /**
     * The same bomb with an empty leaf: it demands as many expansions but produces no
     * characters, so the accumulated-size cap cannot fire and the count cap — the one
     * {@code MusicXmlSerializer} asks for by name — is the only thing left to stop it.
     */
    @Test
    void testEntityCountBombReturnsParseError(@TempDir Path tempDir) throws IOException {
        assertThat(bombFailureMessage(tempDir, "entity-count-bomb", ENTITY_BOMB_EMPTY_LEAF))
            .contains(ENTITY_COUNT_LIMIT_NAME);
    }

    /**
     * Loads a bomb built on {@code leaf} and returns the failure message of the
     * {@code ParseError} it must produce. The caller asserts on which limit that message
     * names: doing so is what keeps these tests from passing on a malformed-fixture parse
     * error, which looks identical from the outside.
     */
    private static String bombFailureMessage(Path tempDir, String fileName, String leaf) throws IOException {
        var file = writeFile(tempDir, fileName + '.' + MUSICXML_EXTENSION, entityBombXml(leaf));
        var result = SongFileLoader.load(file);

        if (!(result instanceof SongLoadResult.ParseError parseError)) {
            throw new AssertionError("expected ParseError but got " + result);
        }

        var message = parseError.cause().getMessage();

        assertThat(message).as("the parse failure must carry a message naming the limit").isNotNull();

        return message;
    }

    // -- (l) malformed (not well-formed) XML --------------------------------------------------

    @Test
    void testMalformedXmlReturnsParseError(@TempDir Path tempDir) throws IOException {
        // Drop the root closing tag: the document is left unterminated, which the SAX
        // parser rejects as not well-formed before our gates ever see it.
        var malformedXml = validMusicXml.replace(NATIVE_ROOT_CLOSE_TAG, "");
        var file = writeFile(tempDir, "malformed." + MUSICXML_EXTENSION, malformedXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.ParseError.class);
    }

    // -- (m) nonexistent path -------------------------------------------------------------------

    @Test
    void testNonexistentPathReturnsIoError(@TempDir Path tempDir) {
        var file = tempDir.resolve(NONEXISTENT_FILE_NAME).toFile();

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.IoError.class);
    }

    // -- (n) an unexpected runtime failure inside the reader ------------------------------------

    @Test
    void testRuntimeExceptionFromReaderReturnsParseError() {
        // A missed null check in the mappers arrives here as an NPE: NullAway cannot see
        // into the generated ProxyMusic classes, so nothing in the build catches one for
        // us. The reader is stubbed because a file that provokes the bug would be a file
        // the current mappers handle correctly. What is asserted is the backstop clause —
        // the bug costs a failed open, not the application.
        var file = new File(STUB_FILE_NAME);
        var cause = new NullPointerException("missed null check");

        try (var readerMock = mockStatic(MusicXmlReader.class)) {
            readerMock.when(() -> MusicXmlReader.read(file)).thenThrow(cause);

            var result = SongFileLoader.load(file);

            if (!(result instanceof SongLoadResult.ParseError parseError)) {
                throw new AssertionError("expected ParseError but got " + result);
            }

            // The original throwable has to survive the wrapping, or the log entry the
            // backstop writes is the only record of what actually went wrong.
            assertThat(parseError.cause()).hasCause(cause);
        }
    }
}
