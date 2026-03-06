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

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import songscribe.music.NoteType;
import songscribe.ui.action.Actions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 1 E2E tests: note insertion, replacement, and pitch drag.
 */
@Order(1)
class NoteInsertionTest extends BaseSwingTest {

    @Test @Order(1)
    void testInsertQuarterNote() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        var staffPositionSp = 0;

        var point = insertionPoint(0, staffPositionSp);
        clickAt(point);
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(1);

        var note = line.getNote(0);
        assertThat(note.getNoteType()).isEqualTo(NoteType.CROTCHET);
        assertThat(note.getStaffPosition()).isEqualTo(staffPositionSp);
    }

    @Test @Order(2)
    void testInsertEighthNote() {
        selectDuration(Actions.EIGHTH_NOTE_ACTION);
        var staffPositionSp = -2;

        var point = insertionPoint(0, staffPositionSp);
        clickAt(point);
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(1);

        var note = line.getNote(0);
        assertThat(note.getNoteType()).isEqualTo(NoteType.QUAVER);
        assertThat(note.getStaffPosition()).isEqualTo(staffPositionSp);
    }

    @Test @Order(3)
    void testInsertHalfNote() {
        selectDuration(Actions.HALF_NOTE_ACTION);
        var staffPositionSp = 2;

        var point = insertionPoint(0, staffPositionSp);
        clickAt(point);
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(1);

        var note = line.getNote(0);
        assertThat(note.getNoteType()).isEqualTo(NoteType.MINIM);
        assertThat(note.getStaffPosition()).isEqualTo(staffPositionSp);
    }

    @Test @Order(4)
    void testInsertNoteAtDifferentStaffPositions() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        var positions = new int[]{0, -4, 4, -8};

        for (var staffPositionSp : positions) {
            var point = insertionPoint(0, staffPositionSp);
            clickAt(point);
            performLayout(0);
        }

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(positions.length);

        for (var i = 0; i < positions.length; i++) {
            assertThat(line.getNote(i).getStaffPosition()).isEqualTo(positions[i]);
        }
    }

    @Test @Order(5)
    void testInsertRest() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        enableRestMode();

        var point = insertionPoint(0, 0);
        clickAt(point);
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(1);
        assertThat(line.getNote(0).getNoteType().isRest()).isTrue();
    }

    @Test @Order(6)
    void testReplaceNoteAtSameXDifferentPitch() {
        // Insert a quarter note
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        var point = insertionPoint(0, 0);
        clickAt(point);
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(1);

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

        // Note count should remain the same (replacement, not addition)
        assertThat(line.noteCount()).isEqualTo(1);

        var note = line.getNote(0);
        assertThat(note.getNoteType()).isEqualTo(NoteType.MINIM);
        assertThat(note.getStaffPosition()).isEqualTo(newStaffPositionSp);
    }

    @Test @Order(7)
    void testDragNoteToNewStaffPosition() {
        // Insert a quarter note at staff position 0
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        var point = insertionPoint(0, 0);
        clickAt(point);
        performLayout(0);

        var line = composition().getLine(0);
        assertThat(line.noteCount()).isEqualTo(1);
        var note = line.getNote(0);

        // Click the note head to start a drag in edit mode
        var notePoint = noteScreenPosition(0, 0);
        clickAt(notePoint);

        // Drag the note to a new staff position
        var targetStaffPositionSp = -4;
        dragNote(0, 0, targetStaffPositionSp);
        performLayout(0);

        // Same note object, updated staff position
        assertThat(line.noteCount()).isEqualTo(1);
        assertThat(note.getStaffPosition()).isEqualTo(targetStaffPositionSp);
    }


}
