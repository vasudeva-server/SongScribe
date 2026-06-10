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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.font.FontKey;

/**
 * Unit tests for {@link AttributionFormatter#lines} and related methods.
 * <p>
 * All tested methods are {@code static}; no Song or Swing instances are needed
 * for the formatter tests.
 */
class AttributionPaneTest extends UnitTest {

    // -------------------------------------------------------------------------
    // §2.2 worked acceptance examples
    // -------------------------------------------------------------------------

    /**
     * Example 1: composer = lyricist = Sri Chinmoy, LYRICIST source, official translation.
     * All four credits share (Sri Chinmoy, " by "), collapsing to one line.
     */
    @Test
    void testExample1AllSriChinmoyWithTranslation() {
        var song = songWith(Song.SRI_CHINMOY, Song.SRI_CHINMOY, Song.LyricsSource.LYRICIST, false, "translation text");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of("Words, Music and Translation by Sri Chinmoy"));
    }

    /**
     * Example 2: same as example 1, with arrangement added.
     * All four credits (Words, Music, Arrangement, Translation) collapse to one line.
     */
    @Test
    void testExample2AllSriChinmoyWithArrangementAndTranslation() {
        var song = songWith(Song.SRI_CHINMOY, Song.SRI_CHINMOY, Song.LyricsSource.LYRICIST, true, "translation text");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of("Words, Music, Arrangement and Translation by Sri Chinmoy"));
    }

    /**
     * Example 3: lyricist = Sri Chinmoy, composer = "Traditional Folk Song", OTHER source.
     * Two groups: Words → Sri Chinmoy via " by "; Music → Traditional Folk Song via ": ".
     */
    @Test
    void testExample3LyricistSriChinmoyComposerOther() {
        var song = songWith(Song.SRI_CHINMOY, "Traditional Folk Song", Song.LyricsSource.OTHER, false, "");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of(
            "Words by Sri Chinmoy",
            "Music: Traditional Folk Song"
        ));
    }

    /**
     * Example 4: lyricist = "John Smith", composer = Sri Chinmoy, LYRICIST source,
     * translation, arrangement.
     * John Smith gets lyricsSource connector (" by " for LYRICIST), forming a separate group.
     * Sri Chinmoy gets Music, Arrangement, Translation → one line.
     */
    @Test
    void testExample4LyricistJohnSmithComposerSriChinmoy() {
        var song = songWith("John Smith", Song.SRI_CHINMOY, Song.LyricsSource.LYRICIST, true, "translation text");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of(
            "Words by John Smith",
            "Music, Arrangement and Translation by Sri Chinmoy"
        ));
    }

    /**
     * Example 5: lyricist = Sri Chinmoy, composer = "John Smith", TEXT source, translation.
     * Sri Chinmoy: Words + Translation → " by " group.
     * John Smith: Music → " from " (TEXT connector).
     */
    @Test
    void testExample5LyricistSriChinmoyComposerJohnSmithText() {
        var song = songWith(Song.SRI_CHINMOY, "John Smith", Song.LyricsSource.TEXT, false, "translation text");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of(
            "Words and Translation by Sri Chinmoy",
            "Music from John Smith"
        ));
    }

    // -------------------------------------------------------------------------
    // Grouping edge cases
    // -------------------------------------------------------------------------

    /**
     * Music and Words group together when they share the same (person, connector).
     * Default song (both = Sri Chinmoy, LYRICIST, no translation, no arrangement) →
     * one line: "Words and Music by Sri Chinmoy".
     */
    @Test
    void testDefaultSongGroupsWordsAndMusic() {
        var song = new Song();

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of("Words and Music by Sri Chinmoy"));
    }

    /**
     * A non–Sri Chinmoy lyricist with TEXT source must route the Words line
     * through the lyricsSource connector (" from "), proving the lyricist — not
     * only the composer — is passed through {@code connectorFor}. The LYRICIST
     * source ({@code " by "}) cannot distinguish this from the Sri Chinmoy path,
     * so TEXT is used here deliberately.
     */
    @Test
    void testLyricistNonSriChinmoyUsesLyricsSourceConnector() {
        var song = songWith("Rabindranath Tagore", Song.SRI_CHINMOY, Song.LyricsSource.TEXT, false, "");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of(
            "Words from Rabindranath Tagore",
            "Music by Sri Chinmoy"
        ));
    }

    /**
     * When lyricist and composer are different non–Sri Chinmoy people and the source is
     * TEXT, each gets its own group because they differ by person.
     * (Invariant: at least one must be Sri Chinmoy — here lyricist is Sri Chinmoy.)
     */
    @Test
    void testDifferentPersonsDifferentGroups() {
        var song = songWith(Song.SRI_CHINMOY, "Folk Melody", Song.LyricsSource.TEXT, false, "");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of(
            "Words by Sri Chinmoy",
            "Music from Folk Melody"
        ));
    }

    /**
     * When lyricist and composer are both Sri Chinmoy and no translation or arrangement
     * is present, the two credits collapse to a single two-item group.
     */
    @Test
    void testBothSriChinmoyNoExtrasCollapseToTwoItems() {
        var song = songWith(Song.SRI_CHINMOY, Song.SRI_CHINMOY, Song.LyricsSource.LYRICIST, false, "");

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of("Words and Music by Sri Chinmoy"));
    }

    /**
     * Unofficial translation is excluded from the attribution even when translatedLyrics
     * is non-empty (showTranslation returns false for unofficial translations).
     */
    @Test
    void testUnofficialTranslationExcludedFromCredits() {
        var song = new Song();
        song.setTranslatedLyrics("some translation");
        var current = song.getMetadata();
        song.setMetadata(new SongMetadata(
            current.title(), current.number(), current.place(), current.year(),
            current.month(), current.day(),
            current.composer(), current.lyricist(),
            current.lyricsSource(), current.arrangement(),
            true // unofficialTranslation
        ));

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertAttributionLines(lines, List.of("Words and Music by Sri Chinmoy"));
    }

    // -------------------------------------------------------------------------
    // Date / place sub-lines
    // -------------------------------------------------------------------------

    /**
     * Year only (month = 0, day = 0): produces the year string alone.
     */
    @Test
    void testDateYearOnly() {
        var lines = AttributionFormatter.lines(metadataWith("", 0, 0, "1984", ""), false);

        assertSubAttributionLines(lines, List.of("1984"));
    }

    /**
     * Month and year (day = 0): produces "MMMM, YYYY".
     */
    @Test
    void testDateMonthAndYear() {
        var lines = AttributionFormatter.lines(metadataWith("", 5, 0, "1984", ""), false);

        assertSubAttributionLines(lines, List.of("May, 1984"));
    }

    /**
     * Full date (month, day, year): produces "MMMM D, YYYY".
     */
    @Test
    void testDateFullDate() {
        var lines = AttributionFormatter.lines(metadataWith("", 5, 31, "1984", ""), false);

        assertSubAttributionLines(lines, List.of("May 31, 1984"));
    }

    /**
     * Place only (no date): produces a place sub-attribution line.
     */
    @Test
    void testDatePlaceOnly() {
        var lines = AttributionFormatter.lines(metadataWith("Jamaica, NY", 0, 0, "", ""), false);

        assertSubAttributionLines(lines, List.of("Jamaica, NY"));
    }

    /**
     * Both date and place: date line appears before place line.
     */
    @Test
    void testDateBothDateAndPlace() {
        var lines = AttributionFormatter.lines(metadataWith("Jamaica, NY", 5, 31, "1984", ""), false);

        assertSubAttributionLines(lines, List.of("May 31, 1984", "Jamaica, NY"));
    }

    /**
     * Neither date nor place: no sub-attribution lines produced.
     */
    @Test
    void testDateNeitherDateNorPlace() {
        var song = new Song();

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        assertSubAttributionLines(lines, List.of());
    }

    /**
     * Day alone (with no year) is ignored: sub-attribution is empty when year is empty.
     */
    @Test
    void testDateDayWithoutYearProducesNoDateLine() {
        var lines = AttributionFormatter.lines(metadataWith("", 0, 15, "", ""), false);

        assertSubAttributionLines(lines, List.of());
    }

    // -------------------------------------------------------------------------
    // attributionText() — multi-line and single-line variants
    // -------------------------------------------------------------------------

    /**
     * {@link AttributionFormatter#text} joins lines with {@code \n}.
     */
    @Test
    void testAttributionTextJoinsLinesWithNewline() {
        var song = songWith(Song.SRI_CHINMOY, "Traditional Folk Song", Song.LyricsSource.OTHER, false, "");

        var text = AttributionFormatter.text(song.getMetadata(), song.showTranslation());

        assertThat(text)
            .as("attributionText joins lines with newline")
            .isEqualTo("Words by Sri Chinmoy\nMusic: Traditional Folk Song");
    }

    /**
     * {@link AttributionFormatter#singleLineText} joins lines with a space.
     */
    @Test
    void testAttributionTextSingleLineJoinsWithSpace() {
        var song = songWith(Song.SRI_CHINMOY, "Traditional Folk Song", Song.LyricsSource.OTHER, false, "");

        var text = AttributionFormatter.singleLineText(song.getMetadata(), song.showTranslation());

        assertThat(text)
            .as("attributionTextSingleLine joins lines with space")
            .isEqualTo("Words by Sri Chinmoy Music: Traditional Folk Song");
    }

    // -------------------------------------------------------------------------
    // oxfordJoin — boundary cases
    // -------------------------------------------------------------------------

    /**
     * Empty list: returns an empty string (no items to join).
     */
    @Test
    void testOxfordJoinEmptyList() {
        assertThat(AttributionFormatter.oxfordJoin(List.of()))
            .as("empty list → empty string")
            .isEqualTo("");
    }

    /**
     * Single item: returned as-is (no connector word).
     */
    @Test
    void testOxfordJoinSingleItem() {
        assertThat(AttributionFormatter.oxfordJoin(List.of("Words")))
            .as("single item → no 'and'")
            .isEqualTo("Words");
    }

    /**
     * Two items: joined with " and " (no Oxford comma).
     */
    @Test
    void testOxfordJoinTwoItems() {
        assertThat(AttributionFormatter.oxfordJoin(List.of("Words", "Music")))
            .as("two items → 'A and B'")
            .isEqualTo("Words and Music");
    }

    /**
     * Three items: joined with commas and trailing " and Z".
     */
    @Test
    void testOxfordJoinThreeItems() {
        assertThat(AttributionFormatter.oxfordJoin(List.of("Words", "Music", "Translation")))
            .as("three items → 'A, B and C'")
            .isEqualTo("Words, Music and Translation");
    }

    /**
     * Four items: all intermediate items separated by commas, last preceded by " and ".
     */
    @Test
    void testOxfordJoinFourItems() {
        assertThat(AttributionFormatter.oxfordJoin(List.of("Words", "Music", "Arrangement", "Translation")))
            .as("four items → 'A, B, C and D'")
            .isEqualTo("Words, Music, Arrangement and Translation");
    }

    // -------------------------------------------------------------------------
    // Font key assignment
    // -------------------------------------------------------------------------

    /**
     * Attribution lines (roles + person) are tagged {@link FontKey#ATTRIBUTION}.
     */
    @Test
    void testAttributionLinesHaveAttributionFontKey() {
        var song = new Song();

        var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());

        var attributionLines = lines.stream()
            .filter(l -> l.font() == FontKey.ATTRIBUTION)
            .toList();

        assertThat(attributionLines)
            .as("at least one ATTRIBUTION line for a default song")
            .isNotEmpty();

        assertThat(attributionLines.get(0).text())
            .as("first ATTRIBUTION line contains 'Sri Chinmoy'")
            .contains("Sri Chinmoy");
    }

    /**
     * Date/place lines are tagged {@link FontKey#SUB_ATTRIBUTION}.
     */
    @Test
    void testSubAttributionLinesHaveSubAttributionFontKey() {
        var lines = AttributionFormatter.lines(metadataWith("Jamaica, NY", 0, 0, "1984", ""), false);

        var subLines = lines.stream()
            .filter(l -> l.font() == FontKey.SUB_ATTRIBUTION)
            .toList();

        assertThat(subLines)
            .as("two SUB_ATTRIBUTION lines: date and place")
            .hasSize(2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Constructs a song with the given attribution fields. Translation is enabled
     * by setting {@code translatedLyrics} to a non-empty string (and keeping
     * {@code unofficialTranslation = false}).
     */
    private static Song songWith(
        String lyricist,
        String composer,
        Song.LyricsSource source,
        boolean arrangement,
        String translatedLyrics
    ) {
        var song = new Song();
        var current = song.getMetadata();
        song.setMetadata(new SongMetadata(
            current.title(), current.number(), current.place(), current.year(),
            current.month(), current.day(),
            composer, lyricist, source, arrangement,
            current.unofficialTranslation()
        ));
        song.setTranslatedLyrics(translatedLyrics);
        return song;
    }

    /**
     * Constructs a {@link SongMetadata} for testing sub-attribution (date/place) lines.
     */
    private static SongMetadata metadataWith(String place, int month, int day, String year, String title) {
        return new SongMetadata(
            title, "", place, year, month, day,
            Song.SRI_CHINMOY, Song.SRI_CHINMOY,
            Song.LyricsSource.LYRICIST, false, false
        );
    }

    /**
     * Asserts that the attribution (ATTRIBUTION-keyed) lines in {@code lines} match
     * {@code expected} in order, by text only.
     */
    private static void assertAttributionLines(List<AttributionLine> lines, List<String> expected) {
        var actual = lines.stream()
            .filter(l -> l.font() == FontKey.ATTRIBUTION)
            .map(AttributionLine::text)
            .toList();

        assertThat(actual)
            .as("ATTRIBUTION lines")
            .isEqualTo(expected);
    }

    /**
     * Asserts that the sub-attribution (SUB_ATTRIBUTION-keyed) lines match
     * {@code expected} in order, by text only.
     */
    private static void assertSubAttributionLines(List<AttributionLine> lines, List<String> expected) {
        var actual = lines.stream()
            .filter(l -> l.font() == FontKey.SUB_ATTRIBUTION)
            .map(AttributionLine::text)
            .toList();

        assertThat(actual)
            .as("SUB_ATTRIBUTION lines")
            .isEqualTo(expected);
    }
}
