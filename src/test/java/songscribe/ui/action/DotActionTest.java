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

import songscribe.music.NoteType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DotActionTest {
    private final DotAction singleDotAction =
        new DotAction(DotAction.DotLevel.SINGLE, "Dot", null, 0, "dot", "Add dot", 0, 0);
    private final DotAction doubleDotAction =
        new DotAction(DotAction.DotLevel.DOUBLE, "Double Dot", null, 0, "double-dot", "Add double dot", 0, 0);

    @Test
    void testAppliesToNote() {
        var note = NoteType.CROTCHET.newInstance();
        assertThat(singleDotAction.appliesTo(note)).isTrue();
    }

    @Test
    void testAppliesToRest() {
        var note = NoteType.CROTCHET_REST.newInstance();
        assertThat(singleDotAction.appliesTo(note)).isTrue();
    }

    @Test
    void testDoesNotApplyToBarline() {
        var note = NoteType.SINGLE_BARLINE.newInstance();
        assertThat(singleDotAction.appliesTo(note)).isFalse();
    }

    @Test
    void testSingleDotMatchesDotCount1() {
        var note = NoteType.CROTCHET.newInstance();
        note.setDotCount(1);
        assertThat(singleDotAction.matchesNote(note)).isTrue();
    }

    @Test
    void testSingleDotDoesNotMatchDotCount0() {
        var note = NoteType.CROTCHET.newInstance();
        note.setDotCount(0);
        assertThat(singleDotAction.matchesNote(note)).isFalse();
    }

    @Test
    void testDoubleDotMatchesDotCount2() {
        var note = NoteType.CROTCHET.newInstance();
        note.setDotCount(2);
        assertThat(doubleDotAction.matchesNote(note)).isTrue();
    }

    @Test
    void testDoubleDotDoesNotMatchDotCount1() {
        var note = NoteType.CROTCHET.newInstance();
        note.setDotCount(1);
        assertThat(doubleDotAction.matchesNote(note)).isFalse();
    }
}
