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

import module java.desktop;

import java.util.Objects;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.junit.jupiter.api.Test;

import songscribe.music.ArticulationType;
import songscribe.music.ElementType;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;

/**
 * E2E tests for element insertion mechanics.
 */
class ElementInsertionTest extends E2ETest {

    @Test
    void testInsertVariousElementTypes() throws Exception {
        int stepCounter = 0;

        debugStep(++stepCounter, "Build composition with various elements", (step) -> {
            buildNotes(Actions.QUARTER_NOTE_ACTION, -4);
            buildNotes(Actions.HALF_NOTE_ACTION, 2);
            buildNotes(Actions.EIGHTH_NOTE_ACTION, -2, -6);

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
        });

        debugStep(++stepCounter, "Verify element types and auto-beam", (step) -> {
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
                () -> assertThat(elem0.getType()).as(step + ": quarter note type").isEqualTo(ElementType.CROTCHET),
                () -> assertThat(elem0.getStaffPosition()).as(step + ": quarter note position").isEqualTo(-4),
                () -> assertThat(elem1.getType()).as(step + ": half note type").isEqualTo(ElementType.MINIM),
                () -> assertThat(elem1.getStaffPosition()).as(step + ": half note position").isEqualTo(2),
                () -> assertThat(elem2.getType()).as(step + ": eighth note type").isEqualTo(ElementType.QUAVER),
                () -> assertThat(elem2.getStaffPosition()).as(step + ": eighth note position").isEqualTo(-2),
                () -> assertThat(elem3.getType()).as(step + ": second eighth type").isEqualTo(ElementType.QUAVER),
                () -> assertThat(elem3.getStaffPosition()).as(step + ": second eighth position").isEqualTo(-6),
                () -> assertThat(note2Beamed).as(step + ": auto-beam: note 2 beamed").isTrue(),
                () -> assertThat(note3Beamed).as(step + ": auto-beam: note 3 beamed").isTrue(),
                () -> {
                    assertThat(beamIntervalStart).as(step + ": auto-beam: interval spans 2-3 (start)").isEqualTo(2);
                    assertThat(beamIntervalEnd).as(step + ": auto-beam: interval spans 2-3 (end)").isEqualTo(3);
                },
                () -> assertThat(elem4.getType().isRest()).as(step + ": rest type").isTrue(),
                () -> assertThat(countBefore).as(step + ": total element count").isEqualTo(5)
            );
        });

        debugStep(++stepCounter, "Insert between and verify shift", (step) -> {
            deselectRestMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPointBefore(0, 1, 4));
            performLayout(0);

            var line = composition().getLine(0);
            var countAfter = line.elementCount();
            var newElem1 = line.getElement(1);
            var origElem1 = line.getElement(2);

            assertAll(
                () -> assertThat(countAfter).as(step + ": element count").isEqualTo(6),
                () -> assertThat(newElem1.getType()).as(step + ": inserted element type").isEqualTo(ElementType.CROTCHET),
                () -> assertThat(newElem1.getStaffPosition()).as(step + ": inserted element position").isEqualTo(4),
                () -> assertThat(origElem1.getType()).as(step + ": shifted element type").isEqualTo(ElementType.MINIM)
            );
        });

        debugStep(++stepCounter, "Click on note replaces it", (step) -> {
            // The line has: quarter(-4), quarter(4), half(2), eighth(-2), eighth(-6), rest(0)
            // Click on the element at index 2 (half note at sp=2) with quarter duration at a different Y
            var countBefore = composition().getLine(0).elementCount();
            var targetSp = -6;

            selectDuration(Actions.QUARTER_NOTE_ACTION);

            // Click at the half note's X but at a different staff position
            var existingPos = noteScreenPosition(0, 2);
            var replacePoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                return new Point(existingPos.x, loc.y + lc.staffPositionToYPx(targetSp));
            }));

            clickAt(replacePoint);
            performLayout(0);

            var line = composition().getLine(0);
            assertAll(
                () -> assertThat(line.getElement(2).getType())
                    .as(step + ": replaced with quarter").isEqualTo(ElementType.CROTCHET),
                () -> assertThat(line.getElement(2).getStaffPosition())
                    .as(step + ": pitch updated").isEqualTo(targetSp),
                () -> assertThat(line.elementCount())
                    .as(step + ": count unchanged").isEqualTo(countBefore),
                () -> assertThat(score().getMode())
                    .as(step + ": mode stays EDIT").isEqualTo(Mode.EDIT)
            );
        });

        debugStep(++stepCounter, "Click with rest selected replaces with rest", (step) -> {
            var countBefore = composition().getLine(0).elementCount();

            enableRestMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);

            // Click on the first element (a pitched quarter note at index 0)
            clickAt(noteScreenPosition(0, 0));
            performLayout(0);

            var line = composition().getLine(0);
            assertAll(
                () -> assertThat(line.getElement(0).getType().isRest())
                    .as(step + ": replaced with rest").isTrue(),
                () -> assertThat(line.elementCount())
                    .as(step + ": count unchanged").isEqualTo(countBefore),
                () -> assertThat(score().getMode())
                    .as(step + ": mode stays EDIT").isEqualTo(Mode.EDIT)
            );

            deselectRestMode();
        });

        debugStep(++stepCounter, "Same-type click strips decorations", (step) -> {
            // Apply staccato to a note, then click it with same duration
            enterSelectMode();
            clickAt(noteScreenPosition(0, 1));
            clickMenuItem(Actions.STACCATO_ACTION);
            performLayout(0);

            // Verify staccato was applied
            assertThat(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(1).hasArticulation(ArticulationType.STACCATO)
            )).as(step + ": staccato applied").isTrue();

            // Now in EDIT mode, click on the same note with same duration
            enterEditMode();
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(noteScreenPosition(0, 1));
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(1).hasArticulation(ArticulationType.STACCATO)
                )).as(step + ": staccato removed").isFalse(),
                () -> assertThat(score().getMode())
                    .as(step + ": mode stays EDIT").isEqualTo(Mode.EDIT)
            );
        });
    }

    @Test
    void testInsertionWhenLineIsFull() throws Exception {
        loadFixture("full-line");
        int stepCounter = 0;

        debugStep(++stepCounter, "Insert into full line shows error", (step) -> {
            var line = composition().getLine(0);
            var originalCount = line.elementCount();

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPointBefore(0, 1, 0));

            var optionPane = JOptionPaneFinder.findOptionPane().using(robot);
            optionPane.requireErrorMessage();
            optionPane.okButton().click();

            assertThat(line.elementCount()).as(step + ": element count unchanged").isEqualTo(originalCount);
        });
    }
}
