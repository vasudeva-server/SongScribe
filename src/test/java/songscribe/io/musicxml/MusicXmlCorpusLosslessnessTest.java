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
import static org.assertj.core.api.Assertions.within;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.FIXED_CLOCK;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.countOccurrences;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parseResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.SongLoadResult;
import songscribe.io.SongLoader;
import songscribe.layout.LineLayoutProvider;

/**
 * Phase 8 losslessness gate: every {@code .mssw} in the test corpus is loaded by
 * the legacy reader, then round-tripped through {@code Song → MusicXML → Song} and
 * asserted lossless via the <em>serialization fixpoint</em>.
 *
 * <p>The corpus lives under {@code src/test/resources/corpus/}:
 * <ul>
 *   <li>{@code synthetic/} — feature-exercising songs built by
 *       {@link MusicXmlCorpusGenerator} (regenerate with {@code -Dcorpus.generate=true});</li>
 *   <li>{@code real/} — copies of the real Sri Chinmoy songs in {@code examples/};</li>
 *   <li>{@code legacy/} — hand-maintained documents carrying constructs the generator
 *       cannot emit because the model no longer has anything to emit them from, such as
 *       the retired accidentals in {@link songscribe.io.LegacyAccidentals}. Never
 *       regenerate these; see {@code corpus/legacy/README.md}.</li>
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
 *
 * <h2>What the fixpoint cannot see</h2>
 * A reader that is <em>stably</em> lossy is invisible to the fixpoint: whatever it
 * discards or alters, it discards or alters identically on every read, so the later
 * generations still agree. Two checks on the <em>first</em> read cover that blind spot,
 * both comparing across the legacy model → generation 1 → recovered model step rather
 * than across the fixpoint, so neither sees the write-forward-only fields:
 * {@link #CONTENT_MARKERS} for dropped elements and {@link #CANONICAL_SCALARS} for
 * altered values.
 *
 * <h2>The backward-compatibility half</h2>
 * {@link #testFixtureDocumentValidatesAndLoads} sweeps the {@code .musicxml} documents in
 * {@code src/test/resources/fixtures/}. Those were written by the streaming writer that the
 * object model replaced, so they are the standing proof that files saved by an earlier
 * SongScribe still validate and still load.
 *
 * <h2>What the object-model writer changed, on purpose</h2>
 * The cutover was measured document by document against the streaming writer it replaced:
 * across every corpus song the two agree on element structure, element order and every
 * attribute value. They differ only in the text that carries them, and every one of those
 * differences is a property of marshalling through the generated model rather than a
 * decision this program makes:
 * <ul>
 *   <li>Attributes appear in the generated {@code propOrder}, not the order the streaming
 *       writer emitted them — visible on {@code <supports>}, {@code <credit-words>} and
 *       {@code <words>}. A test that anchors on two attributes being adjacent is pinning
 *       the marshaller, not the writer.</li>
 *   <li>A childless element is written {@code <foo/>}; the streaming writer wrote
 *       {@code <foo></foo>} or {@code <foo />}.</li>
 *   <li>Elements the streaming writer kept on one line — {@code <key>}, {@code <time>},
 *       {@code <clef>}, {@code <score-part>} — now put each child on its own line.</li>
 * </ul>
 * Two structural differences exist but no corpus song reaches them: a {@code <barline>}
 * carries at most one {@code <ending>} (the schema permits no more, though the streaming
 * writer could emit several), and a note-terminated volta close reuses an immediately
 * following invisible right barline instead of adding a second one.
 */
class MusicXmlCorpusLosslessnessTest extends UnitTest {

    private static final Path CORPUS_ROOT = Path.of("src/test/resources/corpus");
    private static final String MSSW_SUFFIX = ".mssw";

    /** Where the {@code .musicxml} documents written by the pre-object-model writer live. */
    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/fixtures");
    private static final String MUSICXML_SUFFIX = ".musicxml";

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
     * A canonical scalar model value: one the writer emits and the reader is meant to
     * recover exactly, named for the assertion message.
     */
    private record CanonicalScalar(String description, ToDoubleFunction<Song> value) {}

    /**
     * Canonical scalar values whose magnitude must survive the first read.
     * <p>
     * Counting elements says nothing about whether a value inside one survived, and the
     * fixpoint is blind to a reader that quantizes a value on <em>every</em> read: such a
     * read is idempotent, so generation 2 already holds the quantized value and generation 3
     * quantizes it to the same thing. The only place the shift is observable is the legacy
     * model against the model recovered from the first projection.
     * <p>
     * The line width is the known case — the {@code <page-width>} reader once converted
     * through a whole-staff-space rounding helper, and the whole corpus passed with that bug
     * present. Add any other scalar the reader is meant to recover exactly.
     */
    private static final List<CanonicalScalar> CANONICAL_SCALARS = List.of(
        new CanonicalScalar("line width", Song::getLineWidthSs),
        new CanonicalScalar("row-height adjustment", Song::getRowHeightAdjustmentSs),
        new CanonicalScalar("default rest length", Song::getDefaultRestLengthSs)
    );

