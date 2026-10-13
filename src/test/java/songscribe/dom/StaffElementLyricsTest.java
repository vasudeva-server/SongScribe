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
 * per script, kept across every write a caller can make to it: {@link StaffElement#putLyric} and
 * {@link StaffElement#removeLyricForScript}.
 */
class StaffElementLyricsTest extends UnitTest {

    @Test
    void testAtMostOneLyricPerScriptSurvivesPutAndRemoveSequence() {
        var element = crotchet();

        var script1First = new Lyric(Song.Script.LATIN, "la", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var script2 = new Lyric(Song.Script.BENGALI, "le", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
        var script1Replacement =
            new Lyric(Song.Script.LATIN, "lo", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);

        element.putLyric(script1First);
        assertThat(element.getLyricForScript(Song.Script.LATIN))
            .as("the Latin script holds the lyric just put")
            .isEqualTo(script1First);
        assertThat(element.getLyricForScript(Song.Script.BENGALI))
            .as("the Bengali script has never been written")
            .isNull();
        assertThat(element.getLyrics()).extracting(Lyric::script).doesNotHaveDuplicates();

        element.putLyric(script2);
        assertThat(element.getLyricForScript(Song.Script.LATIN))
            .as("the Latin script is unaffected by a put for a different script")
            .isEqualTo(script1First);
        assertThat(element.getLyricForScript(Song.Script.BENGALI))
            .as("the Bengali script holds the lyric just put")
            .isEqualTo(script2);
        assertThat(element.getLyrics()).extracting(Lyric::script).doesNotHaveDuplicates();

        element.putLyric(script1Replacement);
        assertThat(element.getLyricForScript(Song.Script.LATIN))
            .as("a second put for the Latin script replaces the first rather than adding to it")
            .isEqualTo(script1Replacement);
        assertThat(element.getLyricForScript(Song.Script.BENGALI))
            .as("the Bengali script is unaffected by a replacement put for a different script")
            .isEqualTo(script2);
        assertThat(element.getLyrics()).extracting(Lyric::script).doesNotHaveDuplicates();

        element.removeLyricForScript(Song.Script.LATIN);
        assertThat(element.getLyricForScript(Song.Script.LATIN))
            .as("the removed script now holds no lyric")
            .isNull();
        assertThat(element.getLyricForScript(Song.Script.BENGALI))
            .as("the Bengali script is unaffected by removing a different script")
            .isEqualTo(script2);
        assertThat(element.getLyrics()).extracting(Lyric::script).doesNotHaveDuplicates();
    }
}
