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

package songscribe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class FileUtilsTest extends UnitTest {

    @Nested
    class GetExtension {

        @Test
        void testGetExtensionWithPlainFilenameReturnsExtension() {
            assertThat(FileUtils.getExtension("song.mssw")).isEqualTo("mssw");
        }

        @Test
        void testGetExtensionWithPathReturnsExtension() {
            assertThat(FileUtils.getExtension("/home/user/song.mssw")).isEqualTo("mssw");
        }

        @Test
        void testGetExtensionWithNoExtensionReturnsEmpty() {
            assertThat(FileUtils.getExtension("song")).isEqualTo("");
        }

        @Test
        void testGetExtensionWithDotOnlyFilenameReturnsEmpty() {
            // "." has lastDot at index 0; substring(1) is ""
            assertThat(FileUtils.getExtension(".")).isEqualTo("");
        }
    }

    @Nested
    class GetPathWithoutExtension {

        @Test
        void testGetPathWithoutExtensionStripsLastDotAndBeyond() {
            assertThat(FileUtils.getPathWithoutExtension("song/file.mssw")).isEqualTo("song/file");
        }

        @Test
        void testGetPathWithoutExtensionWithNoDotReturnsWholePath() {
            assertThat(FileUtils.getPathWithoutExtension("song/file")).isEqualTo("song/file");
        }

        @Test
        void testGetPathWithoutExtensionWithMultipleDotsStripsLastDot() {
            assertThat(FileUtils.getPathWithoutExtension("song/file.backup.mssw"))
                .isEqualTo("song/file.backup");
        }
    }

    @Nested
    class GetFilename {

        @Test
        void testGetFilenameReturnsFilenameComponentFromPath() {
            assertThat(FileUtils.getFilename("/home/user/song.mssw")).isEqualTo("song.mssw");
        }

        @Test
        void testGetFilenameWithBareNameReturnsBareName() {
            assertThat(FileUtils.getFilename("song.mssw")).isEqualTo("song.mssw");
        }
    }

    @Nested
    class GetDirectory {

        @Test
        void testGetDirectoryReturnsParentPath() {
            assertThat(FileUtils.getDirectory("/home/user/song.mssw")).isEqualTo("/home/user");
        }

        @Test
        void testGetDirectoryWithBareFilenameReturnsEmpty() {
            assertThat(FileUtils.getDirectory("song.mssw")).isEqualTo("");
        }
    }

    @Nested
    class EnsureExtension {

        @Test
        void testEnsureExtensionReturnsSameFileWhenExtensionAlreadyPresent() {
            var file = new File("/home/user/song.mssw");
            assertThat(FileUtils.ensureExtension(file, "mssw")).isEqualTo(file);
        }

        @Test
        void testEnsureExtensionMatchesCaseInsensitively() {
            // file.getName().toLowerCase() is compared, so uppercase extension matches
            var file = new File("/home/user/song.MSSW");
            assertThat(FileUtils.ensureExtension(file, "mssw")).isEqualTo(file);
        }

        @Test
        void testEnsureExtensionAppendsFirstExtensionWhenMissing() {
            var file = new File("/home/user/song");
            assertThat(FileUtils.ensureExtension(file, "mssw").getName()).isEqualTo("song.mssw");
        }

        @Test
        void testEnsureExtensionWithDotPrefixedExtensionArgDoesNotDoubleDot() {
            // toDotExt(".mssw") returns ".mssw" unchanged — no double dot
            var file = new File("/home/user/song");
            assertThat(FileUtils.ensureExtension(file, ".mssw").getName()).isEqualTo("song.mssw");
        }

        @Test
        void testEnsureExtensionWithDotPrefixedArgMatchesExistingExtension() {
            // toDotExt(".mssw") = ".mssw"; "song.mssw".endsWith(".mssw") → same file
            var file = new File("/home/user/song.mssw");
            assertThat(FileUtils.ensureExtension(file, ".mssw")).isEqualTo(file);
        }

        @Test
        void testEnsureExtensionReturnsSameFileWhenMatchingSecondExtension() {
            var file = new File("/home/user/song.pdf");
            assertThat(FileUtils.ensureExtension(file, "mssw", "pdf")).isEqualTo(file);
        }
    }

    @Nested
    class GetDocumentsDirectory {

        @Test
        void testGetDocumentsDirectoryOnNonWindowsReturnsUserHomeDocuments() {
            // Non-Windows: File(user.home, "Documents")
            var expected = new File(System.getProperty("user.home"), "Documents");
            assertThat(FileUtils.getDocumentsDirectory()).isEqualTo(expected);
        }
    }
}