    /**
     * Staff-space tolerance for {@link #CANONICAL_SCALARS}, sized to the coarsest
     * representation any of them passes through: {@code <page-width>} is emitted as tenths
     * with two decimal places (see {@link MusicXmlUnits#formatSsAsTenths}), i.e. thousandths of
     * a staff space. That admits the formatter's own truncation and nothing more — a
     * quantizing read shifts a value by orders of magnitude more (whole-staff-space rounding
     * moves the width by up to half a staff space).
     */
    private static final double SCALAR_TOLERANCE_SS = 0.001;

    /**
     * Every file under {@code root} whose name ends in {@code suffix}, as parameterized cases
     * labelled with the {@code root}-relative path.
     *
     * @param description what the directory is expected to hold, for the empty-directory failure
     */
    private static Stream<Arguments> filesUnder(Path root, String suffix, String description)
        throws IOException {
        try (var paths = Files.walk(root)) {
            var files = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .sorted()
                .toList();

            assertThat(files).as(description).isNotEmpty();

            // Materialize before the stream closes.
            return files.stream()
                .map(path -> Arguments.of(root.relativize(path).toString(), path))
                .toList()
                .stream();
        }
    }

    static Stream<Arguments> corpusFiles() throws IOException {
        return filesUnder(CORPUS_ROOT, MSSW_SUFFIX, "the corpus must contain at least the real example songs");
    }

    static Stream<Arguments> fixtureDocuments() throws IOException {
        return filesUnder(
            FIXTURE_ROOT,
            MUSICXML_SUFFIX,
            "the fixture directory must hold the documents written before the object-model cutover");
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

        var validator = new MusicXmlSchemaValidator();

        // 2. First MusicXML projection of the legacy model — must be schema-valid.
        var firstGeneration = writeMusicXml(legacy.song(), legacy.fonts());
        assertValidates(validator, label, "the first projection", firstGeneration);

        // 3. Normalize once (strips write-forward-only data).
        var normalized = parseResult(firstGeneration);

        // 3a. First-read value fidelity: a canonical scalar must come back from the first
        // read with its magnitude intact. Guards against a reader that quantizes a value
        // on every read, which the fixpoint check alone cannot see.
        for (var scalar : CANONICAL_SCALARS) {
            assertThat(scalar.value().applyAsDouble(normalized.song()))
                .as("%s: the first read must preserve the %s", label, scalar.description())
                .isCloseTo(scalar.value().applyAsDouble(legacy.song()), within(SCALAR_TOLERANCE_SS));
        }

        // 3b. Re-serialize the recovered model, which must be schema-valid in its own right:
        // the recovered model is what every save after the first one writes from.
        var secondGeneration = writeMusicXml(normalized.song(), normalized.fonts());
        assertValidates(validator, label, "the reserialized model", secondGeneration);

        // 3c. First-read content fidelity: every musically meaningful element present in
        // the first projection must survive the first read into the second projection.
        // Guards against a stably-lossy reader that the fixpoint check alone misses.
        for (var marker : CONTENT_MARKERS) {
            assertThat(countOccurrences(secondGeneration, marker))
                .as("%s: the first read must preserve every '%s' element", label, marker)
                .isEqualTo(countOccurrences(firstGeneration, marker));
        }

        // 4. The next round-trip must reproduce the document exactly (the fixpoint).
        var reloaded = parseResult(secondGeneration);
        var thirdGeneration = writeMusicXml(reloaded.song(), reloaded.fonts());

        assertThat(thirdGeneration)
            .as("%s: Song → MusicXML → Song is a lossless fixpoint from the recovered model", label)
            .isEqualTo(secondGeneration);
    }

    /**
     * Backward compatibility for the files already on users' disks: every {@code .musicxml}
     * fixture was written by the streaming writer the object model replaced, so each one is a
     * saved document from before the cutover. It must still validate, and the object-model
     * reader must still load it.
     *
     * <p>What those songs are then <em>used for</em> is asserted by the tests that own each
     * fixture — accidental context, tie validation, selection, line overflow. This sweep is the
     * one place that names the whole set, so a fixture no test loads yet is still covered.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureDocuments")
    void testFixtureDocumentValidatesAndLoads(String label, Path file) throws Exception {
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(file))
            .as("%s: a document written before the cutover must still validate", label)
            .doesNotThrowAnyException();

        assertThat(MusicXmlReader.read(file.toFile()).song().lineCount())
            .as("%s: the object-model reader must recover the document's lines", label)
            .isPositive();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void assertValidates(
        MusicXmlSchemaValidator validator, String label, String generation, String xml) {
        assertThatCode(() -> validator.validate(xml))
            .as("%s: %s must validate against the MusicXML 4.0 schema", label, generation)
            .doesNotThrowAnyException();
    }

    private static String writeMusicXml(Song song, DocumentFontsHolder fonts) {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        // Not MusicXmlRoundTripSupport.writeToString: the fixed clock is MusicXmlWriter's
        // own seam, and SongFileWriter — which writeToString goes through — has no
        // parameter for it, so this fixpoint has to drive the writer directly.
        MusicXmlWriter.writeSong(song, fonts, LineLayoutProvider.headless(song, fonts), pw, FIXED_CLOCK);
        pw.flush();
        return sw.toString();
    }

}
