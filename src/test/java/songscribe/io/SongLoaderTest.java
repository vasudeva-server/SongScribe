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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SongLoader#load}: a {@code .mssw} document that is not a song is a parse error, not a
 * crash and not an empty song.
 */
class SongLoaderTest extends UnitTest {

    /**
     * A document that is well-formed XML and is not a song.
     *
     * @param description the case, as the test's display name
     * @param document    the document's text
     */
    private record NotASongCase(String description, String document) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("notASongCases")
    void testADocumentWithNoSongOrNoVersionIsAParseError(NotASongCase testCase, @TempDir Path directory)
        throws IOException {
        var file = directory.resolve("not-a-song.mssw");
        Files.writeString(file, testCase.document());

        assertThat(SongLoader.load(file.toFile())).isInstanceOf(SongLoadResult.ParseError.class);
    }

    static Stream<NotASongCase> notASongCases() {
        return Stream.of(
            new NotASongCase("a document with no song element", "<?xml version=\"1.0\"?><other/>"),
            new NotASongCase("a song element with no version attribute", "<?xml version=\"1.0\"?><song/>"));
    }
}
