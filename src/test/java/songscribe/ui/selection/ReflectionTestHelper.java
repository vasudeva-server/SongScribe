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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import songscribe.UnitTest;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.Ending;
import songscribe.ui.Mode;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.ScoreView;

/**
 * Test utility for setting up a {@link SelectionCoordinator} with a {@link Line}
 * containing specific notes and a custom set of reflectable actions.
 * This bypasses the {@link songscribe.ui.action.Actions} reflection scan
 * that pulls in the full UI singleton graph.
 */
public final class ReflectionTestHelper {

    private ReflectionTestHelper() {
    }

    /**
     * A score-view stub reporting EDIT mode, which is what
     * {@link SelectionCoordinator#isInSelectMode()} derives its answer from. Tests that need
     * select-mode behavior build their own stub rather than using these helpers.
     */
    private static ScoreView editModeScoreView() {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getMode()).thenReturn(Mode.EDIT);
        return scoreView;
    }

    /**
     * Creates a SelectionCoordinator for an existing Line (e.g. from a fixture),
     * registered and activated at line index 0, with no reflectable actions.
     */
    public static SelectionCoordinator createCoordinatorForLine(Line line) {
        var coordinator = new SelectionCoordinator(editModeScoreView());
        var state = new LineSelectionState(line);
        coordinator.registerLineState(0, state);
        coordinator.activateLine(0);
        return createCoordinator(coordinator, List.of(), List.of());
    }

    /**
     * Creates a SelectionCoordinator with a Line containing the given notes,
     * registered and activated at line index 0, with the given actions
     * injected as the reflectable actions list. The line is backed by a minimal
     * song mock with mutation tracking suspended.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        return createCoordinator(notes, actions, UnitTest.minimalSongMock());
    }

    /**
     * Creates a SelectionCoordinator with a Line backed by the given {@code song},
     * registered and activated at line index 0, with the given actions
     * injected as the reflectable actions list.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        Song song
    ) {
        var managedActions = new ArrayList<UIAction>();

        for (var action : actions) {
            managedActions.add((UIAction) action);
        }

        return createCoordinator(notes, actions, managedActions, song);
    }

    /**
     * Creates a SelectionCoordinator with a Line containing the given notes,
     * registered and activated at line index 0, with the given reflectable and
     * managed actions injected. The line is backed by a minimal song mock with
     * mutation tracking suspended.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions
    ) {
        return createCoordinator(notes, actions, managedActions, UnitTest.minimalSongMock());
    }

    /**
     * Creates a SelectionCoordinator with a Line backed by the given {@code song},
     * registered and activated at line index 0, with the given reflectable and
     * managed actions injected.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions,
        Song song
    ) {
        var line = new Line(song);

        for (var note : notes) {
            line.addElement(note);
        }

        var coordinator = new SelectionCoordinator(editModeScoreView());
        var state = new LineSelectionState(line);
        coordinator.registerLineState(0, state);
        coordinator.activateLine(0);

        return createCoordinator(coordinator, actions, managedActions);
    }

    private static SelectionCoordinator createCoordinator(
        SelectionCoordinator coordinator,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions
    ) {
        try {
            var reflField = SelectionCoordinator.class.getDeclaredField("reflectableActions");
            reflField.setAccessible(true);
            reflField.set(coordinator, new ArrayList<>(actions));

            var managedField = SelectionCoordinator.class.getDeclaredField("managedActions");
            managedField.setAccessible(true);
            managedField.set(coordinator, new ArrayList<>(managedActions));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject test actions", e);
        }

        return coordinator;
    }

    /**
     * Returns the selection state for the coordinator's active line, failing with a
     * message that names the problem rather than letting the caller trip over a bare
     * NullPointerException when the coordinator was never given an active line.
     */
    private static LineSelectionState activeSelection(SelectionCoordinator coordinator) {
        var state = coordinator.getActiveSelection();

        if (state == null) {
            throw new AssertionError("the test coordinator has no active line state");
        }

        return state;
    }

    /**
     * Selects notes [fromIndex..toIndex] inclusive on the coordinator's active line.
     */
    public static void selectRange(SelectionCoordinator coordinator, int fromIndex, int toIndex) {
        coordinator.saveActionStates();

        var state = activeSelection(coordinator);
        state.setSelectionFromClick(fromIndex);

        if (toIndex != fromIndex) {
            state.extendSelectionTo(toIndex);
        }
    }

    /**
     * Selects a single note on the coordinator's active line.
     */
    public static void selectNote(SelectionCoordinator coordinator, int noteIndex) {
        selectRange(coordinator, noteIndex, noteIndex);
    }

    /**
     * Selects the glissando owned by the element at the given index.
     */
    public static void selectGlissando(SelectionCoordinator coordinator, int elementIndex) {
        coordinator.saveActionStates();
        var state = activeSelection(coordinator);
        state.selectDecoration(new SelectedDecoration.SlideSelection(elementIndex));
    }

    /**
     * Selects the given ending on the coordinator's active line.
     */
    public static void selectEnding(SelectionCoordinator coordinator, Ending ending) {
        coordinator.saveActionStates();
        var state = activeSelection(coordinator);
        state.selectDecoration(new SelectedDecoration.EndingSelection(ending));
    }

    /**
     * Selects the given hairpin on the coordinator's active line.
     */
    public static void selectHairpin(SelectionCoordinator coordinator, Hairpin hairpin) {
        coordinator.saveActionStates();
        activeSelection(coordinator).selectDecoration(new SelectedDecoration.HairpinSelection(hairpin));
    }

    /**
     * Clears the selection on the coordinator's active line.
     */
    public static void clearSelection(SelectionCoordinator coordinator) {
        activeSelection(coordinator).clearSelection();
        coordinator.restoreActionStates();
    }
}
