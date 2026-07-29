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
package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link Song#getLyricsText()}.
 * Each test covers one branch of the syllabification logic.
 */
class SongLyricsTextTest extends UnitTest {

    /** A second language for the same notes, used to prove the text follows the showing verse. */
    private static final int SECOND_VERSE = Lyric.FIRST_VERSE + 1;

    // Helper: add a note with a lyric to a line.
    private static StaffElement noteWithLyric(
        Lyric.Syllabic syllabic, boolean compound, String text, Lyric.Extend extend
    ) {
        var note = new StaffElement(ElementType.CROTCHET);
        note.setLyricForVerse(Lyric.FIRST_VERSE, syllabic, compound, text, extend);
        return note;
    }

    @Test
    void testNoLyricsReturnsEmptyString() {
        var song = new Song();
        assertThat(song.getLyricsText()).isEmpty();
    }

    // extend != NONE → appends '_'
    @Test
    void testExtendNonNoneAppendsUnderscore() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(noteWithLyric(Lyric.Syllabic.SINGLE, false, "hel", Lyric.Extend.START));
        });

        assertThat(song.getLyricsText()).isEqualTo("hel_");
    }

    // compound=true, extend=NONE → appends '--'
    @Test
    void testCompoundAppendsDoubleDash() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(noteWithLyric(Lyric.Syllabic.BEGIN, true, "com", Lyric.Extend.NONE));
        });

        assertThat(song.getLyricsText()).isEqualTo("com--");
    }

    // syllabic=BEGIN, extend=NONE, compound=false → appends '-'
    @Test
    void testBeginSyllabicAppendsDash() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(noteWithLyric(Lyric.Syllabic.BEGIN, false, "bro", Lyric.Extend.NONE));
        });

        assertThat(song.getLyricsText()).isEqualTo("bro-");
    }

    // syllabic=MIDDLE, extend=NONE, compound=false → appends '-'
    @Test
    void testMiddleSyllabicAppendsDash() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(noteWithLyric(Lyric.Syllabic.MIDDLE, false, "ther", Lyric.Extend.NONE));
        });

        assertThat(song.getLyricsText()).isEqualTo("ther-");
    }

    // syllabic=SINGLE, extend=NONE, compound=false → appends ' '
    @Test
    void testSingleSyllabicAppendsSpace() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(noteWithLyric(Lyric.Syllabic.SINGLE, false, "joy", Lyric.Extend.NONE));
        });

        assertThat(song.getLyricsText()).isEqualTo("joy ");
    }

    // syllabic=END, extend=NONE, compound=false → appends ' '
    @Test
    void testEndSyllabicAppendsSpace() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(noteWithLyric(Lyric.Syllabic.END, false, "hood", Lyric.Extend.NONE));
        });

        assertThat(song.getLyricsText()).isEqualTo("hood ");
    }

    // Two lines: between non-last and last lines a '\n' is appended when text is present.
    @Test
    void testMultipleLinesJoinedWithNewline() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            line0.addElement(noteWithLyric(Lyric.Syllabic.SINGLE, false, "one", Lyric.Extend.NONE));

            var line1 = new Line(song);
            line1.addElement(noteWithLyric(Lyric.Syllabic.SINGLE, false, "two", Lyric.Extend.NONE));
            song.addLine(line1);
        });

        assertThat(song.getLyricsText()).isEqualTo("one \ntwo ");
    }

    // Notes without a lyric are skipped; only notes with a lyric contribute.
    @Test
    void testNotesWithoutLyricsAreSkipped() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(new StaffElement(ElementType.CROTCHET));
            line.addElement(noteWithLyric(Lyric.Syllabic.SINGLE, false, "yes", Lyric.Extend.NONE));
        });

        assertThat(song.getLyricsText()).isEqualTo("yes ");
    }

    // A song's verses are the languages its lyrics are written in, and it shows one at a time, so
    // the assembled text is the showing language's — not always the first one's.
    @Test
    void testTextComesFromTheActiveVerseNotAlwaysTheFirst() {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            var note = new StaffElement(ElementType.CROTCHET);
            note.setLyricForVerse(
                Lyric.FIRST_VERSE, Lyric.Syllabic.SINGLE, false, "heart", Lyric.Extend.NONE);
            note.setLyricForVerse(
                SECOND_VERSE, Lyric.Syllabic.SINGLE, false, "coeur", Lyric.Extend.NONE);
            song.getLine(0).addElement(note);
        });

        assertThat(song.getLyricsText())
            .as("a song opens on its first verse")
            .isEqualTo("heart ");

        song.setActiveVerse(SECOND_VERSE);

        assertThat(song.getLyricsText())
            .as("selecting the second language must yield that language's words")
            .isEqualTo("coeur ");
    }

    // Selecting a verse the song carries no lyrics in leaves nothing to assemble, rather than
    // falling back to another verse's words.
    @Test
    void testAVerseWithNoLyricsYieldsEmptyText() {
        var song = new Song();
        song.withoutMutationTracking(() ->
            song.getLine(0).addElement(
                noteWithLyric(Lyric.Syllabic.SINGLE, false, "heart", Lyric.Extend.NONE)));

        song.setActiveVerse(SECOND_VERSE);

        assertThat(song.getLyricsText()).isEmpty();
    }
}
