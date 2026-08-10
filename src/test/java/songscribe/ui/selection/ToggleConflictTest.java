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
import static songscribe.dom.StaffElementFactory.crotchet;

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
    private ElementSelection range;

    @BeforeEach
    void setUp() {
        line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());

        range = new ElementSelection(line, 0, 1);
    }

    @Test
    void testQuarterNotesBeamingDisabledTieEnabled() {
        assertThat(RangeQueries.canToggleBeaming(range)).as("beam").isFalse();
        assertThat(RangeQueries.canToggleTie(range)).as("tie").isTrue();
    }

    @Test
    void testChangeDurationToEighthBothEnabled() {
        changeDuration(ElementType.QUAVER);

        assertThat(RangeQueries.canToggleBeaming(range)).as("beam").isTrue();
        assertThat(RangeQueries.canToggleTie(range)).as("tie").isTrue();
    }

    @Test
    void testToggleBeamOnDisablesTie() {
        changeDuration(ElementType.QUAVER);
        toggleBeam();

        assertThat(RangeQueries.canToggleTie(range)).as("tie after beam on").isFalse();
    }

    @Test
    void testToggleBeamOffReenablesTie() {
        changeDuration(ElementType.QUAVER);
        toggleBeam();
        toggleBeam();

        assertThat(RangeQueries.canToggleTie(range)).as("tie after beam off").isTrue();
    }

    @Test
    void testToggleTieOnDisablesBeam() {
        changeDuration(ElementType.QUAVER);
        toggleTie();

        assertThat(RangeQueries.canToggleBeaming(range)).as("beam after tie on").isFalse();
    }

    @Test
    void testToggleTieOffReenablesBeam() {
        changeDuration(ElementType.QUAVER);
        toggleTie();
        toggleTie();

        assertThat(RangeQueries.canToggleBeaming(range)).as("beam after tie off").isTrue();
    }

    @Test
    void testChangeDurationToQuarterDisablesBeam() {
        changeDuration(ElementType.QUAVER);
        changeDuration(ElementType.CROTCHET);

        assertThat(RangeQueries.canToggleBeaming(range)).as("beam after quarter").isFalse();
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
        var begin = range.begin();
        var end = range.end();
        var beginBeam = line.findBeamAt(begin);
        var endBeam = line.findBeamAt(end);

        //noinspection ObjectEquality
        if (beginBeam != null && beginBeam == endBeam) {
            line.removeBeaming(beginBeam);
        } else {
            line.addBeaming(new Beam(line.getElement(begin), line.getElement(end)));
        }
    }

    /**
     * Mirrors {@code MusicEditOperations.toggleTie}: the tie to remove is looked up from the
     * line, not cached by the preceding {@code canToggleTie} call. An earlier version of this
     * helper read a cache the production toggle never touched, so it exercised a path the app
     * does not have.
     */
    private void toggleTie() {
        var begin = range.begin();
        var end = range.end();
        var exactTie = line.findExactTie(begin, end);

        if (exactTie != null) {
            line.removeTie(exactTie);
        } else {
            line.addTie(new Tie(line.getElement(begin), line.getElement(end)));
        }
    }
}
