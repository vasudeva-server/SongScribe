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
import songscribe.model.Song;
import songscribe.model.ElementType;
import songscribe.model.Line;
import songscribe.ui.action.TupletAction;
import songscribe.ui.layout.Tuplet;

class LineSelectionStateTest extends UnitTest {

    // -- canToggleTuplet --

    @Test
    void testEmptySelectionCannotToggleTuplet() {
        var line = detachedLine();
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
        var line = detachedLine();
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
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(2);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isTrue();
    }

    @Test
    void testPartialCoverageOfTripletDoesNotCoverExisting() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(1);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testSelectionSpanningTwoDifferentTupletsCannotToggle() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addTuplet(new Tuplet(line.getElement(0), line.getElement(1), TupletAction.Tuplet.DUPLET.getSize()));
        line.addTuplet(new Tuplet(line.getElement(2), line.getElement(3), TupletAction.Tuplet.TRIPLET.getSize()));
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(0);
        state.extendSelectionTo(3);

        var info = state.canToggleTuplet();

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    // -- selectAll excludes the auto-maintained final barline --

    @Test
    void testSelectAllExcludesAutoMaintainedFinalBarlineOnLastLine() {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        // Line now holds: [quarter, quarter, FINAL_DOUBLE_BARLINE]
        assertThat(line.elementCount()).isEqualTo(3);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

        var state = new LineSelectionState(line);
        state.selectAll();

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
    }

    @Test
    void testSelectAllExcludesAutoMaintainedRightRepeatTerminalOnLastLine() {
        var song = new Song();
        var line = song.getLine(0);
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        assertThat(line.elementCount()).isEqualTo(3);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.REPEAT_RIGHT);

        var state = new LineSelectionState(line);
        state.selectAll();

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(1);
    }

    @Test
    void testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing() {
        var song = new Song();
        var line = song.getLine(0);

        // Default song seeds the first (and only) line with just the final barline.
        assertThat(line.elementCount()).isEqualTo(1);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

        var state = new LineSelectionState(line);
        state.selectAll();

        assertThat(state.hasElementSelection()).isFalse();
    }

    @Test
    void testSelectAllOnNonLastLineIncludesAllElements() {
        var song = new Song();
        var firstLine = song.getLine(0);
        song.addLine(1, new Line(song));

        // firstLine is no longer the last line — its final barline has been transferred away.
        song.withoutMutationTracking(() -> {
            firstLine.addElement(0, ElementType.CROTCHET.newInstance());
            firstLine.addElement(1, ElementType.CROTCHET.newInstance());
        });

        var state = new LineSelectionState(firstLine);
        state.selectAll();

        assertThat(state.getSelectionBegin()).isEqualTo(0);
        assertThat(state.getSelectionEnd()).isEqualTo(firstLine.elementCount() - 1);
    }

    @Test
    void testSelectionContainingNonPitchedElementCannotToggle() {
        var line = detachedLine();
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
