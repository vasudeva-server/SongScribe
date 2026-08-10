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
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ArticulationType;
import songscribe.dom.Articulation;

class ArticulationActionTest extends MainFrameMockTest {

    private ArticulationAction accentAction;
    private ArticulationAction staccatoAction;

    @BeforeEach
    void createActions() {
        accentAction = ArticulationAction.createAccentAction(mainFrame());
        staccatoAction = ArticulationAction.createStaccatoAction(mainFrame());
    }

    @Test
    void testAccentAppliesArticulation() {
        var note = crotchet();
        accentAction.applyToElement(note, true);
        assertThat(note.hasArticulation(ArticulationType.ACCENT)).isTrue();
    }

    @Test
    void testAccentRemovesArticulation() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        accentAction.applyToElement(note, false);
        assertThat(note.hasArticulation(ArticulationType.ACCENT)).isFalse();
    }

    @Test
    void testAccentDoesNotMatchWhenArticulationNull() {
        var note = crotchet();

        assertThat(accentAction.matchesElement(note)).isFalse();
    }

    @Test
    void testAccentMatchesWhenArticulationPresent() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));

        assertThat(accentAction.matchesElement(note)).isTrue();
    }

    @Test
    void testStaccatoAppliesArticulation() {
        var note = crotchet();
        staccatoAction.applyToElement(note, true);
        assertThat(note.hasArticulation(ArticulationType.STACCATO)).isTrue();
    }

    @Test
    void testStaccatoRemovesArticulation() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        staccatoAction.applyToElement(note, false);
        assertThat(note.hasArticulation(ArticulationType.STACCATO)).isFalse();
    }

    @Test
    void testStaccatoDoesNotMatchWhenArticulationNull() {
        var note = crotchet();
        assertThat(staccatoAction.matchesElement(note)).isFalse();
    }

    @Test
    void testStaccatoMatchesWhenArticulationMatches() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        assertThat(staccatoAction.matchesElement(note)).isTrue();
    }

    /**
     * A note may carry accent and staccato at once, so toggling one must leave the other
     * alone. Guards against applyToElement widening from "remove the matching type" to
     * "remove whatever is there", which would silently erase the accent.
     */
    @Test
    void testStaccatoApplyLeavesAnExistingAccentAlone() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));

        staccatoAction.applyToElement(note, true);

        assertThat(note.getArticulations())
            .extracting(Articulation::getType)
            .containsExactlyInAnyOrder(ArticulationType.ACCENT, ArticulationType.STACCATO);
    }

    @Test
    void testStaccatoRemoveLeavesAnExistingAccentAlone() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));

        staccatoAction.applyToElement(note, false);

        assertThat(note.getArticulations())
            .extracting(Articulation::getType)
            .containsExactly(ArticulationType.ACCENT);
    }

    @Test
    void testStaccatoApplyReplacesDuplicateArticulation() {
        var note = crotchet();
        note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        staccatoAction.applyToElement(note, true);
        assertThat(note.getArticulations())
            .filteredOn(a -> a.getType() == ArticulationType.STACCATO)
            .hasSize(1);
    }
}
