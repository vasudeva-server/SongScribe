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

import songscribe.music.DurationArticulation;
import songscribe.music.NoteType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DurationArticulationActionTest {

    private final DurationArticulationAction action = new DurationArticulationAction(
        DurationArticulation.STACCATO, "Staccato", null, 0, "staccato", "Add staccato"
    );

    @Test
    void testMatchesWhenArticulationMatches() {
        var note = NoteType.CROTCHET.newInstance();
        note.setDurationArticulation(DurationArticulation.STACCATO);
        assertThat(action.matchesNote(note)).isTrue();
    }

    @Test
    void testDoesNotMatchWhenArticulationNull() {
        var note = NoteType.CROTCHET.newInstance();
        assertThat(action.matchesNote(note)).isFalse();
    }
}
