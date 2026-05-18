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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.model.ElementType;

class DotActionTest extends UnitTest {
    private final DotAction singleDotAction = DotAction.createDotAction();
    private final DotAction doubleDotAction = DotAction.createDoubleDotAction();

    @Test
    void testApplyToNoteDoubleDotApplies() {
        var note = ElementType.CROTCHET.newInstance();
        doubleDotAction.applyToElement(note, true);
        assertThat(note.getDotCount()).isEqualTo(2);
    }

    @Test
    void testApplyToNoteRemovesDot() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDotCount(1);
        singleDotAction.applyToElement(note, false);
        assertThat(note.getDotCount()).isEqualTo(0);
    }

    @Test
    void testApplyToNoteSingleDotApplies() {
        var note = ElementType.CROTCHET.newInstance();
        singleDotAction.applyToElement(note, true);
        assertThat(note.getDotCount()).isEqualTo(1);
    }

    @Test
    void testAppliesToNote() {
        var note = ElementType.CROTCHET.newInstance();
        assertThat(singleDotAction.appliesTo(note)).isTrue();
    }

    @Test
    void testAppliesToRest() {
        var note = ElementType.CROTCHET_REST.newInstance();
        assertThat(singleDotAction.appliesTo(note)).isTrue();
    }

    @Test
    void testDoesNotApplyToBarline() {
        var note = ElementType.SINGLE_BARLINE.newInstance();
        assertThat(singleDotAction.appliesTo(note)).isFalse();
    }

    @Test
    void testDoubleDotDoesNotMatchDotCount1() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDotCount(1);
        assertThat(doubleDotAction.matchesElement(note)).isFalse();
    }

    @Test
    void testDoubleDotMatchesDotCount2() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDotCount(2);
        assertThat(doubleDotAction.matchesElement(note)).isTrue();
    }

    @Test
    void testSingleDotDoesNotMatchDotCount0() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDotCount(0);
        assertThat(singleDotAction.matchesElement(note)).isFalse();
    }

    @Test
    void testSingleDotMatchesDotCount1() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDotCount(1);
        assertThat(singleDotAction.matchesElement(note)).isTrue();
    }
}
