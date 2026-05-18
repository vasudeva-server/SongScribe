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
import songscribe.model.ArticulationType;
import songscribe.model.ElementType;
import songscribe.ui.layout.Articulation;

class ForceArticulationActionTest extends UnitTest {

    private final ForceArticulationAction action =
        ForceArticulationAction.createAccentAction();

    @Test
    void testApplyToNoteAppliesArticulation() {
        var note = ElementType.CROTCHET.newInstance();
        action.applyToElement(note, true);
        assertThat(note.hasArticulation(ArticulationType.ACCENT)).isTrue();
    }

    @Test
    void testApplyToNoteRemovesArticulation() {
        var note = ElementType.CROTCHET.newInstance();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        action.applyToElement(note, false);
        assertThat(note.hasArticulation(ArticulationType.ACCENT)).isFalse();
    }

    @Test
    void testDoesNotMatchWhenArticulationNull() {
        var note = ElementType.CROTCHET.newInstance();

        assertThat(action.matchesElement(note)).isFalse();
    }

    @Test
    void testMatchesWhenArticulationPresent() {
        var note = ElementType.CROTCHET.newInstance();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));

        assertThat(action.matchesElement(note)).isTrue();
    }
}
