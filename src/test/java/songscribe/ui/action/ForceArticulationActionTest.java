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

import songscribe.UnitTest;
import songscribe.music.ForceArticulation;
import songscribe.music.NoteType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForceArticulationActionTest extends UnitTest {

    private final ForceArticulationAction action =
            new ForceArticulationAction(
                ForceArticulation.ACCENT, "Accent", null, 0, "accent", "Add accent"
            );

    @Test
    void testMatchesWhenArticulationPresent() {
        var note = NoteType.CROTCHET.newInstance();
        note.setForceArticulation(ForceArticulation.ACCENT);

        assertThat(action.matchesNote(note)).isTrue();
    }

    @Test
    void testDoesNotMatchWhenArticulationNull() {
        var note = NoteType.CROTCHET.newInstance();

        assertThat(action.matchesNote(note)).isFalse();
    }

    @Test
    void testApplyToNoteAppliesArticulation() {
        var note = NoteType.CROTCHET.newInstance();
        action.applyToNote(note, true);
        assertThat(note.getForceArticulation()).isEqualTo(ForceArticulation.ACCENT);
    }

    @Test
    void testApplyToNoteRemovesArticulation() {
        var note = NoteType.CROTCHET.newInstance();
        note.setForceArticulation(ForceArticulation.ACCENT);
        action.applyToNote(note, false);
        assertThat(note.getForceArticulation()).isNull();
    }
}
