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
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;

class SyllabicDerivationTest extends UnitTest {

    private static final int VERSE = 1;

    private Song song;
    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // No lyric for the requested verse → getLyricForVerse returns null.
    @Test
    void testNoLyricReturnsNull() {
        var element = StaffElementFactory.crotchet();
        addElementWithoutTracking(element);

        assertThat(element.getLyricForVerse(VERSE)).isNull();
    }

    // STOP carrier (blank text, extend = STOP) → syllabic is null.
    @Test
    void testStopCarrierHasNullSyllabic() {
        var element = StaffElementFactory.crotchet();
        addElementWithoutTracking(element);
        element.properties.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.STOP, null, false));

        assertThat(lyricForVerse(element, VERSE).syllabic()).isNull();
    }

    // CONTINUE carrier (blank text, extend = CONTINUE) → syllabic is null.
    @Test
    void testContinueCarrierHasNullSyllabic() {
        var element = StaffElementFactory.crotchet();
        addElementWithoutTracking(element);
        element.properties.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.CONTINUE, null, false));

        assertThat(lyricForVerse(element, VERSE).syllabic()).isNull();
    }

    // SINGLE stored → retrieved as SINGLE.
    @Test
    void testSingleSyllabicStoredAndRetrieved() {
        var element = StaffElementFactory.crotchet();
        addElementWithoutTracking(element);
        element.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, "word", Lyric.Extend.NONE);

        assertThat(lyricForVerse(element, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
    }

    // BEGIN stored → retrieved as BEGIN.
    @Test
    void testBeginSyllabicStoredAndRetrieved() {
        var element = StaffElementFactory.crotchet();
        addElementWithoutTracking(element);
        element.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);

        assertThat(lyricForVerse(element, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
    }

    // BEGIN + compound stored → retrieved as BEGIN with compound=true.
    @Test
    void testCompoundBeginSyllabicStoredAndRetrieved() {
        var element = StaffElementFactory.crotchet();
        addElementWithoutTracking(element);
        element.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, true, "birth", Lyric.Extend.NONE);

        assertThat(lyricForVerse(element, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
        assertThat(lyricForVerse(element, VERSE).compound()).isTrue();
    }

    // END stored on second element → retrieved as END.
    @Test
    void testEndSyllabicStoredAndRetrieved() {
        var first = StaffElementFactory.crotchet();
        var second = StaffElementFactory.crotchet();
        addElementsWithoutTracking(first, second);
        first.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
        second.setLyricForVerse(VERSE, Lyric.Syllabic.END, false, "lo", Lyric.Extend.NONE);

        assertThat(lyricForVerse(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
    }

    // MIDDLE stored on second element → retrieved as MIDDLE.
    @Test
    void testMiddleSyllabicStoredAndRetrieved() {
        var first = StaffElementFactory.crotchet();
        var second = StaffElementFactory.crotchet();
        var third = StaffElementFactory.crotchet();
        addElementsWithoutTracking(first, second, third);
        first.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
        second.setLyricForVerse(VERSE, Lyric.Syllabic.MIDDLE, false, "lo", Lyric.Extend.NONE);
        third.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, "world", Lyric.Extend.NONE);

        assertThat(lyricForVerse(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.MIDDLE);
    }

    // END following a compound-BEGIN → retrieved as END.
    @Test
    void testEndAfterCompoundBeginStoredAndRetrieved() {
        var first = StaffElementFactory.crotchet();
        var second = StaffElementFactory.crotchet();
        addElementsWithoutTracking(first, second);
        first.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, true, "birth", Lyric.Extend.NONE);
        second.setLyricForVerse(VERSE, Lyric.Syllabic.END, false, "day", Lyric.Extend.NONE);

        assertThat(lyricForVerse(second, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
    }

    // Syllabic is stored per-element; elements without a lyric in between are irrelevant.
    @Test
    void testSyllabicStoredIndependentlyOfNeighbors() {
        var first = StaffElementFactory.crotchet();
        var blank = StaffElementFactory.crotchet();  // no lyric for VERSE
        var third = StaffElementFactory.crotchet();
        addElementsWithoutTracking(first, blank, third);
        first.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "hel", Lyric.Extend.NONE);
        third.setLyricForVerse(VERSE, Lyric.Syllabic.END, false, "lo", Lyric.Extend.NONE);

        assertThat(lyricForVerse(third, VERSE).syllabic()).isEqualTo(Lyric.Syllabic.END);
    }

    // -----------------------------------------------------------------------

    private Lyric lyricForVerse(StaffElement element, int verse) {
        var lyric = element.getLyricForVerse(verse);

        if (lyric == null) {
            throw new AssertionError("expected lyric for verse " + verse + ", found none");
        }

        return lyric;
    }

    private void addElementWithoutTracking(StaffElement element) {
        song.withoutMutationTracking(() -> line.addElement(element));
    }

    private void addElementsWithoutTracking(StaffElement... elements) {
        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });
    }
}
