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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.SongLoadResult;
import songscribe.io.SongLoader;

/**
 * Phase 8 losslessness gate: every {@code .mssw} in the test corpus is loaded by
 * the legacy reader, then round-tripped through {@code Song → MusicXML → Song} and
 * asserted lossless via the <em>serialization fixpoint</em>.
 *
 * <p>The corpus lives under {@code src/test/resources/corpus/}:
 * <ul>
 *   <li>{@code synthetic/} — feature-exercising songs built by
 *       {@link MusicXmlCorpusGenerator} (regenerate with {@code -Dcorpus.generate=true});</li>
 *   <li>{@code real/} — copies of the real Sri Chinmoy songs in {@code examples/}.</li>
 * </ul>
 *
 * <h2>Why a fixpoint, and why the second generation</h2>
 * There is no deep {@code Song.equals()}. Losslessness is instead proven by
 * comparing successive MusicXML serializations: if reading a MusicXML document and
 * re-writing it reproduces the document byte-for-byte, no MusicXML-observable
 * information was lost across the read/write cycle.
 *
 * <p>The comparison starts at the <em>second</em> generation, not the first. Some
 * fields are write-forward only by design (the five credit-words-only font roles,
 * computed {@code default-x}/{@code default-y} base positions, display-only credits
 * re-derived from head metadata). The first write emits those from the legacy model;
 * the reader does not read them back, so the first re-serialization normalizes them
 * away. From the second generation on, the model is in recovered canonical form and
 * the cycle is a true fixpoint — which is exactly the property the MusicXML-as-canonical
 * cutover depends on. The first-generation output is still schema-validated.
 */
class MusicXmlCorpusLosslessnessTest extends UnitTest {

    private static final Path CORPUS_ROOT = Path.of("src/test/resources/corpus");
    private static final String MSSW_SUFFIX = ".mssw";

    /**
     * Musically meaningful element markers whose count must survive the first read.
     * The fixpoint alone cannot catch a reader that <em>stably</em> drops content on
     * every read (e.g. discards every lyric), because the later generations would
     * still match each other. Comparing these counts across the first write/read
     * catches that class of bug, while remaining immune to the write-forward-only
     * fields (fonts, base positions, display credits) the fixpoint deliberately
     * normalizes away.
     */
    private static final List<String> CONTENT_MARKERS = List.of("<note", "<measure", "<lyric");

    /**
     * A fixed clock so the write-forward {@code <encoding-date>} and {@code <rights>}
     * year are identical across generations; otherwise the fixpoint would flake on
     * the wall-clock date.
     */
    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    static Stream<Arguments> corpusFiles() throws Exception {
        try (var paths = Files.walk(CORPUS_ROOT)) {
            var files = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(MSSW_SUFFIX))
                .sorted()
                .toList();

            assertThat(files).as("the corpus must contain at least the real example songs").isNotEmpty();

            // Materialize before the stream closes; label each case with the corpus-relative path.
            return files.stream()
                .map(path -> Arguments.of(CORPUS_ROOT.relativize(path).toString(), path))
                .toList()
                .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusFiles")
    void testCorpusFileRoundTripsLosslessly(String label, Path file) throws Exception {
        // 1. The file must load through the legacy reader (validity gate).
        var loaded = SongLoader.load(file.toFile());
        assertThat(loaded)
            .as("%s: legacy reader must load the corpus file", label)
            .isInstanceOf(SongLoadResult.Success.class);
        var legacy = (SongLoadResult.Success) loaded;

        // 2. First MusicXML projection of the legacy model — must be schema-valid.
        var firstGeneration = writeMusicXml(legacy.song(), legacy.fonts());
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(firstGeneration))
            .as("%s: MusicXML output must validate against the 4.0 schema", label)
            .doesNotThrowAnyException();

        // 3. Normalize once (strips write-forward-only data), then re-serialize.
        var normalized = read(firstGeneration);
        var secondGeneration = writeMusicXml(normalized.song(), normalized.fonts());

        // 3b. First-read fidelity: every musically meaningful element present in the
        // first projection must survive the first read into the second projection.
        // Guards against a stably-lossy reader that the fixpoint check alone misses.
        for (var marker : CONTENT_MARKERS) {
            assertThat(countOccurrences(secondGeneration, marker))
                .as("%s: the first read must preserve every '%s' element", label, marker)
                .isEqualTo(countOccurrences(firstGeneration, marker));
        }

        // 4. The next round-trip must reproduce the document exactly (the fixpoint).
        var reloaded = read(secondGeneration);
        var thirdGeneration = writeMusicXml(reloaded.song(), reloaded.fonts());

        assertThat(thirdGeneration)
            .as("%s: Song → MusicXML → Song is a lossless fixpoint from the recovered model", label)
            .isEqualTo(secondGeneration);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String writeMusicXml(songscribe.dom.Song song, DocumentFontsHolder fonts) {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlWriter.writeSong(song, fonts, pw, FIXED_CLOCK);
        pw.flush();
        return sw.toString();
    }

    private static SongLoadResult.Success read(String xml) throws Exception {
        return MusicXmlReader.read(new InputSource(new StringReader(xml)));
    }

    private static int countOccurrences(String haystack, String needle) {
        var count = 0;
        var index = haystack.indexOf(needle);

        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }

        return count;
    }
}
