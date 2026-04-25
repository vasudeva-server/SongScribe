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

import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.music.Composition;
import songscribe.music.Lyric;
import songscribe.music.StaffElement.SyllableRelation;

class CompositionIOTest extends UnitTest {

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();

    @Test
    void testOpeningNewerVersionFileThrowsNewerVersionException() {
        assertThatThrownBy(() -> loadFixture("newer-version"))
            .isInstanceOf(CompositionIO.NewerVersionException.class);
    }

    @Nested
    class PerNoteLyricSerialization {

        // T40: Round-trip — write a composition with per-note lyrics (COMPOUND_WORD,
        // extend=true); reload; assert Properties.lyrics matches.
        @Test
        void testRoundTripPerNoteLyrics() throws Exception {
            // Load a bare 3-note composition as the base structure.
            var composition = parseXml(threeNoteXml());
            var line = composition.getLine(0);

            line.getElement(0).properties.lyrics.add(
                new Lyric(1, SyllableRelation.COMPOUND_WORD, "heart", Lyric.Extend.START)
            );
            // element 1 intentionally left without lyrics
            line.getElement(2).properties.lyrics.add(
                new Lyric(1, SyllableRelation.NONE, "garden", Lyric.Extend.NONE)
            );

            var reloaded = roundTrip(composition);
            var reloadedLine = reloaded.getLine(0);

            assertThat(reloadedLine.getElement(0).properties.lyrics)
                .as("note 0 lyrics round-trip")
                .containsExactly(new Lyric(1, SyllableRelation.COMPOUND_WORD, "heart", Lyric.Extend.START));

            assertThat(reloadedLine.getElement(1).properties.lyrics)
                .as("note 1 has no lyrics")
                .isEmpty();

            assertThat(reloadedLine.getElement(2).properties.lyrics)
                .as("note 2 lyrics round-trip")
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "garden", Lyric.Extend.NONE));
        }

        // T41: Legacy load — a pre-v2.6 fixture with <lyrics>heart--garden</lyrics>
        // populates per-note Lyric records on load.
        @Test
        void testLegacyLyricsBlobPopulatesPerNoteRecords() throws Exception {
            var composition = parseXml(legacyLyricsXml("heart--garden", "2.5"));
            var line = composition.getLine(0);

            assertThat(line.getElement(0).properties.lyrics)
                .as("note 0: compound-word syllable")
                .containsExactly(new Lyric(1, SyllableRelation.COMPOUND_WORD, "heart", Lyric.Extend.NONE));

            assertThat(line.getElement(1).properties.lyrics)
                .as("note 1: word end syllable")
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "garden", Lyric.Extend.NONE));
        }

        // T42: Version guard — a v2.6 file with both a <lyrics> blob and per-note
        // <lyric> elements does NOT invoke LegacyLyricsImporter; only the per-note
        // data is used.
        @Test
        void testVersionGuardSkipsLegacyImportForNewFormat() throws Exception {
            var composition = parseXml(newFormatWithSpuriousLegacyBlob());
            var line = composition.getLine(0);

            // The per-note data says "hello"; the <lyrics> blob says "spurious".
            // If the guard works, we get exactly the per-note record.
            assertThat(line.getElement(0).properties.lyrics)
                .as("per-note lyric wins over legacy blob in new-format file")
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "hello", Lyric.Extend.NONE));
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
            var composition = parseXml(legacyNudgeFieldsXml());
            assertThat(composition.getLine(0).getElement(0).properties.lyrics)
                .as("nudge-field-only note has no per-note Lyric records")
                .isEmpty();
        }

        // Melisma with MusicXML-style extend carriers: START on the syllable note,
        // STOP on the terminal note with empty text — round-trips through XML.
        @Test
        void testRoundTripMelismaWithStopCarrier() throws Exception {
            var composition = parseXml(threeNoteXml());
            var line = composition.getLine(0);

            line.getElement(0).properties.lyrics.add(
                new Lyric(1, SyllableRelation.NONE, "ah", Lyric.Extend.START)
            );
            line.getElement(1).properties.lyrics.add(
                new Lyric(1, SyllableRelation.NONE, "", Lyric.Extend.STOP)
            );

            var reloaded = roundTrip(composition);
            var reloadedLine = reloaded.getLine(0);

            assertThat(reloadedLine.getElement(0).properties.lyrics)
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "ah", Lyric.Extend.START));
            assertThat(reloadedLine.getElement(1).properties.lyrics)
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "", Lyric.Extend.STOP));
        }

        // A pre-enum v2.6 file with bare <extend/> (no type attribute) loads as START,
        // preserving backward compatibility with files written before the MusicXML update.
        @Test
        void testLegacyExtendTagWithoutTypeLoadsAsStart() throws Exception {
            var composition = parseXml(legacyExtendTagXml());
            var reloadedLine = composition.getLine(0);

            assertThat(reloadedLine.getElement(0).properties.lyrics)
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "ah", Lyric.Extend.START));
        }

        // MusicXML-style input with <extend type="stop"/> on a note carrying only
        // the stop marker (no syllabic, no text) loads cleanly.
        @Test
        void testMusicXmlStopExtendLoadsAsStopCarrier() throws Exception {
            var composition = parseXml(musicXmlStyleStopXml());
            var reloadedLine = composition.getLine(0);

            assertThat(reloadedLine.getElement(0).properties.lyrics)
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "ah", Lyric.Extend.START));
            assertThat(reloadedLine.getElement(1).properties.lyrics)
                .containsExactly(new Lyric(1, SyllableRelation.NONE, "", Lyric.Extend.STOP));
        }

        // T44: Legacy <lyrics> blob with -- maps to COMPOUND_WORD on first note.
        @Test
        void testLegacyDoubleHyphenPreservesCompoundWord() throws Exception {
            var composition = parseXml(legacyLyricsXml("heart--garden", "2.5"));

            assertThat(composition.getLine(0).getElement(0).getMainLyric())
                .isNotNull()
                .extracting(Lyric::relation)
                .isEqualTo(SyllableRelation.COMPOUND_WORD);
        }
    }

    // -- XML Helpers --

    private static Composition parseXml(String xml) throws Exception {
        var parser = PARSER_FACTORY.newSAXParser();
        var reader = new CompositionIO.DocumentReader();
        parser.parse(new InputSource(new StringReader(xml)), reader);
        return reader.getComposition();
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
