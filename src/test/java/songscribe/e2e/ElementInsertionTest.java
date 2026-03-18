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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.junit.jupiter.api.Test;

import songscribe.music.ElementType;
import songscribe.ui.action.Actions;

/**
 * E2E tests for element insertion mechanics.
 */
class ElementInsertionTest extends E2ETest {

    @Test
    void testInsertVariousElementTypes() throws Exception {
        debugStep("Build composition with various elements", () -> {
            buildNotes(Actions.QUARTER_NOTE_ACTION, -4);
            buildNotes(Actions.HALF_NOTE_ACTION, 2);
            buildNotes(Actions.EIGHTH_NOTE_ACTION, -2, -6);

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
        });

        debugStep("Verify element types and auto-beam", () -> {
            var line = composition().getLine(0);
            var countBefore = line.elementCount();
            var elem0 = line.getElement(0);
            var elem1 = line.getElement(1);
            var elem2 = line.getElement(2);
            var elem3 = line.getElement(3);
            var elem4 = line.getElement(4);
            var note2Beamed = isBeamed(0, 2);
            var note3Beamed = isBeamed(0, 3);
            var beamIntervalStart = GuiActionRunner.execute(() -> {
                var interval = composition().getLine(0).getBeamings().findInterval(2);
                return interval != null ? interval.getStart() : -1;
            });
            var beamIntervalEnd = GuiActionRunner.execute(() -> {
                var interval = composition().getLine(0).getBeamings().findInterval(2);
                return interval != null ? interval.getEnd() : -1;
            });

            assertAll(
                () -> assertThat(elem0.getType()).as("quarter note type").isEqualTo(ElementType.CROTCHET),
                () -> assertThat(elem0.getStaffPosition()).as("quarter note position").isEqualTo(-4),
                () -> assertThat(elem1.getType()).as("half note type").isEqualTo(ElementType.MINIM),
                () -> assertThat(elem1.getStaffPosition()).as("half note position").isEqualTo(2),
                () -> assertThat(elem2.getType()).as("eighth note type").isEqualTo(ElementType.QUAVER),
                () -> assertThat(elem2.getStaffPosition()).as("eighth note position").isEqualTo(-2),
                () -> assertThat(elem3.getType()).as("second eighth type").isEqualTo(ElementType.QUAVER),
                () -> assertThat(elem3.getStaffPosition()).as("second eighth position").isEqualTo(-6),
                () -> assertThat(note2Beamed).as("auto-beam: note 2 beamed").isTrue(),
                () -> assertThat(note3Beamed).as("auto-beam: note 3 beamed").isTrue(),
                () -> {
                    assertThat(beamIntervalStart).as("auto-beam: interval spans 2-3 (start)").isEqualTo(2);
                    assertThat(beamIntervalEnd).as("auto-beam: interval spans 2-3 (end)").isEqualTo(3);
                },
                () -> assertThat(elem4.getType().isRest()).as("rest type").isTrue(),
                () -> assertThat(countBefore).as("total element count").isEqualTo(5)
            );
        });

        debugStep("Insert between and verify shift", () -> {
            deselectRestMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPointBefore(0, 1, 4));
            performLayout(0);

            var line = composition().getLine(0);
            var countAfter = line.elementCount();
            var newElem1 = line.getElement(1);
            var origElem1 = line.getElement(2);

            assertAll(
                () -> assertThat(countAfter).as("insert between: element count").isEqualTo(6),
                () -> assertThat(newElem1.getType()).as("inserted element type").isEqualTo(ElementType.CROTCHET),
                () -> assertThat(newElem1.getStaffPosition()).as("inserted element position").isEqualTo(4),
                () -> assertThat(origElem1.getType()).as("shifted element type").isEqualTo(ElementType.MINIM)
            );
        });
    }

    @Test
    void testInsertionWhenLineIsFull() throws Exception {
        loadFixture("full-line");

        debugStep("Insert into full line shows error", () -> {
            var line = composition().getLine(0);
            var originalCount = line.elementCount();

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPointBefore(0, 1, 0));

            var optionPane = JOptionPaneFinder.findOptionPane().using(robot);
            optionPane.requireErrorMessage();
            optionPane.okButton().click();

            assertThat(line.elementCount()).as("element count unchanged").isEqualTo(originalCount);
        });
    }
}
