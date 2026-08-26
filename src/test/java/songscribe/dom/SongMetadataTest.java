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

package songscribe.dom;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class SongMetadataTest extends UnitTest {

    private static final String LINE_FEED = "\n";
    private static final String CARRIAGE_RETURN = "\r";
    private static final String CRLF = "\r\n";

    /** A raw title and what {@link SongMetadata#normalizeTitle} promises to make of it. */
    private record TitleCase(String description, String raw, String expected) {}

    /**
     * Titles written with line feeds only. The line-ending cases below derive their
     * carriage-return variants from these, so a case added here is covered by both.
     */
    static Stream<TitleCase> titleCases() {
        return Stream.of(
            new TitleCase(
                "the first break is kept and every later one becomes a space",
                "One\nTwo\nThree\nFour",
                "One\nTwo Three Four"
            ),
            new TitleCase(
                "a blank line between two lines is dropped",
                "One\n\nTwo",
                "One\nTwo"
            ),
            new TitleCase(
                "a blank first line is dropped rather than spending the title's one break",
                "\nOne\nTwo",
                "One\nTwo"
            ),
            new TitleCase(
                "a trailing break leaves no empty second line",
                "One\n",
                "One"
            ),
            new TitleCase(
                "each line is trimmed and its space runs collapsed",
                "  One   word  \n  Two   words  ",
                "One word\nTwo words"
            ),
            new TitleCase(
                "a title of nothing but whitespace and breaks is empty",
                "  \n \t \n  ",
                ""
            ),
            new TitleCase(
                "a title with no break is left on one line",
                "One  word",
                "One word"
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("titleCases")
    void testNormalizeTitle(TitleCase testCase) {
        assertThat(SongMetadata.normalizeTitle(testCase.raw())).isEqualTo(testCase.expected());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("titleCases")
    void testNormalizeTitleIsIdempotent(TitleCase testCase) {
        var once = SongMetadata.normalizeTitle(testCase.raw());

        assertThat(SongMetadata.normalizeTitle(once)).isEqualTo(once);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("titleCases")
    void testNormalizeTitleReadsEveryLineEndingTheSameWay(TitleCase testCase) {
        var raw = testCase.raw();

        assertThat(SongMetadata.normalizeTitle(raw.replace(LINE_FEED, CRLF)))
            .isEqualTo(testCase.expected());
        assertThat(SongMetadata.normalizeTitle(raw.replace(LINE_FEED, CARRIAGE_RETURN)))
            .isEqualTo(testCase.expected());
    }

    /** A raw subtitle and the single line {@link SongMetadata#normalizeSubtitle} makes of it. */
    private record SubtitleCase(String description, String raw, String expected) {}

    static Stream<SubtitleCase> subtitleCases() {
        return Stream.of(
            new SubtitleCase("a line feed becomes a space", "One\nTwo", "One Two"),
            new SubtitleCase("a CRLF becomes a space", "One\r\nTwo", "One Two"),
            new SubtitleCase("a bare carriage return becomes a space", "One\rTwo", "One Two"),
            new SubtitleCase("several breaks collapse to one space", "  One \n\n Two  ", "One Two")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subtitleCases")
    void testNormalizeSubtitleYieldsOneLine(SubtitleCase testCase) {
        var normalized = SongMetadata.normalizeSubtitle(testCase.raw());

        assertThat(normalized).isEqualTo(testCase.expected());
        assertThat(normalized).doesNotContain(LINE_FEED, CARRIAGE_RETURN);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subtitleCases")
    void testNormalizeSubtitleIsIdempotent(SubtitleCase testCase) {
        var once = SongMetadata.normalizeSubtitle(testCase.raw());

        assertThat(SongMetadata.normalizeSubtitle(once)).isEqualTo(once);
    }

    /**
     * The title and the subtitle go through different normalizers, and the constructor is the
     * only place that pairing is stated. Both normalizers take a string and answer a string, so
     * nothing but this stops the two assignments being swapped or collapsed into one call —
     * which is what once let a subtitle keep a second line its own contract forbids.
     */
    @Test
    void testConstructorNormalizesTheTitleAndTheSubtitleDifferently() {
        var twoLines = "One" + LINE_FEED + "Two";

        var metadata = new SongMetadata(
            twoLines, "", "", "", 0, 0,
            "", "", Song.LyricsSource.LYRICIST, false, false,
            twoLines, "", 0, 0
        );

        assertThat(metadata.title()).isEqualTo(twoLines);
        assertThat(metadata.subtitle()).isEqualTo("One Two");
    }
}
