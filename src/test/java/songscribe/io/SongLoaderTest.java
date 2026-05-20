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

class SongLoaderTest extends UnitTest {

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

    @Test
    void testLoadValidFileReturnsSuccess() throws Exception {
        var result = SongLoader.load(fixtureFile("full-line"));
        assertThat(result).isInstanceOf(SongLoadResult.Success.class);
        var success = (SongLoadResult.Success) result;
        assertThat(success.song()).isNotNull();
        assertThat(success.fonts()).isNotNull();
    }
}
