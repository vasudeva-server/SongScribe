/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class MenuControllerTest extends UnitTest {

    // -------------------------------------------------------------------------
    // buildLabels — unique filenames
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsUniqueFilenames() {
        var paths = List.of(
            Path.of("/tmp/docs/alpha.mssw"),
            Path.of("/tmp/docs/beta.mssw"),
            Path.of("/tmp/docs/gamma.mssw")
        );

        var labels = MenuController.buildLabels(paths);

        assertThat(labels).containsExactly("alpha.mssw", "beta.mssw", "gamma.mssw");
    }

    // -------------------------------------------------------------------------
    // buildLabels — duplicate filenames (depth-1 parent disambiguates)
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsDuplicateFilenames() {
        // Two paths share the same filename but live in different parent dirs.
        // At depth=1, the parent directory names differ, so that suffix
        // is used as the disambiguator.
        var paths = List.of(
            Path.of("/tmp/project-a/song.mssw"),
            Path.of("/tmp/project-b/song.mssw")
        );

        var labels = MenuController.buildLabels(paths);

        assertThat(labels).containsExactly(
            "song.mssw — project-a",
            "song.mssw — project-b"
        );
    }

    // -------------------------------------------------------------------------
    // buildLabels — two-level disambiguation
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsTwoLevelDisambiguation() {
        // Both paths share filename and the same depth-1 parent name ("work"),
        // so depth-1 is not unique. Depth-2 produces unique suffixes "client-a/work"
        // vs "client-b/work".
        var paths = List.of(
            Path.of("/tmp/client-a/work/song.mssw"),
            Path.of("/tmp/client-b/work/song.mssw")
        );

        var labels = MenuController.buildLabels(paths);

        assertThat(labels).containsExactly(
            "song.mssw — client-a/work",
            "song.mssw — client-b/work"
        );
    }

    // -------------------------------------------------------------------------
    // buildLabels — fallback to full path when no depth resolves uniqueness
    // -------------------------------------------------------------------------

    @Test
    void testBuildLabelsFallbackToFullPath() {
        // Both paths are identical (same filename, same parent at every depth).
        // No depth level can distinguish them, so the fallback uses the full
        // path string. Since the paths are under the user's home directory,
        // the full path gets ~ substitution.
        var home = System.getProperty("user.home");
        var path = Path.of(home, "shared-folder", "song.mssw");
        var paths = List.of(path, path);

        var labels = MenuController.buildLabels(paths);

        // Both entries fall back to the tilde-substituted full path.
        assertThat(labels).containsExactly(
            "~/shared-folder/song.mssw",
            "~/shared-folder/song.mssw"
        );
    }

    // -------------------------------------------------------------------------
    // tildeSubstitute — path under home directory
    // -------------------------------------------------------------------------

    @Test
    void testTildeSubstituteUnderHome() {
        var home = Path.of("/home/testuser");
        var result = MenuController.tildeSubstitute("/home/testuser/docs/song.mssw", home);
        assertThat(result).isEqualTo("~/docs/song.mssw");
    }

    // -------------------------------------------------------------------------
    // tildeSubstitute — path outside home directory
    // -------------------------------------------------------------------------

    @Test
    void testTildeSubstituteOutsideHome() {
        var home = Path.of("/home/testuser");
        var result = MenuController.tildeSubstitute("/tmp/other/song.mssw", home);
        assertThat(result).isEqualTo("/tmp/other/song.mssw");
    }

    // -------------------------------------------------------------------------
    // tildeSubstitute — path exactly equal to home directory
    // -------------------------------------------------------------------------

    @Test
    void testTildeSubstituteExactlyHome() {
        var home = Path.of("/home/testuser");
        var result = MenuController.tildeSubstitute("/home/testuser", home);
        assertThat(result).isEqualTo("~");
    }
}
