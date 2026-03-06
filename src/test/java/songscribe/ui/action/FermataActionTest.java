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

class FermataActionTest {

    private final FermataAction action = new FermataAction();

    @Test
    void testMatchesWhenFermataTrue() {
        var note = NoteType.CROTCHET.newInstance();
        note.setFermata(true);
        assertThat(action.matchesNote(note)).isTrue();
    }

    @Test
    void testDoesNotMatchWhenFermataFalse() {
        var note = NoteType.CROTCHET.newInstance();
        assertThat(action.matchesNote(note)).isFalse();
    }
}
