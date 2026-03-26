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

package songscribe.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import songscribe.UnitTest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceScanTest extends UnitTest {

    private static final Path ICONS_DIR = Path.of("src/main/resources/icons");

    @Nested
    class SvgIcons {

        private static final Pattern HARDCODED_BLACK_FILL = Pattern.compile(
            "fill\\s*=\\s*\"(#000|#000000|black)\""
        );
        private static final Pattern HARDCODED_BLACK_STROKE = Pattern.compile(
            "stroke\\s*=\\s*\"(#000|#000000|black)\""
        );

        @Test
        void testNoSvgContainsHardcodedBlackFill() throws IOException {
            var violations = findViolations(HARDCODED_BLACK_FILL);

            assertThat(violations)
                .as("SVG files with hardcoded black fill attributes")
                .isEmpty();
        }

        @Test
        void testNoSvgContainsHardcodedBlackStroke() throws IOException {
            var violations = findViolations(HARDCODED_BLACK_STROKE);

            assertThat(violations)
                .as("SVG files with hardcoded black stroke attributes")
                .isEmpty();
        }

        private List<String> findViolations(Pattern pattern) throws IOException {
            var violations = new ArrayList<String>();

            try (Stream<Path> paths = Files.list(ICONS_DIR)) {
                var svgFiles = paths
                    .filter(p -> p.toString().endsWith(".svg"))
                    .toList();

                for (var svg : svgFiles) {
                    var content = Files.readString(svg);

                    if (pattern.matcher(content).find()) {
                        violations.add(svg.getFileName().toString());
                    }
                }
            }

            return violations;
        }
    }
}
