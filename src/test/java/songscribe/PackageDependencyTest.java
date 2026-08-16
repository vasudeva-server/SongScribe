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

package songscribe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageDependencyTest extends UnitTest {

    private static final Path SRC_ROOT = Paths.get("src/main/java");

    // Collect all import lines in .java files under a given source root-relative path
    // that match the forbidden prefix.
    private List<String> findForbiddenImports(String sourceDir, String forbiddenPrefix)
        throws IOException {
        var dir = SRC_ROOT.resolve(sourceDir);
        var violations = new ArrayList<String>();

        try (var walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try (var lines = Files.lines(file)) {
                        lines
                            .filter(line -> line.startsWith("import " + forbiddenPrefix))
                            .map(line -> file + ": " + line.strip())
                            .forEach(violations::add);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        return violations;
    }

    @Test
    void domMustNotImportLayout() throws IOException {
        var violations = findForbiddenImports("songscribe/dom", "songscribe.layout");
        assertThat(violations)
            .as("songscribe.dom must not import songscribe.layout")
            .isEmpty();
    }

    @Test
    void domMustNotImportUi() throws IOException {
        var violations = findForbiddenImports("songscribe/dom", "songscribe.ui");
        assertThat(violations)
            .as("songscribe.dom must not import songscribe.ui")
            .isEmpty();
    }

    @Test
    void layoutMustNotImportUi() throws IOException {
        var violations = findForbiddenImports("songscribe/layout", "songscribe.ui");
        assertThat(violations)
            .as("songscribe.layout must not import songscribe.ui")
            .isEmpty();
    }
}
