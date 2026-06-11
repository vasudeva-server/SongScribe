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
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import javax.xml.parsers.SAXParserFactory;

import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
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
    // Tests: title, place, year, underlyrics, banglaLyrics, translatedLyrics.
    // Note: <rightinfo> is always written (computed from discrete attribution fields).
    @Test
    void testWriteSongOptionalStringFieldsOmittedWhenEmpty() {
        var song = new Song();
        // Song() sets non-empty defaults for some fields — clear them explicitly.
        {
            var m = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "", m.number(), "", "", m.month(), m.day(),
                m.composer(), m.lyricist(), m.lyricsSource(), m.arrangement(), m.unofficialTranslation()
            ));
        }
        song.setUnderLyrics("");
        song.setBanglaLyrics("");
        song.setTranslatedLyrics("");
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<songtitle>");
        assertThat(xml).doesNotContain("<place>");
        assertThat(xml).doesNotContain("<year>");
        assertThat(xml).doesNotContain("<underlyrics>");
        assertThat(xml).doesNotContain("<banglalyrics>");
        assertThat(xml).doesNotContain("<translatedlyrics>");
        // <rightinfo> is always written (computed from discrete attribution fields).
        assertThat(xml).contains("<rightinfo>");
        // <arrangement> is omitted when isArrangement() is false (the default).
        assertThat(xml).doesNotContain("<arrangement>");
    }

    // <arrangement>true</arrangement> is written when isArrangement() is true.
    @Test
    void testWriteSongArrangementEmittedWhenTrue() {
        var song = new Song();
        {
            var m = song.getMetadata();
            song.setMetadata(new SongMetadata(
                m.title(), m.number(), m.place(), m.year(), m.month(), m.day(),
                m.composer(), m.lyricist(), m.lyricsSource(), true, m.unofficialTranslation()
            ));
        }
        var xml = writeSongToString(song);

        assertThat(xml).contains("<arrangement>true</arrangement>");
    }

    // row 9 (presence + escaping): non-empty values appear and are XML-escaped.
    // <rightinfo> carries the computed attribution blob; discrete fields are always written.
    @Test
    void testWriteSongOptionalStringFieldsEmittedAndEscaped() {
        var song = new Song();
        {
            var m = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Heart & Soul", m.number(), "New York", "2024", m.month(), m.day(),
                "Composer <Name>", m.lyricist(), m.lyricsSource(), m.arrangement(), m.unofficialTranslation()
            ));
        }
        song.setUnderLyrics("under");
        song.setBanglaLyrics("bangla");
        song.setTranslatedLyrics("translated");

        var xml = writeSongToString(song);

        assertThat(xml).contains("<songtitle>Heart &amp; Soul</songtitle>");
        assertThat(xml).contains("<place>New York</place>");
        assertThat(xml).contains("<year>2024</year>");
        assertThat(xml).contains("<composer>Composer &lt;Name&gt;</composer>");
        assertThat(xml).contains("<lyricist>");
        assertThat(xml).contains("<lyricssource>");
        assertThat(xml).contains("<underlyrics>under</underlyrics>");
        assertThat(xml).contains("<banglalyrics>bangla</banglalyrics>");
        assertThat(xml).contains("<translatedlyrics>translated</translatedlyrics>");
    }

    // row 10: month/day omitted when ≤ 0; emitted when > 0.
    @Test
    void testWriteSongMonthDayOmittedWhenNotPositive() {
        var song = new Song();
        // month and day are already 0 by default; this just asserts the default behavior
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<month>");
        assertThat(xml).doesNotContain("<day>");
    }

    @Test
    void testWriteSongMonthDayEmittedWhenPositive() {
        var song = new Song();
        {
            var m = song.getMetadata();
            song.setMetadata(new SongMetadata(
                m.title(), m.number(), m.place(), m.year(), 3, 15,
                m.composer(), m.lyricist(), m.lyricsSource(), m.arrangement(), m.unofficialTranslation()
            ));
        }
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
        // unofficialTranslation is false by default; no change needed
        var xml = writeSongToString(song);

        assertThat(xml).doesNotContain("<unofficialTranslation>");
    }

    @Test
    void testWriteSongUnofficialTranslationPresentWhenTrue() {
        var song = new Song();
        {
            var m = song.getMetadata();
            song.setMetadata(new SongMetadata(
                m.title(), m.number(), m.place(), m.year(), m.month(), m.day(),
                m.composer(), m.lyricist(), m.lyricsSource(), m.arrangement(), true
            ));
        }
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
                .isNotNull()
                .extracting(t -> t.getVisibleTempo())
                .as("song-level tempo BPM")
                .isEqualTo(SONG_LEVEL_TEMPO_BPM);
        }

        // row 23 (pos>0): tempo at flat position N attaches to the element at that position.
        @Test
        void testV10TempoAtNonZeroPositionAttachesToElement() throws Exception {
            var song = parseXml(v10WithAttachedTempoXml());
            var targetNote = song.getLine(0).getElement(ATTACHED_TEMPO_FLAT_POS);
            var attachment = targetNote.findAttachment(TempoChangeAttachment.class);

            // asInstanceOf both narrows the type and fails loudly if the value is
            // null, so the BPM assertion can never be silently skipped.
            assertThat(attachment)
                .as("v1.0 tempo at pos=" + ATTACHED_TEMPO_FLAT_POS +
                    " must attach to the element at that flat position")
                .asInstanceOf(type(TempoChangeAttachment.class))
                .extracting(a -> a.getTempo().getVisibleTempo())
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
            song.setMetadata(new SongMetadata(
                "My Song", String.valueOf(NON_DEFAULT_NUMBER), "London", "2024",
                NON_DEFAULT_MONTH, NON_DEFAULT_DAY,
                "Bach", Song.SRI_CHINMOY, Song.LyricsSource.TEXT, true, true
            ));
            song.setUnderLyrics("under");
            song.setBanglaLyrics("bangla");
            song.setTranslatedLyrics("translated");
            song.setFootnotes("Note");

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
            assertThat(reloaded.getComposer())
                .as("composer round-trip")
                .isEqualTo("Bach");
            assertThat(reloaded.getLyricist())
                .as("lyricist round-trip")
                .isEqualTo(Song.SRI_CHINMOY);
            assertThat(reloaded.getLyricsSource())
                .as("lyricsSource round-trip")
                .isEqualTo(Song.LyricsSource.TEXT);
            assertThat(reloaded.isArrangement())
                .as("arrangement round-trip")
                .isTrue();
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
    // =========================================================================
    // Phase 5 tests
    // =========================================================================

    /**
     * Tests for the validation guards added to {@code endElement12} in Phase 3 (C-10 / I-1).
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement12Validation {

        // C-10a: unknown KeyType string → SAXException
        @Test
        void testUnknownKeyTypeStringThrowsSAXException() {
            assertThatThrownBy(() -> parseXml(buildXmlWithKeyType("BOGUS_KEYTYPE")))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Corrupt document: unknown key type: 'BOGUS_KEYTYPE'");
        }

        // C-10b: non-numeric <keys> value → SAXException
        @Test
        void testNonNumericKeysValueThrowsSAXException() {
            assertThatThrownBy(() -> parseXml(buildXmlWithKeys("notanumber")))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Corrupt document: malformed keys value: 'notanumber'");
        }

        // non-numeric <month> value → SAXException (parse fails before range check)
        @Test
        void testNonNumericMonthThrowsSAXException() {
            assertThatThrownBy(() -> parseXml(buildXmlWithMonth("abc")))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Corrupt document: malformed month value: 'abc'");
        }

        // non-numeric <day> value → SAXException (parse fails before range check)
        @Test
        void testNonNumericDayThrowsSAXException() {
            assertThatThrownBy(() -> parseXml(buildXmlWithDay("abc")))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Corrupt document: malformed day value: 'abc'");
        }

        // I-1: out-of-range month value → month zeroed
        @Test
        void testOutOfRangeMonthZerosMonth() throws Exception {
            var song = parseXml(buildXmlWithMonth("13"));
            assertThat(song.getMonth()).isZero();
        }

        // I-1: out-of-range month with a valid day → both zeroed
        @Test
        void testOutOfRangeMonthAlsoResetsDay() throws Exception {
            var song = parseXml(buildXmlWithMonthAndDay("13", "15"));
            assertThat(song.getMonth()).isZero();
            assertThat(song.getDay()).isZero();
        }

        // I-1: out-of-range month value → WARN logged
        @Test
        void testOutOfRangeMonthLogsWarn() {
            var logger = (Logger) LoggerFactory.getLogger(SongIO.DocumentReader.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);

            try {
                parseXml(buildXmlWithMonth("13"));
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("month"));
            } catch (Exception ignored) {
                // Should not throw, but guard in case — assertion above captures the real failure
            } finally {
                logger.detachAppender(appender);
            }
        }

        // I-1: out-of-range day value → day zeroed
        @Test
        void testOutOfRangeDayZerosDay() throws Exception {
            var song = parseXml(buildXmlWithDay("32"));
            assertThat(song.getDay()).isZero();
        }

        // I-1: out-of-range day value → WARN logged
        @Test
        void testOutOfRangeDayLogsWarn() {
            var logger = (Logger) LoggerFactory.getLogger(SongIO.DocumentReader.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);

            try {
                parseXml(buildXmlWithDay("32"));
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("day"));
            } catch (Exception ignored) {
                // Should not throw, but guard in case — assertion above captures the real failure
            } finally {
                logger.detachAppender(appender);
            }
        }

        private static String minimalCompositionXml(String extraHeaders) {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="2.7">
                  %s
                  <rightinfostarty>0.0</rightinfostarty>
                  <linewidth>200.0</linewidth>
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
                """.formatted(extraHeaders);
        }

        private static String buildXmlWithKeyType(String keyType) {
            return minimalCompositionXml("<keys>0</keys>\n  <keytype>" + keyType + "</keytype>");
        }

        private static String buildXmlWithKeys(String keys) {
            return minimalCompositionXml("<keys>" + keys + "</keys>\n  <keytype>SHARPS</keytype>");
        }

        private static String buildXmlWithMonth(String month) {
            return minimalCompositionXml(
                "<keys>0</keys>\n  <keytype>SHARPS</keytype>\n  <month>" + month + "</month>"
            );
        }

        private static String buildXmlWithDay(String day) {
            return minimalCompositionXml(
                "<keys>0</keys>\n  <keytype>SHARPS</keytype>\n  <day>" + day + "</day>"
            );
        }

        private static String buildXmlWithMonthAndDay(String month, String day) {
            return minimalCompositionXml(
                "<keys>0</keys>\n  <keytype>SHARPS</keytype>\n  <month>" + month + "</month>\n  <day>" + day + "</day>"
            );
        }
    }

    /**
     * Tests for the legacy tempo-position bounds check added to {@code endElement10} in Phase 3 (C-12).
     *
     * <p>The guard fires when {@code idx = pos - firstElementInLine} is negative — which requires
     * a tempo position that falls in the "gap" between lines. In v1.0 format there is only ever a
     * single {@code <notes>} block, so {@code parsedLines} always contains exactly one entry and
     * {@code firstElementInLine} is always 0. The {@code idx < 0} path is therefore unreachable
     * through the standard v1.0 XML parsing flow; these tests exercise the closest reachable
     * behaviour (out-of-range position silently skips without crashing, and a valid position
     * attaches the tempo correctly).
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndElement10LegacyTempoBounds {

        private static final int OUT_OF_RANGE_TEMPO_POS = 99;
        private static final int VALID_TEMPO_POS = 0;
        private static final int EXPECTED_VISIBLE_TEMPO = 120;

        // C-12 (soft boundary): a tempo position beyond elementCount in v1.0 format silently
        // skips rather than throwing, because the loop condition prevents entry when pos >= elementCount.
        @Test
        void testLegacyTempoPositionBeyondElementCountDoesNotThrow() {
            assertThatCode(() -> parseXml(buildV10WithTempoPos(OUT_OF_RANGE_TEMPO_POS)))
                .doesNotThrowAnyException();
        }

        // C-12 (valid path): a valid tempo position in v1.0 format attaches the tempo correctly.
        @Test
        void testLegacyTempoAtPositionZeroAttachesToSong() throws Exception {
            var song = parseXml(buildV10WithTempoPos(VALID_TEMPO_POS));
            assertThat(song.getTempo())
                .asInstanceOf(type(Tempo.class))
                .extracting(Tempo::getVisibleTempo)
                .isEqualTo(EXPECTED_VISIBLE_TEMPO);
        }

        private static String buildV10WithTempoPos(int pos) {
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
                      <position>%d</position>
                      <visibletempo>%d</visibletempo>
                      <tempotype>CROTCHET</tempotype>
                    </tempochange>
                  </tempochanges>
                </song>
                """.formatted(pos, EXPECTED_VISIBLE_TEMPO);
        }
    }

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

    /**
     * Tests for {@link SongIO.DocumentReader#parseLegacyAttributionBlob} and
     * {@link SongIO.DocumentReader#parseLegacyDateLine}.
     *
     * <p>These methods run on files predating the discrete attribution tags (< 2.8).
     * The blob is the text content of the {@code <rightinfo>} element.
     * For v1.0 files ({@code isV10File()} true), date and place lines after the
     * "by" line are also parsed.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LegacyAttributionBlob {

        // ------------------------------------------------------------------
        // parseLegacyAttributionBlob branches
        // ------------------------------------------------------------------

        // Blank blob → composer and lyricist fall back to Song.SRI_CHINMOY (the default).
        @Test
        void testBlankBlobLeavesSriChinmoyAsDefault() throws Exception {
            var song = parseXml(legacyBlobXml("2.0", "   "));

            assertThat(song.getComposer()).isEqualTo(Song.SRI_CHINMOY);
            assertThat(song.getLyricist()).isEqualTo(Song.SRI_CHINMOY);
        }

        // No "by " line in blob → composer and lyricist fall back to Song.SRI_CHINMOY.
        @Test
        void testBlobWithoutByLineLeavesDefaultNames() throws Exception {
            var song = parseXml(legacyBlobXml("2.0", "Words and Music"));

            assertThat(song.getComposer()).isEqualTo(Song.SRI_CHINMOY);
            assertThat(song.getLyricist()).isEqualTo(Song.SRI_CHINMOY);
        }

        // "by " line with empty person name → fall back to Song.SRI_CHINMOY.
        @Test
        void testByLineWithEmptyPersonLeavesDefaultNames() throws Exception {
            var song = parseXml(legacyBlobXml("2.0", "Words and Music\nby "));

            assertThat(song.getComposer()).isEqualTo(Song.SRI_CHINMOY);
            assertThat(song.getLyricist()).isEqualTo(Song.SRI_CHINMOY);
        }

        // "by SomeName" → composer and lyricist both set to SomeName.
        @Test
        void testByLineWithPersonSetsComposerAndLyricist() throws Exception {
            var song = parseXml(legacyBlobXml("2.0", "Words and Music\nby SomeName"));

            assertThat(song.getComposer()).isEqualTo("SomeName");
            assertThat(song.getLyricist()).isEqualTo("SomeName");
        }

        // ------------------------------------------------------------------
        // parseLegacyDateLine branches (v1.0 files only)
        // ------------------------------------------------------------------

        // Full "Month Day, Year" → month, day, year all populated.
        @Test
        void testV10FullDateLinePopulatesMonthDayYear() throws Exception {
            var song = parseXml(v10BlobXml("Words and Music\nby SomeName\nJune 15, 2001"));

            assertThat(song.getMonth()).isEqualTo(6);
            assertThat(song.getDay()).isEqualTo(15);
            assertThat(song.getYear()).isEqualTo("2001");
        }

        // "Month, Year" (no day) → month and year populated; day stays 0.
        @Test
        void testV10MonthYearLinePopulatesMonthAndYear() throws Exception {
            var song = parseXml(v10BlobXml("Words and Music\nby SomeName\nMarch, 1999"));

            assertThat(song.getMonth()).isEqualTo(3);
            assertThat(song.getDay()).isZero();
            assertThat(song.getYear()).isEqualTo("1999");
        }

        // Year only → year populated; month and day stay at defaults.
        @Test
        void testV10YearOnlyLinePopulatesYear() throws Exception {
            var song = parseXml(v10BlobXml("Words and Music\nby SomeName\n2005"));

            assertThat(song.getYear()).isEqualTo("2005");
            assertThat(song.getMonth()).isZero();
            assertThat(song.getDay()).isZero();
        }

        // Unrecognised date format (no leading month name, not purely numeric) →
        // treated as a year string; no month or day set.
        @Test
        void testV10UnrecognisedDateFormatTreatedAsYear() throws Exception {
            var song = parseXml(v10BlobXml("Words and Music\nby SomeName\nABC123"));

            assertThat(song.getYear()).isEqualTo("ABC123");
            assertThat(song.getMonth()).isZero();
            assertThat(song.getDay()).isZero();
        }

        // ------------------------------------------------------------------
        // XML builders
        // ------------------------------------------------------------------

        /**
         * Minimal legacy composition at the given version (must be < 2.8 to
         * trigger {@code isLegacyAttributionFile()}) with the given
         * {@code <rightinfo>} blob.
         */
        private static String legacyBlobXml(String version, String blob) {
            // rightinfostarty and linewidth must be integers for v2.0 (parseVersionedDouble
            // calls Integer.parseInt for versions < 2.1).
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <composition version="%s">
                  <keys>0</keys>
                  <rightinfostarty>0</rightinfostarty>
                  <linewidth>200</linewidth>
                  <rightinfo>%s</rightinfo>
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
                """.formatted(version, blob);
        }

        /**
         * Minimal v1.0 song with the given {@code <rightinfo>} blob.
         * v1.0 uses {@code <song>} root and triggers {@code isV10File()},
         * enabling date/place parsing from the blob.
         */
        private static String v10BlobXml(String blob) {
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <song version="1.0">
                  <keys>0</keys>
                  <rightinfo>%s</rightinfo>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                    </note>
                  </notes>
                </song>
                """.formatted(blob);
        }
    }
}
