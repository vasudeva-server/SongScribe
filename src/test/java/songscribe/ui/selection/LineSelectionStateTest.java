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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.TupletInterval;
import songscribe.ui.action.TupletAction;

class LineSelectionStateTest extends UnitTest {

    // -- canToggleTuplet --

    @Test
    void testEmptySelectionCannotToggleTuplet() {
        var line = new Line();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testTwoPitchedNotesNoTupletCanToggle() {
        var line = new Line();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testFullCoverageOfTripletReportsCoversExisting() {
        var line = new Line();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getTuplets().addInterval(new TupletInterval(0, 2, TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(TupletInterval::getGrade)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isTrue();
    }

    @Test
    void testPartialCoverageOfTripletDoesNotCoverExisting() {
        var line = new Line();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getTuplets().addInterval(new TupletInterval(0, 2, TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(TupletInterval::getGrade)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testSelectionSpanningTwoDifferentTupletsCannotToggle() {
        var line = new Line();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.getTuplets().addInterval(new TupletInterval(0, 1, TupletAction.Tuplet.DUPLET.getSize()));
        line.getTuplets().addInterval(new TupletInterval(2, 3, TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(3);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testSelectionContainingNonPitchedElementCannotToggle() {
        var line = new Line();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET_REST.newInstance());
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }
}
