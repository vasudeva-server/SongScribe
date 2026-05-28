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
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import songscribe.UnitTest;

class ExtensionFileFilterTest extends UnitTest {

    @Nested
    class Constructor {

        @Test
        void testDescriptionAppendsSingleExtensionInParens() {
            var filter = new ExtensionFileFilter("Songs", "mssw");
            assertThat(filter.getDescription()).isEqualTo("Songs (mssw)");
        }

        @Test
        void testDescriptionAppendsMultipleExtensionsCommaDelimited() {
            var filter = new ExtensionFileFilter("Files", "mssw", "pdf");
            assertThat(filter.getDescription()).isEqualTo("Files (mssw, pdf)");
        }
    }

    @Nested
    class AcceptFile {

        @Test
        void testAcceptDirectoryReturnsTrue(@TempDir Path tempDir) {
            var filter = new ExtensionFileFilter("Songs", "mssw");
            assertThat(filter.accept(tempDir.toFile())).isTrue();
        }

        @Test
        void testAcceptFileWithMatchingExtensionReturnsTrue() {
            var filter = new ExtensionFileFilter("Songs", "mssw");
            assertThat(filter.accept(new File("song.mssw"))).isTrue();
        }

        @Test
        void testAcceptFileWithUppercaseExtensionReturnsTrue() {
            // accept() lowercases the file's extension before comparison
            var filter = new ExtensionFileFilter("Songs", "mssw");
            assertThat(filter.accept(new File("song.MSSW"))).isTrue();
        }

        @Test
        void testAcceptFileWithNonMatchingExtensionReturnsFalse() {
            var filter = new ExtensionFileFilter("Songs", "mssw");
            assertThat(filter.accept(new File("song.pdf"))).isFalse();
        }

        @Test
        void testAcceptFileWithNoExtensionReturnsFalse() {
            var filter = new ExtensionFileFilter("Songs", "mssw");
            assertThat(filter.accept(new File("song"))).isFalse();
        }
    }
}
