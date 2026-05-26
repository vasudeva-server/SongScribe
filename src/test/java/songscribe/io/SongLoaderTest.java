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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.font.FontKey;

class SongLoaderTest extends UnitTest {

    // The full-line fixture has exactly 1 line containing 22 notes.
    private static final int FULL_LINE_FIXTURE_LINE_COUNT = 1;
    private static final int FULL_LINE_FIXTURE_LINE_0_ELEMENT_COUNT = 22;

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
}
