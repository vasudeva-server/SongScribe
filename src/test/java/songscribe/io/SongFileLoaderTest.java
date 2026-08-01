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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.UnitTest;
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

    private static final String SOFTWARE_TAG_REGEX = "<software>[^<]*</software>";
    private static final String FOREIGN_SOFTWARE = "Finale";
    private static final String BLANK_SOFTWARE = "   ";

    private static final String NATIVE_ROOT_OPEN_TAG = "<score-partwise version=\"4.0\">";
    private static final String NATIVE_ROOT_CLOSE_TAG = "</score-partwise>";
    private static final String FOREIGN_ROOT_OPEN_TAG = "<score-timewise version=\"4.0\">";
    private static final String FOREIGN_ROOT_CLOSE_TAG = "</score-timewise>";

    private static final String VERSION_ATTR = "version=\"4.0\"";
    private static final String VERSION_TOO_OLD_ATTR = "version=\"3.0\"";
    private static final String VERSION_UNPARSEABLE_ATTR = "version=\"x\"";
    private static final String VERSION_MISSING_TAG = "<score-partwise>";
    private static final Pattern SOFTWARE_TAG_PATTERN = Pattern.compile(SOFTWARE_TAG_REGEX);

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
        MusicXmlWriter.writeSong(fixture.song(), fixture.fonts(), printWriter);
        printWriter.flush();
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
            .replaceFirst("<software>" + FOREIGN_SOFTWARE + "</software>");
        var file = writeFile(tempDir, "foreign." + MUSICXML_EXTENSION, foreignXml);

        var result = SongFileLoader.load(file);

        assertThat(result).isInstanceOf(SongLoadResult.WrongSoftware.class);
        assertThat(((SongLoadResult.WrongSoftware) result).software()).isEqualTo(FOREIGN_SOFTWARE);
    }

    // -- (d) <software> element removed entirely ---------------------------------------------

    @Test
    void testMusicXmlWithSoftwareElementRemovedReturnsWrongSoftwareWithNullSoftware(@TempDir Path tempDir)
        throws IOException {

        var noSoftwareXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml).replaceFirst("");
        var file = writeFile(tempDir, "no-software." + MUSICXML_EXTENSION, noSoftwareXml);

        var result = SongFileLoader.load(file);

        assertThat(result).isInstanceOf(SongLoadResult.WrongSoftware.class);
        assertThat(((SongLoadResult.WrongSoftware) result).software()).isNull();
    }

    // -- (e) <software> whitespace-only -------------------------------------------------------

    @Test
    void testMusicXmlWithBlankSoftwareReturnsWrongSoftware(@TempDir Path tempDir) throws IOException {
        var blankSoftwareXml = SOFTWARE_TAG_PATTERN.matcher(validMusicXml)
            .replaceFirst("<software>" + BLANK_SOFTWARE + "</software>");
        var file = writeFile(tempDir, "blank-software." + MUSICXML_EXTENSION, blankSoftwareXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.WrongSoftware.class);
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
            .replaceFirst("<software>" + FOREIGN_SOFTWARE + "</software>");
        var file = writeFile(tempDir, "foreign." + XML_EXTENSION, foreignXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.WrongSoftware.class);
    }

    // -- (h) unsupported extension / no extension --------------------------------------------

    @Test
    void testPdfExtensionReturnsUnsupportedFileFormatWithExtensionInDetail(@TempDir Path tempDir)
        throws IOException {

        var file = writeFile(tempDir, "song." + PDF_EXTENSION, validMusicXml);

        var result = SongFileLoader.load(file);

        assertThat(result).isInstanceOf(SongLoadResult.UnsupportedFileFormat.class);
        assertThat(((SongLoadResult.UnsupportedFileFormat) result).detail()).isEqualTo(PDF_EXTENSION);
    }

    @Test
    void testExtensionlessPathReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var file = writeFile(tempDir, EXTENSIONLESS_FILE_NAME, validMusicXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.UnsupportedFileFormat.class);
    }

    // -- (i) root element is not <score-partwise> --------------------------------------------

    @Test
    void testWrongRootElementReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var wrongRootXml = validMusicXml
            .replace(NATIVE_ROOT_OPEN_TAG, FOREIGN_ROOT_OPEN_TAG)
            .replace(NATIVE_ROOT_CLOSE_TAG, FOREIGN_ROOT_CLOSE_TAG);
        var file = writeFile(tempDir, "wrong-root." + MUSICXML_EXTENSION, wrongRootXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.UnsupportedFileFormat.class);
    }

    // -- (j) unsupported / missing / unparseable version -------------------------------------

    @Test
    void testVersionTooOldReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var oldVersionXml = validMusicXml.replace(VERSION_ATTR, VERSION_TOO_OLD_ATTR);
        var file = writeFile(tempDir, "old-version." + MUSICXML_EXTENSION, oldVersionXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.UnsupportedFileFormat.class);
    }

    @Test
    void testMissingVersionAttributeReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var missingVersionXml = validMusicXml.replace(NATIVE_ROOT_OPEN_TAG, VERSION_MISSING_TAG);
        var file = writeFile(tempDir, "missing-version." + MUSICXML_EXTENSION, missingVersionXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.UnsupportedFileFormat.class);
    }

    @Test
    void testUnparseableVersionReturnsUnsupportedFileFormat(@TempDir Path tempDir) throws IOException {
        var unparseableVersionXml = validMusicXml.replace(VERSION_ATTR, VERSION_UNPARSEABLE_ATTR);
        var file = writeFile(tempDir, "unparseable-version." + MUSICXML_EXTENSION, unparseableVersionXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.UnsupportedFileFormat.class);
    }

    // -- (k) malformed (not well-formed) XML --------------------------------------------------

    @Test
    void testMalformedXmlReturnsParseError(@TempDir Path tempDir) throws IOException {
        // Drop the root closing tag: the document is left unterminated, which the SAX
        // parser rejects as not well-formed before our gates ever see it.
        var malformedXml = validMusicXml.replace(NATIVE_ROOT_CLOSE_TAG, "");
        var file = writeFile(tempDir, "malformed." + MUSICXML_EXTENSION, malformedXml);

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.ParseError.class);
    }

    // -- (l) nonexistent path -------------------------------------------------------------------

    @Test
    void testNonexistentPathReturnsIoError(@TempDir Path tempDir) {
        var file = tempDir.resolve(NONEXISTENT_FILE_NAME).toFile();

        assertThat(SongFileLoader.load(file)).isInstanceOf(SongLoadResult.IoError.class);
    }
}
