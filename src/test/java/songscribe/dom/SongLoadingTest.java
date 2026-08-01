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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song.LyricsSource;
import songscribe.message.SongData;

@SuppressWarnings("OverlyBroadThrowsClause")
class SongLoadingTest extends UnitTest {

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
        song.setModified(false);
    }

    @Test
    void testLoadingCtorUsesDataLinesWithNoExtraDefaultLine() {
        // Song(SongData) must not add a default line — only the lines supplied by SongData.
        var stub = Song.newParsingStub();
        var dataLine = new Line(stub);
        stub.withoutMutationTracking(() -> {
            // Pre-set key defaults so applyLineDefaults finds them already applied and is a no-op,
            // avoiding a setKeyAccidentalCount call that would require a bracket or suspension.
            dataLine.setKeyAccidentalCount(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);
            dataLine.setKeyType(Song.DEFAULT_KEY_TYPE);
            dataLine.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
        });

        var data = new SongData(
            null, "", "", "", 0, 0, "", "", "", "",
            Song.SRI_CHINMOY, Song.SRI_CHINMOY, LyricsSource.LYRICIST, false,
            "", false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT, Song.DEFAULT_KEY_TYPE,
            0.0, 0.0,
            List.of(dataLine), false, 1,
            "", "", 0, 0
        );

        var loaded = new Song(data);

        assertThat(loaded.lineCount())
            .as("Song(SongData) must contain exactly the lines from SongData, not an extra default line")
            .isEqualTo(1);
        assertThat(loaded.getLine(0)).isSameAs(dataLine);
    }

    // T63: Loading a pre-2.4 file runs migrateFinalTerminal inside withoutMutationTracking,
    //      so the document is clean even though migration mutated elements.
    @Test
    void testLoadingLegacySongDoesNotDirtyDocument() throws Exception {
        var loaded = loadFixture("full-line");
        assertThat(loaded.isModified()).isFalse();
    }

    @Test
    void testLoadFromAppliesAllScalarFields() {
        var tempo = new Tempo();
        tempo.setVisibleTempo(180);

        var line = new Line(song);
        var lineWithTempo = new Line(song);
        song.withoutMutationTracking(() -> {
            lineWithTempo.addElement(new StaffElement(ElementType.CROTCHET));
            lineWithTempo.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
        });

        var data = new SongData(
            tempo,
            "42",
            "Test Title",
            "Paris",
            3,
            15,
            "2024",
            "under text",
            "bangla text",
            "translated text",
            "Bach",
            Song.SRI_CHINMOY,
            LyricsSource.TEXT,
            true,
            "See note 1",
            true,
            2,
            KeyType.SHARPS,
            1.5,
            50.0,
            List.of(lineWithTempo),
            true,
            2,
            "", "", 0, 0
        );

        song.withoutMutationTracking(() -> song.loadFrom(data));

        assertThat(song.getTempo()).isSameAs(tempo);
        assertThat(song.getNumber()).isEqualTo("42");
        assertThat(song.getTitle()).isEqualTo("Test Title");
        assertThat(song.getPlace()).isEqualTo("Paris");
        assertThat(song.getMonth()).isEqualTo(3);
        assertThat(song.getDay()).isEqualTo(15);
        assertThat(song.getYear()).isEqualTo("2024");
        assertThat(song.getUnderLyrics()).isEqualTo("under text");
        assertThat(song.getBanglaLyrics()).isEqualTo("bangla text");
        assertThat(song.getTranslatedLyrics()).isEqualTo("translated text");
        assertThat(song.getComposer()).isEqualTo("Bach");
        assertThat(song.getLyricist()).isEqualTo(Song.SRI_CHINMOY);
        assertThat(song.getLyricsSource()).isEqualTo(LyricsSource.TEXT);
        assertThat(song.isArrangement()).isTrue();
        assertThat(song.getFootnotes()).isEqualTo("See note 1");
        assertThat(song.isUnofficialTranslation()).isTrue();
        assertThat(song.getDefaultKeyAccidentalCount()).isEqualTo(2);
        assertThat(song.getDefaultKeyType()).isEqualTo(KeyType.SHARPS);
        assertThat(song.getRowHeightAdjustmentSs()).isEqualTo(1.5);
        assertThat(song.getLineWidthSs()).isEqualTo(50.0);
        assertThat(song.lineCount()).isEqualTo(1);
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testLoadFromClearsPreviousLinesAndReplacesThem() {
        var extraLine = new Line(song);
        song.withoutMutationTracking(() -> {
            extraLine.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            song.addLine(1, extraLine);
        });
        assertThat(song.lineCount()).isEqualTo(2);

        var newLine = new Line(song);
        song.withoutMutationTracking(() -> {
            newLine.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
        });

        var data = new SongData(
            null,
            "",
            "",
            "",
            0,
            0,
            "",
            "",
            "",
            "",
            Song.SRI_CHINMOY,
            Song.SRI_CHINMOY,
            LyricsSource.LYRICIST,
            false,
            "",
            false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT,
            Song.DEFAULT_KEY_TYPE,
            0.0,
            0.0,
            List.of(newLine),
            false,
            1,
            "", "", 0, 0
        );

        song.withoutMutationTracking(() -> song.loadFrom(data));

        assertThat(song.lineCount()).isEqualTo(1);
        assertThat(song.getLine(0)).isSameAs(newLine);
        assertThat(song.isModified()).isFalse();
    }

    @Test
    void testLoadFromAttachesInitialTempoToFirstLineElement() {
        var tempo = new Tempo();
        tempo.setVisibleTempo(120);

        var lineWithNote = new Line(song);
        var note = new StaffElement(ElementType.CROTCHET);
        song.withoutMutationTracking(() -> {
            lineWithNote.addElement(note);
            lineWithNote.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
        });

        var data = new SongData(
            tempo,
            "",
            "",
            "",
            0,
            0,
            "",
            "",
            "",
            "",
            Song.SRI_CHINMOY,
            Song.SRI_CHINMOY,
            LyricsSource.LYRICIST,
            false,
            "",
            false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT,
            Song.DEFAULT_KEY_TYPE,
            0.0,
            0.0,
            List.of(lineWithNote),
            false,
            1,
            "", "", 0, 0
        );

        song.withoutMutationTracking(() -> song.loadFrom(data));

        var attachment = note.findAttachment(TempoChangeAttachment.class);
        assertThat(attachment).isNotNull();
        assertThat(attachment.getTempo()).isSameAs(tempo);
    }

    @Test
    void testLoadFromAnchorsInitialTempoOnLeadingGraceNote() {
        var tempo = new Tempo();
        tempo.setVisibleTempo(120);

        var line = new Line(song);
        var graceNote = new StaffElement(ElementType.GRACE_QUAVER);
        var hostNote = new StaffElement(ElementType.CROTCHET);
        song.withoutMutationTracking(() -> {
            line.addElement(graceNote);
            line.addElement(hostNote);
            line.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
        });

        var data = songDataWith(tempo, line);

        song.withoutMutationTracking(() -> song.loadFrom(data));

        var attachment = graceNote.findAttachment(TempoChangeAttachment.class);
        assertThat(attachment)
            .as("the initial tempo must anchor on the first element, a leading grace note included")
            .isNotNull();
        assertThat(attachment.getTempo()).isSameAs(tempo);
        assertThat(hostNote.findAttachment(TempoChangeAttachment.class))
            .as("the host note past the grace-note anchor must not carry the initial tempo")
            .isNull();
    }

    private SongData songDataWith(Tempo tempo, Line line) {
        return new SongData(
            tempo,
            "",
            "",
            "",
            0,
            0,
            "",
            "",
            "",
            "",
            Song.SRI_CHINMOY,
            Song.SRI_CHINMOY,
            LyricsSource.LYRICIST,
            false,
            "",
            false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT,
            Song.DEFAULT_KEY_TYPE,
            0.0,
            0.0,
            List.of(line),
            false,
            1,
            "", "", 0, 0
        );
    }

    // selection2.musicxml has no dynamics — regression guard for documents without them.
    private static final int SELECTION2_ELEMENT_COUNT = 19;

    @Test
    void testFixtureWithoutDynamicsLoadsWithoutError() throws Exception {
        var loaded = loadFixture("selection2");
        assertThat(loaded.getLine(0).elementCount())
            .as("selection2 fixture element count")
            .isEqualTo(SELECTION2_ELEMENT_COUNT);
    }

    /**
     * Asserts that all six text fields (title, subtitle, underlyrics, translation, footnotes,
     * per-note lyrics) load with typographic substitutes applied: straight quotes become curly,
     * "--" becomes an em dash. Also asserts the short-A asymmetry: underLyrics and per-note
     * lyrics have ă→a applied (stripShortA=true), while translation and footnotes do not.
     */
    @Test
    void testTypographicSubstitutionAppliedOnLoad() throws Exception {
        var xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <composition version="2.9">
              <keys>0</keys>
              <keytype>SHARPS</keytype>
              <tempo>
                <visibletempo>120</visibletempo>
                <tempotype>CROTCHET</tempotype>
                <tempodescription></tempodescription>
              </tempo>
              <songtitle>It's a "test" -- really</songtitle>
              <subtitle>A "sub" -- title</subtitle>
              <underlyrics>Don't băiat -- well</underlyrics>
              <translatedlyrics>It's "great" -- ăla</translatedlyrics>
              <footnotes>See "note 1" -- here</footnotes>
              <lyricssource>LYRICIST</lyricssource>
              <lines>
                <line>
                  <notes>
                    <note type="CROTCHET">
                      <staffposition>0</staffposition>
                      <lyric number="1">
                        <syllabic>single</syllabic>
                        <text>don't băiat</text>
                      </lyric>
                    </note>
                  </notes>
                </line>
              </lines>
              <view/>
            </composition>
            """;

        var loaded = parseXml(xml);

        // Title: straight quote and -- become typographic; ă is stripped (processText with true).
        assertThat(loaded.getTitle())
            .as("title")
            .isEqualTo("It’s a “test” — really");

        // Subtitle: same pipeline as title (normalizeTitle -> processText with true).
        assertThat(loaded.getSubtitle())
            .as("subtitle")
            .isEqualTo("A “sub” — title");

        // UnderLyrics: typographic + short-A (processText with true).
        assertThat(loaded.getUnderLyrics())
            .as("underlyrics")
            .isEqualTo("Don’t baiat — well");

        // Translation: typographic only, ă preserved (processText with false).
        assertThat(loaded.getTranslatedLyrics())
            .as("translatedlyrics — ă must be preserved")
            .isEqualTo("It’s “great” — ăla");

        // Footnotes: typographic only, ă preserved (processText with false).
        assertThat(loaded.getFootnotes())
            .as("footnotes — ă must be preserved")
            .isEqualTo("See “note 1” — here");

        // Per-note lyric: typographic + short-A (Lyric compact constructor, processText with true).
        var lyric = loaded.getLine(0).getElement(0).getLyricForVerse(Lyric.FIRST_VERSE);
        assertThat(lyric).isNotNull();

        assertThat(lyric.text())
            .as("per-note lyric — straight quote and -- become typographic; ă stripped")
            .isEqualTo("don’t baiat");
    }
}
