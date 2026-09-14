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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * {@link StaffElement}'s invariant that {@link StaffElement#getLyrics()} holds at most one lyric
 * per verse, kept across every write a caller can make to it: {@link StaffElement#putLyric} and
 * {@link StaffElement#removeLyricForVerse}.
 */
class StaffElementLyricsTest extends UnitTest {

    private static final int VERSE_1 = 1;

    private static final int VERSE_2 = 2;

    @Test
    void testAtMostOneLyricPerVerseSurvivesPutAndRemoveSequence() {
        var element = crotchet();

        var verse1First = new Lyric(VERSE_1, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var verse2 = new Lyric(VERSE_2, "le", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var verse1Replacement =
            new Lyric(VERSE_1, "lo", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        element.putLyric(verse1First);
        assertThat(element.getLyricForVerse(VERSE_1))
            .as("verse 1 holds the lyric just put")
            .isEqualTo(verse1First);
        assertThat(element.getLyricForVerse(VERSE_2))
            .as("verse 2 has never been written")
            .isNull();
        assertThat(element.getLyrics()).extracting(Lyric::verse).doesNotHaveDuplicates();

        element.putLyric(verse2);
        assertThat(element.getLyricForVerse(VERSE_1))
            .as("verse 1 is unaffected by a put for a different verse")
            .isEqualTo(verse1First);
        assertThat(element.getLyricForVerse(VERSE_2))
            .as("verse 2 holds the lyric just put")
            .isEqualTo(verse2);
        assertThat(element.getLyrics()).extracting(Lyric::verse).doesNotHaveDuplicates();

        element.putLyric(verse1Replacement);
        assertThat(element.getLyricForVerse(VERSE_1))
            .as("a second put for verse 1 replaces the first rather than adding to it")
            .isEqualTo(verse1Replacement);
        assertThat(element.getLyricForVerse(VERSE_2))
            .as("verse 2 is unaffected by a replacement put for a different verse")
            .isEqualTo(verse2);
        assertThat(element.getLyrics()).extracting(Lyric::verse).doesNotHaveDuplicates();

        element.removeLyricForVerse(VERSE_1);
        assertThat(element.getLyricForVerse(VERSE_1))
            .as("the removed verse now holds no lyric")
            .isNull();
        assertThat(element.getLyricForVerse(VERSE_2))
            .as("verse 2 is unaffected by removing a different verse")
            .isEqualTo(verse2);
        assertThat(element.getLyrics()).extracting(Lyric::verse).doesNotHaveDuplicates();
    }
}
