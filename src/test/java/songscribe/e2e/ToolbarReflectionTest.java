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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for toolbar button reflection when notes are selected.
 * Verifies that selecting notes in select mode causes the toolbar buttons
 * to reflect the attributes of the selected notes.
 */
@Order(7)
class ToolbarReflectionTest extends E2ETest {

    @Test @Order(1)
    void testSingleNoteSelection() {
        buildComposition(crotchet(0));
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));

        assertActionSelected(Actions.QUARTER_NOTE_ACTION, true);
        assertActionSelected(Actions.HALF_NOTE_ACTION, false);
        assertActionSelected(Actions.EIGHTH_NOTE_ACTION, false);
        assertActionSelected(Actions.FLAT_ACTION, false);
        assertActionSelected(Actions.SHARP_ACTION, false);
        assertActionSelected(Actions.DOT_ACTION, false);

        // Verify toolbar button model mirrors the action state
        assertButtonSelected(Actions.QUARTER_NOTE_ACTION, true);
        assertButtonSelected(Actions.FLAT_ACTION, false);
        assertButtonSelected(Actions.DOT_ACTION, false);
    }

    @Test @Order(2)
    void testSingleRestSelection() {
        buildComposition(rest(NoteType.CROTCHET_REST, 0));
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));

        // DURATION actions: appliesTo=true but matchesNote=false (CROTCHET_REST != CROTCHET)
        assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
        assertActionSelected(Actions.HALF_NOTE_ACTION, false);
        assertActionSelected(Actions.EIGHTH_NOTE_ACTION, false);

        // NoteOnlyAction subclasses: appliesTo=false for rests
        assertActionSelected(Actions.FLAT_ACTION, false);
        assertActionSelected(Actions.FERMATA_ACTION, false);
        assertActionSelected(Actions.STACCATO_ACTION, false);
    }

    @Test @Order(3)
    void testMultipleIdenticalNotes() {
        buildComposition(crotchet(0), crotchet(-2));
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        assertThat(score().getSelectionSize()).isEqualTo(2);
        assertActionSelected(Actions.QUARTER_NOTE_ACTION, true);
        assertActionSelected(Actions.FLAT_ACTION, false);
        assertActionSelected(Actions.DOT_ACTION, false);
    }

    @Test @Order(4)
    void testMultipleDifferentDurations() {
        buildComposition(crotchet(0), minim(-2));
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        assertThat(score().getSelectionSize()).isEqualTo(2);
        assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
        assertActionSelected(Actions.HALF_NOTE_ACTION, false);
    }

    @Test @Order(5)
    void testMultipleDifferentAccidentals() {
        var note1 = crotchet(0);
        note1.setAccidental(Note.Accidental.FLAT);

        var note2 = crotchet(-2);
        note2.setAccidental(Note.Accidental.DOUBLE_FLAT);

        buildComposition(note1, note2);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        assertThat(score().getSelectionSize()).isEqualTo(2);
        assertActionSelected(Actions.FLAT_ACTION, false);
        assertActionSelected(Actions.DOUBLE_FLAT_ACTION, false);
        assertActionSelected(Actions.QUARTER_NOTE_ACTION, true);
    }

    @Test @Order(6)
    void testNoteAndRestSelection() {
        buildComposition(crotchet(0), rest(NoteType.CROTCHET_REST, -2));
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        assertThat(score().getSelectionSize()).isEqualTo(2);

        // CROTCHET action: applicable to both (DURATION appliesTo=true for all),
        // but matchesNote=false for the rest (CROTCHET_REST != CROTCHET)
        assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
        assertActionSelected(Actions.FLAT_ACTION, false);
    }

    @Test @Order(7)
    void testDifferentDurationsAndRest() {
        buildComposition(crotchet(0), minim(-2), rest(NoteType.CROTCHET_REST, -4));
        enterSelectMode();

        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 2));

        assertThat(score().getSelectionSize()).isEqualTo(3);
        assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
        assertActionSelected(Actions.HALF_NOTE_ACTION, false);
        assertActionSelected(Actions.FLAT_ACTION, false);
    }

    @Test @Order(8)
    void testSelectionClearedRestoresState() {
        // Use a minim so the flat action state changes are clearly visible.
        // Set flat on the note so reflection will select the flat action.
        var note = minim(0);
        note.setAccidental(Note.Accidental.FLAT);
        buildComposition(note);
        enterSelectMode();

        // The flat action should be deselected before any selection
        assertActionSelected(Actions.FLAT_ACTION, false);

        // Select the note — reflection should select the flat action
        clickAt(noteScreenPosition(0, 0));
        assertActionSelected(Actions.FLAT_ACTION, true);

        // Deselect all via Cmd+D — flat should be restored to deselected
        robot.pressAndReleaseKey(KeyEvent.VK_D, InputEvent.META_DOWN_MASK);
        pause();

        assertActionSelected(Actions.FLAT_ACTION, false);
    }


    // -- Note factory helpers --

    private Note crotchet(int staffPositionSp) {
        var note = NoteType.CROTCHET.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }

    private Note minim(int staffPositionSp) {
        var note = NoteType.MINIM.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }

    private Note rest(NoteType restType, int staffPositionSp) {
        var note = restType.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }


    // -- Composition builder --

    private void buildComposition(Note... notes) {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            for (var note : notes) {
                line.addNote(note);
            }

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }


    // -- Assertion helpers --

    /**
     * Asserts the action's selected state. Works for all actions,
     * including those that only exist in menus.
     */
    private void assertActionSelected(UIAction action, boolean expected) {
        var selectable = (UIAction.Selectable) action;
        var isSelected = GuiActionRunner.execute(() -> selectable.isSelected());
        assertThat(isSelected)
            .as("Action '%s' selected state", action.getActionCommand())
            .isEqualTo(expected);
    }

    /**
     * Asserts the toolbar button model's selected state. Only works for
     * actions that have a corresponding toolbar button.
     */
    private void assertButtonSelected(UIAction action, boolean expected) {
        var button = findButtonByName(action.getActionCommand());
        var isSelected = GuiActionRunner.execute(() -> button.getModel().isSelected());
        assertThat(isSelected)
            .as("Button '%s' selected state", action.getActionCommand())
            .isEqualTo(expected);
    }
}
