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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class SongAttributionTest extends UnitTest {

    private record RawTextCase(String description, String raw) {}

    static Stream<RawTextCase> rawTextCases() {
        return Stream.of(
            new RawTextCase("a plain name", "Sri Chinmoy"),
            new RawTextCase("padding and a run of spaces", "  Sri   Chinmoy  "),
            new RawTextCase("a blank line between two names", "First\n\nSecond"),
            new RawTextCase("a line of spaces between two names", "First\n \nSecond"),
            new RawTextCase("a space before a blank line", "First \n\nSecond"),
            new RawTextCase("straight quotes, a double hyphen and dots", "\"Name\" -- it's ..."),
            new RawTextCase("a short A", "Brahmă"),
            new RawTextCase("empty text", ""),
            new RawTextCase("text of only whitespace", " \n\t ")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rawTextCases")
    void testNormalizePersonIsIdempotent(RawTextCase testCase) {
        var once = SongAttribution.normalizePerson(testCase.raw());

        assertThat(SongAttribution.normalizePerson(once)).isEqualTo(once);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rawTextCases")
    void testNormalizePlaceIsIdempotent(RawTextCase testCase) {
        var once = SongAttribution.normalizePlace(testCase.raw());

        assertThat(SongAttribution.normalizePlace(once)).isEqualTo(once);
    }

    private record NormalizePersonCase(String description, String raw, String expected) {}

    static Stream<NormalizePersonCase> normalizePersonCases() {
        return Stream.of(
            new NormalizePersonCase("a blank line collapses to one line break", "a\n\nb", "a\nb"),
            new NormalizePersonCase("a line of spaces collapses to one line break", "a\n \nb", "a\nb"),
            new NormalizePersonCase("line breaks around the name are trimmed", "\na\n", "a"),
            new NormalizePersonCase("text of only whitespace credits Sri Chinmoy", "  ", Song.SRI_CHINMOY)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalizePersonCases")
    void testNormalizePersonCollapsesBlankLinesAndCreditsSriChinmoyWhenEmpty(NormalizePersonCase testCase) {
        assertThat(SongAttribution.normalizePerson(testCase.raw())).isEqualTo(testCase.expected());
    }

    private record ResolveLyricistCase(String description, String lyricist, String composer, String expected) {}

    static Stream<ResolveLyricistCase> resolveLyricistCases() {
        return Stream.of(
            new ResolveLyricistCase(
                "an empty lyricist takes the normalized composer", "", "  Composer   Name ", "Composer Name"),
            new ResolveLyricistCase(
                "a lyricist of only whitespace takes the normalized composer", " \n\t ", "Composer", "Composer"),
            new ResolveLyricistCase(
                "an empty lyricist and an empty composer credit Sri Chinmoy", "", "", Song.SRI_CHINMOY),
            new ResolveLyricistCase(
                "a lyricist with content keeps its own normalized name",
                "  Lyric   Writer ",
                "Composer",
                "Lyric Writer")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolveLyricistCases")
    void testResolveLyricistCreditsTheComposerOnlyWhenTheLyricistIsBlank(ResolveLyricistCase testCase) {
        assertThat(SongAttribution.resolveLyricist(testCase.lyricist(), testCase.composer()))
            .isEqualTo(testCase.expected());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolveLyricistCases")
    void testResolveLyricistIsIdempotentInTheLyricist(ResolveLyricistCase testCase) {
        var composer = testCase.composer();
        var once = SongAttribution.resolveLyricist(testCase.lyricist(), composer);

        assertThat(SongAttribution.resolveLyricist(once, composer)).isEqualTo(once);
    }
}
