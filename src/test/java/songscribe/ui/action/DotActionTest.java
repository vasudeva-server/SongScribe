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

package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.component.MainFrame;

class DotActionTest extends UnitTest {

    private static final MainFrame MOCK_FRAME = mock(MainFrame.class, RETURNS_DEEP_STUBS);

    private final DotAction singleDotAction = DotAction.createDotAction(MOCK_FRAME);
    private final DotAction doubleDotAction = DotAction.createDoubleDotAction(MOCK_FRAME);

    @Test
    void testApplyToNoteDoubleDotApplies() {
        var note = crotchet();
        doubleDotAction.applyToElement(note, true);
        assertThat(note.getDotCount()).isEqualTo(2);
    }

    @Test
    void testApplyToNoteRemovesDot() {
        var note = crotchet();
        note.setDotCount(1);
        singleDotAction.applyToElement(note, false);
        assertThat(note.getDotCount()).isEqualTo(0);
    }

    @Test
    void testApplyToNoteSingleDotApplies() {
        var note = crotchet();
        singleDotAction.applyToElement(note, true);
        assertThat(note.getDotCount()).isEqualTo(1);
    }

    @Test
    void testAppliesToNote() {
        var note = crotchet();
        assertThat(singleDotAction.appliesTo(note)).isTrue();
    }

    @Test
    void testAppliesToRest() {
        var note = crotchetRest();
        assertThat(singleDotAction.appliesTo(note)).isTrue();
    }

    @Test
    void testDoesNotApplyToBarline() {
        var note = singleBarline();
        assertThat(singleDotAction.appliesTo(note)).isFalse();
    }

    @Test
    void testDoubleDotDoesNotMatchDotCount1() {
        var note = crotchet();
        note.setDotCount(1);
        assertThat(doubleDotAction.matchesElement(note)).isFalse();
    }

    @Test
    void testDoubleDotMatchesDotCount2() {
        var note = crotchet();
        note.setDotCount(2);
        assertThat(doubleDotAction.matchesElement(note)).isTrue();
    }

    @Test
    void testSingleDotDoesNotMatchDotCount0() {
        var note = crotchet();
        note.setDotCount(0);
        assertThat(singleDotAction.matchesElement(note)).isFalse();
    }

    @Test
    void testSingleDotMatchesDotCount1() {
        var note = crotchet();
        note.setDotCount(1);
        assertThat(singleDotAction.matchesElement(note)).isTrue();
    }

    @Test
    void testSingleDotDoesNotMatchDotCount2() {
        var note = crotchet();
        note.setDotCount(2);
        assertThat(singleDotAction.matchesElement(note)).isFalse();
    }
}
