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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.Mode;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SelectableUIAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

/**
 * Tests for miscellaneous SelectionCoordinator behaviors:
 * - globalMouseReleasedListener cleanup (row 76)
 * - musicSelectionDidChangeSaveRestoreActionStates dedup guard (row 80)
 * - triggerReflection dedup guard (row 81)
 * - reflectElement (row 92)
 * - updateGraceNoteActionEnabled select-mode disable (row 93)
 * - updateGraceNoteActionEnabled not-in-select-mode logic (row 94)
 * - triggerReflection grace-note enabled state (row 95)
 */
class SelectionCoordinatorMiscBehaviorTest extends MainFrameMockTest {

    // -------------------------------------------------------------------------
    // Row 76 — globalMouseReleasedListener: clears drag rectangle and nulls
    //           dragging line on MOUSE_RELEASED
    // -------------------------------------------------------------------------

    @Test
    void testGlobalMouseReleasedListenerClearsSelectionBandAndNullsDraggingLine() throws Exception {
        var mockToolkit = mock(Toolkit.class);

        try (var toolkitStatic = mockStatic(Toolkit.class)) {
            toolkitStatic.when(Toolkit::getDefaultToolkit).thenReturn(mockToolkit);

            var coordinator = new SelectionCoordinator(mock(ScoreView.class));

            var mockLine = mock(LineComponent.class);
            when(mockLine.isDraggingSelection()).thenReturn(true);

            // dragDidStart sets the field and registers the AWT listener (mocked to no-op).
            coordinator.getDragTracker().dragDidStart(mockLine);
            assertThat(coordinator.getDragTracker().getDraggingLine())
                .as("draggingLine set after dragDidStart")
                .isSameAs(mockLine);

            // Obtain the listener via the package-private accessor.
            var listener = coordinator.getDragTracker().getGlobalMouseReleasedListener();

            // Invoke the listener directly — the AWT dispatch path is not under test.
            var fakeSource = new Panel();
            var mouseReleasedEvent = new MouseEvent(
                fakeSource, MouseEvent.MOUSE_RELEASED, 0L, 0, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1
            );
            listener.eventDispatched(mouseReleasedEvent);

            // Both cleanup effects must be observable.
            verify(mockLine).clearSelectionBand();
            assertThat(coordinator.getDragTracker().getDraggingLine())
                .as("draggingLine nulled after MOUSE_RELEASED")
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Row 80 — musicSelectionDidChangeSaveRestoreActionStates: no-op when
    //           selection equals lastReflectedSelection
    // -------------------------------------------------------------------------

    @Test
    void testMusicSelectionDidChangeHandlerIsNoOpWhenSelectionUnchanged() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        // Select the note and trigger reflection — this sets lastReflectedSelection and
        // populates savedActionStates via the selectNote helper's saveActionStates call.
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();

        // Confirm states were saved.
        assertThat(coordinator.getActionReflector().hasSavedActionStates())
            .as("savedActionStates non-empty after first reflection")
            .isTrue();

        // Now fire the save/restore handler with the same selection (equals lastReflectedSelection).
        // It must be a complete no-op: it must not call restoreActionStates (which would clear the map).
        // The handler ignores its parameter — provide a mock to satisfy the non-null contract.
        var dummyNotification = mock(MusicSelectionDidChangeNotification.class);
        coordinator.getActionReflector().musicSelectionDidChangeSaveRestoreActionStates(dummyNotification);

        assertThat(coordinator.getActionReflector().hasSavedActionStates())
            .as("savedActionStates must remain non-empty — restore must not have been called")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // Row 81 — triggerReflection: skips when selection equals lastReflectedSelection
    // -------------------------------------------------------------------------

    @Test
    void testTriggerReflectionIsNoOpWhenSelectionUnchanged() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);

        var action = AccidentalAction.createSharpAction(mainFrame());
        var uiAction = (SelectableUIAction) action;

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        // First reflection: action becomes selected=true (SHARP matches).
        coordinator.getActionReflector().triggerReflection();
        assertThat(uiAction.isSelected())
            .as("action selected=true after first reflection (SHARP match)")
            .isTrue();

        // Artificially mutate the action state to something that a second reflection would reset.
        uiAction.setSelected(false);

        // Second triggerReflection with the same selection range → dedup guard fires → no-op.
        coordinator.getActionReflector().triggerReflection();

        // The artificial mutation must survive — the guard prevented re-reflection.
        assertThat(uiAction.isSelected())
            .as("action selected must remain at artificially-set false — dedup guard held")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 92 — reflectElement: sets selected on all reflectable actions based on
    //           a single element
    // -------------------------------------------------------------------------

    @Test
    void testReflectElementSelectsMatchingActionsAndDeselectsNonMatching() {
        var sharpAction = AccidentalAction.createSharpAction(mainFrame());
        var flatAction = AccidentalAction.createFlatAction(mainFrame());
        var uiSharp = (SelectableUIAction) sharpAction;
        var uiFlat = (SelectableUIAction) flatAction;

        // Pre-condition: set both to selected so we can confirm deselection on mismatch.
        uiSharp.setSelected(false);
        uiFlat.setSelected(true);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(),
            List.of(sharpAction, flatAction)
        );

        // A crotchet with SHARP accidental: sharpAction should match, flatAction should not.
        var sharpNote = ElementType.CROTCHET.newInstance();
        sharpNote.setAccidental(StaffElement.Accidental.SHARP);

        coordinator.getActionReflector().reflectElement(sharpNote);

        assertThat(uiSharp.isSelected())
            .as("sharpAction selected=true — element has SHARP")
            .isTrue();
        assertThat(uiFlat.isSelected())
            .as("flatAction selected=false — element does not have FLAT")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 93 — updateGraceNoteActionEnabled: disables grace-note action in select mode
    // -------------------------------------------------------------------------

    @Test
    void testUpdateGraceNoteActionEnabledDisablesInSelectMode() {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getMode()).thenReturn(Mode.SELECT);
        var coordinator = new SelectionCoordinator(scoreView);

        // Force the grace-note action to enabled so we can observe the disable.
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(true);

        // Calling with hasGraceNote=true while inSelectMode must still disable.
        coordinator.getActionReflector().updateGraceNoteActionEnabled(true);

        assertThat(Actions.GRACE_EIGHTH_NOTE_ACTION.isEnabled())
            .as("GRACE_EIGHTH_NOTE_ACTION disabled when in select mode")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 94 — updateGraceNoteActionEnabled: enabled only when not in select mode
    //           and hasGraceNote=true; disabled when hasGraceNote=false
    // -------------------------------------------------------------------------

    @Test
    void testUpdateGraceNoteActionEnabledFollowsHasGraceNoteWhenNotInSelectMode() {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getMode()).thenReturn(Mode.EDIT);
        var coordinator = new SelectionCoordinator(scoreView);

        // hasGraceNote=true, not in select mode → enabled.
        coordinator.getActionReflector().updateGraceNoteActionEnabled(true);
        assertThat(Actions.GRACE_EIGHTH_NOTE_ACTION.isEnabled())
            .as("GRACE_EIGHTH_NOTE_ACTION enabled when not in select mode and hasGraceNote=true")
            .isTrue();

        // hasGraceNote=false, not in select mode → disabled.
        coordinator.getActionReflector().updateGraceNoteActionEnabled(false);
        assertThat(Actions.GRACE_EIGHTH_NOTE_ACTION.isEnabled())
            .as("GRACE_EIGHTH_NOTE_ACTION disabled when not in select mode but hasGraceNote=false")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 95 — triggerReflection updates grace-note action enabled state based on
    //           selection content.
    //
    // The grace-note scan runs inside the outer loop over reflectable actions
    // (checking each element per action). To exercise that loop, each test
    // injects one sentinel reflectable action so the per-element scan is reached.
    // -------------------------------------------------------------------------

    @Test
    void testTriggerReflectionEnablesGraceNoteActionWhenGraceNoteInSelection() {
        // Build a selection that contains a grace note.
        var gracNote = ElementType.GRACE_QUAVER.newInstance();
        var regularNote = ElementType.CROTCHET.newInstance();

        // A sentinel reflectable action is required so the inner element loop
        // (which sets hasGraceNote) is actually reached during triggerReflection.
        var sentinelAction = AccidentalAction.createSharpAction(mainFrame());

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(gracNote, regularNote),
            List.of(sentinelAction)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(Actions.GRACE_EIGHTH_NOTE_ACTION.isEnabled())
            .as("GRACE_EIGHTH_NOTE_ACTION enabled when selection contains a grace note")
            .isTrue();
    }

    @Test
    void testTriggerReflectionDisablesGraceNoteActionWhenNoGraceNoteInSelection() {
        // Build a selection with no grace notes.
        var note1 = ElementType.CROTCHET.newInstance();
        var note2 = ElementType.CROTCHET.newInstance();

        // A sentinel reflectable action is required so the inner element loop runs.
        var sentinelAction = AccidentalAction.createSharpAction(mainFrame());

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2),
            List.of(sentinelAction)
        );

        // Pre-condition: force it enabled so we can observe the disable.
        Actions.GRACE_EIGHTH_NOTE_ACTION.setEnabled(true);

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertThat(Actions.GRACE_EIGHTH_NOTE_ACTION.isEnabled())
            .as("GRACE_EIGHTH_NOTE_ACTION disabled when selection contains no grace notes")
            .isFalse();
    }
}
