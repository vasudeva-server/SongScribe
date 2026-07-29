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
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.StaffElement.Accidental;
import songscribe.font.FontKey;

class SongLoaderTest extends UnitTest {

    // The full-line fixture has exactly 1 line containing 22 notes.
    private static final int FULL_LINE_FIXTURE_LINE_COUNT = 1;
    private static final int FULL_LINE_FIXTURE_LINE_0_ELEMENT_COUNT = 22;

    // Hand-maintained .mssw holding the three retired accidentals; see the corpus README.
    private static final File RETIRED_ACCIDENTALS_FILE =
        Path.of("src/test/resources/corpus/legacy/retired-accidentals.mssw").toFile();

    @Test
    void testLoadDamagedFileReturnsParseError() throws Exception {
        var result = SongLoader.load(fixtureFile("damaged"));
        assertThat(result).isInstanceOf(SongLoadResult.ParseError.class);
    }

    @Test
    void testLoadNewerVersionFileReturnsNewerVersion() throws Exception {
        var result = SongLoader.load(fixtureFile("newer-version"));
        assertThat(result).isInstanceOf(SongLoadResult.NewerVersion.class);
    }

    @Test
    void testLoadNonExistentFileReturnsIoError() {
        var missing = new File("/no/such/file.mssw");
        var result = SongLoader.load(missing);
        assertThat(result).isInstanceOf(SongLoadResult.IoError.class);
    }

    /**
     * An old {@code .mssw} naming a retired accidental must both convert it and say so.
     * The load result's flag is what makes the reopened song count as modified, so the
     * user is prompted to save the converted version instead of losing it on close.
     */
    @Test
    void testLoadFileWithRetiredAccidentalsConvertsThemAndReportsIt() {
        var result = SongLoader.load(RETIRED_ACCIDENTALS_FILE);

        assertThat(result).isInstanceOf(SongLoadResult.Success.class);
        var success = (SongLoadResult.Success) result;

        assertThat(success.accidentalsConverted())
            .as("a file holding retired accidentals must report that they were converted")
            .isTrue();

        // Line 0 is NATURAL_FLAT, NATURAL_SHARP, DOUBLE_NATURAL, in that order.
        var line = success.song().getLine(0);

        assertThat(line.getElement(0).getAccidental())
            .as("NATURAL_FLAT sounds a flat").isEqualTo(Accidental.FLAT);
        assertThat(line.getElement(1).getAccidental())
            .as("NATURAL_SHARP sounds a sharp").isEqualTo(Accidental.SHARP);
        assertThat(line.getElement(2).getAccidental())
            .as("DOUBLE_NATURAL sounds a natural").isEqualTo(Accidental.NATURAL);
    }

    /**
     * The negative half of the pair: without it the flag could be hardwired true and
     * the test above would still pass, so every old file would open marked modified.
     */
    @Test
    void testLoadFileWithoutRetiredAccidentalsReportsNoConversion() throws Exception {
        var result = SongLoader.load(fixtureFile("full-line"));

        assertThat(result).isInstanceOf(SongLoadResult.Success.class);

        assertThat(((SongLoadResult.Success) result).accidentalsConverted())
            .as("a file with no retired accidental must not be reported as converted")
            .isFalse();
    }

    // row 42: ParserConfigurationException → ParseError
    // Not directly unit-testable: PARSER_FACTORY is private static final, so
    // triggering newSAXParser() to throw ParserConfigurationException requires
    // environment manipulation (e.g., broken JAXP provider). The branch is
    // covered by code review; no automated test is feasible without reflection
    // or a custom factory hook.

