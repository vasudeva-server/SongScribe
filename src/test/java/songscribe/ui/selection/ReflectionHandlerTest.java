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

import java.util.List;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.RestModeAction;
import songscribe.ui.action.SelectableUIAction;
import songscribe.ui.action.UIAction;

class ReflectionHandlerTest extends MainFrameMockTest {

    // -- Section B: Core Logic --

    @Test
    void testAllApplicableAllMatchSelected() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testAllApplicableFirstMismatchDeselected() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.FLAT);

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testAllApplicableSecondMismatchDeselected() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setAccidental(StaffElement.Accidental.FLAT);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testAllInapplicableDeselected() {
        var rest1 = ElementType.CROTCHET_REST.newInstance();
        var rest2 = ElementType.CROTCHET_REST.newInstance();

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(rest1, rest2),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testChangedSelectionDoesNotResave() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);

        var note2 = ElementType.CROTCHET.newInstance();
        note2.setAccidental(StaffElement.Accidental.FLAT);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;
        uiAction.setSelected(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2),
            List.of(action)
        );

        // Select note 0 only, reflect — action becomes true (SHARP matches)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected()).isTrue();

        // Extend selection to [0,1], reflect — action becomes false (FLAT mismatch)
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected()).isFalse();

        // Clear and reflect — should restore to original saved state (false)
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected()).isFalse();
    }

    // -- Section F: Enabled State --

    @Test
    void testClearSelectionRestoresEnabledState() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        // Select and reflect — saves state (selected=false, enabled=true)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();

        // Manually disable the action during selection
        uiAction.setEnabled(false);

        // Clear and reflect — restores both selected and enabled from saved state
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isEnabled()).isTrue();
    }

    // -- Section A: State Transitions --

    @Test
    void testClearSelectionRestoresSavedState() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.FLAT);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;
        uiAction.setSelected(true);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        // Select and reflect — action becomes false (FLAT does not match SHARP)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected()).isFalse();

        // Clear and reflect — action restored to pre-selection value (true)
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testNewSelectionSavesState() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;
        uiAction.setSelected(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        // Select and reflect — action becomes true (SHARP matches)
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testNoSelectionNoSavedStateIsNoOp() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;
        uiAction.setSelected(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isFalse();
    }

    @Test
    void testNoteAndRestNoteMatchesSelected() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);

        var rest = ElementType.CROTCHET_REST.newInstance();

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, rest),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isTrue();
    }

    @Test
    void testNoteAndRestNoteMismatchDeselected() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.FLAT);

        var rest = ElementType.CROTCHET_REST.newInstance();

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, rest),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction.isSelected()).isFalse();
    }

    // -- row 73: clearSavedActionStates clears map without restoring --

    @Test
    void testClearSavedActionStatesDoesNotRestoreAndEmptiesMap() {
        var note = ElementType.CROTCHET.newInstance();

        var action = FermataAction.createAction(mainFrame());
        var uiAction = (SelectableUIAction) action;
        uiAction.setSelected(false);
        uiAction.setEnabled(true);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        // Save the initial state (selected=false, enabled=true)
        ReflectionTestHelper.selectNote(coordinator, 0);

        // Mutate the action to a different state while the selection is active
        uiAction.setSelected(true);
        uiAction.setEnabled(false);

        // Clear without restoring — the mutated state must be preserved
        coordinator.getActionReflector().clearSavedActionStates();

        assertThat(uiAction.isSelected())
            .as("selected must remain at mutated value (not restored to saved false)")
            .isTrue();
        assertThat(uiAction.isEnabled())
            .as("enabled must remain at mutated value (not restored to saved true)")
            .isFalse();
        assertThat(coordinator.getActionReflector().hasSavedActionStates())
            .as("saved map must be empty after clearSavedActionStates")
            .isFalse();
    }

    // -- row 74: restoreActionStatesWithFlag restores only flagged actions and clears all saved states --

    @Test
    void testRestoreWithFlagRestoresOnlyFlaggedActionAndClearsMap() {
        var note = ElementType.CROTCHET.newInstance();

        // flaggedAction carries DISABLE_IN_GRACE_MODE → should be restored
        var flaggedAction = RestModeAction.createAction(mainFrame());
        var uiFlagged = (SelectableUIAction) flaggedAction;
        uiFlagged.setSelected(false);
        uiFlagged.setEnabled(true);

        // unflaggedAction does not carry DISABLE_IN_GRACE_MODE → must not be restored
        var unflaggedAction = AccidentalAction.createSharpAction(mainFrame());
        var uiUnflagged = (SelectableUIAction) unflaggedAction;
        uiUnflagged.setSelected(false);
        uiUnflagged.setEnabled(true);

        var reflectableActions = List.<UIAction.Reflectable>of(flaggedAction, unflaggedAction);
        var managedActions = new ArrayList<UIAction>();
        managedActions.add(flaggedAction);
        managedActions.add(unflaggedAction);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            reflectableActions,
            managedActions
        );

        // Save initial states (selected=false, enabled=true for both)
        ReflectionTestHelper.selectNote(coordinator, 0);

        // Mutate both actions to distinct states while selection is active
        uiFlagged.setSelected(true);
        uiFlagged.setEnabled(false);
        uiUnflagged.setSelected(true);
        uiUnflagged.setEnabled(false);

        // Restore only actions carrying DISABLE_IN_GRACE_MODE
        coordinator.getActionReflector().restoreActionStatesWithFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE);

        // Flagged action must be restored to its saved state
        assertThat(uiFlagged.isSelected())
            .as("flagged action selected must be restored to saved false")
            .isFalse();
        assertThat(uiFlagged.isEnabled())
            .as("flagged action enabled must be restored to saved true")
            .isTrue();

        // Non-flagged action must keep its mutated state
        assertThat(uiUnflagged.isSelected())
            .as("unflagged action selected must remain at mutated true (not restored)")
            .isTrue();
        assertThat(uiUnflagged.isEnabled())
            .as("unflagged action enabled must remain at mutated false (not restored)")
            .isFalse();

        // The saved map must be fully cleared regardless
        assertThat(coordinator.getActionReflector().hasSavedActionStates())
            .as("saved map must be empty after restoreActionStatesWithFlag")
            .isFalse();
    }

    @Test
    void testReflectionDoesNotForceEnable() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action1 = AccidentalAction.createSharpAction(mainFrame());
        var action2 = AccidentalAction.createFlatAction(mainFrame());
        var uiAction1 = (SelectableUIAction) action1;
        var uiAction2 = (SelectableUIAction) action2;
        uiAction1.setEnabled(false);
        uiAction2.setEnabled(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action1, action2)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();

        // Reflection only sets selected state, not enabled state.
        // Enabled state is managed by flag-based logic in updateEnabledState().
        assertThat(uiAction1.isEnabled()).isFalse();
        assertThat(uiAction2.isEnabled()).isFalse();
    }

    @Test
    void testSaveRestoreRoundTripPreservesBothSelectedAndEnabled() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action1 = AccidentalAction.createSharpAction(mainFrame());
        var action2 = AccidentalAction.createFlatAction(mainFrame());
        var uiAction1 = (SelectableUIAction) action1;
        var uiAction2 = (SelectableUIAction) action2;

        // Set distinct initial states
        uiAction1.setSelected(true);
        uiAction1.setEnabled(true);
        uiAction2.setSelected(false);
        uiAction2.setEnabled(false);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action1, action2)
        );

        // Select and reflect — saves both selected and enabled for each action
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();

        // During selection, states have changed:
        // action1: selected=true (SHARP matches), action2: selected=false (FLAT doesn't match)
        // Manually flip enabled states to simulate flag chain changes during selection
        uiAction1.setEnabled(false);
        uiAction2.setEnabled(true);

        // Clear and reflect — restores original states
        ReflectionTestHelper.clearSelection(coordinator);
        coordinator.getActionReflector().triggerReflection();

        assertThat(uiAction1.isSelected()).as("action1 selected").isTrue();
        assertThat(uiAction1.isEnabled()).as("action1 enabled").isTrue();
        assertThat(uiAction2.isSelected()).as("action2 selected").isFalse();
        assertThat(uiAction2.isEnabled()).as("action2 enabled").isFalse();
    }
}
