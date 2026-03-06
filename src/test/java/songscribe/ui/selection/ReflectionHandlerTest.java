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

import java.util.List;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.UIAction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionHandlerTest {

    // -- Section A: State Transitions --

    @Test
    void testNoSelectionNoSavedStateIsNoOp() {
        var note = NoteType.CROTCHET.newInstance();
        note.setAccidental(Note.Accidental.SHARP);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;
        uiAction.setSelected(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note),
                List.of(action)
        );

        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testClearSelectionRestoresSavedState() {
        var note = NoteType.CROTCHET.newInstance();
        note.setAccidental(Note.Accidental.FLAT);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;
        uiAction.setSelected(true);

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note),
                List.of(action)
        );

        // Select and reflect — action becomes false (FLAT does not match SHARP)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.reflectSelection(null);
        assertThat(uiAction.isSelected()).isFalse();

        // Clear and reflect — action restored to pre-selection value (true)
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.reflectSelection(null);
        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testNewSelectionSavesState() {
        var note = NoteType.CROTCHET.newInstance();
        note.setAccidental(Note.Accidental.SHARP);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;
        uiAction.setSelected(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note),
                List.of(action)
        );

        // Select and reflect — action becomes true (SHARP matches)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.reflectSelection(null);
        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testChangedSelectionDoesNotResave() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);

        var note2 = NoteType.CROTCHET.newInstance();
        note2.setAccidental(Note.Accidental.FLAT);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;
        uiAction.setSelected(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note1, note2),
                List.of(action)
        );

        // Select note 0 only, reflect — action becomes true (SHARP matches)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.reflectSelection(null);
        assertThat(uiAction.isSelected()).isTrue();

        // Extend selection to [0,1], reflect — action becomes false (FLAT mismatch)
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);
        assertThat(uiAction.isSelected()).isFalse();

        // Clear and reflect — should restore to original saved state (false)
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.reflectSelection(null);
        assertThat(uiAction.isSelected()).isFalse();
    }

    // -- Section B: Core Logic --

    @Test
    void testAllApplicableAllMatchSelected() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);

        var note2 = NoteType.CROTCHET.newInstance();
        note2.setAccidental(Note.Accidental.SHARP);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note1, note2),
                List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testAllApplicableFirstMismatchDeselected() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.FLAT);

        var note2 = NoteType.CROTCHET.newInstance();
        note2.setAccidental(Note.Accidental.SHARP);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note1, note2),
                List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testAllApplicableSecondMismatchDeselected() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);

        var note2 = NoteType.CROTCHET.newInstance();
        note2.setAccidental(Note.Accidental.FLAT);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note1, note2),
                List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testNoteAndRestNoteMatchesSelected() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);

        var rest = NoteType.CROTCHET_REST.newInstance();

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note1, rest),
                List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testNoteAndRestNoteMismatchDeselected() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.FLAT);

        var rest = NoteType.CROTCHET_REST.newInstance();

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note1, rest),
                List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testAllInapplicableDeselected() {
        var rest1 = NoteType.CROTCHET_REST.newInstance();
        var rest2 = NoteType.CROTCHET_REST.newInstance();

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(rest1, rest2),
                List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isSelected()).isFalse();
    }

    // -- Section F: Enabled State --

    @Test
    void testReflectionDoesNotForceEnable() {
        var note = NoteType.CROTCHET.newInstance();
        note.setAccidental(Note.Accidental.SHARP);

        var action1 = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var action2 = new AccidentalAction(Note.Accidental.FLAT, "Flat", null, 0, "flat", "Flat");
        var uiAction1 = (UIAction) action1;
        var uiAction2 = (UIAction) action2;
        uiAction1.setEnabled(false);
        uiAction2.setEnabled(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note),
                List.of(action1, action2)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.reflectSelection(null);

        // Reflection only sets selected state, not enabled state.
        // Enabled state is managed by flag-based logic in updateEnabledState().
        assertThat(uiAction1.isEnabled()).isFalse();
        assertThat(uiAction2.isEnabled()).isFalse();
    }

    @Test
    void testClearSelectionDoesNotSetEnabled() {
        var note = NoteType.CROTCHET.newInstance();
        note.setAccidental(Note.Accidental.SHARP);

        var action = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        var uiAction = (UIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
                List.of(note),
                List.of(action)
        );

        // Select and reflect
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.reflectSelection(null);

        // Manually disable the action
        uiAction.setEnabled(false);

        // Clear and reflect — restore only restores selected, not enabled
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.reflectSelection(null);

        assertThat(uiAction.isEnabled()).isFalse();
    }
}