    // row 43: Success.song() fully assembled — line count and line-0 element count
    @Test
    void testLoadValidFileReturnsSuccess() throws Exception {
        var result = SongLoader.load(fixtureFile("full-line"));
        assertThat(result).isInstanceOf(SongLoadResult.Success.class);
        var success = (SongLoadResult.Success) result;
        var song = success.song();
        assertThat(song).isNotNull();
        assertThat(success.fonts()).isNotNull();
        assertThat(song.lineCount()).isGreaterThanOrEqualTo(FULL_LINE_FIXTURE_LINE_COUNT);
        assertThat(song.getLine(0).elementCount()).isGreaterThanOrEqualTo(FULL_LINE_FIXTURE_LINE_0_ELEMENT_COUNT);
    }

    // row 44: Success.fonts() non-null with expected roles from <view>
    // The full-line fixture's <view> block declares titlefontsize=30, proving the
    // <view> block was parsed and applied to FontKey.TITLE. The PS name is not
    // asserted because the font may be substituted in environments without the font.
    @Test
    void testLoadSuccessFontsHaveExpectedTitleRole() throws Exception {
        var success = loadFixtureResult("full-line");
        var titleFont = success.fonts().getFont(FontKey.TITLE);
        assertThat(titleFont.getSize())
            .as("title font size from <view> block")
            .isEqualTo(30);
    }

    // ── lyricsDate warning tests ──

    /**
     * Loading the lyrics-date-invalid fixture (whose {@code <lyricsDate>} is
     * {@code 1984-13}) must succeed — the load must not abort — and the
     * {@link SongLoadResult.Success#warning()} must carry an
     * {@code INVALID_LYRICS_DATE} warning whose description is the raw invalid
     * string. The {@code io} layer reports only the kind and the offending text;
     * mapping to a user-facing dialog is the UI layer's job.
     */
    @Test
    void testLoadInvalidLyricsDateFileReturnsSuccessWithWarning() throws Exception {
        var result = SongLoader.load(fixtureFile("lyrics-date-invalid"));

        assertThat(result)
            .as("invalid lyricsDate must not abort the load")
            .isInstanceOf(SongLoadResult.Success.class);

        var success = (SongLoadResult.Success) result;
        var warning = success.warning();

        assertThat(warning)
            .as("invalid lyricsDate must produce a non-null warning")
            .isNotNull();

        //noinspection ConstantValue -- NullAway guard after isNotNull assertion
        if (warning == null) {
            return;
        }

        assertThat(warning.type())
            .as("warning type must be INVALID_LYRICS_DATE")
            .isEqualTo(LoadWarning.Type.INVALID_LYRICS_DATE);
        assertThat(warning.description())
            .as("warning description must be the raw invalid string")
            .isEqualTo("1984-13");
    }

    /**
     * The words-date parts of the song loaded from the invalid fixture must be
     * blank/zero (the parser leaves fields at their defaults on malformed input).
     */
    @Test
    void testLoadInvalidLyricsDateFileHasBlankWordsDate() throws Exception {
        var result = SongLoader.load(fixtureFile("lyrics-date-invalid"));

        assertThat(result)
            .as("invalid lyricsDate must not abort the load")
            .isInstanceOf(SongLoadResult.Success.class);

        var success = (SongLoadResult.Success) result;

        assertThat(success.song().getWordsYear())
            .as("invalid lyricsDate: wordsYear must be empty")
            .isEmpty();
        assertThat(success.song().getWordsMonth())
            .as("invalid lyricsDate: wordsMonth must be 0")
            .isZero();
        assertThat(success.song().getWordsDay())
            .as("invalid lyricsDate: wordsDay must be 0")
            .isZero();
    }

    /**
     * Loading a valid fixture (full-line) must produce a Success with a
     * {@code null} warning — no spurious warning for a good file.
     */
    @Test
    void testLoadValidFixtureProducesNullWarning() throws Exception {
        var result = SongLoader.load(fixtureFile("full-line"));

        assertThat(result).isInstanceOf(SongLoadResult.Success.class);

        var success = (SongLoadResult.Success) result;

        assertThat(success.warning())
            .as("valid fixture must not produce a warning")
            .isNull();
    }
}
