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

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.ElementType;
import songscribe.ui.action.Actions;

/**
 * Milestone 1 E2E tests: element insertion, replacement, and pitch drag.
 */
class ElementInsertionTest extends E2ETest {

    @Nested
    class EditOperations {

        @Test
        void testDragNoteToNewStaffPosition() {
            // Insert a quarter note at staff position 0
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            var point = insertionPoint(0, 0);
            clickAt(point);
            performLayout(0);

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);
            var element = line.getElement(0);

            // Drag the note to a new staff position
            var targetStaffPositionSp = -4;
            dragNote(0, 0, targetStaffPositionSp);
            performLayout(0);

            // Same element object, updated staff position
            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(element.getStaffPosition()).isEqualTo(targetStaffPositionSp);
        }

        @Test
        void testReplaceNoteAtSameXDifferentPitch() {
            // Insert a quarter note
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            var point = insertionPoint(0, 0);
            clickAt(point);
            performLayout(0);

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            // Now select half note and click at the same X but different staff position
            selectDuration(Actions.HALF_NOTE_ACTION);
            var notePoint = noteScreenPosition(0, 0);
            var newStaffPositionSp = -4;

            var lc = score().getLineComponent(0);
            var replacementPoint = GuiActionRunner.execute(() -> {
                var yPx = lc.staffPositionToYPx(newStaffPositionSp);
                var loc = lc.getLocationOnScreen();
                return new java.awt.Point(notePoint.x, loc.y + yPx);
            });

            clickAt(replacementPoint);
            performLayout(0);

            // Element count should remain the same (replacement, not addition)
            assertThat(line.elementCount()).isEqualTo(1);

            var element = line.getElement(0);
            assertThat(element.getType()).isEqualTo(ElementType.MINIM);
            assertThat(element.getStaffPosition()).isEqualTo(newStaffPositionSp);
        }
    }

    @Nested
    class NoteInsertion {

        @Test
        void testInsertEighthNote() {
            selectDuration(Actions.EIGHTH_NOTE_ACTION);
            var staffPositionSp = -2;

            var point = insertionPoint(0, staffPositionSp);
            clickAt(point);
            performLayout(0);

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            var element = line.getElement(0);
            assertThat(element.getType()).isEqualTo(ElementType.QUAVER);
            assertThat(element.getStaffPosition()).isEqualTo(staffPositionSp);
        }

        @Test
        void testInsertHalfNote() {
            selectDuration(Actions.HALF_NOTE_ACTION);
            var staffPositionSp = 2;

            var point = insertionPoint(0, staffPositionSp);
            clickAt(point);
            performLayout(0);

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            var element = line.getElement(0);
            assertThat(element.getType()).isEqualTo(ElementType.MINIM);
            assertThat(element.getStaffPosition()).isEqualTo(staffPositionSp);
        }

        @Test
        void testInsertNoteAtDifferentStaffPositions() {
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            var positions = new int[]{0, -4, 4, -8};

            for (var staffPositionSp : positions) {
                var point = insertionPoint(0, staffPositionSp);
                clickAt(point);
                performLayout(0);
            }

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(positions.length);

            for (var i = 0; i < positions.length; i++) {
                assertThat(line.getElement(i).getStaffPosition()).isEqualTo(positions[i]);
            }
        }

        @Test
        void testInsertQuarterNote() {
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            var staffPositionSp = 0;

            var point = insertionPoint(0, staffPositionSp);
            clickAt(point);
            performLayout(0);

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);

            var element = line.getElement(0);
            assertThat(element.getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(element.getStaffPosition()).isEqualTo(staffPositionSp);
        }

        @Test
        void testInsertRest() {
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            enableRestMode();

            var point = insertionPoint(0, 0);
            clickAt(point);
            performLayout(0);

            var line = composition().getLine(0);
            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0).getType().isRest()).isTrue();
        }
    }

}
