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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParserFactory;

import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Lyric;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;

@SuppressWarnings({ "SameReturnValue", "OverlyBroadThrowsClause" })
class SongIOTest extends UnitTest {

    // row 29: getSong() before any parse → IllegalStateException.
    @Test
    void testDocumentReaderGetSongThrowsIllegalStateWhenNotParsed() {
        var reader = new SongIO.DocumentReader();

        assertThatThrownBy(reader::getSong)
            .isInstanceOf(IllegalStateException.class);
    }

    // row 21: non-numeric version attribute → SAXException wrapping NumberFormatException.
    @Test
    void testDocumentReaderNonNumericVersionThrowsSAXException() {
        // "1.abc" has a dot so the split succeeds, but parseInt on "abc" throws NFE,
        // which the catch block wraps in SAXException.
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <song version="1.abc">
              <keys>0</keys>
            </song>
            """;

        assertThatThrownBy(() -> {
            var parser = SAXParserFactory.newInstance().newSAXParser();
            var reader = new SongIO.DocumentReader();
            parser.parse(new InputSource(new StringReader(xml)), reader);
        })
            .isInstanceOf(SAXException.class)
            .hasCauseInstanceOf(NumberFormatException.class);
    }

    // row 31: v1.0 document (no <view> block) → getDocumentFonts() returns defaultsFromPrefs().
    @Test
    void testGetDocumentFontsReturnsDefaultsForV10Document() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <song version="1.0">
              <keys>0</keys>
              <notes></notes>
            </song>
            """;

        var parser = SAXParserFactory.newInstance().newSAXParser();
        var reader = new SongIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);

