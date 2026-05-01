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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;

class LegacyLyricsImporterTest extends UnitTest {

    private Song song;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    @Test
    void testDoReMiProducesThreeSyllables() {
        var line = lineWithNotes(3);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "do-re-mi");

        assertLyric(line, 0, Lyric.Syllabic.BEGIN, false, "do", false);
        assertLyric(line, 1, Lyric.Syllabic.MIDDLE, false, "re", false);
        assertLyric(line, 2, Lyric.Syllabic.END, false, "mi", false);
    }

    @Test
    void testExtenderWithSpaceSeparatedUnderscoresAcrossFourNotes() {
        var line = lineWithNotes(4);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "heart_ _ _garden");

        assertLyric(line, 0, Lyric.Syllabic.SINGLE, false, "heart", true);
        assertNoLyric(line, 1);
        assertNoLyric(line, 2);
        assertLyric(line, 3, Lyric.Syllabic.SINGLE, false, "garden", false);
    }

    @Test
    void testEqualsProducesCompoundWord() {
        var line = lineWithNotes(2);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "heart=garden");

        assertLyric(line, 0, Lyric.Syllabic.BEGIN, true, "heart", false);
        assertLyric(line, 1, Lyric.Syllabic.END, false, "garden", false);
    }

    @Test
    void testDoubleHyphenProducesCompoundWord() {
        var line = lineWithNotes(2);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "heart--garden");

        assertLyric(line, 0, Lyric.Syllabic.BEGIN, true, "heart", false);
        assertLyric(line, 1, Lyric.Syllabic.END, false, "garden", false);
    }

    @Test
    void testDoubleHyphenCompoundOnMiddleSyllable() {
        var line = lineWithNotes(3);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "a-b--c");

        assertLyric(line, 0, Lyric.Syllabic.BEGIN, false, "a", false);
        assertLyric(line, 1, Lyric.Syllabic.MIDDLE, true, "b", false);
        assertLyric(line, 2, Lyric.Syllabic.END, false, "c", false);
    }

    @Test
    void testFullCombinedExample() {
        var line = lineWithNotes(8);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "heart___=gar__-den");

        assertLyric(line, 0, Lyric.Syllabic.BEGIN, true, "heart", true);
        assertNoLyric(line, 1);
        assertNoLyric(line, 2);
        assertNoLyric(line, 3);
        assertLyric(line, 4, Lyric.Syllabic.MIDDLE, false, "gar", true);
        assertNoLyric(line, 5);
        assertNoLyric(line, 6);
        assertLyric(line, 7, Lyric.Syllabic.END, false, "den", false);
    }

    @Test
    void testMidWordLineContinuationPrefix() {
        var lineA = lineWithNotes(2);
        var lineB = lineWithNotes(2);

        LegacyLyricsImporter.importLegacyLyrics(List.of(lineA, lineB), "ele-\n--phant is-big");

        assertLyric(lineA, 0, Lyric.Syllabic.BEGIN, false, "ele", false);
        assertNoLyric(lineA, 1);

        // Leading "--" on line B is stripped; the first token "phant" lands on
        // line B's first note as a standalone syllable.
        assertLyric(lineB, 0, Lyric.Syllabic.SINGLE, false, "phant", false);
        assertLyric(lineB, 1, Lyric.Syllabic.BEGIN, false, "is", false);

        // Cross-line continuation is expressed by line A's last Lyric syllabic.
        assertThat(lineA.getElement(0).getMainLyric())
            .isNotNull()
            .extracting(Lyric::syllabic)
            .isEqualTo(Lyric.Syllabic.BEGIN);
    }

    @Test
    void testMultiLineDoesNotOverrunShorterLines() {
        var lineA = lineWithNotes(3);
        var lineB = lineWithNotes(2);

        LegacyLyricsImporter.importLegacyLyrics(List.of(lineA, lineB), "do-re-mi\nfa-sol");

        assertLyric(lineA, 0, Lyric.Syllabic.BEGIN, false, "do", false);
        assertLyric(lineA, 1, Lyric.Syllabic.MIDDLE, false, "re", false);
        assertLyric(lineA, 2, Lyric.Syllabic.END, false, "mi", false);

        assertLyric(lineB, 0, Lyric.Syllabic.BEGIN, false, "fa", false);
        assertLyric(lineB, 1, Lyric.Syllabic.END, false, "sol", false);
    }

    @Test
    void testEmptyBlobEmitsNothing() {
        var line = lineWithNotes(3);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "");

        assertNoLyric(line, 0);
        assertNoLyric(line, 1);
        assertNoLyric(line, 2);
    }

    @Test
    void testBlankBlobEmitsNothing() {
        var line = lineWithNotes(3);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "   \n   \n");

        assertNoLyric(line, 0);
        assertNoLyric(line, 1);
        assertNoLyric(line, 2);
    }

    @Test
    void testTrailingWordsBeyondElementCountAreDropped() {
        var line = lineWithNotes(2);

        LegacyLyricsImporter.importLegacyLyrics(List.of(line), "one two three four");

        assertLyric(line, 0, Lyric.Syllabic.SINGLE, false, "one", false);
        assertLyric(line, 1, Lyric.Syllabic.SINGLE, false, "two", false);
    }

    private Line lineWithNotes(int count) {
        var line = new Line();
        line.setSong(song);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < count; i++) {
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        });

        return line;
    }

    private static void assertLyric(
        Line line,
        int index,
        Lyric.Syllabic expectedSyllabic,
        boolean expectedCompound,
        String expectedText,
        boolean expectedExtend
    ) {
        assertThat(line.getElement(index).properties.lyrics)
            .as("expected Lyric on element %d", index)
            .containsExactly(new Lyric(1, expectedText,
                expectedExtend ? Lyric.Extend.START : Lyric.Extend.NONE,
                expectedSyllabic, expectedCompound));
    }

    private static void assertNoLyric(Line line, int index) {
        assertThat(line.getElement(index).properties.lyrics)
            .as("expected no Lyric on element %d", index)
            .isEmpty();
    }
}
