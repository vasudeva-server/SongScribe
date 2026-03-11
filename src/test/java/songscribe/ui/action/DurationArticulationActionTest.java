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
import songscribe.music.DurationArticulation;
import songscribe.music.ElementType;

class DurationArticulationActionTest extends UnitTest {

    private final DurationArticulationAction action = new DurationArticulationAction(
        DurationArticulation.STACCATO, "Staccato", null, 0, "staccato", "Add staccato"
    );

    @Test
    void testApplyToNoteAppliesArticulation() {
        var note = ElementType.CROTCHET.newInstance();
        action.applyToElement(note, true);
        assertThat(note.getDurationArticulation()).isEqualTo(DurationArticulation.STACCATO);
    }

    @Test
    void testApplyToNoteRemovesArticulation() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDurationArticulation(DurationArticulation.STACCATO);
        action.applyToElement(note, false);
        assertThat(note.getDurationArticulation()).isNull();
    }

    @Test
    void testDoesNotMatchWhenArticulationNull() {
        var note = ElementType.CROTCHET.newInstance();
        assertThat(action.matchesElement(note)).isFalse();
    }

    @Test
    void testMatchesWhenArticulationMatches() {
        var note = ElementType.CROTCHET.newInstance();
        note.setDurationArticulation(DurationArticulation.STACCATO);
        assertThat(action.matchesElement(note)).isTrue();
    }
}