        assertThat(reader.getDocumentFonts())
            .as("v1.0 doc has no <view> block; must return defaultsFromPrefs()")
            .isEqualTo(DocumentFonts.defaultsFromPrefs());
    }

    @Test
    void testOpeningNewerVersionFileThrowsNewerVersionException() {
        assertThatThrownBy(() -> loadFixture("newer-version"))
            .isInstanceOf(SongIO.NewerVersionException.class);
    }

    @Test
    void testParsedLinesHaveSongSet() throws Exception {
        var song = loadFixture("full-line");

        for (var loadedLine : song.getLines()) {
            assertThat(loadedLine.getSong())
                .as("every parsed line must reference the song that owns it")
                .isSameAs(song);
        }
    }

    // row 14: dynamicLayout=true is always written.
    @Test
    void testWriteSongDynamicLayoutAlwaysWritten() {
        var song = new Song();
        var xml = writeSongToString(song);

        assertThat(xml).contains("<dynamicLayout>true</dynamicLayout>");
    }

    // row 16: all lines are serialized in document order; round-trip preserves per-line element counts.
    @Test
    void testWriteSongLinesSerializedInOrder() throws Exception {
        // Build a two-line song via parseXml so the Song starts with no default line.
        // Line 0: 2 crotchets + barline (3 elements).
        // Line 1: 3 crotchets + final-double-barline (4 elements).
        var song = parseXml(twoLineXml());

        var reloaded = roundTrip(song);

        assertThat(reloaded.lineCount())
            .as("round-trip must preserve line count")
            .isEqualTo(2);
        assertThat(reloaded.getLine(0).elementCount())
            .as("line 0 element count after round-trip")
            .isEqualTo(3);
        assertThat(reloaded.getLine(1).elementCount())
            .as("line 1 element count after round-trip")
            .isEqualTo(4);
    }

    // row 9: empty string fields → tags absent; non-empty → present, XML-escaped.
    // Tests: title, place, year, attribution, underlyrics, banglaLyrics, translatedLyrics.
    @Test
    void testWriteSongOptionalStringFieldsOmittedWhenEmpty() {
        var song = new Song();
        // Song() sets non-empty defaults for some fields — clear them explicitly.
        song.setTitle("");
        song.setPlace("");
        song.setYear("");
        song.setAttribution("");
        song.setUnderLyrics("");
        song.setBanglaLyrics("");
        song.setTranslatedLyrics("");
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<songtitle>");
        assertThat(xml).doesNotContain("<place>");
        assertThat(xml).doesNotContain("<year>");
        assertThat(xml).doesNotContain("<rightinfo>");
        assertThat(xml).doesNotContain("<underlyrics>");
        assertThat(xml).doesNotContain("<banglalyrics>");
        assertThat(xml).doesNotContain("<translatedlyrics>");
    }

    // row 9 (presence + escaping): non-empty values appear and are XML-escaped.
    @Test
    void testWriteSongOptionalStringFieldsEmittedAndEscaped() {
        var song = new Song();
        song.setTitle("Heart & Soul");
        song.setPlace("New York");
        song.setYear("2024");
        song.setAttribution("Composer <Name>");
        song.setUnderLyrics("under");
        song.setBanglaLyrics("bangla");
        song.setTranslatedLyrics("translated");

        var xml = writeSongToString(song);

        assertThat(xml).contains("<songtitle>Heart &amp; Soul</songtitle>");
        assertThat(xml).contains("<place>New York</place>");
        assertThat(xml).contains("<year>2024</year>");
        assertThat(xml).contains("<rightinfo>Composer &lt;Name&gt;</rightinfo>");
        assertThat(xml).contains("<underlyrics>under</underlyrics>");
        assertThat(xml).contains("<banglalyrics>bangla</banglalyrics>");
        assertThat(xml).contains("<translatedlyrics>translated</translatedlyrics>");
    }

    // row 10: month/day omitted when ≤ 0; emitted when > 0.
    @Test
    void testWriteSongMonthDayOmittedWhenNotPositive() {
        var song = new Song();
        song.setMonth(0);
        song.setDay(0);
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<month>");
        assertThat(xml).doesNotContain("<day>");
    }

    @Test
    void testWriteSongMonthDayEmittedWhenPositive() {
        var song = new Song();
        song.setMonth(3);
        song.setDay(15);
        var xml = writeSongToString(song);

        assertThat(xml).contains("<month>3</month>");
        assertThat(xml).contains("<day>15</day>");
    }

    // row 8: null tempo → no <tempo>; non-null tempo → present.
    @Test
    void testWriteSongTempoAbsentWhenNull() {
        var song = new Song();
        song.setTempo(null);
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<tempo>");
    }

    @Test
    void testWriteSongTempoPresentWhenSet() {
        var song = new Song();
        song.setTempo(new Tempo());
        var xml = writeSongToString(song);

        assertThat(xml).contains("<tempo>");
    }

    // row 11: unofficialTranslation=false → absent; =true → present.
    @Test
    void testWriteSongUnofficialTranslationAbsentWhenFalse() {
        var song = new Song();
        song.setUnofficialTranslation(false);
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<unofficialTranslation>");
    }

    @Test
    void testWriteSongUnofficialTranslationPresentWhenTrue() {
        var song = new Song();
        song.setUnofficialTranslation(true);
        var xml = writeSongToString(song);

        assertThat(xml).contains("<unofficialTranslation>true</unofficialTranslation>");
    }

    // row 13: rowheight omitted when exactly 0 (default); present when non-zero.
    @Test
    void testWriteSongRowHeightAbsentWhenZero() {
        var song = new Song();
        // Default rowHeightAdjustmentSs is 0; no explicit set needed.
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<rowheight>");
    }

    @Test
    void testWriteSongRowHeightPresentWhenNonZero() {
        var song = new Song();
        song.setRowHeightAdjustmentSs(1.5);
        var xml = writeSongToString(song);

        assertThat(xml).contains("<rowheight>1.5</rowheight>");
    }

    // row 17: <view>…</view> block is always written.
    @Test
    void testWriteSongViewBlockAlwaysWritten() {
        var song = new Song();
        var xml = writeSongToString(song);

        assertThat(xml).contains("<view>");
        assertThat(xml).contains("</view>");
    }

    // row 7: XML header and <song> root carry the correct version attribute.
    @Test
    void testWriteSongVersionAttribute() {
        var song = new Song();
        var xml = writeSongToString(song);
        var expectedVersion = SongIO.IO_MAJOR_VERSION + "." + SongIO.IO_MINOR_VERSION;

        assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<song version=\"" + expectedVersion + "\">");
    }

    /**
     * Tests for the v1.0 load path through {@code startElement10} / {@code endElement10}.
     * Covers: {@code startElement} dispatch creates {@code StaffElementReader} + {@code TempoReader}
     * (not {@code LineReader}) for v1.0; {@code endElement10} restores {@code where=SONG} on
     * {@code </notes>} and {@code </tempo_changes>}; first {@code Line} is auto-created on the
     * first note when {@code parsedLines} is empty; and tempo at pos=0 maps to song-level while
     * tempo at pos=N attaches to the element at that flat position.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class V10LoadPath {

        private static final int SONG_LEVEL_TEMPO_BPM = 100;
        private static final int ATTACHED_TEMPO_BPM = 72;
        // Position 1 (0-based) in the flat note list targets the second note.
        private static final int ATTACHED_TEMPO_FLAT_POS = 1;

        // rows 18, 22, 24: parse v1.0 <song> → StaffElementReader loads notes into an
        // auto-created Line; </notes> resets where to SONG; parsedLines non-empty.
        @Test
        void testV10NotesLoadedIntoAutoCreatedLine() throws Exception {
            var song = parseXml(v10TwoNoteXml());

            assertThat(song.lineCount())
                .as("v1.0 parse: first note must auto-create a Line")
                .isGreaterThanOrEqualTo(1);
            assertThat(song.getLine(0).elementCount())
                .as("v1.0 parse: notes must be loaded via StaffElementReader")
                .isGreaterThan(0);
        }

        // row 23 (pos=0): tempo at flat position 0 becomes the song-level tempo.
        @Test
        void testV10TempoAtPositionZeroSetsSongLevelTempo() throws Exception {
            var song = parseXml(v10WithSongLevelTempoXml());

            assertThat(song.getTempo())
                .as("v1.0 tempo at pos=0 must map to song-level tempo")
                .isNotNull();

            //noinspection ConstantValue -- NullAway guard
            if (song.getTempo() == null) {
                return;
            }

            assertThat(song.getTempo().getVisibleTempo())
                .as("song-level tempo BPM")
                .isEqualTo(SONG_LEVEL_TEMPO_BPM);
        }

        // row 23 (pos>0): tempo at flat position N attaches to the element at that position.
        @Test
        void testV10TempoAtNonZeroPositionAttachesToElement() throws Exception {
            var song = parseXml(v10WithAttachedTempoXml());
            var targetNote = song.getLine(0).getElement(ATTACHED_TEMPO_FLAT_POS);
            var attachment = targetNote.findAttachment(TempoChangeAttachment.class);

            assertThat(attachment)
                .as("v1.0 tempo at pos=" + ATTACHED_TEMPO_FLAT_POS +
                    " must attach to the element at that flat position")
                .isNotNull();

            //noinspection ConstantValue -- NullAway guard
            if (attachment == null) {
                return;
            }

            assertThat(attachment.getTempo().getVisibleTempo())
                .as("attached tempo BPM")
                .isEqualTo(ATTACHED_TEMPO_BPM);
        }

        // -- XML builders --

        /** Minimal v1.0 song with two crotchets (no tempo changes). */
        private static String v10TwoNoteXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <song version="1.0">
                  <keys>0</keys>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>2</staffposition>
                    </note>
                  </notes>
                </song>
                """;
        }

        /** v1.0 song with one note and a song-level tempo (position=0). */
        private static String v10WithSongLevelTempoXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <song version="1.0">
                  <keys>0</keys>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                  <tempochanges>
                    <tempochange>
                      <position>0</position>
                      <visibletempo>%d</visibletempo>
                      <tempotype>CROTCHET</tempotype>
                    </tempochange>
                  </tempochanges>
                </song>
                """.formatted(SONG_LEVEL_TEMPO_BPM);
        }

        /**
         * v1.0 song with two crotchets and a tempo attached to the second note
         * (flat position=1).
         */
        private static String v10WithAttachedTempoXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <song version="1.0">
                  <keys>0</keys>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>2</staffposition>
                    </note>
                  </notes>
                  <tempochanges>
                    <tempochange>
                      <position>%d</position>
                      <visibletempo>%d</visibletempo>
                      <tempotype>CROTCHET</tempotype>
                    </tempochange>
                  </tempochanges>
                </song>
                """.formatted(ATTACHED_TEMPO_FLAT_POS, ATTACHED_TEMPO_BPM);
        }
    }

    /**
     * Tests for {@code endElement11} grace-note post-processing: after a line is
     * fully parsed in v1.1 format, every grace note in the last line has {@code upper}
     * forced to {@code true}, regardless of whether the XML contained an {@code <upper/>} tag.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class V11GraceNotePostProcessing {

        // row 25: grace note in v1.1 XML without <upper/> gets upper=true after endElement11.
        @Test
        void testV11GraceNoteUpperForcedTrue() throws Exception {
            var song = parseXml(v11GraceNoteXml());
            var graceNote = song.getLine(0).getElement(0);

            assertThat(graceNote.getType().isGraceNote())
                .as("element 0 must be a grace note")
                .isTrue();
            assertThat(graceNote.isUpper())
                .as("v1.1 grace note must have upper=true after endElement11 post-processing")
                .isTrue();
        }

        /**
         * Minimal v1.1 composition with one grace note (no {@code <upper/>} tag) followed
         * by a regular note. The grace note has no explicit upper flag so the post-processing
         * in {@code endElement11} is the only source of {@code upper=true}.
         */
        private static String v11GraceNoteXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="1.1">
                  <keys>0</keys>
                  <rightinfostarty>0</rightinfostarty>
                  <linewidth>200</linewidth>
                  <lines>
                    <line>
                      <lyricsypos>5.0</lyricsypos>
                      <notes>
                        <note type="GRACE_QUAVER">
                          <staffposition>0</staffposition>
                        </note>
                        <note type="CROTCHET">
                          <staffposition>0</staffposition>
                        </note>
                        <note type="FINAL_DOUBLE_BARLINE">
                          <staffposition>0</staffposition>
                        </note>
                      </notes>
                    </line>
                  </lines>
                  <view/>
                </composition>
                """;
        }
    }

    // -- helpers --

    private static String writeSongToString(Song song) {
        var fonts = DocumentFonts.defaultsFromPrefs();
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        SongIO.writeSong(song, fonts, pw);
        pw.flush();
        return sw.toString();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PerNoteLyricSerialization {

        // T40: Round-trip — write a song with per-note lyrics (COMPOUND_WORD,
        // extend=true); reload; assert Properties.lyrics matches.
        @Test
        void testRoundTripPerNoteLyrics() throws Exception {
            // Load a bare 3-note song as the base structure.
            var song = parseXml(threeNoteXml());
            var line = song.getLine(0);

            line.getElement(0).lyrics.add(
                new Lyric(1, "heart", Lyric.Extend.START, Lyric.Syllabic.BEGIN, true)
            );
            // element 1 intentionally left without lyrics
            line.getElement(2).lyrics.add(
                new Lyric(1, "garden", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)
            );

            var reloaded = roundTrip(song);
            var reloadedLine = reloaded.getLine(0);

            assertThat(reloadedLine.getElement(0).lyrics)
                .as("note 0 lyrics round-trip")
                .containsExactly(new Lyric(1, "heart", Lyric.Extend.START, Lyric.Syllabic.BEGIN, true));

            assertThat(reloadedLine.getElement(1).lyrics)
                .as("note 1 has no lyrics")
                .isEmpty();

            assertThat(reloadedLine.getElement(2).lyrics)
                .as("note 2 lyrics round-trip")
                .containsExactly(new Lyric(1, "garden", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
        }

        // T41: Legacy load — a pre-v2.6 fixture with <lyrics>heart--garden</lyrics>
        // populates per-note Lyric records on load.
        @Test
        void testLegacyLyricsBlobPopulatesPerNoteRecords() throws Exception {
            var song = parseXml(legacyLyricsXml("heart--garden", "2.5"));
            var line = song.getLine(0);

            assertThat(line.getElement(0).lyrics)
                .as("note 0: compound-word syllable")
                .containsExactly(new Lyric(1, "heart", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, true));

            assertThat(line.getElement(1).lyrics)
                .as("note 1: word end syllable")
                .containsExactly(new Lyric(1, "garden", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
        }

        // T42: Version guard — a v2.6 file with both a <lyrics> blob and per-note
        // <lyric> elements does NOT invoke LegacyLyricsImporter; only the per-note
        // data is used.
        @Test
        void testVersionGuardSkipsLegacyImportForNewFormat() throws Exception {
            var song = parseXml(newFormatWithSpuriousLegacyBlob());
            var line = song.getLine(0);

            // The per-note data says "hello"; the <lyrics> blob says "spurious".
            // If the guard works, we get exactly the per-note record.
            assertThat(line.getElement(0).lyrics)
                .as("per-note lyric wins over legacy blob in new-format file")
                .containsExactly(new Lyric(1, "hello", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        }

        // T43: Tolerant nudge-field read — a pre-migration file containing
        // <syllablemovement>, <syllablerelationmovement>, <forcesyllable> loads
        // without error; the obsolete values are silently discarded.
        @Test
        void testObsoleteNudgeFieldsAreToleratedAndDiscarded() {
            assertThatCode(() -> parseXml(legacyNudgeFieldsXml()))
                .doesNotThrowAnyException();
        }

        @Test
        void testObsoleteNudgeFieldsLeaveNoLyricRecords() throws Exception {
            var song = parseXml(legacyNudgeFieldsXml());
            assertThat(song.getLine(0).getElement(0).lyrics)
                .as("nudge-field-only note has no per-note Lyric records")
                .isEmpty();
        }

        // Melisma with MusicXML-style extend carriers: START on the syllable note,
        // STOP on the terminal note with empty text — round-trips through XML.
        @Test
        void testRoundTripMelismaWithStopCarrier() throws Exception {
            var song = parseXml(threeNoteXml());
            var line = song.getLine(0);

            line.getElement(0).lyrics.add(
                new Lyric(1, "ah", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false)
            );
            line.getElement(1).lyrics.add(
                new Lyric(1, "", Lyric.Extend.STOP, null, false)
            );

            var reloaded = roundTrip(song);
            var reloadedLine = reloaded.getLine(0);

            assertThat(reloadedLine.getElement(0).lyrics)
                .containsExactly(new Lyric(1, "ah", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));
            assertThat(reloadedLine.getElement(1).lyrics)
                .containsExactly(new Lyric(1, "", Lyric.Extend.STOP, null, false));
        }

        // A pre-enum v2.6 file with bare <extend/> (no type attribute) loads as START,
        // preserving backward compatibility with files written before the MusicXML update.
        @Test
        void testLegacyExtendTagWithoutTypeLoadsAsStart() throws Exception {
            var song = parseXml(legacyExtendTagXml());
            var reloadedLine = song.getLine(0);

            assertThat(reloadedLine.getElement(0).lyrics)
                .containsExactly(new Lyric(1, "ah", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));
        }

        // MusicXML-style input with <extend type="stop"/> on a note carrying only
        // the stop marker (no syllabic, no text) loads cleanly.
        @Test
        void testMusicXmlStopExtendLoadsAsStopCarrier() throws Exception {
            var song = parseXml(musicXmlStyleStopXml());
            var reloadedLine = song.getLine(0);

            assertThat(reloadedLine.getElement(0).lyrics)
                .containsExactly(new Lyric(1, "ah", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));
            assertThat(reloadedLine.getElement(1).lyrics)
                .containsExactly(new Lyric(1, "", Lyric.Extend.STOP, null, false));
        }

        // T44: Legacy <lyrics> blob with -- maps to COMPOUND_WORD on first note.
        @Test
        void testLegacyDoubleHyphenPreservesCompoundWord() throws Exception {
            var song = parseXml(legacyLyricsXml("heart--garden", "2.5"));

            //noinspection DataFlowIssue -- false positive for Lyric::compound
            assertThat(song.getLine(0).getElement(0).getMainLyric())
                .isNotNull()
                .extracting(Lyric::compound)
                .isEqualTo(true);
        }
    }

    /**
     * End-to-end wiring tests for the migration pipeline through {@link SongIO.DocumentReader#getSong()}.
     * Each test parses a fabricated document at a specific legacy version band and asserts the migrated
     * result on the assembled {@link Song}. Where the per-stage logic is already covered in isolation by
     * {@code MigrationPipelineTest}, these tests verify the cut-over's read-back: that {@code getSong}
     * threads the migrated scalars and lines (from the {@code MigrationContext}) into {@code SongData}
     * rather than the stale parsed fields.
     */
    /**
     * Tests for {@code endElement12} field mapping: keys, keytype, number, title,
     * place, year, month, day, underLyrics, banglaLyrics, translatedLyrics,
     * attribution, footnotes, and unofficialTranslation are all read back correctly
     * on a round-trip. Also covers the title-empty→"Untitled" branch.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement12FieldMapping {

        private static final int NON_DEFAULT_KEY_ACCIDENTAL_COUNT = 2;
        private static final int NON_DEFAULT_NUMBER = 5;
        private static final int NON_DEFAULT_MONTH = 6;
        private static final int NON_DEFAULT_DAY = 15;

        // row 27: round-trip preserves all non-default field values.
        @Test
        void testRoundTripPreservesAllFields() throws Exception {
            var song = new Song();
            song.setDefaultKeyAccidentalCount(NON_DEFAULT_KEY_ACCIDENTAL_COUNT);
            song.setDefaultKeyType(KeyType.SHARPS);
            song.setNumber(String.valueOf(NON_DEFAULT_NUMBER));
            song.setTitle("My Song");
            song.setPlace("London");
            song.setYear("2024");
            song.setMonth(NON_DEFAULT_MONTH);
            song.setDay(NON_DEFAULT_DAY);
            song.setUnderLyrics("under");
            song.setBanglaLyrics("bangla");
            song.setTranslatedLyrics("translated");
            song.setAttribution("Author");
            song.setFootnotes("Note");
            song.setUnofficialTranslation(true);

            var reloaded = roundTrip(song);

            assertThat(reloaded.getDefaultKeyAccidentalCount())
                .as("keys round-trip")
                .isEqualTo(NON_DEFAULT_KEY_ACCIDENTAL_COUNT);
            assertThat(reloaded.getDefaultKeyType())
                .as("keytype round-trip")
                .isEqualTo(KeyType.SHARPS);
            assertThat(reloaded.getNumber())
                .as("number round-trip")
                .isEqualTo(String.valueOf(NON_DEFAULT_NUMBER));
            assertThat(reloaded.getTitle())
                .as("title round-trip")
                .isEqualTo("My Song");
            assertThat(reloaded.getPlace())
                .as("place round-trip")
                .isEqualTo("London");
            assertThat(reloaded.getYear())
                .as("year round-trip")
                .isEqualTo("2024");
            assertThat(reloaded.getMonth())
                .as("month round-trip")
                .isEqualTo(NON_DEFAULT_MONTH);
            assertThat(reloaded.getDay())
                .as("day round-trip")
                .isEqualTo(NON_DEFAULT_DAY);
            assertThat(reloaded.getUnderLyrics())
                .as("underLyrics round-trip")
                .isEqualTo("under");
            assertThat(reloaded.getBanglaLyrics())
                .as("banglaLyrics round-trip")
                .isEqualTo("bangla");
            assertThat(reloaded.getTranslatedLyrics())
                .as("translatedLyrics round-trip")
                .isEqualTo("translated");
            assertThat(reloaded.getAttribution())
                .as("attribution round-trip")
                .isEqualTo("Author");
            assertThat(reloaded.getFootnotes())
                .as("footnotes round-trip")
                .isEqualTo("Note");
            assertThat(reloaded.isUnofficialTranslation())
                .as("unofficialTranslation round-trip")
                .isTrue();
        }

        // row 27 (title-empty branch): an empty <songtitle> tag maps to "Untitled".
        @Test
        void testTitleEmptyTagParsedAsUntitled() throws Exception {
            var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="2.7">
                  <keys>0</keys>
                  <songtitle></songtitle>
                  <rightinfostarty>0.0</rightinfostarty>
                  <linewidth>200.0</linewidth>
                  <dynamicLayout>true</dynamicLayout>
                  <lines>
                    <line>
                      <lyricsypos>5.0</lyricsypos>
                      <notes>
                        <note type="CROTCHET">
                          <staffposition>0</staffposition>
                        </note>
                        <note type="FINAL_DOUBLE_BARLINE">
                          <staffposition>0</staffposition>
                        </note>
                      </notes>
                    </line>
                  </lines>
                  <view/>
                </composition>
                """;

            var song = parseXml(xml);

            assertThat(song.getTitle())
                .as("empty <songtitle> must produce 'Untitled'")
                .isEqualTo("Untitled");
        }
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class LegacyMigrationWiring {

        // Pre-2.1 files store song-level positions in pixels; v2.0 serializes them as integers.
        private static final int LEGACY_LINE_WIDTH_PX = 600;
        private static final int LEGACY_ROW_HEIGHT_PX = 160;
        private static final int LEGACY_ATTRIBUTION_START_Y_PX = 240;

        // A buggy v2.1–2.2 writer stored linewidth as a pixel float; any value at or above the
        // pixel-detection threshold (400) is corrected by dividing by pps.
        private static final double BUGGY_LINE_WIDTH_PX = 700.0;

        // Structural fixture values no migration stage acts on. The linewidth is a valid ss
        // value (below the pixel-detection threshold), so line-width-fix leaves it untouched.
        private static final double INERT_LINE_WIDTH_SS = 200.0;
        private static final double LYRICS_Y_POS_SS = 5.0;

        // Pre-2.1: all three song-level scalars are pixel→ss converted, and getSong reads the
        // converted values (from the context) back into the Song rather than the stale pixel fields.
        @Test
        void testPre21ConvertsSongLevelScalarsToStaffSpace() throws Exception {
            var song = parseXml(pre21ScalarsXml());
            var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

            assertThat(song.getLineWidthSs()).isEqualTo(LEGACY_LINE_WIDTH_PX / pps);
            assertThat(song.getRowHeightAdjustmentSs()).isEqualTo(LEGACY_ROW_HEIGHT_PX / pps);
            assertThat(song.getAttributionStartYSs()).isEqualTo(LEGACY_ATTRIBUTION_START_Y_PX / pps);
        }

        // 2.1–2.2 buggy linewidth: the pixel-stored value is corrected and the corrected value
        // reaches the Song. (pixels-to-ss does not fire for v2.2, so only line-width-fix applies.)
        @Test
        void testBuggyLineWidthIsCorrectedOnLoad() throws Exception {
            var song = parseXml(buggyLineWidthXml());

            assertThat(song.getLineWidthSs())
                .isEqualTo(BUGGY_LINE_WIDTH_PX / ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        }

        // Pre-2.4 final-terminal: a last line ending in SINGLE_BARLINE has it replaced with
        // FINAL_DOUBLE_BARLINE. Proves the migrated line list (not a stale copy) feeds the Song.
        @Test
        void testPre24EnforcesFinalTerminal() throws Exception {
            var song = parseXml(pre24SingleBarlineXml());
            var line = song.getLine(0);

            assertThat(line.getElement(line.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        // Pre-2.3 annotation-dynamics: a note annotated "f" becomes a DynamicAttachment(FORTE)
        // with the original annotation removed, observed on the assembled Song.
        @Test
        void testPre23ConvertsAnnotationToDynamic() throws Exception {
            var song = parseXml(pre23ForteAnnotationXml());
            var note = song.getLine(0).getElement(0);

            var dynamic = note.findAttachment(DynamicAttachment.class);
            assertThat(dynamic).isNotNull();

            //noinspection ConstantValue -- needed for NullAway
            if (dynamic == null) {
                return;
            }

            assertThat(dynamic.getType()).isEqualTo(DynamicType.FORTE);
            assertThat(note.findAttachment(AnnotationAttachment.class)).isNull();
        }

        // -- Document builders --

        private String pre21ScalarsXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="2.0">
                  <keys>0</keys>
                  <rightinfostarty>%d</rightinfostarty>
                  <rowheight>%d</rowheight>
                  <linewidth>%d</linewidth>
                  <lines>
                    <line>
                      <lyricsypos>%s</lyricsypos>
                      <notes>
                        <note type="CROTCHET">
                          <staffposition>0</staffposition>
                        </note>
                        <note type="SINGLE_BARLINE">
                          <staffposition>0</staffposition>
                        </note>
                      </notes>
                    </line>
                  </lines>
                  <view/>
                </composition>
                """.formatted(
                LEGACY_ATTRIBUTION_START_Y_PX,
                LEGACY_ROW_HEIGHT_PX,
                LEGACY_LINE_WIDTH_PX,
                LYRICS_Y_POS_SS
            );
        }

        private String buggyLineWidthXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="2.2">
                  <keys>0</keys>
                  <rightinfostarty>0.0</rightinfostarty>
                  <linewidth>%s</linewidth>
                  <lines>
                    <line>
                      <lyricsypos>%s</lyricsypos>
                      <notes>
                        <note type="CROTCHET">
                          <staffposition>0</staffposition>
                        </note>
                        <note type="FINAL_DOUBLE_BARLINE">
                          <staffposition>0</staffposition>
                        </note>
                      </notes>
                    </line>
                  </lines>
                  <view/>
                </composition>
                """.formatted(BUGGY_LINE_WIDTH_PX, LYRICS_Y_POS_SS);
        }

        private String pre24SingleBarlineXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="2.3">
                  <keys>0</keys>
                  <rightinfostarty>0.0</rightinfostarty>
                  <linewidth>%s</linewidth>
                  <lines>
                    <line>
                      <lyricsypos>%s</lyricsypos>
                      <notes>
                        <note type="CROTCHET">
                          <staffposition>0</staffposition>
                        </note>
                        <note type="SINGLE_BARLINE">
                          <staffposition>0</staffposition>
                        </note>
                      </notes>
                    </line>
                  </lines>
                  <view/>
                </composition>
                """.formatted(INERT_LINE_WIDTH_SS, LYRICS_Y_POS_SS);
        }

        private String pre23ForteAnnotationXml() {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="2.2">
                  <keys>0</keys>
                  <rightinfostarty>0.0</rightinfostarty>
                  <linewidth>%s</linewidth>
                  <lines>
                    <line>
                      <lyricsypos>%s</lyricsypos>
                      <notes>
                        <note type="CROTCHET">
                          <staffposition>0</staffposition>
                          <annotation>
                            <name>f</name>
                          </annotation>
                        </note>
                        <note type="FINAL_DOUBLE_BARLINE">
                          <staffposition>0</staffposition>
                        </note>
                      </notes>
                    </line>
                  </lines>
                  <view/>
                </composition>
                """.formatted(INERT_LINE_WIDTH_SS, LYRICS_Y_POS_SS);
        }

    }

    // -- XML Helpers --

    /**
     * Two-line v2.7 composition XML.
     * Line 0: 2 crotchets + single barline (3 elements).
     * Line 1: 3 crotchets + final-double-barline (4 elements).
     */
    private static String twoLineXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.7">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <dynamicLayout>true</dynamicLayout>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="FINAL_DOUBLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    /** Minimal v2.6 composition XML with three crotchets and a terminal barline. */
    private static String threeNoteXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.6">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    /**
     * Minimal v{@code version} composition XML with two crotchets and a {@code <lyrics>} blob.
     */
    private static String legacyLyricsXml(String lyricsBlob, String version) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="%s">
              <keys>0</keys>
              <lyrics>%s</lyrics>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """.formatted(version, lyricsBlob);
    }

    /**
     * A v2.6 composition with a {@code <lyrics>} blob (which the version guard must ignore)
     * AND a per-note {@code <lyric>} element with text "hello".
     */
    private static String newFormatWithSpuriousLegacyBlob() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.6">
              <keys>0</keys>
              <lyrics>spurious</lyrics>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <syllabic>single</syllabic>
                        <text>hello</text>
                      </lyric>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    /** A v2.6 composition with a bare {@code <extend/>} tag (pre-MusicXML format). */
    private static String legacyExtendTagXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.6">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <syllabic>single</syllabic>
                        <text>ah</text>
                        <extend />
                      </lyric>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    /** MusicXML-style input with start + stop extend carriers. */
    private static String musicXmlStyleStopXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.6">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <syllabic>single</syllabic>
                        <text>ah</text>
                        <extend type="start"/>
                      </lyric>
                    </note>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <extend type="stop"/>
                      </lyric>
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }

    /**
     * A pre-migration v2.5 composition with obsolete per-note nudge fields that
     * must be silently discarded.
     */
    private static String legacyNudgeFieldsXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.5">
              <keys>0</keys>
              <rightinfostarty>0.0</rightinfostarty>
              <linewidth>200.0</linewidth>
              <lines>
                <line>
                  <lyricsypos>5.0</lyricsypos>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <syllablemovement>3</syllablemovement>
                      <syllablerelationmovement>-2</syllablerelationmovement>
                      <forcesyllable />
                    </note>
                    <note type="SINGLE_BARLINE">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;
    }
}
