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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class StaffElementTest extends UnitTest {

    // T7: setLyricForVerse(1, ...) replaces existing verse-1 entry
    @Test
    void testSetLyricForVerseReplacesExisting() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "old", Lyric.Extend.NONE);

        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "new", Lyric.Extend.NONE);

        assertThat(element.getLyrics()).hasSize(1);
        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text")
            .isEqualTo("new");
    }

    // T8: setLyricForVerse(1, NONE, "", NONE) removes verse-1 entry
    @Test
    void testSetLyricForVerseEmptyTextRemoves() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "text", Lyric.Extend.NONE);

        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "", Lyric.Extend.NONE);

        assertThat(element.getLyricForVerse(1)).isNull();
        assertThat(element.getLyrics()).isEmpty();
    }

    // T9: setLyricForVerse(2, ...) on element with verse-1 only adds verse-2 without disturbing verse-1
    @Test
    void testSetLyricForVerseAddsWithoutDisturbing() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "verse1", Lyric.Extend.NONE);

        element.setLyricForVerse(2, StaffElement.SyllableRelation.SYLLABLE, "verse2", Lyric.Extend.NONE);

        assertThat(element.getLyrics()).hasSize(2);
        assertThat(element.getLyricForVerse(1)).isNotNull()
            .extracting("text")
            .isEqualTo("verse1");
        assertThat(element.getLyricForVerse(2)).isNotNull()
            .extracting("text")
            .isEqualTo("verse2");
    }

    // Edge case: null text also removes
    @Test
    void testSetLyricForVerseNullTextRemoves() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "text", Lyric.Extend.NONE);

        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, null, Lyric.Extend.NONE);

        assertThat(element.getLyricForVerse(1)).isNull();
    }

    // Edge case: blank text with whitespace also removes
    @Test
    void testSetLyricForVerseBlankTextRemoves() {
        var element = new StaffElement(ElementType.CROTCHET);
        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "text", Lyric.Extend.NONE);

        element.setLyricForVerse(1, StaffElement.SyllableRelation.NONE, "   ", Lyric.Extend.NONE);

        assertThat(element.getLyricForVerse(1)).isNull();
    }
}
