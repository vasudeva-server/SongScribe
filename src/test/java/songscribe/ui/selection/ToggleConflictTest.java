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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Tie;

/**
 * Tests the interplay between beaming and tie toggle availability.
 * Verifies that adding a beam disables tie toggling (and vice versa),
 * and that duration changes correctly update beaming availability.
 */
class ToggleConflictTest extends UnitTest {

    private Line line;
    private LineSelectionState state;

    @BeforeEach
    void setUp() {
        line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());

        state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);
    }

    @Test
    void testQuarterNotesBeamingDisabledTieEnabled() {
        assertThat(state.canToggleBeaming()).as("beam").isFalse();
        assertThat(state.canToggleTie()).as("tie").isTrue();
    }

    @Test
    void testChangeDurationToEighthBothEnabled() {
        changeDuration(ElementType.QUAVER);

        assertThat(state.canToggleBeaming()).as("beam").isTrue();
        assertThat(state.canToggleTie()).as("tie").isTrue();
    }

    @Test
    void testToggleBeamOnDisablesTie() {
        changeDuration(ElementType.QUAVER);
        toggleBeam();

        assertThat(state.canToggleTie()).as("tie after beam on").isFalse();
    }

    @Test
    void testToggleBeamOffReenablesTie() {
        changeDuration(ElementType.QUAVER);
        toggleBeam();
        toggleBeam();

        assertThat(state.canToggleTie()).as("tie after beam off").isTrue();
    }

    @Test
    void testToggleTieOnDisablesBeam() {
        changeDuration(ElementType.QUAVER);
        toggleTie();

        assertThat(state.canToggleBeaming()).as("beam after tie on").isFalse();
    }

    @Test
    void testToggleTieOffReenablesBeam() {
        changeDuration(ElementType.QUAVER);
        toggleTie();
        toggleTie();

        assertThat(state.canToggleBeaming()).as("beam after tie off").isTrue();
    }

    @Test
    void testChangeDurationToQuarterDisablesBeam() {
        changeDuration(ElementType.QUAVER);
        changeDuration(ElementType.CROTCHET);

        assertThat(state.canToggleBeaming()).as("beam after quarter").isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers — simulate the operations that ScoreViewController performs
    // -------------------------------------------------------------------------

    private void changeDuration(ElementType type) {
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            line.setElement(i, type.newInstance());
        }
    }

    private void toggleBeam() {
        var begin = state.getSelectionBegin();
        var end = state.getSelectionEnd();
        var beginBeam = line.findBeamAt(begin);
        var endBeam = line.findBeamAt(end);

        //noinspection ObjectEquality
        if (beginBeam != null && beginBeam == endBeam) {
            line.removeBeaming(beginBeam);
        } else {
            line.addBeaming(new Beam(line.getElement(begin), line.getElement(end)));
        }
    }

    private void toggleTie() {
        // Ensure canToggleTie was evaluated so existingTie is set
        state.canToggleTie();

        var existingTie = state.getExistingTie();

        if (existingTie != null) {
            line.removeTie(existingTie);
        } else {
            var anchorElement = line.getElement(state.getSelectionBegin());
            var endElement = line.getElement(state.getSelectionEnd());
            line.addTie(new Tie(anchorElement, endElement));
        }

        state.resetTieState();
    }
}
